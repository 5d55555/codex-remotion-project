package com.shinjuku.showdown.core;

/**
 * 黑闪的伪随机（PRD）：第 n 次有效判定的触发概率是 min(1, C·n)，触发后 n 重置为 1。
 * C = 0.0322209… 时长期触发率正好 15%；最多连续 31 次不出，第 32 次必出。
 * 只在体术真正命中时判定（挥空、重复命中都不算）。
 */
public final class BlackFlash {
    private int attempt = 1;

    public boolean roll(CombatRng rng) {
        double p = Math.min(1.0, Tuning.BLACK_FLASH_C * attempt);
        boolean hit = rng.nextUnitDouble() < p;
        attempt = hit ? 1 : attempt + 1;
        return hit;
    }

    public int attempt() {
        return attempt;
    }
}
