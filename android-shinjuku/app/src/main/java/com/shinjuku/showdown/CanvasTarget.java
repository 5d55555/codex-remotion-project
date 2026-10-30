package com.shinjuku.showdown;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.LongSparseArray;

/**
 * 用 Android Canvas 实现的像素画面：像素层画进一张小位图（全部不抗锯齿、不过滤），present() 时最近邻放大到目标画布。
 * 截图测试用它（和游戏里的 GL 画同一个场景）；GL 初始化失败时也用它兜底。
 */
final class CanvasTarget implements PixelTarget {
    private final Bitmap[] tex = new Bitmap[3];
    private Bitmap hudBitmap;
    private Canvas dest;
    private Bitmap lo;
    private Canvas lc;
    private int vw, vh;
    private float scale;
    private final Paint sp = new Paint();      // 贴图：不过滤
    private final Paint shp = new Paint();     // 几何：不抗锯齿
    private final Paint up = new Paint();      // 放大：不过滤
    private final Paint hp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect src = new Rect();
    private final RectF dst = new RectF();
    private final LongSparseArray<ColorFilter> filters = new LongSparseArray<>();
    private final PorterDuffXfermode add = new PorterDuffXfermode(PorterDuff.Mode.ADD);

    CanvasTarget(Bitmap atlas, Bitmap bg, Bitmap voidBg) {
        tex[TEX_ATLAS] = atlas;
        tex[TEX_BG] = bg;
        tex[TEX_VOID] = voidBg;
        sp.setFilterBitmap(false);
        sp.setAntiAlias(false);
        shp.setAntiAlias(false);
        up.setFilterBitmap(false);
        up.setAntiAlias(false);
    }

    void setHud(Bitmap b) {
        hudBitmap = b;
    }

    /** 测试用：像素层的位图。 */
    Bitmap scene() {
        return lo;
    }

    /** 这一帧画到哪个画布上。 */
    void setDestination(Canvas c) {
        dest = c;
    }

    @Override
    public void begin(int vw, int vh, int sw, int sh, float scale) {
        if (lo == null || lo.getWidth() != vw || lo.getHeight() != vh) {
            if (lo != null) lo.recycle();
            lo = Bitmap.createBitmap(vw, vh, Bitmap.Config.ARGB_8888);
            lc = new Canvas(lo);
        }
        this.vw = vw;
        this.vh = vh;
        this.scale = scale;
        lc.drawColor(0xFF000000, PorterDuff.Mode.SRC);
        additive(false);
    }

    @Override
    public void sprite(int t, int sx, int sy, int w, int h, float x, float y, float pivotX, float pivotY,
                       float deg, float scaleX, float scaleY, int mul, int addColor) {
        lc.save();
        lc.translate(x, y);
        if (deg != 0) lc.rotate(deg);
        if (scaleX != 1 || scaleY != 1) lc.scale(scaleX, scaleY);
        src.set(sx, sy, sx + w, sy + h);
        dst.set(-pivotX, -pivotY, -pivotX + w, -pivotY + h);
        sp.setAlpha(mul >>> 24);
        sp.setColorFilter(filter(mul | 0xFF000000, addColor & 0xFFFFFF));
        lc.drawBitmap(tex[t], src, dst, sp);
        lc.restore();
    }

    private ColorFilter filter(int mul, int addColor) {
        if (mul == 0xFFFFFFFF && addColor == 0) return null;
        long key = ((long) mul << 32) | (addColor & 0xFFFFFFFFL);
        ColorFilter f = filters.get(key);
        if (f == null) {
            f = new LightingColorFilter(mul & 0xFFFFFF, addColor);
            filters.put(key, f);
        }
        return f;
    }

    @Override
    public void rect(float x, float y, float w, float h, int argb) {
        shp.setStyle(Paint.Style.FILL);
        shp.setColor(argb);
        lc.drawRect(x, y, x + w, y + h, shp);
    }

    @Override
    public void shape(float cx, float cy, float rOut, float rIn, float a0, float aLen, int argb) {
        shp.setColor(argb);
        boolean full = aLen >= (float) (Math.PI * 2) - 1e-4f;
        if (rIn <= 0) {
            shp.setStyle(Paint.Style.FILL);
            if (full) lc.drawCircle(cx, cy, rOut, shp);
            else lc.drawArc(cx - rOut, cy - rOut, cx + rOut, cy + rOut, (float) Math.toDegrees(a0), (float) Math.toDegrees(aLen), true, shp);
        } else {
            shp.setStyle(Paint.Style.STROKE);
            shp.setStrokeWidth(Math.max(1, rOut - rIn));
            float r = (rOut + rIn) * 0.5f;
            if (full) lc.drawCircle(cx, cy, r, shp);
            else lc.drawArc(cx - r, cy - r, cx + r, cy + r, (float) Math.toDegrees(a0), (float) Math.toDegrees(aLen), false, shp);
        }
    }

    @Override
    public void line(float x0, float y0, float x1, float y1, float width, int argb) {
        shp.setStyle(Paint.Style.STROKE);
        shp.setStrokeWidth(Math.max(1, width));
        shp.setStrokeCap(Paint.Cap.SQUARE);
        shp.setColor(argb);
        lc.drawLine(x0, y0, x1, y1, shp);
    }

    @Override
    public void additive(boolean on) {
        sp.setXfermode(on ? add : null);
        shp.setXfermode(on ? add : null);
    }

    @Override
    public void present() {
        present(0, 0, vw, vh);
    }

    @Override
    public void present(int x, int y, int w, int h) {
        additive(false);
        src.set(x, y, x + w, y + h);
        dst.set(0, 0, vw * scale, vh * scale);
        dest.drawBitmap(lo, src, dst, up);
    }

    @Override
    public void hud(int sx, int sy, int w, int h, float x, float y, int mul) {
        if (hudBitmap == null) return;
        src.set(sx, sy, sx + w, sy + h);
        dst.set(x, y, x + w, y + h);
        hp.setAlpha(mul >>> 24);
        hp.setColorFilter(filter(mul | 0xFF000000, 0));
        dest.drawBitmap(hudBitmap, src, dst, hp);
    }

    @Override
    public void end() {
    }
}
