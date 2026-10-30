package com.shinjuku.showdown.core;

/** 平衡实验（手动运行）：在某个难度的参数上改一项，看标准脚本的胜率怎么变。 */
public final class AiExperiment {
    static void run(String name, int level, java.util.function.Consumer<BattleConfig.AiTuning> f) {
        int n = 400, gojo = 0;
        long sukDealt = 0, gojoDealt = 0;
        for (int i = 0; i < n; i++) {
            BattleConfig c = new BattleConfig();
            c.sukunaAi = true;
            c.ai.preset(level);
            f.accept(c.ai);
            BalanceSim.Result r = BalanceSim.play(GojoBot.STANDARD, i, c);
            if (r.winner == 0) gojo++;
            sukDealt += r.ampMelee + r.world;
            gojoDealt += r.gojoDealt;
        }
        System.out.printf("%-28s 五条悟胜 %3.0f%%  宿傩每局打出 %4.0f  五条每局打出 %5.0f%n", name, 100.0 * gojo / n, sukDealt / 1000.0 / n, gojoDealt / 1000.0 / n);
    }

    public static void main(String[] a) {
        String[] names = BalanceSim.DIFFICULTY;
        for (int level = 0; level < 4; level++) run(names[level], level, t -> { });
        run("里梅 不试探", 1, t -> t.probeChance = 0);
        run("里梅 不打断", 1, t -> t.interruptChance = 0);
        run("里梅 不拉开距离", 1, t -> t.kiteChance = 0);
        run("里梅 不跳越", 1, t -> t.vaultChance = 0);
        run("万 不骗招", 2, t -> t.ampBaitChance = 0);
        run("万 不追闪避", 2, t -> t.dodgePunishChance = 0);
        run("史上最强 不读习惯", 3, t -> t.learnMinSamples = 0);
        run("史上最强 迟疑 6–12", 3, t -> { t.recoverMin = 6; t.recoverMax = 12; });
    }
}
