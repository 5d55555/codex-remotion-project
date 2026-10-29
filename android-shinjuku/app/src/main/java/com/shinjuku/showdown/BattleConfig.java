package com.shinjuku.showdown;

/**
 * 一场战斗的设定。进入战斗前由 Difficulty.configure() 写入；以后宿傩的技能、AI 强度也加在这里。
 * 带“占位”的数值是暂定的，等确定后再改。
 */
final class BattleConfig {
    Difficulty difficulty = Difficulty.MIWA;

    // 五条悟
    float gojoMaxHp = 1000f;

    // 宿傩（这一版是活靶子：没有技能、没有 AI）
    float sukunaMaxHp = 4000f; // 占位
    boolean sukunaAi = false;
}
