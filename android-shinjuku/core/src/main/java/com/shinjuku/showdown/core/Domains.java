package com.shinjuku.showdown.core;

/**
 * 领域相关的世界状态（这一版还没有领域，全都是 false）。
 * 以后宿傩的领域展开、领域展延、特殊手段在这里登记，Defense 据此判断能不能绕过“无限”。
 */
public final class Domains {
    /** sureHit[a][b]：a 的领域已生效且必中对 b 有效（没有被抵消）。 */
    public final boolean[][] sureHit = new boolean[Fighter.COUNT][Fighter.COUNT];
    /** amplification[a]：a 当前处于领域展延。 */
    public final boolean[] amplification = new boolean[Fighter.COUNT];
    /** specialBypass[a]：a 当前拥有被规则认可的特殊绕过手段。 */
    public final boolean[] specialBypass = new boolean[Fighter.COUNT];

    void clear() {
        for (boolean[] row : sureHit) java.util.Arrays.fill(row, false);
        java.util.Arrays.fill(amplification, false);
        java.util.Arrays.fill(specialBypass, false);
    }
}
