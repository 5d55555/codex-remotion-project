package com.shinjuku.showdown;

import android.graphics.Bitmap;

import com.shinjuku.showdown.core.Attack;
import com.shinjuku.showdown.core.BlueOrb;
import com.shinjuku.showdown.core.Events;
import com.shinjuku.showdown.core.Fighter;
import com.shinjuku.showdown.core.Hollow;
import com.shinjuku.showdown.core.Shot;
import com.shinjuku.showdown.core.Tuning;
import com.shinjuku.showdown.core.World;

/**
 * 像素风战斗画面：整个战场（背景、角色、特效、界面的方块部分）画在 512 像素宽的像素层上，最近邻放大铺满屏幕；
 * 中文字（名字、难度、按键、招式小字）画在屏幕分辨率层。
 * 只在战斗线程上用；构造之后每帧零分配。不依赖具体后端（GL 或 Canvas），截图测试和游戏画的是同一份代码。
 */
final class PixelScene {
    static final int VW = 512;
    static final float PPU = VW / Tuning.WIDTH;   // 每个世界单位多少虚拟像素
    private static final int BG_FEET = 319;      // 背景图里脚底所在的行（从背景图顶部数）

    // 招式配色（Luna 按动画画面近似取样，见 notes/能力与配色考证.md）
    private static final int BLUE_CORE = 0xFFFFFFFF, BLUE_LIGHT = 0xFFD8F5FF, BLUE_MID = 0xFF8FD4FF, BLUE = 0xFF2679F5, BLUE_DARK = 0xFF1531A8;
    private static final int RED_CORE = 0xFFFFE8D8, RED_LIGHT = 0xFFFF9A62, RED = 0xFFD92837, RED_DARK = 0xFF7A102A;
    private static final int PURPLE_CORE = 0xFFFFFFFF, PURPLE_LIGHT = 0xFFF2C7FF, PURPLE_HOT = 0xFFC77DFF, PURPLE = 0xFF873BEA, PURPLE_DARK = 0xFF35116F;
    private static final int BF_BLACK = 0xFF11131A, BF_RED = 0xFFFF3E3E;
    private static final int VOID_LIGHT = 0xFFCFF4FF, VOID_RING = 0xFF7A5CFF;
    // 宿傩：领域展延的灰白轮廓、魔虚罗法阵的金色、世界斩的红、反转术式的白
    private static final int AMP_LIGHT = 0xFFE9EAF2, AMP_DARK = 0xFF7D8294, WHEEL = 0xFFE0B25A, WHEEL_LIT = 0xFFFFF0B8,
            CUT_RED = 0xFFB0101E, WORLD_RED = 0xFFFF3A4A, RCT_LIGHT = 0xFFE6FFF0;

    private final Atlas atlas;
    private final int bgW, bgH, skyColor;
    private final int voidW, voidH, voidSky; // 无量空处的背景（和新宿同高，脚底在同一行）
    private final Controls controls;
    private final Difficulty difficulty;
    private final Bgm bgm;
    private HudText hud;
    volatile boolean statsOn;

    // 布局
    private Controls.Layout laid;
    private int laidW, laidH;
    private int vh;
    private float s;            // 每个虚拟像素多少屏幕像素
    private float groundY, bgY;
    private final Pose pose = new Pose();
    private float handX, handY, backHandX, backHandY; // 最近画的那个人的前手、后手（虚拟像素）
    private float gojoSX, gojoSY, sukunaSX, sukunaSY;  // 这一帧两人脚底的位置（虚拟像素，含震屏）

    // 粒子
    private static final int P = 512;
    private final float[] pX = new float[P], pY = new float[P], pVX = new float[P], pVY = new float[P];
    private final float[] pLife = new float[P], pAge = new float[P], pG = new float[P], pDrag = new float[P];
    private final int[] pColor = new int[P];
    private final byte[] pSize = new byte[P], pKind = new byte[P]; // kind: 0 方块、1 发光方块（加色）、2 拖尾线（加色）
    private int pNext;

    // 定时特效
    private static final int FX = 48;
    private static final int FX_RING = 1, FX_RING_IN = 2, FX_DISC = 3, FX_BOLTS = 4, FX_SPEED = 5, FX_KO = 6, FX_STAR = 7, FX_CUTS = 8;
    private final int[] fxType = new int[FX], fxColor = new int[FX], fxColor2 = new int[FX];
    private final float[] fxX = new float[FX], fxY = new float[FX], fxT0 = new float[FX], fxDur = new float[FX], fxA = new float[FX], fxB = new float[FX];

    // 伤害数字
    private static final int NUMS = 24;
    private final float[] numX = new float[NUMS], numY = new float[NUMS], numT0 = new float[NUMS];
    private final int[] numVal = new int[NUMS], numKind = new int[NUMS];
    private int numNext;
    private final int[] digits = new int[12];

    // 横幅、闪屏、震屏
    private int bannerBig = -1, bannerSub = -1, bannerColor;
    private float bannerT0 = -99, flashT0 = -99, shakeAmp, shakeT0 = -99;
    private int flashColor;
    private float koT0Gojo = -99, koT0Sukuna = -99;

    // 无量空处：voidK 从 0（新宿）过渡到 1（无量空处），以展开的位置为圆心一圈圈铺开
    private float voidK, voidCx, voidCy;
    private boolean purple200; // 最近一发茈是不是 200%（命中时的特效用）
    // 解：一道一闪而过的细白线，宿傩头上一个“解”字
    private float slashT0 = -99, slashX0, slashY0, slashX1, dismantleT0 = -99;
    // 宿傩：头上小字（领域展延、反转术式、适应完成）出现的时刻、法阵转到的角度、世界斩斩出的那一道
    private float ampT0 = -99, rctT0 = -99, adaptedT0 = -99, wheelAngle, wheelT0 = -99;
    private float worldT0 = -99, worldX0, worldY0, worldX1;

    private float gojoLag = 1, sukunaLag = 1, lastTime;
    private long seenEvents;
    private long rng = 0x2545F4914F6CDD1DL;
    private float fpsAvg = 60, tickMs, drawMs;

    PixelScene(Atlas atlas, Bitmap bg, Bitmap voidBg, Difficulty difficulty, Controls controls, Bgm bgm) {
        this.atlas = atlas;
        bgW = bg.getWidth();
        bgH = bg.getHeight();
        skyColor = bg.getPixel(0, 0);
        voidW = voidBg.getWidth();
        voidH = voidBg.getHeight();
        voidSky = voidBg.getPixel(0, 0);
        this.difficulty = difficulty;
        this.controls = controls;
        this.bgm = bgm;
    }

    /** 当前的屏幕分辨率文字图集（布局变了会换新的；后端据此更新纹理）。 */
    HudText hudText() {
        return hud;
    }

    void reportFrame(long dtNs, float tickMsAvg, float drawMsAvg) {
        if (dtNs > 0) fpsAvg += (1e9f / dtNs - fpsAvg) * 0.05f;
        tickMs = tickMsAvg;
        drawMs = drawMsAvg;
    }

    /** 布局（屏幕尺寸、安全区）变了就重算，返回 true 表示文字图集换了新的。 */
    boolean layout(int screenW, int screenH) {
        Controls.Layout l = controls.layout;
        if (l == null) return false;
        if (l == laid && screenW == laidW && screenH == laidH) return false;
        boolean dpChanged = hud == null || hud.dp != l.dp;
        laid = l;
        laidW = screenW;
        laidH = screenH;
        s = screenW / (float) VW;
        vh = (int) Math.ceil(screenH / s);
        bgY = vh - bgH;
        groundY = bgY + BG_FEET;
        if (dpChanged) hud = new HudText(l.dp, difficulty);
        return dpChanged;
    }

    /* ---------------- 每帧 ---------------- */

    void draw(PixelTarget t, World w, float alpha, float time, int screenW, int screenH) {
        layout(screenW, screenH);
        Controls.Layout l = laid;
        if (l == null) return;
        float dt = Math.max(0, Math.min(0.1f, time - lastTime));
        lastTime = time;
        consumeEvents(w, time);
        updateParticles(dt);

        t.begin(VW, vh, screenW, screenH, s);
        // 震屏：整个战场按整数像素平移
        int sx = 0, sy = 0;
        float st = time - shakeT0;
        if (st < 0.3f) {
            float a = shakeAmp * (1 - st / 0.3f);
            sx = Math.round((float) Math.sin(st * 95) * a);
            sy = Math.round((float) Math.cos(st * 73) * a * 0.6f);
        }
        voidK = w.domainTicks > 0 ? Math.min(1, voidK + dt / 0.7f) : Math.max(0, voidK - dt / 0.5f);
        if (voidK < 1) {
            if (bgY > 0) t.rect(0, 0, VW, bgY + 1, skyColor);
            t.sprite(PixelTarget.TEX_BG, 0, 0, bgW, bgH, Math.round((VW - bgW) * 0.5f) + sx, bgY + sy, 0, 0, 0, 1, 1, 0xFFFFFFFF, 0);
        }
        if (voidK > 0) drawVoid(t, time, sx, sy);

        Fighter g = w.gojo, k = w.sukuna;
        float gx = X(lerp(g.px, g.x, alpha)) + sx, gy = Y(lerp(g.py, g.y, alpha)) + sy;
        float kx = X(lerp(k.px, k.x, alpha)) + sx, ky = Y(lerp(k.py, k.y, alpha)) + sy;
        gojoSX = gx;
        gojoSY = gy;
        sukunaSX = kx;
        sukunaSY = ky;
        // 念 200% 茈的咒：周围一段比一段暗
        if (g.state == Fighter.PURPLE_CHANT) t.rect(0, 0, VW, vh, withAlpha(0xFF0A0418, 0.14f + 0.1f * Math.max(0, w.chantPhase)));
        shadow(t, gx, g.y, sy);
        shadow(t, kx, k.y, sy);

        BlueOrb b = w.blue;
        float bx = X(lerp(b.px, b.x, alpha)) + sx, by = Y(lerp(b.py, b.y, alpha)) + sy;
        if (b.active()) blueBack(t, b, bx, by, time);
        Hollow h = w.hollow;
        if (h.active) hollowBack(t, h, X(h.x) + sx, Y(h.y) + sy, time);

        poseSukuna(w, time);
        if (k.state != Fighter.KO) sukunaBack(t, w, kx, ky, time);
        figure(t, false, kx, ky);
        if (k.state != Fighter.KO) sukunaFront(t, w, kx, ky, time, dt, sx, sy);
        if (w.domainTicks > 0 && k.state != Fighter.KO) overload(t, kx, ky, time);
        poseGojo(w, time);
        figure(t, true, gx, gy);
        handFx(t, w, handX, handY, backHandX, backHandY, time, dt);

        if (b.active()) blueFront(t, b, bx, by, time);
        Shot r = w.red;
        if (r.active) redOrb(t, X(lerp(r.px, r.x, alpha)) + sx, Y(lerp(r.py, r.y, alpha)) + sy, r.dir, r.max, time, dt);
        Shot pu = w.purple;
        if (pu.active) purpleShot(t, X(lerp(pu.px, pu.x, alpha)) + sx, Y(lerp(pu.py, pu.y, alpha)) + sy, pu.dir, pu.max, time, dt);
        if (h.active) hollowFront(t, h, X(h.x) + sx, Y(h.y) + sy, time);
        slash(t, time, sx, sy);
        worldSlash(t, time, sx, sy);
        drawEffects(t, time, sx, sy);
        drawParticles(t, sx, sy);
        drawNumbers(t, time, sx, sy);

        // 全屏闪
        float ft = time - flashT0;
        if (ft < 0.25f) {
            t.additive((flashColor & 0xFFFFFF) != 0);
            t.rect(0, 0, VW, vh, withAlpha(flashColor, (flashColor >>> 24) / 255f * (1 - ft / 0.25f)));
            t.additive(false);
        }
        drawBannerPixels(t, time);
        drawHudPixels(t, w, dt);
        drawControlsPixels(t, w);
        t.present();
        drawHudText(t, w, time);
        t.end();
    }

    private float X(float wx) {
        return wx * PPU;
    }

    private float Y(float wy) {
        return groundY - wy * PPU;
    }

    /* ---------------- 事件 → 特效 ---------------- */

    private void consumeEvents(World w, float time) {
        Events ev = w.events;
        long end = ev.written;
        if (end - seenEvents > Events.CAP) seenEvents = end - Events.CAP;
        for (; seenEvents < end; seenEvents++) {
            int i = (int) (seenEvents & Events.MASK);
            float x = X(ev.x[i]), y = Y(ev.y[i]);
            int kind = ev.kind[i];
            switch (ev.type[i]) {
                case Events.HIT:
                    hitFx(kind, x, y, time);
                    break;
                case Events.BLACK_FLASH:
                    fx(FX_BOLTS, x, y, 0.4f, 0, kind == Attack.BLUE_FIST ? 52 : 38, 0, 0);
                    fx(FX_STAR, x, y, 0.22f, 0.4f, 34, BF_RED, 0);
                    sparks(x, y, 26, BF_RED, BF_BLACK, 170);
                    if (kind == Attack.BLUE_FIST) sparks(x, y, 16, BLUE_LIGHT, BLUE, 150);
                    flash(0x8C000000, time);
                    shake(6, time);
                    banner(Atlas.BIG_BLACK_FLASH, -1, BF_RED, time);
                    break;
                case Events.RED_FIRE:
                    if (kind == 1) { // 最大输出：出手就是一圈冲击
                        fx(FX_DISC, x, y, 0.12f, 12, 0, RED_CORE, 0);
                        fx(FX_RING, x, y, 0.3f, 6, 40, RED_LIGHT, RED);
                        fx(FX_SPEED, x, y, 0.25f, 10, 40, RED_LIGHT, 0);
                        flash(0x66FF5A4A, time);
                        shake(4, time);
                    } else {
                        fx(FX_DISC, x, y, 0.06f, 5, 0, RED_CORE, 0);
                    }
                    break;
                case Events.RED_BURST:
                    if (kind == 1) {
                        float R = Tuning.RED_MAX_RANGE * PPU;
                        fx(FX_RING, x, y, 0.45f, 8, R * 1.5f, RED_LIGHT, RED);
                        fx(FX_RING, x, y, 0.6f, 4, R * 2.4f, RED, RED_DARK);
                        fx(FX_DISC, x, y, 0.16f, 22, 0, RED_CORE, 0);
                        fx(FX_SPEED, x, y, 0.3f, 14, 70, RED_LIGHT, 0);
                        sparks(x, y, 56, RED_LIGHT, RED, 300);
                        flash(0xAAFF3A2A, time);
                        shake(8, time);
                    } else {
                        fx(FX_RING, x, y, 0.32f, 4, Tuning.RED_RANGE * PPU, RED_LIGHT, RED);
                        fx(FX_DISC, x, y, 0.1f, 8, 0, RED_CORE, 0);
                        fx(FX_SPEED, x, y, 0.22f, 8, 34, RED_LIGHT, 0);
                        sparks(x, y, 22, RED_LIGHT, RED, 160);
                        shake(3.5f, time);
                    }
                    break;
                case Events.PURPLE_FIRE:
                    purple200 = kind == 1;
                    if (purple200) {
                        fx(FX_DISC, x, y, 0.18f, 26, 0, PURPLE_CORE, 0);
                        fx(FX_RING, x, y, 0.45f, 10, 90, PURPLE_LIGHT, PURPLE);
                        fx(FX_RING, x, y, 0.7f, 6, 150, PURPLE_HOT, PURPLE_DARK);
                        fx(FX_SPEED, x, y, 0.35f, 16, 80, PURPLE_LIGHT, 0);
                        sparks(x, y, 50, PURPLE_LIGHT, PURPLE_HOT, 280);
                        flash(0xD9F2C7FF, time);
                        shake(9, time);
                    } else {
                        fx(FX_DISC, x, y, 0.1f, 12, 0, PURPLE_CORE, 0);
                        fx(FX_RING, x, y, 0.3f, 6, 40, PURPLE_LIGHT, PURPLE);
                        flash(0x66F2C7FF, time);
                        shake(3, time);
                    }
                    break;
                case Events.HOLLOW_FORM:
                    flash(0xCCF2C7FF, time);
                    shake(7, time);
                    fx(FX_RING, x, y, 0.5f, 2, ev.value[i] * PPU * 1.2f, PURPLE_CORE, PURPLE_LIGHT);
                    sparks(x, y, 40, PURPLE_LIGHT, PURPLE_HOT, 240);
                    banner(Atlas.BIG_PURPLE, HudText.SUB_HOLLOW, 0xFFE3B0FF, time);
                    break;
                case Events.HOLLOW_END:
                    sparks(x, y, 24, PURPLE_HOT, PURPLE_DARK, 120);
                    break;
                case Events.CAST: {
                    int v = Math.round(ev.value[i]);
                    if (kind == Attack.BLUE_FIST) banner(Atlas.BIG_FIST, HudText.SUB_FIST, BLUE_LIGHT, time);
                    else if (kind == Attack.WORLD_SLASH) banner(Atlas.BIG_DISMANTLE, v == 3 ? HudText.SUB_WORLD_BOUND : HudText.SUB_WORLD, WORLD_RED, time);
                    else if (kind == Attack.RED) banner(Atlas.BIG_RED, v == 1 ? HudText.SUB_RED_MAX : HudText.SUB_RED, v == 1 ? 0xFFFF3A2A : 0xFFFF5A4A, time);
                    else if (kind == Attack.PURPLE) banner(Atlas.BIG_PURPLE, v == 2 ? HudText.SUB_PURPLE_200 : HudText.SUB_PURPLE, v == 2 ? 0xFFF7DDFF : 0xFFE3B0FF, time);
                    else if (v > 0) banner(Atlas.BIG_BLUE, HudText.SUB_BLUE_MAX, BLUE_CORE, time);
                    else banner(Atlas.BIG_BLUE, HudText.SUB_BLUE, BLUE_LIGHT, time);
                    break;
                }
                case Events.CHANT: {
                    int phase = Math.round(ev.value[i]);
                    if (phase >= 0) { // 每念一段，身上的紫光往外推一圈
                        fx(FX_RING, x, y, 0.5f, 10, 40 + 14 * phase, PURPLE_LIGHT, PURPLE);
                        sparks(x, y, 10 + 6 * phase, PURPLE_LIGHT, PURPLE_HOT, 90 + 30 * phase);
                        shake(1 + phase, time);
                    } else {          // 被打断：散掉
                        sparks(x, y, 18, PURPLE_HOT, 0xFF8A8FA0, 90);
                        fx(FX_RING, x, y, 0.3f, 14, 4, 0xFF8A8FA0, PURPLE_DARK);
                    }
                    break;
                }
                case Events.DOMAIN_OPEN:
                    voidCx = x;
                    voidCy = y;
                    flash(0xE6FFFFFF, time);
                    shake(4, time);
                    fx(FX_RING, x, y, 0.8f, 6, 160, VOID_LIGHT, VOID_RING);
                    banner(Atlas.BIG_VOID, HudText.SUB_DOMAIN, VOID_LIGHT, time);
                    break;
                case Events.DOMAIN_CLOSE:
                    voidCx = x;
                    voidCy = y;
                    fx(FX_RING_IN, x, y, 0.5f, 1, 120, VOID_LIGHT, 0);
                    break;
                case Events.SLASH:
                    if (kind == Attack.WORLD_SLASH) { // 世界斩：整条斩带一下子劈开
                        worldT0 = time;
                        worldX0 = x;
                        worldY0 = y;
                        worldX1 = X(ev.value[i]);
                        flash(0xCCFFFFFF, time);
                        shake(9, time);
                        for (int k = 0; k < 12; k++) {
                            float px = x + (worldX1 - x) * rnd();
                            sparks(px, y, 3, 0xFFFFFFFF, WORLD_RED, 120);
                        }
                    } else {
                        slashT0 = time;
                        slashX0 = x;
                        slashY0 = y;
                        slashX1 = X(ev.value[i]);
                        dismantleT0 = time;
                    }
                    break;
                case Events.AMP:
                    if (ev.value[i] > 0) {
                        ampT0 = time;
                        fx(FX_RING, x, y, 0.4f, 8, 34, AMP_LIGHT, AMP_DARK);
                        sparks(x, y, 10, AMP_LIGHT, AMP_DARK, 70);
                    } else {
                        fx(FX_RING_IN, x, y, 0.25f, 1, 30, AMP_DARK, 0);
                    }
                    break;
                case Events.ADAPT:
                    wheelT0 = time;
                    if (kind == 1) { // 适应完成：学会了世界斩
                        adaptedT0 = time;
                        flash(0x40FFF0B8, time);
                        fx(FX_RING, x, y, 0.6f, 6, 40, WHEEL_LIT, WHEEL);
                    }
                    break;
                case Events.WORLD_CHANT: {
                    int v = Math.round(ev.value[i]);
                    if (v < 0) {         // 被打断
                        sparks(x, y, 14, CUT_RED, AMP_DARK, 80);
                        fx(FX_RING, x, y, 0.3f, 12, 4, AMP_DARK, CUT_RED);
                    } else if (v >= 1 && v <= 3) {
                        fx(FX_RING, x, y, 0.4f, 8, 28 + 6 * v, WORLD_RED, CUT_RED);
                        shake(1 + 0.5f * v, time);
                    } else if (v == 4) { // 指向：锁定
                        fx(FX_DISC, x, Y(ev.y[i]), 0.12f, 6, 0, 0xFFFFFFFF, 0);
                    }
                    break;
                }
                case Events.RCT:
                    if (ev.value[i] > 0) {
                        rctT0 = time;
                        fx(FX_RING, x, y, 0.5f, 6, 30, RCT_LIGHT, 0xFF9FE0C0);
                    }
                    break;
                case Events.BLUE_LAND:
                    fx(FX_RING_IN, x, y, 0.28f, 1, Tuning.BLUE_RADIUS * (ev.value[i] > 0 ? 2 : 1) * PPU, BLUE_LIGHT, BLUE);
                    break;
                case Events.BLUE_END:
                    fx(FX_RING, x, y, 0.25f, 2, 22, BLUE_LIGHT, BLUE);
                    sparks(x, y, 12, BLUE_LIGHT, BLUE, 80);
                    break;
                case Events.INFINITY_BLOCK:
                    // 无限：攻击停在身前，空间泛起两圈涟漪；解的斩痕停在半空
                    fx(FX_RING, x, y, 0.4f, 6, 18, BLUE_LIGHT, BLUE);
                    fx(FX_RING, x, y, 0.55f, 10, 30, 0xFFFFFFFF, BLUE_MID);
                    if (kind == Attack.DISMANTLE) fx(FX_CUTS, x - 0, y, 0.3f, 0, 9, 0xFFFFFFFF, BLUE_MID);
                    else sparks(x, y, 6, BLUE_LIGHT, 0xFFFFFFFF, 60);
                    break;
                case Events.DAMAGE:
                    number(ev.x[i], ev.y[i], Math.round(ev.value[i]), kind, time);
                    break;
                case Events.KO:
                    if (kind == Fighter.GOJO) koT0Gojo = time;
                    else koT0Sukuna = time;
                    fx(FX_KO, x, y - 34, 1.4f, 0, 0, 0xFFFFE6A8, 0);
                    shake(3, time);
                    break;
                case Events.REVIVE:
                    fx(FX_RING, x, y, 0.4f, 6, 26, 0xFFFFE6A8, 0xFFFF6A5A);
                    break;
                case Events.LAND:
                    dust(x, y, Math.min(1, ev.value[i] / 700f));
                    break;
                default:
                    break;
            }
        }
    }

    /** 打中了：按招式出火花、冲击星、震屏。体术也要有分量（停顿在逻辑里，这里给画面）。 */
    private void hitFx(int kind, float x, float y, float time) {
        switch (kind) {
            case Attack.MELEE:
                sparks(x, y, 16, 0xFFFFF6D0, 0xFFFFC24A, 110);
                fx(FX_DISC, x, y, 0.08f, 7, 0, 0xFFFFFFFF, 0);
                fx(FX_STAR, x, y, 0.16f, rnd() * 0.8f, 20, 0xFFFFF6D0, 0);
                fx(FX_SPEED, x, y, 0.18f, 8, 26, 0xFFFFFFFF, 0);
                fx(FX_RING, x, y, 0.18f, 3, 16, 0xFFFFFFFF, 0xFFFFC24A);
                shake(2.5f, time);
                break;
            case Attack.BLUE_FIST: // 苍拳：先往里一吸，再炸开
                sparks(x, y, 28, BLUE_LIGHT, BLUE, 180);
                fx(FX_RING_IN, x, y, 0.16f, 1, 34, BLUE_LIGHT, 0);
                fx(FX_RING, x, y, 0.32f, 4, 44, BLUE_LIGHT, BLUE);
                fx(FX_DISC, x, y, 0.12f, 11, 0, BLUE_CORE, 0);
                fx(FX_STAR, x, y, 0.22f, rnd() * 0.8f, 32, BLUE_LIGHT, 0);
                flash(0x55D8F5FF, time);
                shake(5, time);
                break;
            case Attack.RED:
                sparks(x, y, 14, RED_LIGHT, RED, 110);
                break;
            case Attack.PURPLE:
                if (purple200) {
                    sparks(x, y, 60, PURPLE_LIGHT, PURPLE_HOT, 300);
                    fx(FX_RING, x, y, 0.6f, 8, 110, PURPLE_CORE, PURPLE);
                    fx(FX_STAR, x, y, 0.35f, 0.3f, 70, PURPLE_LIGHT, 0);
                    flash(0xE6FFFFFF, time);
                    shake(11, time);
                } else {
                    sparks(x, y, 24, PURPLE_LIGHT, PURPLE_HOT, 150);
                    fx(FX_RING, x, y, 0.4f, 6, 46, PURPLE_LIGHT, PURPLE);
                    flash(0x99C77DFF, time);
                    shake(5, time);
                }
                break;
            case Attack.HOLLOW:
                sparks(x, y, 14, PURPLE_LIGHT, PURPLE, 120);
                break;
            case Attack.S_MELEE:
                sparks(x, y, 12, 0xFFFFD0C8, 0xFFE0303F, 100);
                fx(FX_STAR, x, y, 0.16f, rnd(), 18, 0xFFFFD0C8, 0);
                shake(2.5f, time);
                break;
            case Attack.DISMANTLE:
                fx(FX_CUTS, x, y, 0.35f, 0, 16, 0xFFFFFFFF, 0xFFB0101E);
                shake(2, time);
                break;
            case Attack.WORLD_SLASH: // 连空间一起被斩开
                fx(FX_CUTS, x, y, 0.6f, 0, 30, 0xFFFFFFFF, CUT_RED);
                fx(FX_STAR, x, y, 0.3f, 0, 40, WORLD_RED, 0);
                sparks(x, y, 30, 0xFFFFFFFF, WORLD_RED, 220);
                flash(0x99FF2A3A, time);
                shake(11, time);
                break;
            default:
                break;
        }
    }

    private void fx(int type, float x, float y, float dur, float a, float b, int c1, int c2) {
        int slot = 0;
        float oldest = Float.MAX_VALUE;
        for (int i = 0; i < FX; i++) {
            if (fxType[i] == 0) {
                slot = i;
                break;
            }
            if (fxT0[i] < oldest) {
                oldest = fxT0[i];
                slot = i;
            }
        }
        fxType[slot] = type;
        fxX[slot] = x;
        fxY[slot] = y;
        fxT0[slot] = lastTime;
        fxDur[slot] = dur;
        fxA[slot] = a;
        fxB[slot] = b;
        fxColor[slot] = c1;
        fxColor2[slot] = c2;
    }

    private void number(float wx, float wy, int v, int kind, float time) {
        int i = numNext;
        numNext = (numNext + 1) % NUMS;
        numX[i] = wx;
        numY[i] = wy;
        numT0[i] = time;
        numVal[i] = v;
        numKind[i] = kind;
    }

    private void banner(int big, int sub, int color, float time) {
        bannerBig = big;
        bannerSub = sub;
        bannerColor = color;
        bannerT0 = time;
    }

    private void flash(int color, float time) {
        flashColor = color;
        flashT0 = time;
    }

    private void shake(float px, float time) {
        float cur = time - shakeT0 < 0.3f ? shakeAmp * (1 - (time - shakeT0) / 0.3f) : 0;
        if (px < cur) return;
        shakeAmp = px;
        shakeT0 = time;
    }

    /* ---------------- 粒子 ---------------- */

    private void emit(float x, float y, float vx, float vy, float life, int color, int size, float g, float drag, int kind) {
        int i = pNext;
        pNext = (pNext + 1) & (P - 1);
        pX[i] = x;
        pY[i] = y;
        pVX[i] = vx;
        pVY[i] = vy;
        pLife[i] = life;
        pAge[i] = 0;
        pColor[i] = color;
        pSize[i] = (byte) size;
        pG[i] = g;
        pDrag[i] = drag;
        pKind[i] = (byte) kind;
    }

    private void sparks(float x, float y, int n, int c1, int c2, float speed) {
        for (int i = 0; i < n; i++) {
            float a = rnd() * 6.2832f, v = speed * (0.35f + 0.65f * rnd());
            emit(x, y, (float) Math.cos(a) * v, (float) Math.sin(a) * v - speed * 0.2f, 0.25f + 0.3f * rnd(),
                    rnd() < 0.5f ? c1 : c2, rnd() < 0.3f ? 2 : 1, 260, 2.5f, i % 3 == 0 ? 2 : 0);
        }
    }

    private void dust(float x, float y, float k) {
        int n = 4 + (int) (6 * k);
        for (int i = 0; i < n; i++) {
            float dir = i % 2 == 0 ? 1 : -1;
            emit(x + dir * 3, y - 1, dir * (25 + 40 * rnd()) * (0.5f + k), -8 - 14 * rnd(), 0.35f + 0.2f * rnd(),
                    rnd() < 0.5f ? 0xCCD9DCE6 : 0xCCAEB4C2, 2, 30, 3, 0);
        }
    }

    private void updateParticles(float dt) {
        if (dt <= 0) return;
        for (int i = 0; i < P; i++) {
            if (pLife[i] <= 0) continue;
            pAge[i] += dt;
            if (pAge[i] >= pLife[i]) {
                pLife[i] = 0;
                continue;
            }
            float d = 1 - Math.min(1, pDrag[i] * dt);
            pVX[i] *= d;
            pVY[i] = pVY[i] * d + pG[i] * dt;
            pX[i] += pVX[i] * dt;
            pY[i] += pVY[i] * dt;
        }
    }

    private void drawParticles(PixelTarget t, int sx, int sy) {
        for (int pass = 0; pass < 2; pass++) {
            t.additive(pass == 1);
            for (int i = 0; i < P; i++) {
                if (pLife[i] <= 0 || (pKind[i] != 0) != (pass == 1)) continue;
                float k = 1 - pAge[i] / pLife[i];
                int c = withAlpha(pColor[i], ((pColor[i] >>> 24) / 255f) * (k < 0.3f ? k / 0.3f : 1));
                float x = Math.round(pX[i]) + sx, y = Math.round(pY[i]) + sy;
                if (pKind[i] == 2) {
                    t.line(x, y, x - pVX[i] * 0.03f, y - pVY[i] * 0.03f, 1, c);
                } else {
                    int sz = pSize[i];
                    t.rect(x, y, sz, sz, c);
                }
            }
        }
        t.additive(false);
    }

    private float rnd() {
        rng ^= rng << 13;
        rng ^= rng >>> 7;
        rng ^= rng << 17;
        return (rng >>> 40) / (float) (1L << 24);
    }

    /* ---------------- 场景 ---------------- */

    private void shadow(PixelTarget t, float x, float wy, int sy) {
        float k = 1 - Math.min(0.7f, wy / 260f);
        int w = Math.round(14 * k), hh = Math.max(1, Math.round(3 * k));
        t.rect(Math.round(x) - w, groundY + sy - hh + 1, w * 2, hh, 0x55101018);
        t.rect(Math.round(x) - w + 2, groundY + sy - hh, w * 2 - 4, 1, 0x55101018);
    }

    /** 苍：作用范围的点圈、向心的光点、分档的光晕（画在人后面）。 */
    private void blueBack(PixelTarget t, BlueOrb b, float cx, float cy, float time) {
        cx = Math.round(cx);
        cy = Math.round(cy);
        if (b.phase == BlueOrb.FLY) {
            float r = 3 + 3 * Math.min(1, b.tick / (float) Tuning.BLUE_FLY);
            t.additive(true);
            t.line(X(b.ox), Y(b.oy), cx, cy, 1, withAlpha(BLUE_MID, 0.5f));
            t.shape(cx, cy, r * 2.4f, 0, 0, 7, withAlpha(BLUE, 0.35f));
            t.additive(false);
            t.shape(cx, cy, r, 0, 0, 7, BLUE_LIGHT);
            t.shape(cx, cy, r * 0.5f, 0, 0, 7, BLUE_CORE);
            return;
        }
        float grow = blueGrow(b);
        float R = b.radius * PPU;
        // 作用范围：一圈慢慢转的点
        int dots = b.charged ? 72 : 48;
        for (int i = 0; i < dots; i++) {
            double a = i * Math.PI * 2 / dots + time * 0.4;
            if ((i & 1) == 1) continue;
            t.rect(Math.round(cx + (float) Math.cos(a) * R), Math.round(cy + (float) Math.sin(a) * R), 1, 1, withAlpha(BLUE_LIGHT, 0.45f * grow));
        }
        // 往中心吸的光点：螺旋着进去
        t.additive(true);
        int motes = b.charged ? 40 : 26;
        for (int i = 0; i < motes; i++) {
            float ph = (i * 0.6180339f + time * (b.charged ? 0.85f : 0.65f)) % 1f;
            float rr = R * (1 - ph) * (1 - ph) * grow + 3;
            double a = i * 2.39996 + (1 - ph) * 3.2;
            float x = cx + (float) Math.cos(a) * rr, y = cy + (float) Math.sin(a) * rr;
            float x2 = cx + (float) Math.cos(a - 0.25) * (rr + 6), y2 = cy + (float) Math.sin(a - 0.25) * (rr + 6);
            int c = ph > 0.7f ? BLUE_CORE : ph > 0.35f ? BLUE_LIGHT : BLUE_MID;
            t.line(Math.round(x2), Math.round(y2), Math.round(x), Math.round(y), 1, withAlpha(c, 0.35f + 0.6f * ph));
        }
        // 分档光晕（像素风：一圈一圈，不用渐变）
        float core = coreR(b) * grow;
        t.shape(cx, cy, core * 3.6f, 0, 0, 7, withAlpha(BLUE_DARK, 0.28f));
        t.shape(cx, cy, core * 2.6f, 0, 0, 7, withAlpha(BLUE, 0.32f));
        t.shape(cx, cy, core * 1.8f, 0, 0, 7, withAlpha(BLUE_MID, 0.38f));
        t.additive(false);
    }

    private static float blueGrow(BlueOrb b) {
        return Math.min(1, b.tick / 8f) * Math.min(1, (Tuning.BLUE_LIFE - b.tick) / 14f + 0.15f);
    }

    private static float coreR(BlueOrb b) {
        return b.charged ? 11 : 7;
    }

    /** 苍：白色的核心、浅蓝的外圈、绕着转的旋臂（画在人前面，被吸进去的人像是被卷在里面）。 */
    private void blueFront(PixelTarget t, BlueOrb b, float cx, float cy, float time) {
        if (b.phase != BlueOrb.PULL) return;
        cx = Math.round(cx);
        cy = Math.round(cy);
        float grow = blueGrow(b);
        float pulse = (float) Math.sin(time * 18) > 0 ? 1 : 0;
        float core = coreR(b) * grow + pulse;
        t.shape(cx, cy, core + 2, core, 0, 7, BLUE_MID);
        t.shape(cx, cy, core, 0, 0, 7, BLUE_LIGHT);
        t.shape(cx, cy, core * 0.62f, 0, 0, 7, BLUE_CORE);
        // 旋臂：三条短弧绕核心转
        t.additive(true);
        for (int i = 0; i < 3; i++) {
            float rr = core * (1.5f + 0.35f * i);
            float a0 = time * (7 - i * 1.5f) + i * 2.09f;
            t.shape(cx, cy, rr + 1, rr, a0, 1.3f - i * 0.2f, withAlpha(i == 0 ? BLUE_CORE : BLUE_LIGHT, 0.85f));
        }
        if (b.charged) {
            // 出力最大：外面再加一圈旋转的光线
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + time * 1.7;
                float r0 = core * 2.2f, r1 = core * (3.4f + 0.6f * (float) Math.sin(time * 9 + i));
                t.line(cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0, cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, 1, withAlpha(BLUE_LIGHT, 0.55f));
            }
        }
        t.additive(false);
        float life = b.tick / (float) Tuning.BLUE_LIFE;
        if (life > 0.9f) t.shape(cx, cy, core * 1.3f, 0, 0, 7, withAlpha(BLUE_CORE, (life - 0.9f) * 8));
    }

    /** 赫：红色的球（亮部偏橙，核心近白），往后甩火星。最大输出：球大将近三倍，拖着冲击的尾迹，外圈有电弧。 */
    private void redOrb(PixelTarget t, float x, float y, float dir, boolean max, float time, float dt) {
        x = Math.round(x);
        y = Math.round(y);
        float k = max ? Tuning.RED_MAX_ORB_R * PPU / 7f : 1; // 普通赫的球半径 7 像素
        boolean flick = ((int) (time * 30) & 1) == 0;
        t.additive(true);
        if (max) {
            for (int i = 1; i <= 7; i++) t.shape(x - dir * i * 10, y, 12 * k * (1 - i * 0.1f), 0, 0, 7, withAlpha(RED_DARK, 0.5f - i * 0.06f));
        }
        t.shape(x, y, 13 * k, 0, 0, 7, withAlpha(RED_DARK, 0.45f));
        t.shape(x, y, 10 * k, 0, 0, 7, withAlpha(RED, 0.5f));
        t.additive(false);
        t.shape(x, y, 7 * k + (flick ? 1 : 0), 0, 0, 7, RED);
        t.shape(x, y, 5 * k, 0, 0, 7, RED_LIGHT);
        t.shape(x, y, 2.5f * k, 0, 0, 7, RED_CORE);
        if (max) crackle(t, x, y, 9 * k, (int) (time * 24), RED_LIGHT, 6);
        if (dt > 0) {
            int n = max ? 6 : 2;
            for (int i = 0; i < n; i++) {
                emit(x + (rnd() - 0.5f) * 8 * k, y + (rnd() - 0.5f) * 8 * k, (rnd() - 0.5f) * 60 * k - dir * (max ? 120 : 0), (rnd() - 0.5f) * 60 * k - 20,
                        0.2f + 0.15f * rnd(), rnd() < 0.5f ? RED_LIGHT : RED, max && i % 3 == 0 ? 2 : 1, -40, 2, 1);
            }
        }
    }

    /** 茈：紫色大球，外圈噼啪的电弧，身后一道被抹掉的拖痕。200%：球更大，拖痕更长，电弧更密，飞行中一直震屏。 */
    private void purpleShot(PixelTarget t, float x, float y, float dir, boolean max, float time, float dt) {
        x = Math.round(x);
        y = Math.round(y);
        float R = (max ? Tuning.PURPLE_200_R : Tuning.PURPLE_R) * PPU;
        int trail = max ? 10 : 6;
        t.additive(true);
        // 拖痕：身后一条渐隐的紫带
        for (int i = 1; i <= trail; i++) {
            float tx = x - dir * i * (max ? 9 : 7);
            t.shape(tx, y, R * (1 - i * (0.72f / trail)), 0, 0, 7, withAlpha(PURPLE_DARK, 0.5f - i * (0.42f / trail)));
        }
        t.shape(x, y, R * 1.5f, 0, 0, 7, withAlpha(PURPLE_DARK, 0.5f));
        t.shape(x, y, R * 1.2f, 0, 0, 7, withAlpha(PURPLE, 0.45f));
        if (max) t.shape(x, y, R * 1.9f, R * 1.75f, time * 3, 7, withAlpha(PURPLE_LIGHT, 0.35f + 0.25f * (float) Math.sin(time * 40)));
        t.additive(false);
        t.shape(x, y, R, 0, 0, 7, PURPLE);
        t.shape(x, y, R * 0.75f, 0, 0, 7, PURPLE_HOT);
        t.shape(x, y, R * 0.48f, 0, 0, 7, PURPLE_LIGHT);
        t.shape(x, y, R * 0.24f, 0, 0, 7, PURPLE_CORE);
        crackle(t, x, y, R * 1.15f, (int) (time * 20), PURPLE_LIGHT, max ? 10 : 4);
        if (max) {
            crackle(t, x, y, R * 1.6f, (int) (time * 26) + 7, PURPLE_CORE, 5);
            shake(2, time);
        }
        if (dt > 0) {
            int n = max ? 8 : 3;
            for (int i = 0; i < n; i++) {
                float a = rnd() * 6.2832f;
                emit(x + (float) Math.cos(a) * R, y + (float) Math.sin(a) * R, -dir * (40 + 80 * rnd()) * (max ? 1.8f : 1), (rnd() - 0.5f) * (max ? 120 : 50), 0.3f,
                        rnd() < 0.5f ? PURPLE_HOT : PURPLE_LIGHT, max && i % 2 == 0 ? 2 : 1, 0, 1.5f, 1);
            }
        }
    }

    /** 无指向茈：紫色的球向四周扩张（画在人后面的部分）。 */
    private void hollowBack(PixelTarget t, Hollow h, float x, float y, float time) {
        x = Math.round(x);
        y = Math.round(y);
        float R = h.radius * PPU;
        float life = h.tick / (float) Tuning.HOLLOW_LIFE;
        float a = life < 0.7f ? 1 : (1 - life) / 0.3f;
        t.additive(true);
        t.shape(x, y, R * 1.12f, 0, 0, 7, withAlpha(PURPLE_DARK, 0.45f * a));
        t.shape(x, y, R, 0, 0, 7, withAlpha(PURPLE, 0.4f * a));
        t.additive(false);
        t.shape(x, y, R * 0.8f, 0, 0, 7, withAlpha(PURPLE_DARK, 0.55f * a));
        // 内部翻滚的暗紫弧
        for (int i = 0; i < 5; i++) {
            float rr = R * (0.25f + 0.13f * i);
            t.shape(x, y, rr + 2, rr, time * (4 + i) * (i % 2 == 0 ? 1 : -1) + i, 2.2f, withAlpha(PURPLE, 0.8f * a));
        }
    }

    /** 无指向茈：亮边、白核、外圈电弧（画在人前面，半透明，人还看得见）。 */
    private void hollowFront(PixelTarget t, Hollow h, float x, float y, float time) {
        x = Math.round(x);
        y = Math.round(y);
        float R = h.radius * PPU;
        float life = h.tick / (float) Tuning.HOLLOW_LIFE;
        float a = life < 0.7f ? 1 : (1 - life) / 0.3f;
        t.additive(true);
        t.shape(x, y, R, R - 3, 0, 7, withAlpha(PURPLE_LIGHT, 0.9f * a));
        t.shape(x, y, R - 3, R - 6, 0, 7, withAlpha(PURPLE_HOT, 0.6f * a));
        t.shape(x, y, R * 0.3f, 0, 0, 7, withAlpha(PURPLE_LIGHT, 0.7f * a));
        t.shape(x, y, R * 0.16f, 0, 0, 7, withAlpha(PURPLE_CORE, a));
        t.additive(false);
        if (a > 0.3f) crackle(t, x, y, R, (int) (time * 18), PURPLE_LIGHT, 6);
    }

    /** 一圈噼啪的电弧：每根三段折线，形状每 1/20 秒换一次。 */
    private void crackle(PixelTarget t, float x, float y, float R, int seed, int color, int n) {
        long z = seed * 0x9E3779B97F4A7C15L;
        t.additive(true);
        for (int i = 0; i < n; i++) {
            z = mix(z);
            double a = unit(z) * Math.PI * 2;
            float px0 = x + (float) Math.cos(a) * R * 0.85f, py0 = y + (float) Math.sin(a) * R * 0.85f;
            for (int sgm = 0; sgm < 3; sgm++) {
                z = mix(z);
                double aa = a + (unit(z) - 0.5) * 1.2;
                float len = R * 0.18f;
                float px1 = px0 + (float) Math.cos(aa) * len, py1 = py0 + (float) Math.sin(aa) * len;
                t.line(Math.round(px0), Math.round(py0), Math.round(px1), Math.round(py1), 1, withAlpha(color, 0.9f));
                px0 = px1;
                py0 = py1;
            }
        }
        t.additive(false);
    }

    /**
     * 手上的术式：蓄苍时的蓝光、苍拳拳头上的苍、赫的红光（最大输出越蓄越大）、茈前摇时苍和赫在手前相互绕着转然后合在一起、
     * 念 200% 茈时两只手上各托着苍和赫。
     */
    private void handFx(PixelTarget t, World w, float hx, float hy, float bhx, float bhy, float time, float dt) {
        Fighter g = w.gojo;
        hx = Math.round(hx);
        hy = Math.round(hy);
        int st = g.stateTick;
        switch (g.state) {
            case Fighter.MELEE:
                if (st > Tuning.MELEE_STARTUP) { // 还按着：苍开始往拳头上聚
                    float k = Math.min(1, (st - Tuning.MELEE_STARTUP) / (float) (Tuning.FIST_HOLD - Tuning.MELEE_STARTUP));
                    fistBlue(t, hx, hy, 1.5f + 2.5f * k, k, time, dt);
                }
                break;
            case Fighter.MELEE_ACTIVE: { // 出拳的一瞬：拳头前一点白光、身后两道速度线
                t.additive(true);
                t.shape(hx, hy, 3, 0, 0, 7, withAlpha(0xFFFFFFFF, 0.8f));
                float f = g.facing;
                t.line(hx - f * 4, hy - 2, hx - f * 16, hy - 2, 1, withAlpha(0xFFFFFFFF, 0.6f));
                t.line(hx - f * 6, hy + 2, hx - f * 13, hy + 2, 1, withAlpha(0xFFFFFFFF, 0.45f));
                t.additive(false);
                break;
            }
            case Fighter.FIST_WINDUP:
                fistBlue(t, hx, hy, 4 + 3 * Math.min(1, st / (float) Tuning.FIST_WINDUP), 1, time, dt);
                break;
            case Fighter.FIST_ACTIVE: {
                fistBlue(t, hx, hy, 7, 1, time, dt);
                float f = g.facing;
                t.additive(true);
                for (int i = 1; i <= 4; i++) t.shape(hx - f * i * 6, hy, 6 - i, 0, 0, 7, withAlpha(BLUE_MID, 0.5f - i * 0.1f));
                t.additive(false);
                break;
            }
            case Fighter.FIST_RECOVER:
                if (st < 12) fistBlue(t, hx, hy, 7 * (1 - st / 12f), 1 - st / 12f, time, 0);
                break;
            case Fighter.BLUE_HOLD:
            case Fighter.BLUE_WINDUP: {
                boolean charged = g.state == Fighter.BLUE_WINDUP && w.pendingCharged();
                float k = g.state == Fighter.BLUE_HOLD ? 0.4f + 0.6f * Math.min(1, st / (float) Tuning.BLUE_HOLD_THRESHOLD) : 1;
                float r = (charged ? 4 + 4 * Math.min(1, st / (float) Tuning.BLUE_WINDUP_CHARGED) : 3) * k;
                t.additive(true);
                t.shape(hx, hy, r * 2.5f, 0, 0, 7, withAlpha(BLUE, 0.4f));
                t.additive(false);
                t.shape(hx, hy, r, 0, 0, 7, BLUE_LIGHT);
                t.shape(hx, hy, r * 0.5f, 0, 0, 7, BLUE_CORE);
                if (dt > 0 && (charged || rnd() < 0.5f)) {
                    float a = rnd() * 6.2832f, d = 18 + 10 * rnd();
                    emit(hx + (float) Math.cos(a) * d, hy + (float) Math.sin(a) * d, -(float) Math.cos(a) * d * 4, -(float) Math.sin(a) * d * 4,
                            0.22f, BLUE_LIGHT, 1, 0, 0, 2);
                }
                break;
            }
            case Fighter.RED_HOLD: {
                float r = 1 + 4 * Math.min(1, st / (float) Tuning.RED_WINDUP);
                if (st > Tuning.RED_WINDUP) r += 3 * Math.min(1, (st - Tuning.RED_WINDUP) / (float) (Tuning.RED_HOLD - Tuning.RED_WINDUP)); // 还按着：往最大输出蓄
                redHand(t, hx, hy, r, false, time, dt);
                break;
            }
            case Fighter.RED_WINDUP: // 最大输出的前摇：红球越来越大，往外推出一圈圈的斥力
                redHand(t, hx, hy, 8 + 7 * Math.min(1, st / (float) Tuning.RED_MAX_WINDUP), true, time, dt);
                break;
            case Fighter.PURPLE_WINDUP: {
                float k = Math.min(1, st / (float) Tuning.PURPLE_WINDUP);
                float d = 16 * (1 - k * k) + 1, a = time * (6 + 14 * k);
                float bxx = hx + (float) Math.cos(a) * d, byy = hy + (float) Math.sin(a) * d * 0.6f;
                float rxx = hx - (float) Math.cos(a) * d, ryy = hy - (float) Math.sin(a) * d * 0.6f;
                t.additive(true);
                t.shape(bxx, byy, 7, 0, 0, 7, withAlpha(BLUE, 0.4f));
                t.shape(rxx, ryy, 7, 0, 0, 7, withAlpha(RED, 0.4f));
                t.shape(hx, hy, 4 + 12 * k, 0, 0, 7, withAlpha(PURPLE, 0.35f * k));
                t.additive(false);
                t.shape(Math.round(bxx), Math.round(byy), 3, 0, 0, 7, BLUE_LIGHT);
                t.shape(Math.round(rxx), Math.round(ryy), 3, 0, 0, 7, RED_LIGHT);
                if (k > 0.6f) {
                    t.shape(hx, hy, 2 + 6 * (k - 0.6f) / 0.4f, 0, 0, 7, PURPLE_LIGHT);
                    crackle(t, hx, hy, 8 + 10 * k, (int) (time * 24), PURPLE_LIGHT, 3);
                }
                break;
            }
            case Fighter.PURPLE_CHANT: { // 念咒：前手托苍，后手托赫，一段比一段大；全身裹着紫光
                int phase = Math.max(0, w.chantPhase);
                float k = Math.min(1, st / (float) (Tuning.CHANT_PHRASES * Tuning.CHANT_PHRASE));
                float r = 3 + 4 * k;
                bhx = Math.round(bhx);
                bhy = Math.round(bhy);
                t.additive(true);
                t.shape(hx, hy, r * 2.4f, 0, 0, 7, withAlpha(BLUE, 0.45f));
                t.shape(bhx, bhy, r * 2.4f, 0, 0, 7, withAlpha(RED, 0.45f));
                float cx = (hx + bhx) * 0.5f, cy = gojoSY - 26;
                float pulse = 0.5f + 0.5f * (float) Math.sin(time * (6 + 4 * phase));
                t.shape(Math.round(cx), Math.round(cy), 24 + 6 * phase + 2 * pulse, 0, 0, 7, withAlpha(PURPLE_DARK, 0.35f + 0.08f * phase));
                t.shape(Math.round(cx), Math.round(cy), 18 + 5 * phase, 14 + 5 * phase, time * 2, 7, withAlpha(PURPLE_HOT, 0.25f + 0.1f * pulse));
                t.additive(false);
                t.shape(hx, hy, r, 0, 0, 7, BLUE_LIGHT);
                t.shape(hx, hy, r * 0.5f, 0, 0, 7, BLUE_CORE);
                t.shape(bhx, bhy, r, 0, 0, 7, RED_LIGHT);
                t.shape(bhx, bhy, r * 0.45f, 0, 0, 7, RED_CORE);
                if (phase >= 2) crackle(t, cx, cy, 20 + 6 * phase, (int) (time * 22), PURPLE_LIGHT, 2 + 2 * phase);
                if (dt > 0) { // 紫光往上飘
                    for (int i = 0; i < 1 + phase; i++) {
                        emit(cx + (rnd() - 0.5f) * 30, gojoSY - rnd() * 6, (rnd() - 0.5f) * 20, -40 - 60 * rnd(), 0.5f + 0.3f * rnd(),
                                rnd() < 0.5f ? PURPLE_HOT : PURPLE_LIGHT, 1, -30, 1, 1);
                    }
                }
                break;
            }
            case Fighter.DOMAIN_CAST:
                t.additive(true);
                t.shape(hx, hy, 4, 0, 0, 7, withAlpha(VOID_LIGHT, 0.8f));
                t.additive(false);
                break;
            default:
                break;
        }
    }

    /** 苍附在拳头上：白核、浅蓝外圈、绕着转的一道弧，周围的光点往拳头里吸。 */
    private void fistBlue(PixelTarget t, float hx, float hy, float r, float k, float time, float dt) {
        t.additive(true);
        t.shape(hx, hy, r * 2.3f, 0, 0, 7, withAlpha(BLUE, 0.45f * k));
        t.shape(hx, hy, r * 1.6f, r * 1.3f, time * 12, 2.2f, withAlpha(BLUE_LIGHT, 0.8f * k));
        t.additive(false);
        t.shape(hx, hy, r, 0, 0, 7, BLUE_LIGHT);
        t.shape(hx, hy, r * 0.55f, 0, 0, 7, BLUE_CORE);
        if (dt > 0 && rnd() < 0.3f + 0.7f * k) {
            float a = rnd() * 6.2832f, d = 12 + 10 * rnd();
            emit(hx + (float) Math.cos(a) * d, hy + (float) Math.sin(a) * d, -(float) Math.cos(a) * d * 5, -(float) Math.sin(a) * d * 5,
                    0.18f, rnd() < 0.5f ? BLUE_LIGHT : BLUE_CORE, 1, 0, 0, 2);
        }
    }

    /** 手指上的赫；max 时外圈一圈圈往外推（斥力），火星往外甩。 */
    private void redHand(PixelTarget t, float hx, float hy, float r, boolean max, float time, float dt) {
        t.additive(true);
        t.shape(hx, hy, r * 2.4f, 0, 0, 7, withAlpha(RED, 0.45f));
        if (max) {
            for (int i = 0; i < 3; i++) {
                float ph = (time * 2.5f + i / 3f) % 1f, rr = r * (1.4f + 2.2f * ph);
                t.shape(hx, hy, rr + 1, rr, 0, 7, withAlpha(RED_LIGHT, 0.7f * (1 - ph)));
            }
        }
        t.additive(false);
        t.shape(hx, hy, r, 0, 0, 7, RED_LIGHT);
        t.shape(hx, hy, r * 0.45f, 0, 0, 7, RED_CORE);
        if (max) {
            crackle(t, hx, hy, r * 1.3f, (int) (time * 24), RED_LIGHT, 4);
            if (dt > 0) {
                for (int i = 0; i < 3; i++) {
                    float a = rnd() * 6.2832f;
                    emit(hx + (float) Math.cos(a) * r, hy + (float) Math.sin(a) * r, (float) Math.cos(a) * 160, (float) Math.sin(a) * 160,
                            0.25f, rnd() < 0.5f ? RED_LIGHT : RED, 1, 0, 3, 2);
                }
            }
        }
    }

    /** 无量空处：以展开的位置为圆心，按行把宇宙背景铺开（每行一条贴图，像素对齐，两种后端结果一样）；铺满后星星一闪一闪。 */
    private void drawVoid(PixelTarget t, float time, int sx, int sy) {
        int left = Math.round((VW - voidW) * 0.5f) + sx, top = Math.round(bgY) + sy;
        if (voidK >= 1) {
            if (top > 0) t.rect(0, 0, VW, top + 1, voidSky);
            t.sprite(PixelTarget.TEX_VOID, 0, 0, voidW, voidH, left, top, 0, 0, 0, 1, 1, 0xFFFFFFFF, 0);
        } else {
            float e = voidK * voidK * (3 - 2 * voidK);
            float cx = Math.round(voidCx) + sx, cy = Math.round(voidCy) + sy;
            float fx = Math.max(cx, VW - cx), fy = Math.max(cy, vh - cy);
            float R = e * (float) Math.sqrt(fx * fx + fy * fy);
            int y0 = Math.max(0, (int) Math.floor(cy - R)), y1 = Math.min(vh, (int) Math.ceil(cy + R));
            for (int y = y0; y < y1; y++) {
                float dy = y + 0.5f - cy, hw2 = R * R - dy * dy;
                if (hw2 <= 0) continue;
                float hw = (float) Math.sqrt(hw2);
                int xa = Math.max(Math.max(0, left), Math.round(cx - hw)), xb = Math.min(Math.min(VW, left + voidW), Math.round(cx + hw));
                if (xb <= xa) continue;
                int ty = y - top;
                if (ty < 0) t.rect(xa, y, xb - xa, 1, voidSky);
                else if (ty < voidH) t.sprite(PixelTarget.TEX_VOID, xa - left, ty, xb - xa, 1, xa, y, 0, 0, 0, 1, 1, 0xFFFFFFFF, 0);
            }
            // 铺开的边缘：一圈亮环
            t.additive(true);
            t.shape(cx, cy, R + 3, R, 0, 7, withAlpha(VOID_LIGHT, 0.85f * (1 - e * 0.6f)));
            t.shape(cx, cy, R + 7, R + 3, 0, 7, withAlpha(VOID_RING, 0.5f * (1 - e * 0.6f)));
            t.additive(false);
        }
        // 星星：固定的位置，各自按自己的节奏闪
        t.additive(true);
        float horizon = groundY - 50;
        for (int i = 0; i < 40; i++) {
            long z = mix(i * 0x632BE59BD9B4E019L);
            float x = Math.round(unit(z) * VW) + sx;
            z = mix(z);
            float y = Math.round(unit(z) * horizon) + sy;
            z = mix(z);
            float tw = (float) Math.sin(time * (1.2f + 2.5f * unit(z)) + i * 1.7f);
            if (tw < 0.25f) continue;
            float a = (tw - 0.25f) / 0.75f * voidK;
            t.rect(x, y, 1, 1, withAlpha(0xFFFFFFFF, a));
            if (tw > 0.8f) {
                t.rect(x - 1, y, 3, 1, withAlpha(VOID_LIGHT, a * 0.6f));
                t.rect(x, y - 1, 1, 3, withAlpha(VOID_LIGHT, a * 0.6f));
            }
        }
        t.additive(false);
    }

    /** 被无量空处困住：无穷的信息从四面八方灌进宿傩的脑子（细光线往头上汇）。 */
    private void overload(PixelTarget t, float kx, float ky, float time) {
        float hx = Math.round(kx), hy = Math.round(ky) - 40;
        t.additive(true);
        for (int i = 0; i < 12; i++) {
            float ph = (i * 0.6180339f + time * 1.6f) % 1f;
            double a = i * 2.39996 + 0.3;
            float r0 = 46 * (1 - ph) + 6, r1 = r0 + 8;
            int c = i % 3 == 0 ? 0xFFFFFFFF : VOID_LIGHT;
            t.line(Math.round(hx + (float) Math.cos(a) * r1), Math.round(hy + (float) Math.sin(a) * r1),
                    Math.round(hx + (float) Math.cos(a) * r0), Math.round(hy + (float) Math.sin(a) * r0), 1, withAlpha(c, 0.25f + 0.6f * ph));
        }
        t.additive(false);
    }

    /** 宿傩身后：领域展延的灰白轮廓（开的时候从断续的几段合成一圈，快结束时闪）。 */
    private void sukunaBack(PixelTarget t, World w, float kx, float ky, float time) {
        Fighter s = w.sukuna;
        float k;
        if (s.state == Fighter.S_AMP_START) k = Math.min(1, s.stateTick / (float) Tuning.S_AMP_STARTUP);
        else if (w.ampTicks > 0) k = w.ampTicks < 60 && ((int) (time * 16) & 1) == 0 ? 0.35f : 1;
        else return;
        float cx = Math.round(kx), cy = Math.round(ky) - 26, r = 31;
        t.additive(true);
        t.shape(cx, cy, r, 0, 0, 7, withAlpha(AMP_DARK, 0.18f * k));
        int seg = 12, on = Math.max(1, Math.round(seg * k));
        for (int i = 0; i < on; i++) {
            float a0 = i * 6.2832f / seg + time * 0.8f;
            t.shape(cx, cy, r + 1, r - 1, a0, 6.2832f / seg * (0.45f + 0.5f * k), withAlpha(AMP_LIGHT, 0.75f * k));
        }
        t.additive(false);
    }

    /** 宿傩身前：头上的魔虚罗法阵、世界斩的预警（第一次是往前裂开的空间，之后是锁定的斩线）、反转术式的光。 */
    private void sukunaFront(PixelTarget t, World w, float kx, float ky, float time, float dt, int sx, int sy) {
        Fighter s = w.sukuna;
        // 法阵：八根辐条的轮，每适应一次转 45 度；展延时暗（不转），学会世界斩后发亮
        float target = w.adaptPoints * 45f;
        wheelAngle += (target - wheelAngle) * Math.min(1, dt * 8);
        float wx = Math.round(kx), wy = Math.round(ky) - 64 + ((int) (time * 2) & 1);
        boolean lit = w.worldUnlocked;
        float turning = Math.max(0, 1 - (time - wheelT0) / 0.4f);
        int c = lit ? WHEEL_LIT : WHEEL;
        float a = w.ampTicks > 0 ? 0.4f : 0.9f;
        if (lit || turning > 0) {
            t.additive(true);
            t.shape(wx, wy, 11, 0, 0, 7, withAlpha(c, (lit ? 0.18f + 0.1f * (float) Math.sin(time * 5) : 0) + 0.4f * turning));
            t.additive(false);
        }
        double rad = Math.toRadians(wheelAngle);
        for (int i = 0; i < 8; i++) {
            double q = rad + i * Math.PI / 4;
            float cs = (float) Math.cos(q), sn = (float) Math.sin(q);
            t.line(Math.round(wx + cs * 2), Math.round(wy + sn * 2), Math.round(wx + cs * 7), Math.round(wy + sn * 7), 1, withAlpha(c, a));
            t.rect(Math.round(wx + cs * 8.5f), Math.round(wy + sn * 8.5f), 1, 1, withAlpha(c, a));
        }
        t.shape(wx, wy, 6, 5, 0, 7, withAlpha(c, a));
        t.shape(wx, wy, 1.5f, 0, 0, 7, withAlpha(c, a));

        if (s.state == Fighter.S_WORLD) {
            int st = s.stateTick;
            float y = Y(w.worldY) + sy, x0 = Math.round(kx) + w.worldDir * 8;
            float len = Tuning.S_WORLD_RANGE * PPU;
            if (w.worldWindup == Tuning.S_WORLD_FIRST_WINDUP) {
                // 束缚的第一次：空间从宿傩手前往前裂开（预警）
                float k = Math.min(1, st / (float) Tuning.S_WORLD_FIRST_WINDUP);
                crackLine(t, x0, y, w.worldDir, len * k, (int) (time * 30), withAlpha(CUT_RED, 0.5f + 0.5f * k));
            } else {
                // 结印、咏唱时手上一团暗红；指向之后：锁定的斩线一闪一闪
                t.additive(true);
                t.shape(Math.round(handX), Math.round(handY), 3 + 2 * Math.min(1, st / (float) Tuning.S_WORLD_SIGN), 0, 0, 7, withAlpha(WORLD_RED, 0.6f));
                t.additive(false);
                if (st >= Tuning.S_WORLD_WINDUP - Tuning.S_WORLD_POINT) {
                    boolean on = ((int) (time * 20) & 1) == 0;
                    for (float d = 0; d < len; d += 8) {
                        float xa = x0 + w.worldDir * d;
                        t.rect(Math.round(Math.min(xa, xa + w.worldDir * 5)), Math.round(y), 5, 1, withAlpha(on ? 0xFFFFFFFF : WORLD_RED, 0.8f));
                    }
                }
            }
        } else if (s.state == Fighter.S_RCT) {
            int st = s.stateTick;
            boolean healing = st > Tuning.S_RCT_STARTUP && st <= Tuning.S_RCT_STARTUP + Tuning.S_RCT_DURATION;
            float k = healing ? 1 : Math.min(1, st / (float) Tuning.S_RCT_STARTUP) * 0.5f;
            t.additive(true);
            t.shape(Math.round(kx), Math.round(ky) - 26, 22, 0, 0, 7, withAlpha(RCT_LIGHT, 0.12f * k));
            t.additive(false);
            if (healing && dt > 0) {
                for (int i = 0; i < 2; i++) {
                    emit(kx + (rnd() - 0.5f) * 26, ky - rnd() * 40, (rnd() - 0.5f) * 10, -30 - 30 * rnd(), 0.5f, rnd() < 0.5f ? RCT_LIGHT : 0xFF9FE0C0, 1, -20, 1, 1);
                }
            }
        }
    }

    /** 一道锯齿状的裂纹（空间裂开）：从 (x0, y) 往 dir 方向长 len；形状每 1/30 秒变一次。 */
    private void crackLine(PixelTarget t, float x0, float y, int dir, float len, int seed, int color) {
        long z = seed * 0x9E3779B97F4A7C15L;
        float px = x0, py = y;
        for (float d = 0; d < len; ) {
            z = mix(z);
            float step = 6 + 6 * unit(z);
            d = Math.min(len, d + step);
            z = mix(z);
            float ny = y + (unit(z) - 0.5f) * 4;
            float nx = x0 + dir * d;
            t.line(Math.round(px), Math.round(py), Math.round(nx), Math.round(ny), 1, color);
            px = nx;
            py = ny;
        }
    }

    /** 世界斩：整条斩带被劈开——白色的宽光、黑色的裂缝、红边，0.35 秒内收拢。 */
    private void worldSlash(PixelTarget t, float time, int sx, int sy) {
        float f = (time - worldT0) / 0.35f;
        if (f < 0 || f >= 1) return;
        float y = Math.round(worldY0) + sy, xa = Math.round(worldX0) + sx, xb = Math.round(worldX1) + sx;
        float x0 = Math.min(xa, xb), w = Math.abs(xb - xa);
        float th = Math.max(1, Math.round(5 * (1 - f)));
        t.additive(true);
        t.rect(x0, y - th - 2, w, th * 2 + 5, withAlpha(WORLD_RED, 0.35f * (1 - f)));
        t.rect(x0, y - th, w, th * 2 + 1, withAlpha(0xFFFFFFFF, 0.9f * (1 - f)));
        t.additive(false);
        t.rect(x0, y, w, 1, withAlpha(0xFF05040A, 1 - f * f));
        crackLine(t, xa, y - 2, xb >= xa ? 1 : -1, w, (int) (worldT0 * 30), withAlpha(CUT_RED, 0.8f * (1 - f)));
    }

    /** 解：看不见的斩击，只留一道一闪而过的细白线。 */
    private void slash(PixelTarget t, float time, int sx, int sy) {
        float f = (time - slashT0) / 0.12f;
        if (f < 0 || f >= 1) return;
        float y = Math.round(slashY0) + sy;
        t.additive(true);
        t.line(Math.round(slashX0) + sx, y, Math.round(slashX1) + sx, y, 1, withAlpha(0xFFFFFFFF, 0.9f * (1 - f)));
        t.line(Math.round(slashX0) + sx, y + 1, Math.round(slashX1) + sx, y + 1, 1, withAlpha(0xFFB4BCD0, 0.35f * (1 - f)));
        t.additive(false);
    }

    private void drawEffects(PixelTarget t, float time, int sx, int sy) {
        for (int i = 0; i < FX; i++) {
            if (fxType[i] == 0) continue;
            float f = (time - fxT0[i]) / fxDur[i];
            if (f >= 1 || f < 0) {
                if (f >= 1) fxType[i] = 0;
                continue;
            }
            float x = Math.round(fxX[i]) + sx, y = Math.round(fxY[i]) + sy;
            switch (fxType[i]) {
                case FX_RING: {
                    float e = 1 - (1 - f) * (1 - f);
                    float r = fxA[i] + (fxB[i] - fxA[i]) * e, th = Math.max(1, 3 * (1 - f));
                    t.additive(true);
                    t.shape(x, y, r + th + 1, r, 0, 7, withAlpha(fxColor2[i], 0.6f * (1 - f)));
                    t.shape(x, y, r + th, r, 0, 7, withAlpha(fxColor[i], 1 - f));
                    t.additive(false);
                    break;
                }
                case FX_RING_IN: {
                    float r = fxB[i] * (1 - f) + 2;
                    t.additive(true);
                    t.shape(x, y, r + 2, r, 0, 7, withAlpha(fxColor[i], 0.9f * (1 - f * 0.5f)));
                    t.additive(false);
                    break;
                }
                case FX_DISC:
                    t.additive(true);
                    t.shape(x, y, fxA[i] * (1 + f), 0, 0, 7, withAlpha(fxColor[i], 1 - f));
                    t.additive(false);
                    break;
                case FX_SPEED: {
                    // 向外的速度线
                    t.additive(true);
                    for (int s2 = 0; s2 < 10; s2++) {
                        double a = s2 * Math.PI / 5 + fxT0[i] * 5;
                        float r0 = fxA[i] + fxB[i] * f, r1 = r0 + 6 * (1 - f) + 2;
                        t.line(x + (float) Math.cos(a) * r0, y + (float) Math.sin(a) * r0, x + (float) Math.cos(a) * r1, y + (float) Math.sin(a) * r1, 1,
                                withAlpha(fxColor[i], 1 - f));
                    }
                    t.additive(false);
                    break;
                }
                case FX_BOLTS:
                    blackFlashBolts(t, x, y, fxB[i] * (0.6f + 0.4f * Math.min(1, f * 4)), (int) (time * 20) + i * 31, f);
                    break;
                case FX_STAR: {
                    // 冲击星：六根长短相间的尖刺一下子刺出去，中间一点白
                    float e = 1 - (1 - f) * (1 - f), len = fxB[i] * (0.45f + 0.55f * e);
                    t.additive(true);
                    for (int k = 0; k < 6; k++) {
                        double a = k * Math.PI / 3 + fxA[i];
                        float lk = (k % 2 == 0 ? 1 : 0.55f) * len, r0 = len * 0.25f * e;
                        t.line(Math.round(x + (float) Math.cos(a) * r0), Math.round(y + (float) Math.sin(a) * r0),
                                Math.round(x + (float) Math.cos(a) * lk), Math.round(y + (float) Math.sin(a) * lk), k % 2 == 0 ? 2 : 1, withAlpha(fxColor[i], 1 - f));
                    }
                    t.shape(x, y, 4 * (1 - f) + 1, 0, 0, 7, withAlpha(0xFFFFFFFF, 1 - f));
                    t.additive(false);
                    break;
                }
                case FX_CUTS: {
                    // 斩痕：三道细长的斜线一下子划开，再慢慢淡掉
                    float grow = Math.min(1, f * 6), a = f < 0.5f ? 1 : (1 - f) / 0.5f;
                    for (int k = 0; k < 3; k++) {
                        double ang = -0.75 + k * 0.5;
                        float half = fxB[i] * (0.75f + 0.15f * k) * grow, ox = (k - 1) * 4, oy = (k - 1) * 3;
                        float dx = (float) Math.cos(ang) * half, dy = (float) Math.sin(ang) * half;
                        if (fxColor2[i] != 0) t.line(Math.round(x + ox - dx), Math.round(y + oy - dy + 1), Math.round(x + ox + dx), Math.round(y + oy + dy + 1), 1, withAlpha(fxColor2[i], 0.8f * a));
                        t.additive(true);
                        t.line(Math.round(x + ox - dx), Math.round(y + oy - dy), Math.round(x + ox + dx), Math.round(y + oy + dy), 1, withAlpha(fxColor[i], a));
                        t.additive(false);
                    }
                    break;
                }
                case FX_KO: {
                    float a = f < 0.8f ? 1 : (1 - f) / 0.2f;
                    int img = Atlas.BIG_KO;
                    float yy = y - Math.round(6 * Math.min(1, f * 5));
                    t.sprite(PixelTarget.TEX_ATLAS, atlas.x[img], atlas.y[img], atlas.w[img], atlas.h[img],
                            Math.round(x - atlas.w[img] * 0.5f), Math.round(yy), 0, 0, 0, 1, 1, withAlpha(fxColor[i], a), 0);
                    break;
                }
                default:
                    break;
            }
        }
    }

    /** 黑闪：黑色的闪电带红边，从命中点往外劈；形状每 1/20 秒闪一次。 */
    private void blackFlashBolts(PixelTarget t, float x, float y, float R, int seed, float f) {
        float a = f < 0.6f ? 1 : (1 - f) / 0.4f;
        long z = seed * 0x9E3779B97F4A7C15L;
        for (int pass = 0; pass < 2; pass++) {
            long zz = z;
            for (int b = 0; b < 7; b++) {
                zz = mix(zz);
                double ang = b * Math.PI * 2 / 7 + unit(zz) * 0.7;
                float px0 = x, py0 = y;
                float reach = R * (0.6f + 0.4f * unit(mix(zz)));
                for (int sgm = 1; sgm <= 4; sgm++) {
                    zz = mix(zz);
                    float rr = reach * sgm / 4;
                    float j = (unit(zz) - 0.5f) * reach * 0.35f;
                    float px1 = x + (float) Math.cos(ang) * rr - (float) Math.sin(ang) * j;
                    float py1 = y + (float) Math.sin(ang) * rr + (float) Math.cos(ang) * j;
                    if (pass == 0) t.line(Math.round(px0), Math.round(py0), Math.round(px1), Math.round(py1), 3, withAlpha(BF_RED, a));
                    else t.line(Math.round(px0), Math.round(py0), Math.round(px1), Math.round(py1), 1, withAlpha(BF_BLACK, a));
                    px0 = px1;
                    py0 = py1;
                }
            }
        }
    }

    private void drawNumbers(PixelTarget t, float time, int sx, int sy) {
        for (int i = 0; i < NUMS; i++) {
            float f = (time - numT0[i]) / 0.9f;
            if (f < 0 || f >= 1 || numVal[i] <= 0) continue;
            int kind = numKind[i];
            int scale = kind == Attack.BLACK_FLASH || kind == Attack.PURPLE || kind == Attack.HOLLOW || kind == Attack.WORLD_SLASH ? 2 : 1;
            int color = kind == Attack.BLACK_FLASH ? BF_RED : kind == Attack.RED ? RED_LIGHT : kind == Attack.BLUE_FIELD ? BLUE_LIGHT
                    : kind == Attack.PURPLE || kind == Attack.HOLLOW ? PURPLE_LIGHT : kind == Attack.WORLD_SLASH ? WORLD_RED
                    : kind == Attack.S_MELEE ? 0xFFFFC8C8 : 0xFFFFFFFF;
            float a = f < 0.75f ? 1 : (1 - f) / 0.25f;
            float x = X(numX[i]) + sx + (i % 3 - 1) * 5, y = Y(numY[i]) + sy - 14 * (1 - (1 - f) * (1 - f)) - 6;
            drawNumber(t, numVal[i], x, y, scale, withAlpha(color, a), true);
        }
    }

    /** 像素数字（每位 5×7 + 描边），centered 时以 x 为中心。 */
    private void drawNumber(PixelTarget t, int v, float x, float y, int scale, int mul, boolean centered) {
        int n = 0;
        do {
            digits[n++] = v % 10;
            v /= 10;
        } while (v > 0 && n < digits.length);
        int gw = (atlas.w[Atlas.DIGIT0] - 1) * scale;
        float left = centered ? x - n * gw * 0.5f : x;
        left = Math.round(left);
        y = Math.round(y);
        for (int i = n - 1, k = 0; i >= 0; i--, k++) {
            int img = Atlas.DIGIT0 + digits[i];
            t.sprite(PixelTarget.TEX_ATLAS, atlas.x[img], atlas.y[img], atlas.w[img], atlas.h[img], left + k * gw, y, 0, 0, 0, scale, scale, mul, 0);
        }
    }

    private void drawBannerPixels(PixelTarget t, float time) {
        float tt = time - bannerT0;
        if (bannerBig < 0 || tt > 1.05f) return;
        float a = tt < 0.75f ? 1 : (1.05f - tt) / 0.3f;
        int img = bannerBig;
        float y = Math.round(vh * 0.24f) + Math.round(-4 * Math.max(0, 1 - tt / 0.12f));
        t.sprite(PixelTarget.TEX_ATLAS, atlas.x[img], atlas.y[img], atlas.w[img], atlas.h[img],
                Math.round((VW - atlas.w[img]) * 0.5f), y, 0, 0, 0, 1, 1, withAlpha(bannerColor, a), tt < 0.06f ? 0xFFFFFF : 0);
    }

    /* ---------------- 姿势 ---------------- */

    private void poseGojo(World w, float time) {
        Fighter g = w.gojo;
        Pose p = pose;
        p.reset();
        p.face = g.facing;
        int st = g.stateTick;
        p.hurt = Math.max(0, 1 - (w.tick - g.lastHitTick) / 10f);
        if (g.state == Fighter.KO) {
            knockedOut(p, time - koT0Gojo);
            return;
        }
        if (g.hitstun > 0) {
            float f = Math.min(1, g.hitstun / 24f);
            p.lean = -16 * f;
            p.armFront = 60 * f;
            p.armBack = -50 * f;
            p.legFront = 20 * f;
            return;
        }
        boolean air = !g.onGround;
        if (air) {
            p.legFront = 35;
            p.legBack = -15;
            p.armFront = 50;
            p.armBack = -40;
        } else if (Math.abs(g.vx) > 30) {
            float sn = (float) Math.sin(g.x * 0.05f);
            p.legFront = sn * 34;
            p.legBack = -sn * 34;
            p.armFront = -sn * 30;
            p.armBack = sn * 30;
            p.lean = 10;
            p.bob = Math.abs(sn) > 0.7f ? 1 : 0;
        } else {
            p.bob = Math.sin(time * 2.6f) > 0.3f ? 1 : 0;
            p.armFront = 8;
            p.armBack = -8;
        }
        switch (g.state) {
            case Fighter.MELEE: { // 起手；还按着就是在蓄苍拳：拳头往后收
                int combo = (w.meleeCombo - 1) % 3;
                meleeWindup(p, combo, Math.min(1, st / (float) Tuning.MELEE_STARTUP));
                if (st > Tuning.MELEE_STARTUP) {
                    float k = Math.min(1, (st - Tuning.MELEE_STARTUP) / (float) (Tuning.FIST_HOLD - Tuning.MELEE_STARTUP));
                    fistWindup(p, k);
                }
                break;
            }
            case Fighter.MELEE_ACTIVE:
                meleeStrike(p, (w.meleeCombo - 1) % 3, 1);
                break;
            case Fighter.MELEE_RECOVER:
                meleeStrike(p, (w.meleeCombo - 1) % 3, 1 - st / (float) Tuning.MELEE_RECOVERY);
                break;
            case Fighter.FIST_WINDUP:
                fistWindup(p, 1);
                p.bob = 0;
                break;
            case Fighter.FIST_ACTIVE:
                p.armFront = 95;
                p.armBack = -45;
                p.lean = 22;
                p.legFront = 32;
                p.legBack = -30;
                break;
            case Fighter.FIST_RECOVER: {
                float f = 1 - st / (float) Tuning.FIST_RECOVERY;
                p.armFront = 10 + 85 * f;
                p.armBack = -45 * f;
                p.lean = 22 * f;
                p.legFront = 32 * f;
                p.legBack = -30 * f;
                break;
            }
            case Fighter.BLUE_HOLD:
                p.armFront = 85;
                p.armBack = -20;
                p.lean = 4;
                break;
            case Fighter.BLUE_WINDUP:
                if (w.pendingCharged()) {
                    p.armFront = 150;
                    p.armBack = 40;
                    p.lean = -4;
                } else {
                    p.armFront = 85;
                    p.armBack = -20;
                    p.lean = 4;
                }
                break;
            case Fighter.BLUE_RECOVER:
                p.armFront = 100;
                p.armBack = -25;
                p.lean = 8;
                break;
            case Fighter.RED_HOLD: // 赫：指尖朝前
                p.armFront = 80;
                p.armBack = -25;
                p.lean = -4;
                p.legFront = 14;
                p.legBack = -14;
                break;
            case Fighter.RED_WINDUP: // 最大输出：站稳，手臂伸直
                p.armFront = 88;
                p.armBack = -55;
                p.lean = -10;
                p.legFront = 30;
                p.legBack = -28;
                p.bob = 0;
                break;
            case Fighter.RED_ACTIVE:
                p.armFront = 95;
                p.armBack = -35;
                p.lean = 16;
                p.legFront = 28;
                p.legBack = -24;
                break;
            case Fighter.RED_RECOVER: {
                float f = st / (float) (w.redMax() ? Tuning.RED_MAX_RECOVERY : Tuning.RED_RECOVERY);
                p.armFront = 95 - 85 * f;
                p.armBack = -35 * (1 - f);
                p.lean = 16 * (1 - f);
                p.legFront = 28 * (1 - f);
                p.legBack = -24 * (1 - f);
                break;
            }
            case Fighter.PURPLE_WINDUP:
                p.armFront = 80;
                p.armBack = 70;
                p.lean = -6;
                p.legFront = 22;
                p.legBack = -22;
                break;
            case Fighter.PURPLE_CHANT: { // 念咒：两手张开（前手苍、后手赫），一段一段往中间合
                float k = Math.min(1, st / (float) (Tuning.CHANT_PHRASES * Tuning.CHANT_PHRASE));
                p.armFront = 100 - 20 * k;
                p.armBack = -100 + 165 * k;
                p.lean = -4;
                p.legFront = 24;
                p.legBack = -24;
                p.bob = 0;
                break;
            }
            case Fighter.PURPLE_RECOVER: {
                float f = st / (float) (w.purple200() ? Tuning.PURPLE_200_RECOVER : Tuning.PURPLE_RECOVER);
                float big = w.purple200() ? 1.4f : 1;
                p.armFront = 95 - 80 * f;
                p.armBack = 60 * (1 - f) - 10;
                p.lean = (14 * (1 - f) - 4 * f) * big;
                p.legFront = 26 * (1 - f) * big;
                p.legBack = -26 * (1 - f) * big;
                break;
            }
            case Fighter.DOMAIN_CAST: // 结印：手举到胸前
                p.armFront = 150;
                p.armBack = -10;
                p.lean = 0;
                p.bob = 0;
                break;
            default:
                break;
        }
    }

    /** 体术起手：直拳收拳、踢腿提膝、上勾下沉。 */
    private static void meleeWindup(Pose p, int combo, float wind) {
        if (combo == 0) {
            p.armFront = -35 * wind;
            p.armBack = -25;
            p.lean = -6 * wind;
            p.legFront = 20;
            p.legBack = -18;
        } else if (combo == 1) {
            p.legFront = 25 * wind;
            p.legBack = -12;
            p.armFront = 45;
            p.armBack = -45;
            p.lean = -4;
        } else {
            p.armFront = -30 * wind;
            p.armBack = -30;
            p.lean = -8 * wind;
            p.legFront = 16;
            p.legBack = -16;
        }
    }

    /** 体术打出去：back 从 1（打满）退到 0（收回）。 */
    private static void meleeStrike(Pose p, int combo, float back) {
        if (combo == 0) {        // 直拳
            p.armFront = 10 + 85 * back;
            p.armBack = -30;
            p.lean = 18 * back;
            p.legFront = 22;
            p.legBack = -20;
        } else if (combo == 1) { // 踢
            p.legFront = 10 + 85 * back;
            p.legBack = -12;
            p.armFront = 45;
            p.armBack = -45;
            p.lean = -16 * back;
        } else {                 // 上勾
            p.armFront = 10 + 150 * back;
            p.armBack = -30;
            p.lean = 6 * back;
            p.legFront = 16;
            p.legBack = -16;
            p.bob = Math.round(3 * back);
        }
    }

    /** 苍拳蓄力：拳头收到身后，身体压低。 */
    private static void fistWindup(Pose p, float k) {
        p.armFront = -40 - 25 * k;
        p.armBack = 30 * k;
        p.lean = -8 - 4 * k;
        p.legFront = 20 + 6 * k;
        p.legBack = -18 - 6 * k;
    }

    private void poseSukuna(World w, float time) {
        Fighter s = w.sukuna;
        Pose p = pose;
        p.reset();
        p.face = s.facing;
        int since = w.tick - s.lastHitTick;
        p.hurt = Math.max(0, 1 - since / 10f);
        if (s.state == Fighter.KO) {
            knockedOut(p, time - koT0Sukuna);
            return;
        }
        BlueOrb b = w.blue;
        boolean inBlue = false;
        if (b.phase == BlueOrb.PULL) {
            float dx = b.cx - s.x, dy = b.cy - s.centerY();
            inBlue = s.captured || dx * dx + dy * dy < b.radius * b.radius;
        }
        if (s.captured) {
            p.armFront = 120 + (float) Math.sin(time * 11) * 15;
            p.armBack = 110 - (float) Math.sin(time * 9) * 15;
            p.legFront = 30;
            p.legBack = -30;
            p.spin = Math.round((float) Math.sin(time * 7) * 2) * 10;
            return;
        }
        boolean air = s.y > 1;
        if (air && s.hitstun > 0 && (s.lastHitKind == Attack.RED || s.lastHitKind == Attack.PURPLE || s.lastHitKind == Attack.HOLLOW
                || s.lastHitKind == Attack.BLUE_FIST || s.lastHitKind == Attack.BLACK_FLASH)) {
            p.spin = -Math.round(36 * Math.min(1, since / 40f)) * 10; // 被打飞：翻一个跟头（每 10 度一档）
            p.armFront = 100;
            p.armBack = 80;
            p.legFront = 40;
            p.legBack = -20;
            return;
        }
        if (inBlue) {
            p.lean = -16;
            p.armFront = 70 + (float) Math.sin(time * 14) * 20;
            p.armBack = -50;
            p.legFront = 22;
            p.legBack = -26;
            return;
        }
        if (s.hitstun > 0) {
            float f = Math.min(1, s.hitstun / 18f);
            p.lean = -16 * f;
            p.armFront = 50 * f;
            p.armBack = -40 * f;
            if (air) {
                p.legFront = 30;
                p.legBack = -10;
            }
            return;
        }
        if (w.domainTicks > 0 && !air) { // 无量空处：信息塞满了脑子，僵在原地，偶尔抽一下
            p.lean = -5;
            p.armFront = 22;
            p.armBack = -18;
            p.legFront = 6;
            p.legBack = -6;
            p.bob = ((int) (time * 24) % 9) == 0 ? 1 : 0;
            return;
        }
        int st = s.stateTick;
        switch (s.state) {
            case Fighter.S_MELEE: {
                int su = Tuning.S_MELEE_STARTUP, act = su + Tuning.S_MELEE_ACTIVE;
                if (st <= su) {
                    float k = st / (float) su;
                    p.armFront = -40 * k;
                    p.armBack = 20 * k;
                    p.lean = -6 * k;
                    p.legFront = 16 * k;
                    p.legBack = -14 * k;
                } else {
                    float back = st <= act ? 1 : Math.max(0, 1 - (st - act) / (float) Tuning.S_MELEE_RECOVERY);
                    p.armFront = 10 + 85 * back;
                    p.armBack = -30 * back;
                    p.lean = 16 * back;
                    p.legFront = 22 * back;
                    p.legBack = -20 * back;
                }
                return;
            }
            case Fighter.S_DISMANTLE: { // 解：几乎没有抬手，手腕一甩
                float k = st <= Tuning.DISMANTLE_WINDUP ? st / (float) Tuning.DISMANTLE_WINDUP
                        : Math.max(0, 1 - (st - Tuning.DISMANTLE_WINDUP) / (float) Tuning.DISMANTLE_RECOVERY);
                p.armFront = 10 + 60 * k;
                p.armBack = -12 * k;
                p.lean = 4 * k;
                return;
            }
            case Fighter.S_AMP_START: // 开领域展延：沉肩、两手往下压
                p.armFront = -20;
                p.armBack = 20;
                p.lean = -4;
                p.legFront = 14;
                p.legBack = -14;
                return;
            case Fighter.S_WORLD: { // 结印、咏唱：两手合在胸前；指向：前手笔直指出去
                boolean pointing = w.worldWindup == Tuning.S_WORLD_FIRST_WINDUP || st >= w.worldWindup - Tuning.S_WORLD_POINT;
                p.armFront = pointing ? 90 : 140;
                p.armBack = pointing ? -30 : 130;
                p.lean = pointing ? 6 : -2;
                p.legFront = 18;
                p.legBack = -18;
                return;
            }
            case Fighter.S_WORLD_RECOVER: {
                float k = Math.max(0, 1 - st / (float) Tuning.S_WORLD_RECOVERY);
                p.armFront = 10 + 85 * k;
                p.armBack = -30 * k;
                p.lean = 8 * k;
                p.legFront = 18 * k;
                p.legBack = -18 * k;
                return;
            }
            case Fighter.S_RCT: // 反转术式：站直，两手微微张开
                p.armFront = 25;
                p.armBack = -25;
                p.lean = -2;
                return;
            default:
                break;
        }
        if (air) {
            p.legFront = 30;
            p.legBack = -12;
            p.armFront = 40;
            p.armBack = -35;
        } else if (Math.abs(s.vx) > 30) {
            float sn = (float) Math.sin(s.x * 0.05f);
            p.legFront = sn * 30;
            p.legBack = -sn * 30;
            p.armFront = -sn * 26;
            p.armBack = sn * 26;
            p.lean = 8;
            p.bob = Math.abs(sn) > 0.7f ? 1 : 0;
        } else {
            p.bob = Math.sin(time * 2.2f + 1) > 0.3f ? 1 : 0;
            p.armFront = 10;
            p.armBack = -10;
        }
    }

    private static void knockedOut(Pose p, float since) {
        float f = Math.min(1, since / 0.2f);
        p.spin = -90 * f;
        p.bob = -Math.round(18 * f);
        p.armFront = 60;
        p.armBack = -50;
        p.legFront = 12;
        p.legBack = -8;
    }

    /* ---------------- 像素角色：按骨骼摆部件 ---------------- */

    private float figX, figY, figFace, figCos = 1, figSin;

    /** 画一个人：脚底在 (fx, fy)。头和身体不转（只按前倾平移整数像素，保持像素干净），手脚绕关节转。 */
    private void figure(PixelTarget t, boolean gojo, float fx, float fy) {
        Pose p = pose;
        int head = gojo ? Atlas.GOJO_HEAD : Atlas.SUKUNA_HEAD, torso = head + 1, arm = head + 2, leg = head + 3;
        figX = Math.round(fx);
        figY = Math.round(fy - p.bob);
        figFace = p.face;
        double sp = Math.toRadians(p.spin);
        figCos = (float) Math.cos(sp);
        figSin = (float) Math.sin(sp);
        int add = p.hurt > 0 ? gray(Math.round(p.hurt * 4) * 48) : 0;
        int back = 0xFFB4B4BC;
        float lean = p.lean;
        int dTorso = Math.round(lean * 0.12f), dHead = Math.round(lean * 0.2f), dShoulder = Math.round(lean * 0.16f);
        part(t, arm, -6 + dShoulder, SHOULDER_Y, -p.armBack, back, add);
        part(t, leg, -2, -13, -p.legBack, back, add);
        part(t, leg, 2, -13, -p.legFront, 0xFFFFFFFF, add);
        part(t, torso, dTorso, -12, 0, 0xFFFFFFFF, add);
        part(t, head, dHead, -26 + Math.round(Math.abs(lean) * 0.04f), 0, 0xFFFFFFFF, add);
        part(t, arm, 6 + dShoulder, SHOULDER_Y, -p.armFront, 0xFFFFFFFF, add);
        // 两只手的位置（给手上的术式用）
        float[] q = hand(6 + dShoulder, -p.armFront);
        handX = q[0];
        handY = q[1];
        q = hand(-6 + dShoulder, -p.armBack);
        backHandX = q[0];
        backHandY = q[1];
    }

    private static final int SHOULDER_Y = -24;
    private static final float HAND_REACH = 9.5f; // 手掌中心离肩关节多远（像素）

    private float[] hand(float shoulderX, float deg) {
        double a = Math.toRadians(deg);
        return toWorld(shoulderX - HAND_REACH * (float) Math.sin(a), SHOULDER_Y + HAND_REACH * (float) Math.cos(a));
    }

    private final float[] tw = new float[2];

    private float[] toWorld(float lx, float ly) {
        float dx = lx, dy = ly + 26;
        float rx = dx * figCos - dy * figSin, ry = dx * figSin + dy * figCos - 26;
        tw[0] = figX + figFace * rx;
        tw[1] = figY + ry;
        return tw;
    }

    private void part(PixelTarget t, int img, float jx, float jy, float deg, int mul, int add) {
        float[] q = toWorld(jx, jy);
        float wdeg = figFace * (pose.spin + deg);
        float x = q[0], y = q[1];
        if (wdeg == 0) {
            x = Math.round(x);
            y = Math.round(y);
        }
        t.sprite(PixelTarget.TEX_ATLAS, atlas.x[img], atlas.y[img], atlas.w[img], atlas.h[img], x, y, atlas.px[img], atlas.py[img],
                wdeg, figFace, 1, mul, add);
    }

    /* ---------------- 界面（像素层的方块部分） ---------------- */

    private float barL1, barR1, barL2, barR2, barTop, barH;

    private void drawHudPixels(PixelTarget t, World w, float dt) {
        Controls.Layout l = laid;
        float gHp = w.gojo.hpMilli / (float) w.gojo.maxHpMilli;
        float sHp = w.sukuna.hpMilli / (float) w.sukuna.maxHpMilli;
        gojoLag = lag(gojoLag, gHp, dt);
        sukunaLag = lag(sukunaLag, sHp, dt);

        // 返回、音乐
        float bx = l.backX / s, by = l.backY / s, mx = l.musicX / s, my = l.musicY / s, br = l.smallR / s;
        pixelButton(t, bx, by, br, controls.hudPressed == 0, 0xFFEFF2FF);
        icon(t, Atlas.ICON_BACK, bx, by, 0xFFEFF2FF);
        boolean muted = bgm != null && bgm.isMuted();
        pixelButton(t, mx, my, br, controls.hudPressed == 1, 0xFFEFF2FF);
        icon(t, Atlas.ICON_NOTE, mx, my, muted ? 0xFF7A7E8C : 0xFFEFF2FF);
        if (muted) t.line(mx - br * 0.6f, my + br * 0.6f, mx + br * 0.6f, my - br * 0.6f, 2, 0xFFFF5A6A);

        // 头像框 + 血条
        int top = Math.round(l.hudTop / s);
        int face = 30;
        float pl = Math.round(bx + br + 4), pr = Math.round(mx - br - 4) - face;
        portrait(t, Atlas.GOJO_HEAD, pl, top, 1, 0xFF6FC3FF);
        portrait(t, Atlas.SUKUNA_HEAD, pr, top, -1, 0xFFE0303F);
        barTop = top + 13;
        barH = 9;
        barL1 = pl + face + 3;
        barR1 = Math.round(VW * 0.5f) - 30;
        barL2 = Math.round(VW * 0.5f) + 30;
        barR2 = pr - 3;
        bar(t, barL1, barR1, gHp, gojoLag, 0xFF3FA8FF, 0xFF9FF0FF, false);
        bar(t, barL2, barR2, sHp, sukunaLag, 0xFFB0101E, 0xFFFF6A5A, true);
        drawNumber(t, (int) ((w.gojo.hpMilli + 999) / 1000), barR1 - 2 - digitsWidth((int) ((w.gojo.hpMilli + 999) / 1000)), barTop, 1, 0xF2FFFFFF, false);
        drawNumber(t, (int) ((w.sukuna.hpMilli + 999) / 1000), barL2 + 2, barTop, 1, 0xF2FFFFFF, false);
        if (statsOn) {
            float y = top + 30;
            drawNumber(t, Math.round(fpsAvg), VW * 0.5f, y, 1, 0xFF9FFF9F, true);
            drawNumber(t, Math.round(tickMs * 100), VW * 0.5f - 30, y + 10, 1, 0xFF9FFF9F, true);
            drawNumber(t, Math.round(drawMs * 100), VW * 0.5f + 30, y + 10, 1, 0xFF9FFF9F, true);
        }
    }

    private int digitsWidth(int v) {
        int n = 1;
        while (v >= 10) {
            v /= 10;
            n++;
        }
        return n * (atlas.w[Atlas.DIGIT0] - 1);
    }

    private void portrait(PixelTarget t, int head, float x, float y, float face, int accent) {
        t.rect(x - 1, y - 1, 32, 30, 0xFF05040A);
        t.rect(x, y, 30, 28, 0xFF1A1D2B);
        t.rect(x, y + 26, 30, 2, accent);
        float px = face > 0 ? x : x + 30;
        t.sprite(PixelTarget.TEX_ATLAS, atlas.x[head], atlas.y[head], atlas.w[head], atlas.h[head] - 1, px, y, 0, 0, 0, face, 1, 0xFFFFFFFF, 0);
    }

    private void bar(PixelTarget t, float l, float r, float hp, float lag, int c1, int c2, boolean fromRight) {
        float top = barTop, h = barH, w = r - l;
        t.rect(l - 1, top - 1, w + 2, h + 2, 0xFF05040A);
        t.rect(l, top, w, h, 0xFF2A2D3C);
        if (lag > hp) {
            float lw = Math.round(w * lag);
            t.rect(fromRight ? r - lw : l, top, lw, h, 0xFFFFE6A8);
        }
        float fw = Math.round(w * hp);
        if (fw > 0) {
            float fx = fromRight ? r - fw : l;
            t.rect(fx, top, fw, h, c1);
            t.rect(fx, top + 1, fw, 2, c2);
            t.rect(fx, top + h - 1, fw, 1, 0x66000000);
        }
        for (int i = 1; i < 10; i++) t.rect(Math.round(l + w * i / 10f), top, 1, h, 0x33000000);
    }

    private void icon(PixelTarget t, int img, float cx, float cy, int mul) {
        t.sprite(PixelTarget.TEX_ATLAS, atlas.x[img], atlas.y[img], atlas.w[img], atlas.h[img],
                Math.round(cx - atlas.w[img] * 0.5f), Math.round(cy - atlas.h[img] * 0.5f), 0, 0, 0, 1, 1, mul, 0);
    }

    private void pixelButton(PixelTarget t, float x, float y, float r, boolean pressed, int ring) {
        x = Math.round(x);
        y = Math.round(y);
        // 底色半透明：人走到按键后面也看得见
        t.shape(x, y, r + 1, r, 0, 7, 0xB305040A);
        t.shape(x, y, r, 0, 0, 7, pressed ? 0x99343A58 : 0x59151829);
        t.shape(x, y, r, r - 1, 0, 7, (ring & 0xFFFFFF) | (pressed ? 0xFF000000 : 0xB3000000));
        t.shape(x, y, r - 2, r - 3, 3.6f, 1.6f, 0x40FFFFFF); // 左上角一点高光
    }

    private static float lag(float lag, float cur, float dt) {
        if (cur >= lag) return cur;
        return Math.max(cur, lag - dt * 0.35f);
    }

    private void drawControlsPixels(PixelTarget t, World w) {
        Controls.Layout l = laid;
        boolean active = controls.stickActive;
        float bx = (active ? controls.stickBaseX : l.stickX) / s, by = (active ? controls.stickBaseY : l.stickY) / s;
        float R = l.stickR / s;
        t.shape(Math.round(bx), Math.round(by), R, 0, 0, 7, active ? 0x40101222 : 0x26101222);
        t.shape(Math.round(bx), Math.round(by), R, R - 1, 0, 7, active ? 0x99FFFFFF : 0x4DFFFFFF);
        float kx = (active ? controls.knobX : l.stickX) / s, ky = (active ? controls.knobY : l.stickY) / s;
        if (!active) {
            kx = bx;
            ky = by;
        }
        float kr = l.knobR / s;
        t.shape(Math.round(kx), Math.round(ky), kr + 1, 0, 0, 7, 0x8005040A);
        t.shape(Math.round(kx), Math.round(ky), kr, 0, 0, 7, active ? 0xCCE8EEFF : 0x66E8EEFF);

        Fighter g = w.gojo;
        for (int i = 0; i < Controls.BUTTONS; i++) {
            float x = Math.round(l.bx[i] / s), y = Math.round(l.by[i] / s), r = l.br[i] / s;
            boolean pressed = controls.held.get(i) != 0;
            int ring = buttonColor(i);
            pixelButton(t, x, y, pressed ? r - 1 : r, pressed, ring);
            int cd = cooldown(w, i), cdMax = 1;
            if (i == Controls.BLUE) cdMax = w.blueCdMax;
            else if (i == Controls.RED) cdMax = w.redCdMax;
            else if (i == Controls.PURPLE) cdMax = w.purpleCdMax;
            else if (i == Controls.DOMAIN) cdMax = Tuning.DOMAIN_COOLDOWN;
            if (cd > 0) {
                t.shape(x, y, r - 1, 0, (float) (-Math.PI / 2), (float) (Math.PI * 2 * cd / cdMax), 0x99000000);
                drawNumber(t, (cd + Tuning.HZ - 1) / Tuning.HZ, x, y - 4, 1, 0xFFEFF2FF, true);
            }
            // 外圈进度：长按蓄力（苍、苍拳、赫最大输出、茈念咒）、领域剩余时间
            float prog = -1;
            boolean full = false;
            int st = g.stateTick;
            if (i == Controls.BLUE) {
                if (g.state == Fighter.BLUE_HOLD) prog = Math.min(1, st / (float) Tuning.BLUE_HOLD_THRESHOLD);
                else if (g.state == Fighter.BLUE_WINDUP && w.pendingCharged()) full = true;
            } else if (i == Controls.MELEE) {
                if (g.state == Fighter.MELEE && pressed && st > Tuning.MELEE_STARTUP) prog = st / (float) Tuning.FIST_HOLD;
                else if (g.state == Fighter.FIST_WINDUP) full = true;
            } else if (i == Controls.RED) {
                if (g.state == Fighter.RED_HOLD && pressed && st > Tuning.RED_WINDUP) prog = st / (float) Tuning.RED_HOLD;
                else if (g.state == Fighter.RED_WINDUP) full = true;
            } else if (i == Controls.PURPLE) {
                if (g.state == Fighter.PURPLE_WINDUP && pressed) prog = st / (float) Tuning.PURPLE_HOLD * 0.25f;
                else if (g.state == Fighter.PURPLE_CHANT) prog = Math.min(1, st / (float) (Tuning.CHANT_PHRASES * Tuning.CHANT_PHRASE));
            } else if (i == Controls.DOMAIN && w.domainTicks > 0) {
                prog = w.domainTicks / (float) Tuning.DOMAIN_DURATION;
            }
            if (full) prog = 1;
            if (prog > 0) t.shape(x, y, r + 3, r + 1, (float) (-Math.PI / 2), (float) (Math.PI * 2 * Math.min(1, prog)), full ? 0xFFFFFFFF : ring);
            if (i == Controls.PURPLE && g.state == Fighter.PURPLE_CHANT) { // 四段咒词：外圈分四格
                for (int q = 0; q < 4; q++) {
                    double a = -Math.PI / 2 + q * Math.PI / 2;
                    t.rect(Math.round(x + (float) Math.cos(a) * (r + 2)), Math.round(y + (float) Math.sin(a) * (r + 2)), 1, 1, 0xFF05040A);
                }
            }
        }
    }

    private static int buttonColor(int i) {
        return i == Controls.BLUE ? BLUE_MID : i == Controls.RED ? RED_LIGHT : i == Controls.PURPLE ? PURPLE_HOT : i == Controls.DOMAIN ? VOID_LIGHT : 0xFFEFF2FF;
    }

    private static int cooldown(World w, int button) {
        switch (button) {
            case Controls.BLUE:
                return w.blueCd;
            case Controls.RED:
                return w.redCd;
            case Controls.PURPLE:
                return w.purpleCd;
            case Controls.DOMAIN:
                return w.domainTicks > 0 ? 0 : w.domainCd;
            default:
                return 0;
        }
    }

    /* ---------------- 屏幕分辨率层：中文字 ---------------- */

    private void drawHudText(PixelTarget t, World w, float time) {
        HudText ht = hud;
        Controls.Layout l = laid;
        float dp = l.dp;
        hudAt(t, HudText.GOJO, barL1 * s, barTop * s - 2 * dp, false, 0xFFEFF2FF);
        hudAt(t, HudText.SUKUNA, barR2 * s, barTop * s - 2 * dp, true, 0xFFEFF2FF);
        hudCenter(t, HudText.DIFF_TITLE, VW * 0.5f * s, l.hudTop + 1 * dp, difficulty.accent);
        hudCenter(t, HudText.DIFF_TIER, VW * 0.5f * s, l.hudTop + 19 * dp, 0xCCEFF2FF);
        for (int i = 0; i < Controls.BUTTONS; i++) {
            if (cooldown(w, i) > 0) continue;
            int id = HudText.BTN_MELEE + i;
            int color = i == Controls.BLUE ? 0xFFD8F5FF : i == Controls.RED ? 0xFFFFC2B0 : i == Controls.PURPLE ? 0xFFF2C7FF : i == Controls.DOMAIN ? VOID_LIGHT : 0xFFEFF2FF;
            t.hud(ht.x[id], ht.y[id], ht.w[id], ht.h[id], l.bx[i] - ht.w[id] * 0.5f, l.by[i] - ht.h[id] * 0.5f, color);
        }
        // 200% 茈的咒词：念到哪段，五条悟头上就亮哪段，念过的留在上面
        Fighter g = w.gojo;
        if (g.state == Fighter.PURPLE_CHANT && w.chantPhase >= 0) {
            int ph = Math.min(3, w.chantPhase);
            float local = (g.stateTick - ph * Tuning.CHANT_PHRASE) / (float) Tuning.CHANT_PHRASE;
            float cx = Math.max(60 * dp, Math.min(laidW - 60 * dp, gojoSX * s));
            float y = Math.max(l.hudTop + 44 * dp, (gojoSY - 62) * s - ht.h[HudText.CHANT0]);
            float step = ht.h[HudText.CHANT0] * 0.8f;
            for (int k = 0; k < ph; k++) hudCenter(t, HudText.CHANT0 + k, cx, y - (ph - k) * step, 0x80D9C2F2);
            hudCenter(t, HudText.CHANT0 + ph, cx, y - 4 * dp * Math.max(0, 1 - local * 6), withAlpha(0xFFF7E6FF, Math.min(1, local * 6)));
        }
        // 宿傩头上：世界斩的咏唱（念到哪段亮哪段）；领域展延、反转术式、适应完成的小字
        Fighter k = w.sukuna;
        float kcx = Math.max(60 * dp, Math.min(laidW - 60 * dp, sukunaSX * s)), ktop = Math.max(l.hudTop + 44 * dp, (sukunaSY - 76) * s);
        if (k.state == Fighter.S_WORLD && w.worldWindup == Tuning.S_WORLD_WINDUP) {
            int tt = k.stateTick - Tuning.S_WORLD_SIGN;
            if (tt >= 0) {
                int ph = Math.min(2, tt / Tuning.S_WORLD_CHANT_PHRASE);
                float local = (tt - ph * Tuning.S_WORLD_CHANT_PHRASE) / (float) Tuning.S_WORLD_CHANT_PHRASE;
                float step = ht.h[HudText.WORLD_CHANT0] * 0.8f, y = ktop - ht.h[HudText.WORLD_CHANT0];
                for (int q = 0; q < ph; q++) hudCenter(t, HudText.WORLD_CHANT0 + q, kcx, y - (ph - q) * step, 0x80F2B8B8);
                hudCenter(t, HudText.WORLD_CHANT0 + ph, kcx, y, withAlpha(0xFFFFE0E0, Math.min(1, local * 6)));
            }
        } else {
            int label = -1;
            float lt = 99;
            if (time - adaptedT0 < 1.2f) {
                label = HudText.ADAPTED;
                lt = time - adaptedT0;
            } else if (time - rctT0 < 1.0f) {
                label = HudText.RCT;
                lt = time - rctT0;
            } else if (time - ampT0 < 1.0f) {
                label = HudText.AMP;
                lt = time - ampT0;
            }
            if (label >= 0) {
                float a = lt < 0.8f ? 1 : Math.max(0, (1.2f - lt) / 0.4f);
                hudCenter(t, label, kcx, ktop - ht.h[label], withAlpha(label == HudText.ADAPTED ? 0xFFFFF0B8 : 0xFFEFF2FF, a));
            }
        }
        // 解：宿傩头上一个“解”字
        float dt0 = time - dismantleT0;
        if (dt0 >= 0 && dt0 < 0.5f) {
            float a = dt0 < 0.35f ? 1 : (0.5f - dt0) / 0.15f;
            hudCenter(t, HudText.DISMANTLE, sukunaSX * s, (sukunaSY - 60) * s - ht.h[HudText.DISMANTLE], withAlpha(0xFFFFE0E0, a));
        }
        float tt = time - bannerT0;
        if (bannerSub >= 0 && tt <= 1.05f) {
            float a = tt < 0.75f ? 1 : (1.05f - tt) / 0.3f;
            hudCenter(t, bannerSub, VW * 0.5f * s, Math.round(vh * 0.24f) * s - ht.h[bannerSub] + 2 * dp, withAlpha(0xFFEFF2FF, a));
        }
    }

    private void hudAt(PixelTarget t, int id, float x, float bottom, boolean alignRight, int color) {
        HudText ht = hud;
        float left = alignRight ? x - ht.w[id] : x;
        t.hud(ht.x[id], ht.y[id], ht.w[id], ht.h[id], left, bottom - ht.h[id], color);
    }

    private void hudCenter(PixelTarget t, int id, float cx, float top, int color) {
        HudText ht = hud;
        t.hud(ht.x[id], ht.y[id], ht.w[id], ht.h[id], cx - ht.w[id] * 0.5f, top, color);
    }

    /* ---------------- 小工具 ---------------- */

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    static int withAlpha(int argb, float a) {
        int ai = (int) (Math.max(0, Math.min(1, a)) * 255);
        return (ai << 24) | (argb & 0xFFFFFF);
    }

    private static int gray(int v) {
        v = Math.min(255, v);
        return (v << 16) | (v << 8) | v;
    }

    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static float unit(long z) {
        return (z >>> 40) / (float) (1L << 24);
    }
}
