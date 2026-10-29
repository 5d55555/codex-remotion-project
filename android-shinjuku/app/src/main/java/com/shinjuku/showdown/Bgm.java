package com.shinjuku.showdown;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * 背景音乐：用户给的原曲（res/raw/bgm.mp3，原样打包，不转码、不改响度），循环播放。
 * - 启动后就准备好（prepareAsync），点“开始游戏”立刻从头播放；反复点就每次都从头。
 * - MediaPlayer 的所有调用都在专用线程上：它们是跨进程调用，放在主线程上偶尔会卡住界面。
 * - 切到后台暂停，回到前台接着播；来电等临时占用音频时暂停，结束后接着播；
 *   别的应用长期占用（比如开始放音乐）时停下，等下次点开始游戏。
 * - 播放器出错就重建（自动重试有上限），任何 MediaPlayer 异常都不会让应用崩溃。
 * - 全应用只有一个实例，以后进游戏界面时音乐不断。
 *
 * 下面所有字段只在 bgm 线程上读写。
 */
final class Bgm implements MediaPlayer.OnPreparedListener, MediaPlayer.OnSeekCompleteListener, MediaPlayer.OnErrorListener,
        AudioManager.OnAudioFocusChangeListener {

    private static final String TAG = "Bgm";
    private static final float VOLUME = 1.0f; // 原曲原样播放，不改音量
    private static final int MAX_AUTO_RETRIES = 3;

    @android.annotation.SuppressLint("StaticFieldLeak") // 只持有 Application Context，不会泄漏 Activity
    private static Bgm instance;

    private final Context app;
    private final Handler handler;
    private final AudioManager audio;
    private final AudioAttributes attrs;
    private final AudioFocusRequest focusRequest;

    private MediaPlayer player;
    private boolean prepared, started, seeking; // 播放器状态（自己记，不拿 isPlaying() 当状态机）
    private boolean wanted, restartPending;     // 用户的意图：要播；下次要从头
    private boolean hostPaused;                 // Activity 在后台
    private boolean focusRegistered, focusUsable; // 焦点请求还登记着；现在允许出声
    private int retries;

    static synchronized Bgm get(Context context) {
        if (instance == null) instance = new Bgm(context.getApplicationContext());
        return instance;
    }

    private Bgm(Context app) {
        this.app = app;
        HandlerThread thread = new HandlerThread("bgm"); // 只发命令，解码和出声不在这个线程，用默认优先级
        thread.start();
        handler = new Handler(thread.getLooper());
        audio = app.getSystemService(AudioManager.class);
        attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener(this, handler)
                .build();
    }

    /* ---------------- 主线程调用的入口：只投递 ---------------- */

    /** 提前准备：创建播放器并异步解码到可播放状态。重复调用无副作用。 */
    void prepare() {
        handler.post(this::create);
    }

    /** 从头播放（循环）。还没准备好就记下来，准备好后立刻从头开始。 */
    void playFromStart() {
        handler.post(() -> {
            wanted = true;
            restartPending = true;
            retries = 0; // 用户主动点击，允许重新尝试
            if (player == null) create();
            drive();
        });
    }

    /** Activity onPause / onResume。 */
    void setHostPaused(boolean paused) {
        handler.post(() -> {
            hostPaused = paused;
            if (paused) pauseNow();
            else drive();
        });
    }

    /** 应用退出时释放。 */
    void release() {
        handler.post(() -> {
            wanted = false;
            restartPending = false;
            handler.removeCallbacksAndMessages(RETRY);
            destroyPlayer();
            abandonFocus();
        });
    }

    /* ---------------- 以下都在 bgm 线程上 ---------------- */

    private static final Object RETRY = new Object();

    private void create() {
        if (player != null) return;
        prepared = started = seeking = false;
        MediaPlayer mp = null;
        try (AssetFileDescriptor fd = app.getResources().openRawResourceFd(R.raw.bgm)) {
            mp = new MediaPlayer();
            mp.setAudioAttributes(attrs);
            mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            mp.setLooping(true);
            mp.setVolume(VOLUME, VOLUME);
            mp.setOnPreparedListener(this);
            mp.setOnSeekCompleteListener(this);
            mp.setOnErrorListener(this);
            mp.prepareAsync();
            player = mp;
        } catch (Exception e) {
            Log.w(TAG, "create failed", e);
            if (mp != null) mp.release();
            scheduleRetry();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (mp != player) return;
        prepared = true; // 重试计数只在用户点击时清零，避免“准备成功、播放失败”无限重建
        drive();
    }

    /**
     * 唯一的播放入口：点击、准备完成、seek 完成、回到前台、焦点恢复都走这里。
     * 要从头就先 seek 到 0，等 onSeekComplete 再开始，seek 没完成之前不会开播。
     */
    private void drive() {
        if (player == null || !prepared || !wanted || hostPaused || seeking) return;
        if (!ensureFocus()) return;
        try {
            if (restartPending) {
                // 先停住，跳回开头，等最后一次 seek 完成再统一开播（连点也不会先响一小段）
                pauseNow();
                if (player == null) return; // 暂停时播放器坏了，已安排重建
                restartPending = false;
                seeking = true;
                player.seekTo(0, MediaPlayer.SEEK_CLOSEST);
            } else if (!started) {
                player.start();
                started = true;
            }
        } catch (IllegalStateException e) {
            fail(e);
        }
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        if (mp != player || !seeking) return;
        seeking = false;
        drive(); // 正在播的话已经从开头接着播；停着的话现在开始（期间切了后台或丢了焦点就先不播）
    }

    private void pauseNow() {
        if (player == null || !prepared || !started) return;
        started = false;
        try {
            player.pause();
        } catch (IllegalStateException e) {
            fail(e);
        }
    }

    /** 能出声吗：临时失去焦点期间等它回来；没有登记过就去申请（必须在前台，失败就不播）。 */
    private boolean ensureFocus() {
        if (focusUsable) return true;
        if (focusRegistered) return false;
        if (audio.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRegistered = true;
            focusUsable = true;
            return true;
        }
        return false;
    }

    private void abandonFocus() {
        if (focusRegistered) audio.abandonAudioFocusRequest(focusRequest);
        focusRegistered = false;
        focusUsable = false;
    }

    @Override
    public void onAudioFocusChange(int change) {
        switch (change) {
            case AudioManager.AUDIOFOCUS_GAIN:
                focusRegistered = true;
                focusUsable = true;
                drive();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // 来电、语音助手等临时占用：暂停，焦点回来再接着播
                focusUsable = false;
                pauseNow();
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // 别的应用长期占用：停下并交还焦点，等下次点开始游戏
                pauseNow();
                wanted = false;
                restartPending = false;
                abandonFocus();
                break;
            default:
                // 允许降音的临时占用：不改音量（Android 8+ 系统会自己处理）
                break;
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (mp != player) return true;
        Log.w(TAG, "player error what=" + what + " extra=" + extra);
        fail(null);
        return true;
    }

    /** 播放器坏了：释放，延迟重建（有上限）；重建后要播的话从头开始。 */
    private void fail(Exception e) {
        if (e != null) Log.w(TAG, "player call failed", e);
        destroyPlayer();
        if (wanted) restartPending = true;
        scheduleRetry();
    }

    private void scheduleRetry() {
        if (retries >= MAX_AUTO_RETRIES) return;
        retries++;
        handler.postAtTime(this::create, RETRY, android.os.SystemClock.uptimeMillis() + 500L * retries);
    }

    private void destroyPlayer() {
        if (player != null) {
            try {
                player.release();
            } catch (RuntimeException ignored) {
                // 已经坏了，释放失败也不再处理
            }
            player = null;
        }
        prepared = started = seeking = false;
    }
}
