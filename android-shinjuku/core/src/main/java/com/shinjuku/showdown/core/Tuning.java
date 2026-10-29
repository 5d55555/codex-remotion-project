package com.shinjuku.showdown.core;

/**
 * 战斗数值。逻辑每秒 120 tick，时间都用整数 tick。
 * 用户给定的写“定”；其余是第一轮试玩用的占位，可以调。
 * 世界坐标：x 向右，战场宽 1280；y 是离地高度，向上为正；人物身高约 100。
 */
public final class Tuning {
    private Tuning() {
    }

    public static final int HZ = 120;
    public static final float DT = 1f / HZ;

    // 战场与人物
    public static final float WIDTH = 1280f;
    public static final float CEILING = 360f;      // 苍的落点最高到这里（被吸上去的人头顶不出画面）
    public static final float GRAVITY = 1800f;     // 占位
    public static final float BODY_HALF_W = 22f;
    public static final float BODY_H = 100f;
    public static final float MOVE_SPEED = 300f;   // 占位
    public static final float JUMP_SPEED = 600f;   // 占位
    public static final float GROUND_FRICTION = 2400f; // 被打飞后在地上滑行减速
    public static final int INPUT_BUFFER = 14;     // 提前按下的体术/赫/跳跃保留约 0.12 秒，动作一结束就接上

    // 体术：50 伤害；15% 黑闪（伪随机），黑闪 350 伤害（定）
    public static final long MELEE_DAMAGE_MILLI = 50_000;
    public static final long BLACK_FLASH_DAMAGE_MILLI = 350_000;
    public static final double BLACK_FLASH_C = 0.03222091437308769; // 线性递增伪随机常数，长期频率 15%
    public static final int MELEE_STARTUP = 10, MELEE_ACTIVE = 6, MELEE_RECOVERY = 20; // 占位
    public static final float MELEE_REACH = 94f;   // 占位：前方多远打得到（中心到中心）
    public static final float MELEE_IMPULSE = 140f;
    public static final int MELEE_HITSTUN = 18;
    public static final float BLACK_FLASH_IMPULSE_X = 380f, BLACK_FLASH_IMPULSE_Y = 220f;
    public static final int BLACK_FLASH_HITSTUN = 36;
    public static final int BLACK_FLASH_HITSTOP = 8; // 命中停顿（逻辑计数，不 sleep）

    // 苍：吸力；投掷最远不超过战场宽度 30%；离中心越近伤害越高，最高每秒 100（定）
    // 长按：波及范围和吸力翻倍，但有前摇（定）
    public static final float BLUE_MAX_THROW = 0.30f * WIDTH;
    public static final long BLUE_MAX_DPS_MILLI = 100_000;
    public static final float BLUE_RANGE_MULT_CHARGED = 2f, BLUE_PULL_MULT_CHARGED = 2f;
    public static final float BLUE_RADIUS = 0.18f * WIDTH; // 占位：普通苍的作用半径
    // 吸引：目标速度指向中心，大小 min(最高速度, 收敛系数 × 距离)；速度以跟随系数追目标速度，加速度不超过上限。
    // 远处全速冲向中心，近处按距离减速，等价于临界附近阻尼的弹簧（k = 跟随 × 收敛 = 80，b = 16），不会冲过头来回抖。
    public static final float BLUE_ACCEL = 2400f;  // 占位：最大吸引加速度（边缘 25%，越近越大）
    public static final float BLUE_ARRIVE = 5f;    // 占位：收敛系数（/s）
    public static final float BLUE_FOLLOW = 16f;   // 占位：速度跟随系数（/s）
    public static final float BLUE_MAX_SPEED = 480f;
    public static final float BLUE_CORE = 10f;     // 核心捕获半径
    public static final int BLUE_FLY = 18;         // 飞到落点的时间
    public static final int BLUE_LIFE = 300;       // 占位：吸引持续 2.5 秒
    public static final int BLUE_HOLD_THRESHOLD = 42; // 占位：按住 0.35 秒算长按
    public static final int BLUE_WINDUP = 12;      // 占位：普通苍前摇
    public static final int BLUE_WINDUP_CHARGED = 36; // 占位：蓄满前摇 0.3 秒
    public static final int BLUE_AIM_LOCK = 12;    // 前摇最后这几 tick 锁定方向
    public static final float BLUE_WINDUP_MOVE = 0.3f; // 蓄力前摇时的移动速度比例
    public static final int BLUE_COOLDOWN = 720;   // 占位：6 秒
    public static final int BLUE_COOLDOWN_CHARGED = 1080; // 占位：9 秒
    public static final int BLUE_FAIL_LOCK = 60;   // 被打断后 0.5 秒内不能再按

    // 赫：斥力击飞，直接 150 伤害（定）
    public static final long RED_DAMAGE_MILLI = 150_000;
    public static final int RED_WINDUP = 14, RED_ACTIVE = 4, RED_RECOVERY = 24; // 占位
    public static final float RED_REACH = 0.16f * WIDTH; // 占位
    public static final float RED_IMPULSE_X = 650f, RED_IMPULSE_Y = 420f;
    public static final float MAX_VX = 800f, MAX_VY = 600f;
    public static final int RED_HITSTUN = 42;
    public static final int RED_PULL_RESIST = 24; // 被赫打中后这段时间不会被苍吸回去
    public static final int RED_COOLDOWN = 480;   // 占位：4 秒

    // 活靶子：被打倒后过一会儿满血站起来
    public static final int KO_TICKS = 360;       // 占位：3 秒
}
