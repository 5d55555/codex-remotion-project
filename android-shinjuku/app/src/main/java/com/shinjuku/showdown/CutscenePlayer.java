package com.shinjuku.showdown;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;

import java.util.concurrent.CountDownLatch;

/**
 * 领域展开前的过场视频（res/raw/domain_void.mp4，用户给的原片，原样打包）。
 * - 画面：第二个 SurfaceView（setZOrderMediaOverlay：在战斗画面上面、窗口下面），按视频比例完整显示，两边黑边，不裁不拉伸；
 *   上面盖一层“幕布”：第一帧出来之前全黑（不露出战斗画面），之后只挡黑边。放的时候战斗收不到触摸。
 * - 进战斗就 prepareAsync，按下领域时直接开播。MediaPlayer 的所有调用都在专用线程上（和 Bgm 一样，不碰主线程）。
 * - 结束（播完、出错、准备或首帧超过 2 秒、前台累计超过 10 秒）都走同一个入口，同一次只算一次；结束后回调 Host.cutsceneDone。
 * - 切后台：视频暂停、不计时；回前台接着播。背景音乐不动（原样继续，视频的声音和它混在一起）。
 *
 * 带 “m” 前缀的字段只在 cutscene 线程上读写。
 */
final class CutscenePlayer implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener {

    interface Host {
        /** 过场结束（主线程）：castId 是开始时带过来的那个。 */
        void cutsceneDone(int castId);
    }

    private static final String TAG = "Cutscene";
    private static final long FIRST_FRAME_TIMEOUT_MS = 2000, TOTAL_TIMEOUT_MS = 10_000;

    private final Context app;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("cutscene");
    private final Handler media;
    private final SurfaceView video;
    private final Curtain curtain;

    // 主线程
    private int showing = -1; // 正在放的 castId，-1 没在放
    private boolean released;
    private float videoAspect = 16f / 9f;

    // cutscene 线程
    private MediaPlayer mPlayer;
    private boolean mPrepared, mFailed, mStarted, mRendering, mForeground = true;
    private SurfaceHolder mSurface;
    private int mSession = -1;           // 正在放的 castId，-1 没在放
    private long mPlayedMs, mResumedAt;  // 前台累计播放时间（超时用）
    private final Runnable mFirstFrameTimeout = () -> finish("首帧超时");
    private final Runnable mTotalTimeout = () -> finish("总时长超时");

    CutscenePlayer(Context context, FrameLayout parent, Host host) {
        this.app = context.getApplicationContext();
        this.host = host;
        thread.start();
        media = new Handler(thread.getLooper());
        video = new SurfaceView(context);
        video.setZOrderMediaOverlay(true); // 挂进窗口前设置
        video.getHolder().addCallback(this);
        video.setVisibility(View.GONE);
        curtain = new Curtain(context);
        curtain.setVisibility(View.GONE);
        parent.addView(video, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        parent.addView(curtain, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        parent.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> fit());
        media.post(this::create);
    }

    /* ---------------- 主线程 ---------------- */

    /** 开始放（主线程）。 */
    void play(int castId) {
        if (released) return;
        showing = castId;
        curtain.open = false;
        curtain.setVisibility(View.VISIBLE);
        curtain.invalidate();
        video.setVisibility(View.VISIBLE); // Surface 稍后建好（surfaceCreated）
        fit();
        media.post(() -> {
            mSession = castId;
            mRendering = false;
            mPlayedMs = 0;
            if (mFailed) {
                finish("视频不可用");
                return;
            }
            if (mForeground) armTimeouts();
            maybeStart();
        });
    }

    boolean isPlaying() {
        return showing >= 0;
    }

    void setForeground(boolean fg) {
        media.post(() -> {
            if (mForeground == fg) return;
            mForeground = fg;
            if (mSession < 0) return;
            if (!fg) {
                media.removeCallbacks(mFirstFrameTimeout);
                media.removeCallbacks(mTotalTimeout);
                if (mStarted) {
                    mPlayedMs += SystemClock.uptimeMillis() - mResumedAt;
                    try {
                        mPlayer.pause();
                    } catch (RuntimeException e) {
                        Log.w(TAG, "pause", e);
                    }
                }
            } else {
                armTimeouts();
                if (mStarted) {
                    try {
                        mPlayer.start();
                        mResumedAt = SystemClock.uptimeMillis();
                    } catch (RuntimeException e) {
                        finish("恢复失败");
                    }
                } else {
                    maybeStart();
                }
            }
        });
    }

    /**
     * 退出（主线程）：之后不再回调 Host。cutscene 线程上只撤掉超时，不清空整个队列——
     * surfaceDestroyed 投递的“摘下 Surface”还在排队的话必须执行，主线程在等它。
     */
    void release() {
        released = true;
        showing = -1;
        main.removeCallbacksAndMessages(null);
        media.post(() -> {
            media.removeCallbacks(mFirstFrameTimeout);
            media.removeCallbacks(mTotalTimeout);
            mSession = -1;
            if (mPlayer != null) {
                try {
                    mPlayer.release();
                } catch (RuntimeException ignored) {
                    // 已经坏了
                }
                mPlayer = null;
            }
            thread.quitSafely();
        });
    }

    /** 按视频比例放在屏幕正中（完整显示，两边或上下留黑边）。 */
    private void fit() {
        View parent = (View) video.getParent();
        if (parent == null) return;
        int w = parent.getWidth(), h = parent.getHeight();
        if (w <= 0 || h <= 0) return;
        int vw = w, vh = Math.round(w / videoAspect);
        if (vh > h) {
            vh = h;
            vw = Math.round(h * videoAspect);
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) video.getLayoutParams();
        int left = (w - vw) / 2, top = (h - vh) / 2;
        curtain.setHole(left, top, left + vw, top + vh);
        if (lp.width == vw && lp.height == vh && lp.leftMargin == left && lp.topMargin == top) return;
        lp.width = vw;
        lp.height = vh;
        lp.leftMargin = left;
        lp.topMargin = top;
        video.setLayoutParams(lp);
    }

    private void hide() {
        showing = -1;
        video.setVisibility(View.GONE);
        curtain.setVisibility(View.GONE);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        media.post(() -> {
            mSurface = holder;
            if (mPlayer != null && mPrepared) {
                try {
                    mPlayer.setDisplay(holder);
                } catch (RuntimeException e) {
                    Log.w(TAG, "setDisplay", e);
                }
            }
            maybeStart();
        });
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    }

    /** Surface 要没了：等 cutscene 线程把播放器从它上面摘下来再返回。 */
    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        CountDownLatch done = new CountDownLatch(1);
        boolean posted = media.post(() -> {
            try {
                mSurface = null;
                if (mPlayer != null && mPrepared) mPlayer.setDisplay(null);
            } catch (RuntimeException e) {
                Log.w(TAG, "setDisplay(null)", e);
            } finally {
                done.countDown();
            }
        });
        if (!posted) return;
        boolean interrupted = false;
        while (true) {
            try {
                done.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) Thread.currentThread().interrupt();
    }

    /* ---------------- cutscene 线程 ---------------- */

    private void create() {
        try {
            MediaPlayer p = new MediaPlayer();
            mPlayer = p;
            p.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                    .build());
            p.setOnPreparedListener(this);
            p.setOnCompletionListener(this);
            p.setOnErrorListener(this);
            p.setOnInfoListener(this);
            try (AssetFileDescriptor fd = app.getResources().openRawResourceFd(R.raw.domain_void)) {
                p.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            }
            p.prepareAsync();
        } catch (Exception e) { // IOException、IllegalStateException……都当作没有过场
            Log.w(TAG, "prepare", e);
            mFailed = true;
            if (mSession >= 0) finish("准备失败");
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mPrepared = true;
        int w = mp.getVideoWidth(), h = mp.getVideoHeight();
        if (w > 0 && h > 0) {
            float aspect = w / (float) h;
            main.post(() -> {
                if (released) return;
                videoAspect = aspect;
                fit();
            });
        }
        if (mSurface != null) {
            try {
                mp.setDisplay(mSurface);
            } catch (RuntimeException e) {
                Log.w(TAG, "setDisplay", e);
            }
        }
        maybeStart();
    }

    private void maybeStart() {
        if (mSession < 0 || mStarted || !mPrepared || mSurface == null || !mForeground) return;
        try {
            mPlayer.seekTo(0);
            mPlayer.start();
            mStarted = true;
            mResumedAt = SystemClock.uptimeMillis();
        } catch (RuntimeException e) {
            finish("开播失败");
        }
    }

    private void armTimeouts() {
        media.removeCallbacks(mFirstFrameTimeout);
        media.removeCallbacks(mTotalTimeout);
        if (!mRendering) media.postDelayed(mFirstFrameTimeout, FIRST_FRAME_TIMEOUT_MS);
        media.postDelayed(mTotalTimeout, Math.max(0, TOTAL_TIMEOUT_MS - mPlayedMs));
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && mSession >= 0 && !mRendering) {
            mRendering = true;
            media.removeCallbacks(mFirstFrameTimeout);
            main.post(() -> {
                if (released) return;
                curtain.open = true; // 第一帧出来了：幕布只挡黑边
                curtain.invalidate();
            });
        }
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        finish(null);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        Log.w(TAG, "error " + what + "/" + extra);
        mFailed = true; // 坏了就不再用它：以后的领域展开直接跳过过场
        mPrepared = false;
        finish("播放出错");
        return true;
    }

    /** 唯一的结束入口：同一次只算一次。播放器停在开头，下次展开再从头放。 */
    private void finish(String why) {
        int id = mSession;
        if (id < 0) return;
        if (why != null) Log.w(TAG, "跳过过场：" + why);
        mSession = -1;
        media.removeCallbacks(mFirstFrameTimeout);
        media.removeCallbacks(mTotalTimeout);
        if (mStarted && !mFailed) {
            try {
                mPlayer.pause();
                mPlayer.seekTo(0);
            } catch (RuntimeException e) {
                Log.w(TAG, "reset", e);
            }
        }
        mStarted = false;
        main.post(() -> {
            if (released || showing != id) return;
            hide();
            host.cutsceneDone(id);
        });
    }

    /** 幕布：第一帧之前全黑；之后只把视频以外的地方涂黑。吃掉所有触摸。 */
    @SuppressLint("ViewConstructor")
    private static final class Curtain extends View {
        private final Paint black = new Paint();
        boolean open;
        private int hl, ht, hr, hb;

        Curtain(Context c) {
            super(c);
            black.setColor(0xFF000000);
            setClickable(true);
        }

        void setHole(int l, int t, int r, int b) {
            hl = l;
            ht = t;
            hr = r;
            hb = b;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (!open) {
                c.drawRect(0, 0, w, h, black);
                return;
            }
            c.drawRect(0, 0, w, ht, black);
            c.drawRect(0, hb, w, h, black);
            c.drawRect(0, ht, hl, hb, black);
            c.drawRect(hr, ht, w, hb, black);
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouchEvent(MotionEvent e) {
            return true;
        }
    }
}
