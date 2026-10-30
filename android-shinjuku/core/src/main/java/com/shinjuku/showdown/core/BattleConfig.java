package com.shinjuku.showdown.core;

/**
 * 一场战斗的设定。进入战斗前由难度的 configure() 写入，开战后不再改。
 */
public final class BattleConfig {
    /** 难度编号（应用层 Difficulty 的序号），战斗逻辑只保存，不解释。 */
    public int difficulty;
    /** 随机种子：同一个种子 + 同一串输入 = 完全相同的一场战斗（回放、测试用）。 */
    public long seed = 0x5EED_5EEDL;

    /** 宿傩的基础血量（三轮）：v0.10 按用户要求降低，10800 → 5400（按自动对战模拟在几档里选的，见 notes/宿傩平衡.md）。 */
    public static final float SUKUNA_BASE_HP = 5400f;

    public float gojoMaxHp = 1000f;
    /** 训练（测试模式）：打空血倒下，过一会儿满血站起来。其他难度打空就分胜负：宿傩打空胜、五条悟打空负（定）。 */
    public boolean training = true;
    public float sukunaMaxHp = SUKUNA_BASE_HP;
    /** 宿傩自己的攻击伤害倍率（难度每高一档 ×1.1，定；血量的倍率直接乘在 sukunaMaxHp 上）。 */
    public float sukunaDamageMult = 1f;
    // 宿傩能用什么（难度决定）。默认是测试用的：御厨子全套，没有十种影法术、没有原身
    public boolean shrine = true;     // 御厨子：解、世界斩（三轮的十影宿傩没有，定）
    public boolean wheel = true;      // 魔虚罗的法阵（适应无限、学会世界斩）；三轮没有
    public boolean agito = false;     // 十种影法术：嵌合兽「顎吐」
    public boolean mahoraga = false;  // 十种影法术：魔虚罗（里梅以上，定；一局只能召唤一次，定）
    public boolean trueForm = false;  // 原身（里梅以上，定；回满血，之后不能用十种影法术，定）
    public boolean spaceSlash = false; // 空间斩（里梅以上：要御厨子，三轮的十影宿傩没有）

    /** 难度的数值档：第 n 档（三轮 0）宿傩伤害、血量都 ×1.1 的 n 次方（定）。 */
    public void tier(int n) {
        double m = Math.pow(1.1, n);
        sukunaDamageMult = (float) m;
        sukunaMaxHp = (float) (SUKUNA_BASE_HP * m);
    }

    /**
     * 宿傩的难度（应用的四个难度和自动对战模拟共用）：0 三轮、1 里梅、2 万、3 史上最强。
     * 三轮是十影宿傩：没有御厨子（解、世界斩）、没有法阵和魔虚罗、不能变原身，十种影法术只有顎吐（定）。
     * 里梅以上：御厨子 + 十种影法术（顎吐、魔虚罗）+ 法阵 + 原身（定）。都没有领域（还没给素材）。
     */
    public void sukunaDifficulty(int level) {
        sukunaAi = true;
        training = false;
        ai.preset(level);
        tier(level);
        boolean full = level > 0;
        shrine = full;
        wheel = full;
        agito = true;
        mahoraga = full;
        trueForm = full;
        spaceSlash = full;
    }
    /** 宿傩有没有 AI。测试模式没有（活靶子）。 */
    public boolean sukunaAi = false;
    /** 宿傩 AI 的参数（默认是三轮的；更高难度用 ai.preset(难度)）。 */
    public final AiTuning ai = new AiTuning();

    public static final class AiTuning {
        public int decideInterval;                // 多久看一次局势（tick）
        public int reactMin, reactMax;            // 看到新威胁（飞来的招、开始念咒）后多久才反应
        public int holdMin, holdMax;              // 一个移动意图至少保持多久，免得左右抖
        public int attackMin, attackMax;          // 每隔多久考虑一次出手
        public float attackChance;                // 考虑出手时真的出手的概率
        public float mistake;                     // 看到威胁却没反应的概率
        public float dodge;                       // 反应过来后尝试躲开的概率
        public int jumpGap;                       // 两次主动跳跃至少隔多久
        public int recoverMin, recoverMax;        // 脱离控制（硬直、被吸、领域）后的迟疑
        public float approachWeight;              // 开展延贴身压制（否则在中距离用解试探）的比例
        public float approachWeightAdapting;      // 还没学会世界斩时的比例（多用解，攒魔虚罗的适应）
        public float nearMin, nearMax, midMin, midMax, hysteresis;
        public float ampMin, ampMax;              // 离五条悟这么远时开展延（再贴上去）
        public float worldMin, worldMax;          // 在这个距离考虑世界斩（再近就在五条悟的拳头里了）
        public float worldChance;                 // 世界斩能放时真的放的概率（三轮：每次决策；里梅以上：每个合格的机会抽一次）
        public float rctHp;                       // 血量低于这个比例才考虑反转术式
        public float rctDistance;                 // 离五条悟至少这么远才敢回血

        // 战术层（里梅以上才开：0 三轮，1 里梅，2 万，3 史上最强；Astra 的方案，见 notes/宿傩AI.md）
        public int tacticLevel;
        public float probeChance, ampBaitChance, punishChance, interruptChance, dodgePunishChance, escapeChance;
        public int probeCd, probeInTicks, probeOutTicks, probeObserveTicks;
        public int ampBaitCd, baitWaitMin, baitWaitMax;
        public int escapeCd, ampExitFarTicks;       // ampExitFarTicks 0：不主动关展延
        public int worldRecheckGap, worldSafetyMargin;
        public float rctSecondHp;
        public float kiteChance;                    // 没开展延时被贴身：拉开距离的概率（每次被贴近抽一次）
        public float vaultChance;                   // 被逼到墙边：从五条悟头上跳过去的概率（每次被逼到墙边抽一次）
        public int learnMinSamples;                 // 0：不读习惯
        public float learnThreshold, learnExploitChance;
        // 十种影法术与原身（里梅以上才有魔虚罗和原身；三轮只有顎吐）
        public float agitoChance;                   // 中距离牵制：每个合适的机会用顎吐的概率
        public int summonMinTicks;                  // 打了这么久（有效战斗时间）才召唤魔虚罗
        public float summonHp;                      // 血量不高于这个比例才召唤
        public float summonDistance;                // 离五条悟至少这么远
        public int summonMargin;                    // 估计五条悟来不及打断的时间要比咏唱多出这么多
        public float transformHp, transformHpEmergency; // 变原身的血线（魔虚罗不在场 / 在场时的紧急血线）
        public int transformMaxWait;                // 魔虚罗在场时最多再等多久（从血量第一次跌破血线算）
        // 恐惧（v0.11）：多挨 100 血涨几点、少挨 100 血降几点、安全时每秒自然回落几点；空间斩的基础使用率
        public float fearUp, fearDown, fearCalm, spaceChance;

        public AiTuning() {
            reset();
        }

        /** 回到三轮（标准）的参数（Astra 给的标准难度初值，按模拟调过）；战术层全关。 */
        public void reset() {
            decideInterval = 6;
            reactMin = 24;
            reactMax = 36;
            holdMin = 24;
            holdMax = 48;
            attackMin = 36;
            attackMax = 60;
            attackChance = 0.75f;
            mistake = 0.2f;
            dodge = 0.65f;
            jumpGap = 120;
            recoverMin = 12;
            recoverMax = 24;
            approachWeight = 0.7f;
            approachWeightAdapting = 0.45f;
            nearMin = 78;
            nearMax = 94;
            midMin = 300;
            midMax = 440;
            hysteresis = 20;
            ampMin = 100;
            ampMax = 220;
            worldMin = 130;
            worldMax = 900;
            worldChance = 0.65f;
            rctHp = 0.55f;
            rctDistance = 650;
            tacticLevel = 0;
            probeChance = ampBaitChance = punishChance = interruptChance = dodgePunishChance = escapeChance = 0;
            probeCd = 300;
            probeInTicks = 18;
            probeOutTicks = 24;
            probeObserveTicks = 48;
            ampBaitCd = 480;
            baitWaitMin = 12;
            baitWaitMax = 24;
            escapeCd = 120;
            ampExitFarTicks = 0;
            worldRecheckGap = 120;
            worldSafetyMargin = 12;
            rctSecondHp = 0.35f;
            kiteChance = vaultChance = 0;
            learnMinSamples = 0;
            learnThreshold = 0.7f;
            learnExploitChance = 0.65f;
            agitoChance = 0.4f;
            summonMinTicks = 360;
            summonHp = 1f;
            summonDistance = 300;
            summonMargin = 12;
            transformHp = 0.35f;
            transformHpEmergency = 0.2f;
            transformMaxWait = 360;
            fearUp = 4.0f;
            fearDown = 2.0f;
            fearCalm = 0.5f;
            spaceChance = 0;
        }

        /** 各难度的参数：0 三轮（就是 reset() 的默认值）、1 里梅、2 万、3 史上最强。越高越聪明（不加血、不加攻）。 */
        public void preset(int level) {
            reset();
            tacticLevel = level;
            if (level == 0) return;
            decideInterval = level == 1 ? 6 : 4;
            reactMin = level == 1 ? 20 : level == 2 ? 14 : 12;
            reactMax = level == 1 ? 30 : level == 2 ? 22 : 18;
            recoverMin = level == 1 ? 10 : level == 2 ? 8 : 2;   // 史上最强：被打断、被控之后几乎不迟疑
            recoverMax = level == 1 ? 18 : level == 2 ? 14 : 6;
            holdMin = level == 1 ? 24 : 18;
            holdMax = level == 1 ? 42 : level == 2 ? 36 : 30;
            attackMin = level == 1 ? 34 : 30;
            attackMax = level == 1 ? 54 : level == 2 ? 48 : 44;
            attackChance = level == 1 ? 0.78f : level == 2 ? 0.82f : 0.84f;
            mistake = level == 1 ? 0.15f : level == 2 ? 0.10f : 0.07f;
            dodge = level == 1 ? 0.72f : level == 2 ? 0.80f : 0.85f;
            jumpGap = level == 1 ? 108 : level == 2 ? 96 : 90;
            approachWeight = level == 1 ? 0.65f : 0.6f;
            approachWeightAdapting = level == 1 ? 0.42f : 0.38f;
            nearMin = level == 1 ? 82 : 86;
            nearMax = level == 1 ? 98 : 100;
            midMin = level == 1 ? 300 : 320;
            midMax = level == 1 ? 460 : 480;
            hysteresis = level == 1 ? 20 : 18;
            ampMin = level == 1 ? 115 : 120;
            ampMax = level == 1 ? 210 : 200;
            worldMin = 180;
            worldChance = level == 1 ? 0.55f : level == 2 ? 0.65f : 0.70f;
            rctHp = level == 1 ? 0.55f : 0.60f;
            rctDistance = level == 1 ? 620 : level == 2 ? 580 : 560;
            probeChance = level == 1 ? 0.30f : level == 2 ? 0.45f : 0.50f;
            probeCd = level == 1 ? 300 : 240;
            probeObserveTicks = level == 3 ? 60 : 48;
            ampBaitChance = level == 1 ? 0 : level == 2 ? 0.35f : 0.45f;
            ampBaitCd = level == 2 ? 480 : 420;
            baitWaitMax = level == 3 ? 30 : 24;
            punishChance = level == 1 ? 0.55f : level == 2 ? 0.75f : 0.85f;
            interruptChance = level == 1 ? 0.65f : level == 2 ? 0.80f : 0.88f;
            dodgePunishChance = level == 1 ? 0 : level == 2 ? 0.65f : 0.80f;
            escapeChance = level == 1 ? 0.65f : level == 2 ? 0.80f : 0.90f;
            escapeCd = level == 1 ? 120 : level == 2 ? 96 : 90;
            ampExitFarTicks = level == 1 ? 0 : level == 2 ? 72 : 54;
            worldSafetyMargin = level == 1 ? 12 : level == 2 ? 18 : 24;
            rctSecondHp = level == 1 ? 0.35f : level == 2 ? 0.40f : 0.45f;
            learnMinSamples = level == 3 ? 6 : 0;
            kiteChance = level == 1 ? 0.5f : level == 2 ? 0.75f : 1f;
            vaultChance = level == 1 ? 0.7f : 1f;
            agitoChance = level == 1 ? 0.3f : level == 2 ? 0.25f : 0.2f;
            summonMinTicks = level == 1 ? 360 : level == 2 ? 240 : 120; // 开场拉开距离时就召（越难越早）
            summonHp = 1f;
            summonDistance = level == 1 ? 300 : level == 2 ? 280 : 260;
            summonMargin = level == 1 ? 12 : level == 2 ? 18 : 24;
            transformHp = level == 1 ? 0.35f : level == 2 ? 0.3f : 0.25f;
            transformHpEmergency = level == 1 ? 0.2f : level == 2 ? 0.18f : 0.15f;
            transformMaxWait = level == 1 ? 360 : level == 2 ? 480 : 600;
            // 越难越会收心：多挨打涨得少、没挨打降得快
            fearUp = level == 1 ? 3.6f : level == 2 ? 3.2f : 2.8f;
            fearDown = level == 1 ? 2.4f : level == 2 ? 2.8f : 3.2f;
            fearCalm = level == 1 ? 0.7f : level == 2 ? 0.9f : 1.1f;
            spaceChance = level == 1 ? 0.55f : level == 2 ? 0.65f : 0.75f;
        }
    }
}
