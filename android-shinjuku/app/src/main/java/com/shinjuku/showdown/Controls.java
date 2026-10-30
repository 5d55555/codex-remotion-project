package com.shinjuku.showdown;

import android.os.SystemClock;
import android.view.MotionEvent;

import com.shinjuku.showdown.core.TickInput;

import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * 触屏操作：左下浮动摇杆，右下 体术 / 跳 / 苍 / 赫 / 茈 / 领域 / 闪避 七个键，顶部 返回 和 音乐 两个小按钮。
 * 组合：按住跳时点体术是苍拳；按住闪避时点苍、点赫是瞬移。
 * 主线程处理触摸；战斗线程每个 tick 取一次（sample）。
 * 按键：主线程把“第几个键、按下还是松开、什么时候”写进预分配的事件环（单写单读，不加锁、不分配），
 * 战斗线程按时间顺序取。同一个键在一个 tick 里最多取一次按下加一次松开（点按不丢），再多的顺延到下一个 tick，
 * 所以战斗线程卡一下也不会把几次按键合并成一次，“按下→松开→再按住”的顺序也保得住。按住状态由事件推出来，和边沿一致。
 * 摇杆是连续量，只传最新值：两个分量打包在一个 volatile long 里，读到的总是同一次写入。
 */
final class Controls {
    static final int MELEE = 0, JUMP = 1, BLUE = 2, RED = 3, PURPLE = 4, DOMAIN = 5, DODGE = 6, BUTTONS = 7;

    /** 布局（视图像素）。尺寸或安全区变化时在主线程重建一个新的，渲染线程只读。 */
    static final class Layout {
        final float w, h, dp;
        final float safeL, safeT, safeR, safeB;
        final float stickX, stickY, stickR, knobR;   // 摇杆默认位置
        final float[] bx = new float[BUTTONS], by = new float[BUTTONS], br = new float[BUTTONS];
        final float backX, backY, musicX, musicY, smallR;
        final float hudTop, barH, gojoBarL, gojoBarR, sukunaBarL, sukunaBarR;

        Layout(float w, float h, float dp, float sl, float st, float sr, float sb) {
            this.w = w;
            this.h = h;
            this.dp = dp;
            safeL = sl;
            safeT = st;
            safeR = sr;
            safeB = sb;
            stickR = 58 * dp;
            knobR = 26 * dp;
            stickX = sl + 40 * dp + stickR;
            stickY = h - sb - 34 * dp - stickR;
            // 体术在右下角最大；跳、苍、茈、赫沿一道弧排在它的左上方（彼此留出空隙），领域在外圈
            float ax = w - sr - 34 * dp - 44 * dp, ay = h - sb - 30 * dp - 44 * dp;
            set(MELEE, ax, ay, 44 * dp);
            set(JUMP, ax - 110 * dp, ay + 19 * dp, 30 * dp);
            arc(BLUE, ax, ay, 125 * dp, 152, 31 * dp);
            arc(PURPLE, ax, ay, 125 * dp, 121, 31 * dp);
            arc(RED, ax, ay, 125 * dp, 90, 31 * dp);
            arc(DOMAIN, ax, ay, 196 * dp, 114, 27 * dp);
            arc(DODGE, ax, ay, 196 * dp, 152, 27 * dp);
            smallR = 19 * dp;
            backX = sl + 14 * dp + smallR;
            backY = st + 12 * dp + smallR;
            musicX = w - sr - 14 * dp - smallR;
            musicY = backY;
            hudTop = st + 12 * dp;
            barH = 13 * dp;
            gojoBarL = backX + smallR + 14 * dp;
            gojoBarR = w * 0.5f - 64 * dp;
            sukunaBarL = w * 0.5f + 64 * dp;
            sukunaBarR = musicX - smallR - 14 * dp;
        }

        private void arc(int i, float cx, float cy, float dist, float deg, float r) {
            double a = Math.toRadians(deg);
            set(i, cx + (float) Math.cos(a) * dist, cy - (float) Math.sin(a) * dist, r);
        }

        private void set(int i, float x, float y, float r) {
            bx[i] = x;
            by[i] = y;
            br[i] = r;
        }
    }

    interface Actions {
        void back();

        void toggleMusic();

        void toggleStats();
    }

    volatile Layout layout;

    // ---- 主线程写，渲染/游戏线程读 ----
    private volatile long stick;               // 高 32 位 x，低 32 位 y（向上为正），已过死区
    volatile boolean stickActive;
    volatile float stickBaseX, stickBaseY, knobX, knobY; // 画面用：摇杆底座和摇杆头的位置
    final AtomicIntegerArray held = new AtomicIntegerArray(BUTTONS); // 画面用：按键是否按着

    // 按键事件环：主线程写 evWrite，战斗线程写 evRead
    private static final int RING = 256, RMASK = RING - 1;
    private static final long FAR_AHEAD_NS = 250_000_000L; // 比逻辑时钟超前这么多：时钟对不上，直接处理
    private final int[] evCode = new int[RING];   // 键 × 2 + (按下 ? 1 : 0)
    private final long[] evTime = new long[RING]; // 纳秒，和 Choreographer 同一个时钟（CLOCK_MONOTONIC）
    private volatile long evWrite, evRead;
    private volatile boolean overflow;
    // 主线程“改按住状态 + 入队”在这把锁里一起做；战斗线程只在溢出恢复时拿它（平时不加锁）
    private final Object ringLock = new Object();
    volatile int hudPressed = -1;              // 0 返回，1 音乐，2 难度标签（长按开帧率面板）

    // ---- 只在主线程 ----
    private static final float DEADZONE = 0.12f;
    private final Actions actions;
    private int stickPointer = -1;
    private final int[] buttonPointer = {-1, -1, -1, -1, -1, -1, -1};
    private boolean blocked; // 放过场时：战斗不收触摸
    private int hudPointer = -1, hudTarget = -1;
    private long hudDownAt;

    // ---- 只在战斗线程 ----
    private int heldBits;

    Controls(Actions actions) {
        this.actions = actions;
    }

    /* ---------------- 战斗线程：每个 tick 取一次 ---------------- */

    /** 取发生在 limitNs 之前的按键事件（帧内最后一个 tick 传 Long.MAX_VALUE：已经到的全取，不加延迟）。 */
    void sample(TickInput in, long limitNs) {
        long s = stick;
        in.moveX = Float.intBitsToFloat((int) (s >>> 32));
        in.moveY = Float.intBitsToFloat((int) s);
        if (overflow) {
            // 事件环满过（几乎不可能）：丢掉积压的，按住状态以主线程为准。
            // 在锁里做：拿到的写指针和按住状态是同一时刻的，主线程也不会在中间插进新事件或新的溢出标记。
            discardPending();
        }
        int downs = 0, ups = 0;
        long r = evRead, w = evWrite;
        while (r < w) {
            int i = (int) (r & RMASK);
            long t = evTime[i];
            if (t > limitNs && t - limitNs < FAR_AHEAD_NS) break; // 属于以后的 tick
            int code = evCode[i], bit = 1 << (code >> 1);
            if ((code & 1) != 0) {
                if (((downs | ups) & bit) != 0) break; // 这个键这一 tick 已经有过按下/松开：顺延
                downs |= bit;
                heldBits |= bit;
            } else {
                if ((ups & bit) != 0) break;
                ups |= bit;
                heldBits &= ~bit;
            }
            r++;
        }
        evRead = r;
        in.melee = (downs & (1 << MELEE)) != 0;
        in.jump = (downs & (1 << JUMP)) != 0;
        in.red = (downs & (1 << RED)) != 0;
        in.purple = (downs & (1 << PURPLE)) != 0;
        in.domain = (downs & (1 << DOMAIN)) != 0;
        in.dodge = (downs & (1 << DODGE)) != 0;
        in.blueDown = (downs & (1 << BLUE)) != 0;
        in.blueUp = (ups & (1 << BLUE)) != 0;
        in.blueHeld = (heldBits & (1 << BLUE)) != 0;
        in.redHeld = (heldBits & (1 << RED)) != 0;
        in.purpleHeld = (heldBits & (1 << PURPLE)) != 0;
        in.jumpHeld = (heldBits & (1 << JUMP)) != 0;
        in.dodgeHeld = (heldBits & (1 << DODGE)) != 0;
    }

    /** 战斗线程：过场结束、接着打之前，把积压的按键事件全丢掉，按住状态以主线程为准（放过场时主线程已全部松开）。 */
    void discardPending() {
        synchronized (ringLock) {
            evRead = evWrite;
            overflow = false;
            heldBits = 0;
            for (int b = 0; b < BUTTONS; b++) if (held.get(b) != 0) heldBits |= 1 << b;
        }
    }

    /** 主线程：放过场时不收触摸（先把按着的全部松开），放完再收。 */
    void setBlocked(boolean b) {
        if (b) releaseAll();
        blocked = b;
    }

    /** 主线程：按键按下/松开。按住状态和事件一起改，溢出恢复时看到的两者一致。 */
    private void press(int button, boolean down, long timeMs) {
        synchronized (ringLock) {
            held.set(button, down ? 1 : 0);
            push(button, down, timeMs);
        }
    }

    /** 记一个按键事件（主线程，持有 ringLock；测试里直接调用）。 */
    void push(int button, boolean down, long timeMs) {
        long w = evWrite;
        if (w - evRead >= RING) {
            overflow = true;
            return;
        }
        int i = (int) (w & RMASK);
        evCode[i] = button * 2 + (down ? 1 : 0);
        evTime[i] = timeMs * 1_000_000L;
        evWrite = w + 1; // volatile 写：之前写进数组的内容对战斗线程可见
    }

    /* ---------------- 主线程：触摸 ---------------- */

    boolean onTouch(MotionEvent e) {
        Layout l = layout;
        if (l == null) return false;
        int action = e.getActionMasked();
        if (blocked) return true;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int i = e.getActionIndex();
                down(l, e.getPointerId(i), e.getX(i), e.getY(i), e.getEventTime());
                break;
            }
            case MotionEvent.ACTION_MOVE:
                if (stickPointer >= 0) {
                    int i = e.findPointerIndex(stickPointer);
                    if (i >= 0) moveStick(l, e.getX(i), e.getY(i));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP: {
                int i = e.getActionIndex();
                up(l, e.getPointerId(i), e.getX(i), e.getY(i), e.getEventTime(), false);
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                break;
            default:
                break;
        }
        return true;
    }

    private void down(Layout l, int id, float x, float y, long t) {
        // 先看按键（判定范围比画出来的大一圈），再看顶部小按钮，最后左半屏下方是摇杆
        int best = -1;
        float bestD = Float.MAX_VALUE;
        for (int b = 0; b < BUTTONS; b++) {
            float dx = x - l.bx[b], dy = y - l.by[b], r = l.br[b] * 1.3f;
            float d = dx * dx + dy * dy;
            if (d <= r * r && d < bestD && buttonPointer[b] < 0) {
                best = b;
                bestD = d;
            }
        }
        if (best >= 0) {
            buttonPointer[best] = id;
            press(best, true, t);
            return;
        }
        int hud = hudAt(l, x, y);
        if (hud >= 0 && hudPointer < 0) {
            hudPointer = id;
            hudTarget = hud;
            hudDownAt = t;
            hudPressed = hud;
            return;
        }
        if (stickPointer < 0 && x < l.w * 0.5f && y > l.hudTop + 56 * l.dp) {
            stickPointer = id;
            // 浮动摇杆：在手指按下的地方出现（底座整个留在屏幕里）
            float bx = Math.max(l.safeL + l.stickR, Math.min(x, l.w * 0.5f - l.stickR));
            float by = Math.max(l.hudTop + 56 * l.dp + l.stickR, Math.min(y, l.h - l.safeB - l.stickR));
            stickBaseX = bx;
            stickBaseY = by;
            stickActive = true;
            moveStick(l, x, y);
        }
    }

    private void moveStick(Layout l, float x, float y) {
        float dx = (x - stickBaseX) / l.stickR, dy = (y - stickBaseY) / l.stickR;
        float m = (float) Math.sqrt(dx * dx + dy * dy);
        if (m > 1) {
            dx /= m;
            dy /= m;
            m = 1;
        }
        knobX = stickBaseX + dx * l.stickR;
        knobY = stickBaseY + dy * l.stickR;
        float sx = 0, sy = 0;
        if (m > DEADZONE) {
            float k = (m - DEADZONE) / (1 - DEADZONE) / m;
            sx = dx * k;
            sy = -dy * k; // 屏幕向下，逻辑向上
        }
        setStick(sx, sy);
    }

    private void setStick(float x, float y) {
        stick = ((long) Float.floatToRawIntBits(x) << 32) | (Float.floatToRawIntBits(y) & 0xFFFFFFFFL);
    }

    private void up(Layout l, int id, float x, float y, long t, boolean cancel) {
        if (id == stickPointer) {
            stickPointer = -1;
            stickActive = false;
            setStick(0, 0);
            return;
        }
        for (int b = 0; b < BUTTONS; b++) {
            if (buttonPointer[b] == id) {
                buttonPointer[b] = -1;
                press(b, false, t);
                return;
            }
        }
        if (id == hudPointer) {
            int target = hudTarget;
            hudPointer = -1;
            hudTarget = -1;
            hudPressed = -1;
            if (cancel || hudAt(l, x, y) != target) return;
            if (target == 0) actions.back();
            else if (target == 1) actions.toggleMusic();
            else if (target == 2 && t - hudDownAt >= 500) actions.toggleStats();
        }
    }

    /** 触摸被系统收走（来电、手势导航）：全部松开。 */
    void releaseAll() {
        Layout l = layout;
        long t = SystemClock.uptimeMillis();
        if (stickPointer >= 0) up(l, stickPointer, 0, 0, t, true);
        for (int b = 0; b < BUTTONS; b++) if (buttonPointer[b] >= 0) up(l, buttonPointer[b], 0, 0, t, true);
        if (hudPointer >= 0) up(l, hudPointer, 0, 0, t, true);
    }

    private static int hudAt(Layout l, float x, float y) {
        float r = l.smallR * 1.5f;
        if (sq(x - l.backX) + sq(y - l.backY) <= r * r) return 0;
        if (sq(x - l.musicX) + sq(y - l.musicY) <= r * r) return 1;
        if (Math.abs(x - l.w * 0.5f) < 60 * l.dp && y < l.hudTop + 40 * l.dp) return 2;
        return -1;
    }

    private static float sq(float v) {
        return v * v;
    }
}
