package com.shinjuku.showdown.core;

import java.util.Arrays;

/**
 * 自动对战模拟：三档五条悟脚本（GojoBot）× 一批种子，对四个难度的宿傩（BattleConfig.sukunaDifficulty：AI、倍率、能用的术式都和游戏一样），
 * 每局最多 90 秒有效时间，宿傩第一次倒下就算五条悟赢。统计胜率、击杀用时、宿傩方的伤害构成、世界斩、反转术式和原身的回复、
 * 魔虚罗（召唤、存活、被一发打掉）和顎吐。不是单元测试，手动运行：
 *   java -cp core/build/classes/java/main:core/build/classes/java/test com.shinjuku.showdown.core.BalanceSim [局数] [血量] [起始种子]
 */
public final class BalanceSim {
    static final int LIMIT = 90 * Tuning.HZ;

    static final class Result {
        int winner = -1; // 0 五条悟赢，1 宿傩赢，-1 超时
        int ticks;
        long ampMelee, world, mahoDealt, heal, transformHeal, gojoDealt, mahoTaken;
        int unlockTick = -1, worldStarted, worldFired, worldHit, domainUsed, rctUsed, tp, tpBroken, chants, chantBroken, amps;
        int agitoCast, agitoTouch, summonTry, summonBroken, summonTick = -1, mahoGoneTick = -1, mahoAdaptTick = -1, transformTick = -1;
        int tpPurple, maxPurple, maxBroken;
        long fearSum;                       // 按有效 tick 累加的恐惧（万分制）
        int fearTicks, fearHigh, fearPeak;  // 有效 tick 数、恐惧 60 以上的 tick 数、最高值
        int spaceStart, spaceKill, spaceBroken, spaceReady, spaceThinks; // 空间斩：起手、斩死、起手被打断；三个硬条件同时满足过（0/1）；认真考虑过几次
        boolean mahoKilled, mahoOneShot;
        float transformHp;
    }

    private static boolean lastHitBig;

    static Result play(int level, long seed, BattleConfig cfg) {
        cfg.seed = seed;
        World w = new World(cfg);
        GojoBot bot = new GojoBot(level, seed);
        TickInput in = new TickInput();
        Result r = new Result();
        long seen = 0, lastSukunaHp = w.sukuna.hpMilli;
        // 时钟统一用 fightTicks：逻辑真正推进了一 tick 才加一（命中停顿、过场都不算）
        int lastFight = -1;
        while (w.fightTicks < LIMIT) {
            bot.fill(w, in);
            w.step(in);
            if (w.fightTicks != lastFight) {
                lastFight = w.fightTicks;
                int fear = w.sukunaFear();
                r.fearSum += fear;
                r.fearTicks++;
                if (fear >= Tuning.FEAR_PANIC) r.fearHigh++;
                r.fearPeak = Math.max(r.fearPeak, fear);
                if (w.fightTicks >= Tuning.SPACE_MIN_TICKS && w.sukuna.hpMilli > 0 && w.sukuna.hpMilli * 4 <= w.sukuna.maxHpMilli
                        && fear < Tuning.SPACE_FEAR_GATE) r.spaceReady = 1;
            }
            if (w.cutscene) {
                w.endCutscene(w.castId); // 模拟里过场直接放完
                r.domainUsed++;
            }
            Events e = w.events;
            for (; seen < e.written; seen++) {
                int i = (int) (seen & Events.MASK);
                if (e.type[i] == Events.DAMAGE) {
                    long dmg = Math.round(e.value[i] * 1000);
                    if (e.kind[i] == Attack.S_MELEE) r.ampMelee += dmg;
                    else if (e.kind[i] == Attack.M_PUNCH || e.kind[i] == Attack.M_SWORD) r.mahoDealt += dmg;
                    else if (e.kind[i] == Attack.WORLD_SLASH) {
                        r.world += dmg;
                        r.worldHit++;
                    }
                } else if (e.type[i] == Events.AGITO && e.value[i] == 0) {
                    r.agitoCast++;
                } else if (e.type[i] == Events.INFINITY_BLOCK && e.kind[i] == Attack.AGITO) {
                    r.agitoTouch++;
                } else if (e.type[i] == Events.SUMMON) {
                    if (e.value[i] == 0) r.summonTry++;
                    else if (e.value[i] < 0) r.summonBroken++;
                    else r.summonTick = w.fightTicks;
                } else if (e.type[i] == Events.MAHORAGA_GONE) {
                    r.mahoGoneTick = w.fightTicks;
                    r.mahoKilled = e.value[i] == 0;
                    r.mahoOneShot = r.mahoKilled && w.damageTaken[Fighter.MAHORAGA] == Tuning.MAHORAGA_HP_MILLI && lastHitBig;
                } else if (e.type[i] == Events.ADAPT && e.kind[i] == 2) {
                    r.mahoAdaptTick = w.fightTicks;
                } else if (e.type[i] == Events.TRANSFORM && e.value[i] > 0) {
                    r.transformTick = w.fightTicks;
                    r.transformHp = lastSukunaHp / (float) w.sukuna.maxHpMilli;
                } else if (e.type[i] == Events.SLASH && e.kind[i] == Attack.WORLD_SLASH) {
                    r.worldFired++;
                } else if (e.type[i] == Events.CAST && e.kind[i] == Attack.WORLD_SLASH) {
                    r.worldStarted++;
                } else if (e.type[i] == Events.RCT && e.value[i] > 0) {
                    r.rctUsed++;
                } else if (e.type[i] == Events.TELEPORT) {
                    r.tp++;
                    if (e.kind[i] == Attack.PURPLE) r.tpPurple++;
                } else if (e.type[i] == Events.CAST && e.value[i] == 5) {
                    r.maxPurple++;
                } else if (e.type[i] == Events.CHANT && e.kind[i] == Attack.PURPLE_MAX && e.value[i] < 0) {
                    r.maxBroken++;
                } else if (e.type[i] == Events.CHANT && e.kind[i] == Attack.BLUE_FIST && e.value[i] < 0) {
                    r.tpBroken++; // 瞬移或下砸苍拳的前摇被打断
                } else if (e.type[i] == Events.CHANT && e.kind[i] != Attack.BLUE_FIST) {
                    if (e.value[i] == 0) r.chants++;
                    else if (e.value[i] < 0) r.chantBroken++;
                } else if (e.type[i] == Events.AMP && e.value[i] > 0) {
                    r.amps++;
                } else if (e.type[i] == Events.SPACE) {
                    if (e.value[i] == 0) r.spaceStart++;
                    else if (e.value[i] == 3) r.spaceKill++;
                    else if (e.value[i] < 0) r.spaceBroken++;
                }
            }
            lastSukunaHp = w.sukuna.hpMilli;
            lastHitBig = w.mahoraga.present && w.mahoraga.hpMilli == Tuning.MAHORAGA_HP_MILLI; // 下一 tick 被打倒的话就是一发打掉
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
        r.ticks = w.fightTicks;
        r.spaceThinks = w.ai().spaceThinks;
        if (r.summonTick >= 0 && r.mahoGoneTick < 0) r.mahoGoneTick = w.fightTicks; // 打完还在场
        r.gojoDealt = w.damageTaken[Fighter.SUKUNA];
        r.mahoTaken = w.damageTaken[Fighter.MAHORAGA];
        r.heal = w.rctHealed;
        r.transformHeal = w.transformHealed;
        return r;
    }

    static final String[] DIFFICULTY = {"三轮", "里梅", "万", "史上最强"};

    public static void main(String[] args) {
        int n = args.length > 0 ? Integer.parseInt(args[0]) : 200;
        float hp = args.length > 1 ? Float.parseFloat(args[1]) : 0;
        int seed0 = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        for (int diff = 0; diff < 4; diff++) {
            final int dl = diff;
            System.out.println("== 难度：" + DIFFICULTY[diff]);
            for (int level = 0; level < 3; level++) report(level, n, seed0, c -> {
                c.sukunaDifficulty(dl);
                if (hp > 0) c.sukunaMaxHp = (float) (hp * Math.pow(1.1, dl)); // 试别的基础血量（照样按难度 ×1.1）
            });
        }
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

    static void report(int level, int n, int seed0, java.util.function.Consumer<BattleConfig> tweak) {
        int[] wins = new int[3];
        int[] gojoTtk = new int[n], sukunaTtk = new int[n], unlock = new int[n];
        int gk = 0, sk = 0, uk = 0;
        long ampSum = 0, worldSum = 0;
        int fired = 0, hit = 0, started = 0, rct = 0, tp = 0, tpBroken = 0, chants = 0, chantBroken = 0, amps = 0;
        long gojoDealt = 0, mahoDealt = 0, transformHeal = 0, rctHeal = 0, mahoTaken = 0;
        int tpPurple = 0, maxPurple = 0, maxBroken = 0;
        long fearSum = 0, fearTicks = 0, fearHigh = 0;
        int[] fearPeak = new int[n];
        int spaceStart = 0, spaceKill = 0, spaceBroken = 0, spaceReady = 0, spaceThinks = 0;
        int agitoCast = 0, agitoTouch = 0, summonTry = 0, summonBroken = 0, summoned = 0, mahoKilled = 0, oneShot = 0, adapted = 0, transformed = 0;
        int[] mahoLife = new int[n];
        float transformHpSum = 0;
        int ml = 0;
        for (int i = 0; i < n; i++) {
            BattleConfig cfg = new BattleConfig();
            cfg.sukunaAi = true;
            if (tweak != null) tweak.accept(cfg);
            Result r = play(level, seed0 + i, cfg);
            wins[r.winner + 1]++;
            if (r.winner == 0) gojoTtk[gk++] = r.ticks;
            if (r.winner == 1) sukunaTtk[sk++] = r.ticks;
            if (r.unlockTick >= 0) unlock[uk++] = r.unlockTick;
            ampSum += r.ampMelee;
            worldSum += r.world;
            fired += r.worldFired;
            started += r.worldStarted;
            rct += r.rctUsed;
            tp += r.tp;
            tpBroken += r.tpBroken;
            chants += r.chants;
            chantBroken += r.chantBroken;
            amps += r.amps;
            gojoDealt += r.gojoDealt;
            hit += r.worldHit;
            mahoDealt += r.mahoDealt;
            tpPurple += r.tpPurple;
            maxPurple += r.maxPurple;
            maxBroken += r.maxBroken;
            fearSum += r.fearSum;
            fearTicks += r.fearTicks;
            fearHigh += r.fearHigh;
            fearPeak[i] = r.fearPeak;
            spaceStart += r.spaceStart;
            spaceKill += r.spaceKill;
            spaceBroken += r.spaceBroken;
            spaceReady += r.spaceReady;
            spaceThinks += r.spaceThinks;
            rctHeal += r.heal;
            transformHeal += r.transformHeal;
            mahoTaken += r.mahoTaken;
            agitoCast += r.agitoCast;
            agitoTouch += r.agitoTouch;
            summonTry += r.summonTry;
            summonBroken += r.summonBroken;
            if (r.summonTick >= 0) {
                summoned++;
                mahoLife[ml++] = r.mahoGoneTick - r.summonTick;
            }
            if (r.mahoKilled) mahoKilled++;
            if (r.mahoOneShot) oneShot++;
            if (r.mahoAdaptTick >= 0) adapted++;
            if (r.transformTick >= 0) {
                transformed++;
                transformHpSum += r.transformHp;
            }
        }
        long dealt = ampSum + worldSum + mahoDealt;
        System.out.printf("[%s脚本] %d 局：五条悟胜 %.0f%%，宿傩胜 %.0f%%，超时 %.0f%%%n", GojoBot.NAMES[level], n,
                100.0 * wins[1] / n, 100.0 * wins[2] / n, 100.0 * wins[0] / n);
        System.out.printf("  击杀用时中位数：五条悟赢 %s，宿傩赢 %s；世界斩解锁中位数 %s（%d 局解锁）%n",
                sec(gojoTtk, gk), sec(sukunaTtk, sk), sec(unlock, uk), uk);
        System.out.printf("  宿傩方伤害构成：展延体术 %.0f%%、世界斩 %.0f%%、魔虚罗 %.0f%%（平均每局 %.0f）；世界斩起手 %d 次、斩出 %d 次、命中 %d 次；反转术式 %d 次（平均每局回 %.0f）；五条悟平均每局打宿傩 %.0f、打魔虚罗 %.0f%n",
                dealt == 0 ? 0 : 100.0 * ampSum / dealt, dealt == 0 ? 0 : 100.0 * worldSum / dealt, dealt == 0 ? 0 : 100.0 * mahoDealt / dealt,
                dealt / 1000.0 / n, started, fired, hit, rct, rctHeal / 1000.0 / n, gojoDealt / 1000.0 / n, mahoTaken / 1000.0 / n);
        if (summonTry + agitoCast + transformed > 0) {
            System.out.printf("  十种影：顎吐每局 %.2f 次（碰到五条悟 %.2f）；召唤魔虚罗 %d 局（尝试 %d、被打断 %d），存活中位数 %s，被打倒 %d（一发打掉 %d），适应无限 %d；变原身 %d 局（平均血线 %.0f%%，平均回 %.0f）%n",
                    agitoCast / (double) n, agitoTouch / (double) n, summoned, summonTry, summonBroken, sec(mahoLife, ml), mahoKilled, oneShot, adapted,
                    transformed, transformed == 0 ? 0 : 100.0 * transformHpSum / transformed, transformed == 0 ? 0 : transformHeal / 1000.0 / transformed);
        }
        System.out.printf("  每局：瞬移成功 %.2f（其中瞬移·茈 %.2f）、前摇被打断 %.2f；念咒 %.2f、被打断 %.2f；茈·最大输出 %.2f（被打断 %.2f）；展延 %.2f 次%n",
                tp / (double) n, tpPurple / (double) n, tpBroken / (double) n, chants / (double) n, chantBroken / (double) n,
                maxPurple / (double) n, maxBroken / (double) n, amps / (double) n);
        Arrays.sort(fearPeak);
        System.out.printf("  恐惧：平均 %.1f，60 以上占 %.1f%% 的时间，每局最高值的中位数 %.0f；空间斩：%d 局满足过硬条件，考虑 %d 次，起手 %d 次（被打断 %d）、斩死 %d 次%n",
                fearTicks == 0 ? 0 : fearSum / 100.0 / fearTicks, fearTicks == 0 ? 0 : 100.0 * fearHigh / fearTicks, fearPeak[n / 2] / 100.0,
                spaceReady, spaceThinks, spaceStart, spaceBroken, spaceKill);
    }

    private static String sec(int[] a, int n) {
        if (n == 0) return "-";
        int[] c = Arrays.copyOf(a, n);
        Arrays.sort(c);
        return String.format("%.1f 秒", c[n / 2] / (float) Tuning.HZ);
    }
}
