// 第五幕「茈」：第 83–97 小节（142.34 – 167.92 s）。第二次 Drop：赫射出、穿血打苍、咏唱补足苍、
// 苍赫相撞成无指向的茈、全片最大的爆炸、魔虚罗消散、巨坑与黄昏、「僕の勝ちだ」。
import {B, H, W, clamp, ease, hash, kf, lerp, shakeAt} from '../engine/core';
import {plate} from '../engine/assets';
import {Stage} from '../engine/stage';
import {POSE, P, Pose, RIG, breathe, poseAt} from '../engine/rig';
import * as fx from '../engine/fx';
import {Shot, seq} from './types';
import {actor, anchor, ghosts, gojoEyes, gradeWinter, street, streetEye, sukunaEyes, winterAir, winterNear, BLUE_P, GOJO_C} from './kit';
import {cutIn} from './cutin';

const F = 1 / 60;

// 全幕固定的空间关系：苍在左上，五条左下，魔虚罗中，宿傩右下
const GX = 200, MX = 400, SX = 540;

const gradeDrop = (st: Stage) => {
  gradeWinter(st, 0.6);
  st.post.bloom = 0.6;
  st.post.vignette = 0.45;
};

const maho = (st: Stage, pose: Pose, x: number, y = 0, o: {alpha?: number; sil?: string; silA?: number; tint?: string; tintA?: number} = {}) =>
  actor(st, {rig: RIG.maho, pose, x, y, face: -1, shadow: y < -20 ? 0 : 0.5, opts: o});

/** 赫的轨迹：第 83 小节从五条手里射出，沿右侧弧线升到右上。 */
const redPos = (T: number): [number, number] => {
  const k = clamp((T - B(83)) / (B(89, 3) - B(83)));
  const a = lerp(Math.PI * 0.9, Math.PI * 1.75, ease.out2(k));
  return [lerp(GX + 20, 440, ease.out2(k)) + Math.cos(a) * 30, lerp(-40, -260, ease.out2(k))];
};

/** 巨坑（crater.jpg）：脚踩在石台面上（图上 y=870）。 */
export function crater(st: Stage, o: {d?: number; blur?: number; ax?: number} = {}) {
  const im = plate('crater');
  const d = o.d ?? 0.9;
  const zl = Math.pow(st.cam.z, d);
  const feetY = st.sy(0);
  const s = Math.max(0.42, (W * 1.15) / (im.width * zl), (H - feetY + 40) / ((im.height - 870) * zl), (feetY + 40) / (870 * zl));
  const [x, y] = anchor(st, d, s, im, im.width / 2, 870, o.ax ?? st.cam.x, 0);
  st.fill('#2a0a12', 1);
  st.plate('crater', {x, y, s, d, blur: o.blur});
}

const gradeDusk = (st: Stage) => {
  st.post.bloom = 0.5;
  st.post.vignette = 0.5;
  st.post.tint = '#ff8a3a';
  st.post.tintA = 0.18;
};

const shots = seq(
  [
    // 57 第二次 Drop：后手一甩，赫沿弧线射向右上
    {
      id: '57-aka', at: B(83),
      draw: (st, t, T) => {
        const z = kf(T, [[B(83), 1.9], [B(84), 1.6]]);
        st.camera(kf(T, [[B(83), 300], [B(84), 330]]), kf(T, [[B(83), -110], [B(84), -150]]), z, shakeAt(T, B(83), 14, 0.4, 2));
        street(st, {base: [320, -130]});
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        maho(st, P({armF: 70, armB: 0}), MX);
        actor(st, {rig: RIG.sukuna, pose: P({armF: 30, armB: 20}), x: SX, y: 0, face: -1});
        const gi = actor(st, {rig: RIG.gojo, pose: poseAt(T, [[B(83), P({armB: 160, armF: 20, lean: -6})], [B(83) + 0.08, POSE.skyFling, ease.out5], [B(84), P({armF: 70, armB: 40})]]), x: GX, y: 0, face: 1});
        st.flush();
        fx.blue(st, ...BLUE_P, 6, T, {pull: 1.5, debris: 30});
        const [rx, ry] = redPos(T);
        // 尾迹
        for (let i = 1; i <= 10; i++) {
          const [px, py] = redPos(T - i * 0.03);
          st.disc(px, py, 2.2 * (1 - i / 11), '#f0443e', 0.5 * (1 - i / 11), 'lighter');
        }
        fx.red(st, rx, ry, 3, T);
        fx.shock(st, gi.backHand[0], gi.backHand[1], T, B(83), 0.4, 40, {color: '#ff9a8a', width: 3});
        gojoEyes(st, gi, 1);
        winterNear(st, T);
        gradeDrop(st);
        st.text({kind: 'tech', jp: '赫', cn: '赫', age: T - B(83), dur: B(84) - B(83), x: 0.82, y: 0.22, size: 200, color: '#ff6a5a'});
      },
      cues: [[0, 'red_fire', 0]],
    },
    // 58 宿傩双臂交叠指向苍：穿血
    {
      id: '58-piercing', at: B(84),
      draw: (st, t, T) => {
        const fire = B(84, 3);
        const z = 2;
        st.camera(390, -130, z);
        street(st, {base: [390, -130]});
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        maho(st, P({armF: 70, armB: 0}), MX);
        const si = actor(st, {rig: RIG.sukuna, pose: poseAt(T, [[B(84), P({armF: 30, armB: 20})], [B(84) + 0.3, P({armF: 120, armB: 110, lean: 6, legF: 20, legB: -18})]]), x: SX, y: 0, face: -1});
        st.flush();
        fx.blue(st, ...BLUE_P, 6, T, {pull: 1.5, debris: 30});
        fx.red(st, ...redPos(T), 3, T);
        const reach = clamp((T - fire) / 0.18);
        if (reach > 0) {
          const [hx, hy] = si.hand;
          fx.jet(st, hx, hy, lerp(hx, BLUE_P[0] + 20, reach), lerp(hy, BLUE_P[1] + 14, reach), T, {a: 1, w: 1.4});
        }
        sukunaEyes(st, si, 1, 1.6);
        winterNear(st, T);
        gradeDrop(st);
        st.text({kind: 'tech', jp: '穿血', cn: '穿血', age: T - fire, dur: B(85) - fire, x: 0.8, y: 0.25, size: 120, color: '#ff5a6a'});
      },
      cues: [[B(84, 3) - B(84), 'jet', -2]],
    },
    // 59–61 苍的咏唱：位相 · 黄昏 · 智慧の瞳
    {
      id: '59-phase', at: B(85),
      draw: (st, t, T) => {
        const z = 5;
        st.camera(GX + 40, -44, z);
        streetEye(st, {ax: GX + 40, blur: 7});
        st.glowScreen(W * 0.15, -100, 900, 'rgba(60,120,255,0.55)', 0.9);
        winterAir(st, T, {fogA: 0, snow: 0.6});
        const gi = actor(st, {rig: RIG.gojo, pose: poseAt(T, [[B(85), P({armF: 70, armB: 40})], [B(85) + 0.25, P({armF: 120, armB: 160, lean: 0, legF: 14, legB: -12})]]), x: GX, y: 0, face: 1});
        st.flush();
        gojoEyes(st, gi, 1, 1.7);
        winterNear(st, T);
        gradeDrop(st);
        st.text({kind: 'chant', jp: '位相', cn: '位相', age: T - B(85), dur: B(86) - B(85), x: 0.8, y: 0.12, size: 160, color: '#9fd0ff'});
      },
      cues: [[0, 'chant', -4], [0, 'bell', -10]],
    },
    {
      id: '60-twilight', at: B(86),
      draw: (st, t, T) => {
        const z = kf(T, [[B(86), 4], [B(87), 6, ease.io2]]);
        st.camera(BLUE_P[0] + 10, BLUE_P[1] + 6, z);
        st.plate('skyplate', {s: 0.9, d: 0.2, blur: 4});
        st.fill('#10204a', 0.35, 'multiply');
        // 穿血的尖端被扯向球面，绕成螺旋
        const k = clamp((T - B(86)) / (B(87) - B(86)));
        const [sx0, sy0] = [BLUE_P[0] + 90, BLUE_P[1] + 70];
        let px = sx0, py = sy0;
        for (let i = 1; i <= 24; i++) {
          const u = i / 24;
          const ang = Math.atan2(sy0 - BLUE_P[1], sx0 - BLUE_P[0]) + u * k * 4;
          const r = lerp(Math.hypot(sx0 - BLUE_P[0], sy0 - BLUE_P[1]), 6, u);
          const x = BLUE_P[0] + Math.cos(ang) * r, y = BLUE_P[1] + Math.sin(ang) * r;
          if (u < 0.35 + k * 0.65) st.line(px, py, x, y, 1.2, '#c91e32', 1, 'source-over');
          px = x;
          py = y;
        }
        fx.blue(st, ...BLUE_P, kf(T, [[B(86), 6], [B(86, 3), 5], [B(87), 7]]), T, {pull: 2.5, debris: 50});
        gradeDrop(st);
        st.text({kind: 'chant', jp: '黄昏', cn: '黄昏', age: T - B(86), dur: B(87) - B(86), x: 0.16, y: 0.12, size: 160, color: '#9fd0ff'});
      },
      cues: [[0, 'chant', -4], [0.2, 'suck', -6]],
    },
    {
      id: '61-eye', at: B(87),
      draw: (st, t, T) => {
        const z = 1.8;
        st.camera(260, kf(T, [[B(87), -200], [B(88), -110, ease.io2]]), z);
        street(st, {base: [260, -150]});
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        maho(st, P({armF: 70, armB: 0}), MX);
        actor(st, {rig: RIG.sukuna, pose: P({armF: 110, armB: 100, lean: -4}), x: SX, y: 0, face: -1});
        const gi = actor(st, {rig: RIG.gojo, pose: P({armF: 120, armB: 160, legF: 14, legB: -12}), x: GX, y: 0, face: 1});
        st.flush();
        const swallow = clamp((T - B(87, 3)) / 0.2);
        if (swallow < 1) fx.jet(st, lerp(SX - 10, BLUE_P[0] + 20, ease.in2(swallow)), lerp(-30, BLUE_P[1] + 14, ease.in2(swallow)), BLUE_P[0] + 20, BLUE_P[1] + 14, T, {a: 1 - swallow * 0.5, w: 1.2});
        fx.blue(st, ...BLUE_P, 7 + (T > B(87, 3) ? kf(T, [[B(87, 3), 3], [B(88), 0]]) : 0), T, {pull: 2, debris: 50});
        fx.shock(st, ...BLUE_P, T, B(87, 3), 0.5, 80, {color: '#9fd0ff', width: 4});
        fx.red(st, ...redPos(T), 3, T);
        gojoEyes(st, gi, 1);
        winterNear(st, T);
        gradeDrop(st);
        st.text({kind: 'chant', jp: '智慧の瞳', cn: '智慧之瞳', age: T - B(87), dur: B(88) - B(87), x: 0.84, y: 0.12, size: 120, color: '#9fd0ff'});
      },
      cues: [[0, 'chant', -4], [B(87, 3) - B(87), 'swallow', -2]],
    },
    // 62 魔虚罗浮起冲向苍；五条追上去，一拳把它打离连线
    {
      id: '62-intercept', at: B(88),
      draw: (st, t, T) => {
        const hit = B(89);
        const z = 2.0;
        st.camera(340, -125, z, shakeAt(T, hit, 14, 0.4, 3));
        street(st, {base: [340, -125]});
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        const my = -kf(T, [[B(88), 0], [hit, 150, ease.io2], [B(89, 3), 110]]);
        const mx = kf(T, [[B(88), MX], [hit, 330, ease.io2], [B(89, 3), 470, ease.out3]]);
        maho(st, poseAt(T, [[B(88), P({armF: 150, armB: 30})], [hit + 0.02, P({armF: 110, armB: 90, lean: -18})]]), mx, my, {});
        actor(st, {rig: RIG.sukuna, pose: P({armF: 60, armB: 40}), x: SX, y: 0, face: -1});
        const gx = kf(T, [[B(88, 3), GX], [hit, 300, ease.in2], [B(89, 3), 300]]);
        const gy = -kf(T, [[B(88, 3), 0], [hit, 150, ease.out3], [B(89, 3), 140]]);
        const gp = T < hit ? POSE.air : POSE.upper;
        if (T > B(88, 3) && T < hit) ghosts(st, RIG.gojo, gp, [[gx - 10, gy + 20], [gx - 20, gy + 40]], 1, GOJO_C, 0.4);
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: gy, face: 1});
        st.flush();
        fx.wheel(st, mx + 1, my - 76, 19, 45, {a: 1, lit: 0.2});
        fx.blue(st, ...BLUE_P, 7, T, {pull: 2, debris: 40});
        fx.red(st, ...redPos(T), 3, T);
        fx.blackFlash(st, 318, -186, 90, (T - hit) / 0.4, 19);
        gojoEyes(st, gi, 1);
        winterNear(st, T);
        gradeDrop(st);
      },
      cues: [[0, 'rise', -6], [B(89) - B(88), 'black_flash', -2]],
    },
    // 63 两球相向：半小节内快速逼近
    {
      id: '63-approach', at: B(89, 3),
      draw: (st, t, T) => {
        const z = 1.1;
        st.camera(330, -200, z);
        street(st, {base: [330, -200], dim: 0.3});
        const k = ease.in3(clamp((T - B(89, 3)) / (B(90, 2) - B(89, 3))));
        const bx = lerp(BLUE_P[0], 330, k), by = lerp(BLUE_P[1], -240, k);
        const [r0x, r0y] = redPos(B(89, 3));
        const rx = lerp(r0x, 336, k), ry = lerp(r0y, -240, k);
        actor(st, {rig: RIG.gojo, pose: P({armF: 120, armB: 160}), x: GX, y: 0, face: 1, opts: {sil: '#0a0a14', silA: 0.7}});
        actor(st, {rig: RIG.sukuna, pose: P({armF: 60, armB: 40}), x: SX, y: 0, face: -1, opts: {sil: '#0a0a14', silA: 0.7}});
        st.flush();
        fx.blue(st, bx, by, 7, T, {pull: 2, debris: 30});
        fx.red(st, rx, ry, 4, T);
        st.line(bx, by, rx, ry, 1, 'rgba(200,150,255,0.5)', k, 'lighter');
        fx.streaks(st, T, {n: 30, a: 0.25 * k, color: '#e0d0ff'});
        gradeDrop(st);
      },
      cues: [[0, 'riser', -2]],
    },
    // 64 ★ 接触：紫核成形，一切先被反向吸进中心
    {
      id: '64-contact', at: B(90, 2),
      draw: (st, t, T) => {
        const z = kf(T, [[B(90, 2), 1.4], [B(92), 2.2, ease.in3]]);
        st.camera(333, -235, z, shakeAt(T, B(90, 2), 20, 0.6, 5));
        street(st, {base: [333, -200], dim: 0.45});
        st.fill('#5a1cff', 0.35, 'soft-light');
        const g = st.g;
        // 反吸：屏幕上的碎片向中心流
        const cx = st.sx(333), cy = st.sy(-240);
        g.save();
        g.globalCompositeOperation = 'lighter';
        for (let i = 0; i < 160; i++) {
          const h1 = hash(i, 64), h2 = hash(i, 65);
          const ph = (T * (0.5 + h2) + h1) % 1;
          const ang = h1 * Math.PI * 2;
          const R = (1 - ph) * 1300;
          g.globalAlpha = 0.6 * ph;
          g.fillStyle = h2 > 0.5 ? '#e0b7ff' : '#9a8cff';
          g.fillRect(cx + Math.cos(ang) * R, cy + Math.sin(ang) * R, 3, 3);
        }
        g.restore();
        actor(st, {rig: RIG.gojo, pose: P({armF: 120, armB: 160}), x: GX, y: 0, face: 1, opts: {sil: '#05030a', silA: 0.85}});
        actor(st, {rig: RIG.sukuna, pose: P({armF: 60, armB: 40}), x: SX, y: 0, face: -1, opts: {sil: '#05030a', silA: 0.85}});
        maho(st, P({armF: 110, armB: 90, lean: -18}), 470, -60, {sil: '#05030a', silA: 0.85});
        st.flush();
        const grow = kf(T, [[B(90, 2), 4], [B(92), 9, ease.in2]]);
        fx.purple(st, 333, -240, grow, T, {a: 1, seam: 0});
        st.glow(333, -240, grow * 20, 'rgba(150,70,255,0.4)', 1);
        if (T < B(90, 2) + 2 * F) st.fill('#ffffff', 1);
        gradeDrop(st);
        st.post.bloom = 0.8;
        st.text({kind: 'tech', jp: '虚式「茈」', cn: '虚式“茈”', age: T - B(90, 2), dur: B(92) - B(90, 2), x: 0.5, y: 0.8, size: 130, color: '#d9b0ff'});
      },
      cues: [[0, 'purple_form', 0], [0.4, 'reverse_suck', -4]],
    },
    // 65 ★★ 无指向的茈：全片最大的爆炸
    {
      id: '65-explosion', at: B(92),
      draw: (st, t, T) => {
        const z = kf(T, [[B(92), 1.15], [B(94), 0.95]]);
        st.camera(330, -180, z, shakeAt(T, B(92), 60, 2.5, 8));
        if (t < 2 * F) {
          st.fill('#ffffff', 1);
          return;
        }
        street(st, {base: [330, -180]});
        // 被光吞掉的城市：先烧成紫黑，再被抹平
        st.fill('#2a0a40', kf(t, [[0.1, 0], [0.6, 0.75], [2.4, 0.55]]), 'multiply');
        const R = kf(t, [[0, 40], [0.3, 520, ease.out3], [2.0, 1500, ease.out2]]);
        const cx = st.sx(333), cy = st.sy(-200);
        const g = st.g;
        // 光球：白芯 → 淡紫 → 紫边，越往后越透明，露出被抹平的地面
        g.save();
        const gr = g.createRadialGradient(cx, cy, 0, cx, cy, Math.max(1, st.s(R)));
        gr.addColorStop(0, '#ffffff');
        gr.addColorStop(0.18, 'rgba(245,235,255,1)');
        gr.addColorStop(0.5, 'rgba(190,120,255,0.85)');
        gr.addColorStop(0.8, 'rgba(110,40,220,0.45)');
        gr.addColorStop(1, 'rgba(60,10,140,0)');
        g.fillStyle = gr;
        g.globalCompositeOperation = 'screen';
        g.globalAlpha = kf(t, [[0, 1], [0.6, 1], [2.6, 0.35]]);
        g.fillRect(0, 0, W, H);
        g.restore();
        // 球面亮边
        st.ring(333, -200, R * 0.97, 6, '#f2e4ff', kf(t, [[0, 1], [2, 0.2]]), 'lighter');
        // 剪影
        actor(st, {rig: RIG.gojo, pose: P({armF: 100, armB: -100, lean: -8}), x: GX, y: 0, face: 1, shadow: 0, opts: {sil: '#000000', silA: 1}});
        actor(st, {rig: RIG.sukuna, pose: {...POSE.hurtBig, spin: kf(t, [[0, 0], [1, 30]])}, x: kf(t, [[0, SX], [1, SX + 30]]), y: 0, face: -1, shadow: 0, opts: {sil: '#000000', silA: 1}});
        maho(st, {...P({armF: 110, armB: 90, lean: -18}), spin: kf(t, [[0, 0], [1.5, 20]])}, kf(t, [[0, 470], [1.5, 470]]), -60, {sil: '#000000', silA: 1});
        st.flush();
        fx.shock(st, 333, -200, t, 0.05, 1.2, 1400, {color: '#ffffff', width: 30});
        fx.shock(st, 333, -200, t, 0.18, 1.6, 1900, {color: '#d9b0ff', width: 18});
        fx.shock(st, 333, 0, t, 0.1, 1.4, 1600, {color: '#ffffff', width: 10, flat: 0.12});
        fx.debris(st, 333, -200, t, 0.1, {n: 120, speed: 900, dir: -Math.PI / 2, spread: Math.PI * 2, grav: 300, ground: 40, size: 5, seed: 65, life: 3.5});
        fx.dust(st, 333, -40, t, 0.3, {n: 26, r: 120, spread: 900, rise: 100, life: 3.2, color: '230,220,240', a: 0.8, seed: 66});
        st.post.bloom = 1;
        st.post.vignette = 0.2;
        st.post.chroma = kf(t, [[0, 16], [1, 0]]);
        st.post.fade = '#ffffff';
        st.post.fadeA = kf(t, [[0, 1], [0.12, 0.35], [0.5, 0.05], [2.6, 0], [3.4, 0.4]]);
      },
      cues: [[0, 'purple_boom', 3], [0, 'subdrop', 0], [0.2, 'shockwave', -2], [0.8, 'rumble', -4]],
    },
    // 66 魔虚罗与车轮一起碎成颗粒；光由紫白转成暖灰
    {
      id: '66-dissolve', at: B(94),
      draw: (st, t, T) => {
        const z = 2.4;
        st.camera(470, -120, z);
        st.fill('#d8c8d8', 1);
        st.plate('skysunset', {s: 0.8, d: 0.2, blur: 10, a: kf(t, [[0, 0], [3.4, 0.6]])});
        st.image(plate('crater'), {screen: true, s: 1.0, a: kf(t, [[1.6, 0], [3.4, 0.55]]), blur: 6});
        st.fill('#ffffff', kf(t, [[0, 0.6], [2, 0]]), 'soft-light');
        const k = clamp(t / 2.6);
        const mp = P({armF: 110, armB: 90, lean: -18, spin: 20});
        maho(st, mp, 470, -60, {sil: '#2a2028', silA: 0.85, alpha: 1 - k});
        for (let i = 0; i < 120; i++) {
          const h1 = hash(i, 66), h2 = hash(i, 67), h3 = hash(i, 68);
          const born = h1 * 2.4;
          const u = t - born;
          if (u < 0) continue;
          const x = 470 + (h2 - 0.5) * 30 + u * (20 + h3 * 30), y = -60 - h1 * 70 - u * (10 + h2 * 20);
          st.pixRect(x, y, 1, 1, h3 > 0.5 ? '#3a3038' : '#c29a4e', clamp(1 - u / 1.5));
        }
        st.flush();
        // 车轮的辐条一根根断开
        const wa = clamp(1 - (t - 0.5) / 2);
        fx.wheel(st, 471, -136, 19 * (1 + k * 0.3), 45 + t * 25, {a: wa, lit: 0.6 * wa});
        st.post.bloom = 0.6;
        st.post.tint = '#ffb070';
        st.post.tintA = 0.2 * k;
      },
      cues: [[0, 'dissolve', -6]],
    },
    // 67 尘幕分开：巨坑，天边开始有黄昏色
    {
      id: '67-crater', at: B(96),
      draw: (st, t, T) => {
        const z = kf(t, [[0, 1.2], [1.7, 1.3]]);
        st.camera(330, -200, z);
        crater(st, {d: 0.8});
        const part = ease.out2(clamp(t / 1.6));
        // 尘幕：从中间向两边退，边缘是软的
        const g = st.g;
        g.save();
        g.globalAlpha = 1 - part * 0.85;
        const off = part * W * 0.75;
        for (const side of [-1, 1]) {
          const x0 = W / 2 + side * off;
          const gr = g.createLinearGradient(x0, 0, x0 + side * 500, 0);
          gr.addColorStop(0, 'rgba(184,168,176,0)');
          gr.addColorStop(1, 'rgba(184,168,176,1)');
          g.fillStyle = gr;
          g.fillRect(side < 0 ? 0 : x0, 0, side < 0 ? x0 : W - x0, H);
        }
        g.restore();
        fx.dust(st, 330, -20, t, -0.8, {n: 20, r: 90, spread: 500, rise: 40, life: 4, color: '200,180,190', a: 0.6, seed: 67});
        actor(st, {rig: RIG.gojo, pose: breathe(T, POSE.stand), x: 180, y: 0, face: 1, opts: {sil: '#120a10', silA: 0.6}});
        actor(st, {rig: RIG.sukuna, pose: P({lean: 30, armF: 10, armB: -10, legF: 18, legB: -18}), x: 520, y: 0, face: -1, opts: {sil: '#120a10', silA: 0.6}});
        st.flush();
        fx.embers(st, T, {n: 90, color: '#ff9a4a', a: 0.8});
        gradeDusk(st);
      },
      cues: [[0, 'wind', -6]],
    },
    // 68 宿傩勉强撑着，红眼微亮
    {
      id: '68-sukuna-down', at: B(97),
      draw: (st, t, T) => {
        const z = 3.6;
        st.camera(520 - 120 / z, -40, z);
        crater(st, {d: 0.7, blur: 5, ax: 480});
        const si = actor(st, {rig: RIG.sukuna, pose: poseAt(T, [[B(97), P({lean: 30, armF: 10, armB: -10, legF: 18, legB: -18})], [B(97, 2.5), P({lean: 26, armF: 14, armB: -10, legF: 10, legB: -10})]]), x: 520, y: 0, face: -1, opts: {tint: '#2a0a10', tintA: 0.35}});
        st.flush();
        sukunaEyes(st, si, 0.4 + 0.2 * Math.sin(T * 5), 1.4);
        fx.embers(st, T, {n: 60, color: '#ff9a4a', a: 0.8});
        gradeDusk(st);
      },
      cues: [[0, 'thud', -10]],
    },
    // 69 僕の勝ちだ
    {
      id: '69-i-won', at: B(97, 3),
      draw: (st, t, T) => {
        cutIn(st, 'gojo', t, B(98) - B(97, 3), {eyes: 0.6, s: 0.92, bg: ['#1a0a20', '#c0602a'], streaks: 20, slide: 0, drift: 10});
        fx.embers(st, T, {n: 60, color: '#ffb070', a: 0.7});
        st.post.bloom = 0.45;
        st.post.vignette = 0.45;
        st.text({kind: 'inner', jp: '五条の勝ちだ', cn: '——五条，赢了。', age: T - B(97, 3), dur: B(98) - B(97, 3) + 0.1, x: 0.28, y: 0.5, size: 84});
      },
      cues: [[0, 'heartbeat', -6]],
    },
  ],
  B(98),
);

export default shots as Shot[];
