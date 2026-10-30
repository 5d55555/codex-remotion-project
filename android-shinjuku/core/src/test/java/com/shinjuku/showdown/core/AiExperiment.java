package com.shinjuku.showdown.core;

/** 平衡实验（手动运行）：在某个难度的配置上改一项，看标准、熟练脚本的胜率怎么变。 */
public final class AiExperiment {
    static void run(String name, int level, java.util.function.Consumer<BattleConfig> f) {
        int n = 400;
        int[] gojo = new int[2];
        long sukDealt = 0, gojoDealt = 0;
        for (int b = 0; b < 2; b++) {
            for (int i = 0; i < n; i++) {
                BattleConfig c = new BattleConfig();
                c.sukunaDifficulty(level);
                f.accept(c);
                BalanceSim.Result r = BalanceSim.play(b == 0 ? GojoBot.STANDARD : GojoBot.SKILLED, i, c);
                if (r.winner == 0) gojo[b]++;
                if (b == 0) {
                    sukDealt += r.ampMelee + r.world + r.mahoDealt;
                    gojoDealt += r.gojoDealt;
                }
            }
        }
        System.out.printf("%-30s 五条悟胜 标准 %3.0f%% 熟练 %3.0f%%  宿傩方每局打出 %4.0f  五条每局打宿傩 %5.0f%n", name,
                100.0 * gojo[0] / n, 100.0 * gojo[1] / n, sukDealt / 1000.0 / n, gojoDealt / 1000.0 / n);
    }

    public static void main(String[] a) {
        String[] names = BalanceSim.DIFFICULTY;
        for (int level = 0; level < 4; level++) run(names[level], level, c -> { });
        run("里梅 不变原身", 1, c -> c.trueForm = false);
        run("里梅 不变原身、×1", 1, c -> {
            c.trueForm = false;
            c.tier(0);
        });
        run("里梅 没有魔虚罗", 1, c -> c.mahoraga = false);
        run("里梅 原身血线 15%", 1, c -> c.ai.transformHp = 0.15f);
        run("里梅 原身血线 10%", 1, c -> c.ai.transformHp = 0.10f);
        run("里梅 三轮的 AI", 1, c -> {
            c.ai.preset(0);
        });
        run("史上最强 不变原身", 3, c -> c.trueForm = false);
    }
}
