package com.shinjuku.showdown;

import com.shinjuku.showdown.core.Attack;
import com.shinjuku.showdown.core.BlueOrb;
import com.shinjuku.showdown.core.Events;
import com.shinjuku.showdown.core.Fighter;
import com.shinjuku.showdown.core.Tuning;
import com.shinjuku.showdown.core.World;

/**
 * 技能音效的导演（v0.15；设计是 Astra 的方案，见 notes/音效设计.md）。只在战斗线程上用，不依赖 Android，零分配。
 * 每推进一个逻辑 tick（以及过场结束、展开领域之后）调一次 update()：读自己的事件游标（不和画面共用）和角色状态的变化，
 * 先把这一 tick 的事件整批看一遍做仲裁（黑闪替代普通命中、赫的爆发替代赫的受击、苍的持续伤害不出声……），再出声；
 * 没有事件的动作（体术起手、苍/赫/茈的按住、宿傩体术、顎吐结手影、魔虚罗起手、跳、轻落地）按状态变化触发；
 * 苍的吸引、无量空处、领域对撞、领域展延、反转术式这些持续的状态用循环音，每 tick 按状态决定开关。
 * 放领域展开的过场（视频自带声音）、切后台时全部停下、事件跳过，恢复后循环音按状态重新起，单发不补。
 * 只读世界，不改世界：战斗结果和有没有音效无关。随机变化（选变体、微调音高）用自己的随机数。
 */
final class SfxDirector {
    private static final int SLOT_FIELD = 0, SLOT_DOMAIN = 1, SLOT_SUKUNA = 2;
    private static final int[] MIN_TICKS = new int[SfxBank.COUNT];   // 同一个音的最短重复间隔（tick）
    private static final int[] PRIORITY = new int[SfxBank.COUNT];
    private static final float[] JITTER = new float[SfxBank.COUNT];  // 随机音高的幅度（rate ± 这么多）

    static {
        java.util.Arrays.fill(MIN_TICKS, 6);
        java.util.Arrays.fill(PRIORITY, 1);
        int[] hits = {SfxBank.HIT_LIGHT, SfxBank.HIT_LIGHT_NB, SfxBank.HIT_MEDIUM, SfxBank.HIT_MEDIUM_NB, SfxBank.HIT_HEAVY};
        for (int c : hits) {
            PRIORITY[c] = 2;
            JITTER[c] = 0.025f;
        }
        MIN_TICKS[SfxBank.HIT_LIGHT] = MIN_TICKS[SfxBank.HIT_LIGHT_NB] = 5;
        MIN_TICKS[SfxBank.HIT_MEDIUM] = MIN_TICKS[SfxBank.HIT_MEDIUM_NB] = 7;
        MIN_TICKS[SfxBank.HIT_HEAVY] = 10;
        for (int c : new int[]{SfxBank.SWING_JAB, SfxBank.SWING_KICK, SfxBank.SWING_UPPER, SfxBank.SUKUNA_SWING, SfxBank.DODGE, SfxBank.JUMP,
                SfxBank.LAND_LIGHT, SfxBank.LAND_HEAVY, SfxBank.INFINITY_BLOCK, SfxBank.CAST_BREAK, SfxBank.WHEEL_CLICK, SfxBank.SLASH_DISMANTLE,
                SfxBank.SHRINE_SLASH_HIT, SfxBank.SHRINE_SLASH_MISS, SfxBank.FIST_CHARGE}) JITTER[c] = 0.02f;
        MIN_TICKS[SfxBank.SWING_JAB] = MIN_TICKS[SfxBank.SWING_KICK] = MIN_TICKS[SfxBank.SWING_UPPER] = 8;
        MIN_TICKS[SfxBank.SUKUNA_SWING] = 11;
        MIN_TICKS[SfxBank.INFINITY_BLOCK] = 8;
        MIN_TICKS[SfxBank.WHEEL_CLICK] = 18;
        MIN_TICKS[SfxBank.DODGE] = MIN_TICKS[SfxBank.JUMP] = 18;
        MIN_TICKS[SfxBank.SLASH_DISMANTLE] = 8;
        MIN_TICKS[SfxBank.DISSOLVE] = 12;
        // 危险预警、决定性的节点：最高优先级（SoundPool 声部不够时最后才被挤掉）
        for (int c : new int[]{SfxBank.CAST_BREAK, SfxBank.PURPLE_FIRE, SfxBank.PURPLE_FIRE_200, SfxBank.PURPLE_FIRE_TP, SfxBank.HOLLOW_FORM,
                SfxBank.BLACK_FLASH, SfxBank.BLACK_FLASH_FIST, SfxBank.VOID_OPEN, SfxBank.CINE_CONTACT, SfxBank.CINE_BLAST, SfxBank.WORLD_SIGN,
                SfxBank.WORLD_SIGN_CRACK, SfxBank.SUKUNA_CHANT, SfxBank.LOCK_WORLD, SfxBank.LOCK_SPACE, SfxBank.SLASH_WORLD, SfxBank.SLASH_SPACE,
                SfxBank.SPACE_WINDUP, SfxBank.SPACE_KILL, SfxBank.SHRINE_SIGN, SfxBank.SHRINE_OPEN, SfxBank.SHRINE_WARN, SfxBank.BREAK_SHRINE,
                SfxBank.BREAK_VOID, SfxBank.ADAPT_WORLD, SfxBank.ADAPT_INFINITY, SfxBank.SIMPLE_BREAK}) PRIORITY[c] = 3;
        for (int c : new int[]{SfxBank.CHANT_BLUE, SfxBank.CHANT_RED, SfxBank.CHANT_PURPLE, SfxBank.BLUE_ANCHOR, SfxBank.BLUE_ANCHOR_MAX,
                SfxBank.RED_FIRE, SfxBank.RED_FIRE_MAX, SfxBank.RED_FIRE_TP, SfxBank.RED_BURST, SfxBank.RED_BURST_MAX, SfxBank.RED_BURST_TP,
                SfxBank.TP_DEPART, SfxBank.TP_ARRIVE, SfxBank.VOID_CLOSE, SfxBank.INFINITY_BLOCK, SfxBank.SLASH_DISMANTLE, SfxBank.AMP_ON,
                SfxBank.AGITO_ZAP, SfxBank.SUMMON_CHANT, SfxBank.MAHORAGA_RISE, SfxBank.TRANSFORM_START, SfxBank.TRANSFORM_DONE, SfxBank.EXHAUST,
                SfxBank.SHRINE_SLASH_HIT, SfxBank.SHRINE_SLASH_MISS, SfxBank.SHRINE_END, SfxBank.WHEEL_CLICK, SfxBank.MAHO_SWORD_WINDUP,
                SfxBank.MAHORAGA_FALL, SfxBank.KO, SfxBank.SIG_WIN, SfxBank.SIG_LOSE, SfxBank.SIG_START, SfxBank.SIG_REVIVE,
                SfxBank.LAND_HEAVY, SfxBank.DOWN_THUD, SfxBank.SIMPLE_OPEN, SfxBank.SIMPLE_ERODE}) PRIORITY[c] = 2;
        for (int c : new int[]{SfxBank.JUMP, SfxBank.DISSOLVE, SfxBank.DISSOLVE_LONG, SfxBank.HEAD_TURN, SfxBank.HOLD_BLUE, SfxBank.HOLD_RED,
                SfxBank.HOLD_PURPLE}) PRIORITY[c] = 0;
    }

    private final SfxOut out;
    private long seen = -1;           // 事件游标；-1：还没对上（第一次 update 从当前位置开始）
    private boolean silent;           // 过场、后台：停着
    private boolean paused;           // BattleLoop 停了（切后台、Surface 没了）
    private boolean started;          // 开场提示放过了
    private int rng = 0x2545F491;     // 音频自己的随机数（xorshift32）
    private final int[] last = new int[SfxBank.COUNT];
    private int now;                  // world.tick（命中停顿、固定动作里也走：和真实时间同步）

    // 上一 tick 的状态（没有事件的动作按状态变化触发）
    private int gState = -1, sState = -1, mState = -1, gHitstun, sStagger;
    private boolean gGround = true, sGround = true, snapped;

    // 要能掐掉的单发：五条悟的起手、咏唱段；宿傩的咏唱/结印/召唤/空间斩起手/变身；魔虚罗举剑；顎吐结手影
    private int gHold, gChant, sLong, mWind, sSign;
    private boolean worldFirst;       // 这次世界斩是第一次（束缚，免咏唱：裂纹预警）
    private boolean rctOn;            // 反转术式在回血
    private int lastBodyTick = -1000; // 上一次带低频 body 的普通受击
    private int tpBlueHits;           // 瞬移苍连段第几下

    // 一批事件的仲裁标志和受击合并（按攻击种类）
    private static final int KINDS = 17;
    private final float[] dmgMax = new float[KINDS], dmgX = new float[KINDS];
    private final int[] dmgN = new int[KINDS];

    SfxDirector(SfxOut out) {
        this.out = out;
        java.util.Arrays.fill(last, -100_000);
    }

    /* ---------------- 生命周期（战斗线程） ---------------- */

    /** BattleLoop 停了（切后台、Surface 没了）：全停。恢复后循环音按状态重新起，单发不补。 */
    void setPaused(boolean p) {
        if (p && !paused) silence();
        paused = p;
    }

    /** 分出胜负、结算弹出来的那一刻。 */
    void result(boolean win) {
        if (silent || paused) return;
        play(win ? SfxBank.SIG_WIN : SfxBank.SIG_LOSE, 0, 1f, -1);
    }

    private void silence() {
        out.stopAll();
        gHold = gChant = sLong = mWind = sSign = 0;
    }

    /* ---------------- 每个逻辑 tick ---------------- */

    void update(World w) {
        Events ev = w.events;
        long end = ev.written;
        if (seen < 0) seen = end;                                  // 第一次：从现在开始，不补之前的
        else if (end - seen > Events.CAP) seen = end - Events.CAP; // 落后太多（不会发生）：只看环里还在的
        now = w.tick;
        if (w.cutscene || paused) { // 过场（视频自带声音）、后台：不出声，事件跳过
            if (!silent) {
                silence();
                silent = true;
            }
            seen = end;
            snapshot(w);
            return;
        }
        silent = false;
        if (!started) {
            started = true;
            play(SfxBank.SIG_START, 0, 1f, -1);
        }
        // 1. 先把这批事件看一遍：要仲裁的标志、按攻击种类合并的受击
        boolean blackFlash = false, redBurst = false, cineBlast = false, spaceKill = false, voidBroken = false;
        int landed = 0; // 这一 tick 有 LAND 事件的角色（按 Fighter.id 的位）
        boolean breakEv = false, adaptBig = false;
        for (int k = 0; k < KINDS; k++) {
            dmgMax[k] = 0;
            dmgN[k] = 0;
            dmgX[k] = 0;
        }
        for (long n = seen; n < end; n++) {
            int i = (int) (n & Events.MASK);
            int type = ev.type[i], kind = ev.kind[i];
            float v = ev.value[i];
            switch (type) {
                case Events.BLACK_FLASH:
                    blackFlash = true;
                    break;
                case Events.RED_BURST:
                    redBurst = true;
                    break;
                case Events.CINE:
                    if ((int) v == Events.CINE_BLAST) cineBlast = true;
                    break;
                case Events.SPACE:
                    if ((int) v == 3) spaceKill = true;
                    if ((int) v == -1) breakEv = true;
                    break;
                case Events.LAND:
                    if (kind >= 0 && kind < Fighter.COUNT) landed |= 1 << kind;
                    break;
                case Events.SHRINE:
                    if ((int) v == Events.SHRINE_VOID_BROKEN) voidBroken = true;
                    if ((int) v == Events.SHRINE_SIGN_BROKEN) breakEv = true;
                    break;
                case Events.CHANT:
                case Events.WORLD_CHANT:
                case Events.SUMMON:
                    if ((int) v == -1) breakEv = true;
                    break;
                case Events.ADAPT:
                    if (kind != 0) adaptBig = true;
                    break;
                case Events.DAMAGE:
                    if (kind > 0 && kind < KINDS) {
                        dmgMax[kind] = Math.max(dmgMax[kind], v);
                        dmgX[kind] += ev.x[i];
                        dmgN[kind]++;
                    }
                    break;
                default:
                    break;
            }
        }
        // 2. 逐个事件出声
        for (long n = seen; n < end; n++) {
            int i = (int) (n & Events.MASK);
            event(w, ev.type[i], ev.kind[i], ev.value[i], ev.x[i], adaptBig, voidBroken);
        }
        seen = end;
        // 3. 受击：同一 tick 同一种攻击合并成一声；有专用音的不再叠通用受击
        hits(w, blackFlash, redBurst, cineBlast, spaceKill);
        // 4. 没有事件的动作：按状态变化
        if (snapped) edges(w, landed, breakEv);
        // 5. 持续状态的循环音
        loops(w);
        snapshot(w);
    }

    private void snapshot(World w) {
        gState = w.gojo.state;
        sState = w.sukuna.state;
        mState = w.mahoraga.present ? w.mahoraga.state : -1;
        gGround = w.gojo.onGround;
        sGround = w.sukuna.onGround;
        gHitstun = w.gojo.hitstun;
        sStagger = w.sStagger;
        snapped = true;
    }

    private void event(World w, int type, int kind, float value, float x, boolean adaptBig, boolean voidBroken) {
        int v = (int) value;
        switch (type) {
            case Events.BLACK_FLASH:
                play(kind == Attack.BLUE_FIST ? SfxBank.BLACK_FLASH_FIST : SfxBank.BLACK_FLASH, x);
                break;
            case Events.RED_FIRE:
                stopG();
                play(kind == 2 ? SfxBank.RED_FIRE_TP : kind == 1 ? SfxBank.RED_FIRE_MAX : SfxBank.RED_FIRE, x);
                break;
            case Events.RED_BURST:
                play(kind == 2 ? SfxBank.RED_BURST_TP : kind == 1 ? SfxBank.RED_BURST_MAX : SfxBank.RED_BURST, x);
                break;
            case Events.PURPLE_FIRE:
                stopG();
                play(kind == 2 ? SfxBank.PURPLE_FIRE_TP : kind == 1 ? SfxBank.PURPLE_FIRE_200 : SfxBank.PURPLE_FIRE, x);
                break;
            case Events.BLUE_SPAWN:
                stopG();
                play(v == 1 ? SfxBank.BLUE_LAUNCH_MAX : SfxBank.BLUE_LAUNCH, x);
                break;
            case Events.BLUE_LAND:
                play(v == 1 ? SfxBank.BLUE_ANCHOR_MAX : SfxBank.BLUE_ANCHOR, x);
                break;
            case Events.BLUE_END:
            case Events.HOLLOW_END:
                play(SfxBank.DISSOLVE, x);
                break;
            case Events.HOLLOW_FORM:
                play(SfxBank.HOLLOW_FORM, x);
                break;
            case Events.INFINITY_BLOCK:
                play(SfxBank.INFINITY_BLOCK, x);
                break;
            case Events.KO:
                if (kind != Fighter.MAHORAGA) play(SfxBank.KO, x);
                break;
            case Events.REVIVE:
                play(SfxBank.SIG_REVIVE, x);
                break;
            case Events.LAND: { // 落地速度：普通跳起落下来 600–720，被打飞摔下来更快；被打倒摔地是倒地闷响
                Fighter f = kind == Fighter.GOJO ? w.gojo : kind == Fighter.MAHORAGA ? w.mahoraga : w.sukuna;
                play(f.downTicks > 0 ? SfxBank.DOWN_THUD : value >= 800 ? SfxBank.LAND_HEAVY : SfxBank.LAND_LIGHT, x);
                break;
            }
            case Events.CAST:
                if (v == 5) { // 茈·最大输出的固定动作开始：停掉咏唱，空间被托起；苍的循环在固定动作里慢慢起来
                    stopG();
                    play(SfxBank.CINE_RISE, 0, 1f, -1);
                } else if (v == 4) { // 瞬移的前摇（瞬移·茈先念咒，咒词另有事件）
                    stopG();
                    gHold = play(kind == Attack.RED ? SfxBank.HOLD_RED : kind == Attack.PURPLE ? SfxBank.HOLD_PURPLE : SfxBank.HOLD_BLUE, x);
                } else if (kind == Attack.WORLD_SLASH) {
                    worldFirst = v == 3;
                }
                break;
            case Events.CHANT:
                if (v < 0) {
                    stopG();
                    play(SfxBank.CAST_BREAK, x);
                } else {
                    int cue, seg;
                    if (kind == Attack.PURPLE_MAX) {
                        cue = v < 3 ? SfxBank.CHANT_BLUE : v < 6 ? SfxBank.CHANT_RED : SfxBank.CHANT_PURPLE;
                        seg = v < 3 ? v : v < 6 ? v - 3 : v - 6;
                    } else {
                        cue = kind == Attack.RED ? SfxBank.CHANT_RED : kind == Attack.PURPLE ? SfxBank.CHANT_PURPLE : SfxBank.CHANT_BLUE;
                        seg = v;
                    }
                    stopG();
                    gChant = playV(cue, Math.min(3, seg), x, 1f);
                }
                break;
            case Events.DOMAIN_OPEN:
                play(SfxBank.VOID_OPEN, 0, 1f, -1);
                break;
            case Events.DOMAIN_CLOSE:
                if (!voidBroken) play(SfxBank.VOID_CLOSE, 0, 1f, -1);
                break;
            case Events.SLASH:
                if (kind == Attack.WORLD_SLASH) {
                    stopS();
                    play(SfxBank.SLASH_WORLD, x);
                } else if (kind == Attack.SPACE_SLASH) {
                    stopS();
                    play(SfxBank.SLASH_SPACE, x);
                } else {
                    play(SfxBank.SLASH_DISMANTLE, x);
                }
                break;
            case Events.AMP:
                play(v == 1 ? SfxBank.AMP_ON : SfxBank.AMP_OFF, x);
                break;
            case Events.ADAPT:
                if (kind == 1) play(SfxBank.ADAPT_WORLD, x);
                else if (kind == 2) play(SfxBank.ADAPT_INFINITY, x);
                else if (!adaptBig) play(SfxBank.WHEEL_CLICK, x);
                break;
            case Events.WORLD_CHANT:
                if (v < 0) {
                    stopS();
                    play(SfxBank.CAST_BREAK, x);
                } else if (v == 0) {
                    stopS();
                    sLong = play(worldFirst ? SfxBank.WORLD_SIGN_CRACK : SfxBank.WORLD_SIGN, x);
                } else if (v <= 3) {
                    stopS();
                    sLong = playV(SfxBank.SUKUNA_CHANT, v - 1, x, 1f);
                } else {
                    play(SfxBank.LOCK_WORLD, x);
                }
                break;
            case Events.RCT:
                rctOn = v == 1;
                break;
            case Events.DODGE:
                play(SfxBank.DODGE, x);
                break;
            case Events.TELEPORT:
                stopG();
                play(SfxBank.TP_DEPART, x);
                play(SfxBank.TP_ARRIVE, value);
                tpBlueHits = 0;
                break;
            case Events.SUMMON:
                if (v == 0) {
                    stopS();
                    sLong = play(SfxBank.SUMMON_CHANT, x);
                } else if (v == 1) {
                    stopS();
                    play(SfxBank.MAHORAGA_RISE, x);
                } else {
                    stopS();
                    play(SfxBank.CAST_BREAK, x);
                }
                break;
            case Events.AGITO:
                if (v == 0) {
                    out.stop(sSign);
                    sSign = 0;
                    play(SfxBank.AGITO_FLY, x);
                } else if (v == 1) {
                    play(SfxBank.AGITO_ZAP, x);
                } else {
                    play(SfxBank.DISSOLVE, x);
                }
                break;
            case Events.TRANSFORM:
                if (v == 1) {
                    stopS();
                    sLong = play(SfxBank.TRANSFORM_START, x);
                } else {
                    play(SfxBank.TRANSFORM_DONE, x);
                }
                break;
            case Events.MAHORAGA_GONE:
                out.stop(mWind);
                mWind = 0;
                play(v == 0 ? SfxBank.MAHORAGA_FALL : SfxBank.DISSOLVE, x);
                break;
            case Events.CINE:
                cine(v, kind, x);
                break;
            case Events.SPACE:
                if (v == 0) {
                    stopS();
                    sLong = play(SfxBank.SPACE_WINDUP, x);
                } else if (v == 1) {
                    stopS();
                    play(SfxBank.LOCK_SPACE, x);
                } else if (v == 3) {
                    play(SfxBank.SPACE_KILL, x);
                } else if (v == -1) {
                    stopS();
                    play(SfxBank.CAST_BREAK, x);
                } else if (v == 4) {
                    play(SfxBank.EXHAUST, x);
                } else if (v == 5) {
                    play(SfxBank.RESTORE_EXHAUST, x);
                }
                break;
            case Events.SHRINE:
                shrine(v, kind, x);
                break;
            case Events.SIMPLE_DOMAIN: // 简易领域（v0.17）：开、被削一层、削光碎掉；别的原因收掉只是轻轻散掉
                if (v == Events.SIMPLE_OPEN) play(SfxBank.SIMPLE_OPEN, x);
                else if (v == Events.SIMPLE_ERODE) play(SfxBank.SIMPLE_ERODE, x);
                else if (v == Events.SIMPLE_CLOSE && kind == Events.SIMPLE_ERODED) play(SfxBank.SIMPLE_BREAK, x);
                else if (v == Events.SIMPLE_CLOSE && kind != Events.SIMPLE_FIGHT_END) play(SfxBank.DISSOLVE, x);
                break;
            default:
                break;
        }
    }

    /** 茈·最大输出的固定动作里的节点。 */
    private void cine(int v, int kind, float x) {
        switch (v) {
            case Events.CINE_RED:
                play(SfxBank.RED_FIRE_MAX, x);
                break;
            case Events.CINE_SLASH:
                play(SfxBank.SLASH_CINE, x);
                break;
            case Events.CINE_MAHO_SWING:
                play(SfxBank.MAHO_CINE_SWING, x);
                break;
            case Events.CINE_CHANT:
                stopG();
                gChant = playV(SfxBank.CHANT_BLUE, Math.min(3, Math.max(0, kind)), x, 1f);
                break;
            case Events.CINE_CONTACT:
                out.stopLoop(SLOT_FIELD);
                play(SfxBank.CINE_CONTACT, x);
                break;
            case Events.CINE_BLAST:
                play(SfxBank.CINE_BLAST, 0, 1f, -1);
                break;
            case Events.CINE_END:
                play(SfxBank.DISSOLVE_LONG, x);
                break;
            case Events.CINE_TAP:
                play(SfxBank.SHOULDER_TAP, x);
                break;
            case Events.CINE_TURN:
                play(SfxBank.HEAD_TURN, x);
                break;
            default:
                break;
        }
    }

    private void shrine(int v, int kind, float x) {
        switch (v) {
            case Events.SHRINE_SIGN:
                stopS();
                sLong = play(SfxBank.SHRINE_SIGN, x);
                break;
            case Events.SHRINE_SIGN_BROKEN:
                stopS();
                play(SfxBank.CAST_BREAK, x);
                break;
            case Events.SHRINE_OPEN:
                play(SfxBank.SHRINE_OPEN, 0, 1f, -1);
                break;
            case Events.SHRINE_WARN:
                play(SfxBank.SHRINE_WARN, x);
                break;
            case Events.SHRINE_SLASH:
                if (kind == 1) play(SfxBank.SHRINE_SLASH_HIT, x);
                else if (kind == 0) play(SfxBank.SHRINE_SLASH_MISS, x);
                else if (kind == 3) play(SfxBank.SHRINE_SLASH_MISS, -1, 0.5f, x); // 被简易领域中和：很轻的切口，主声是“被削”
                break;
            case Events.SHRINE_BROKEN:
                play(SfxBank.BREAK_SHRINE, 0, 1f, -1);
                break;
            case Events.SHRINE_VOID_BROKEN:
                play(SfxBank.BREAK_VOID, 0, 1f, -1);
                break;
            case Events.SHRINE_END:
                play(SfxBank.SHRINE_END, 0, 1f, -1);
                break;
            case Events.SHRINE_BURN_END:
                play(SfxBank.RESTORE_BURN, x);
                break;
            default:
                break; // SHRINE_CLASH：对撞是循环音（loops）
        }
    }

    /** 受击：按攻击种类合并，按伤害分轻（< 80）中（< 250）重；连着的轻、中击只有隔开 0.15 秒的那一下带低频 body。 */
    private void hits(World w, boolean blackFlash, boolean redBurst, boolean cineBlast, boolean spaceKill) {
        int played = 0;
        for (int k = KINDS - 1; k > 0 && played < 2; k--) {
            if (dmgN[k] == 0) continue;
            if (k == Attack.BLUE_FIELD || k == Attack.SHRINE_SLASH || k == Attack.AGITO || k == Attack.WORLD_SLASH || k == Attack.DISMANTLE) continue;
            if (k == Attack.RED && redBurst) continue;
            if ((k == Attack.MELEE || k == Attack.BLACK_FLASH || k == Attack.BLUE_FIST) && blackFlash) continue;
            if (k == Attack.PURPLE_MAX && cineBlast) continue;
            if (k == Attack.SPACE_SLASH && spaceKill) continue;
            float dmg = dmgMax[k], x = dmgX[k] / dmgN[k];
            int cue;
            if (dmg >= 250) {
                cue = SfxBank.HIT_HEAVY;
                lastBodyTick = now;
            } else {
                boolean body = now - lastBodyTick >= 18;
                if (k == Attack.BLUE_FIST && w.gojo.state == Fighter.TP_BLUE) { // 瞬移苍连段：第 1、5 下完整
                    tpBlueHits++;
                    body = tpBlueHits == 1 || tpBlueHits >= Tuning.TP_BLUE_HITS;
                }
                if (body) lastBodyTick = now;
                cue = dmg >= 80 ? (body ? SfxBank.HIT_MEDIUM : SfxBank.HIT_MEDIUM_NB) : (body ? SfxBank.HIT_LIGHT : SfxBank.HIT_LIGHT_NB);
            }
            play(cue, x);
            played++;
        }
    }

    private static boolean holdState(int st) {
        return st == Fighter.BLUE_HOLD || st == Fighter.RED_HOLD || st == Fighter.PURPLE_WINDUP || st == Fighter.FIST_WINDUP || st == Fighter.TP_WINDUP;
    }

    private static boolean chantState(int st) {
        return st == Fighter.BLUE_CHANT || st == Fighter.RED_CHANT || st == Fighter.PURPLE_CHANT || st == Fighter.PURPLE_MAX_CHANT
                || st == Fighter.TP_WINDUP;
    }

    private static boolean sukunaLong(int st) {
        return st == Fighter.S_WORLD || st == Fighter.S_SUMMON || st == Fighter.S_SPACE || st == Fighter.S_DOMAIN_SIGN || st == Fighter.S_TRANSFORM;
    }

    /** 没有事件的动作：按这一 tick 的状态变化。 */
    private void edges(World w, int landed, boolean breakEv) {
        Fighter g = w.gojo, s = w.sukuna, m = w.mahoraga;
        int gs = g.state, ss = s.state;
        if (gs != gState) {
            switch (gs) {
                case Fighter.MELEE: {
                    int c = Math.floorMod(w.meleeCombo - 1, 3);
                    play(c == 0 ? SfxBank.SWING_JAB : c == 1 ? SfxBank.SWING_KICK : SfxBank.SWING_UPPER, g.x);
                    break;
                }
                case Fighter.FIST_WINDUP:
                    stopG();
                    gHold = play(SfxBank.FIST_CHARGE, g.x);
                    break;
                case Fighter.FIST_ACTIVE:
                    out.stop(gHold);
                    gHold = 0;
                    play(SfxBank.BLUE_LAUNCH, 0, 0.56f, g.x); // 苍拳出手（−5 dB）；打中另算
                    break;
                case Fighter.BLUE_HOLD:
                    stopG();
                    gHold = play(SfxBank.HOLD_BLUE, g.x);
                    break;
                case Fighter.RED_HOLD:
                    stopG();
                    gHold = play(SfxBank.HOLD_RED, g.x);
                    break;
                case Fighter.PURPLE_WINDUP:
                    stopG();
                    gHold = play(SfxBank.HOLD_PURPLE, g.x);
                    break;
                default:
                    break;
            }
            // 离开按住、咏唱：停掉起手、咏唱的余音（释放、念下一段另有声音）；被打得出硬直而没有打断事件的，补一声“断”
            if ((holdState(gState) || chantState(gState)) && !holdState(gs) && !chantState(gs)) {
                stopG();
                if (g.hitstun > gHitstun && !breakEv) play(SfxBank.CAST_BREAK, g.x);
            }
        }
        if (ss != sState) {
            if (ss == Fighter.S_MELEE) play(SfxBank.SUKUNA_SWING, s.x);
            if (ss == Fighter.S_AGITO) sSign = play(SfxBank.AGITO_SIGN, s.x);
            if (sState == Fighter.S_AGITO && ss != Fighter.S_AGITO) { // 离开结手影（被打断、被控）：掐掉；正常飞出时已经停过了
                out.stop(sSign);
                sSign = 0;
            }
            if (sukunaLong(sState) && !sukunaLong(ss)) stopS(); // 收招、被打断：咏唱/结印的余音掐掉
        }
        if (w.sStagger > 0 && sStagger == 0 && !breakEv) play(SfxBank.CAST_BREAK, s.x); // 宿傩的招被打断
        // 魔虚罗起手
        int ms = m.present ? m.state : -1;
        if (ms != mState) {
            if (ms == Fighter.M_PUNCH) mWind = play(SfxBank.MAHO_PUNCH_WINDUP, m.x);
            else if (ms == Fighter.M_SWORD) mWind = play(SfxBank.MAHO_SWORD_WINDUP, m.x);
            else if (mState == Fighter.M_SWORD || mState == Fighter.M_PUNCH) {
                out.stop(mWind); // 离开起手（被打、被控、展延暂停、退场）：掐掉；正常收招时起手音早就放完了
                mWind = 0;
            }
        }
        // 跳、轻落地（重落地有 LAND 事件）
        jumpLand(g, gGround, (landed & 1 << Fighter.GOJO) != 0);
        jumpLand(s, sGround, (landed & 1 << Fighter.SUKUNA) != 0);
    }

    private void jumpLand(Fighter f, boolean wasGround, boolean landEv) {
        if (!wasGround && f.onGround && f.downTicks > 0 && !landEv) { // 被打倒、落得慢（没有 LAND 事件）：照样一声倒地
            play(SfxBank.DOWN_THUD, f.x);
            return;
        }
        if (wasGround && !f.onGround && f.vy > 0 && f.hitstun == 0 && !f.knocked() && !f.scripted && !f.captured
                && (f.state == Fighter.FREE || f.state == Fighter.S_MELEE)) {
            play(SfxBank.JUMP, f.x);
        } else if (!wasGround && f.onGround && !landEv && f.hitstun == 0 && !f.scripted) {
            play(SfxBank.LAND_LIGHT, f.x);
        }
    }

    /** 循环音：每 tick 按状态决定开关（SfxOut 自己判断“已经在响”）。 */
    private void loops(World w) {
        // 苍的吸引（茈·最大输出的固定动作里：0–1 秒慢慢起来，接近时压低 5 dB，相撞时停）
        // 按固定动作的进度判断（不靠事件锁存：中途切后台再回来也能接上）
        if (w.cine == World.CINE_PURPLE_MAX && w.cineTick < Tuning.PMAX_CONTACT) {
            int t = w.cineTick;
            float gain = Math.min(1f, t / (float) Tuning.PMAX_RISE);
            if (t > Tuning.PMAX_CLOSE) gain *= 1f - 0.44f * Math.min(1f, (t - Tuning.PMAX_CLOSE) / 120f);
            out.loop(SLOT_FIELD, SfxBank.BLUE_LOOP_MAX, pan(w.blue.cx), Math.max(0.02f, gain));
        } else if (w.blue.phase == BlueOrb.PULL && w.cine == World.CINE_NONE) {
            out.loop(SLOT_FIELD, w.blue.charged ? SfxBank.BLUE_LOOP_MAX : SfxBank.BLUE_LOOP, pan(w.blue.cx), 1f);
        } else {
            out.stopLoop(SLOT_FIELD);
        }
        // 领域：对撞优先，其次无量空处困住宿傩
        if (w.clashTicks > 0) out.loop(SLOT_DOMAIN, SfxBank.CLASH_LOOP, 0, 1f);
        else if (w.voidControl()) out.loop(SLOT_DOMAIN, SfxBank.VOID_LOOP, 0, 1f);
        else out.stopLoop(SLOT_DOMAIN);
        // 宿傩身上：反转术式优先，其次领域展延
        Fighter s = w.sukuna;
        if (rctOn && s.state == Fighter.S_RCT && s.hpMilli > 0) out.loop(SLOT_SUKUNA, SfxBank.RCT_LOOP, pan(s.x), 1f);
        else if (w.ampTicks > 0 && s.hpMilli > 0) out.loop(SLOT_SUKUNA, SfxBank.AMP_LOOP, pan(s.x), 1f);
        else out.stopLoop(SLOT_SUKUNA);
        if (s.state != Fighter.S_RCT) rctOn = false;
    }

    /* ---------------- 出声 ---------------- */

    private void stopG() {
        out.stop(gHold);
        out.stop(gChant);
        gHold = gChant = 0;
    }

    private void stopS() {
        out.stop(sLong);
        sLong = 0;
    }

    private static float pan(float worldX) {
        float p = worldX / Tuning.WIDTH * 2 - 1;
        return Math.max(-0.5f, Math.min(0.5f, p));
    }

    private int nextRandom() {
        int x = rng;
        x ^= x << 13;
        x ^= x >>> 17;
        x ^= x << 5;
        rng = x;
        return x & 0x7FFFFFFF;
    }

    private int play(int cue, float worldX) {
        return play(cue, -1, 1f, worldX);
    }

    /** variant −1：随机选；worldX 按位置给声像（NaN 居中）。 */
    private int play(int cue, int variant, float gain, float worldX) {
        if (now - last[cue] < MIN_TICKS[cue]) return 0;
        last[cue] = now;
        int n = SfxBank.RES[cue].length;
        int v = variant >= 0 ? variant % n : n > 1 ? nextRandom() % n : 0;
        float j = JITTER[cue];
        float rate = j > 0 ? 1 + j * (nextRandom() / (float) Integer.MAX_VALUE * 2 - 1) : 1f;
        float g = gain * (j > 0 ? 0.9f + 0.1f * (nextRandom() / (float) Integer.MAX_VALUE) : 1f);
        return out.play(cue, v, Float.isNaN(worldX) ? 0 : pan(worldX), g, rate, PRIORITY[cue]);
    }

    /** 居中播放（领域、结算这种全屏的）。 */
    private int play(int cue, int variant, float gain, int centered) {
        return play(cue, variant, gain, Float.NaN);
    }

    /** 指定变体（咒词第几段，不随机）。 */
    private int playV(int cue, int variant, float worldX, float gain) {
        return play(cue, variant, gain, worldX);
    }
}
