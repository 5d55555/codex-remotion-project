// 第一幕「序」：0 – 57.12 s（弱起 + 第 0–32 小节）。空城、等待、帐、增幅、咏唱、200% 茈蓄满；第 32 小节断层是吸气。
import {B, H, W, clamp, ease, hash, kf, lerp, noise1} from '../engine/core';
import {Stage} from '../engine/stage';
import {POSE, P, RIG, breathe, mixPose, poseAt} from '../engine/rig';
import * as fx from '../engine/fx';
import {Shot, seq} from './types';
import {actor, gojoEyes, gradeWinter, rooftop, street, streetEye, sukunaEyes, winterAir, winterNear, BLUE} from './kit';
import {cutIn} from './cutin';

const SUK_X = 330; // 宿傩在街上的位置

/** 街上的宿傩（第一幕里一直站着等）。 */
const sukunaStanding = (st: Stage, t: number, o: {alpha?: number; eyes?: number; arm?: number; lean?: number; spin?: number; sil?: string; silA?: number; tint?: string; tintA?: number} = {}) => {
  const pose = breathe(t, mixPose(POSE.stand, P({armF: o.arm ?? 4, armB: -4, lean: o.lean ?? 0, legF: 8, legB: -8, spin: o.spin ?? 0}), 1));
  const info = actor(st, {rig: RIG.sukuna, pose, x: SUK_X, y: 0, face: -1, opts: {alpha: o.alpha, sil: o.sil, silA: o.silA, tint: o.tint, tintA: o.tintA}});
  return info;
};

const gojoRoof = (st: Stage, pose = POSE.stand, o: {bob?: number; tint?: string; tintA?: number} = {}) =>
  actor(st, {rig: RIG.gojo, pose: {...pose, bob: pose.bob + (o.bob ?? 0)}, x: 0, y: 0, face: 1, shadow: 0, opts: {tint: o.tint, tintA: o.tintA}});

/** 咏唱阶段五条的手：前手 / 后手的姿势随小节变化（第 17–30 小节）。 */
const chantPose = (T: number) =>
  poseAt(T, [
    [B(15), P({armF: 6, armB: -6, legF: 8, legB: -8})],
    [B(16), P({armF: 35, armB: 35, legF: 8, legB: -8})],
    [B(17), P({armF: 90, armB: 35, legF: 8, legB: -8})],
    [B(18), P({armF: 90, armB: 75, legF: 8, legB: -8})],
    [B(19), P({armF: 90, armB: 75, legF: 6, legB: -6})],
    [B(20), P({armF: 110, armB: 75, lean: 5, legF: 6, legB: -6})],
    [B(25), P({armF: 110, armB: 75, lean: 5, legF: 3, legB: -3})],
    [B(26), P({armF: 110, armB: 110, lean: 5, legF: 3, legB: -3})],
    [B(30), P({armF: 110, armB: 110, lean: 5, legF: 3, legB: -3})],
    [B(30, 3), P({armF: 92, armB: 90, lean: 8, legF: 10, legB: -10}, ), ease.out3],
  ]);

/** 两只手掌之间的那一点：苍红合流的位置。 */
const palm = (info: {hand: [number, number]; backHand: [number, number]}): [number, number] => [
  (info.hand[0] + info.backHand[0]) / 2 + 4,
  (info.hand[1] + info.backHand[1]) / 2,
];

/** 第 19–32 小节：手里的术式随咏唱长大。返回要画的东西的参数。 */
const orbState = (T: number) => {
  const blueR = kf(T, [[B(19), 0], [B(20), 2.2], [B(21), 3]]);
  const redA = kf(T, [[B(21), 0], [B(22), 1]]);
  const merge = kf(T, [[B(25), 0], [B(27), 1]]); // 红蓝收拢
  const purple = kf(T, [[B(27), 0], [B(28), 1]]);
  const swell = kf(T, [[B(28), 1], [B(31), 1.8], [B(32), 2.6]]);
  return {blueR, redA, merge, purple, swell};
};

/** 在 (X,Y) 画咏唱中的术式（苍、赫绕着共同中心转，然后合成紫核）。 */
const drawOrbs = (st: Stage, X: number, Y: number, T: number, scale = 1) => {
  const o = orbState(T);
  const t = T;
  if (o.purple < 1) {
    const rot = (T - B(23)) * 1.6;
    const sep = (1 - o.merge) * 7 * scale;
    const bx = X - Math.cos(rot) * sep * (o.redA > 0 ? 1 : 0), by = Y - Math.sin(rot) * sep * 0.5 * (o.redA > 0 ? 1 : 0);
    const rx = X + Math.cos(rot) * sep, ry = Y + Math.sin(rot) * sep * 0.5;
    fx.blue(st, bx, by, o.blueR * scale * 0.6, t, {a: clamp(o.blueR) * (1 - o.purple)});
    if (o.redA > 0) fx.red(st, rx, ry, 1.6 * scale, t, {a: o.redA * (1 - o.purple)});
  }
  if (o.purple > 0) fx.purple(st, X, Y, 2.2 * scale * o.swell, t, {a: o.purple, seam: clamp(1 - (T - B(27.5)) / 2)});
};

const shots = seq(
  [
    // 01 空城：缓慢右摇，雪，宿傩在远处从雾里出现
    {
      id: '01-empty-street', at: 0,
      draw: (st, t, T) => {
        st.zoomBoost = 1;
        const z = 1.25;
        st.camera(lerp(-260, -150, T / B(3)), -175, z);
        street(st, {base: [-200, -175]});
        winterAir(st, T, {fogA: 0.3});
        const a = kf(T, [[B(1), 0], [B(2), 1]]);
        if (a > 0) sukunaStanding(st, T, {alpha: a, sil: '#1a1d26', silA: 0.55 * (1 - kf(T, [[B(2), 0], [B(3), 0.6]]))});
        st.flush();
        winterNear(st, T);
        gradeWinter(st);
        st.post.letterbox = 1;
        st.post.fade = '#000';
        st.post.fadeA = kf(T, [[0, 1], [B(0), 0.3], [B(0.5), 0]]);
        st.text({kind: 'card', jp: '東京 · 新宿', cn: '2018 年 12 月 24 日', age: T - B(0), dur: B(2.6) - B(0)});
      },
      cues: [[0, 'wind', -8], [B(0), 'swell', -14]],
    },
    // 02 沿街推进，宿傩在右边等
    {
      id: '02-push-sukuna', at: B(3),
      draw: (st, t, T) => {
        const k = ease.io2(clamp((T - B(3)) / (B(7) - B(3))));
        const z = lerp(1.3, 2.3, k);
        st.camera(lerp(60, 230, k), lerp(-150, -60, k), z);
        street(st, {base: [100, -120]});
        winterAir(st, T, {fogA: 0.24});
        sukunaStanding(st, T, {lean: kf(T, [[B(5), 5], [B(6), 0]])});
        st.flush();
        winterNear(st, T);
        gradeWinter(st);
        st.post.letterbox = 1;
      },
    },
    // 03 宿傩头肩特写：红眼亮
    {
      id: '03-sukuna-eyes', at: B(7),
      draw: (st, t, T) => {
        const z = kf(T, [[B(7), 8], [B(9), 11, ease.io2]]);
        st.camera(SUK_X - 200 / z, -42, z);
        streetEye(st, {blur: 8, ax: SUK_X - 200 / z});
        winterAir(st, T, {snow: 0.7, fogA: 0.2});
        const info = sukunaStanding(st, T, {arm: kf(T, [[B(8), 4], [B(8.5), 25]])});
        st.flush();
        const ea = kf(T, [[B(7), 0], [B(7.3), 1, ease.out3]]);
        sukunaEyes(st, info, ea, 1.4);
        winterNear(st, T, {snow: 1.4});
        gradeWinter(st);
        st.post.letterbox = 1;
      },
      cues: [[0, 'pulse', -10]],
    },
    // 04 楼顶：从远处城市横摇到五条
    {
      id: '04-roof-reveal', at: B(9),
      draw: (st, t, T) => {
        const k = ease.io3(clamp((T - B(9)) / (B(11) - B(9))));
        const z = 1.6;
        st.camera(lerp(280, 60, k), -150, z);
        rooftop(st);
        winterAir(st, T, {fogA: 0.12, fogY: H * 0.85});
        gojoRoof(st, breathe(T, POSE.stand));
        st.flush();
        winterNear(st, T);
        gradeWinter(st);
        st.post.letterbox = 1;
      },
      cues: [[0, 'wind', -10]],
    },
    // 05 立绘插镜：まあ勝つさ
    {
      id: '05-gojo-cutin', at: B(11),
      draw: (st, t, T) => {
        cutIn(st, 'gojo', t, B(13) - B(11), {eyes: kf(t, [[0.15, 0], [0.5, 1]]), s: 0.8, y: 0.56});
        winterNear(st, T, {snow: 1.2});
        st.post.letterbox = 1;
        st.post.bloom = 0.4;
        st.text({kind: 'quote', jp: 'まあ勝つさ', cn: '总之，我会赢的。', age: T - B(11.25), dur: B(12.9) - B(11.25), color: '#5ab8f2'});
      },
      cues: [[0, 'whoosh', -8], [0.1, 'pulse', -12]],
    },
    // 06 俯瞰：帐从右往左罩下来
    {
      id: '06-curtain', at: B(13),
      draw: (st, t, T) => {
        st.zoomBoost = 1;
        const k = clamp((T - B(13)) / (B(15) - B(13)));
        st.camera(lerp(60, -40, k), 0, 1);
        st.plate('aerial', {s: 1.0, d: 1});
        const ck = ease.io2(clamp((T - B(13.2)) / (B(14.6) - B(13.2))));
        fx.curtain(st, lerp(W * 0.95, W * 0.62, ck), ck, {a: 0.6, w: 1900});
        winterAir(st, T, {fogA: 0, snow: 0.8});
        gradeWinter(st);
        st.post.letterbox = 1;
        st.text({kind: 'tech', jp: '帳', cn: '伊地知布下的“帐”：遮住咒力，掩护先手', age: T - B(13.4), dur: B(15) - B(13.4), x: 0.22, y: 0.42, size: 150, color: '#b9a8ff'});
      },
      cues: [[B(13.2) - B(13), 'curtain', -6]],
    },
    // 07 增幅阵纹在脚下亮起
    {
      id: '07-amplify', at: B(15),
      draw: (st, t, T) => {
        const z = 2.2;
        st.camera(40, -70, z);
        rooftop(st, {d: 0.85});
        winterAir(st, T, {fogA: 0});
        const r1 = kf(T, [[B(15), 0], [B(15.4), 1, ease.out3]]), r2 = kf(T, [[B(16), 0], [B(16.4), 1, ease.out3]]);
        fx.sigil(st, 0, 0, 40, T, {a: r1});
        fx.sigil(st, 0, 0, 62, T, {a: r2 * 0.8, rot: -0.3});
        const info = gojoRoof(st, chantPose(T));
        st.flush();
        // 四向光柱
        const pil = Math.max(r1, r2);
        for (const dx of [-36, 36]) st.line(dx, 10, dx, -260, 10, 'rgba(140,190,255,0.35)', pil * 0.5);
        gojoEyes(st, info, 0.5);
        winterNear(st, T);
        gradeWinter(st);
        st.post.letterbox = 1;
        st.text({kind: 'sub', jp: '庵歌姫 · 術式増幅', cn: '歌姬的增幅术式', age: T - B(15.1), dur: B(17) - B(15.1), x: 0.72, y: 0.3});
      },
      cues: [[0, 'chime', -10], [B(16) - B(15), 'chime', -9]],
    },
    // 08 两手在胸前重叠
    {
      id: '08-hands', at: B(17),
      draw: (st, t, T) => {
        const z = kf(T, [[B(17), 3], [B(19), 4]]);
        st.camera(14, kf(T, [[B(17), -30], [B(19), -38]]), z);
        rooftop(st, {d: 0.7, blur: 4});
        winterAir(st, T, {fogA: 0, snow: 0.8});
        fx.sigil(st, 0, 0, 62, T, {a: 0.6, rot: -0.3});
        const info = gojoRoof(st, chantPose(T));
        st.flush();
        const [px, py] = palm(info);
        const sp = kf(T, [[B(18), 0], [B(18.3), 1]]);
        st.glow(px, py, 7, 'rgba(120,190,255,0.9)', sp);
        fx.crackle(st, px, py, 6, Math.floor(T * 20), '#cfe9ff', 4, sp * 0.6);
        gojoEyes(st, info, 0.8);
        winterNear(st, T);
        gradeWinter(st);
        st.post.letterbox = 1;
      },
      cues: [[0, 'charge', -12], [B(18) - B(17), 'charge', -11]],
    },
    // 09–12 咏唱四句：近景 / 特写 / 拉开 / 近景
    {
      id: '09-chant-1', at: B(19),
      draw: (st, t, T) => chantShot(st, T, {z: 5, cx: 64, cy: -34, side: 'right'}),
      cues: [[0, 'chant', -6], [0, 'bell', -12]],
    },
    {
      id: '10-chant-2', at: B(21),
      draw: (st, t, T) => chantShot(st, T, {z: 6.5, cx: kf(T, [[B(21), 30], [B(23), 42]]), cy: -30, side: 'right', orbFocus: true}),
      cues: [[0, 'chant', -6], [0.2, 'redin', -10]],
    },
    {
      id: '11-chant-3', at: B(23),
      draw: (st, t, T) => chantShot(st, T, {z: kf(T, [[B(23), 4], [B(25), 3]]), cx: kf(T, [[B(23), 80], [B(25), 107]]), cy: kf(T, [[B(23), -34], [B(25), -44]]), side: 'right', float: kf(T, [[B(23), 0], [B(24), 2]])}),
      cues: [[0, 'chant', -6], [0, 'whoosh', -14]],
    },
    {
      id: '12-chant-4', at: B(25),
      draw: (st, t, T) => chantShot(st, T, {z: 5, cx: kf(T, [[B(25), 70], [B(27), 60]]), cy: -36, side: 'right', float: 2}),
      cues: [[0, 'chant', -6], [0, 'bell', -12]],
    },
    // 13 楼顶远景斜推：红蓝接触，紫光照亮楼群
    {
      id: '13-two-hundred', at: B(27),
      draw: (st, t, T) => {
        const k = ease.io2(clamp((T - B(27)) / (B(29) - B(27))));
        const z = lerp(1.55, 2.4, k);
        st.camera(lerp(160, 40, k), lerp(-150, -60, k), z);
        rooftop(st);
        const lit = kf(T, [[B(27.5), 0], [B(28.5), 1]]);
        st.fill('#7a3cff', lit * 0.16, 'soft-light');
        winterAir(st, T, {fogA: 0});
        fx.sigil(st, 0, 0, 62, T, {a: 0.5, rot: -0.3});
        const info = gojoRoof(st, chantPose(T), {bob: 2, tint: '#a84dff', tintA: lit * 0.25});
        st.flush();
        const [px, py] = palm(info);
        drawOrbs(st, px, py, T);
        st.glow(px, py, 160, 'rgba(120,50,220,0.35)', lit);
        gojoEyes(st, info, 1);
        winterNear(st, T);
        gradeWinter(st, 1 - lit * 0.5);
        st.post.letterbox = 1;
        st.post.bloom = 0.55;
        st.text({kind: 'tech', jp: '出力 200%', cn: '两倍出力', age: T - B(27), dur: B(28.8) - B(27), x: 0.74, y: 0.3, size: 110, color: '#c79bff'});
      },
      cues: [[0, 'contact', -6], [0, 'riser', -10]],
    },
    // 14 宿傩：察觉，紫光照到身上
    {
      id: '14-sukuna-senses', at: B(29),
      draw: (st, t, T) => {
        const z = 6;
        st.camera(SUK_X - 300 / z, -34, z);
        streetEye(st, {blur: 7, ax: SUK_X - 300 / z});
        st.fill('#6a2cff', 0.14, 'soft-light');
        winterAir(st, T, {snow: 0.7, fogA: 0});
        const arm = kf(T, [[B(29.5), 4], [B(29.9), 70, ease.out3]]);
        const info = sukunaStanding(st, T, {arm, spin: -3, tint: '#a84dff', tintA: 0.18});
        st.flush();
        sukunaEyes(st, info, 1, 1.5);
        // 左边缘的紫色受光
        st.glowScreen(0, H * 0.5, 900, 'rgba(150,70,255,0.45)', 0.8);
        winterNear(st, T, {snow: 1.4});
        gradeWinter(st, 0.5);
        st.post.letterbox = 1;
      },
      cues: [[0, 'pulse', -9]],
    },
    // 15 五条：前倾，双手推平，紫球压缩
    {
      id: '15-kyoshiki', at: B(30),
      draw: (st, t, T) => {
        const z = kf(T, [[B(30), 4], [B(31), 5]]);
        st.camera(76, -36, z);
        rooftop(st, {d: 0.6, blur: 6});
        st.fill('#5a1cff', 0.2, 'soft-light');
        winterAir(st, T, {fogA: 0, snow: 0.8});
        const info = gojoRoof(st, chantPose(T), {bob: 2, tint: '#a84dff', tintA: 0.22});
        st.flush();
        const [px, py] = palm(info);
        const squeeze = kf(T, [[B(30.5), 1], [B(30.9), 0.75, ease.out3]]);
        fx.purple(st, px + 10, py, 3.6 * squeeze, T, {a: 1});
        fx.speedLines(st, st.sx(px + 10), st.sy(py), T, {n: 50, r0: 300, a: 0.25, color: '#e0b7ff', width: 4});
        gojoEyes(st, info, 1);
        winterNear(st, T);
        st.post.letterbox = 1;
        st.post.bloom = 0.6;
        st.text({kind: 'chant', jp: '虚式', age: T - B(30), dur: B(31) - B(30), x: 0.8, y: 0.16, size: 160, color: '#e0b7ff'});
      },
      cues: [[0, 'lock', -6]],
    },
    // 16 远景：全城被紫光照亮
    {
      id: '16-murasaki', at: B(31),
      draw: (st, t, T) => {
        const z = 1.6;
        st.camera(280, -170, z);
        rooftop(st);
        const k = kf(T, [[B(31), 0.4], [B(31.6), 1]]);
        st.fill('#6020ff', 0.3 * k, 'soft-light');
        st.fill('#b070ff', 0.12 * k, 'overlay');
        winterAir(st, T, {fogA: 0, snow: 0.8});
        const info = gojoRoof(st, chantPose(T), {bob: 2, tint: '#a84dff', tintA: 0.3});
        st.flush();
        const [px, py] = palm(info);
        fx.purple(st, px + 10, py, 3.4 * k, T, {a: 1});
        st.glow(px + 10, py, 420 * k, 'rgba(140,60,255,0.35)', 1);
        // 远处街口的一点红：宿傩所在
        st.glow(980, -10, 14, 'rgba(224,48,63,0.9)', 0.6 + 0.4 * Math.sin(T * 6));
        winterNear(st, T);
        st.post.letterbox = 1;
        st.post.bloom = 0.65;
        st.text({kind: 'chant', jp: '茈', cn: '“茈”', age: T - B(31), dur: B(32) - B(31), x: 0.86, y: 0.16, size: 220, color: '#d9b0ff'});
      },
      cues: [[0, 'riser', -6]],
    },
    // 17 紫核特写：骤缩成一点，外圈光反而扩大（断层开始）
    {
      id: '17-core', at: B(32),
      draw: (st, t, T) => {
        st.camera(0, 0, 6);
        st.fill('#120624', 1);
        const k = ease.in3(clamp((T - B(32)) / (B(32, 3) - B(32))));
        const r = lerp(30, 3, k);
        st.glow(0, 0, lerp(80, 220, k), 'rgba(170,90,255,0.6)', 1);
        fx.purple(st, 0, 0, r, T, {a: 1});
        st.disc(0, 0, r * 0.4 * k, '#05000a', k);
        st.fill('#f4ecff', k * 0.55);
        st.post.letterbox = 1;
        st.post.bloom = 0.7;
      },
      cues: [[0, 'suck', -6]],
    },
    // 18 屏住呼吸：一切冻结，乳白，只剩城市轮廓
    {
      id: '18-breath', at: B(32, 3),
      draw: (st, t, T) => {
        const z = 1.6;
        st.camera(280, -170, z);
        rooftop(st);
        st.fill('#6020ff', 0.3, 'soft-light');
        winterAir(st, T, {fogA: 0, snow: 0.8, freeze: B(32, 3)});
        const info = gojoRoof(st, chantPose(T), {bob: 2});
        st.flush();
        const [px, py] = palm(info);
        const edge = kf(T, [[B(32, 4), 0], [B(33) - 0.02, 1]]);
        fx.purple(st, px + 10, py, 3 + edge * 1.2, B(32, 3), {a: 1});
        winterNear(st, T, {freeze: B(32, 3)});
        st.post.letterbox = 1;
        st.post.fade = '#f3eefa';
        st.post.fadeA = 0.9 - edge * 0.12;
      },
    },
  ],
  B(33),
);

/** 咏唱镜头（第 19–27 小节）：五条近景，手里的术式随小节长大，竖排咏唱字。 */
function chantShot(st: Stage, T: number, o: {z: number; cx: number; cy: number; side: 'left' | 'right'; orbFocus?: boolean; float?: number}) {
  st.camera(o.cx, o.cy, o.z);
  rooftop(st, {d: 0.55, blur: 7});
  const k = clamp((T - B(19)) / (B(27) - B(19)));
  st.fill('#1f3cff', 0.1 + 0.12 * k, 'soft-light');
  winterAir(st, T, {fogA: 0, snow: 0.7});
  const info = gojoRoof(st, chantPose(T), {bob: o.float ?? 0});
  st.flush();
  const [px, py] = palm(info);
  drawOrbs(st, px + 2, py, T, 1);
  // 碎屑上浮（第 23 小节起）
  const up = clamp((T - B(23)) / 2);
  if (up > 0) {
    for (let i = 0; i < 24; i++) {
      const h1 = hash(i, 91), h2 = hash(i, 92);
      const ph = (T * (0.15 + h2 * 0.2) + h1) % 1;
      st.pixRect(-40 + h1 * 90, 4 - ph * 70, 1, 1, h2 > 0.5 ? '#c9d6ea' : '#8090a8', up * Math.sin(ph * Math.PI));
    }
    st.flush();
  }
  gojoEyes(st, info, 1, 1.6);
  winterNear(st, T, {snow: 1.2});
  gradeWinter(st, 0.6);
  st.post.letterbox = 1;
  st.post.bloom = 0.55;
  const lines: [number, string, string][] = [
    [B(19), '九綱', '九纲'],
    [B(21), '偏光', '偏光'],
    [B(23), '烏と声明', '乌与声明'],
    [B(25), '表裏の間', '表里之间'],
  ];
  for (const [at, jp, cn] of lines) {
    if (T >= at && T < at + (B(2) - B(0))) st.text({kind: 'chant', jp, cn, age: T - at, dur: B(2) - B(0) - 0.02, x: o.side === 'left' ? 0.16 : 0.84, y: 0.16, size: jp.length > 2 ? 110 : 150, color: '#dbe8ff'});
  }
}

export default shots as Shot[];
