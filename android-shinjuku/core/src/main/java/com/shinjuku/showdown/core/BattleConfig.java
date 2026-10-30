package com.shinjuku.showdown.core;

/**
 * 一场战斗的设定。进入战斗前由难度的 configure() 写入，开战后不再改。
 */
public final class BattleConfig {
    /** 难度编号（应用层 Difficulty 的序号），战斗逻辑只保存，不解释。 */
    public int difficulty;
    /** 随机种子：同一个种子 + 同一串输入 = 完全相同的一场战斗（回放、测试用）。 */
    public long seed = 0x5EED_5EEDL;

    public float gojoMaxHp = 1000f;
    public float sukunaMaxHp = 10800f; // 按自动对战模拟定的（标准脚本的五条悟胜率约 56%），见 notes/宿傩平衡.md
    /** 宿傩有没有 AI。测试模式没有（活靶子）。 */
    public boolean sukunaAi = false;
    /** 宿傩 AI 的参数（Astra 给的标准难度初值；以后更高难度改这里）。 */
    public final AiTuning ai = new AiTuning();

    public static final class AiTuning {
        public int decideInterval = 6;            // 多久看一次局势（tick）
        public int reactMin = 24, reactMax = 36;  // 看到新威胁（飞来的招、开始念咒）后多久才反应
        public int holdMin = 24, holdMax = 48;    // 一个移动意图至少保持多久，免得左右抖
        public int attackMin = 36, attackMax = 60; // 每隔多久考虑一次出手
        public float attackChance = 0.75f;        // 考虑出手时真的出手的概率
        public float mistake = 0.2f;              // 看到威胁却没反应的概率
        public float dodge = 0.65f;               // 反应过来后尝试躲开的概率
        public int jumpGap = 120;                 // 两次主动跳跃至少隔多久
        public int recoverMin = 12, recoverMax = 24; // 脱离控制（硬直、被吸、领域）后的迟疑
        public float approachWeight = 0.7f;       // 开展延贴身压制（否则在中距离用解试探）的比例
        public float approachWeightAdapting = 0.45f; // 还没学会世界斩时的比例（多用解，攒魔虚罗的适应）
        public float nearMin = 78, nearMax = 94, midMin = 300, midMax = 440, hysteresis = 20;
        public float ampMin = 100, ampMax = 220;  // 离五条悟这么远时开展延（再贴上去）
        public float worldMin = 130, worldMax = 900; // 在这个距离考虑世界斩（再近就在五条悟的拳头里了）
        public float worldChance = 0.65f;         // 世界斩能放时真的放的概率（每次决策）
        public float rctHp = 0.55f;               // 血量低于这个比例才考虑反转术式
        public float rctDistance = 650;           // 离五条悟至少这么远才敢回血
    }
}
