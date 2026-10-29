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
    // 出招快、收招快：后摇过了 MELEE_CANCEL 就能推摇杆走开（移动取消）
    public static final int MELEE_STARTUP = 6, MELEE_ACTIVE = 4, MELEE_RECOVERY = 10, MELEE_CANCEL = 4; // 占位
    public static final int MELEE_COOLDOWN = MELEE_STARTUP + MELEE_ACTIVE + MELEE_CANCEL;
    public static final float MELEE_REACH = 94f;   // 占位：前方多远打得到（中心到中心）
    public static final float MELEE_LUNGE = 150f;  // 出拳那几 tick 往前带一步
    public static final float MELEE_IMPULSE = 170f;
    public static final int MELEE_HITSTUN = 20;
    public static final int MELEE_HITSTOP = 4;     // 打中的顿挫（整个世界停几 tick）
    public static final float BLACK_FLASH_IMPULSE_X = 420f, BLACK_FLASH_IMPULSE_Y = 240f;
    public static final int BLACK_FLASH_HITSTUN = 40;
    public static final int BLACK_FLASH_HITSTOP = 10; // 命中停顿（逻辑计数，不 sleep）

    // 苍拳：按住体术 FIST_HOLD tick 变成苍拳，苍附在手上再打；伤害是体术的 1.7 倍（黑闪也是）（定），后摇比体术大（定）
    public static final int FIST_HOLD = 18;        // 占位：按住 0.15 秒
    public static final int FIST_WINDUP = 14, FIST_ACTIVE = 5, FIST_RECOVERY = 30, FIST_CANCEL = 20; // 占位
    public static final long FIST_DAMAGE_MILLI = MELEE_DAMAGE_MILLI * 17 / 10;              // 85
    public static final long FIST_BLACK_FLASH_DAMAGE_MILLI = BLACK_FLASH_DAMAGE_MILLI * 17 / 10; // 595
    public static final float FIST_REACH = 104f, FIST_LUNGE = 260f;
    public static final float FIST_IMPULSE_X = 330f, FIST_IMPULSE_Y = 140f;
    public static final int FIST_HITSTUN = 30, FIST_HITSTOP = 7;

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
    public static final int BLUE_WINDUP = 8;       // 占位：普通苍前摇
    public static final int BLUE_WINDUP_CHARGED = 30; // 占位：蓄满前摇 0.25 秒
    public static final int BLUE_RECOVERY = 6;
    public static final int BLUE_AIM_LOCK = 12;    // 前摇最后这几 tick 锁定方向
    public static final float BLUE_WINDUP_MOVE = 0.3f; // 蓄力前摇时的移动速度比例
    public static final int BLUE_COOLDOWN = 720;   // 占位：6 秒
    public static final int BLUE_COOLDOWN_CHARGED = 1080; // 占位：9 秒
    public static final int BLUE_FAIL_LOCK = 60;   // 被打断后 0.5 秒内不能再按

    // 赫：斥力击飞，直接 150 伤害（定）。从手里飞出一颗红色球，RED_TRAVEL tick 内飞完射程，碰到人就爆
    public static final long RED_DAMAGE_MILLI = 150_000;
    public static final int RED_WINDUP = 10, RED_ACTIVE = 4, RED_RECOVERY = 12, RED_CANCEL = 6; // 占位
    public static final float RED_REACH = 0.32f * WIDTH; // 射程（原来 16%，翻倍）
    public static final int RED_TRAVEL = 20;             // 飞完射程用的 tick（速度不变）
    public static final float RED_SPEED = RED_REACH * HZ / RED_TRAVEL;
    public static final float RED_ORB_R = 30f;           // 占位：命中半径
    public static final float RED_RANGE = 0.08f * WIDTH; // 占位：赫的作用范围（和苍的作用范围“互相在内”就变成无指向茈）
    public static final float RED_IMPULSE_X = 650f, RED_IMPULSE_Y = 420f;
    public static final float MAX_VX = 1300f, MAX_VY = 700f;
    public static final int RED_HITSTUN = 42;
    public static final int RED_PULL_RESIST = 24; // 被赫打中后这段时间不会被苍吸回去
    public static final int RED_COOLDOWN = 480;   // 占位：4 秒

    // 赫·最大输出：长按赫，射程覆盖整个战场，550 伤害（定），排斥比普通赫强烈（定）
    public static final int RED_HOLD = 36;         // 占位：按住 0.3 秒
    public static final int RED_MAX_WINDUP = 24, RED_MAX_RECOVERY = 22; // 占位
    public static final long RED_MAX_DAMAGE_MILLI = 550_000;
    public static final float RED_MAX_ORB_R = 50f, RED_MAX_RANGE = 0.12f * WIDTH;
    public static final float RED_MAX_IMPULSE_X = 1250f, RED_MAX_IMPULSE_Y = 560f;
    public static final int RED_MAX_HITSTUN = 80, RED_MAX_HITSTOP = 6;
    public static final int RED_MAX_COOLDOWN = 720; // 占位：6 秒

    // 茈：向朝向发射，850 伤害（定）
    public static final long PURPLE_DAMAGE_MILLI = 850_000;
    public static final int PURPLE_WINDUP = 30, PURPLE_RECOVER = 16, PURPLE_CANCEL = 8; // 占位
    public static final float PURPLE_SPEED = 1500f;      // 占位
    public static final float PURPLE_R = 44f;            // 占位：命中半径
    public static final int PURPLE_COOLDOWN = 1800;      // 占位：15 秒
    public static final float PURPLE_IMPULSE_X = 800f, PURPLE_IMPULSE_Y = 360f;
    public static final int PURPLE_HITSTUN = 60;

    // 200% 茈：长按茈键念完四段咒词（九纲、偏光、乌与声明、表里之间）才放；期间不能移动，松手或被攻击都会打断（定）；200% 的效果（定）
    public static final int PURPLE_HOLD = 24;      // 占位：按住 0.2 秒开始念
    public static final int CHANT_PHRASE = 54;     // 占位：每段 0.45 秒
    public static final int CHANT_PHRASES = 4;
    public static final long PURPLE_200_DAMAGE_MILLI = PURPLE_DAMAGE_MILLI * 2;
    public static final float PURPLE_200_R = PURPLE_R * 2, PURPLE_200_SPEED = 1300f;
    public static final float PURPLE_200_IMPULSE_X = 1300f, PURPLE_200_IMPULSE_Y = 600f;
    public static final int PURPLE_200_HITSTUN = 110, PURPLE_200_HITSTOP = 14, PURPLE_200_RECOVER = 34;
    public static final int PURPLE_200_COOLDOWN = 3600; // 占位：30 秒
    public static final int CHANT_BROKEN_LOCK = 30;     // 被打断后 0.25 秒内不能再按

    // 领域展开「无量空处」：先放过场（全场暂停），展开期间宿傩无法主动行动（定）
    public static final int DOMAIN_DURATION = 960;  // 占位：8 秒
    public static final int DOMAIN_COOLDOWN = 4800; // 占位：40 秒（领域结束后开始算）

    // 宿傩：只有体术（75）和解（90，射程远、几乎没有前摇、冷却短）（定）
    public static final float SUKUNA_SPEED = 250f;   // 占位
    public static final long S_MELEE_DAMAGE_MILLI = 75_000;
    public static final int S_MELEE_STARTUP = 10, S_MELEE_ACTIVE = 6, S_MELEE_RECOVERY = 20; // 占位（Astra 的初值）
    public static final float S_MELEE_REACH = 94f, S_MELEE_IMPULSE = 160f;
    public static final int S_MELEE_HITSTUN = 18, S_MELEE_COOLDOWN = 36;
    public static final long DISMANTLE_DAMAGE_MILLI = 90_000;
    public static final int DISMANTLE_WINDUP = 2, DISMANTLE_RECOVERY = 18; // 几乎没有前摇
    public static final float DISMANTLE_RANGE = 560f;          // 占位：射程不俗（战场宽度 44%）
    public static final int DISMANTLE_COOLDOWN = 60;           // 占位：0.5 秒
    public static final int S_ATTACK_GAP = 48;                 // 宿傩两次出手至少隔 0.4 秒

    // 无指向茈：赫与苍重叠或互相在作用范围内时生成；伤害和茈一样 850（定），范围内的五条悟也挨，只有一半（定）
    public static final long HOLLOW_DAMAGE_MILLI = PURPLE_DAMAGE_MILLI;
    public static final long HOLLOW_SELF_DAMAGE_MILLI = HOLLOW_DAMAGE_MILLI / 2;
    // 占位：覆盖范围半径（和苍一样大）。苍放得近，五条悟自己会被卷进去；苍放远、让赫飞到射程末端再相遇，就能躲开
    public static final float HOLLOW_RADIUS = 0.18f * WIDTH;
    public static final int HOLLOW_GROW = 16, HOLLOW_LIFE = 54; // 占位：扩张用时、总时长
    public static final float HOLLOW_IMPULSE_X = 800f, HOLLOW_IMPULSE_Y = 420f;
    public static final int HOLLOW_HITSTUN = 60;
    public static final float HOLLOW_SELF_IMPULSE_X = 380f, HOLLOW_SELF_IMPULSE_Y = 260f;
    public static final int HOLLOW_SELF_HITSTUN = 24;

    // 活靶子：被打倒后过一会儿满血站起来
    public static final int KO_TICKS = 360;       // 占位：3 秒
}
