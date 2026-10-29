import React from 'react';
import {interpolate, useCurrentFrame} from 'remotion';
import {prog} from '../kit';
import {Creator, God, Scholar, TianZun} from '../puppet';
import {Basket, Candle, Desk, GreenHat, Heart, Leather, Narcissus, OilLamp, Source, SourceKind, Xi, Zzz} from '../props';
import {ActCard, Big, Line, Narr, Seal, Stage} from '../stage';
import {C, F} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/* 时间都按整片的秒数写（和音乐对齐），换算成本段的帧号 */
export const useClock = (T0: number) => {
  const f = useCurrentFrame();
  const at = (sec: number) => Math.round((sec - T0) * 30);
  const L = (list: [number, number, string][]): Line[] => list.map(([a, b, t]) => [at(a), at(b), t]);
  const p = (sec: number, dur: number) => prog(f, at(sec), dur * 30);
  return {f, at, L, p};
};

// 走路：一颠一颠
const bob = (f: number, on: boolean) => (on ? -Math.abs(Math.sin(f * 0.22)) * 10 : 0);

/* ================= 楔子 0–34s：梦境占卜法 ================= */
export const Open: React.FC = () => {
  const {f, at, L, p} = useClock(0);
  const light = interpolate(f, [at(0.8), at(1.1), at(1.3), at(2.6)], [0, 0.5, 0.25, 1], clamp);
  const sleep = p(27.8, 2.2);
  const warp = p(31.5, 2.5);
  const counts = '一二三四五六七';
  const lines = L([
    [4.5, 9, '各位好。今天，本人向大家汇报一项研究成果。'],
    [9, 13.5, '关于第一纪——混沌纪元——正史记载极少。'],
    [13.5, 18.5, '为此，本人采用了一种严谨的方法：梦境占卜法。'],
    [18.5, 23.5, '第一步：把问题写在纸上——天尊与上帝的真实关系。'],
    [23.5, 27.5, '第二步：默念七遍。'],
    [27.5, 31, '第三步：借助冥想，进入沉眠。'],
    [31, 34.2, '以下，就是本人在梦中所见。'],
  ]);
  const titleOp = Math.min(p(3, 0.8), 1 - p(12.8, 0.8));
  const scOp = p(13.5, 0.8);
  return (
    <Stage light={light} warp={warp} narr={<Narr lines={lines} />}>
      {titleOp > 0 && (
        <div style={{position: 'absolute', left: 0, right: 0, top: 210, textAlign: 'center', opacity: titleOp, mixBlendMode: 'multiply'}}>
          <div style={{fontFamily: F.brush, fontSize: 170, color: C.black, letterSpacing: '0.04em', transform: `scale(${0.96 + 0.04 * p(3, 1)})`}}>第一纪隐秘历史</div>
          <div style={{fontFamily: F.serif, fontWeight: 700, fontSize: 40, color: '#5a2a10', marginTop: 16, letterSpacing: '0.1em'}}>——基于梦境占卜法的田野调查</div>
          <div style={{display: 'inline-block', marginTop: 40, fontFamily: F.kai, fontSize: 34, color: '#fff2da', background: C.seal, padding: '4px 18px', borderRadius: 4, transform: 'rotate(-3deg)'}}>报告人：本人</div>
        </div>
      )}
      {scOp > 0 && (
        <>
          <Scholar x={760} y={330 + sleep * 30} s={0.95} opacity={scOp} pose={{head: -sleep * 28 + (f > at(23.5) && f < at(27.4) ? Math.sin((f - at(23.5)) * 0.44) * 4 : 0), lean: -sleep * 12, near: {sh: 30 - sleep * 20, el: 50}, far: {sh: 10, el: 30}}} />
          <Leather opacity={scOp}>
            <Desk x={820} y={560} />
            <OilLamp x={980} y={560} />
            <Zzz x={850} y={170} at={at(28.6)} />
          </Leather>
        </>
      )}
      {/* 写着问题的纸 */}
      {f >= at(18.6) && f < at(31) && (
        <div style={{position: 'absolute', left: 1180, top: 120, width: 300, padding: '28px 26px', background: '#fff4dc', border: `4px solid ${C.edge}`, mixBlendMode: 'multiply', transform: `rotate(3deg) translateY(${(1 - p(18.6, 0.5)) * -40}px)`, opacity: Math.min(p(18.6, 0.4), 1 - p(30.4, 0.6))}}>
          <div style={{fontFamily: F.kai, fontSize: 30, color: '#7a2a10'}}>问：</div>
          <div style={{fontFamily: F.brush, fontSize: 64, color: C.black, lineHeight: 1.15, marginTop: 6}}>天尊与上帝的真实关系</div>
        </div>
      )}
      {/* 默念七遍：一拍一个字 */}
      {f >= at(23.7) && f < at(27.6) && (
        <div style={{position: 'absolute', left: 1180, top: 560, display: 'flex', gap: 10, mixBlendMode: 'multiply'}}>
          {counts.split('').map((c, i) => {
            const t0 = at(23.7) + i * 14;
            return (
              <div key={c} style={{fontFamily: F.kai, fontSize: 60, color: C.red, opacity: f >= t0 ? 1 : 0, transform: `scale(${f >= t0 ? interpolate(f - t0, [0, 4, 8], [1.6, 0.9, 1], clamp) : 1})`}}>
                {c}
              </div>
            );
          })}
        </div>
      )}
      <Seal at={at(9.3)} until={at(13.4)} kind="正文" text="《宿命之环》六卷126《解读象征》：第一纪被称为“混沌纪元”" x={40} y={30} />
      <Seal at={at(18.8)} until={at(31)} kind="正文" text="《诡秘之主》一卷55《启示》：克莱恩把占卜语句写在纸上，默念七遍，冥想后入睡" x={40} y={30} />
    </Stage>
  );
};

/* ================= 第一折 34–70s：水仙 ================= */
export const Narc: React.FC = () => {
  const {f, at, L, p} = useClock(34);
  const flash = 1 - p(34, 0.6);
  const crOp = p(36, 0.8) * (1 - p(45, 1));
  const split = p(42, 3);
  const pairOp = p(44.5, 1.2);
  const near = p(51, 9); // 被聚合本能拉近
  const jump = p(64.85, 0.3); // “水仙”落下，两位被弹开
  const tzX = 520 + near * 170 - jump * 260;
  const gdX = 1220 - near * 170 + jump * 260;
  const thread = p(55.5, 1.5);
  const lines = L([
    [36.5, 41, '在最初，只有一位造物主。'],
    [41, 46, '后来，祂分出了不同的人格。其中两个，是天尊与上帝。'],
    [46, 50.5, '也就是说，天尊和上帝，本是同一位。'],
    [50.5, 55.5, '黑夜女神说过：聚合是本能。'],
    [55.5, 60, '两位之间，存在一种无法遏制的、重新聚合的倾向。'],
    [60, 62.7, '那一刻，天尊动心了。'],
    [62.7, 65, '学术上，这种关系称为——'],
    [65.4, 70, '（自己，爱上了自己。）'],
  ]);
  return (
    <Stage narr={<Narr lines={lines} />}>
      <ActCard at={at(34.1)} until={at(37.6)} no="第一折" title="分身" />
      {crOp > 0 && <Creator x={870} y={300} s={0.95} split={split} opacity={crOp} />}
      {pairOp > 0 && (
        <>
          <TianZun x={tzX} y={300} s={0.95} opacity={pairOp} pose={{near: {sh: 20 + near * 30, el: 30}, head: f > at(60) ? -4 : 0}} />
          <God x={gdX} y={310} s={0.95} face={-1} opacity={pairOp} pose={{near: {sh: 30 + near * 20, el: 40}, far: {sh: 10, el: 20}}} />
        </>
      )}
      {thread > 0 && (
        <Leather>
          {/* 红线：两位之间的“聚合本能” */}
          <path d={`M${tzX + 40} 330 C ${(tzX + gdX) / 2} ${250 + Math.sin(f * 0.1) * 20} ${(tzX + gdX) / 2} ${410 - Math.sin(f * 0.1) * 20} ${gdX - 40} 330`} stroke={C.red} strokeWidth={5} fill="none" strokeDasharray={`${thread * 900} 900`} />
          {f > at(60) &&
            [0, 1, 2].map((i) => {
              const t = ((f - at(60) + i * 20) % 60) / 60;
              return <Heart key={i} x={(tzX + gdX) / 2 + (i - 1) * 60} y={300 - t * 160} s={0.6 + i * 0.15} rot={(i - 1) * 12} />;
            })}
        </Leather>
      )}
      <Seal at={at(42)} until={at(50.3)} kind="正文" text="《宿命之环》七卷54《神性与人性》：愚者说，天尊、上帝都是最初造物主的一个人格" x={40} y={30} />
      <Seal at={at(50.8)} until={at(62.5)} kind="正文" text="《诡秘之主》八部16《大手笔》：黑夜女神说“聚合是本能”，天尊与上帝有重新聚合、成为最初造物主的倾向" x={40} y={30} w={760} />
      {f >= at(65) && (
        <Leather>
          <Narcissus x={770} y={470} s={1.0 * p(65, 0.3)} />
          <Narcissus x={970} y={470} s={1.0 * p(65.1, 0.3)} />
        </Leather>
      )}
      <Big at={at(65)} text="水仙" x={870} y={240} size={250} />
      {flash > 0 && <div style={{position: 'absolute', inset: 0, background: '#fff8e0', opacity: flash}} />}
    </Stage>
  );
};

/* ================= 第二折 70–95s：彩礼 ================= */
const SIX: SourceKind[] = ['永暗之河', '失序之国', '母巢', '暗影世界', '知识荒野', '光之钥'];

// 扁担：从天尊肩上横过去，两头挂竹篮，篮里冒出收来的源质
export const Pole: React.FC<{x: number; y: number; s: number; f: number; walking: boolean; filled: number}> = ({x, y, s, f, walking, filled}) => {
  const sway = walking ? Math.sin(f * 0.22) * 6 : 0;
  const py = y + 26 * s;
  const ends: [number, number][] = [
    [x - 210 * s, py + 12 * s + sway],
    [x + 230 * s, py + 12 * s - sway],
  ];
  return (
    <Leather>
      <path d={`M${ends[0][0]} ${ends[0][1]} Q ${x} ${py - 6} ${ends[1][0]} ${ends[1][1]}`} stroke="#7a3a18" strokeWidth={9 * s} fill="none" strokeLinecap="round" />
      {ends.map(([ex, ey], i) => (
        <g key={i}>
          {SIX.filter((_, k) => k % 2 === i && k < filled).map((kd, j) => (
            <Source key={kd} kind={kd} x={ex - 30 * s + j * 30 * s} y={ey + 62 * s} r={22 * s} label={false} />
          ))}
          <Basket x={ex} y={ey + 150 * s * 0.9} s={s * 0.9} />
        </g>
      ))}
    </Leather>
  );
};

export const Gift: React.FC = () => {
  const {f, at, L, p} = useClock(70);
  const lines = L([
    [71, 75, '天尊喜欢上帝。但是，祂娶不起。'],
    [75, 79, '因为，没有彩礼。'],
    [79, 83.5, '于是，天尊开始收集源质，东拼西凑。'],
    [83.5, 88, '永暗之河、失序之国、母巢……'],
    [88, 92, '……暗影世界、知识荒野、光之钥。一共六份。'],
    [92, 95.2, '天尊挑起担子，上门提亲。'],
  ]);
  const listOp = Math.min(p(75.5, 0.5), 1 - p(82.8, 0.5));
  // 六份源质：踩着拍子一份份收进来
  const beat = 14.29;
  const t0 = at(83.6);
  const got = (i: number) => t0 + Math.round(i * beat * 2.2);
  const intoBasket = p(91.2, 0.8);
  const walking = f > at(92.2);
  const tx = 420 + (walking ? (f - at(92.2)) * 7 : 0);
  return (
    <Stage narr={<Narr lines={lines} />}>
      <ActCard at={at(70.1)} until={at(73.6)} no="第二折" title="彩礼" />
      <TianZun x={tx} y={300 + bob(f, walking)} s={0.95} pose={{near: {sh: f > at(91) ? 70 : 20, el: f > at(91) ? 70 : 30}, head: f > at(75) && f < at(79) ? -6 : 0}} />
      {f > at(91) && <Pole x={tx} y={300 + bob(f, walking)} s={0.95} f={f} walking={walking} filled={6} />}
      {/* 礼单 */}
      {listOp > 0 && (
        <div style={{position: 'absolute', left: 1040, top: 90, width: 520, padding: '30px 40px 40px', background: C.red, border: `6px solid ${C.black}`, boxShadow: `inset 0 0 0 4px ${C.gold}`, mixBlendMode: 'multiply', opacity: listOp, transformOrigin: '50% 0', transform: `scaleY(${p(75.5, 0.6)})`}}>
          <div style={{fontFamily: F.brush, fontSize: 80, color: '#fff1d6', textAlign: 'center'}}>礼单</div>
          {[
            ['迎娶', '支柱一位'],
            ['彩礼', '源质　若干'],
            ['现有', '源堡　一份'],
          ].map(([a, b]) => (
            <div key={a} style={{display: 'flex', justifyContent: 'space-between', fontFamily: F.kai, fontSize: 44, color: '#fff1d6', marginTop: 14}}>
              <span>{a}</span>
              <span>{b}</span>
            </div>
          ))}
          <div style={{fontFamily: F.serif, fontWeight: 700, fontSize: 26, color: '#ffe0b8', marginTop: 16, opacity: p(77.2, 0.4), textAlign: 'right'}}>（源堡就是天尊自己，不能算）</div>
        </div>
      )}
      <Seal at={at(77.2)} until={at(83)} kind="正文" text="《宿命之环》七卷54《神性与人性》：天尊是“源堡”的化身" x={40} y={30} />
      {/* 收来的六份 */}
      {f >= t0 && intoBasket < 1 && (
        <Leather>
          {SIX.map((k, i) => {
            const g = got(i);
            if (f < g) return null;
            const s = interpolate(f - g, [0, 5, 9], [1.5, 0.92, 1], clamp);
            const fly = intoBasket;
            const bx = 420 + (i % 2 ? 230 : -210) * 0.95;
            const tx2 = 700 + i * 170;
            return (
              <g key={k} transform={`translate(${tx2 + (bx - tx2) * fly} ${150 + (440 - 150) * fly * fly}) scale(${s * (1 - fly * 0.6)})`}>
                <Source kind={k} x={0} y={0} r={56} label={fly < 0.2} />
                {f - g < 26 && (
                  <text x={44} y={-40} fontFamily={F.kai} fontSize={46} fill={C.red} transform="rotate(14 44 -40)" opacity={1 - (f - g) / 26}>
                    收
                  </text>
                )}
              </g>
            );
          })}
        </Leather>
      )}
      {f >= t0 && intoBasket < 1 && (
        <div style={{position: 'absolute', left: 700, top: 300, fontFamily: F.kai, fontSize: 40, color: C.black, mixBlendMode: 'multiply', opacity: 1 - intoBasket}}>
          彩礼：{SIX.filter((_, i) => f >= got(i)).length} / 6
        </div>
      )}
      <Seal at={at(84)} until={at(90.5)} kind="正文" text="《宿命之环》七卷54：天尊在第一纪容纳过永暗之河、失序之国" x={40} y={30} />
      <Seal at={at(88.4)} until={at(95)} kind="梦" text="另外四份，是梦里看见的" x={40} y={110} />
    </Stage>
  );
};

/* ================= 第三折 95–120s：提亲（被源质绿了；深情；拜堂） ================= */
export const Green: React.FC = () => {
  const {f, at, L, p} = useClock(95);
  const lines = L([
    [98.5, 102.5, '结果发现：上帝那边，已经有了两份源质。'],
    [102.5, 106.5, '学术上，这种情况称为：被源质绿了。'],
    [106.5, 110, '但天尊很深情。祂没有计较。'],
    [110, 113.3, '一拜——混沌。（那时，还没有天地。）'],
    [113.3, 116.6, '二拜高堂——高堂，也是祂们自己。'],
    [116.6, 120.2, '夫妻对拜。礼成。'],
  ]);
  const walkIn = interpolate(f, [0, at(98)], [-120, 560], clamp);
  const walking = f < at(98);
  const wed = p(110, 1);
  const tzX = walkIn + wed * 140;
  const gdX = 1260 - wed * 180;
  // 拜三拜
  const bowAt = [111.4, 114.8, 118.2].map(at);
  const bow = bowAt.reduce((v, b) => Math.max(v, interpolate(f, [b, b + 10, b + 24, b + 34], [0, 1, 1, 0], clamp)), 0);
  const hatFall = p(103.4, 0.5);
  const hatOff = p(107.5, 0.8);
  const greenTint = Math.min(p(103.4, 0.3), 1 - p(106.8, 1)) * 0.5;
  const orbitOp = 1 - p(109.5, 0.6);
  return (
    <Stage narr={<Narr lines={lines} />}>
      <ActCard at={at(95.1)} until={at(98.6)} no="第三折" title="提亲" />
      {wed > 0 && (
        <Leather opacity={wed}>
          <g opacity={1 - Math.min(p(113.3, 0.4), 1 - p(116.4, 0.4))}>
            <Xi x={870} y={210} s={0.85} />
          </g>
          <Candle x={560} y={640} s={1.1} />
          <Candle x={1180} y={640} s={1.1} />
        </Leather>
      )}
      {f > at(113) && f < at(117) && <Creator x={870} y={200} s={0.42} opacity={0.6 * Math.min(p(113.3, 0.5), 1 - p(116.3, 0.5))} />}
      <TianZun x={tzX} y={300 + bob(f, walking)} s={0.95} pose={{lean: bow * 22, near: {sh: walking ? 70 : 20 + bow * 30, el: walking ? 70 : 40}, head: f > at(98.5) && f < at(103) ? 6 : 0}} />
      {walking && <Pole x={tzX} y={300 + bob(f, walking)} s={0.95} f={f} walking filled={6} />}
      {!walking && f < at(110) && (
        <Leather opacity={1 - p(109.2, 0.8)}>
          {[0, 1].map((i) => {
            const kd = (['永暗之河', '失序之国', '母巢', '暗影世界', '知识荒野', '光之钥'] as SourceKind[]);
            return kd.filter((_, k) => k % 2 === i).map((k, j) => <Source key={k} kind={k} x={tzX - 260 + i * 70 + j * 40} y={600 - j * 30} r={24} label={false} />);
          })}
        </Leather>
      )}
      <God x={gdX} y={310} s={0.95} face={-1} pose={{lean: bow * (f > bowAt[2] - 2 ? 22 : 18), near: {sh: 30, el: 40}, far: {sh: 10, el: 20}}} />
      {orbitOp > 0 && f > at(98.5) && (
        <Leather opacity={orbitOp * p(98.5, 0.6)}>
          {(['灾祸之城', '？'] as SourceKind[]).map((k, i) => {
            const a = f * 0.03 + i * Math.PI;
            return <Source key={k} kind={k} x={gdX + Math.cos(a) * 240} y={210 + Math.sin(a) * 70} r={48} glow={1} />;
          })}
        </Leather>
      )}
      <Seal at={at(99)} until={at(106)} kind="正文" text="《宿命之环》七卷55《序列之上》：原初上帝同样容纳过多份源质；七卷54：愚者推测上帝容纳过灾祸之城" x={40} y={30} w={760} />
      {hatFall > 0 && hatOff < 1 && (
        <Leather>
          <GreenHat x={tzX + 8 + hatOff * -240} y={-60 + hatFall * 210 + hatOff * 330} s={0.95} rot={hatOff * -60} />
        </Leather>
      )}
      {greenTint > 0 && <div style={{position: 'absolute', inset: 0, background: '#3f9a55', mixBlendMode: 'multiply', opacity: greenTint}} />}
      <Big at={at(119)} text="礼成" x={870} y={420} size={180} color={C.red} rot={-6} />
    </Stage>
  );
};
