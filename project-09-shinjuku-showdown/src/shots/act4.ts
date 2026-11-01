// 第四幕「适应」：第 65–82 小节（111.66 – 142.34 s）。领域烧断、魔虚罗参战、车轮一格格转、黑闪、
// 退魔之剑斩穿无下限、顎吐加入三打一、强化苍吞掉顎吐并留在战场、术式反转「赫」。
import {B, H, W, clamp, ease, hash, kf, lerp, shakeAt} from '../engine/core';
import {Stage} from '../engine/stage';
import {POSE, P, Pose, RIG, breathe, poseAt, runPose} from '../engine/rig';
import * as fx from '../engine/fx';
import {Shot, seq} from './types';
import {actor, ghosts, gojoEyes, gradeWinter, street, streetEye, sukunaEyes, winterAir, winterNear, BLUE_P, GOJO_C, SUKUNA_C} from './kit';
import {cutIn} from './cutin';

const F = 1 / 60;
const DUST = '150,152,160';

/** 第四幕调色：更冷、更暗，像天色开始变晚。 */
const gradeAdapt = (st: Stage, k = 1) => {
  gradeWinter(st, 0.9);
  st.post.tint = '#6f86b0';
  st.post.tintA = 0.26 * k;
  st.post.vignette = 0.5;
  st.post.desat = 0.22;
};

/** 魔虚罗。 */
const maho = (st: Stage, pose: Pose, x: number, y = 0, o: {hurt?: number; alpha?: number} = {}) =>
  actor(st, {rig: RIG.maho, pose, x, y, face: -1, opts: {hurt: o.hurt, alpha: o.alpha}});

/** 魔虚罗头后的车轮位置。 */
const wheelAt = (x: number, y = 0): [number, number] => [x + 1, y - 76];

/** 无下限：五条身前一道淡蓝的弧（straight 0..1：被适应以后弧线被拉直）。 */
const infinity = (st: Stage, x: number, y: number, face: number, a: number, straight = 0, T = 0) => {
  if (a <= 0.01) return;
  for (let i = 0; i < 4; i++) {
    const r = 22 + i * 5 + Math.sin(T * 6 + i) * 0.8;
    const bend = lerp(0.9, 0.12, straight);
    st.ring(x - face * (r - 16), y, r / Math.max(0.2, bend * 1.1), 0.8, i % 2 ? '#9fd0ff' : '#cfe9ff', a * (0.5 - i * 0.08), 'lighter', face > 0 ? -bend : Math.PI - bend, face > 0 ? bend : Math.PI + bend);
  }
};

/** 车轮转一格（在 at 时刻用 0.12 秒转 45°）。 */
const turn = (T: number, at: number) => kf(T, [[at, 0], [at + 0.12, 45, ease.outBack]]);

const shots = seq(
  [
    // 45 领域烧断：两人头上的反转术式光一闪就灭；宿傩想再开领域，红环开了一半碎掉
    {
      id: '45-burnout', at: B(65),
      draw: (st, t, T) => {
        const z = 2.1;
        st.camera(330, -60, z);
        streetEye(st, {ax: 330});
        winterAir(st, T, {fogA: 0.25, snow: 0.7});
        const gp = breathe(T, P({lean: 25, bob: -2, armF: 30, armB: -10, legF: 16, legB: -16}));
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: 220, y: 0, face: 1});
        maho(st, breathe(T * 0.7, P({armF: 50, armB: -10, legF: 8, legB: -8})), 480);
        const sp = poseAt(T, [[B(65), POSE.stand], [B(66), POSE.stand], [B(66) + 0.2, P({armF: 90, armB: 20, legF: 10, legB: -10})], [B(66, 3), P({armF: 90, armB: 20, legF: 10, legB: -10})], [B(67), P({armF: 20, armB: -4, legF: 16, legB: -16, lean: 6})]]);
        const si = actor(st, {rig: RIG.sukuna, pose: sp, x: 400, y: 0, face: -1});
        st.flush();
        const [wx, wy] = wheelAt(480);
        fx.wheel(st, wx, wy, 19, 270, {a: 1, lit: 0.2});
        // 头顶的绿白光闪了两下灭掉
        const fl = (T > B(65, 1.5) && T < B(65, 2)) || (T > B(65, 3) && T < B(65, 3.3)) ? 0.8 : 0;
        st.glow(gi.head[0], gi.head[1], 14, 'rgba(160,255,200,0.6)', fl);
        st.glow(si.head[0], si.head[1], 14, 'rgba(160,255,200,0.6)', fl);
        // 宿傩的红环：开到一半就碎
        const k = clamp((T - B(66) - 0.2) / 0.5);
        if (k > 0 && T < B(66, 3)) st.ring(400, -30, 38, 2, '#ff6a6a', 0.9, 'lighter', -Math.PI / 2, -Math.PI / 2 + k * Math.PI * 1.2);
        fx.shards(st, 400, -30, T, B(66, 3), {n: 18, speed: 160, size: 5, color: 'rgba(255,90,90,0.8)', seed: 45});
        gojoEyes(st, gi, 0.7);
        sukunaEyes(st, si, 0.8);
        winterNear(st, T);
        gradeAdapt(st);
        st.text({kind: 'quote', jp: 'あとは貴様を切り刻みながら その無限にも適応させてもらう', cn: '接下来，就一边把你切碎，一边让它适应你的无下限。', age: T - B(65, 1.5), dur: B(67) - B(65, 1.5), color: SUKUNA_C, size: 38});
      },
      cues: [[0.4, 'fizzle', -8], [B(66, 3) - B(65), 'glass', -6]],
    },
    // 46 宿傩插镜：边切碎你，边适应无下限；再见了，最强
    {
      id: '46-sukuna-cutin', at: B(67),
      draw: (st, t, T) => {
        cutIn(st, 'sukuna', t, B(69) - B(67), {eyes: 1, s: 0.95});
        winterNear(st, T, {snow: 0.8});
        st.post.bloom = 0.4;
        st.post.vignette = 0.4;
        st.text({kind: 'quote', jp: 'じゃあな最強 俺がいない時代に生まれただけの凡夫', cn: '再见了，最强。你不过是生在没有我的时代的凡夫。', age: T - B(67) - 0.15, dur: B(69) - B(67) - 0.2, color: SUKUNA_C, size: 42});
      },
      cues: [[0, 'whoosh', -8], [0.1, 'pulse_red', -10]],
    },
    // 47 苍挂在手上：吸来碎石逼魔虚罗举剑，绕到左侧一拳打在胸口
    {
      id: '47-maho-punch', at: B(69),
      draw: (st, t, T) => {
        const hit = B(70, 3);
        const z = 2.2;
        st.camera(kf(T, [[B(69), 280], [B(71), 340]]), -70, z, shakeAt(T, hit, 18, 0.4, 3));
        streetEye(st, {ax: 300});
        winterAir(st, T, {fogA: 0.2, snow: 0.7});
        const mx = kf(T, [[hit, 400], [hit + 0.4, 412, ease.out3]]);
        const mp = poseAt(T, [[B(69), P({armF: 50, armB: -10})], [B(69, 3), P({armF: 140, armB: 30, lean: -6})], [hit, P({armF: 140, armB: 30, lean: -6})], [hit + 0.05, P({armF: 120, armB: 60, lean: -14})], [B(71), P({armF: 90, armB: 20})]]);
        maho(st, mp, mx, 0, {hurt: T > hit ? kf(T, [[hit, 1], [hit + 0.12, 0]]) : 0});
        const gx = kf(T, [[B(69), 230], [B(70), 300, ease.io2], [hit - 0.05, 372, ease.in2], [B(71), 370]]);
        const gp = poseAt(T, [[B(69), POSE.blueHold], [B(70), runPose(T * 300)], [hit - 0.1, POSE.punchWind], [hit, POSE.dashPunch, ease.out3], [B(71), POSE.dashPunch]]);
        if (T > B(70) && T < hit) ghosts(st, RIG.gojo, gp, [[gx - 14, 0], [gx - 28, 0]], 1, GOJO_C, 0.35);
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: 0, face: 1});
        // 被吸过去的碎石
        const pull = clamp((T - B(69)) / 1.2);
        for (let i = 0; i < 16; i++) {
          const h1 = hash(i, 47), h2 = hash(i, 48);
          const k = clamp(pull * (0.5 + h2));
          if (k >= 1) continue;
          st.pixRect(lerp(250 + h1 * 260, gi.hand[0], k), lerp(-h2 * 10, gi.hand[1], k), 2, 2, h1 > 0.5 ? '#5b6170' : '#8a909c');
        }
        st.flush();
        const [wx, wy] = wheelAt(mx);
        fx.wheel(st, wx, wy, 19, 270, {a: 1, lit: 0.15});
        fx.blue(st, gi.hand[0], gi.hand[1], 2.4, T, {a: T < hit + 0.3 ? 1 : 0, pull: 1.4});
        fx.shock(st, mx - 6, -50, T, hit, 0.4, 70, {color: '#cfe9ff', width: 3});
        fx.sparks(st, mx - 6, -50, T, hit, {n: 24, speed: 300, color: '#ffffff', color2: '#9fd0ff', seed: 47});
        gojoEyes(st, gi, 0.9);
        winterNear(st, T);
        gradeAdapt(st);
      },
      cues: [[0, 'blue_pull', -8], [B(70) - B(69), 'dash', -8], [B(70, 3) - B(69), 'punch_heavy', -2]],
    },
    // 48 车轮特写：转一格
    {
      id: '48-wheel-close', at: B(71),
      draw: (st, t, T) => {
        const z = 6;
        const [wx, wy] = wheelAt(420);
        st.camera(wx, wy + 10, z);
        streetEye(st, {ax: 420, blur: 9});
        st.fill('#05070c', 0.35);
        maho(st, P({armF: 90, armB: 20}), 420);
        st.flush();
        fx.wheel(st, wx, wy, 19, 270 + turn(T, B(71)), {a: 1, lit: kf(T, [[B(71), 1], [B(71, 3), 0.25]])});
        // 背景里的蓝线被切成一段一段
        const cut = clamp((T - B(71, 3)) / 0.2);
        for (let i = 0; i < 6; i++) {
          const y = wy - 30 + i * 12;
          const seg = 1 - cut * 0.6;
          for (let k = 0; k < 6; k++) {
            const x0 = wx - 60 + k * 22;
            st.line(x0, y, x0 + 22 * seg, y, 0.4, '#9fd0ff', 0.35, 'lighter');
          }
        }
        winterNear(st, T, {snow: 0.6});
        gradeAdapt(st);
      },
      cues: [[0, 'wheel', 0]],
    },
    // 49 魔虚罗横剑，五条跃起越过剑线，空中一拳——黑闪
    {
      id: '49-maho-blackflash', at: B(72),
      draw: (st, t, T) => {
        const swing = B(72, 3), hit = B(73);
        const z = kf(T, [[B(72), 2.2], [hit, 3.2, ease.in2], [B(74), 3]]);
        st.camera(kf(T, [[B(72), 330], [hit, 380]]), -70, z, shakeAt(T, hit, 28, 0.5, 9));
        const flash = (T - hit) / 0.5;
        const mp = poseAt(T, [[B(72), P({armF: 90, armB: 20})], [swing - 0.08, P({armF: 150, armB: 30, lean: -8})], [swing + 0.04, P({armF: 60, armB: -10, lean: 16}), ease.out5], [hit, P({armF: 50, armB: -10, lean: 10})], [hit + 0.02, P({armF: 110, armB: 90, lean: -18})], [B(74), P({armF: 70, armB: 10, lean: -4})]]);
        const gx = kf(T, [[B(72), 250], [swing, 330, ease.io2], [hit, 384], [B(74), 380]]);
        const gy = -kf(T, [[swing - 0.1, 0], [swing + 0.1, 26, ease.out3], [hit, 30], [hit + 0.4, 0, ease.in2]]);
        const gp = poseAt(T, [[B(72), POSE.dodgeF], [swing - 0.1, POSE.air], [swing + 0.1, {...POSE.air, spin: -10}], [hit - 0.08, {...POSE.punchWind, spin: -10}], [hit, {...POSE.dashPunch, spin: -6}, ease.out3], [hit + 0.4, POSE.air], [B(74), POSE.stand]]);
        const mx = kf(T, [[hit, 420], [hit + 0.4, 440, ease.out3]]);
        if (flash >= 0 && flash < 0.07) {
          st.fill('#2a0006', 1);
          maho(st, mp, mx);
          actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: gy, face: 1, shadow: 0, opts: {sil: '#000'}});
          st.flush();
          fx.blackFlash(st, 405, -60, 140, 0.15, 11);
          st.post.bloom = 0.2;
          st.post.vignette = 0;
        } else {
          streetEye(st, {ax: 360});
          winterAir(st, T, {fogA: 0.2, snow: 0.7});
          maho(st, mp, mx, 0, {hurt: T > hit ? kf(T, [[hit, 1], [hit + 0.15, 0]]) : 0});
          const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: gy, face: 1});
          st.flush();
          const [wx, wy] = wheelAt(mx);
          fx.wheel(st, wx, wy, 19, 315, {a: 1, lit: 0.15});
          fx.slashLine(st, 470, -40, 200, -36, T, swing, {w: 2.4, life: 0.3});
          fx.blackFlash(st, 405, -60, 140, flash, 11);
          gojoEyes(st, gi, 1);
          winterNear(st, T);
          gradeAdapt(st);
        }
        if (T >= hit) st.text({kind: 'tech', jp: '黒閃', cn: '黑闪', age: T - hit, dur: B(73, 4) - hit, x: 0.5, y: 0.2, size: 170, color: '#f23b4b'});
      },
      cues: [[B(72, 3) - B(72), 'sword', -4], [B(73) - B(72), 'black_flash', 0]],
    },
    // 50 车轮再转一格；魔虚罗举剑，五条举臂挡住，剑尖前的蓝弧慢慢被拉直
    {
      id: '50-straighten', at: B(74),
      draw: (st, t, T) => {
        const z = kf(T, [[B(74), 2.2], [B(76), 2.6]]);
        st.camera(330, -80, z);
        streetEye(st, {ax: 330});
        winterAir(st, T, {fogA: 0.25, snow: 0.7});
        const mp = poseAt(T, [[B(74), P({armF: 70, armB: 10})], [B(75), P({armF: 150, armB: 30, lean: -6})], [B(76), P({armF: 150, armB: 30, lean: -6})]]);
        maho(st, mp, 410);
        actor(st, {rig: RIG.sukuna, pose: breathe(T, POSE.stand), x: 520, y: 0, face: -1});
        const gi = actor(st, {rig: RIG.gojo, pose: poseAt(T, [[B(74), POSE.stand], [B(75), P({armF: 90, armB: 70, lean: -4, legF: 16, legB: -16})]]), x: 300, y: 0, face: 1});
        st.flush();
        const [wx, wy] = wheelAt(410);
        fx.wheel(st, wx, wy, 19, 315 + turn(T, B(74)), {a: 1, lit: kf(T, [[B(74), 1], [B(74, 3), 0.3]])});
        infinity(st, 312, -30, 1, 0.9, kf(T, [[B(75), 0], [B(76), 1]]), T);
        if (T > B(75)) st.glow(372, -78, 6, 'rgba(201,210,216,0.9)', 0.6 + 0.3 * Math.sin(T * 20));
        gojoEyes(st, gi, 1);
        winterNear(st, T);
        gradeAdapt(st);
      },
      cues: [[0, 'wheel', -2], [B(75) - B(74), 'sword_hum', -8]],
    },
    // 51 ★ 退魔之剑斩穿无下限：一条白线直接穿过蓝弧
    {
      id: '51-cut-infinity', at: B(76),
      draw: (st, t, T) => {
        const cut = B(76) + 0.05;
        const z = 3;
        st.camera(340, -50, z, shakeAt(T, cut, 22, 0.5, 13));
        streetEye(st, {ax: 340, blur: 3});
        winterAir(st, T, {fogA: 0.25, snow: 0.7});
        const mp = poseAt(T, [[B(76), P({armF: 150, armB: 30, lean: -6})], [cut, P({armF: 80, armB: -10, lean: 16}), ease.out5], [B(77), P({armF: 70, armB: -10, lean: 8})]]);
        maho(st, mp, 405);
        const gx = kf(T, [[cut, 300], [cut + 0.4, 262, ease.out3]]);
        const lost = T > B(76, 3);
        const gi = actor(st, {rig: RIG.gojo, pose: {...POSE.hurt, spin: kf(T, [[cut, 0], [cut + 0.3, -12]])}, x: gx, y: 0, face: 1, opts: {noArmB: lost, hurt: kf(T, [[cut, 1], [cut + 0.15, 0]])}});
        st.flush();
        infinity(st, 312, -30, 1, kf(T, [[cut, 1], [cut + 0.3, 0]]), 1, T);
        fx.slashLine(st, 380, -95, 250, 10, T, cut, {w: 2.6, life: 0.5});
        st.glow(312, -30, 60, 'rgba(230,240,255,0.95)', T >= cut ? kf(T, [[cut, 1], [cut + 0.12, 0]]) : 0);
        fx.dust(st, gi.chest[0] - 6, gi.chest[1], T, B(76, 3) - 0.05, {n: 10, r: 14, spread: 20, rise: 6, life: 1.4, color: DUST, a: 0.9, seed: 51});
        gojoEyes(st, gi, 0.8);
        winterNear(st, T);
        gradeAdapt(st);
        st.post.chroma = kf(T, [[cut, 8], [cut + 0.3, 0]]);
      },
      cues: [[0.05, 'sword_cut', 0], [0.06, 'glass', -6]],
    },
    // 52 嵌合兽「顎吐」从右上落地
    {
      id: '52-agito', at: B(77),
      draw: (st, t, T) => {
        const land = B(77) + 0.18;
        const z = kf(T, [[B(77), 1.5], [B(78), 2, ease.io2]]);
        st.camera(360, -110, z, shakeAt(T, land, 16, 0.5, 4));
        street(st, {base: [340, -110]});
        winterAir(st, T, {fogA: 0.25, snow: 0.7});
        const ay = kf(T, [[B(77), -260], [land, 0, ease.in2]]);
        const ax = kf(T, [[B(77), 560], [land, 470]]);
        actor(st, {rig: RIG.sukuna, pose: breathe(T, POSE.stand), x: 540, y: 0, face: -1});
        maho(st, P({armF: 70, armB: -10}), 400);
        const gx = kf(T, [[B(77, 3), 270], [B(78), 240, ease.out3]]);
        const gi = actor(st, {rig: RIG.gojo, pose: T > B(77, 3) ? POSE.dodgeB : POSE.hurt, x: gx, y: 0, face: 1, opts: {noArmB: true}});
        fx.shadow(st, ax, 0, 30, 0.5);
        st.pixSprite('agito.body', ax, ay, {face: -1, scale: 2, py: 18});
        st.flush();
        const [wx, wy] = wheelAt(400);
        fx.wheel(st, wx, wy, 19, 0, {a: 1, lit: 0.15});
        fx.dust(st, ax, -4, T, land, {n: 14, r: 26, spread: 90, rise: 10, life: 1.8, color: DUST, a: 0.8, seed: 52});
        fx.shock(st, ax, 0, T, land, 0.5, 120, {color: '#ffffff', width: 2, flat: 0.25});
        gojoEyes(st, gi, 0.8);
        winterNear(st, T);
        gradeAdapt(st);
        st.text({kind: 'tech', jp: '嵌合獣「顎吐」', cn: '嵌合兽·顎吐', age: T - land, dur: B(78) - land, x: 0.75, y: 0.22, size: 100, color: '#6fe0ff'});
      },
      cues: [[0.18, 'land_heavy', -2]],
    },
    // 53 三路夹击：剑、顎吐下压、远处的“解”，各占一拍；五条往上逃
    {
      id: '53-three', at: B(78),
      draw: (st, t, T) => {
        const s1 = B(78), s2 = B(78, 2), s3 = B(78, 3), esc = B(78, 4);
        const z = 2.3;
        st.camera(380, -80, z);
        street(st, {base: [340, -110]});
        winterAir(st, T, {fogA: 0.25, snow: 0.7});
        actor(st, {rig: RIG.sukuna, pose: poseAt(T, [[s3 - 0.1, POSE.slashWind], [s3, POSE.slash, ease.out3]]), x: 540, y: 0, face: -1});
        maho(st, poseAt(T, [[s1 - 0.05, P({armF: 150, armB: 30})], [s1 + 0.05, P({armF: 60, armB: -10, lean: 16}), ease.out5], [B(79), P({armF: 70, armB: 0})]]), 390);
        const ay = -kf(T, [[s2 - 0.2, 0], [s2 - 0.05, 30, ease.out3], [s2 + 0.05, 0, ease.in3]]);
        st.pixSprite('agito.body', kf(T, [[s2 - 0.2, 470], [s2, 330]]), ay, {face: -1, scale: 2, py: 18});
        const gy = -kf(T, [[esc - 0.05, 0], [B(79), 90, ease.out3]]);
        const gx = kf(T, [[s1, 300], [s2, 280], [esc, 300]]);
        const gp = T < esc ? (T > s1 ? POSE.dodgeB : POSE.stand) : POSE.air;
        if (T > esc) ghosts(st, RIG.gojo, gp, [[gx, gy + 20], [gx, gy + 40], [gx, gy + 60]], 1, GOJO_C, 0.5);
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: gy, face: 1, opts: {noArmB: true}});
        st.flush();
        fx.wheel(st, ...wheelAt(390), 19, 0, {a: 1, lit: 0.15});
        fx.slashLine(st, 440, -40, 230, -50, T, s1, {w: 2.2});
        fx.dust(st, 330, -4, T, s2 + 0.05, {n: 14, r: 24, spread: 80, rise: 10, life: 1.4, color: DUST, a: 0.8, seed: 53});
        fx.slashLine(st, 540, -40, 200, -110, T, s3, {w: 1.4});
        gojoEyes(st, gi, 0.8);
        winterNear(st, T);
        gradeAdapt(st);
      },
      cues: [[0, 'sword', -3], [B(78, 2) - B(78), 'land_heavy', -4], [B(78, 3) - B(78), 'slash', -4], [B(78, 4) - B(78), 'jump', -6]],
    },
    // 54 强化苍：吞掉顎吐，苍留在战场上；五条的手臂长回来
    {
      id: '54-blue-max', at: B(79),
      draw: (st, t, T) => {
        const suck = B(80), gone = B(80, 3);
        const z = kf(T, [[B(79), 2.6], [B(81), 2.0, ease.io2]]);
        st.camera(kf(T, [[B(79), 300], [B(81), 330]]), kf(T, [[B(79), -150], [B(81), -120]]), z);
        street(st, {base: [300, -140]});
        winterAir(st, T, {fogA: 0.25, snow: 0.7});
        const [BX, BY] = BLUE_P;
        const ak = clamp((T - suck) / (gone - suck));
        if (ak < 1) {
          st.pixSprite('agito.body', lerp(360, BX, ease.in2(ak)), lerp(0, BY + 10, ease.in2(ak)), {face: -1, scale: 2 * (1 - ak * 0.8), py: 18, deg: ak * 200, alpha: 1 - ak * 0.5});
        }
        maho(st, P({armF: 70, armB: 0, lean: -10}), 420);
        actor(st, {rig: RIG.sukuna, pose: P({armF: 40, armB: 30, lean: -8}), x: 540, y: 0, face: -1});
        const regrow = clamp((T - B(80, 3)) / 0.5);
        const gy = -kf(T, [[B(80, 3), 90], [B(81) - 0.05, 0, ease.in2]]);
        const gpose = T < B(80, 3) ? P({armF: 120, armB: 40, legF: 20, legB: -10}) : poseAt(T, [[B(80, 3), P({armF: 120, armB: 40, legF: 20, legB: -10})], [B(81) - 0.05, POSE.crouch]]);
        const gi = actor(st, {rig: RIG.gojo, pose: gpose, x: 200, y: gy, face: 1, opts: {noArmB: regrow <= 0}});
        if (regrow > 0 && regrow < 1) st.figure(RIG.gojo, gpose, 200, gy, {face: 1, tint: '#d8ffe6', tintA: 1 - regrow});
        // 碎屑被吸过去
        for (let i = 0; i < 40; i++) {
          const h1 = hash(i, 54), h2 = hash(i, 55);
          const ph = (T * (0.4 + h2 * 0.5) + h1) % 1;
          const ang = h1 * 6.28 + ph * 3;
          const R = (1 - ph) * 160;
          st.pixRect(BX + Math.cos(ang) * R, BY + Math.sin(ang) * R * 0.8, 2, 2, h2 > 0.5 ? '#8a909c' : '#5b6170', clamp((T - B(79)) * 2));
        }
        st.flush();
        fx.blue(st, BX, BY, kf(T, [[B(79), 2], [B(80), 6, ease.out3]]), T, {pull: 2, debris: 40});
        fx.dust(st, 200, -2, T, B(81) - 0.05, {n: 8, r: 14, spread: 30, rise: 6, life: 1.2, color: '200,205,215', a: 0.6, seed: 54});
        st.line(gi.hand[0], gi.hand[1], BX, BY, 0.6, 'rgba(120,190,255,0.5)', 0.6);
        fx.rct(st, gi.chest[0] - 6, gi.chest[1], 10, T, kf(T, [[B(80, 3), 0], [B(80, 3) + 0.1, 1], [B(81), 0]]));
        fx.wheel(st, ...wheelAt(420), 19, 0, {a: 1, lit: 0.15});
        gojoEyes(st, gi, 1);
        winterNear(st, T);
        gradeAdapt(st);
        st.text({kind: 'chant', jp: '出力最大', cn: '“苍”·出力最大', age: T - B(79), dur: B(80, 3) - B(79), x: 0.86, y: 0.12, size: 120, color: '#9fd0ff'});
      },
      cues: [[0, 'charge', -4], [B(80) - B(79), 'blue_pull', 0], [B(80, 3) - B(79), 'rct', -6]],
    },
    // 55 断层：所有人慢到四分之一，苍挂在左上，五条抬头
    {
      id: '55-slow', at: B(81),
      draw: (st, t, T) => {
        const slow = B(81) + (T - B(81)) * 0.25;
        const z = 1.7;
        st.camera(360, -130, z);
        street(st, {base: [330, -130]});
        winterAir(st, slow, {fogA: 0.25, snow: 0.7});
        maho(st, P({armF: 70, armB: 0, lean: -6}), 420);
        actor(st, {rig: RIG.sukuna, pose: P({armF: 30, armB: 20, lean: -4}), x: 540, y: 0, face: -1});
        const gi = actor(st, {rig: RIG.gojo, pose: {...POSE.stand, spin: -3}, x: 200, y: 0, face: 1});
        st.flush();
        fx.blue(st, ...BLUE_P, 6, slow, {pull: 0.5, debris: 30});
        fx.wheel(st, ...wheelAt(420), 19, 0, {a: 1, lit: 0.15});
        gojoEyes(st, gi, 1);
        winterNear(st, slow);
        gradeAdapt(st);
        st.post.desat = 0.35;
      },
      cues: [[0, 'blue_hum', -8]],
    },
    // 56 術式反転：后手举起，掌中一点红
    {
      id: '56-reverse', at: B(82),
      draw: (st, t, T) => {
        const z = 4;
        st.camera(250, -60, z);
        streetEye(st, {ax: 250, blur: 6});
        st.glowScreen(W * 0.1, 0, 900, 'rgba(60,120,255,0.5)', 0.6);
        winterAir(st, T, {fogA: 0, snow: 0.6});
        const gp = poseAt(T, [[B(82), POSE.stand], [B(82, 2.5), P({armB: 160, armF: 20, lean: -6, legF: 14, legB: -12})]]);
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: 220, y: 0, face: 1});
        st.flush();
        const rk = kf(T, [[B(82, 3), 0], [B(82, 4), 1, ease.out3]]);
        fx.red(st, gi.backHand[0], gi.backHand[1] - 3, 0.6 + rk * 1.4, T, {a: clamp((T - B(82, 3)) * 8)});
        gojoEyes(st, gi, 1, 1.6);
        winterNear(st, T);
        gradeAdapt(st, 0.6);
        st.text({kind: 'chant', jp: '術式反転', cn: '术式反转', age: T - B(82, 3), dur: B(83, 2) - B(82, 3), x: 0.82, y: 0.12, size: 120, color: '#ff9a8a'});
      },
      cues: [[B(82, 3) - B(82), 'red_charge', -4]],
    },
  ],
  B(83),
);

export default shots as Shot[];
