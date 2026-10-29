package com.shinjuku.showdown;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;

/**
 * 背景音乐：用户给的原曲（res/raw/bgm.mp3，原样打包，不转码、不改响度），循环播放。
 * - 启动后就在后台准备好（prepareAsync），点“开始游戏”立刻从头播放；反复点就每次都从头。
 * - MediaPlayer 的所有调用（创建、准备、seek、播放、暂停、音频焦点）都放在专用线程上，
 *   这些都是跨进程调用，放在主线程上偶尔会卡住几毫秒、拖慢界面。
 * - 切到后台暂停，回到前台接着播；被来电、其他应用抢走音频焦点时暂停，焦点回来再接着播。
 * - 全应用只有一个实例，以后进游戏界面时音乐不断。
 */
final class Bgm implements MediaPlayer.OnPreparedListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

    private static final float VOLUME = 1.0f; // 原曲原样播放，不改音量
    private static final float DUCK_VOLUME = 0.25f;
    @android.annotation.SuppressLint("StaticFieldLeak") // 只持有 Application Context，不会泄漏 Activity
    private static Bgm instance;

    private final Context app;
    private final Handler handler; // 下面所有字段只在这个线程上读写
    private final AudioManager audio;
    private final AudioFocusRequest focusRequest;
    private MediaPlayer player;
    private boolean prepared, wanted, hostPaused, focusPaused, hasFocus, startAfterSeek;

    static synchronized Bgm get(Context context) {
        if (instance == null) instance = new Bgm(context.getApplicationContext());
        return instance;
    }

    private Bgm(Context app) {
        this.app = app;
        HandlerThread thread = new HandlerThread("bgm", Process.THREAD_PRIORITY_AUDIO);
        thread.start();
        handler = new Handler(thread.getLooper());
        audio = app.getSystemService(AudioManager.class);
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setWillPauseWhenDucked(false)
                .setOnAudioFocusChangeListener(this, handler)
                .build();
    }

    /* ---------------- 主线程调用的入口：只投递，不阻塞 ---------------- */

    /** 提前准备：创建播放器并异步解码到可播放状态。重复调用无副作用。 */
    void prepare() {
        handler.post(this::create);
    }

    /** 从头播放（循环）。还没准备好就记下来，准备好后立刻从头开始。 */
    void playFromStart() {
        handler.post(() -> {
            wanted = true;
            if (prepared) startFromZero();
            else create();
        });
    }

    /** Activity onPause / onResume。 */
    void setHostPaused(boolean paused) {
        handler.post(() -> {
            hostPaused = paused;
            if (paused) pauseIfPlaying();
            else resumeIfWanted();
        });
    }

    /** 应用退出时释放。 */
    void release() {
        handler.post(() -> {
            wanted = false;
            destroyPlayer();
            abandonFocus();
        });
    }

    /* ---------------- 以下都在 bgm 线程上 ---------------- */

    private void create() {
        if (player != null) return;
        prepared = false;
        MediaPlayer mp = new MediaPlayer();
        try (AssetFileDescriptor fd = app.getResources().openRawResourceFd(R.raw.bgm)) {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mp.setLooping(true);
            mp.setVolume(VOLUME, VOLUME);
            mp.setOnPreparedListener(this);
            mp.setOnSeekCompleteListener(this);
            mp.setOnErrorListener(this);
            mp.prepareAsync();
            player = mp;
        } catch (Exception e) {
            mp.release();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp != player) return;
        prepared = true;
        if (wanted) startFromZero();
    }

    /** 精确跳回开头；等 seek 真正完成（onSeekComplete）再开始播，避免 seek 和播放抢跑。 */
    private void startFromZero() {
        if (player == null || !prepared || hostPaused) return;
        if (!requestFocus()) return;
        focusPaused = false;
        startAfterSeek = true;
        player.seekTo(0, MediaPlayer.SEEK_CLOSEST);
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        if (mp != player || !startAfterSeek) return;
        startAfterSeek = false;
        // 正在播的话 seek 完就从开头接着播；停着的话现在开始（期间切了后台或丢了焦点就不播）
        if (wanted && !hostPaused && !focusPaused && !mp.isPlaying()) mp.start();
    }

    private void resumeIfWanted() {
        if (!wanted || player == null || !prepared || hostPaused || focusPaused) return;
        if (!requestFocus()) return;
        if (!player.isPlaying()) player.start();
    }

    private void pauseIfPlaying() {
        if (player != null && prepared && player.isPlaying()) player.pause();
    }

    private boolean requestFocus() {
        if (hasFocus) return true;
        hasFocus = audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        return hasFocus;
    }

    private void abandonFocus() {
        if (hasFocus) audio.abandonAudioFocusRequest(focusRequest);
        hasFocus = false;
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                hasFocus = true;
                if (player != null) player.setVolume(VOLUME, VOLUME);
                if (focusPaused) {
                    focusPaused = false;
                    resumeIfWanted();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player != null) player.setVolume(DUCK_VOLUME, DUCK_VOLUME);
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                focusPaused = true;
                pauseIfPlaying();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // 别的应用长期占用（比如开始放音乐）：暂停并交还焦点，等用户再点开始游戏或回到前台
                hasFocus = false;
                focusPaused = true;
                pauseIfPlaying();
                break;
            default:
                break;
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        // 播放器坏了：重建并重新准备；想播的话准备好后从头接着放
        destroyPlayer();
        create();
        return true;
    }

    private void destroyPlayer() {
        if (player != null) {
            player.release();
            player = null;
        }
        prepared = false;
        startAfterSeek = false;
    }
}
