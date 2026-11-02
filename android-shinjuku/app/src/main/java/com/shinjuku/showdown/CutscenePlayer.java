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
 * 领域展开前的过场视频（用户给的原片，原样打包）：五条悟的无量空处 res/raw/domain_void.mp4、宿傩的伏魔御厨子 res/raw/domain_shrine.mp4。
 * 一个播放器，进战斗先准备无量空处；要放的不是准备好的那个就当场换片子再准备（本地文件，几百毫秒，算在首帧超时里）。
 * - 画面：第二个 SurfaceView（setZOrderMediaOverlay：在战斗画面上面、窗口下面），按视频比例完整显示，两边黑边，不裁不拉伸；
 *   上面盖一层“幕布”：第一帧出来之前全黑（不露出战斗画面），之后只挡黑边。放的时候战斗收不到触摸。
 * - 进战斗就 prepareAsync，按下领域时直接开播。MediaPlayer 的所有调用都在专用线程上（和 Bgm 一样，不碰主线程）。
 * - 结束（播完、出错、准备或首帧超过 2 秒、前台累计超过视频长度 + 2.5 秒）都走同一个入口，同一次只算一次；结束后回调 Host.cutsceneDone。
 * - 切后台：视频暂停、不计时；回前台接着播。背景音乐由 BattleActivity 在过场期间压低。
 * - v0.17 反制：放宿傩的伏魔御厨子时，幕布识别长按（0.45 秒、移动不超过 24dp）→ Host.cutsceneCounter；战斗线程接受后
 *   counterResult(true) 换成两边同时展开的 domain_clash.mp4（新的 castId，换一个新的 MediaPlayer：旧播放器排着的回调对不上就丢掉）。
 *   宿傩那段在长按进行中或请求还没回来时播完：先停在末帧等结果。宿傩那段放不出来：给 1 秒的兜底卡片，照样能长按。
 *
 * 带 “m” 前缀的字段只在 cutscene 线程上读写。
 */
final class CutscenePlayer implements SurfaceHolder.Callback, MediaPlayer.OnPreparedListener, MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener, MediaPlayer.OnInfoListener {

    interface Host {
        /** 过场结束（主线程）：castId 是开始时带过来的那个。 */
        void cutsceneDone(int castId);

        /** 宿傩的过场里长按成功（主线程）：去战斗线程试着反制，结果回 counterResult。 */
        void cutsceneCounter(int castId);
    }

    /** 反制的提示：能用、冷却中、当前状态不能、不适用（不是宿傩的过场）。 */
    static final int COUNTER_READY = 0, COUNTER_COOLDOWN = 1, COUNTER_BLOCKED = 2, COUNTER_NONE = 3;
    private static final long COUNTER_HOLD_MS = 450, COUNTER_FALLBACK_MS = 1000;

    private static final String TAG = "Cutscene";
    private static final long FIRST_FRAME_TIMEOUT_MS = 2000, TOTAL_SLACK_MS = 2500, TOTAL_FALLBACK_MS = 16_000;
    static final int VOID = 0, SHRINE = 1, CLASH = 2; // 哪一段：无量空处、伏魔御厨子、两边同时展开（v0.17）
    private static final int[] RAW = {R.raw.domain_void, R.raw.domain_shrine, R.raw.domain_clash};

    private final Context app;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final HandlerThread thread = new HandlerThread("cutscene");
    private final Handler media;
    private final SurfaceView video;
    private final Curtain curtain;

    // 主线程
    private int showing = -1; // 正在放的 castId，-1 没在放
    private int showingKind = -1;
    private boolean released;
    private boolean counterInFlight; // 长按成功、等战斗线程的结果
    private int doneWaiting = -1;    // 这一段已经放完，等长按 / 反制的结果再收
    private boolean fallbackShown;
    private float videoAspect = 16f / 9f;

    // cutscene 线程
    private MediaPlayer mPlayer;
    private boolean mPrepared, mStarted, mRendering, mForeground = true;
    private final boolean[] mFailed = new boolean[RAW.length]; // 这一段坏了：以后直接跳过
    private int mLoaded = -1, mWant = VOID;  // 播放器里装的是哪一段；这次要放哪一段
    private long mDurationMs;              // 装着的这一段多长（准备好以后才知道）
    private SurfaceHolder mSurface;
    private int mSession = -1;           // 正在放的 castId，-1 没在放
    private long mPlayedMs, mResumedAt;  // 前台累计播放时间（超时用）
    private final Runnable mFirstFrameTimeout = () -> finish("首帧超时");
    private final Runnable mTotalTimeout = () -> finish("总时长超时");
    private final float mVolume;         // 设置里的总音量（v0.16；视频声音原本已经统一到 −10 LUFS 左右）

    CutscenePlayer(Context context, FrameLayout parent, Host host) {
        this.app = context.getApplicationContext();
        this.host = host;
        mVolume = Settings.videoGain(context);
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

    /** 开始放（主线程）：kind 是 VOID 或 SHRINE；宿傩的过场带上反制的状态（能不能长按、冷却还剩几秒）。 */
    void play(int castId, int kind, int counterStatus, float cooldownSec) {
        if (released) return;
        showing = castId;
        showingKind = kind;
        counterInFlight = false;
        doneWaiting = -1;
        fallbackShown = false;
        curtain.setCounter(kind == SHRINE ? counterStatus : COUNTER_NONE, cooldownSec);
        curtain.open = false;
        curtain.setVisibility(View.VISIBLE);
        curtain.invalidate();
        video.setVisibility(View.VISIBLE); // Surface 稍后建好（surfaceCreated）
        fit();
        media.post(() -> {
            mSession = castId;
            mRendering = false;
            mPlayedMs = 0;
            mWant = kind;
            if (mFailed[kind]) {
                finish("视频不可用");
                return;
            }
            if (mForeground) armTimeouts();
            if (mLoaded != kind) load(kind); // 装的不是这一段：换片子（准备好了 onPrepared 再开播）
            else maybeStart();
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
                        mPlayer.setVolume(mVolume, mVolume);
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

    /** 长按反制的结果（主线程）：接受了就换成同时展开的那一段（新的 castId）；没接受就照常收尾。 */
    void counterResult(int oldId, boolean accepted, int newId) {
        if (released || showing != oldId) return;
        counterInFlight = false;
        if (!accepted) {
            curtain.setCounter(COUNTER_BLOCKED, 0);
            if (doneWaiting == oldId) complete(oldId);
            return;
        }
        showing = newId;
        showingKind = CLASH;
        doneWaiting = -1;
        curtain.setCounter(COUNTER_NONE, 0);
        curtain.showClashTitle();
        curtain.open = false; // 新片第一帧出来之前全黑
        curtain.invalidate();
        media.post(() -> {
            media.removeCallbacks(mFirstFrameTimeout);
            media.removeCallbacks(mTotalTimeout);
            if (mPlayer != null) { // 换一个新的播放器：旧的排着的回调（播完、出错、首帧）对不上 mPlayer 就丢掉
                try {
                    mPlayer.release();
                } catch (RuntimeException ignored) {
                    // 已经坏了
                }
                mPlayer = null;
            }
            mSession = newId;
            mWant = CLASH;
            mRendering = false;
            mPlayedMs = 0;
            mLoaded = -1;
            mPrepared = mStarted = false;
            create(); // 新播放器装上 CLASH
            if (mForeground) armTimeouts();
        });
    }

    /** 长按在进行（主线程，幕布回调）：这一段放完也先别收。 */
    private void onCounterGesture(boolean holding) {
        if (!holding && doneWaiting == showing && !counterInFlight) complete(showing);
    }

    private void onCounterFire() {
        if (released || showing < 0 || showingKind != SHRINE || counterInFlight) return;
        counterInFlight = true;
        host.cutsceneCounter(showing);
    }

    /** 真正收尾：藏起来、告诉 Host。 */
    private void complete(int id) {
        if (released || showing != id) return;
        doneWaiting = -1;
        hide();
        host.cutsceneDone(id);
    }

    private void hide() {
        showing = -1;
        showingKind = -1;
        curtain.setCounter(COUNTER_NONE, 0);
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
        } catch (Exception e) {
            Log.w(TAG, "create", e);
            java.util.Arrays.fill(mFailed, true);
            return;
        }
        load(mSession >= 0 ? mWant : VOID);
    }

    private boolean stale(MediaPlayer mp) {
        return mp != mPlayer; // 换片时换掉的旧播放器的回调
    }

    /** 把这一段装进播放器（异步准备）。 */
    private void load(int kind) {
        if (mPlayer == null) {
            mFailed[kind] = true;
            if (mSession >= 0) finish("没有播放器");
            return;
        }
        mLoaded = kind;
        mPrepared = false;
        mStarted = false;
        mDurationMs = 0;
        try {
            mPlayer.reset();
            try (AssetFileDescriptor fd = app.getResources().openRawResourceFd(RAW[kind])) {
                mPlayer.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            }
            mPlayer.prepareAsync();
        } catch (Exception e) { // IOException、IllegalStateException……都当作没有这段过场
            Log.w(TAG, "prepare", e);
            mFailed[kind] = true;
            if (mSession >= 0) finish("准备失败");
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        if (stale(mp)) return;
        mPrepared = true;
        try {
            mDurationMs = Math.max(0, mp.getDuration());
        } catch (RuntimeException e) {
            mDurationMs = 0;
        }
        if (mSession >= 0 && mForeground) armTotal(); // 知道了片长：只重排总时长保护（首帧超时从开始放算，准备的时间也算在里面）
        int w = mp.getVideoWidth(), h = mp.getVideoHeight();
        if (w > 0 && h > 0) {
            float aspect = w / (float) h;
            int id = mSession;
            main.post(() -> {
                if (released || showing != id) return;
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
        if (mSession < 0 || mStarted || !mPrepared || mLoaded != mWant || mSurface == null || !mForeground) return;
        try {
            mPlayer.seekTo(0);
            mPlayer.setVolume(mVolume, mVolume); // 设置里的总音量（v0.16）
            mPlayer.start();
            mStarted = true;
            mResumedAt = SystemClock.uptimeMillis();
        } catch (RuntimeException e) {
            finish("开播失败");
        }
    }

    private void armTimeouts() {
        media.removeCallbacks(mFirstFrameTimeout);
        if (!mRendering) media.postDelayed(mFirstFrameTimeout, FIRST_FRAME_TIMEOUT_MS);
        armTotal();
    }

    private void armTotal() {
        media.removeCallbacks(mTotalTimeout);
        long total = mPrepared && mLoaded == mWant && mDurationMs > 0 ? mDurationMs + TOTAL_SLACK_MS : TOTAL_FALLBACK_MS;
        media.postDelayed(mTotalTimeout, Math.max(0, total - mPlayedMs));
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (stale(mp)) return false;
        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START && mSession >= 0 && !mRendering) {
            mRendering = true;
            media.removeCallbacks(mFirstFrameTimeout);
            int id = mSession;
            main.post(() -> {
                if (released || showing != id) return; // 换片后才到的旧首帧通知：不能提前打开幕布
                curtain.open = true; // 第一帧出来了：幕布只挡黑边
                curtain.invalidate();
            });
        }
        return false;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (stale(mp)) return;
        finish(null);
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        if (stale(mp)) return true;
        Log.w(TAG, "error " + what + "/" + extra);
        if (mLoaded >= 0) mFailed[mLoaded] = true; // 这一段坏了就不再用它：以后的这种领域展开直接跳过过场
        mPrepared = false;
        mLoaded = -1; // 下次要放就重新装（reset 以后才能再用）
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
        if (mStarted && mLoaded >= 0 && !mFailed[mLoaded]) {
            try {
                mPlayer.pause();
                mPlayer.seekTo(0);
            } catch (RuntimeException e) {
                Log.w(TAG, "reset", e);
            }
        }
        mStarted = false;
        boolean failed = why != null;
        main.post(() -> {
            if (released || showing != id) return;
            // 宿傩那段放完或放不出来，而玩家正在长按 / 反制请求还没回来：停在末帧等结果
            if (showingKind == SHRINE && (curtain.holding() || counterInFlight)) {
                doneWaiting = id;
                return;
            }
            // 宿傩那段放不出来、本来能反制：给 1 秒的兜底卡片，照样能长按
            if (showingKind == SHRINE && failed && curtain.counterReady() && !fallbackShown) {
                fallbackShown = true;
                doneWaiting = id;
                curtain.showFallback();
                main.postDelayed(() -> {
                    if (doneWaiting == id && !curtain.holding() && !counterInFlight) complete(id);
                }, COUNTER_FALLBACK_MS);
                return;
            }
            complete(id);
        });
    }

    /**
     * 幕布：第一帧之前全黑；之后只把视频以外的地方涂黑。吃掉所有触摸（战斗收不到）。
     * v0.17：放宿傩的过场时在底部写反制的提示；能用时识别长按（主指按住 0.45 秒、移动不超过 24dp，第二根手指不算），
     * 按着时在指尖画一圈进度。长按成功后这一次触摸一直吞到全部抬起。
     */
    @SuppressLint("ViewConstructor")
    private final class Curtain extends View {
        private final Paint black = new Paint(), text = new Paint(Paint.ANTI_ALIAS_FLAG), box = new Paint(Paint.ANTI_ALIAS_FLAG),
                ring = new Paint(Paint.ANTI_ALIAS_FLAG), ringBg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float dp;
        boolean open;
        private int hl, ht, hr, hb;
        private int counter = COUNTER_NONE;
        private float cooldown;
        private boolean fallback, clashTitle;
        private int pointer = -1;          // 正在长按的主指
        private float downX, downY;
        private long downAt;
        private boolean fired, swallow;
        private final Runnable fire = this::fire;

        Curtain(Context c) {
            super(c);
            dp = c.getResources().getDisplayMetrics().density;
            black.setColor(0xFF000000);
            text.setColor(0xFFF4F1EA);
            text.setTextSize(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, 15, c.getResources().getDisplayMetrics()));
            text.setTextAlign(Paint.Align.CENTER);
            text.setLetterSpacing(0.12f);
            box.setColor(0xB3000000);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(3 * dp);
            ring.setStrokeCap(Paint.Cap.ROUND);
            ring.setColor(0xFF7FC8FF);
            ringBg.setStyle(Paint.Style.STROKE);
            ringBg.setStrokeWidth(3 * dp);
            ringBg.setColor(0x55FFFFFF);
            setClickable(true);
        }

        void setHole(int l, int t, int r, int b) {
            hl = l;
            ht = t;
            hr = r;
            hb = b;
            invalidate();
        }

        void setCounter(int status, float cooldownSec) {
            counter = status;
            cooldown = cooldownSec;
            fallback = false;
            clashTitle = false;
            cancelGesture();
            invalidate();
        }

        void showFallback() {
            fallback = true;
            open = false;
            invalidate();
        }

        void showClashTitle() {
            clashTitle = true;
            invalidate();
        }

        boolean counterReady() {
            return counter == COUNTER_READY;
        }

        boolean holding() {
            return pointer >= 0 && !fired;
        }

        private void cancelGesture() {
            boolean was = holding();
            removeCallbacks(fire);
            pointer = -1;
            if (was) onCounterGesture(false);
        }

        private void fire() {
            if (pointer < 0 || fired) return;
            fired = true;
            swallow = true;
            pointer = -1;
            invalidate();
            onCounterFire();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (!open) {
                c.drawRect(0, 0, w, h, black);
            } else {
                c.drawRect(0, 0, w, ht, black);
                c.drawRect(0, hb, w, h, black);
                c.drawRect(0, ht, hl, hb, black);
                c.drawRect(hr, ht, w, hb, black);
            }
            if (fallback) drawLabel(c, w, h * 0.45f, "伏魔御厨子正在展开");
            String hint = null;
            if (clashTitle) hint = "领域对撞";
            else if (counter == COUNTER_READY) hint = holding() ? "正在展开" : "长按屏幕 · 展开无量空处";
            else if (counter == COUNTER_COOLDOWN) hint = String.format(java.util.Locale.ROOT, "无量空处冷却中 · %.1f 秒", cooldown);
            else if (counter == COUNTER_BLOCKED) hint = "当前状态无法展开";
            if (hint != null) drawLabel(c, w, h - 24 * dp - 22 * dp, hint);
            if (holding()) {
                float k = Math.min(1f, (SystemClock.uptimeMillis() - downAt) / (float) COUNTER_HOLD_MS);
                float r = 28 * dp;
                c.drawCircle(downX, downY, r, ringBg);
                c.drawArc(downX - r, downY - r, downX + r, downY + r, -90, 360 * k, false, ring);
                postInvalidateOnAnimation();
            }
        }

        private void drawLabel(Canvas c, int w, float y, String s) {
            float tw = text.measureText(s), pad = 14 * dp, th = text.getTextSize();
            c.drawRoundRect(w * 0.5f - tw * 0.5f - pad, y - th - 8 * dp, w * 0.5f + tw * 0.5f + pad, y + 10 * dp, 8 * dp, 8 * dp, box);
            c.drawText(s, w * 0.5f, y, text);
        }

        @Override
        @SuppressLint("ClickableViewAccessibility")
        public boolean onTouchEvent(MotionEvent e) {
            int a = e.getActionMasked();
            if (swallow) { // 长按成功以后：这一次触摸吞到全部抬起
                if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) swallow = false;
                return true;
            }
            if (counter != COUNTER_READY || showingKind != SHRINE || counterInFlight) return true;
            switch (a) {
                case MotionEvent.ACTION_DOWN:
                    if (doneWaiting >= 0 && !fallback) return true; // 片子已经放完：不再开始新的长按
                    pointer = e.getPointerId(0);
                    downX = e.getX();
                    downY = e.getY();
                    downAt = SystemClock.uptimeMillis();
                    fired = false;
                    postDelayed(fire, COUNTER_HOLD_MS);
                    onCounterGesture(true);
                    invalidate();
                    break;
                case MotionEvent.ACTION_MOVE: {
                    int i = e.findPointerIndex(pointer);
                    if (i >= 0 && Math.hypot(e.getX(i) - downX, e.getY(i) - downY) > 24 * dp) {
                        cancelGesture();
                        invalidate();
                    }
                    break;
                }
                case MotionEvent.ACTION_POINTER_UP:
                    if (e.getPointerId(e.getActionIndex()) == pointer) {
                        cancelGesture();
                        invalidate();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    cancelGesture();
                    invalidate();
                    break;
                default:
                    break;
            }
            return true;
        }
    }
}
