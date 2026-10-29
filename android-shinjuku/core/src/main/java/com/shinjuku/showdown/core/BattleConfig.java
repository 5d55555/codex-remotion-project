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
    public float sukunaMaxHp = 4000f; // 占位
    /** 宿傩有没有 AI。测试模式没有（活靶子）。 */
    public boolean sukunaAi = false;
    /** 宿傩 AI 的参数（Astra 给的标准难度初值；以后更高难度改这里）。 */
    public final AiTuning ai = new AiTuning();

    public static final class AiTuning {
        public int decideInterval = 6;            // 多久看一次局势（tick）
        public int reactMin = 24, reactMax = 36;  // 看到新威胁后多久才反应
        public int holdMin = 24, holdMax = 48;    // 一个移动意图至少保持多久，免得左右抖
        public int attackMin = 60, attackMax = 96; // 每隔多久考虑一次出手
        public float attackChance = 0.65f;        // 考虑出手时真的出手的概率
        public float meleeWeight = 0.75f;         // 近身时用体术（否则用解）的比例
        public float mistake = 0.2f;              // 看到威胁却没反应的概率
        public float dodge = 0.6f;                // 反应过来后尝试躲开的概率
        public int jumpGap = 120;                 // 两次主动跳跃至少隔多久
        public int recoverMin = 12, recoverMax = 24; // 脱离控制（硬直、被吸、领域）后的迟疑
        public float approachWeight = 0.6f;       // 贴身压制（否则拉开距离用解）的比例
        public float nearMin = 65, nearMax = 85, midMin = 220, midMax = 340, hysteresis = 25;
    }
}
