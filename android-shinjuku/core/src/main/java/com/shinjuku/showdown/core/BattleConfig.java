package com.shinjuku.showdown.core;

/**
 * 一场战斗的设定。进入战斗前由难度的 configure() 写入；以后宿傩的技能、AI 强度也加在这里。
 */
public final class BattleConfig {
    /** 难度编号（应用层 Difficulty 的序号），战斗逻辑只保存，不解释。 */
    public int difficulty;
    /** 随机种子：同一个种子 + 同一串输入 = 完全相同的一场战斗（回放、测试用）。 */
    public long seed = 0x5EED_5EEDL;

    public float gojoMaxHp = 1000f;
    public float sukunaMaxHp = 4000f; // 占位
    /** 这一版宿傩是活靶子：没有技能、没有 AI。 */
    public boolean sukunaAi = false;
}
