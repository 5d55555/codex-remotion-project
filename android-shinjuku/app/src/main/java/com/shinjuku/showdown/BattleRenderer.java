package com.shinjuku.showdown;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;

import com.shinjuku.showdown.core.Attack;
import com.shinjuku.showdown.core.BlueOrb;
import com.shinjuku.showdown.core.Events;
import com.shinjuku.showdown.core.Fighter;
import com.shinjuku.showdown.core.Tuning;
import com.shinjuku.showdown.core.World;

/**
 * 战斗画面。只在战斗线程上用；构造之后每帧零分配（所有 Paint、数组、文字缓冲都预先建好）。
 * 坐标：先把画布缩放到视图像素（Surface 可能用了更小的缓冲区），世界坐标 (x, 离地高度 y) → 屏幕 (x·ppu, 地面 − y·ppu)。
 * 层次：背景 → 雨 → 影子 → 苍的光晕和核心 → 宿傩 → 五条悟 → 苍的旋臂、特效、伤害数字 → 全屏闪 → 横幅 → HUD 和按键。
 */
final class BattleRenderer {
    private static final float VIS = 1.2f;           // 角色画得比判定框稍大
    private static final float GROUND = 0.86f;       // 背景图里脚底所在的高度比例（湿路面上）
    private static final int BLUE = 0xFF6FD6FF, RED = 0xFFFF3B4E, PURPLE = 0xFFA98BFF;

    private final Controls controls;
    private final Difficulty difficulty;
    private final Bgm bgm;                           // 测试里为 null
    private final Bitmap bg, musicIcon;
    private final ChibiSprite gojoSprite, sukunaSprite;
    private final Rig.Pose pose = new Rig.Pose();
    private final String diffTitle, diffTier;
    volatile boolean statsOn;

    // 相机和布局（尺寸变化时重算）
    private Controls.Layout laid;
    private int laidW;
    private float k = 1, ppu, groundY, dp;
    private final RectF bgDst = new RectF(), tmp = new RectF();

    // 画笔
    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rain = new Paint();
    private final Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPurple = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowRed = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barGojo = new Paint(), barSukuna = new Paint(), vignette = new Paint();

    // 雨
    private static final int DROPS = 70;
    private final float[] dropX = new float[DROPS], dropPhase = new float[DROPS], dropSpeed = new float[DROPS];
    private final float[] rainPts = new float[DROPS * 4];

    // 苍的向心流线
    private static final int STREAKS = 18;
    private final float[] streakA = new float[STREAKS], streakPh = new float[STREAKS];
    private final float[] streakPts = new float[STREAKS * 4];

    // 黑闪的闪电（单位长度的形状，画的时候换算成屏幕坐标）
    private static final int BOLTS = 8, SEG = 5, BRANCH = 2;
    private final float[] boltShape = new float[BOLTS * (SEG + BRANCH) * 4];
    private final float[] boltPts = new float[BOLTS * (SEG + BRANCH) * 4];
    private final float[] sparkPts = new float[10 * 4];

    // 特效池
    private static final int FX = 40;
    private static final int FX_SPARK = 1, FX_RED_WAVE = 2, FX_BOLTS = 3, FX_IMPLODE = 4, FX_POP = 5, FX_RIPPLE = 6, FX_KO = 7, FX_REVIVE = 8;
    private final int[] fxType = new int[FX];
    private final float[] fxX = new float[FX], fxY = new float[FX], fxT0 = new float[FX], fxDur = new float[FX], fxVal = new float[FX];

    // 伤害数字
    private static final int NUMS = 20;
    private final float[] numX = new float[NUMS], numY = new float[NUMS], numT0 = new float[NUMS];
    private final int[] numVal = new int[NUMS], numKind = new int[NUMS];
    private int numNext;

    // 横幅、全屏闪、震屏
    private String bannerSmall, bannerBig;
    private int bannerColor;
    private float bannerT0 = -99, flashT0 = -99, shakeAmp, shakeT0 = -99, koT0 = -99, reviveT0 = -99;

    // 血条的“残血”拖尾
    private float gojoLag = 1, sukunaLag = 1, lastTime;
    private long seenEvents;

    // 帧率面板
    private final char[] buf = new char[64];
    private int len;
    private float fpsAvg = 60, tickMs, drawMs;

    BattleRenderer(Resources res, Difficulty difficulty, Controls controls, Bgm bgm, boolean hardwareBitmaps) {
        this.difficulty = difficulty;
        this.controls = controls;
        this.bgm = bgm;
        diffTitle = difficulty.title;
        diffTier = difficulty.tier;
        BitmapFactory.Options o = new BitmapFactory.Options();
        if (hardwareBitmaps) {
            o.inPreferredConfig = Bitmap.Config.HARDWARE; // 直接放在显存里，不占 Java 堆，也不用每次上传
        }
        bg = BitmapFactory.decodeResource(res, R.drawable.bg_battle, o);
        musicIcon = BitmapFactory.decodeResource(res, R.drawable.icon_music, o);
        gojoSprite = new ChibiSprite(new Chibi(Chibi.GOJO));
        sukunaSprite = new ChibiSprite(new Chibi(Chibi.SUKUNA));

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        rain.setStrokeCap(Paint.Cap.ROUND);
        text.setTypeface(Typeface.DEFAULT_BOLD);
        textStroke.setTypeface(Typeface.DEFAULT_BOLD);
        textStroke.setStyle(Paint.Style.STROKE);
        textStroke.setStrokeJoin(Paint.Join.ROUND);
        glow.setShader(new RadialGradient(0, 0, 1, new int[]{0xCC8FE3FF, 0x553A7BFF, 0x00000000}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
        glowPurple.setShader(new RadialGradient(0, 0, 1, new int[]{0xCCC9B5FF, 0x666A3BFF, 0x00000000}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
        glowRed.setShader(new RadialGradient(0, 0, 1, new int[]{0xEEFFD0C0, 0x99FF3B4E, 0x00000000}, new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));

        // 固定种子的“随机”：每次进来画面都一样
        long seed = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < DROPS; i++) {
            seed = mix(seed);
            dropX[i] = unit(seed);
            seed = mix(seed);
            dropPhase[i] = unit(seed);
            seed = mix(seed);
            dropSpeed[i] = 0.9f + 0.6f * unit(seed);
        }
        for (int i = 0; i < STREAKS; i++) {
            seed = mix(seed);
            streakA[i] = (float) (i * Math.PI * 2 / STREAKS + unit(seed) * 0.3);
            seed = mix(seed);
            streakPh[i] = unit(seed);
        }
        // 黑闪的闪电：从命中点往外，折线忽左忽右，中段长出一根分叉
        int o4 = 0;
        for (int b = 0; b < BOLTS; b++) {
            seed = mix(seed);
            double a = b * Math.PI * 2 / BOLTS + unit(seed) * 0.5;
            seed = mix(seed);
            float reach = 0.55f + 0.45f * unit(seed);
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            float px = ca * 0.08f, py = sa * 0.08f, bx = 0, by = 0;
            for (int s = 1; s <= SEG; s++) {
                float r = 0.08f + (reach - 0.08f) * s / SEG;
                seed = mix(seed);
                float j = ((s & 1) == 0 ? 1 : -1) * (0.02f + 0.03f * unit(seed));
                float nx = ca * r - sa * j, ny = sa * r + ca * j;
                boltShape[o4++] = px;
                boltShape[o4++] = py;
                boltShape[o4++] = nx;
                boltShape[o4++] = ny;
                px = nx;
                py = ny;
                if (s == SEG / 2) {
                    bx = nx;
                    by = ny;
                }
            }
            seed = mix(seed);
            double ba = a + (unit(seed) > 0.5f ? 0.55 : -0.55);
            float bca = (float) Math.cos(ba), bsa = (float) Math.sin(ba);
            for (int s = 1; s <= BRANCH; s++) {
                seed = mix(seed);
                float j = ((s & 1) == 0 ? 1 : -1) * 0.03f * unit(seed);
                float nx = bx + bca * 0.12f * s - bsa * j, ny = by + bsa * 0.12f * s + bca * j;
                float lx = s == 1 ? bx : boltShape[o4 - 2], ly = s == 1 ? by : boltShape[o4 - 1];
                boltShape[o4++] = lx;
                boltShape[o4++] = ly;
                boltShape[o4++] = nx;
                boltShape[o4++] = ny;
            }
        }
    }

    void release() {
        // 位图交给 GC；硬件位图在这里主动释放显存
        if (bg != null) bg.recycle();
        if (musicIcon != null) musicIcon.recycle();
    }

    void reportFrame(long dtNs, float tickMsAvg, float drawMsAvg) {
        if (dtNs > 0) fpsAvg += (1e9f / dtNs - fpsAvg) * 0.05f;
        tickMs = tickMsAvg;
        drawMs = drawMsAvg;
    }

    /** 只读事件、生成特效，不画（截图测试里用来模拟“之前那些帧”）。 */
    void consume(World w, float time) {
        consumeEvents(w, time);
        lastTime = time;
    }

    /* ---------------- 每帧 ---------------- */

    void draw(Canvas c, World w, float alpha, float time, int surfaceW, int surfaceH) {
        Controls.Layout l = controls.layout;
        if (l == null) return;
        if (l != laid || surfaceW != laidW) relayout(l, surfaceW);
        float dt = Math.max(0, Math.min(0.1f, time - lastTime));
        lastTime = time;
        consumeEvents(w, time);

        c.save();
        c.scale(k, k);
        // 震屏
        float shake = 0, sx = 0, sy = 0;
        float st = time - shakeT0;
        if (st < 0.35f) {
            shake = shakeAmp * (1 - st / 0.35f);
            sx = (float) Math.sin(st * 90) * shake;
            sy = (float) Math.cos(st * 71) * shake * 0.6f;
        }
        c.save();
        c.translate(sx, sy);
        c.drawBitmap(bg, null, bgDst, bmp);
        drawRain(c, l, time);

        Fighter g = w.gojo, s = w.sukuna;
        float gx = lerp(g.px, g.x, alpha), gy = lerp(g.py, g.y, alpha);
        float kx = lerp(s.px, s.x, alpha), ky = lerp(s.py, s.y, alpha);
        shadow(c, gx, gy);
        shadow(c, kx, ky);
        BlueOrb b = w.blue;
        float bx = lerp(b.px, b.x, alpha), by = lerp(b.py, b.y, alpha);
        if (b.active()) blueBack(c, b, bx, by, time);

        poseSukuna(w, time);
        sukunaSprite.draw(c, X(kx), Y(ky), ppu * VIS, pose);
        poseGojo(w, time);
        gojoSprite.draw(c, X(gx), Y(gy), ppu * VIS, pose);

        if (b.active()) blueFront(c, b, bx, by, time);
        drawEffects(c, time);
        drawNumbers(c, time);
        c.restore();

        // 黑闪的全屏一暗
        float ft = time - flashT0;
        if (ft < 0.32f) {
            float f = 1 - ft / 0.32f;
            fill.setColor(argb(f * (ft < 0.06f ? 0.55f : 0.3f), ft < 0.06f ? 0x000000 : 0x5A0010));
            c.drawRect(0, 0, l.w, l.h, fill);
        }
        c.drawRect(0, l.h * 0.62f, l.w, l.h, vignette);
        drawBanner(c, l, time);
        drawHud(c, l, w, dt);
        drawControls(c, l, w);
        if (statsOn) drawStats(c, l);
        c.restore();
    }

    private void relayout(Controls.Layout l, int surfaceW) {
        laid = l;
        laidW = surfaceW;
        k = surfaceW / l.w;
        dp = l.dp;
        ppu = l.w / Tuning.WIDTH;
        float s = Math.max(l.w / bg.getWidth(), l.h / bg.getHeight()) * 1.02f; // 多 2%：震屏时不露边
        float bw = bg.getWidth() * s, bh = bg.getHeight() * s;
        bgDst.set((l.w - bw) / 2, (l.h - bh) / 2, (l.w + bw) / 2, (l.h + bh) / 2);
        groundY = bgDst.top + bh * GROUND;
        gojoSprite.bake(ppu * VIS * k);
        sukunaSprite.bake(ppu * VIS * k);
        rain.setStrokeWidth(1.3f * dp);
        rain.setColor(0x38C8D4FF);
        barGojo.setShader(new LinearGradient(l.gojoBarL, 0, l.gojoBarR, 0, 0xFF3FA8FF, 0xFF9FF0FF, Shader.TileMode.CLAMP));
        barSukuna.setShader(new LinearGradient(l.sukunaBarL, 0, l.sukunaBarR, 0, 0xFFFF6A5A, 0xFFB0101E, Shader.TileMode.CLAMP));
        vignette.setShader(new LinearGradient(0, l.h * 0.62f, 0, l.h, 0x00000000, 0x8C04030C, Shader.TileMode.CLAMP));
    }

    private float X(float wx) {
        return wx * ppu;
    }

    private float Y(float wy) {
        return groundY - wy * ppu;
    }

    /* ---------------- 事件 → 特效 ---------------- */

    private void consumeEvents(World w, float time) {
        Events ev = w.events;
        long end = ev.written;
        if (end - seenEvents > Events.CAP) seenEvents = end - Events.CAP;
        for (; seenEvents < end; seenEvents++) {
            int i = (int) (seenEvents & Events.MASK);
            float x = ev.x[i], y = ev.y[i];
            switch (ev.type[i]) {
                case Events.HIT:
                    if (ev.kind[i] == Attack.MELEE) {
                        spawn(FX_SPARK, x, y, 0.22f, 0, time);
                        shake(2.5f, time);
                    } else if (ev.kind[i] == Attack.RED) {
                        spawn(FX_SPARK, x, y, 0.3f, 1, time);
                    }
                    break;
                case Events.BLACK_FLASH:
                    spawn(FX_BOLTS, x, y, 0.42f, 0, time);
                    spawn(FX_SPARK, x, y, 0.3f, 2, time);
                    flashT0 = time;
                    shake(9, time);
                    banner(null, "黑闪", RED, time);
                    break;
                case Events.RED_BURST:
                    spawn(FX_RED_WAVE, x, y, 0.42f, ev.value[i], time);
                    shake(6, time);
                    break;
                case Events.CAST:
                    if (ev.kind[i] == Attack.RED) banner("术式反转", "「赫」", RED, time);
                    else if (ev.value[i] > 0) banner("术式顺转 · 出力最大", "「苍」", PURPLE, time);
                    else banner("术式顺转", "「苍」", BLUE, time);
                    break;
                case Events.BLUE_LAND:
                    spawn(FX_IMPLODE, x, y, 0.3f, ev.value[i], time);
                    break;
                case Events.BLUE_END:
                    spawn(FX_POP, x, y, 0.28f, 0, time);
                    break;
                case Events.INFINITY_BLOCK:
                    spawn(FX_RIPPLE, x, y, 0.5f, 0, time);
                    break;
                case Events.DAMAGE:
                    number(x, y, Math.round(ev.value[i]), ev.kind[i], time);
                    break;
                case Events.KO:
                    koT0 = time;
                    spawn(FX_KO, x, y, 1.2f, 0, time);
                    shake(5, time);
                    break;
                case Events.REVIVE:
                    reviveT0 = time;
                    spawn(FX_REVIVE, x, y, 0.5f, 0, time);
                    break;
                default:
                    break;
            }
        }
    }

    private void spawn(int type, float wx, float wy, float dur, float val, float time) {
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
        fxX[slot] = wx;
        fxY[slot] = wy;
        fxT0[slot] = time;
        fxDur[slot] = dur;
        fxVal[slot] = val;
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

    private void banner(String small, String big, int color, float time) {
        bannerSmall = small;
        bannerBig = big;
        bannerColor = color;
        bannerT0 = time;
    }

    private void shake(float amountDp, float time) {
        float a = amountDp * dp;
        if (time - shakeT0 < 0.35f && shakeAmp * (1 - (time - shakeT0) / 0.35f) > a) return;
        shakeAmp = a;
        shakeT0 = time;
    }

    /* ---------------- 场景 ---------------- */

    private void drawRain(Canvas c, Controls.Layout l, float time) {
        float len = l.h * 0.045f, slant = 0.16f;
        for (int i = 0; i < DROPS; i++) {
            float f = (dropPhase[i] + time * dropSpeed[i] * 1.15f) % 1.15f - 0.08f;
            float y = f * l.h;
            float x = (dropX[i] * (l.w + l.h * slant) - y * slant);
            int o = i * 4;
            rainPts[o] = x;
            rainPts[o + 1] = y;
            rainPts[o + 2] = x - len * slant;
            rainPts[o + 3] = y + len;
        }
        c.drawLines(rainPts, rain);
    }

    private void shadow(Canvas c, float wx, float wy) {
        float s = 1 - Math.min(0.6f, wy / 300f);
        float hw = 24 * VIS * ppu * s, hh = 5 * ppu * s;
        fill.setColor(argb(0.45f * s, 0x000000));
        tmp.set(X(wx) - hw, groundY - hh, X(wx) + hw, groundY + hh);
        c.drawOval(tmp, fill);
    }

    /** 苍：作用范围、向心流线、光晕、核心（画在人后面）。 */
    private void blueBack(Canvas c, BlueOrb b, float wx, float wy, float time) {
        float cx = X(wx), cy = Y(wy);
        if (b.phase == BlueOrb.FLY) {
            float r = (8 + 10 * Math.min(1, b.tick / (float) Tuning.BLUE_FLY)) * ppu;
            stroke.setStrokeWidth(r * 0.9f);
            stroke.setColor(argb(0.35f, b.charged ? 0xA98BFF : 0x6FD6FF));
            c.drawLine(X(b.ox), Y(b.oy), cx, cy, stroke);
            drawGlow(c, cx, cy, r * 2.6f, b.charged ? glowPurple : glow);
            core(c, cx, cy, r, time);
            return;
        }
        float grow = Math.min(1, b.tick / 10f) * Math.min(1, (Tuning.BLUE_LIFE - b.tick) / 15f + 0.2f);
        float R = b.radius * ppu;
        int tint = b.charged ? 0xA98BFF : 0x6FD6FF;
        // 范围
        stroke.setStrokeWidth(1.5f * dp);
        stroke.setColor(argb(0.22f * grow, tint));
        c.drawCircle(cx, cy, R * (0.98f + 0.02f * (float) Math.sin(time * 6)), stroke);
        // 向心流线：从范围边缘往中心收
        for (int i = 0; i < STREAKS; i++) {
            float f = (streakPh[i] + time * 1.25f) % 1f;
            float r0 = R * (1 - f) * grow + 6 * dp, r1 = r0 + (10 + 26 * (1 - f)) * dp;
            double a = streakA[i] + time * 0.9;
            float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
            int o = i * 4;
            streakPts[o] = cx + ca * r0;
            streakPts[o + 1] = cy + sa * r0;
            streakPts[o + 2] = cx + ca * r1;
            streakPts[o + 3] = cy + sa * r1;
        }
        stroke.setStrokeWidth(5 * dp);
        stroke.setColor(argb(0.22f * grow, tint));
        c.drawLines(streakPts, stroke);
        stroke.setStrokeWidth(1.8f * dp);
        stroke.setColor(argb(0.85f * grow, b.charged ? 0xE6DEFF : 0xD6F6FF));
        c.drawLines(streakPts, stroke);
        float coreR = (b.charged ? 30 : 20) * ppu * grow;
        drawGlow(c, cx, cy, coreR * 3.4f, b.charged ? glowPurple : glow);
    }

    private void core(Canvas c, float cx, float cy, float r, float time) {
        fill.setColor(0xFF0A0E36);
        c.drawCircle(cx, cy, r, fill);
        stroke.setStrokeWidth(Math.max(2 * dp, r * 0.16f));
        stroke.setColor(0xFF9FE8FF);
        c.drawCircle(cx, cy, r, stroke);
        fill.setColor(0xCCFFFFFF);
        c.drawCircle(cx - r * 0.3f, cy - r * 0.3f, r * 0.18f, fill);
    }

    /** 苍：核心和绕着它转的旋臂（画在人前面，被抓住的人像是被卷在里面）。 */
    private void blueFront(Canvas c, BlueOrb b, float wx, float wy, float time) {
        if (b.phase != BlueOrb.PULL) return;
        float cx = X(wx), cy = Y(wy);
        float grow = Math.min(1, b.tick / 10f) * Math.min(1, (Tuning.BLUE_LIFE - b.tick) / 15f + 0.2f);
        float coreR = (b.charged ? 30 : 20) * ppu * grow * (1 + 0.06f * (float) Math.sin(time * 22));
        core(c, cx, cy, coreR, time);
        float life = b.tick / (float) Tuning.BLUE_LIFE;
        if (life > 0.9f) {
            fill.setColor(argb((life - 0.9f) * 6, 0xFFFFFF));
            c.drawCircle(cx, cy, coreR * 0.7f, fill);
        }
        float r = (b.charged ? 30 : 20) * ppu * grow * 1.7f;
        stroke.setStrokeWidth(3 * dp);
        stroke.setColor(b.charged ? 0xCCE2D8FF : 0xCCCFF4FF);
        for (int i = 0; i < 3; i++) {
            float rr = r * (1 + 0.18f * i);
            tmp.set(cx - rr, cy - rr * 0.8f, cx + rr, cy + rr * 0.8f);
            c.drawArc(tmp, time * (520 - 90 * i) + i * 120, 70 - 12 * i, false, stroke);
        }
    }

    private void drawGlow(Canvas c, float cx, float cy, float r, Paint p) {
        c.save();
        c.translate(cx, cy);
        c.scale(r, r);
        c.drawCircle(0, 0, 1, p);
        c.restore();
    }

    private void drawEffects(Canvas c, float time) {
        for (int i = 0; i < FX; i++) {
            if (fxType[i] == 0) continue;
            float f = (time - fxT0[i]) / fxDur[i];
            if (f >= 1) {
                fxType[i] = 0;
                continue;
            }
            float x = X(fxX[i]), y = Y(fxY[i]);
            switch (fxType[i]) {
                case FX_SPARK: {
                    int color = fxVal[i] == 1 ? 0xFFFF8A70 : fxVal[i] == 2 ? 0xFFFF2B3D : 0xFFFFF4D6;
                    float r0 = (6 + 30 * f) * dp, r1 = r0 + (16 * (1 - f) + 4) * dp;
                    for (int s = 0; s < 10; s++) {
                        double a = s * Math.PI / 5 + fxT0[i] * 7;
                        float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                        sparkPts[s * 4] = x + ca * r0;
                        sparkPts[s * 4 + 1] = y + sa * r0;
                        sparkPts[s * 4 + 2] = x + ca * r1;
                        sparkPts[s * 4 + 3] = y + sa * r1;
                    }
                    stroke.setStrokeWidth(3 * dp * (1 - f) + 1);
                    stroke.setColor((color & 0xFFFFFF) | ((int) (255 * (1 - f)) << 24));
                    c.drawLines(sparkPts, stroke);
                    fill.setColor(argb(0.8f * (1 - f), 0xFFFFFF));
                    c.drawCircle(x, y, 10 * dp * (1 - f), fill);
                    break;
                }
                case FX_RED_WAVE: {
                    float e = 1 - (1 - f) * (1 - f);
                    float R = Tuning.RED_REACH * ppu * (0.25f + 0.9f * e);
                    float cx = x + fxVal[i] * R * 0.55f;
                    drawGlow(c, cx, y, R * 0.55f * (1 - f) + 1, glowRed);
                    // 冲击波只朝前方展开（不从五条悟身上扫过去）
                    float start = fxVal[i] > 0 ? -95 : 85;
                    stroke.setStrokeWidth(12 * dp * (1 - f) + 1);
                    stroke.setColor(argb(1 - f, 0xFF5A4A));
                    tmp.set(x - R * 0.6f, y - R, x + R * 0.6f, y + R);
                    tmp.offset(fxVal[i] * R * 0.2f, 0);
                    c.drawArc(tmp, start, 190, false, stroke);
                    stroke.setStrokeWidth(3 * dp * (1 - f) + 1);
                    stroke.setColor(argb(1 - f, 0xFFE8D0));
                    tmp.inset(R * 0.15f, R * 0.25f);
                    c.drawArc(tmp, start + 15, 160, false, stroke);
                    break;
                }
                case FX_BOLTS: {
                    float R = (60 + 90 * Math.min(1, f * 4)) * dp;
                    double rot = fxT0[i] * 13;
                    float ca = (float) Math.cos(rot), sa = (float) Math.sin(rot);
                    for (int p = 0; p < boltShape.length; p += 2) {
                        float bx = boltShape[p], by = boltShape[p + 1];
                        boltPts[p] = x + (bx * ca - by * sa) * R;
                        boltPts[p + 1] = y + (bx * sa + by * ca) * R;
                    }
                    float a = f < 0.5f ? 1 : (1 - f) * 2;
                    // 红色的光晕在下，黑色的芯在上：黑色的闪电带着红边
                    stroke.setStrokeWidth(11 * dp);
                    stroke.setColor(argb(0.55f * a, 0xFF1030));
                    c.drawLines(boltPts, stroke);
                    stroke.setStrokeWidth(5 * dp);
                    stroke.setColor(argb(a, 0xFF2B3D));
                    c.drawLines(boltPts, stroke);
                    stroke.setStrokeWidth(2.6f * dp);
                    stroke.setColor(argb(a, 0x020005));
                    c.drawLines(boltPts, stroke);
                    break;
                }
                case FX_IMPLODE: {
                    float R = Tuning.BLUE_RADIUS * (fxVal[i] > 0 ? 2 : 1) * ppu * (1 - f);
                    stroke.setStrokeWidth(4 * dp * (1 - f) + 1);
                    stroke.setColor(argb(0.8f * (1 - f), fxVal[i] > 0 ? 0xC9B5FF : 0x9FE8FF));
                    c.drawCircle(x, y, R, stroke);
                    break;
                }
                case FX_POP: {
                    stroke.setStrokeWidth(5 * dp * (1 - f) + 1);
                    stroke.setColor(argb(1 - f, 0xBFEFFF));
                    c.drawCircle(x, y, (10 + 60 * f) * dp, stroke);
                    break;
                }
                case FX_RIPPLE: {
                    for (int r = 0; r < 3; r++) {
                        float ff = f - r * 0.15f;
                        if (ff <= 0 || ff >= 1) continue;
                        stroke.setStrokeWidth(2 * dp);
                        stroke.setColor(argb(0.7f * (1 - ff), 0x9FE8FF));
                        c.drawCircle(x, y, (14 + 40 * ff) * dp, stroke);
                    }
                    break;
                }
                case FX_KO: {
                    float a = f < 0.75f ? 1 : (1 - f) * 4;
                    float pop = f < 0.1f ? 1.4f - 4 * f : 1;
                    outlined(c, "击倒", x, y - 80 * dp, 34 * dp * pop, 0xFFFFE6A8, a);
                    break;
                }
                case FX_REVIVE: {
                    stroke.setStrokeWidth(3 * dp * (1 - f) + 1);
                    stroke.setColor(argb(1 - f, 0xFF6A5A));
                    c.drawCircle(x, y, (20 + 50 * f) * dp, stroke);
                    break;
                }
                default:
                    break;
            }
        }
    }

    private void drawNumbers(Canvas c, float time) {
        for (int i = 0; i < NUMS; i++) {
            float f = (time - numT0[i]) / 0.9f;
            if (f < 0 || f >= 1 || numVal[i] <= 0) continue;
            int kind = numKind[i];
            float size = (kind == Attack.BLACK_FLASH ? 34 : kind == Attack.RED ? 28 : kind == Attack.BLUE_FIELD ? 17 : 21) * dp;
            int color = kind == Attack.BLACK_FLASH ? 0xFFFF2B3D : kind == Attack.RED ? 0xFFFF8A5A : kind == Attack.BLUE_FIELD ? 0xFF9FE8FF : 0xFFFFFFFF;
            float pop = f < 0.08f ? 1.35f - 4.4f * f : 1;
            float x = X(numX[i]) + (i % 3 - 1) * 10 * dp, y = Y(numY[i]) - 46 * dp * (1 - (1 - f) * (1 - f));
            len = 0;
            appendInt(numVal[i]);
            outlinedChars(c, x, y, size * pop, color, f < 0.7f ? 1 : (1 - f) / 0.3f);
        }
    }

    private void drawBanner(Canvas c, Controls.Layout l, float time) {
        float t = time - bannerT0;
        if (bannerBig == null || t > 1.05f) return;
        float a = t < 0.75f ? 1 : (1.05f - t) / 0.3f;
        float pop = t < 0.12f ? 1.3f - 2.5f * t : 1;
        float y = l.h * 0.3f;
        if (bannerSmall != null) outlined(c, bannerSmall, l.w * 0.5f, y - 44 * dp, 17 * dp, 0xFFEFF2FF, a);
        outlined(c, bannerBig, l.w * 0.5f, y, 46 * dp * pop, bannerColor, a);
    }

    /* ---------------- 姿势 ---------------- */

    private void poseGojo(World w, float time) {
        Fighter g = w.gojo;
        Rig.Pose p = pose;
        p.reset();
        p.face = g.facing;
        int st = g.stateTick;
        boolean air = !g.onGround;
        if (air) {
            p.legFront = 32;
            p.legBack = -14;
            p.armFront = 48;
            p.armBack = -34;
            p.squash = g.vy > 0 ? 1.04f : 0.98f;
        } else if (Math.abs(g.vx) > 30) {
            float ph = g.x * 0.045f;
            float sn = (float) Math.sin(ph);
            p.legFront = sn * 32;
            p.legBack = -sn * 32;
            p.armFront = -sn * 28;
            p.armBack = sn * 28;
            p.lean = 9;
            p.bob = Math.abs(sn) * 2.5f;
        } else {
            float br = (float) Math.sin(time * 2.6f);
            p.bob = br * 0.9f;
            p.armFront = 6 + br * 2;
            p.armBack = -6;
            p.head = (float) Math.sin(time * 1.3f) * 2;
        }
        switch (g.state) {
            case Fighter.MELEE: {
                int combo = (w.meleeCombo - 1) % 3;
                float wind = Math.min(1, st / (float) Tuning.MELEE_STARTUP);
                int hitEnd = Tuning.MELEE_STARTUP + Tuning.MELEE_ACTIVE;
                float back = st <= Tuning.MELEE_STARTUP ? 0 : st <= hitEnd ? 1 : 1 - (st - hitEnd) / (float) Tuning.MELEE_RECOVERY;
                boolean striking = st > Tuning.MELEE_STARTUP;
                if (combo == 0) {        // 直拳
                    p.armFront = striking ? 10 + 85 * back : -30 * wind;
                    p.armBack = -20;
                    p.lean = striking ? 14 * back : -5 * wind;
                    p.legFront = 18;
                    p.legBack = -16;
                } else if (combo == 1) { // 踢
                    p.legFront = striking ? 10 + 88 * back : 20 * wind;
                    p.legBack = -10;
                    p.armFront = 40;
                    p.armBack = -40;
                    p.lean = striking ? -14 * back : -4;
                } else {                 // 上勾
                    p.armFront = striking ? 10 + 150 * back : -25 * wind;
                    p.armBack = -25;
                    p.lean = striking ? 6 * back : -6 * wind;
                    p.squash = striking ? 1 + 0.04f * back : 0.97f;
                    p.legFront = 14;
                    p.legBack = -14;
                }
                break;
            }
            case Fighter.BLUE_HOLD:
                p.armFront = 85;
                p.armBack = -15;
                p.lean = 4;
                p.glow = 0.4f + 0.6f * Math.min(1, st / (float) Tuning.BLUE_HOLD_THRESHOLD);
                p.glowColor = BLUE;
                break;
            case Fighter.BLUE_WINDUP:
                if (w.pendingCharged()) {
                    p.armFront = 150;
                    p.armBack = 40;
                    p.glowColor = PURPLE;
                    p.squash = 1.02f;
                } else {
                    p.armFront = 85;
                    p.armBack = -15;
                    p.glowColor = BLUE;
                }
                p.glow = 1;
                p.lean = 4;
                break;
            case Fighter.BLUE_RECOVER:
                p.armFront = 100;
                p.armBack = -20;
                p.glow = 1 - st / 10f;
                p.glowColor = BLUE;
                p.lean = 8;
                break;
            case Fighter.RED_WINDUP:
                p.armFront = -30;
                p.armBack = 30;
                p.lean = -8;
                p.glow = Math.min(1, st / (float) Tuning.RED_WINDUP);
                p.glowColor = RED;
                break;
            case Fighter.RED_ACTIVE:
                p.armFront = 95;
                p.armBack = -30;
                p.lean = 16;
                p.legFront = 26;
                p.legBack = -22;
                p.glow = 1;
                p.glowColor = RED;
                break;
            case Fighter.RED_RECOVER: {
                float f = st / (float) Tuning.RED_RECOVERY;
                p.armFront = 95 - 85 * f;
                p.armBack = -30 * (1 - f);
                p.lean = 16 * (1 - f);
                p.legFront = 26 * (1 - f);
                p.legBack = -22 * (1 - f);
                p.glow = 1 - f;
                p.glowColor = RED;
                break;
            }
            default:
                break;
        }
    }

    private void poseSukuna(World w, float time) {
        Fighter s = w.sukuna;
        Rig.Pose p = pose;
        p.reset();
        p.face = s.facing;
        int since = w.tick - s.lastHitTick;
        p.hurt = Math.max(0, 1 - since / 10f);
        if (s.state == Fighter.KO) {
            float f = Math.min(1, (time - koT0) / 0.25f);
            p.spin = -84 * f;
            p.bob = -38 * f;
            p.armFront = 60;
            p.armBack = -50;
            p.legFront = 12;
            p.legBack = -8;
            return;
        }
        float rv = time - reviveT0;
        if (rv < 0.3f) p.squash = 0.94f + 0.2f * rv;
        BlueOrb b = w.blue;
        boolean inBlue = false;
        if (b.phase == BlueOrb.PULL) {
            float dx = b.cx - s.x, dy = b.cy - s.centerY();
            inBlue = s.captured || dx * dx + dy * dy < b.radius * b.radius;
        }
        if (s.captured) {
            p.armFront = 120 + (float) Math.sin(time * 11) * 14;
            p.armBack = 110 - (float) Math.sin(time * 9) * 14;
            p.legFront = 30;
            p.legBack = -30;
            p.spin = (float) Math.sin(time * 7) * 14;
            p.head = -10;
            return;
        }
        boolean air = s.y > 1;
        if (air && s.hitstun > 0 && s.lastHitKind == Attack.RED) {
            p.spin = -360 * Math.min(1, since / 40f); // 被赫打飞：翻一个跟头
            p.armFront = 100;
            p.armBack = 80;
            p.legFront = 40;
            p.legBack = -20;
            return;
        }
        if (inBlue) {
            p.lean = -18;
            p.armFront = 70 + (float) Math.sin(time * 14) * 20;
            p.armBack = -50;
            p.legFront = 22;
            p.legBack = -24;
            return;
        }
        if (s.hitstun > 0) {
            float f = Math.min(1, s.hitstun / 18f);
            p.lean = -16 * f;
            p.head = -12 * f;
            p.armFront = 50 * f;
            p.armBack = -40 * f;
            if (air) {
                p.legFront = 30;
                p.legBack = -10;
            }
            return;
        }
        float br = (float) Math.sin(time * 2.2f + 1);
        p.bob = br * 0.8f;
        p.armFront = 8 + br * 2;
        p.armBack = -8;
    }

    /* ---------------- HUD 和按键 ---------------- */

    private void drawHud(Canvas c, Controls.Layout l, World w, float dt) {
        float gHp = w.gojo.hpMilli / (float) w.gojo.maxHpMilli;
        float sHp = w.sukuna.hpMilli / (float) w.sukuna.maxHpMilli;
        gojoLag = lag(gojoLag, gHp, dt);
        sukunaLag = lag(sukunaLag, sHp, dt);
        float top = l.hudTop + 18 * dp, bot = top + l.barH;
        bar(c, l.gojoBarL, top, l.gojoBarR, bot, gHp, gojoLag, barGojo, false);
        bar(c, l.sukunaBarL, top, l.sukunaBarR, bot, sHp, sukunaLag, barSukuna, true);

        // 文字都用描边衬底（不用 setShadowLayer：那个每帧都要对字形做模糊）
        hudText(c, "五条悟", l.gojoBarL + 2 * dp, top - 5 * dp, 13 * dp, 0xFFEFF2FF, Paint.Align.LEFT);
        hudText(c, "两面宿傩", l.sukunaBarR - 2 * dp, top - 5 * dp, 13 * dp, 0xFFEFF2FF, Paint.Align.RIGHT);

        len = 0;
        appendInt((int) (w.gojo.hpMilli / 1000));
        append(" / ");
        appendInt((int) (w.gojo.maxHpMilli / 1000));
        hudChars(c, l.gojoBarR - 4 * dp, bot - 2.5f * dp, 10 * dp, 0xF2FFFFFF, Paint.Align.RIGHT);
        len = 0;
        appendInt((int) ((w.sukuna.hpMilli + 999) / 1000));
        append(" / ");
        appendInt((int) (w.sukuna.maxHpMilli / 1000));
        hudChars(c, l.sukunaBarL + 4 * dp, bot - 2.5f * dp, 10 * dp, 0xF2FFFFFF, Paint.Align.LEFT);

        // 难度
        hudText(c, diffTitle, l.w * 0.5f, l.hudTop + 15 * dp, 16 * dp, difficulty.accent, Paint.Align.CENTER);
        hudText(c, diffTier, l.w * 0.5f, l.hudTop + 30 * dp, 10 * dp, 0xCCEFF2FF, Paint.Align.CENTER);

        // 返回
        roundButton(c, l.backX, l.backY, l.smallR, controls.hudPressed == 0);
        stroke.setStrokeWidth(2.4f * dp);
        stroke.setColor(0xFFEFF2FF);
        float ax = l.backX, ay = l.backY, s = 7 * dp;
        c.drawLine(ax + s, ay, ax - s, ay, stroke);
        c.drawLine(ax - s, ay, ax - s + 6 * dp, ay - 6 * dp, stroke);
        c.drawLine(ax - s, ay, ax - s + 6 * dp, ay + 6 * dp, stroke);

        // 音乐（关掉时变暗并划一道）
        boolean muted = bgm != null && bgm.isMuted();
        roundButton(c, l.musicX, l.musicY, l.smallR, controls.hudPressed == 1);
        float ir = l.smallR * 0.78f;
        tmp.set(l.musicX - ir, l.musicY - ir, l.musicX + ir, l.musicY + ir);
        bmp.setAlpha(muted ? 110 : 255);
        c.drawBitmap(musicIcon, null, tmp, bmp);
        bmp.setAlpha(255);
        if (muted) {
            stroke.setStrokeWidth(2.6f * dp);
            stroke.setColor(0xFFFF5A6A);
            c.drawLine(l.musicX - ir * 0.8f, l.musicY + ir * 0.8f, l.musicX + ir * 0.8f, l.musicY - ir * 0.8f, stroke);
        }
    }

    private static float lag(float lag, float cur, float dt) {
        if (cur >= lag) return cur;
        return Math.max(cur, lag - dt * 0.35f);
    }

    private void bar(Canvas c, float l, float t, float r, float b, float hp, float lag, Paint fillPaint, boolean fromRight) {
        float rad = (b - t) * 0.5f;
        tmp.set(l - 2 * dp, t - 2 * dp, r + 2 * dp, b + 2 * dp);
        fill.setColor(0xB3070812);
        c.drawRoundRect(tmp, rad + 2 * dp, rad + 2 * dp, fill);
        float wTot = r - l;
        if (lag > hp) {
            fill.setColor(0xCCFFE6A8);
            if (fromRight) tmp.set(r - wTot * lag, t, r, b);
            else tmp.set(l, t, l + wTot * lag, b);
            c.drawRoundRect(tmp, rad, rad, fill);
        }
        if (hp > 0) {
            if (fromRight) tmp.set(r - wTot * hp, t, r, b);
            else tmp.set(l, t, l + wTot * hp, b);
            c.drawRoundRect(tmp, rad, rad, fillPaint);
        }
        stroke.setStrokeWidth(1.2f * dp);
        stroke.setColor(0x80FFFFFF);
        tmp.set(l - 2 * dp, t - 2 * dp, r + 2 * dp, b + 2 * dp);
        c.drawRoundRect(tmp, rad + 2 * dp, rad + 2 * dp, stroke);
    }

    private void roundButton(Canvas c, float x, float y, float r, boolean pressed) {
        fill.setColor(pressed ? 0xCC2A2F4A : 0x99101222);
        c.drawCircle(x, y, r, fill);
        stroke.setStrokeWidth(1.4f * dp);
        stroke.setColor(0x80FFFFFF);
        c.drawCircle(x, y, r, stroke);
    }

    private void drawControls(Canvas c, Controls.Layout l, World w) {
        // 摇杆
        boolean active = controls.stickActive;
        float bx = active ? controls.stickBaseX : l.stickX, by = active ? controls.stickBaseY : l.stickY;
        fill.setColor(active ? 0x40101222 : 0x26101222);
        c.drawCircle(bx, by, l.stickR, fill);
        stroke.setStrokeWidth(1.6f * dp);
        stroke.setColor(active ? 0x99FFFFFF : 0x4DFFFFFF);
        c.drawCircle(bx, by, l.stickR, stroke);
        float kx = active ? controls.knobX : bx, ky = active ? controls.knobY : by;
        fill.setColor(active ? 0xCCE8EEFF : 0x66E8EEFF);
        c.drawCircle(kx, ky, l.knobR, fill);

        Fighter g = w.gojo;
        for (int i = 0; i < Controls.BUTTONS; i++) {
            float x = l.bx[i], y = l.by[i], r = l.br[i];
            boolean pressed = controls.held.get(i) != 0;
            int ring = i == Controls.BLUE ? BLUE : i == Controls.RED ? RED : 0xFFEFF2FF;
            c.save();
            if (pressed) c.scale(0.93f, 0.93f, x, y);
            fill.setColor(pressed ? 0xCC262B45 : 0x8C0D0F1C);
            c.drawCircle(x, y, r, fill);
            stroke.setStrokeWidth(2.2f * dp);
            stroke.setColor((ring & 0xFFFFFF) | (pressed ? 0xFF000000 : 0xB3000000));
            c.drawCircle(x, y, r, stroke);

            int cd = i == Controls.BLUE ? w.blueCd : i == Controls.RED ? w.redCd : 0;
            int cdMax = i == Controls.BLUE ? w.blueCdMax : Tuning.RED_COOLDOWN;
            text.setTextAlign(Paint.Align.CENTER);
            if (cd > 0) {
                fill.setColor(0x99000000);
                tmp.set(x - r, y - r, x + r, y + r);
                c.drawArc(tmp, -90, 360f * cd / cdMax, true, fill);
                len = 0;
                appendInt((cd + Tuning.HZ - 1) / Tuning.HZ);
                text.setTextSize(r * 0.75f);
                text.setColor(0xFFEFF2FF);
                c.drawText(buf, 0, len, x, y + r * 0.27f, text);
            } else {
                String label = Controls.LABELS[i];
                text.setTextSize(label.length() == 1 ? r * 0.85f : r * 0.5f);
                text.setColor(i == Controls.BLUE ? 0xFFBFEFFF : i == Controls.RED ? 0xFFFFB0B8 : 0xFFEFF2FF);
                c.drawText(label, x, y + text.getTextSize() * 0.36f, text);
            }
            // 苍：按住时显示长按进度，到了就是出力最大
            if (i == Controls.BLUE) {
                float prog = -1;
                boolean full = false;
                if (g.state == Fighter.BLUE_HOLD) prog = Math.min(1, g.stateTick / (float) Tuning.BLUE_HOLD_THRESHOLD);
                else if (g.state == Fighter.BLUE_WINDUP && w.pendingCharged()) {
                    prog = 1;
                    full = true;
                }
                if (prog > 0) {
                    stroke.setStrokeWidth(4 * dp);
                    stroke.setColor(full ? PURPLE : 0xFF9FE8FF);
                    float rr = r + 5 * dp;
                    tmp.set(x - rr, y - rr, x + rr, y + rr);
                    c.drawArc(tmp, -90, 360 * prog, false, stroke);
                }
            }
            c.restore();
        }
    }

    private void drawStats(Canvas c, Controls.Layout l) {
        len = 0;
        appendInt(Math.round(fpsAvg));
        append(" 帧/秒 · 逻辑 ");
        appendFixed2(tickMs);
        append(" ms · 绘制 ");
        appendFixed2(drawMs);
        append(" ms");
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(11 * dp);
        float tw = text.measureText(buf, 0, len);
        float y = l.hudTop + 48 * dp;
        fill.setColor(0xB3000000);
        tmp.set(l.w * 0.5f - tw * 0.5f - 8 * dp, y - 13 * dp, l.w * 0.5f + tw * 0.5f + 8 * dp, y + 5 * dp);
        c.drawRoundRect(tmp, 4 * dp, 4 * dp, fill);
        text.setColor(0xFF9FFF9F);
        c.drawText(buf, 0, len, l.w * 0.5f, y, text);
    }

    /* ---------------- 文字 ---------------- */

    private void hudText(Canvas c, String s, float x, float y, float size, int color, Paint.Align align) {
        textStroke.setTextSize(size);
        textStroke.setTextAlign(align);
        textStroke.setStrokeWidth(size * 0.22f);
        textStroke.setColor(0xB3000000);
        c.drawText(s, x, y, textStroke);
        text.setTextSize(size);
        text.setTextAlign(align);
        text.setColor(color);
        c.drawText(s, x, y, text);
    }

    private void hudChars(Canvas c, float x, float y, float size, int color, Paint.Align align) {
        textStroke.setTextSize(size);
        textStroke.setTextAlign(align);
        textStroke.setStrokeWidth(size * 0.22f);
        textStroke.setColor(0x99000000);
        c.drawText(buf, 0, len, x, y, textStroke);
        text.setTextSize(size);
        text.setTextAlign(align);
        text.setColor(color);
        c.drawText(buf, 0, len, x, y, text);
    }

    private void outlined(Canvas c, String s, float x, float y, float size, int color, float a) {
        textStroke.setTextSize(size);
        textStroke.setTextAlign(Paint.Align.CENTER);
        textStroke.setStrokeWidth(size * 0.16f);
        textStroke.setColor(argb(a, 0x05040A));
        c.drawText(s, x, y, textStroke);
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor((color & 0xFFFFFF) | ((int) (255 * a) << 24));
        c.drawText(s, x, y, text);
    }

    private void outlinedChars(Canvas c, float x, float y, float size, int color, float a) {
        textStroke.setTextSize(size);
        textStroke.setTextAlign(Paint.Align.CENTER);
        textStroke.setStrokeWidth(size * 0.18f);
        textStroke.setColor(argb(a, 0x05040A));
        c.drawText(buf, 0, len, x, y, textStroke);
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.CENTER);
        text.setColor((color & 0xFFFFFF) | ((int) (255 * a) << 24));
        c.drawText(buf, 0, len, x, y, text);
    }

    private void append(String s) {
        for (int i = 0; i < s.length() && len < buf.length; i++) buf[len++] = s.charAt(i);
    }

    private void appendInt(int v) {
        if (v < 0) {
            if (len < buf.length) buf[len++] = '-';
            v = -v;
        }
        int start = len;
        do {
            if (len >= buf.length) return;
            buf[len++] = (char) ('0' + v % 10);
            v /= 10;
        } while (v > 0);
        for (int i = start, j = len - 1; i < j; i++, j--) {
            char t = buf[i];
            buf[i] = buf[j];
            buf[j] = t;
        }
    }

    private void appendFixed2(float v) {
        int x = Math.round(v * 100);
        appendInt(x / 100);
        if (len < buf.length) buf[len++] = '.';
        int f = x % 100;
        if (len < buf.length) buf[len++] = (char) ('0' + f / 10);
        if (len < buf.length) buf[len++] = (char) ('0' + f % 10);
    }

    /* ---------------- 小工具 ---------------- */

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static int argb(float a, int rgb) {
        int ai = (int) (Math.max(0, Math.min(1, a)) * 255);
        return (ai << 24) | (rgb & 0xFFFFFF);
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
