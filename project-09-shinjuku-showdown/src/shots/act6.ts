// 第六幕「南へ」：第 98 小节到片尾（167.92 – 197.17 s）。世界斩（第 98 小节第 2 拍，全曲最重的一击）、
// 龍鱗 · 反発 · 番いの流星、闪回、机场、「天晴れだ五条悟」、片名。
import {B, DURATION, FPS, H, W, clamp, ease, hash, kf, lerp} from '../engine/core';
import {plate} from '../engine/assets';
import {Stage} from '../engine/stage';
import {POSE, P, RIG, breathe, poseAt} from '../engine/rig';
import * as fx from '../engine/fx';
import {Shot, seq} from './types';
import {actor, anchor, gojoEyes, sukunaEyes, GOJO_C, SUKUNA_C} from './kit';
import {cutIn} from './cutin';
import {crater} from './act5';
import act1 from './act1';
import act2 from './act2';
import act3 from './act3';
import act4 from './act4';
import act5 from './act5';

const END = DURATION / FPS;
const CUT = B(98, 2); // 世界斩

const gradeDusk = (st: Stage) => {
  st.post.bloom = 0.5;
  st.post.vignette = 0.55;
  st.post.tint = '#ff8a3a';
  st.post.tintA = 0.2;
  st.post.letterbox = 1;
};

/** 世界斩的切线（屏幕坐标）：从左下到右上，经过五条的胸口。 */
const cutLine = (st: Stage, gx: number, gy: number): [number, number, number, number] => {
  const x = st.sx(gx), y = st.sy(gy);
  const slope = -0.16;
  return [0, y - slope * x, W, y + slope * (W - x)];
};

/** 巨坑远景：五条左、宿傩右，夕阳逆光。 */
const duskWide = (st: Stage, T: number, o: {gojo?: boolean; z?: number} = {}) => {
  const z = o.z ?? 2.2;
  st.camera(330, -60, z);
  crater(st, {d: 0.85});
  fx.embers(st, T, {n: 70, color: '#ffa060', a: 0.7});
  if (o.gojo !== false) actor(st, {rig: RIG.gojo, pose: breathe(T, P({armF: 4, armB: -4, legF: 6, legB: -6})), x: 200, y: 0, face: 1, opts: {tint: '#1a0a10', tintA: 0.45}});
  actor(st, {rig: RIG.sukuna, pose: P({lean: 22, armF: 14, armB: -10, legF: 10, legB: -10}), x: 520, y: 0, face: -1, opts: {tint: '#1a0a10', tintA: 0.45}});
  st.flush();
};

// ---------------- 闪回 ----------------
const ALL = [...act1, ...act2, ...act3, ...act4, ...act5];
const byId = (id: string) => ALL.find((s) => s.id.startsWith(id))!;
/** 重放之前的某个镜头的某一刻，套上冷色、去饱和的“记忆”调色。 */
const flashback = (st: Stage, id: string, localT: number, age: number) => {
  const s = byId(id);
  const T = s.from + localT;
  st.begin(T, localT);
  s.draw(st, localT, T);
  st.flush();
  st.texts = [];
  st.post.desat = 0.75;
  st.post.tint = '#6f86b0';
  st.post.tintA = 0.35;
  st.post.vignette = 0.8;
  st.post.grain = 0.12;
  st.post.letterbox = 1;
  st.post.invert = 0;
  st.post.chroma = 0;
  st.post.fade = '#ffffff';
  st.post.fadeA = kf(age, [[0, 0.7], [0.12, 0]]);
};

const shots = seq(
  [
    // 70 ★ 世界斩：整个画面沿一条线错开（不是飞来的刀气）
    {
      id: '70-world-cut', at: B(98),
      draw: (st, t, T) => {
        duskWide(st, T);
        gradeDusk(st);
        if (T >= CUT) {
          const [x1, y1, x2, y2] = cutLine(st, 200, -24);
          const u = T - CUT;
          const shift = 70 * ease.out3(clamp(u / 0.6)) + 10 * u;
          const gap = 10 * ease.out3(clamp(u / 0.5)) + 3 * u;
          st.slice(x1, y1, x2, y2, -shift, gap, '#000', 1);
          // 斩口：一道发白的细线沿着缝慢慢冷下来
          const g2 = st.g;
          g2.save();
          g2.globalCompositeOperation = 'lighter';
          g2.strokeStyle = '#ffe8d8';
          g2.globalAlpha = 0.9 * clamp(1 - u / 1.2);
          g2.lineWidth = 2;
          g2.beginPath();
          g2.moveTo(x1, y1);
          g2.lineTo(x2, y2);
          g2.stroke();
          g2.restore();
          // 斩线一闪
          if (u < 0.12) {
            const g = st.g;
            g.save();
            g.globalCompositeOperation = 'lighter';
            g.strokeStyle = '#ffffff';
            g.globalAlpha = 1 - u / 0.12;
            g.lineWidth = 6;
            g.beginPath();
            g.moveTo(x1, y1);
            g.lineTo(x2, y2);
            g.stroke();
            g.restore();
          }
          if (u < 2 / 60) st.post.invert = 1;
          // 切开后新的余烬照样飘过缝隙
          fx.embers(st, T, {n: 40, color: '#ffa060', a: 0.6, seed: 9});
        }
      },
      cues: [[CUT - B(98), 'world_cut', 2], [CUT - B(98) + 0.02, 'subdrop', -2]],
    },
    // 71 宿傩插镜：咏唱一句句叠上来
    {
      id: '71-chant', at: B(99),
      draw: (st, t, T) => {
        cutIn(st, 'sukuna', t, B(101) - B(99), {eyes: 1, s: 0.9, x: 0.34 * W, bg: ['#0c0203', '#3a070e'], streaks: 30, drift: 30});
        fx.embers(st, T, {n: 40, color: '#ff6040', a: 0.5});
        st.post.bloom = 0.4;
        st.post.vignette = 0.6;
        st.post.desat = 0.45; // 说明性的插镜：压掉一半颜色，跟现场区分开
        st.post.letterbox = 1;
        st.text({kind: 'tech', jp: '世界を断つ斬撃', cn: '斩断世界本身的斩击', age: T - B(99), dur: B(101) - B(99), x: 0.66, y: 0.24, size: 84, color: '#ffd0c8', align: 'left'});
        const lines: [number, string, string, number][] = [
          [B(99, 3), '龍鱗', '龙鳞', 0.93],
          [B(100), '反発', '反发', 0.85],
          [B(100, 2.5), '番いの流星', '成双的流星', 0.77],
        ];
        for (const [at, jp, cn, x] of lines) st.text({kind: 'chant', jp, cn, age: T - at, dur: B(101) - at, x, y: 0.36, size: jp.length > 2 ? 72 : 92, color: '#ffd0c8'});
        st.text({kind: 'sub', jp: '以魔虚罗的适应为范本——斩击的对象是世界本身', age: T - B(99, 2), dur: B(101) - B(99, 2), x: 0.55, y: 0.88, size: 28, align: 'left'});
      },
      cues: [[0, 'chant_low', -4], [B(99, 3) - B(99), 'chant_low', -5], [B(100) - B(99), 'chant_low', -5]],
    },
    // 72 断开的地平线：上半个画面慢慢沉下去，只剩一道肩线落出遮幅
    {
      id: '72-fall', at: B(101),
      draw: (st, t, T) => {
        duskWide(st, T);
        gradeDusk(st);
        const [x1, y1, x2, y2] = cutLine(st, 200, -24);
        const u = T - CUT;
        st.slice(x1, y1, x2, y2, -70 - 10 * u - 60 * ease.in2(clamp(t / 1.7)), 10 + 3 * u + 120 * ease.in2(clamp(t / 1.7)), '#000', 1);
        st.fill('#000000', kf(t, [[0.8, 0], [1.7, 0.35]]));
      },
      cues: [[0.4, 'thud', -8]],
    },
    // 73–74 闪回：每两拍一个记忆
    {
      id: '73-memory', at: B(102),
      draw: (st, t, T) => {
        const k = Math.floor((T - B(102)) / (B(0, 3) - B(0, 1)));
        const age = (T - B(102)) - k * (B(0, 3) - B(0, 1));
        const list: [string, number][] = [
          ['05-', 0.9],
          ['19-', 0.25 + age * 0.3],
          ['34-', 1.0 + age * 0.3],
          ['29-', B(46) - B(45) + 0.12 + age * 0.2],
          ['48-', 0.05 + age * 0.3],
          ['51-', 0.06 + age * 0.3],
          ['64-', 0.5 + age * 0.5],
          ['04-', 2.6 + age * 0.3],
        ];
        const [id, lt] = list[Math.min(k, list.length - 1)];
        flashback(st, id, lt, age);
      },
      cues: [[0, 'memory', -6], [B(103) - B(102), 'memory', -8], [B(104) - B(102), 'memory', -6], [B(105) - B(102), 'memory', -8]],
    },
    // 75 一片雪沿着斩线落下，白场，窗格渐显
    {
      id: '75-white', at: B(106),
      draw: (st, t, T) => {
        st.fill('#f6f4ef', 1);
        const k = clamp((T - B(106)) / (B(106, 3) - B(106)));
        st.plate('airport', {s: 0.7, d: 0, a: k * 0.5, blur: 8 * (1 - k)});
        const x = lerp(W * 0.2, W * 0.7, k), y = lerp(H * 0.2, H * 0.6, k);
        st.g.fillStyle = '#ffffff';
        st.g.fillRect(x, y, 6, 6);
        st.post.bloom = 0.3;
        st.post.vignette = 0.15;
        st.post.letterbox = 1;
        st.post.grain = 0.04;
      },
      cues: [[0, 'airport_air', -10]],
    },
    // 76 ★ 机场：五条坐在左边，右边一整排空座位
    {
      id: '76-airport', at: B(106, 3),
      draw: (st, t, T) => airport(st, T, {z: kf(T, [[B(106, 3), 1.9], [B(108, 3), 2.2]]), cx: kf(T, [[B(106, 3), 60], [B(108, 3), 40]]), cy: -60}),
    },
    // 77 中景：五条看向旁边的空座，蓝眼慢慢暗下去
    {
      id: '77-south', at: B(108, 3),
      draw: (st, t, T) => {
        airport(st, T, {z: 3.2, cx: 34, cy: -30, turn: kf(T, [[B(109), 0], [B(109, 3), 3]]), eyes: kf(T, [[B(109, 2), 0.8], [B(110), 0]])});
        st.text({kind: 'inner', jp: '南へ', cn: '向南', age: T - B(109), dur: B(110) - B(109) + 0.2, x: 0.7, y: 0.36, size: 110, dark: true});
      },
    },
    // 78 回到巨坑：宿傩站着——天晴れだ 五条悟
    {
      id: '78-appare', at: B(110),
      draw: (st, t, T) => {
        const z = kf(T, [[B(110), 3.2], [B(112), 2.4]]);
        st.camera(kf(T, [[B(110), 500], [B(112), 470]]), -40, z);
        crater(st, {d: 0.85});
        fx.embers(st, T, {n: 70, color: '#ffa060', a: 0.7});
        const si = actor(st, {rig: RIG.sukuna, pose: poseAt(T, [[B(110), P({lean: 10, armF: 10, armB: -6, legF: 8, legB: -8})], [B(111), P({lean: 0, armF: 4, armB: -4, legF: 8, legB: -8})]]), x: 520, y: 0, face: -1, opts: {tint: '#1a0a10', tintA: 0.4}});
        // 左下：被地形挡住大半的白发
        st.flush();
        sukunaEyes(st, si, 0.5, 1.5);
        gradeDusk(st);
        st.text({kind: 'quote', jp: '天晴れだ 五条悟', cn: '了不起，五条悟。', age: T - B(110), dur: B(111) - B(110) - 0.05, color: SUKUNA_C, size: 50});
        st.text({kind: 'quote', jp: '生涯 貴様を忘れることはないだろう', cn: '此生，我都不会忘记你。', age: T - B(111), dur: B(112) - B(111), color: SUKUNA_C, size: 46});
      },
    },
    // 79 片名
    {
      id: '79-title', at: B(112),
      draw: (st, t, T) => {
        st.camera(420, -90, 1.6);
        crater(st, {d: 0.85, blur: 4});
        st.fill('#000000', kf(T, [[B(112), 0.3], [B(113), 0.8], [END - 0.8, 0.92]]));
        st.post.bloom = 0.35;
        st.post.vignette = 0.6;
        st.post.letterbox = 1;
        st.post.fade = '#000';
        st.post.fadeA = kf(T, [[END - 1.4, 0], [END - 0.25, 1]]);
        st.text({kind: 'title', jp: '新宿決戦', cn: '五条悟　对　两面宿傩', age: T - B(112), dur: END - B(112) - 0.3});
      },
    },
  ],
  END,
);

/** 机场候机厅：五条坐在长椅上（座面是世界 y=0），右边是空座。 */
function airport(st: Stage, T: number, o: {z: number; cx: number; cy: number; turn?: number; eyes?: number}) {
  const im = plate('airport');
  st.camera(o.cx, o.cy, o.z);
  const d = 0.8;
  const zl = Math.pow(o.z, d);
  const s = Math.max(0.3, (W * 1.12) / (im.width * zl), (H * 1.1) / (im.height * zl));
  const [x, y] = anchor(st, d, s, im, 1500, 880, 0, 0);
  st.plate('airport', {x, y, s, d});
  fx.rays(st, W * 0.08, -60, T, {n: 8, len: 1800, a: 0.14, dir: Math.PI * 0.3, spread: 0.5, color: 'rgba(255,248,230,0.6)'});
  const gi = actor(st, {rig: RIG.gojo, pose: {...P({legF: 80, legB: 76, armF: 12, armB: -8, lean: 4, bob: -12}), spin: o.turn ?? 0}, x: 0, y: 0, face: 1, shadow: 0});
  st.flush();
  gojoEyes(st, gi, o.eyes ?? 0.6, 1.6);
  fx.motes(st, T, {n: 60, a: 0.4});
  st.post.bloom = 0.3;
  st.post.bloomTh = 2.8;
  st.post.vignette = 0.35;
  st.post.letterbox = 1;
  st.post.tint = '#ffe6c8';
  st.post.tintA = 0.1;
  st.post.grain = 0.05;
}

export default shots as Shot[];
