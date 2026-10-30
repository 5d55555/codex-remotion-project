package com.shinjuku.showdown.core;

import java.util.Arrays;

/**
 * 自动对战模拟：三档五条悟脚本（GojoBot）× 一批种子，对三轮难度的宿傩 AI，每局最多 90 秒有效时间，先倒下的输。
 * 统计胜率、击杀用时、宿傩的伤害构成、世界斩解锁时间和命中率、反转术式回复量。不是单元测试，手动运行：
 *   java -cp core/build/classes/java/main:core/build/classes/java/test com.shinjuku.showdown.core.BalanceSim [局数]
 */
public final class BalanceSim {
    static final int LIMIT = 90 * Tuning.HZ;

    static final class Result {
        int winner = -1; // 0 五条悟赢，1 宿傩赢，-1 超时
        int ticks;
        long ampMelee, world, heal, gojoDealt;
        int unlockTick = -1, worldStarted, worldFired, worldHit, domainUsed, rctUsed;
    }

    static Result play(int level, long seed, BattleConfig cfg) {
        cfg.seed = seed;
        World w = new World(cfg);
        GojoBot bot = new GojoBot(level, seed);
        TickInput in = new TickInput();
        Result r = new Result();
        long seen = 0, lastSukunaHp = w.sukuna.hpMilli;
        int effective = 0;
        while (effective < LIMIT) {
            bot.fill(w, in);
            w.step(in);
            if (w.cutscene) {
                w.endCutscene(w.castId); // 模拟里过场直接放完
                r.domainUsed++;
            }
            if (w.hitstop == 0) effective++;
            Events e = w.events;
            for (; seen < e.written; seen++) {
                int i = (int) (seen & Events.MASK);
                if (e.type[i] == Events.DAMAGE) {
                    long dmg = Math.round(e.value[i] * 1000);
                    if (e.kind[i] == Attack.S_MELEE) r.ampMelee += dmg;
                    else if (e.kind[i] == Attack.WORLD_SLASH) {
                        r.world += dmg;
                        r.worldHit++;
                    }
                } else if (e.type[i] == Events.SLASH && e.kind[i] == Attack.WORLD_SLASH) {
                    r.worldFired++;
                } else if (e.type[i] == Events.CAST && e.kind[i] == Attack.WORLD_SLASH) {
                    r.worldStarted++;
                } else if (e.type[i] == Events.RCT && e.value[i] > 0) {
                    r.rctUsed++;
                }
            }
            if (w.sukuna.hpMilli > lastSukunaHp && w.sukuna.state != Fighter.KO) r.heal += w.sukuna.hpMilli - lastSukunaHp;
            lastSukunaHp = w.sukuna.hpMilli;
            if (r.unlockTick < 0 && w.worldUnlocked) r.unlockTick = w.fightTicks;
            if (w.sukuna.state == Fighter.KO) {
                r.winner = 0;
                break;
            }
            if (w.gojo.state == Fighter.KO) {
                r.winner = 1;
                break;
            }
        }
        r.ticks = effective;
        r.gojoDealt = w.sukuna.maxHpMilli - w.sukuna.hpMilli;
        return r;
    }

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        float hp = args.length > 1 ? Float.parseFloat(args[1]) : 0;
        for (int level = 0; level < 3; level++) report(level, n, hp > 0 ? c -> c.sukunaMaxHp = hp : null);
        for (int mode = 0; mode < 3; mode++) {
            long[] dmg = new long[100];
            for (int i = 0; i < dmg.length; i++) dmg[i] = domainBurst(mode, i);
            Arrays.sort(dmg);
            System.out.printf("[领域爆发 %s] 960 tick 伤害 P50 %.0f，P95 %.0f，最高 %.0f%n", new String[]{"只打体术", "200% 茈 + 体术", "200% 茈 + 最大输出赫 + 体术"}[mode],
                    dmg[50] / 1000.0, dmg[95] / 1000.0, dmg[99] / 1000.0);
        }
    }

    /** 领域爆发：贴着宿傩开无量空处，960 tick 里能打多少。mode 0 只打体术；1 先念 200% 茈再打体术；2 先念 200% 茈、再赫·最大输出、再体术。 */
    static long domainBurst(int mode, long seed) {
        BattleConfig cfg = new BattleConfig();
        cfg.seed = seed;
        cfg.sukunaAi = true;
        World w = new World(cfg);
        w.gojo.x = 500;
        w.sukuna.x = 580;
        TickInput in = new TickInput();
        in.domain = true;
        w.step(in);
        in.clearEdges();
        w.endCutscene(w.castId);
        long hp0 = w.sukuna.hpMilli;
        int phase = mode == 0 ? 2 : 0;
        while (w.domainTicks > 0) {
            Fighter g = w.gojo, k = w.sukuna;
            in.moveX = 0;
            if (phase == 0) {
                in.purple = g.state == Fighter.FREE;
                in.purpleHeld = true;
                if (w.purpleCd > 0) phase = mode == 2 ? 1 : 2;
            } else if (phase == 1) {
                in.purpleHeld = false;
                in.red = g.state == Fighter.FREE && w.redCd == 0;
                in.redHeld = true;
                if (w.redCd > 0 && g.state == Fighter.FREE) phase = 2;
            } else {
                in.purpleHeld = in.redHeld = false;
                float d = Math.abs(k.x - g.x);
                if (d > Tuning.MELEE_REACH - 10) in.moveX = k.x > g.x ? 1 : -1;
                else in.melee = w.meleeCd == 0;
            }
            w.step(in);
            in.clearEdges();
        }
        return hp0 - w.sukuna.hpMilli;
    }

    static void report(int level, int n, java.util.function.Consumer<BattleConfig> tweak) {
        int[] wins = new int[3];
        int[] gojoTtk = new int[n], sukunaTtk = new int[n], unlock = new int[n];
        long[] heal = new long[n];
        int gk = 0, sk = 0, uk = 0;
        long ampSum = 0, worldSum = 0;
        int fired = 0, hit = 0, started = 0, rct = 0;
        long gojoDealt = 0;
        for (int i = 0; i < n; i++) {
            BattleConfig cfg = new BattleConfig();
            cfg.sukunaAi = true;
            if (tweak != null) tweak.accept(cfg);
            Result r = play(level, i, cfg);
            wins[r.winner + 1]++;
            if (r.winner == 0) gojoTtk[gk++] = r.ticks;
            if (r.winner == 1) sukunaTtk[sk++] = r.ticks;
            if (r.unlockTick >= 0) unlock[uk++] = r.unlockTick;
            heal[i] = r.heal;
            ampSum += r.ampMelee;
            worldSum += r.world;
            fired += r.worldFired;
            started += r.worldStarted;
            rct += r.rctUsed;
            gojoDealt += r.gojoDealt;
            hit += r.worldHit;
        }
        long dealt = ampSum + worldSum;
        System.out.printf("[%s脚本] %d 局：五条悟胜 %.0f%%，宿傩胜 %.0f%%，超时 %.0f%%%n", GojoBot.NAMES[level], n,
                100.0 * wins[1] / n, 100.0 * wins[2] / n, 100.0 * wins[0] / n);
        System.out.printf("  击杀用时中位数：五条悟赢 %s，宿傩赢 %s；世界斩解锁中位数 %s（%d 局解锁）%n",
                sec(gojoTtk, gk), sec(sukunaTtk, sk), sec(unlock, uk), uk);
        System.out.printf("  宿傩伤害构成：展延体术 %.0f%%、世界斩 %.0f%%（平均每局 %.0f）；世界斩起手 %d 次、斩出 %d 次、命中 %d 次；反转术式 %d 次，回复中位数 %.0f；五条悟平均每局打出 %.0f%n",
                dealt == 0 ? 0 : 100.0 * ampSum / dealt, dealt == 0 ? 0 : 100.0 * worldSum / dealt, dealt / 1000.0 / n,
                started, fired, hit, rct, median(heal) / 1000.0, gojoDealt / 1000.0 / n);
    }

    private static String sec(int[] a, int n) {
        if (n == 0) return "-";
        int[] c = Arrays.copyOf(a, n);
        Arrays.sort(c);
        return String.format("%.1f 秒", c[n / 2] / (float) Tuning.HZ);
    }

    private static double median(long[] a) {
        long[] c = a.clone();
        Arrays.sort(c);
        return c[c.length / 2];
    }
}
