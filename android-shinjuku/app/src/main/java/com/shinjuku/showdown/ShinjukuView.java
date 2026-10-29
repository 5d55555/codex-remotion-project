package com.shinjuku.showdown;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RecordingCanvas;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.Choreographer;
import android.view.Display;
import android.view.View;

import java.util.Random;

/**
 * 主页背景：新宿的夜。
 * 夜空和星星；一蓝一红两道光在半空交锋；地平线上两层楼群（近处那层亮着窗），
 * 正中是都厅双塔的剪影；地面有一层红雾，火星慢慢往上飘。全部用代码画，不用图片。
 *
 * 性能：
 * - 不会动的部分（夜空、远近楼群、灯窗、双塔）只录一次：Android 10+ 录成 RenderNode，
 *   每帧检查显示列表是否还在，被系统丢掉就重录；Android 8–9 缓存成一张有像素预算的位图。
 * - 会动的部分每帧零分配：着色器、路径、点数组都预先建好；星星、灭掉的窗、火星各用一次 drawPoints。
 * - 自己用 Choreographer 调度：按显示刷新率整数分频，最多约 60–90 帧/秒（120Hz 屏隔一帧画一次）。
 * - 只有“前台 + 可见 + 系统允许动画”时才动；停下时动画时间冻结，恢复后接着播。
 */
public class ShinjukuView extends View implements Choreographer.FrameCallback {

    private static final int TOWER = 0xFF1B2050; // 都厅双塔，比远处的楼亮一档
    private static final int NEAR = 0xFF07081A;
    private static final int FAR = 0xFF0E1130;
    private static final int STAR_GROUPS = 3;
    private static final int EMBER_BINS = 4;
    private static final long BITMAP_PIXEL_BUDGET = 2_000_000L; // 旧系统位图缓存的像素上限（约 8MB）
    private static final float[] BEAM_WIDTHS = {44, 26, 14, 6};
    private static final int[] BEAM_ALPHAS = {0x22, 0x40, 0x80, 0xE0};

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dots = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint flarePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hazePaint = new Paint();
    private final Paint bitmapPaint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect bitmapDst = new Rect();
    private final float dp;

    // 静态层
    private RenderNode staticNode;
    private Bitmap staticBitmap;

    // 动态部分预先算好的数据
    private final float[][] starPts = new float[STAR_GROUPS * 2][];
    private final float[] starPhase = new float[STAR_GROUPS];
    private float[] windows = new float[0]; // 每扇窗 3 个数：中心 x、中心 y、种子
    private float[] offPts = new float[0];
    private float[][] embers = new float[0][]; // x、起始进度、（空）、速度、相位
    private final float[][] emberPts = new float[EMBER_BINS][];
    private final int[] emberCount = new int[EMBER_BINS];
    private final Path[] beamPaths = new Path[8];
    private final Paint[] beamPaints = new Paint[8];
    private float flareX, flareY, flareR, towerLightY, towerLightL, towerLightR;

    // 调度与动画时钟
    private boolean resumed, visible, allowed = true, scheduled;
    private int vsyncDivisor = 1, vsyncCount;
    private long activeMs;      // 已经播放过的动画时长（停下时不累计）
    private long runStartMs = -1; // 本段开始播放的时刻；-1 表示停着

    public ShinjukuView(Context context) {
        this(context, null);
    }

    public ShinjukuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        dots.setStrokeCap(Paint.Cap.ROUND);
        for (int i = 0; i < beamPaths.length; i++) {
            beamPaths[i] = new Path();
            beamPaints[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        }
        if (Build.VERSION.SDK_INT >= 35) {
            // 慢节奏的背景动画，不需要让屏幕一直跑高刷（只是偏好，不是限速；限速见 doFrame）
            setRequestedFrameRate(REQUESTED_FRAME_RATE_CATEGORY_NORMAL);
        }
    }

    /* ---------------- 尺寸确定：建好所有缓存 ---------------- */

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        if (w <= 0 || h <= 0) return;
        Random r = new Random(1999);
        buildStars(w, h, r);
        buildEmbers(w, r);
        buildClash(w, h);
        hazePaint.setShader(new LinearGradient(0, h * 0.72f, 0, h,
                new int[]{0x00E0283C, 0x55E0283C, 0x99300810}, new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP));
        buildStatic(w, h);
    }

    private void buildStars(int w, int h, Random r) {
        int n = 90;
        int[] counts = new int[STAR_GROUPS * 2];
        float[][] tmp = new float[n][];
        for (int i = 0; i < n; i++) {
            int g = i % STAR_GROUPS;
            int big = r.nextFloat() < 0.3f ? 1 : 0;
            tmp[i] = new float[]{r.nextFloat() * w, r.nextFloat() * h * 0.6f, g * 2 + big};
            counts[g * 2 + big]++;
        }
        for (int k = 0; k < counts.length; k++) starPts[k] = new float[counts[k] * 2];
        int[] fillAt = new int[counts.length];
        for (float[] s : tmp) {
            int k = (int) s[2];
            starPts[k][fillAt[k]++] = s[0];
            starPts[k][fillAt[k]++] = s[1];
        }
        for (int g = 0; g < STAR_GROUPS; g++) starPhase[g] = g * 2.1f;
    }

    private void buildEmbers(int w, Random r) {
        embers = new float[28][];
        for (int i = 0; i < embers.length; i++) {
            embers[i] = new float[]{r.nextFloat() * w, r.nextFloat(), 0, 0.35f + r.nextFloat() * 0.65f, r.nextFloat() * 6.28f};
        }
        for (int b = 0; b < EMBER_BINS; b++) emberPts[b] = new float[embers.length * 2];
    }

    /** 两道光：每道四层越来越窄、越来越亮的楔形，路径和着色器都只建一次。 */
    private void buildClash(int w, int h) {
        flareX = w * 0.5f;
        flareY = h * 0.27f;
        beam(0, -w * 0.15f, h * 0.10f, 0xFF4C7DFF);
        beam(4, w * 1.15f, h * 0.06f, 0xFFE0283C);
        flareR = w * 0.32f;
        flarePaint.setShader(new RadialGradient(flareX, flareY, flareR,
                new int[]{0xCCFFFFFF, 0x55B98CFF, 0x00000000}, new float[]{0f, 0.25f, 1f}, Shader.TileMode.CLAMP));
    }

    private void beam(int at, float x0, float y0, int color) {
        float x1 = flareX, y1 = flareY;
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        float nx = -dy / len, ny = dx / len;
        int rgb = color & 0x00FFFFFF;
        for (int i = 0; i < 4; i++) {
            float hw = BEAM_WIDTHS[i] * dp / 2;
            Path p = beamPaths[at + i];
            p.reset();
            p.moveTo(x0 + nx * hw * 0.3f, y0 + ny * hw * 0.3f);
            p.lineTo(x1 + nx * hw, y1 + ny * hw);
            p.lineTo(x1 - nx * hw, y1 - ny * hw);
            p.lineTo(x0 - nx * hw * 0.3f, y0 - ny * hw * 0.3f);
            p.close();
            beamPaints[at + i].setShader(new LinearGradient(x0, y0, x1, y1, rgb, (BEAM_ALPHAS[i] << 24) | rgb, Shader.TileMode.CLAMP));
        }
    }

    /** 建静态层：Android 10+ 用 RenderNode（第一次绘制时录制），更早的系统用有像素预算的位图。 */
    private void buildStatic(int w, int h) {
        releaseStatic();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            staticNode = new RenderNode("shinjuku-static");
            staticNode.setPosition(0, 0, w, h);
            drawStatic(null, w, h); // 先算出窗户位置；真正录制推迟到第一次硬件绘制
        } else {
            double scale = Math.min(1.0, Math.sqrt((double) BITMAP_PIXEL_BUDGET / ((long) w * h)));
            int bw = Math.max(1, (int) Math.ceil(w * scale));
            int bh = Math.max(1, (int) Math.ceil(h * scale));
            staticBitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
            Canvas bc = new Canvas(staticBitmap);
            bc.scale((float) scale, (float) scale);
            drawStatic(bc, w, h);
            bitmapDst.set(0, 0, w, h);
        }
    }

    /**
     * 画不会动的部分。楼群用自己的固定种子：不管画几次（重录、软件画布），楼和窗的位置都一样。
     * c 为 null 时只计算窗户位置，不画。这是尺寸变化或重录时才走的慢路径，允许分配。
     */
    private void drawStatic(Canvas c, int w, int h) {
        Random r = new Random(2024);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (c != null) {
            p.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0xFF04050D, 0xFF0B0E2C, 0xFF1C0B26, 0xFF2E0A16},
                    new float[]{0f, 0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, p);
            p.setShader(null);
        }

        // 远处一层楼：高低错落，只有轮廓
        p.setColor(FAR);
        for (float x = -10 * dp; x < w; ) {
            float bw = (18 + r.nextFloat() * 34) * dp;
            float bh = h * (0.12f + r.nextFloat() * 0.16f);
            if (c != null) c.drawRect(x, h - bh, x + bw, h, p);
            x += bw + r.nextFloat() * 4 * dp;
        }

        // 都厅双塔：正中最高，顶上两座塔楼，塔身几列竖着的灯带
        float cx = w * 0.5f, bw = Math.min(w * 0.30f, 150 * dp);
        float body = h * 0.30f, tower = h * 0.38f, tw = bw * 0.36f;
        Path path = new Path();
        if (c != null) {
            p.setColor(TOWER);
            c.drawRect(cx - bw / 2, h - body, cx + bw / 2, h, p);
        }
        for (int s = -1; s <= 1; s += 2) {
            float tx = cx + s * (bw / 2 - tw / 2);
            if (c != null) {
                path.reset();
                path.moveTo(tx - tw / 2, h - body);
                path.lineTo(tx - tw / 2, h - tower + tw * 0.25f);
                path.lineTo(tx - tw * 0.32f, h - tower);
                path.lineTo(tx + tw * 0.32f, h - tower);
                path.lineTo(tx + tw / 2, h - tower + tw * 0.25f);
                path.lineTo(tx + tw / 2, h - body);
                path.close();
                p.setColor(TOWER);
                c.drawPath(path, p);
                p.setColor(0x55FFCF7A);
                for (int k = -1; k <= 1; k++) {
                    float lx = tx + k * tw * 0.26f;
                    c.drawRect(lx - 0.8f * dp, h - tower + tw * 0.5f, lx + 0.8f * dp, h - body * 0.55f, p);
                }
            }
            if (s < 0) towerLightL = tx;
            else towerLightR = tx;
        }
        towerLightY = h - tower - 3 * dp;

        // 近处一层楼与灯窗。窗户记下位置，偶尔“灭掉”的那几扇每帧用一次 drawPoints 盖上。
        float win = 3.6f * dp, gap = 4 * dp;
        float[] buf = new float[4096 * 3];
        int n = 0;
        Paint lit = new Paint();
        lit.setColor(0xCCFFCF7A);
        for (float x = -6 * dp; x < w; ) {
            float bwn = (26 + r.nextFloat() * 40) * dp;
            float bh = h * (0.06f + r.nextFloat() * 0.11f);
            if (c != null) {
                p.setColor(NEAR);
                c.drawRect(x, h - bh, x + bwn, h, p);
            }
            for (float wy = h - bh + 8 * dp; wy < h - 10 * dp; wy += win + gap) {
                for (float wx = x + 5 * dp; wx < x + bwn - 6 * dp; wx += win + gap) {
                    if (r.nextFloat() >= 0.42f) continue;
                    if (c != null) c.drawRect(wx, wy, wx + win, wy + win, lit);
                    float seed = r.nextFloat() * 1000;
                    if (n + 3 <= buf.length) {
                        buf[n++] = wx + win / 2;
                        buf[n++] = wy + win / 2;
                        buf[n++] = seed;
                    }
                }
            }
            x += bwn + r.nextFloat() * 3 * dp;
        }
        if (windows.length != n) {
            windows = new float[n];
            offPts = new float[n / 3 * 2];
        }
        System.arraycopy(buf, 0, windows, 0, n);
    }

    private void releaseStatic() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && staticNode != null) {
            staticNode.discardDisplayList();
        }
        staticNode = null;
        // 不主动 recycle：位图可能还被 View 的显示列表引用，交给 GC
        staticBitmap = null;
    }

    /* ---------------- 每帧：只画会动的部分，零分配 ---------------- */

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        double t = animSeconds();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && staticNode != null && c.isHardwareAccelerated()) {
            if (!staticNode.hasDisplayList()) {
                // 第一次绘制，或系统丢掉了显示列表：重录（慢路径）
                RecordingCanvas rc = staticNode.beginRecording(w, h);
                try {
                    drawStatic(rc, w, h);
                } finally {
                    staticNode.endRecording();
                }
            }
            c.drawRenderNode(staticNode);
        } else if (staticBitmap != null) {
            c.drawBitmap(staticBitmap, null, bitmapDst, bitmapPaint);
        } else {
            drawStatic(c, w, h); // 软件画布（截图等）：直接画，不另外缓存
        }

        // 星星：三组，各自明暗；大小两档
        for (int g = 0; g < STAR_GROUPS; g++) {
            int a = (int) (90 + 120 * (0.5f + 0.5f * Math.sin(t * 1.3 + starPhase[g])));
            dots.setColor((a << 24) | 0xDDE4FF);
            for (int big = 0; big < 2; big++) {
                float[] pts = starPts[g * 2 + big];
                if (pts == null || pts.length == 0) continue;
                dots.setStrokeWidth((big == 1 ? 3.2f : 1.8f) * dp);
                c.drawPoints(pts, dots);
            }
        }

        // 两道光与交锋点：只改透明度和缩放
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 2.2);
        int pa = (int) (255 * (0.75f + 0.25f * pulse));
        for (int i = 0; i < beamPaths.length; i++) {
            beamPaints[i].setAlpha(pa);
            c.drawPath(beamPaths[i], beamPaints[i]);
        }
        c.save();
        c.scale(pulse, pulse, flareX, flareY);
        c.drawCircle(flareX, flareY, flareR, flarePaint);
        c.restore();

        // 双塔顶上的航空灯
        for (int s = 0; s < 2; s++) {
            int a = (int) (120 + 135 * (0.5f + 0.5f * Math.sin(t * 3 + (s == 0 ? -1 : 1))));
            fill.setColor((a << 24) | 0xFF3B4E);
            c.drawCircle(s == 0 ? towerLightL : towerLightR, towerLightY, 2.2f * dp, fill);
        }

        // 偶尔灭掉一扇窗：用楼的颜色盖住
        int off = 0;
        for (int i = 0; i + 2 < windows.length; i += 3) {
            if (((long) (t * 0.25 + windows[i + 2])) % 7 == 0) {
                offPts[off++] = windows[i];
                offPts[off++] = windows[i + 1];
            }
        }
        if (off > 0) {
            dots.setStrokeCap(Paint.Cap.SQUARE);
            dots.setStrokeWidth(4.4f * dp);
            dots.setColor(NEAR);
            c.drawPoints(offPts, 0, off, dots);
            dots.setStrokeCap(Paint.Cap.ROUND);
        }

        // 地面红雾
        c.drawRect(0, h * 0.72f, w, h, hazePaint);

        // 火星：从底下往上飘，按高度分四档透明度，每档一次 drawPoints
        for (int b = 0; b < EMBER_BINS; b++) emberCount[b] = 0;
        for (float[] e : embers) {
            float p = (float) ((e[1] + t * 0.05 * e[3]) % 1.0);
            int b = Math.min(EMBER_BINS - 1, (int) (p * EMBER_BINS));
            float[] pts = emberPts[b];
            pts[emberCount[b]++] = e[0] + (float) Math.sin(t * 0.8 + e[4]) * 14 * dp;
            pts[emberCount[b]++] = h * (1.02f - p * 0.55f);
        }
        dots.setStrokeWidth(3.4f * dp);
        for (int b = 0; b < EMBER_BINS; b++) {
            if (emberCount[b] == 0) continue;
            int a = (int) (210 * (1f - (b + 0.5f) / EMBER_BINS));
            dots.setColor((a << 24) | 0xFF7A3C);
            c.drawPoints(emberPts[b], 0, emberCount[b], dots);
        }
    }

    /* ---------------- 调度：前台 + 可见 + 允许动画才动 ---------------- */

    /** 动画时钟：只累计真正在播放的时间。long 相减后再转 double，长时间运行也不丢精度。 */
    private double animSeconds() {
        long ms = activeMs + (runStartMs >= 0 ? SystemClock.uptimeMillis() - runStartMs : 0L);
        return ms / 1000.0;
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        scheduled = false;
        if (!shouldRun()) return;
        // 按刷新率整数分频：60Hz 每帧画，90Hz 每帧画，120/144Hz 隔一帧画
        if (++vsyncCount >= vsyncDivisor) {
            vsyncCount = 0;
            invalidate();
        }
        schedule();
    }

    private boolean shouldRun() {
        return resumed && visible && allowed && isAttachedToWindow();
    }

    private void schedule() {
        if (!scheduled) {
            scheduled = true;
            Choreographer.getInstance().postFrameCallback(this);
        }
    }

    /** 所有状态变化都走这里：决定开始还是停下，并保证只有一条回调链。 */
    private void sync() {
        boolean run = shouldRun();
        if (run && runStartMs < 0) {
            runStartMs = SystemClock.uptimeMillis();
            Display d = getDisplay();
            float hz = d != null ? d.getRefreshRate() : 60f;
            vsyncDivisor = Math.max(1, (int) (hz / 60f + 0.25f));
            vsyncCount = 0;
            invalidate();
            schedule();
        } else if (!run && runStartMs >= 0) {
            activeMs += SystemClock.uptimeMillis() - runStartMs;
            runStartMs = -1;
            Choreographer.getInstance().removeFrameCallback(this);
            scheduled = false;
        }
    }

    /** Activity 在 onResume/onPause 调用。 */
    public void setResumed(boolean on) {
        resumed = on;
        sync();
    }

    /** 系统动画开关（无障碍“移除动画”、动画缩放为 0、省电模式）。关掉时画面停在当前一帧。 */
    public void setAnimationsAllowed(boolean on) {
        allowed = on;
        sync();
    }

    @Override
    public void onVisibilityAggregated(boolean isVisible) {
        super.onVisibilityAggregated(isVisible);
        visible = isVisible;
        sync();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (staticNode == null && staticBitmap == null && getWidth() > 0) buildStatic(getWidth(), getHeight());
        sync();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        sync(); // 此时已不再 attached，会停下并移除回调
        releaseStatic();
    }
}
