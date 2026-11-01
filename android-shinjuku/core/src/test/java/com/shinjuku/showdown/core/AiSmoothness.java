package com.shinjuku.showdown.core;

/**
 * 宿傩、魔虚罗走位的顺滑程度和逻辑耗时（不是单元测试，手动运行）：
 *   java -cp core/build/classes/java/main:core/build/classes/java/test com.shinjuku.showdown.core.AiSmoothness [局数]
 * 四个难度 × 标准脚本的五条悟，每局最多 90 秒。只看“自己能走”的时候（自由、在地上、没硬直、没被控），按 tick 统计：
 * - 起步 / 停步：速度从 0 变成在走、从在走变成 0；折返：上一段往一边走，停不到 0.1 秒（或不停）就往另一边走；
 * - 碎步：走了不到 0.1 秒就停；顿一下：停了不到 0.1 秒又接着走（同方向）；
 * - 急变：速度每 tick 的变化量之和（像素 / 秒，按每秒平均）；转身：朝向每分钟翻几次。
 * 都换算成每分钟（按有效 tick）；逻辑耗时是 World.step 的平均和 99.9 分位（JIT 预热以后）。
 */
public final class AiSmoothness {
    static final int LIMIT = 90 * Tuning.HZ;
    static final int SHORT = 12; // 0.1 秒

    static final class Track {
        int dir, runLen, stopLen = 9999, lastDir, facing;
        long jolts, ticks, moving, starts, stops, reversals, shortRuns, shortStops, facingFlips;
        double jerk;
        float lastVx;
        boolean inCtx;

        void sample(Fighter f, boolean ctx) {
            if (!ctx) {
                inCtx = false;
                return;
            }
            float vx = f.vx;
            int d = vx > 30 ? 1 : vx < -30 ? -1 : 0;
            if (!inCtx) { // 重新进入可以走动的状态：从这一刻开始算，不把被打飞的速度算成急变
                inCtx = true;
                dir = d;
                runLen = 0;
                stopLen = 9999;
                lastVx = vx;
                facing = f.facing;
                lastDir = 0;
                return;
            }
            ticks++;
            if (Math.abs(vx - lastVx) > 120) jolts++; // 一个 tick 里速度变了 120 以上（1.4 万 px/s² 以上）：肉眼看是“瞬间”起停
            jerk += Math.abs(vx - lastVx);
            lastVx = vx;
            if (f.facing != facing) {
                facingFlips++;
                facing = f.facing;
            }
            if (d != 0) moving++;
            if (d != dir) {
                if (dir != 0 && d == 0) { // 停步
                    stops++;
                    if (runLen < SHORT) shortRuns++;
                    lastDir = dir;
                    stopLen = 0;
                } else if (dir == 0 && d != 0) { // 起步
                    starts++;
                    if (lastDir != 0 && lastDir != d && stopLen < SHORT) reversals++;
                    else if (lastDir == d && stopLen < SHORT) shortStops++;
                    runLen = 0;
                } else { // 直接反向
                    reversals++;
                    if (runLen < SHORT) shortRuns++;
                    runLen = 0;
                }
                dir = d;
            }
            if (d != 0) runLen++;
            else stopLen++;
        }

        void add(Track o) {
            ticks += o.ticks;
            moving += o.moving;
            starts += o.starts;
            stops += o.stops;
            reversals += o.reversals;
            shortRuns += o.shortRuns;
            shortStops += o.shortStops;
            facingFlips += o.facingFlips;
            jerk += o.jerk;
            jolts += o.jolts;
        }


        String report() {
            double min = ticks / (60.0 * Tuning.HZ);
            if (min == 0) return "（没有样本）";
            return String.format("可走时长 %5.1f 分  在走 %3.0f%%  起步 %5.1f  停步 %5.1f  折返 %5.1f  碎步 %5.1f  顿一下 %5.1f  转身 %5.1f  速度突变 %5.1f（每分钟）",
                    min, 100.0 * moving / ticks, starts / min, stops / min, reversals / min, shortRuns / min, shortStops / min, facingFlips / min, jolts / min);
        }
    }

    static boolean canWalk(World w, Fighter f) {
        return f.present && f.state == Fighter.FREE && f.onGround && f.hitstun == 0 && !f.scripted && !f.captured && !f.knockedAir
                && w.cine == World.CINE_NONE && !w.voidControl() && w.tick - f.lastHitTick > 8 && !w.inBlueField(f)
                && (f != w.sukuna || w.sukunaCanAct());
    }

    public static void main(String[] a) {
        int n = a.length > 0 ? Integer.parseInt(a[0]) : 200;
        // JIT 预热
        for (int i = 0; i < 20; i++) run(1, 1000 + i, null, null, null);
        for (int level = 0; level < 4; level++) {
            Track s = new Track(), m = new Track();
            long[] ns = new long[LIMIT * n + 1];
            int[] cnt = new int[1];
            for (int i = 0; i < n; i++) run(level, i, s, m, new Timing(ns, cnt));
            long[] t = java.util.Arrays.copyOf(ns, cnt[0]);
            java.util.Arrays.sort(t);
            double avg = 0;
            for (long v : t) avg += v;
            avg /= Math.max(1, t.length);
            System.out.println("== " + BalanceSim.DIFFICULTY[level]);
            System.out.println("  宿傩   " + s.report());
            System.out.println("  魔虚罗 " + m.report());
            System.out.printf("  World.step 平均 %.1f µs  99.9%% %.1f µs  最大 %.1f µs%n", avg / 1000, t[(int) (t.length * 0.999)] / 1000.0, t[t.length - 1] / 1000.0);
        }
    }

    static final class Timing {
        final long[] ns;
        final int[] cnt;

        Timing(long[] ns, int[] cnt) {
            this.ns = ns;
            this.cnt = cnt;
        }
    }

    static void run(int level, long seed, Track s, Track m, Timing timing) {
        BattleConfig cfg = new BattleConfig();
        cfg.sukunaDifficulty(level);
        cfg.seed = seed;
        World w = new World(cfg);
        GojoBot bot = new GojoBot(GojoBot.STANDARD, seed);
        TickInput in = new TickInput();
        int lastFight = -1;
        while (w.fightTicks < LIMIT && w.outcome == World.OUTCOME_NONE) {
            bot.fill(w, in);
            long t0 = System.nanoTime();
            w.step(in);
            long dt = System.nanoTime() - t0;
            if (timing != null && timing.cnt[0] < timing.ns.length) timing.ns[timing.cnt[0]++] = dt;
            if (w.cutscene) w.endCutscene(w.castId);
            if (w.fightTicks == lastFight) continue; // 命中停顿、过场：逻辑没推进
            lastFight = w.fightTicks;
            if (s != null) s.sample(w.sukuna, canWalk(w, w.sukuna) && w.sStagger == 0);
            if (m != null) m.sample(w.mahoraga, canWalk(w, w.mahoraga));
        }
    }
}
