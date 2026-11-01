package com.shinjuku.showdown;

/**
 * 音效的输出端（v0.15）。SfxDirector 只调这几个方法：游戏里是 SoundPoolOut（SoundPool），测试里换成记录调用的假实现。
 * 所有方法都在战斗线程上调用，实现不能在这里分配对象。
 */
interface SfxOut {
    /**
     * 单发：编号 cue 的第 variant 个变体（0 起），pan −1 左 … 1 右，gain 线性（≤ 1），rate 播放速率（音高），priority 0–3。
     * 返回句柄（之后可以 stop 掉）；没播出来（没加载好、关着）返回 0。
     */
    int play(int cue, int variant, float pan, float gain, float rate, int priority);

    /** 掐掉一个单发（句柄已经播完或无效也没关系）。 */
    void stop(int handle);

    /** 循环音占用第 slot 个循环位（同一个位同时只响一个）；已经在响同一个音就只更新音量和声像。 */
    void loop(int slot, int cue, float pan, float gain);

    /** 停掉第 slot 个循环位。 */
    void stopLoop(int slot);

    /** 全部停下（过场、暂停、结束）：单发和循环都停。 */
    void stopAll();
}
