package com.shinjuku.showdown.core;

/**
 * 战斗用随机数：固定算法（xorshift64*），同一个种子永远得到同一串数。
 * 只给命中判定用；粒子之类的装饰另用自己的随机数，免得影响回放。
 */
public final class CombatRng {
    private long s;

    public CombatRng(long seed) {
        s = seed == 0 ? 0x9E3779B97F4A7C15L : seed;
    }

    public double nextUnitDouble() {
        s ^= s >>> 12;
        s ^= s << 25;
        s ^= s >>> 27;
        long r = s * 0x2545F4914F6CDD1DL;
        return (r >>> 11) * 0x1.0p-53;
    }

    public long state() {
        return s;
    }
}
