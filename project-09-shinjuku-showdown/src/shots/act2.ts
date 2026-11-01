// 第二幕「激突」：第 33–48 小节（57.12 – 84.39 s）。200% 茈命中、反转术式长回双臂、街头近身战、黑闪、领域展开前的对峙。
import {B, H, W, clamp, ease, hash, kf, lerp, shakeAt} from '../engine/core';
import {Stage} from '../engine/stage';
import {POSE, P, Pose, RIG, breathe, poseAt, runPose} from '../engine/rig';
import * as fx from '../engine/fx';
import {Shot, seq} from './types';
import {actor, ghosts, gojoEyes, gradeWinter, street, streetEye, sukunaEyes, winterAir, winterNear, GOJO_C, SUKUNA_C} from './kit';
import {cutIn} from './cutin';

const F = 1 / 60;

/** 灰尘色（RGB 字符串给 fx.dust）。 */
const DUST = '150,152,160';
const DUSTW = '200,205,215';

/** 冲击帧：白底 + 黑剪影（调用方随后用 sil 画人物）。 */
const impactWhite = (st: Stage) => st.fill('#f4f2ff', 1);

/** 动作戏的基础调色：比第一幕稍暖、对比更高。 */
const gradeFight = (st: Stage) => {
  gradeWinter(st, 0.7);
  st.post.bloom = 0.45;
};

const shots = seq(
  [
    // 19 ★ 200% 茈命中：第一帧就是贯通的光束和受击轮廓
    {
      id: '19-impact', at: B(33),
      draw: (st, t, T) => {
        const z = kf(t, [[0.1, 1.3], [1.4, 2.0, ease.io3]]);
        const sh = shakeAt(t, 0, 26, 1.2, 3);
        st.camera(kf(t, [[0.1, 120], [1.4, 300, ease.io3]]), -110, z, sh);
        const beamA = kf(t, [[0, 1], [0.55, 1], [1.1, 0, ease.in2]]);
        const beamW = kf(t, [[0, 140], [0.08, 110], [0.6, 90], [1.1, 30]]);
        if (t < 2 * F) {
          // 冲击帧：紫底、白色光束、黑色人影——第一眼就是“茈”
          st.fill('#4a12a8', 1);
          st.line(-2000, -32, 3000, -32, beamW * 0.95, '#ffffff', 1, 'source-over', 'butt');
          actor(st, {rig: RIG.sukuna, pose: POSE.hurtBig, x: 330, y: 0, face: -1, shadow: 0, opts: {sil: '#000'}});
          st.flush();
          st.post.letterbox = kf(t, [[0, 1], [0.01, 1]]);
          st.post.bloom = 0;
          st.post.vignette = 0;
          return;
        }
        street(st, {base: [200, -110]});
        // 光束扫过的地方被照成紫色
        st.fill('#7a3cff', beamA * 0.35, 'soft-light');
        winterAir(st, T, {fogA: 0.15, snow: 0.6});
        // 宿傩：被光束吞没，往右后方被推走
        const sx = kf(t, [[0, 330], [0.6, 420, ease.out3]]);
        const sp = kf(t, [[0, 0], [0.6, 28]]);
        actor(st, {rig: RIG.sukuna, pose: {...POSE.hurtBig, spin: sp}, x: sx, y: -kf(t, [[0, 0], [0.3, 14], [0.6, 0]]), face: -1, opts: {hurt: beamA, noArmF: t > 0.2, noArmB: t > 0.2}});
        st.flush();
        fx.beam(st, -2000, -32, 3000, -32, beamW, T, {a: beamA});
        fx.debris(st, 330, -20, t, 0.05, {n: 40, speed: 380, dir: -0.3, spread: 1.6, grav: 380, ground: 0, size: 3, seed: 4});
        fx.dust(st, 380, -10, t, 0.2, {n: 16, r: 40, spread: 160, rise: 30, life: 2.6, color: DUST, a: 0.75, dir: -0.2});
        fx.shock(st, 330, -30, t, 0.02, 0.6, 260, {color: '#e0b7ff', width: 10, flat: 0.5});
        fx.speedLines(st, st.sx(330), st.sy(-30), t, {n: 60, a: 0.35 * beamA, color: '#e8d4ff'});
        winterNear(st, T, {snow: 0.6});
        gradeFight(st);
        st.post.chroma = kf(t, [[0, 10], [0.4, 0]]);
        st.post.bloom = 0.8;
        // 遮幅在命中瞬间打开到全幅
        st.post.letterbox = kf(t, [[0, 1], [0.35, 0, ease.out3]]);
      },
      cues: [[0, 'purple_hit', 0], [0.02, 'subdrop', -2], [0.35, 'collapse', -6]],
    },
    // 20 烟里：反转术式把手臂长回来
    {
      id: '20-regrow', at: B(34),
      draw: (st, t, T) => {
        const z = kf(t, [[0, 2.2], [B(35) - B(34), 3, ease.io2]]);
        st.camera(kf(t, [[0, 330], [1.7, 390]]), -40, z);
        streetEye(st, {blur: 4, ax: 360});
        winterAir(st, T, {fogA: 0.3, snow: 0.6});
        const grow = clamp((T - B(34, 3)) / (B(35) - B(34, 3)));
        const pose = poseAt(T, [[B(34), P({armF: 40, armB: 30, lean: 6, legF: 10, legB: -10})], [B(35), P({armF: 20, armB: 10, lean: 0, legF: 8, legB: -8})]]);
        const info = actor(st, {rig: RIG.sukuna, pose, x: 420, y: 0, face: -1, opts: {noArmF: grow <= 0, noArmB: grow <= 0}});
        if (grow > 0 && grow < 1) {
          // 新长出来的手臂：先是绿白色，慢慢变回原色
          st.figure(RIG.sukuna, pose, 420, 0, {face: -1, tint: '#d8ffe6', tintA: (1 - grow) * 0.9, alpha: 1});
        }
        st.flush();
        fx.rct(st, info.chest[0], info.chest[1], 16, T, kf(T, [[B(34, 2), 0], [B(34, 3), 1], [B(35), 0.3]]));
        fx.dust(st, 420, -10, t, -1.2, {n: 18, r: 46, spread: 140, rise: 20, life: 4, color: DUST, a: 0.85, seed: 5});
        sukunaEyes(st, info, 0.8, 1.4);
        winterNear(st, T);
        gradeFight(st);
      },
      cues: [[0, 'rct', -8]],
    },
    // 21 宿傩站定；左边烟被五条的蓝色残影切开
    {
      id: '21-ready', at: B(35),
      draw: (st, t, T) => {
        const z = 4;
        st.camera(420 - 260 / z, -32, z);
        streetEye(st, {blur: 6, ax: 360});
        winterAir(st, T, {fogA: 0.3, snow: 0.6});
        const info = actor(st, {rig: RIG.sukuna, pose: breathe(T, P({armF: 4, armB: -4, legF: 8, legB: -8})), x: 420, y: 0, face: -1});
        st.flush();
        fx.dust(st, 330, -20, t, -2.4, {n: 14, r: 40, spread: 120, rise: 14, life: 5, color: DUST, a: 0.8, seed: 9});
        // 残影掠过
        const k = clamp((T - B(35, 3)) / 0.18);
        if (k > 0 && k < 1) {
          const gx = lerp(250, 380, k);
          ghosts(st, RIG.gojo, POSE.dodgeF, [[gx, 0], [gx - 14, 0], [gx - 28, 0], [gx - 42, 0]], 1, GOJO_C, 0.7);
          st.flush();
          fx.streaks(st, T, {n: 30, a: 0.4, color: '#bfe4ff', y0: H * 0.4, y1: H * 0.8});
        }
        sukunaEyes(st, info, 1, 1.4);
        winterNear(st, T);
        gradeFight(st);
      },
      cues: [[B(35, 3) - B(35), 'dash', -6]],
    },
    // 22 五条冲进来，苍拳打在胸口
    {
      id: '22-blue-fist', at: B(36),
      draw: (st, t, T) => {
        const hit = B(36, 3);
        const z = 2.2;
        st.camera(kf(T, [[B(36), 250], [B(37), 330]]), -60, z, shakeAt(T, hit, 14, 0.35, 5));
        streetEye(st, {blur: 2, ax: 300, s: 0.3});
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        const gx = kf(T, [[B(36), 150], [hit - 0.05, 335, ease.in2], [hit + 0.12, 335], [B(37), 320]]);
        const gp = poseAt(T, [[B(36), POSE.dodgeF], [hit - 0.1, POSE.punchWind], [hit - 0.02, POSE.dashPunch, ease.out3], [hit + 0.15, POSE.dashPunch], [B(37), POSE.jab]]);
        const sx = kf(T, [[hit, 372], [hit + 0.12, 372], [hit + 0.4, 420, ease.out3]]);
        const sp = poseAt(T, [[B(36), POSE.stand], [hit, POSE.stand], [hit + 0.02, POSE.hurt], [B(37), {...POSE.hurt, spin: 8}]]);
        if (T < hit) ghosts(st, RIG.gojo, gp, [[gx - 16, 0], [gx - 32, 0], [gx - 48, 0]], 1, GOJO_C, 0.45);
        actor(st, {rig: RIG.sukuna, pose: sp, x: sx, y: 0, face: -1, opts: {hurt: kf(T, [[hit, 1], [hit + 0.1, 0]]) * (T >= hit ? 1 : 0)}});
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: 0, face: 1});
        st.flush();
        fx.blue(st, gi.hand[0] + 2, gi.hand[1], 2.2, T, {a: kf(T, [[B(36, 2), 0], [hit - 0.05, 1], [hit + 0.3, 0]])});
        fx.shock(st, 368, -30, T, hit, 0.4, 60, {color: '#9fd0ff', width: 3});
        fx.sparks(st, 368, -30, T, hit, {n: 24, speed: 300, color: '#cfe9ff', color2: '#5ab8f2', dir: 0, spread: 2.4, seed: 12});
        st.glow(368, -30, 40, 'rgba(220,240,255,0.95)', kf(T, [[hit, 1], [hit + 0.1, 0]]) * (T >= hit ? 1 : 0));
        gojoEyes(st, gi, 0.7);
        winterNear(st, T);
        gradeFight(st);
      },
      cues: [[0, 'dash', -6], [B(36, 3) - B(36), 'punch_heavy', -2]],
    },
    // 23 宿傩一挥手：一道“解”从五条头顶掠过，五条压低身子躲开；后面的东西被切开错位
    {
      id: '23-dismantle', at: B(37),
      draw: (st, t, T) => {
        const cut = B(37) + 0.12, duck = B(37, 3);
        const z = 2.2;
        st.camera(250, -60, z);
        streetEye(st, {blur: 2, ax: 300});
        // 背景被切：斩线经过的高度，上半部往右错开
        const sl = clamp((T - cut) / 0.25);
        if (sl > 0) {
          const y = st.sy(-52);
          st.slice(0, y, W, y - 40, -14 * ease.out3(sl), 3 * ease.out3(sl), '#000', 0.9);
        }
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        const sp = poseAt(T, [[B(37), POSE.stand], [cut - 0.06, POSE.slashWind], [cut, POSE.slash, ease.out3], [B(38), POSE.stand]]);
        actor(st, {rig: RIG.sukuna, pose: sp, x: 360, y: 0, face: -1});
        const gp = poseAt(T, [[B(37), POSE.stand], [cut - 0.04, P({lean: 35, bob: -3, armF: 20, armB: -40, legF: 30, legB: -30})], [duck + 0.25, P({lean: 30, bob: -3, armF: 20, armB: -40, legF: 30, legB: -30})], [B(38), POSE.stand]]);
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: 230, y: 0, face: 1});
        st.flush();
        fx.slashLine(st, -400, -50, 900, -54, T, cut, {w: 1.5, life: 0.35});
        fx.slashLine(st, -400, -58, 900, -64, T, cut + 0.03, {w: 0.8, life: 0.25});
        gojoEyes(st, gi, 0.6);
        winterNear(st, T);
        gradeFight(st);
      },
      cues: [[0.1, 'slash', -4], [B(37, 3) - B(37), 'swish', -10]],
    },
    // 24 五条滑步，后手横扫；宿傩双臂挡住
    {
      id: '24-block', at: B(38),
      draw: (st, t, T) => {
        const hit = B(38, 3);
        const z = kf(T, [[B(38), 3], [B(39), 4]]);
        st.camera(kf(T, [[B(38), 268], [B(39), 290]]), -36, z, shakeAt(T, hit, 10, 0.3, 7));
        streetEye(st, {blur: 4, ax: 290});
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        const gx = kf(T, [[B(38), 240], [B(38, 2), 268, ease.out3], [B(39), 272]]);
        const gp = poseAt(T, [[B(38), P({legF: 35, legB: -25, lean: 12, armF: 30, armB: -40})], [hit - 0.08, P({legF: 30, legB: -25, lean: 6, armF: -20, armB: -60})], [hit, P({legF: 30, legB: -25, lean: 18, armF: -30, armB: 92})], [B(39), P({legF: 20, legB: -15, lean: 4, armF: 0, armB: 40})]]);
        const sp = poseAt(T, [[B(38), POSE.stand], [hit - 0.1, POSE.guard], [hit + 0.05, {...POSE.guard, lean: -10}], [B(39), POSE.guard]]);
        actor(st, {rig: RIG.sukuna, pose: sp, x: kf(T, [[hit, 300], [hit + 0.2, 308]]), y: 0, face: -1});
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: 0, face: 1});
        st.flush();
        fx.shock(st, 290, -30, T, hit, 0.35, 34, {color: '#9fd0ff', width: 2});
        fx.sparks(st, 290, -30, T, hit, {n: 16, speed: 200, color: '#ffffff', color2: '#9fd0ff', seed: 3});
        gojoEyes(st, gi, 0.7, 1.6);
        winterNear(st, T);
        gradeFight(st);
      },
      cues: [[0, 'step', -10], [B(38, 3) - B(38), 'block', -4]],
    },
    // 25 远景：两人一起往右，宿傩被打进楼里，楼往下塌
    {
      id: '25-building', at: B(39),
      draw: (st, t, T) => {
        const hit = B(39, 3);
        const z = 2.0;
        st.camera(kf(T, [[B(39), 230], [hit, 330], [B(40), 420]]), -100, z, shakeAt(T, hit, 18, 0.8, 9));
        street(st, {base: [220, -170]});
        winterAir(st, T, {fogA: 0.25, snow: 0.6});
        const gx = kf(T, [[B(39), 200], [hit, 360, ease.io2], [B(40), 380]]);
        const sx = kf(T, [[B(39), 260], [hit, 420, ease.io2], [hit + 0.25, 560, ease.out3]]);
        const sy = kf(T, [[hit, 0], [hit + 0.15, -30], [hit + 0.25, -60]]);
        actor(st, {rig: RIG.sukuna, pose: {...POSE.hurtBig, spin: kf(T, [[hit, 0], [hit + 0.25, 40]])}, x: sx, y: sy, face: -1, opts: {alpha: T > hit + 0.27 ? 0 : 1}});
        actor(st, {rig: RIG.gojo, pose: T < hit ? runPose(gx * 2) : POSE.dashPunch, x: gx, y: 0, face: 1});
        st.flush();
        fx.dust(st, 560, -60, T, hit + 0.2, {n: 22, r: 50, spread: 200, rise: 70, life: 3, color: DUST, a: 0.85, seed: 13});
        fx.debris(st, 560, -60, T, hit + 0.22, {n: 40, speed: 260, dir: -1.2, spread: 2.6, grav: 420, ground: 0, size: 4, seed: 14});
        winterNear(st, T);
        gradeFight(st);
      },
      cues: [[0, 'run', -10], [B(39, 3) - B(39), 'punch_heavy', -3], [B(39, 3) - B(39) + 0.22, 'collapse', -3]],
    },
    // 26 从烟里两道“解”，逼五条往左退；前景的柱子被切开
    {
      id: '26-two-slashes', at: B(40),
      draw: (st, t, T) => {
        const s1 = B(40, 3), s2 = B(40, 3) + 0.14;
        const z = 2.2;
        st.camera(kf(T, [[B(40), 420], [B(41), 300]]), -60, z, shakeAt(T, s1, 8, 0.3, 2));
        streetEye(st, {blur: 3, ax: 330});
        const c1 = clamp((T - s1 - 0.03) / 0.3), c2 = clamp((T - s2 - 0.03) / 0.3);
        if (c1 > 0) st.slice(st.sx(470), st.sy(-40), st.sx(120), st.sy(-10), 10 * ease.out3(c1), 2 * ease.out3(c1), '#05060a', 0.9);
        if (c2 > 0) st.slice(st.sx(470), st.sy(-20), st.sx(120), st.sy(-62), -12 * ease.out3(c2), 2 * ease.out3(c2), '#05060a', 0.9);
        winterAir(st, T, {fogA: 0.3, snow: 0.6});
        const sp = poseAt(T, [[B(40), POSE.stand], [B(40, 2), POSE.slashWind], [s1, POSE.slash, ease.out3], [s2 - 0.04, POSE.slashWind], [s2, POSE.slash, ease.out3]]);
        actor(st, {rig: RIG.sukuna, pose: sp, x: 470, y: 0, face: -1});
        const gx = kf(T, [[s1, 290], [s2 + 0.2, 230, ease.out3]]);
        const gp = poseAt(T, [[B(40), POSE.stand], [s1, POSE.dodgeB], [B(41), {...POSE.stand, spin: 0}]]);
        const gi = actor(st, {rig: RIG.gojo, pose: {...gp, spin: kf(T, [[s1, 0], [s1 + 0.15, -6], [B(41), 0]])}, x: gx, y: 0, face: 1});
        st.flush();
        fx.dust(st, 470, -20, t, -1, {n: 14, r: 40, spread: 120, rise: 10, life: 4, color: DUST, a: 0.75, seed: 15});
        fx.slashLine(st, 470, -40, 120, -10, T, s1, {w: 1.6});
        fx.slashLine(st, 470, -20, 120, -62, T, s2, {w: 1.4});
        gojoEyes(st, gi, 0.7);
        winterNear(st, T);
        gradeFight(st);
      },
      cues: [[B(40, 3) - B(40), 'slash', -3], [B(40, 3) - B(40) + 0.14, 'slash', -5]],
    },
    // 27 ★ 完整的近身长镜：压迫、闪避、牵引、反击
    {
      id: '27-long-take', at: B(41),
      draw: (st, t, T) => {
        const a1 = B(41), a2 = B(41, 3), a3 = B(42), a4 = B(42, 3);
        const z = 2.3;
        st.camera(kf(T, [[a1, 260], [a4, 290]]), -58, z, shakeAt(T, a4, 16, 0.45, 11));
        streetEye(st, {blur: 2, ax: 280});
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        // 宿傩：41.1 往前压，42.1 被苍拉住脚，42.3 挨打
        const sx = kf(T, [[a1, 380], [a2, 320, ease.out3], [a3, 305], [a3 + 0.3, 296, ease.in2], [a4, 296], [a4 + 0.35, 380, ease.out3]]);
        const sp = poseAt(T, [[a1, POSE.jabWind], [a1 + 0.2, POSE.jab, ease.out3], [a2, POSE.slashWind], [a2 + 0.1, POSE.slash], [a3, P({lean: 18, armF: 60, armB: 40, legF: -20, legB: 30})], [a4, P({lean: 10, armF: 40, armB: 20, legF: 10, legB: -10})], [a4 + 0.02, POSE.hurtBig], [B(43), POSE.hurt]]);
        // 五条：后退 → 侧滑 → 举手放苍 → 出拳
        const gx = kf(T, [[a1, 290], [a2, 220, ease.out3], [a2 + 0.25, 250, ease.io2], [a3, 252], [a4 - 0.08, 262], [a4, 278, ease.in2], [B(43), 270]]);
        const gp = poseAt(T, [[a1, POSE.dodgeB], [a2, POSE.dodgeB], [a2 + 0.25, POSE.dodgeF], [a3, POSE.blueHold], [a4 - 0.1, POSE.punchWind], [a4, POSE.dashPunch, ease.out3], [a4 + 0.2, POSE.dashPunch], [B(43), POSE.jab]]);
        if (T > a2 && T < a2 + 0.25) ghosts(st, RIG.gojo, gp, [[gx - 12, 0], [gx - 24, 0]], 1, GOJO_C, 0.4);
        actor(st, {rig: RIG.sukuna, pose: sp, x: sx, y: 0, face: -1, opts: {hurt: T >= a4 ? kf(T, [[a4, 1], [a4 + 0.12, 0]]) : 0}});
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: 0, face: 1});
        // 苍把碎石拉过来
        const pull = clamp((T - a3) / 0.5);
        if (pull > 0 && T < a4 + 0.2) {
          for (let i = 0; i < 18; i++) {
            const h1 = hash(i, 41), h2 = hash(i, 42);
            const k = clamp(pull * (0.6 + h2 * 0.6));
            const x0 = 200 + h1 * 200, y0 = -2 - h2 * 6;
            st.pixRect(lerp(x0, 300, k * 0.8), lerp(y0, -20, k * 0.8), 2, 2, h1 > 0.5 ? '#5b6170' : '#8a909c');
          }
        }
        st.flush();
        const bA = kf(T, [[a3, 0], [a3 + 0.1, 1], [a4 + 0.2, 1], [a4 + 0.5, 0]]);
        fx.blue(st, 300, -12, 3.2, T, {a: bA, pull: 1.6});
        st.line(gi.hand[0], gi.hand[1], 300, -12, 0.8, 'rgba(120,190,255,0.6)', bA * 0.6);
        fx.shock(st, 296, -30, T, a4, 0.45, 90, {color: '#cfe9ff', width: 4});
        fx.sparks(st, 296, -30, T, a4, {n: 30, speed: 360, color: '#ffffff', color2: '#9fd0ff', seed: 21});
        fx.slashLine(st, 360, -40, 180, -46, T, a2 + 0.08, {w: 1.2});
        st.glow(296, -30, 46, 'rgba(220,240,255,0.95)', T >= a4 ? kf(T, [[a4, 1], [a4 + 0.12, 0]]) : 0);
        gojoEyes(st, gi, 0.8);
        winterNear(st, T);
        gradeFight(st);
      },
      cues: [[0, 'jab', -8], [B(41, 3) - B(41), 'slash', -6], [B(42) - B(41), 'blue_pull', -6], [B(42, 3) - B(41), 'punch_heavy', 0]],
    },
    // 28 对峙：五条——你才是挑战者
    {
      id: '28-challenger', at: B(43),
      draw: (st, t, T) => {
        if (T < B(44)) {
          const z = kf(T, [[B(43), 3], [B(44), 3.4]]);
          st.camera(320, -40, z);
          streetEye(st, {blur: 5, ax: 320});
          winterAir(st, T, {fogA: 0.3, snow: 0.6});
          actor(st, {rig: RIG.sukuna, pose: breathe(T, P({armF: 4, armB: -4, legF: 10, legB: -10})), x: 420, y: 0, face: -1});
          const gi = actor(st, {rig: RIG.gojo, pose: breathe(T, P({armF: 6, armB: -6, legF: 6, legB: -6})), x: 250, y: 0, face: 1});
          st.flush();
          gojoEyes(st, gi, 1);
          winterNear(st, T);
          gradeFight(st);
        } else {
          cutIn(st, 'gojo', T - B(44), B(45) - B(44), {eyes: 1, s: 0.95});
          winterNear(st, T, {snow: 1.2});
          st.post.bloom = 0.4;
        }
        st.text({kind: 'quote', jp: '勘違いしてるみたいだから言っとくけど', cn: '你好像搞错了什么，我先把话说清楚——', age: T - B(43, 1.5), dur: B(44) - B(43, 1.5), color: GOJO_C, size: 40});
        st.text({kind: 'quote', jp: 'そっちが挑戦者だから', cn: '你才是挑战者。', age: T - B(44), dur: B(45) - B(44) - 0.05, color: GOJO_C, size: 52});
      },
      cues: [[B(44) - B(43), 'whoosh', -8]],
    },
    // 29 黑闪
    {
      id: '29-black-flash', at: B(45),
      draw: (st, t, T) => {
        const hit = B(46);
        const z = kf(T, [[B(45), 2.2], [hit, 3.6, ease.in2], [B(47), 4]]);
        st.camera(kf(T, [[B(45), 240], [hit, 300]]), -40, z, shakeAt(T, hit, 30, 0.6, 13));
        streetEye(st, {blur: 3, ax: 300});
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        const gx = kf(T, [[B(45), 200], [hit - 0.04, 288, ease.in2], [B(47), 290]]);
        const gp = poseAt(T, [[B(45), P({lean: 30, legF: 30, legB: -30, armF: -20, armB: 40})], [hit - 0.12, POSE.punchWind], [hit - 0.02, POSE.dashPunch, ease.out3], [hit + 0.4, POSE.dashPunch], [B(46, 3), POSE.stand]]);
        const sx = kf(T, [[hit, 330], [hit + 0.5, 400, ease.out3]]);
        const sp = poseAt(T, [[B(45), POSE.stand], [B(45, 3), POSE.guard], [hit, POSE.guard], [hit + 0.02, {...POSE.hurtBig, spin: 15}], [B(47), {...POSE.hurt, spin: 15}]]);
        const flash = (T - hit) / 0.5;
        if (flash >= 0 && flash < 0.07) {
          // 黑闪冲击帧：红底黑影
          st.fill('#2a0006', 1);
          actor(st, {rig: RIG.sukuna, pose: sp, x: sx, y: 0, face: -1, shadow: 0, opts: {sil: '#000'}});
          actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: 0, face: 1, shadow: 0, opts: {sil: '#000'}});
          st.flush();
          fx.blackFlash(st, 318, -32, 120, 0.15, 7);
          st.post.bloom = 0.2;
          st.post.vignette = 0;
        } else {
          actor(st, {rig: RIG.sukuna, pose: sp, x: sx, y: 0, face: -1, opts: {hurt: T > hit ? kf(T, [[hit, 1], [hit + 0.15, 0]]) : 0}});
          const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: 0, face: 1});
          st.flush();
          fx.blackFlash(st, 318, -32, 120, flash, 7);
          gojoEyes(st, gi, 0.8);
          winterNear(st, T);
          gradeFight(st);
        }
        if (T >= hit) st.text({kind: 'tech', jp: '黒閃', cn: '黑闪', age: T - hit, dur: B(46, 4) - hit, x: 0.5, y: 0.22, size: 170, color: '#f23b4b'});
      },
      cues: [[0, 'dash', -6], [B(46) - B(45), 'black_flash', 0]],
    },
    // 30 拉开：两人向两边滑退
    {
      id: '30-apart', at: B(47),
      draw: (st, t, T) => {
        const z = kf(T, [[B(47), 2.6], [B(47) + 0.3, 1.9, ease.out3]]);
        st.camera(305, -90, z);
        street(st, {base: [300, -150]});
        winterAir(st, T, {fogA: 0.25, snow: 0.6});
        const gx = kf(T, [[B(47), 290], [B(47, 3), 270], [B(48), 140, ease.out3]]);
        const sx = kf(T, [[B(47), 400], [B(47, 3), 430], [B(48), 470, ease.out3]]);
        actor(st, {rig: RIG.gojo, pose: T > B(47, 3) ? POSE.crouch : POSE.stand, x: gx, y: 0, face: 1});
        actor(st, {rig: RIG.sukuna, pose: T > B(47, 3) ? POSE.crouch : POSE.hurt, x: sx, y: 0, face: -1});
        st.flush();
        // 拖痕
        const k = clamp((T - B(47, 3)) / 0.4);
        if (k > 0) {
          st.line(270, 1, gx, 1, 1.4, 'rgba(20,20,26,0.8)', 1, 'source-over');
          st.line(430, 1, sx, 1, 1.4, 'rgba(20,20,26,0.8)', 1, 'source-over');
          fx.dust(st, gx, -2, T, B(47, 3), {n: 8, r: 14, spread: 30, rise: 6, life: 1.4, color: DUSTW, a: 0.5, seed: 31});
          fx.dust(st, sx, -2, T, B(47, 3), {n: 8, r: 14, spread: 30, rise: 6, life: 1.4, color: DUSTW, a: 0.5, seed: 32});
        }
        winterNear(st, T);
        gradeFight(st);
      },
      cues: [[B(47, 3) - B(47), 'slide', -8]],
    },
    // 31 对称：两人同时结印——领域展开
    {
      id: '31-ryouiki', at: B(48),
      draw: (st, t, T) => {
        const z = 2.4;
        st.camera(305, -40, z);
        streetEye(st, {blur: 3, ax: 305});
        const split = kf(T, [[B(48, 2), 0], [B(48, 3), 1]]);
        winterAir(st, T, {fogA: 0.2, snow: 0.6, freeze: T > B(48, 3) ? B(48, 3) : undefined});
        const sign = poseAt(T, [[B(48), POSE.stand], [B(48, 2.6), POSE.stand], [B(48, 3), {...POSE.sign, legF: 8, legB: -8}, ease.out3]]);
        const gi = actor(st, {rig: RIG.gojo, pose: sign, x: 205, y: 0, face: 1});
        const si = actor(st, {rig: RIG.sukuna, pose: sign, x: 405, y: 0, face: -1});
        st.flush();
        // 左蓝右红
        const g = st.g;
        g.save();
        g.globalCompositeOperation = 'soft-light';
        g.globalAlpha = 0.45 * split;
        g.fillStyle = '#2050ff';
        g.fillRect(0, 0, W / 2, H);
        g.fillStyle = '#ff2030';
        g.fillRect(W / 2, 0, W / 2, H);
        g.restore();
        const ea = kf(T, [[B(48, 4) - 0.05, 0], [B(48, 4), 1]]);
        gojoEyes(st, gi, ea, 2);
        sukunaEyes(st, si, ea, 2);
        st.glow(gi.hand[0], gi.hand[1], 10, 'rgba(90,184,242,0.8)', split);
        st.glow(si.hand[0], si.hand[1], 10, 'rgba(224,48,63,0.8)', split);
        winterNear(st, T, {freeze: T > B(48, 3) ? B(48, 3) : undefined});
        gradeFight(st);
        st.text({kind: 'tech', jp: '領域展開', cn: '领域展开', age: T - B(48, 3), dur: B(49, 3) - B(48, 3), x: 0.5, y: 0.2, size: 140, color: '#ffffff'});
      },
      cues: [[B(48, 3) - B(48), 'sign', -4], [B(48, 4) - B(48), 'eyes', -10]],
    },
  ],
  B(49),
);

export default shots as Shot[];
