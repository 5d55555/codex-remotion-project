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
    // 连段衰减：轻拳（双方的体术）在同一串连击里打到第 3 下以后不再造成硬直；1 秒没挨打就重新算（防止贴身连打锁死对手）
    public static final int COMBO_STUN_HITS = 3, COMBO_RESET = 120;

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

    // 苍拳（长按体术，定）：按住体术 FIST_HOLD tick 变成苍拳，苍附在手上往前冲一拳；伤害是体术的 1.7 倍（黑闪也是）（定），后摇比体术大（定）
    public static final int FIST_HOLD = 18;        // 占位：按住 0.15 秒
    public static final int FIST_WINDUP = 14, FIST_ACTIVE = 5, FIST_RECOVERY = 30, FIST_CANCEL = 20; // 占位
    public static final long FIST_DAMAGE_MILLI = MELEE_DAMAGE_MILLI * 17 / 10;              // 85
    public static final long FIST_BLACK_FLASH_DAMAGE_MILLI = BLACK_FLASH_DAMAGE_MILLI * 17 / 10; // 595
    public static final float FIST_REACH = 104f, FIST_LUNGE = 260f;
    public static final float FIST_IMPULSE_X = 330f, FIST_IMPULSE_Y = 140f;
    public static final int FIST_HITSTUN = 30, FIST_HITSTOP = 7;

    // 下砸苍拳（按住跳跃时点体术，定）：从上往下砸，空中是往下俯冲一拳，地上是举起来往下劈；115（定），有前摇和后摇（定），
    // 前摇时被宿傩的攻击碰到（哪怕被无限挡住）就打断（“容易被打断”，定）。黑闪照样能出，黑闪伤害按体术的同样倍数（×7）
    public static final int SMASH_WINDUP = 14, SMASH_ACTIVE = 6, SMASH_DIVE_MAX = 30, SMASH_RECOVERY = 26; // 占位
    public static final long SMASH_DAMAGE_MILLI = 115_000;
    public static final long SMASH_BLACK_FLASH_DAMAGE_MILLI = SMASH_DAMAGE_MILLI * 7; // 805
    public static final float SMASH_REACH = 100f, SMASH_DIVE_VX = 220f, SMASH_DIVE_VY = -1000f;
    public static final float SMASH_IMPULSE_X = 260f, SMASH_IMPULSE_Y = 0f;
    public static final int SMASH_HITSTUN = 30;

    // 闪避（定）：默认往面朝的方向，推着摇杆就往摇杆的方向；闪避期间无敌；冷却 0.5 秒
    public static final int DODGE_TICKS = 18, DODGE_COOLDOWN = 60; // 占位：闪 0.15 秒
    public static final float DODGE_SPEED = 1000f;                 // 占位：闪 150 的距离

    // 瞬移（闪避 + 苍 / 赫，先按哪个都行，定）：按住闪避时点苍 / 点赫，或者按住苍 / 赫时点闪避；刚点过闪避（TP_LENIENCY 内）再点苍 / 赫也算。
    // 前摇可被打断（被宿傩的攻击碰到就断）；之后出现在宿傩背后，放固定动作，镜头拉近
    // 苍：用苍把宿傩吸到空中，苍拳连打 5 下（每下 115），最后一下击飞、击倒。赫：在背后放一发很大的赫，把宿傩轰出战场宽度 85%，击倒
    public static final int TP_WINDUP = 24, TP_COOLDOWN = 1200;    // 占位：前摇 0.2 秒；冷却 10 秒
    public static final int TP_LENIENCY = 30;                      // 刚点过闪避 0.25 秒内再点苍 / 赫，也算组合
    public static final float TP_BEHIND = 70f;
    public static final int TP_BLUE_LIFT = 30, TP_BLUE_FIRST_HIT = 36, TP_BLUE_HIT_GAP = 14, TP_BLUE_HITS = 5; // 占位
    public static final float TP_BLUE_HEIGHT = 120f, TP_BLUE_LAUNCH_X = 620f, TP_BLUE_LAUNCH_Y = 420f;
    public static final int TP_BLUE_HITSTOP = 3, TP_BLUE_AFTER = 20;
    public static final int TP_RED_CHARGE = 24, TP_RED_FLIGHT = 40, TP_RED_AFTER = 30; // 占位
    public static final long TP_RED_DAMAGE_MILLI = 550_000;        // 占位：和赫·最大输出一样
    public static final float TP_RED_DISTANCE = 0.85f * WIDTH, TP_RED_ARC = 70f;
    public static final int DOWN_TICKS = 60;                       // 被击倒：落地后这么久起不来
    // 瞬移·茈（按住闪避 + 茈，先按哪个都行，定）：先念茈的四段咒词（不用一直按着；被攻击碰到就断），然后闪到宿傩背后，
    // 拍拍他的肩膀，他回头时贴脸一发茈，击飞（定）；1900（定）。用掉瞬移冷却和 200% 茈的冷却（30 秒，占位）
    public static final long TP_PURPLE_DAMAGE_MILLI = 1_900_000;
    public static final int TP_PURPLE_TAP = 24, TP_PURPLE_TURN = 46, TP_PURPLE_FIRE = 70, TP_PURPLE_AFTER = 50; // 占位
    public static final float TP_PURPLE_IMPULSE_X = 1300f, TP_PURPLE_IMPULSE_Y = 560f;
    public static final int TP_PURPLE_HITSTUN = 110;


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
    public static final int BLUE_HOLD_THRESHOLD = 24; // 占位：按住 0.2 秒开始念咒（位相、黄昏、智慧之瞳），念完就是出力最大
    public static final int BLUE_WINDUP = 8;       // 占位：普通苍前摇
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
    public static final int RED_HOLD = 24;         // 占位：按住 0.2 秒开始念咒（位相、波罗蜜、光之柱），念完放最大输出
    public static final int RED_MAX_RECOVERY = 22; // 占位
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
    // 茈·最大输出（同时按住苍和赫，定）：先念苍、赫的咒词，再念茈的咒词（十段；松开任何一个或被攻击碰到就断，定），
    // 念完放一段 11 秒的固定动作（定：10 秒以上）：全场暂停只走这段动画，五条悟和宿傩都在空中——空中留一个巨大的苍，
    // 向天空发射赫朝苍移动，宿傩（和魔虚罗）想阻止两者接触，五条追加咏唱增强苍挡下干扰，最后苍和赫在空中相撞，爆成大范围的茈（定）。
    // 宿傩（魔虚罗在场也一样）3500（定）；五条悟开始时离爆心不超过爆炸半径的话自己挨 700（定）。三种术式都要好了才能用
    public static final long PURPLE_MAX_DAMAGE_MILLI = 3_500_000, PURPLE_MAX_SELF_DAMAGE_MILLI = 700_000;
    public static final int PURPLE_MAX_COOLDOWN = 7200;   // 占位：60 秒
    // 固定动作的时间线（tick）：苍张开 → 赫射上天 → 宿傩（魔虚罗）干扰 → 追加咏唱、苍变大把他们吸回去 → 相撞 → 爆发 → 落地
    public static final int PMAX_RISE = 120, PMAX_RED = 240, PMAX_INTERFERE = 360, PMAX_CHANT = 600, PMAX_PULLBACK = 720,
            PMAX_CLOSE = 900, PMAX_CONTACT = 1020, PMAX_HIT = 1060, PMAX_END = 1320;
    public static final int PMAX_CHANT_GAP = 100;        // 追加咏唱每段的间隔
    public static final float PMAX_SKY_Y = 250f, PMAX_GOJO_Y = 170f, PMAX_BLUE_R = 140f, PMAX_BLUE_R_MAX = 220f;
    public static final float PMAX_RADIUS = 0.35f * WIDTH; // 爆炸半径（占位）
    public static final int PMAX_HITSTUN = 150;
    // 苍、赫长按的三段咏唱（定）：苍「位相、黄昏、智慧之瞳」，赫「位相、波罗蜜、光之柱」；和茈一样念的时候不能动，松手或被攻击碰到就打断
    public static final int CHANT3_PHRASE = 36, CHANT3_PHRASES = 3; // 占位：每段 0.3 秒

    // 领域展开「无量空处」：先放过场（全场暂停），展开期间宿傩无法主动行动（定）
    public static final int DOMAIN_DURATION = 960;  // 占位：8 秒
    public static final int DOMAIN_COOLDOWN = 4800; // 占位：40 秒（领域结束后开始算）

    // 宿傩（新宿决战的技能组，Luna 查证、Astra 定的首轮数值，见 notes/宿傩平衡.md）。
    // 体术 75、解 90 是定的；无限挡住一切，只有领域展延下的体术和世界斩能打到五条悟。
    public static final float SUKUNA_SPEED = 330f;   // 比五条悟（300）快一点，追得上后撤
    public static final long S_MELEE_DAMAGE_MILLI = 75_000;
    public static final int S_MELEE_STARTUP = 8, S_MELEE_ACTIVE = 4, S_MELEE_RECOVERY = 14; // 占位
    public static final float S_MELEE_REACH = 100f, S_MELEE_LUNGE = 180f, S_MELEE_IMPULSE = 160f;
    public static final int S_MELEE_HITSTUN = 16, S_MELEE_COOLDOWN = 30;
    public static final long DISMANTLE_DAMAGE_MILLI = 90_000;
    public static final int DISMANTLE_WINDUP = 2, DISMANTLE_RECOVERY = 18; // 几乎没有前摇
    public static final float DISMANTLE_RANGE = 560f;          // 占位：射程不俗（战场宽度 44%）
    public static final int DISMANTLE_COOLDOWN = 60;           // 占位：0.5 秒
    public static final int DISMANTLE_HITSTUN = 8;
    public static final int S_ATTACK_GAP = 30;                 // 宿傩两次出手至少隔 0.25 秒

    // 领域展延：以领域包住自己，体术碰到五条悟时中和无限。展延期间不能用生得术式（解、世界斩、反转术式）
    public static final int S_AMP_STARTUP = 18, S_AMP_DURATION = 360; // 占位：开 0.15 秒，持续 3 秒
    public static final int S_AMP_EXIT = 12;      // 关闭后这么久不能用术式
    public static final int S_AMP_COOLDOWN = 240; // 占位：关闭后 2 秒

    // 魔虚罗的适应（宿傩头上的法阵）：没开展延时，每 4 秒自然转一格；解碰到无限（被挡住）并收完招，立刻转一格。
    // 两格之间至少隔 1 秒；展延期间不转（展延时用不了十种影法术）。转满 4 格且打了 12 秒以上，学会世界斩
    public static final int S_ADAPT_STAGES = 4, S_ADAPT_POINTS_PER_STAGE = 1;
    public static final int S_ADAPT_POINTS = S_ADAPT_STAGES * S_ADAPT_POINTS_PER_STAGE;
    public static final int S_ADAPT_POINT_GAP = 120;
    public static final int S_ADAPT_PASSIVE = 480;
    public static final int S_ADAPT_UNLOCK_MIN_TICKS = 1440;

    // 世界斩（斩断世界的斩击）：斩的是五条悟所在的空间，能破无限。第一次靠束缚免结印咏唱（仍有 0.4 秒裂纹预警），
    // 之后要结印 + 三段咏唱（龙鳞、反发、番之流星）+ 指向，最后 0.2 秒锁定方向和高度。站在地上发动；一道水平的斩带，跳过去就躲开
    public static final long S_WORLD_DAMAGE_MILLI = 400_000;
    public static final int S_WORLD_FIRST_WINDUP = 48;
    public static final int S_WORLD_SIGN = 24, S_WORLD_CHANT_PHRASE = 32, S_WORLD_CHANT_PHRASES = 3, S_WORLD_POINT = 24;
    public static final int S_WORLD_WINDUP = S_WORLD_SIGN + S_WORLD_CHANT_PHRASE * S_WORLD_CHANT_PHRASES + S_WORLD_POINT; // 144
    public static final int S_WORLD_RECOVERY = 72, S_WORLD_COOLDOWN = 1440; // 占位：冷却 12 秒（从起手算）
    public static final int S_WORLD_BREAK_LOCK = 36; // 咏唱被打断：这么久不能行动（和受击硬直同时计）
    public static final int S_WORLD_HITSTUN = 36;
    public static final float S_WORLD_RANGE = 960f, S_WORLD_HEIGHT = 50f, S_WORLD_HALF_THICKNESS = 10f;
    public static final float S_WORLD_IMPULSE_X = 420f, S_WORLD_IMPULSE_Y = 120f;

    // 反转术式：每秒回 170（定），不可打断（定）。起手 0.3 秒，回 1.5 秒（共 255），收招 0.2 秒；一局最多 2 次，冷却 15 秒（占位）
    public static final int S_RCT_STARTUP = 36, S_RCT_DURATION = 180, S_RCT_RECOVERY = 24;
    public static final int S_RCT_COOLDOWN = 1800, S_RCT_MAX_USES = 2;
    public static final long S_RCT_HEAL_PER_SEC_MILLI = 170_000;
    public static final long S_RCT_TOTAL_MILLI = S_RCT_HEAL_PER_SEC_MILLI * S_RCT_DURATION / HZ; // 255

    // ---- 十种影法术与原身（Luna 考证、Astra 定的首版数值，见 notes/宿傩平衡.md） ----
    // 嵌合兽「顎吐」（原作第 234 话）：结手影（可被打断）→ 从影子里飞出，从锁定点上方俯冲电击 → 消失。
    // 被无限挡住（原作没有穿透的依据），碰到五条悟时照样打断念咒和前摇。伤害固定（不吃难度倍率）
    public static final long AGITO_DAMAGE_MILLI = 60_000;
    public static final int AGITO_SIGN = 30, AGITO_DIVE = 30, AGITO_ACTIVE = 6, AGITO_RECOVERY = 18, AGITO_COOLDOWN = 720;
    public static final float AGITO_RANGE = 560f, AGITO_RADIUS = 48f, AGITO_DROP = 180f;
    public static final int AGITO_HITSTUN = 12;
    public static final float AGITO_IMPULSE_X = 120f;
    // 魔虚罗（里梅以上）：召唤要念「布瑠部由良由良」，被打断不算用掉、但 5 秒后才能重试；成功后一局不能再召（定）
    public static final int SUMMON_CHANT = 120, SUMMON_RECOVERY = 24, SUMMON_BREAK_LOCK = 36, SUMMON_RETRY = 600;
    public static final float SUMMON_OFFSET = 80f;
    public static final long MAHORAGA_HP_MILLI = 850_000;         // 定：一发茈（850）正好打掉
    public static final float MAHORAGA_HALF_W = 34f, MAHORAGA_H = 150f, MAHORAGA_SPEED = 270f;
    public static final int MAHORAGA_RISE = 48;                   // 显现后多久才开始行动（可以挨打）
    public static final int MAHORAGA_DECIDE = 12, MAHORAGA_REACT = 24;
    public static final float MAHORAGA_PULL = 0.5f;               // 苍对它的吸力减半（伤害不减）
    // 重拳、退魔之剑：伤害固定；没适应无限时被挡住（挡下也让法阵转一格），适应后能打穿无限（原作第 234 话）
    public static final long M_PUNCH_DAMAGE_MILLI = 90_000, M_SWORD_DAMAGE_MILLI = 140_000;
    public static final int M_PUNCH_STARTUP = 36, M_PUNCH_ACTIVE = 6, M_PUNCH_RECOVERY = 42, M_PUNCH_COOLDOWN = 120;
    public static final int M_SWORD_STARTUP = 60, M_SWORD_ACTIVE = 6, M_SWORD_RECOVERY = 60, M_SWORD_COOLDOWN = 240;
    public static final float M_PUNCH_REACH = 120f, M_PUNCH_DY = 100f, M_SWORD_REACH = 210f, M_SWORD_DY = 120f;
    public static final int M_PUNCH_HITSTUN = 18, M_SWORD_HITSTUN = 30, M_ATTACK_GAP = 24;
    public static final float M_PUNCH_IMPULSE_X = 180f, M_SWORD_IMPULSE_X = 300f, M_SWORD_IMPULSE_Y = 120f;
    // 原身（里梅以上，一局一次）：开始时回满血（定），之后不能用十种影法术（定）；变身 1 秒，不可打断，照常挨打
    public static final int TRANSFORM_TICKS = 120;

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
