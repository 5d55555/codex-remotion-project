package com.shinjuku.showdown;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 主页背景：新宿的夜。
 * 夜空和星星；一蓝一红两道光在半空交锋；地平线上两层楼群（近处那层亮着窗），
 * 正中是都厅双塔的剪影；地面有一层红雾，火星慢慢往上飘。全部用代码画，不用图片。
 */
public class ShinjukuView extends View {

    private static final class Building {
        float x, w, h;
        boolean near;
        List<float[]> windows = new ArrayList<>(); // {x, y, w, h, 随机种子}
    }

    private static final int TOWER = 0xFF1B2050; // 都厅双塔，比远处的楼亮一档

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final List<Building> buildings = new ArrayList<>();
    private float[][] stars = new float[0][];
    private float[][] embers = new float[0][];
    private Shader sky, haze;
    private float dp;

    public ShinjukuView(Context context) {
        this(context, null);
    }

    public ShinjukuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        sky = new LinearGradient(0, 0, 0, h,
                new int[]{0xFF04050D, 0xFF0B0E2C, 0xFF1C0B26, 0xFF2E0A16},
                new float[]{0f, 0.45f, 0.75f, 1f}, Shader.TileMode.CLAMP);
        haze = new LinearGradient(0, h * 0.72f, 0, h,
                new int[]{0x00E0283C, 0x55E0283C, 0x99300810},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);

        Random r = new Random(1999);
        stars = new float[90][];
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new float[]{r.nextFloat() * w, r.nextFloat() * h * 0.62f, (0.6f + r.nextFloat() * 1.4f) * dp, r.nextFloat() * 6.28f};
        }
        embers = new float[28][];
        for (int i = 0; i < embers.length; i++) {
            embers[i] = new float[]{r.nextFloat() * w, r.nextFloat(), (1f + r.nextFloat() * 1.8f) * dp, 0.35f + r.nextFloat() * 0.65f, r.nextFloat() * 6.28f};
        }
        buildSkyline(w, h, r);
    }

    private void buildSkyline(int w, int h, Random r) {
        buildings.clear();
        float ground = h;
        // 远处一层：高低错落，只有轮廓
        for (float x = -10 * dp; x < w; ) {
            Building b = new Building();
            b.x = x;
            b.w = (18 + r.nextFloat() * 34) * dp;
            b.h = h * (0.12f + r.nextFloat() * 0.16f);
            b.near = false;
            buildings.add(b);
            x += b.w + r.nextFloat() * 4 * dp;
        }
        // 近处一层：矮一些，亮着窗
        for (float x = -6 * dp; x < w; ) {
            Building b = new Building();
            b.x = x;
            b.w = (26 + r.nextFloat() * 40) * dp;
            b.h = h * (0.06f + r.nextFloat() * 0.11f);
            b.near = true;
            float ww = 3 * dp, wh = 4 * dp, gap = 4 * dp;
            for (float wy = ground - b.h + 8 * dp; wy < ground - 10 * dp; wy += wh + gap) {
                for (float wx = b.x + 5 * dp; wx < b.x + b.w - 6 * dp; wx += ww + gap) {
                    if (r.nextFloat() < 0.42f) b.windows.add(new float[]{wx, wy, ww, wh, r.nextFloat() * 1000});
                }
            }
            buildings.add(b);
            x += b.w + r.nextFloat() * 3 * dp;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        float t = SystemClock.uptimeMillis() / 1000f;

        // 夜空
        fill.setShader(sky);
        c.drawRect(0, 0, w, h, fill);
        fill.setShader(null);

        // 星星：慢慢闪
        for (float[] s : stars) {
            int a = (int) (90 + 120 * (0.5f + 0.5f * Math.sin(t * 1.3f + s[3])));
            fill.setColor((a << 24) | 0xDDE4FF);
            c.drawCircle(s[0], s[1], s[2], fill);
        }

        drawClash(c, w, h, t);
        drawSkyline(c, w, h, t);

        // 地面的红雾
        fill.setShader(haze);
        c.drawRect(0, h * 0.72f, w, h, fill);
        fill.setShader(null);

        // 火星：从底下往上飘，越高越淡
        for (float[] e : embers) {
            float p = (e[1] + t * 0.05f * e[3]) % 1f;
            float y = h * (1.02f - p * 0.55f);
            float x = e[0] + (float) Math.sin(t * 0.8f + e[4]) * 14 * dp;
            int a = (int) (200 * (1 - p));
            fill.setColor((a << 24) | 0xFF7A3C);
            c.drawCircle(x, y, e[2], fill);
        }

        postInvalidateOnAnimation();
    }

    /** 一蓝一红两道光，从左右两侧斜着撞到标题后方。 */
    private void drawClash(Canvas c, int w, int h, float t) {
        float cx = w * 0.5f, cy = h * 0.27f;
        float pulse = 0.85f + 0.15f * (float) Math.sin(t * 2.2f);
        beam(c, -w * 0.15f, h * 0.10f, cx, cy, 0xFF4C7DFF, pulse);
        beam(c, w * 1.15f, h * 0.06f, cx, cy, 0xFFE0283C, pulse);
        // 交锋点的闪光
        float rr = w * 0.32f * pulse;
        glow.setShader(new RadialGradient(cx, cy, rr, new int[]{0xCCFFFFFF, 0x55B98CFF, 0x00000000}, new float[]{0f, 0.25f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rr, glow);
        glow.setShader(null);
    }

    private void beam(Canvas c, float x0, float y0, float x1, float y1, int color, float pulse) {
        float dx = x1 - x0, dy = y1 - y0;
        float len = (float) Math.hypot(dx, dy);
        float nx = -dy / len, ny = dx / len;
        int rgb = color & 0x00FFFFFF;
        // 外层光晕：几层越来越窄、越来越亮
        float[] widths = {44, 26, 14, 6};
        int[] alphas = {0x22, 0x40, 0x80, 0xE0};
        for (int i = 0; i < widths.length; i++) {
            float hw = widths[i] * dp * pulse / 2;
            path.reset();
            path.moveTo(x0 + nx * hw * 0.3f, y0 + ny * hw * 0.3f);
            path.lineTo(x1 + nx * hw, y1 + ny * hw);
            path.lineTo(x1 - nx * hw, y1 - ny * hw);
            path.lineTo(x0 - nx * hw * 0.3f, y0 - ny * hw * 0.3f);
            path.close();
            glow.setShader(new LinearGradient(x0, y0, x1, y1, rgb, (alphas[i] << 24) | rgb, Shader.TileMode.CLAMP));
            c.drawPath(path, glow);
        }
        glow.setShader(null);
    }

    private void drawSkyline(Canvas c, int w, int h, float t) {
        float ground = h;
        // 远景楼群
        fill.setColor(0xFF0E1130);
        for (Building b : buildings) if (!b.near) c.drawRect(b.x, ground - b.h, b.x + b.w, ground, fill);

        // 都厅双塔：正中最高，顶上两座塔楼
        float cx = w * 0.5f, base = ground, bw = Math.min(w * 0.30f, 150 * dp);
        float body = h * 0.30f, tower = h * 0.38f, tw = bw * 0.36f;
        fill.setColor(TOWER);
        c.drawRect(cx - bw / 2, base - body, cx + bw / 2, base, fill);
        for (int s = -1; s <= 1; s += 2) {
            float tx = cx + s * (bw / 2 - tw / 2);
            path.reset();
            path.moveTo(tx - tw / 2, base - body);
            path.lineTo(tx - tw / 2, base - tower + tw * 0.25f);
            path.lineTo(tx - tw * 0.32f, base - tower);
            path.lineTo(tx + tw * 0.32f, base - tower);
            path.lineTo(tx + tw / 2, base - tower + tw * 0.25f);
            path.lineTo(tx + tw / 2, base - body);
            path.close();
            c.drawPath(path, fill);
            // 塔身上几列竖着的灯带
            fill.setColor(0x55FFCF7A);
            for (int k = -1; k <= 1; k++) {
                float lx = tx + k * tw * 0.26f;
                c.drawRect(lx - 0.8f * dp, base - tower + tw * 0.5f, lx + 0.8f * dp, base - body * 0.55f, fill);
            }
            // 塔顶的红色航空灯，一明一暗
            int a = (int) (120 + 135 * (0.5f + 0.5f * Math.sin(t * 3 + s)));
            fill.setColor((a << 24) | 0xFF3B4E);
            c.drawCircle(tx, base - tower - 3 * dp, 2.2f * dp, fill);
            fill.setColor(TOWER);
        }

        // 近景楼群与灯窗
        for (Building b : buildings) {
            if (!b.near) continue;
            fill.setColor(0xFF07081A);
            c.drawRect(b.x, ground - b.h, b.x + b.w, ground, fill);
            for (float[] win : b.windows) {
                boolean on = ((int) (t * 0.25f + win[4])) % 7 != 0; // 偶尔有一扇灭掉
                if (!on) continue;
                fill.setColor(0xCCFFCF7A);
                c.drawRect(win[0], win[1], win[0] + win[2], win[1] + win[3], fill);
            }
        }
    }
}
