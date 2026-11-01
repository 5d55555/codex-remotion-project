// 第三幕「领域」：第 49–64 小节（84.40 – 111.66 s）。无量空处对伏魔御厨子、从外侧破界、反转术式、缩小领域、
// 第五次五条先成、「最低でも心臓！！」、车轮出现、魔虚罗从影子里破域而出。
import {B, H, W, clamp, ease, hash, kf, lerp, shakeAt} from '../engine/core';
import {plate} from '../engine/assets';
import {Stage} from '../engine/stage';
import {POSE, P, RIG, breathe, poseAt} from '../engine/rig';
import * as fx from '../engine/fx';
import {Shot, seq} from './types';
import {actor, anchor, ghosts, gojoEyes, soft, street, streetEye, sukunaEyes, winterAir, GOJO_C, SUKUNA_C} from './kit';

const F = 1 / 60;

/** 无量空处的内部：宇宙底图缓慢旋转放大，加从中心涌出的信息流。 */
export function voidBg(st: Stage, T: number, o: {cx?: number; cy?: number; a?: number; zoom?: number; streams?: number} = {}) {
  const im = plate('voidplate');
  // 亮核放在画面右上，角色站的地方背景是暗的；图要大到旋转、偏心以后仍盖满屏幕
  const cx = o.cx ?? W * 0.68, cy = o.cy ?? H * 0.3;
  const need = Math.max((2 * Math.max(cx, W - cx) + 300) / im.width, (2 * Math.max(cy, H - cy) + 300) / im.height);
  const s = need * 1.08 * (o.zoom ?? 1) * (1 + 0.02 * Math.sin(T * 0.3));
  const g = st.g;
  g.save();
  g.globalAlpha = o.a ?? 1;
  g.translate(cx, cy);
  g.rotate(0.05 * Math.sin(T * 0.21));
  g.imageSmoothingEnabled = true;
  g.drawImage(im, (-im.width * s) / 2, (-im.height * s) / 2, im.width * s, im.height * s);
  g.restore();
  fx.infoStream(st, cx, cy, T, {n: Math.round(240 * (o.streams ?? 1)), a: 0.75 * (o.a ?? 1)});
  // 压暗下半部，角色在暗处更清楚
  st.vgrad(H * 0.35, H, [[0, 'rgba(0,0,10,0)'], [1, 'rgba(0,0,10,0.55)']]);
}

/** 伏魔御厨子：神龛像素图（脚踩在血水面上），红天，背景里不停闪过的斩线。 */
export function shrineBg(st: Stage, T: number, o: {d?: number; blur?: number; slashes?: number; feetY?: number} = {}) {
  const im = plate('shrine');
  const d = o.d ?? 0.7;
  const zl = Math.pow(st.cam.z, d);
  const s = Math.max(0.35, (W * 1.15) / (im.width * zl));
  const [x, y] = anchor(st, d, s, im, im.width / 2, o.feetY ?? 760, st.cam.x, 0);
  st.fill('#2a0508', 1);
  st.plate('shrine', {x, y, s, d, blur: o.blur ?? 0});
  // 背景斩线
  const n = Math.round(18 * (o.slashes ?? 1));
  for (let i = 0; i < n; i++) {
    const cyc = 0.5 + hash(i, 3) * 0.6;
    const k = Math.floor(T / cyc + hash(i, 4)), ph = (T / cyc + hash(i, 4)) % 1;
    if (ph > 0.25) continue;
    const x0 = hash(i, k, 5) * W, y0 = hash(i, k, 6) * H * 0.7;
    const ang = (hash(i, k, 7) - 0.5) * 1.2 + (hash(i, k, 8) > 0.5 ? 0 : Math.PI / 2);
    const L = 300 + hash(i, k, 9) * 700;
    const a = 1 - ph / 0.25;
    const g = st.g;
    g.save();
    g.globalCompositeOperation = 'lighter';
    g.strokeStyle = '#ffd8d0';
    g.globalAlpha = 0.5 * a;
    g.lineWidth = 1.5;
    g.beginPath();
    g.moveTo(x0 - Math.cos(ang) * L / 2, y0 - Math.sin(ang) * L / 2);
    g.lineTo(x0 + Math.cos(ang) * L / 2, y0 + Math.sin(ang) * L / 2);
    g.stroke();
    g.restore();
  }
  fx.embers(st, T, {n: 50, color: '#ff5040', a: 0.5, rise: 1.4, seed: 17});
}

const gradeVoid = (st: Stage) => {
  st.post.bloom = 0.55;
  st.post.vignette = 0.5;
  st.post.tint = '#3050ff';
  st.post.tintA = 0.12;
};
const gradeShrine = (st: Stage) => {
  st.post.bloom = 0.5;
  st.post.vignette = 0.55;
  st.post.tint = '#ff2020';
  st.post.tintA = 0.12;
};

/** 斩击雨：在 (X,Y) 附近 [t0,t1) 之间每 gap 秒落一道随机方向的斩线。 */
const slashRain = (st: Stage, T: number, t0: number, t1: number, X: number, Y: number, R: number, gap = 0.05, seed = 1) => {
  for (let i = 0, tt = t0; tt < t1; i++, tt += gap) {
    if (T < tt || T > tt + 0.3) continue;
    const ang = hash(i, seed) * Math.PI;
    const ox = (hash(i, seed, 2) - 0.5) * R, oy = (hash(i, seed, 3) - 0.5) * R * 0.8;
    const L = R * (0.7 + hash(i, seed, 4));
    fx.slashLine(st, X + ox - Math.cos(ang) * L, Y + oy - Math.sin(ang) * L, X + ox + Math.cos(ang) * L, Y + oy + Math.sin(ang) * L, T, tt, {w: 0.9, life: 0.22});
  }
};

/** 宿傩被无量空处灌满信息：僵住，眼神发白，身上一层蓝。 */
const frozenSukuna = (st: Stage, T: number, x: number, o: {spin?: number; y?: number; hurt?: number; pose?: ReturnType<typeof P>} = {}) =>
  actor(st, {rig: RIG.sukuna, pose: {...(o.pose ?? P({armF: 20, armB: 10, legF: 6, legB: -6, lean: -4})), spin: o.spin ?? 0}, x, y: o.y ?? 0, face: -1, shadow: 0, opts: {tint: '#9fd0ff', tintA: 0.22 + 0.08 * Math.sin(T * 30), hurt: o.hurt}});

const shots = seq(
  [
    // 32 无量空处：黑蓝的宇宙从五条身后吞没整条街
    {
      id: '32-muryokusho', at: B(49),
      draw: (st, t, T) => {
        const z = kf(T, [[B(49), 1.6], [B(50), 2.2, ease.io2]]);
        st.camera(305, -60, z);
        streetEye(st, {ax: 305});
        winterAir(st, T, {fogA: 0.2, snow: 0.6});
        const r = kf(T, [[B(49), 0], [B(49) + 0.55, 700, ease.in3]]);
        fx.revealCircle(st, 205, -30, r, () => voidBg(st, T, {cx: st.sx(205), cy: st.sy(-30)}), '#cfe3ff', r < 690 ? 1 : 0);
        const gi = actor(st, {rig: RIG.gojo, pose: {...POSE.sign, legF: 8, legB: -8}, x: 205, y: 0, face: 1, shadow: 0, opts: {sil: '#0a0f24', silA: r > 690 ? 0.6 : 0}});
        const si = actor(st, {rig: RIG.sukuna, pose: {...POSE.sign, legF: 8, legB: -8}, x: 405, y: 0, face: -1, shadow: 0, opts: {sil: '#0a0f24', silA: r > 690 ? 0.6 : 0}});
        st.flush();
        gojoEyes(st, gi, 1, 2);
        sukunaEyes(st, si, 1, 2);
        gradeVoid(st);
        st.text({kind: 'tech', jp: '領域展開', cn: '领域展开', age: T - B(48, 3), dur: B(49, 3) - B(48, 3), x: 0.5, y: 0.2, size: 140, color: '#ffffff'});
        st.text({kind: 'tech', jp: '無量空処', cn: '无量空处', age: T - B(49, 2), dur: B(50) - B(49, 2), x: 0.07, y: 0.2, size: 130, color: '#9fd0ff', align: 'left'});
      },
      cues: [[0, 'domain_void', -2]],
    },
    // 33 伏魔御厨子：神龛从宿傩身后出现，红天盖住右半边
    {
      id: '33-fukuma', at: B(50),
      draw: (st, t, T) => {
        const z = 2.2;
        st.camera(kf(T, [[B(50), 260], [B(51), 360]]), -60, z);
        const r = kf(T, [[B(50), 0], [B(50) + 0.5, 900, ease.in3]]);
        voidBg(st, T, {cx: st.sx(205), cy: st.sy(-30)});
        // 没有结界：红色从右边“渗”进来，边缘是锯齿状的斩痕
        const g = st.g;
        g.save();
        g.beginPath();
        const cx = st.sx(405), cy = st.sy(-30), R = st.s(r);
        for (let i = 0; i <= 48; i++) {
          const a = (i / 48) * Math.PI * 2;
          const rr = R * (0.85 + 0.15 * hash(i, Math.floor(T * 20)));
          g.lineTo(cx + Math.cos(a) * rr, cy + Math.sin(a) * rr);
        }
        g.closePath();
        g.clip();
        shrineBg(st, T, {d: 0.6});
        g.restore();
        const gi = actor(st, {rig: RIG.gojo, pose: {...POSE.sign, legF: 8, legB: -8}, x: 205, y: 0, face: 1, shadow: 0});
        const si = actor(st, {rig: RIG.sukuna, pose: {...POSE.sign, legF: 8, legB: -8}, x: 405, y: 0, face: -1, shadow: 0});
        st.flush();
        gojoEyes(st, gi, 1, 2);
        sukunaEyes(st, si, 1, 2);
        gradeShrine(st);
        st.post.tintA = 0.08;
        st.text({kind: 'tech', jp: '伏魔御廚子', cn: '伏魔御厨子', age: T - B(50, 1.5), dur: B(51) - B(50, 1.5), x: 0.93, y: 0.2, size: 130, color: '#ff6a6a', align: 'right'});
      },
      cues: [[0, 'domain_shrine', -2]],
    },
    // 34 ★ 从外面看：斩击从外侧打在蓝黑结界球上，球壳由外向内裂开
    {
      id: '34-outside', at: B(51),
      draw: (st, t, T) => {
        st.zoomBoost = 1;
        st.camera(0, 0, 1);
        st.plate('aerial', {s: 1.0, d: 1, x: 0, y: 40});
        st.fill('#3a0006', 0.55, 'multiply');
        st.fill('#ff2a2a', 0.25, 'soft-light');
        // 右后方神龛的剪影和红光
        st.glowScreen(W * 0.82, H * 0.55, 700, 'rgba(255,40,40,0.5)', 0.9);
        st.image(soft('shrine'), {screen: true, x: W * 0.84, y: H * 0.46, s: 0.5, a: 0.9, blur: 2, op: 'lighten'});
        const crack = kf(T, [[B(51, 3), 0], [B(52) - 0.05, 1, ease.out3]]);
        fx.barrierSphere(st, -120, 30, 330, T, {cracks: crack, seed: 5});
        // 外侧斩线向球壳汇集
        for (let i = 0; i < 40; i++) {
          const t0 = B(51) + i * 0.035;
          const ang = hash(i, 8) * Math.PI * 2;
          const R0 = 330 + 20, R1 = 330 + 160 + hash(i, 9) * 200;
          const a2 = ang + (hash(i, 10) - 0.5) * 0.3;
          fx.slashLine(st, -120 + Math.cos(ang) * R1, 30 + Math.sin(ang) * R1, -120 + Math.cos(a2) * R0, 30 + Math.sin(a2) * R0, T, t0, {w: 1.6, life: 0.2});
        }
        st.post.bloom = 0.5;
        st.post.vignette = 0.5;
        if (T > B(51, 3) && T < B(51, 3) + 2 * F) st.fill('#dfe9ff', 0.45);
      },
      cues: [[0, 'slash_rain', -4], [B(51, 3) - B(51), 'glass_crack', -2]],
    },
    // 35 五条在红天下：斩击交错，反转术式
    {
      id: '35-rct', at: B(52),
      draw: (st, t, T) => {
        const z = 3;
        st.camera(250, -40, z, shakeAt(T, B(52), 8, 0.4, 3));
        shrineBg(st, T, {d: 0.6, blur: 3});
        const lean = kf(T, [[B(52), 15], [B(52, 3), 0]]);
        const gp = P({lean, armF: 20 + lean, armB: -20, legF: 14, legB: -14});
        const hurt = Math.max(0, Math.sin(T * 50)) * (T < B(52, 3) ? 0.8 : 0);
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: 230, y: 0, face: 1, opts: {hurt}});
        st.flush();
        slashRain(st, T, B(52), B(53), 230, -30, 60, 0.06, 4);
        fx.rct(st, gi.chest[0], gi.chest[1] - 6, 14, T, kf(T, [[B(52, 2.5), 0], [B(52, 3), 1], [B(53), 0.5]]));
        st.glow(gi.head[0], gi.head[1], 18, 'rgba(160,255,200,0.5)', kf(T, [[B(52, 2.5), 0], [B(52, 3), 0.8], [B(53), 0]]));
        gojoEyes(st, gi, 1, 1.6);
        gradeShrine(st);
        st.text({kind: 'tech', jp: '反転術式', cn: '反转术式', age: T - B(52, 3), dur: B(53) - B(52, 3), x: 0.72, y: 0.24, size: 110, color: '#c8ffd8'});
      },
      cues: [[0, 'slash_rain', -6], [B(52, 3) - B(52), 'rct', -4]],
    },
    // 36 再次展开：大球壳压缩成小球（两轮）
    {
      id: '36-shrink', at: B(53),
      draw: (st, t, T) => {
        const z = kf(T, [[B(53), 2], [B(54), 1.3, ease.io2]]);
        st.camera(300, -40, z);
        shrineBg(st, T, {d: 0.5, blur: 4, slashes: 1.5});
        const r = kf(T, [[B(53), 60], [B(53, 2), 400, ease.out3], [B(53, 3), 400], [B(53, 3.5), 120, ease.in3], [B(54), 110]]);
        fx.revealCircle(st, 230, -30, r, () => voidBg(st, T, {streams: 0.6}), '#cfe3ff', 1);
        // 第二轮的轮廓（同心）
        st.ring(230, -30, kf(T, [[B(53, 3), 400], [B(53, 3.5), 120, ease.in3]]), 3, '#9fd0ff', T > B(53, 3) ? 0.6 : 0);
        const gi = actor(st, {rig: RIG.gojo, pose: POSE.sign, x: 230, y: 0, face: 1, shadow: 0});
        actor(st, {rig: RIG.sukuna, pose: POSE.slashWind, x: 410, y: 0, face: -1, shadow: 0});
        st.flush();
        gojoEyes(st, gi, 1, 2);
        gradeShrine(st);
        st.post.tintA = 0.06;
      },
      cues: [[0, 'pulse', -4], [B(53, 3) - B(53), 'pulse', -6]],
    },
    // 37 领域里互殴：蓝星空与红神龛轮流侵入
    {
      id: '37-in-domain', at: B(54),
      draw: (st, t, T) => {
        const h1 = B(54), h2 = B(54, 3);
        const z = 2.4;
        st.camera(310, -40, z, shakeAt(T, h1, 12, 0.3, 2));
        const flip = T >= B(54, 3) && T < B(54, 4) ? 1 : 0;
        if (flip === 0) voidBg(st, T);
        else shrineBg(st, T, {d: 0.55, blur: 3});
        const gx = kf(T, [[h1 - 0.1, 250], [h1, 290, ease.in2], [h2, 270]]);
        const gp = poseAt(T, [[B(54), POSE.punchWind], [h1 + 0.02, POSE.dashPunch, ease.out3], [h2 - 0.1, POSE.dashPunch], [h2, P({lean: 30, bob: -3, armF: 20, armB: -40, legF: 30, legB: -30})], [B(55), POSE.stand]]);
        const sx = kf(T, [[h1, 340], [h1 + 0.2, 370, ease.out3], [h2 - 0.1, 360], [h2, 330, ease.in2], [B(55), 345]]);
        const sp = poseAt(T, [[h1, POSE.hurt], [h1 + 0.25, POSE.stand], [h2 - 0.08, POSE.slashWind], [h2, POSE.slash, ease.out3], [B(55), POSE.stand]]);
        actor(st, {rig: RIG.sukuna, pose: sp, x: sx, y: 0, face: -1, shadow: 0, opts: {hurt: T > h1 ? kf(T, [[h1, 1], [h1 + 0.1, 0]]) : 0}});
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: 0, face: 1, shadow: 0});
        st.flush();
        fx.sparks(st, 320, -30, T, h1, {n: 20, speed: 260, color: '#ffffff', color2: '#9fd0ff', seed: 37});
        fx.slashLine(st, 380, -46, 150, -60, T, h2, {w: 1.4});
        // 第四轮结束：结界碎
        fx.shards(st, 300, -40, T, B(54, 4), {n: 30, speed: 400, size: 10, seed: 6});
        gojoEyes(st, gi, 0.8);
        if (flip === 0) gradeVoid(st);
        else gradeShrine(st);
      },
      cues: [[0, 'punch_heavy', -4], [B(54, 3) - B(54), 'slash', -4], [B(54, 4) - B(54), 'glass', -6]],
    },
    // 38 第五次：五条的环先闭合，宿傩的环慢了两帧、半成就熄灭——宿傩被定住
    {
      id: '38-fifth', at: B(55),
      draw: (st, t, T) => {
        const z = kf(T, [[B(55), 3], [B(56), 4]]);
        st.camera(305, -40, z);
        const gClose = B(55) + 0.18, sClose = gClose + 2 * F;
        const won = T > B(55, 2);
        if (won) voidBg(st, T, {streams: 1.3});
        else {
          st.fill('#05060c', 1);
          voidBg(st, T, {a: 0.4});
        }
        const gi = actor(st, {rig: RIG.gojo, pose: T < B(55, 3) ? POSE.sign : POSE.stand, x: 230, y: 0, face: 1, shadow: 0});
        const si = T > B(55, 2) ? frozenSukuna(st, T, 380) : actor(st, {rig: RIG.sukuna, pose: POSE.sign, x: 380, y: 0, face: -1, shadow: 0});
        st.flush();
        const gk = clamp((T - B(55)) / (gClose - B(55)));
        st.ring(230, -30, 40, 2, '#9fd0ff', gk < 1 ? 0.9 : kf(T, [[gClose, 1], [gClose + 0.3, 0]]), 'lighter', -Math.PI / 2, -Math.PI / 2 + gk * Math.PI * 2);
        const sk = clamp((T - B(55)) / (sClose - B(55))) * 0.62; // 只合到六成
        st.ring(380, -30, 40, 2, '#ff6a6a', T < sClose + 0.1 ? 0.9 : kf(T, [[sClose + 0.1, 0.9], [sClose + 0.2, 0]]), 'lighter', -Math.PI / 2, -Math.PI / 2 + sk * Math.PI * 2);
        if (T > gClose && T < gClose + 2 * F) st.fill('#cfe3ff', 0.6);
        gojoEyes(st, gi, 1, 2);
        if (!won) sukunaEyes(st, si, 1, 2);
        else st.glow(si.head[0], si.head[1] + 4, 14, 'rgba(160,210,255,0.7)', 0.8);
        gradeVoid(st);
      },
      cues: [[0.18, 'chime_hi', -4], [0.22, 'thud', -8]],
    },
    // 39 最低でも心臓！！
    {
      id: '39-heart', at: B(56),
      draw: (st, t, T) => {
        const hit = B(56, 3);
        const z = 2.3;
        st.camera(kf(T, [[B(56), 280], [B(57), 330]]), -50, z, shakeAt(T, hit, 22, 0.5, 4));
        voidBg(st, T, {cx: W * 0.25, cy: H * 0.3, streams: 1.2});
        const gx = kf(T, [[B(56), 200], [hit - 0.04, 340, ease.in2], [B(57), 345]]);
        const gp = poseAt(T, [[B(56), P({lean: 30, legF: 35, legB: -35, armF: -20, armB: 40})], [hit - 0.1, POSE.punchWind], [hit, POSE.dashPunch, ease.out3], [B(57), POSE.dashPunch]]);
        if (T < hit) ghosts(st, RIG.gojo, gp, [[gx - 14, 0], [gx - 28, 0], [gx - 42, 0]], 1, GOJO_C, 0.4);
        const si = frozenSukuna(st, T, kf(T, [[hit, 380], [hit + 0.3, 392, ease.out3]]), {hurt: T > hit ? kf(T, [[hit, 1], [hit + 0.12, 0]]) : 0});
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: gx, y: 0, face: 1, shadow: 0});
        st.flush();
        if (T >= hit && T < hit + 2 * F) {
          st.fill('#000', 0.85);
          st.disc(si.chest[0], si.chest[1], 10, '#ffffff', 1);
        }
        fx.shock(st, si.chest[0], si.chest[1], T, hit, 0.4, 70, {color: '#ffffff', width: 4});
        fx.shock(st, si.chest[0], si.chest[1], T, hit + 0.06, 0.5, 110, {color: '#9fd0ff', width: 3});
        fx.blue(st, gi.hand[0], gi.hand[1], 2, T, {a: kf(T, [[hit - 0.2, 0], [hit, 1], [hit + 0.4, 0]])});
        gojoEyes(st, gi, 1);
        gradeVoid(st);
        st.text({kind: 'quote', jp: '最低でも心臓！！', cn: '至少也要捏碎心脏！', age: T - B(56), dur: B(58) - B(56), color: GOJO_C, size: 56});
      },
      cues: [[0, 'dash', -6], [B(56, 3) - B(56), 'punch_heavy', 0], [B(56, 3) - B(56), 'subdrop', -6]],
    },
    // 40 第二拳：宿傩整个人往后转、浮起
    {
      id: '40-second', at: B(57),
      draw: (st, t, T) => {
        const hit = B(57);
        const z = kf(T, [[B(57), 4], [B(58), 3]]);
        st.camera(360, -36, z, shakeAt(T, hit + 0.05, 16, 0.4, 6));
        voidBg(st, T, {cx: W * 0.75, cy: H * 0.28, streams: 1.2});
        const gp = poseAt(T, [[B(57), POSE.dashPunch], [B(57) + 0.12, P({armF: -20, armB: 95, lean: 22, legF: 30, legB: -30})], [B(58), POSE.stand]]);
        frozenSukuna(st, T, kf(T, [[hit, 390], [hit + 0.4, 410, ease.out3]]), {spin: kf(T, [[hit + 0.1, 0], [hit + 0.5, 10]]), y: -kf(T, [[hit + 0.1, 0], [hit + 0.5, 4]]), hurt: kf(T, [[hit + 0.1, 1], [hit + 0.2, 0]])});
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: 352, y: 0, face: 1, shadow: 0});
        st.flush();
        fx.shock(st, 392, -30, T, hit + 0.1, 0.4, 60, {color: '#ffffff', width: 3});
        fx.shock(st, 392, -30, T, hit + 0.16, 0.5, 90, {color: '#9fd0ff', width: 2});
        gojoEyes(st, gi, 1);
        gradeVoid(st);
        st.text({kind: 'quote', jp: '最低でも心臓！！', cn: '至少也要捏碎心脏！', age: T - B(56), dur: B(58) - B(56), color: GOJO_C, size: 56});
      },
      cues: [[0.1, 'punch', -2]],
    },
    // 41 宿傩头上：暗金色的八辐车轮出现，转了一格
    {
      id: '41-wheel', at: B(58),
      draw: (st, t, T) => {
        const z = 5;
        st.camera(392 - 200 / z, -60, z);
        voidBg(st, T, {cx: W * 0.3, cy: H * 0.25, streams: 1});
        const si = frozenSukuna(st, T, 392);
        st.flush();
        const a = kf(T, [[B(58), 0], [B(58) + 0.3, 1, ease.out3]]);
        const ang = kf(T, [[B(58, 3), 0], [B(58, 3) + 0.12, 45, ease.outBack]]);
        fx.wheel(st, 392, -78 + (1 - a) * -10, 13, ang, {a, lit: kf(T, [[B(58, 3), 1], [B(59), 0.2]])});
        st.glow(si.head[0], si.head[1] + 4, 14, 'rgba(160,210,255,0.7)', 0.8);
        gradeVoid(st);
        st.post.tintA = 0.05;
      },
      cues: [[0, 'wheel_appear', -4], [B(58, 3) - B(58), 'wheel', -2]],
    },
    // 42 五条连打；车轮每到小节头转一格，宿傩脚下的影子越来越大
    {
      id: '42-flurry', at: B(59),
      draw: (st, t, T) => {
        const z = kf(T, [[B(59), 2.6], [B(61), 2.2]]);
        st.camera(330, -50, z);
        voidBg(st, T, {streams: 1 - clamp((T - B(60)) / 2) * 0.6});
        const hits = [0, 0.5, 1, 1.5, 2, 2.5, 3, 3.5].map((b) => B(59) + b * BEAT);
        let last = -1;
        hits.forEach((h, i) => (T >= h ? (last = i) : 0));
        const since = last >= 0 ? T - hits[last] : 9;
        const gp = since < 0.08 ? (last % 2 ? P({armF: -20, armB: 95, lean: 22, legF: 30, legB: -30}) : POSE.dashPunch) : POSE.punchWind;
        const shadowR = kf(T, [[B(59), 10], [B(61), 34]]);
        fx.shadow(st, 392, 0, shadowR, 0.9);
        const si = frozenSukuna(st, T, 392, {hurt: since < 0.08 ? 0.8 : 0, spin: last % 2 ? 4 : -2});
        const gi = actor(st, {rig: RIG.gojo, pose: gp, x: 356, y: 0, face: 1, shadow: 0});
        st.flush();
        if (since < 0.3) fx.shock(st, 380, -30, T, hits[last], 0.3, 40 + (last % 3) * 10, {color: last % 2 ? '#9fd0ff' : '#ffffff', width: 2});
        const turns = (T > B(60) ? 1 : 0);
        fx.wheel(st, 392, -78, 13, 45 + turns * kf(T, [[B(60), 0], [B(60) + 0.12, 45, ease.outBack]]), {a: 1, lit: kf(T, [[B(60), 1], [B(60, 2), 0.2]])});
        gojoEyes(st, gi, 1);
        gradeVoid(st);
      },
      cues: [[0, 'punch', -4], [0.43, 'punch', -6], [0.85, 'punch', -4], [1.28, 'punch', -6], [B(60) - B(59), 'wheel', -2], [1.7, 'punch', -4], [2.13, 'punch', -6], [2.56, 'punch', -4], [2.98, 'punch', -6]],
    },
    // 43 信息流绕开车轮：宿傩的眼神回来了；五条察觉，停手
    {
      id: '43-adapting', at: B(61),
      draw: (st, t, T) => {
        const z = kf(T, [[B(61), 2], [B(64), 1.3, ease.io2]]);
        st.camera(320, -60, z);
        voidBg(st, T, {streams: 0.5, a: kf(T, [[B(61), 1], [B(64), 0.6]])});
        const sh = kf(T, [[B(61), 34], [B(63), 70]]);
        fx.shadow(st, 392, 0, sh, 1);
        const wake = kf(T, [[B(62), 0], [B(63), 1]]);
        const si = wake < 1 ? frozenSukuna(st, T, 392) : actor(st, {rig: RIG.sukuna, pose: breathe(T, POSE.stand), x: 392, y: 0, face: -1, shadow: 0});
        const gx = kf(T, [[B(61), 300], [B(62, 3), 320], [B(63, 3), 300, ease.out3]]);
        const gi = actor(st, {rig: RIG.gojo, pose: T < B(63, 3) ? POSE.blueHold : POSE.dodgeB, x: gx, y: 0, face: 1, shadow: 0});
        st.flush();
        const ang = 90 + kf(T, [[B(62), 0], [B(62) + 0.12, 45, ease.outBack]]) + kf(T, [[B(63), 0], [B(63) + 0.12, 45, ease.outBack]]);
        fx.wheel(st, 392, -78, 13, ang, {a: 1, lit: Math.max(kf(T, [[B(62), 1], [B(62, 2), 0.2]]), kf(T, [[B(63), 1], [B(63, 2), 0.2]]))});
        sukunaEyes(st, si, wake, 1.8);
        gojoEyes(st, gi, 1);
        gradeVoid(st);
        st.post.desat = 0.2 * wake;
      },
      cues: [[B(62) - B(61), 'wheel', -2], [B(63) - B(61), 'wheel', -2], [B(63, 3) - B(61), 'swish', -8]],
    },
    // 44 ★ 魔虚罗：先见车轮，身体从影子里升起，剑落下，无量空处碎裂
    {
      id: '44-mahoraga', at: B(64),
      draw: (st, t, T) => {
        const slash = B(64, 3), shatter = B(64, 4);
        const z = kf(T, [[B(64), 2.8], [shatter, 2.2, ease.io2], [B(65), 1.9]]);
        st.camera(kf(T, [[B(64), 420], [B(65), 380]]), kf(T, [[B(64), -70], [B(65), -60]]), z, shakeAt(T, shatter, 26, 0.6, 7));
        if (T < shatter) voidBg(st, T, {streams: 0.3, a: 0.7});
        else {
          streetEye(st, {ax: 330});
          winterAir(st, T, {fogA: 0.2});
        }
        fx.shadow(st, 440, 0, 70, 1);
        const rise = kf(T, [[B(64, 2), 0], [slash - 0.05, 1, ease.out3]]);
        const mp = poseAt(T, [[B(64), P({armF: 150, armB: 20})], [slash - 0.06, P({armF: 150, armB: 20, lean: -4})], [slash + 0.04, P({armF: 65, armB: -10, lean: 18}), ease.out5], [B(65), P({armF: 50, armB: -10, lean: 10})]]);
        const my = lerp(70, 0, rise) - kf(T, [[slash - 0.2, 0], [slash, 6], [slash + 0.2, 0]]);
        if (rise > 0) {
          const g = st.g;
          g.save();
          g.beginPath();
          g.rect(0, 0, W, st.sy(0) + 2); // 地面以下的部分藏在影子里
          g.clip();
          st.figure(RIG.maho, mp, 440, my, {face: -1, tint: '#000000', tintA: 0.6 * (1 - rise)});
          st.flush();
          g.restore();
        }
        const si = actor(st, {rig: RIG.sukuna, pose: breathe(T, POSE.stand), x: 392, y: 0, face: -1, shadow: 0});
        const gi = actor(st, {rig: RIG.gojo, pose: POSE.guard, x: 290, y: 0, face: 1, shadow: 0});
        st.flush();
        fx.wheel(st, 440, lerp(-20, -80, rise), lerp(13, 19, rise), 180 + kf(T, [[B(64), 0], [B(64) + 0.12, 45, ease.outBack]]), {a: 1, lit: kf(T, [[B(64), 1], [B(64, 2), 0.3]])});
        fx.slashLine(st, 470, -110, 300, 30, T, slash, {w: 3, life: 0.4});
        fx.shards(st, 330, -60, T, shatter, {n: 70, speed: 700, size: 22, seed: 9, life: 1.6});
        if (T >= shatter && T < shatter + 2 * F) st.fill('#ffffff', 0.5);
        sukunaEyes(st, si, 1, 1.8);
        gojoEyes(st, gi, 1);
        if (T < shatter) gradeVoid(st);
        else {
          st.post.bloom = 0.45;
          st.post.desat = 0.2;
        }
        st.text({kind: 'sub', jp: '八握剣 異戒神将 魔虚羅', cn: '八握剑 异戒神将 魔虚罗', age: T - B(64, 2), dur: B(65) - B(64, 2) + 0.6, x: 0.5, y: 0.86, size: 34});
      },
      cues: [[0, 'wheel', -2], [B(64, 2) - B(64), 'rise', -6], [B(64, 3) - B(64), 'sword', -2], [B(64, 4) - B(64), 'glass_big', 0]],
    },
  ],
  B(65),
);

const BEAT = (B(1) - B(0)) / 4;

export default shots as Shot[];
