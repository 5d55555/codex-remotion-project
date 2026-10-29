package com.shinjuku.showdown;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * 战斗里用的角色：把 Chibi 的六个部件按屏幕上的实际大小烘焙成小位图，每帧只按骨骼变换贴图。
 * 比每帧画几十条抗锯齿路径省得多（GPU 只是贴十几张小纹理）。受击闪白用预先建好的颜色滤镜。
 * 只在渲染线程上使用；屏幕尺寸变了就重新烘焙。
 */
final class ChibiSprite extends Rig {
    private static final int HURT_LEVELS = 6;

    private final Chibi source;
    private final Bitmap[] bitmaps = new Bitmap[PARTS];
    private final RectF[] dst = new RectF[PARTS];
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final ColorFilter[] hurtFilters = new ColorFilter[HURT_LEVELS + 1];
    private float baked = -1;

    ChibiSprite(Chibi source) {
        this.source = source;
        for (int i = 0; i < PARTS; i++) {
            float[] b = BOUNDS[i];
            dst[i] = new RectF(b[0], b[1], b[2], b[3]);
        }
        for (int k = 1; k <= HURT_LEVELS; k++) {
            int add = 150 * k / HURT_LEVELS;
            hurtFilters[k] = new LightingColorFilter(0xFFFFFF, (add << 16) | (add << 8) | add);
        }
    }

    /** 按每单位 pxPerUnit 像素烘焙（同样大小不重复烘焙）。 */
    void bake(float pxPerUnit) {
        if (Math.abs(pxPerUnit - baked) < 0.01f) return;
        baked = pxPerUnit;
        Rig.Pose rest = new Rig.Pose();
        for (int i = 0; i < PARTS; i++) {
            float[] b = BOUNDS[i];
            int w = Math.max(1, (int) Math.ceil((b[2] - b[0]) * pxPerUnit));
            int h = Math.max(1, (int) Math.ceil((b[3] - b[1]) * pxPerUnit));
            if (bitmaps[i] != null) bitmaps[i].recycle();
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp);
            c.scale(w / (b[2] - b[0]), h / (b[3] - b[1]));
            c.translate(-b[0], -b[1]);
            source.part(c, i, rest);
            bmp.prepareToDraw();
            bitmaps[i] = bmp;
        }
    }

    @Override
    void part(Canvas c, int part, Pose p) {
        int k = Math.round(p.hurt * HURT_LEVELS);
        paint.setColorFilter(k > 0 ? hurtFilters[Math.min(k, HURT_LEVELS)] : null);
        c.drawBitmap(bitmaps[part], null, dst[part], paint);
    }
}
