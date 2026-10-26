import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {ChaosSea, FogBank, Space} from '../bg';
import {God, Tianzun, Yuanbao} from '../figures';
import {
  Bolt,
  Chains,
  Converge,
  Core,
  Doors,
  Eyes,
  FireCity,
  Flash,
  FogVeil,
  Glyph,
  Invoke,
  Orb,
  Phantoms,
  River,
  Shadows,
  Shake,
  Shock,
  Storm,
  SunPillars,
  Theft,
} from '../fx';
import {prog} from '../kit';
import {Label, Sub, Tag, Title} from '../text';
import {C, F} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/* 两位神的站位与关键点 */
export const TZ = {x: 470, y: 1010, h: 820};
export const GD = {x: 1450, y: 990, h: 860};
const tzHand = {x: 470, y: 520};
const gdHead = {x: 1450, y: 300};
const gdChest = {x: 1450, y: 470};

type Fig = {x?: number; y?: number; crack?: number; cast?: number; glow?: number; flash?: number; fade?: number; lean?: number};

/* 战场：星空、源堡远景、左雾右海、两位神。 */
export const Field: React.FC<{tz?: Fig; gd?: Fig; seaGray?: number; seaLevel?: number; ybGlow?: number; ybFade?: number; dark?: number; children?: React.ReactNode; gdWrap?: React.CSSProperties; hideYb?: boolean}> = ({
  tz = {},
  gd = {},
  seaGray = 0,
  seaLevel = 0.82,
  ybGlow = 0,
  ybFade = 0,
  dark = 0,
  children,
  gdWrap,
  hideYb,
}) => (
  <AbsoluteFill>
    <Space dim={1 - dark * 0.9} />
    {!hideYb && <Yuanbao x={(tz.x ?? TZ.x) + 60} y={330} w={1000} opacity={(0.4 + ybGlow * 0.5) * (1 - ybFade)} />}
    <FogBank level={0.7} side="left" />
    <AbsoluteFill style={{filter: `grayscale(${seaGray}) brightness(${1 - seaGray * 0.4})`}}>
      <ChaosSea level={seaLevel} side="right" />
    </AbsoluteFill>
    <Tianzun x={tz.x ?? TZ.x} y={tz.y ?? TZ.y} h={TZ.h} crack={tz.crack} cast={tz.cast} glow={tz.glow} flash={tz.flash} fade={tz.fade} lean={tz.lean} />
    <AbsoluteFill style={gdWrap}>
      <God x={gd.x ?? GD.x} y={gd.y ?? GD.y} h={GD.h} crack={gd.crack} cast={gd.cast} glow={gd.glow} flash={gd.flash} fade={gd.fade} lean={gd.lean} />
    </AbsoluteFill>
    {dark > 0 && <AbsoluteFill style={{backgroundColor: '#02040c', opacity: dark * 0.55}} />}
    {children}
  </AbsoluteFill>
);

/* 顶部状态：两位的名字与源质球；用到哪颗，哪颗亮。 */
export const HUD: React.FC<{flare?: Record<string, number>; at?: number}> = ({flare = {}, at = 0}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 12);
  return (
    <AbsoluteFill style={{opacity: p}}>
      <div style={{position: 'absolute', left: 70, top: 44, fontFamily: F.serif, fontWeight: 900, fontSize: 40, color: C.bronze, textShadow: `0 0 14px ${C.bronze}`}}>天尊</div>
      <Orb x={232} y={70} r={15} kind="yuanbao" flare={flare.yuanbao ?? 0} />
      <Orb x={282} y={70} r={15} kind="river" flare={flare.river ?? 0} />
      <Orb x={332} y={70} r={15} kind="disorder" flare={flare.disorder ?? 0} />
      <div style={{position: 'absolute', right: 70, top: 44, fontFamily: F.serif, fontWeight: 900, fontSize: 40, color: '#fff', textShadow: `0 0 14px ${C.chaos}`}}>上帝</div>
      <Orb x={1688} y={70} r={15} kind="chaos" flare={flare.chaos ?? 0} />
      <Orb x={1638} y={70} r={15} kind="calamity" flare={flare.calamity ?? 0} dashed />
      <Tag text="推演：原作没有写过这一战的过程" x={790} y={56} />
    </AbsoluteFill>
  );
};

// 中招后仰：命中后 22 帧内先猛退再回位。出招前冲：随 cast 往前挪。
const recoil = (f: number, hits: number[]) => hits.reduce((v, at) => (f >= at && f < at + 22 ? Math.max(v, Math.sin((Math.PI * (f - at)) / 22) * (1 - (f - at) / 30)) : v), 0);

// 某颗源质在一段时间内亮起。
const flareAt = (f: number, at: number, until: number) => prog(f, at, 10) * (1 - prog(f, until - 10, 10));

const RoundTitle: React.FC<{no: string; name: string; out?: number}> = ({no, name, out = 60}) => {
  const f = useCurrentFrame();
  if (f > out + 20) return null;
  const o = 1 - prog(f, out, 18);
  return (
    <AbsoluteFill style={{opacity: o}}>
      <Label x={960} y={420} a="c" text={no} at={2} size={30} color={C.dim} font={F.serif} w={700} ls={0.4} />
      <Title x={960} y={500} text={name} at={6} size={84} glow="rgba(255,255,255,0.5)" />
    </AbsoluteFill>
  );
};

/* ---------------- 第一回合：聚合 ---------------- */

const R1: React.FC = () => {
  const f = useCurrentFrame();
  const pull = prog(f, 60, 140);
  const clash = f >= 212;
  const back = prog(f, 212, 30);
  return (
    <Shake hits={[[212, 36]]}>
      <Field
        tz={{x: TZ.x + pull * 60 - back * 60, lean: pull * 4 - back * 4, glow: 0.5 + 0.5 * pull, flash: clash ? 1 - prog(f, 212, 12) : 0}}
        gd={{x: GD.x - pull * 60 + back * 60, lean: -pull * 4 + back * 4, glow: 0.5 + 0.5 * pull, cast: pull * 0.6, flash: clash ? 1 - prog(f, 212, 12) : 0}}
        ybGlow={prog(f, 150, 30)}
        seaLevel={0.82 - prog(f, 150, 30) * 0.08}
      >
        <Converge x={960} y={560} at={60} until={212} colors={[C.bronze, '#ffffff', '#c78bff', '#6dff8a', '#ff5fd2']} />
        <Shock x={960} y={600} at={212} r={900} color="#fff" dur={30} />
        <Flash at={212} dur={14} />
        <HUD at={20} flare={{yuanbao: flareAt(f, 150, 240), chaos: flareAt(f, 150, 240)}} />
        <RoundTitle no="第一回合" name="聚合" />
        <Sub
          lines={[
            {at: 70, text: '聚合是本能。祂们被拉向彼此。'},
            {at: 150, text: '各自的象征显现：灰雾之上的源堡，包容所有颜色的混沌海。'},
            {at: 230, text: '“一场异常激烈却动静不大的战斗”——这是克莱恩的推想。'},
          ]}
        />
      </Field>
    </Shake>
  );
};

/* ---------------- 第二回合：源堡 对 混沌海 ---------------- */

const R2: React.FC = () => {
  const f = useCurrentFrame();
  const gdCast = interpolate(f, [40, 60, 100, 120, 380, 395, 430, 450, 640, 655, 690, 710], [0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0], clamp);
  const tzCast = interpolate(f, [110, 125, 170, 190, 270, 285, 330, 350, 520, 535, 600, 620], [0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0], clamp);
  const gdCrack = f >= 196 ? 0.1 : 0;
  const tzCrack = f >= 700 ? 0.28 : f >= 440 ? 0.15 : 0;
  const hit = (at: number) => (f >= at && f < at + 12 ? 1 - (f - at) / 12 : 0);
  const confused = f >= 300 && f < 400 ? Math.sin(f * 0.25) * 6 : 0;
  return (
    <Shake hits={[[100, 12], [196, 26], [440, 22], [700, 26]]}>
      <Field
        tz={{x: TZ.x + tzCast * 40 - recoil(f, [440, 700]) * 90, lean: tzCast * 3 - recoil(f, [440, 700]) * 6, cast: tzCast, crack: tzCrack, flash: Math.max(hit(440), hit(700)), glow: 0.5 + 0.5 * tzCast}}
        gd={{x: GD.x - gdCast * 40 + recoil(f, [100, 196]) * 90, cast: gdCast, crack: gdCrack, flash: hit(196), glow: 0.5 + 0.5 * gdCast, lean: confused + recoil(f, [196]) * 6 - gdCast * 3}}
        ybGlow={flareAt(f, 115, 640)}
      >
        {/* 交锋一：风暴 → 门 */}
        <Glyph char="风" from={{x: 1300, y: 330}} at={45} hold={18} color="#8fc8ff" />
        <Storm from={{x: 1280, y: 520}} to={{x: 760, y: 560}} at={66} travel={60} until={160} />
        <Bolt x1={600} y1={0} x2={560} y2={600} at={100} />
        <Bolt x1={420} y1={0} x2={480} y2={560} at={112} />
        <Glyph char="门" from={{x: 640, y: 340}} at={115} hold={22} color="#b9a6ff" />
        <Doors x={760} y={560} at={124} until={186} />
        <Doors x={1660} y={520} at={168} until={214} n={5} r={140} />
        <Storm from={{x: 1660, y: 520}} to={{x: 1470, y: 470}} at={172} travel={18} until={212} />
        <Bolt x1={1660} y1={520} x2={1450} y2={460} at={186} />
        <Bolt x1={1700} y1={380} x2={1440} y2={500} at={192} />
        <Shock x={gdChest.x} y={gdChest.y} at={196} color="#b9a6ff" />
        <Invoke side="R" at={45} until={150} name="风暴 · 雷电" path="混沌海 · 水手途径（序列0：暴君）" color="#8fc8ff" />
        <Invoke side="L" at={115} until={230} name="门 · 空间" path="源堡 · 学徒途径（序列0：门）" color="#b9a6ff" />

        {/* 交锋二：愚弄 → 太阳 */}
        <Glyph char="愚" from={{x: 640, y: 340}} at={272} hold={22} color="#d8dbe2" />
        <FogVeil at={286} until={466} peak={0.82} />
        <Glyph char="日" from={{x: 1300, y: 330}} at={385} hold={18} color={C.sun} />
        <SunPillars xs={[330, 430, 530, 630]} at={410} dur={46} />
        <Shock x={470} y={560} at={440} color={C.sun} />
        <Flash at={440} dur={10} color={C.sun} peak={0.5} />
        <Invoke side="L" at={272} until={380} name="愚弄" path="源堡 · 占卜家途径（序列0：愚者）" color="#d8dbe2" />
        <Invoke side="R" at={385} until={505} name="太阳 · 净化" path="混沌海 · 歌颂者途径（序列0：太阳）" color={C.sun} />

        {/* 交锋三：窃取 → 心灵 */}
        <Glyph char="窃" from={{x: 640, y: 340}} at={525} hold={18} color={C.bronze} />
        <Theft from={{x: 540, y: 540}} to={gdHead} at={540} reach={40} pull={40} until={700} />
        <Glyph char="心" from={{x: 1300, y: 330}} at={645} hold={18} color="#ff7ad9" />
        <Phantoms at={655} until={745}>{(i) => <Tianzun x={TZ.x} y={TZ.y} h={TZ.h * 0.9} id={`ph${i}`} />}</Phantoms>
        <Shock x={540} y={540} at={700} color="#ff7ad9" r={520} />
        <Flash at={700} dur={12} color="#ff7ad9" peak={0.5} />
        <Invoke side="L" at={525} until={640} name="窃取" path="源堡 · 偷盗者途径（序列0：错误）" color={C.bronze} />
        <Invoke side="R" at={645} until={775} name="心灵 · 空想具现" path="混沌海 · 观众途径（序列0：空想家）" color="#ff7ad9" />

        <HUD flare={{yuanbao: Math.max(flareAt(f, 115, 230), flareAt(f, 272, 380), flareAt(f, 525, 640)), chaos: Math.max(flareAt(f, 45, 150), flareAt(f, 385, 505), flareAt(f, 645, 775))}} />
        <RoundTitle no="第二回合" name="源堡 对 混沌海" out={28} />
        <Sub
          lines={[
            {at: 50, text: '上帝先出手：风暴与雷电。'},
            {at: 122, text: '天尊开门——风暴进了门，'},
            {at: 172, text: '从上帝背后出来。'},
            {at: 280, text: '愚弄：灰雾吞没战场，连方向都是假的。'},
            {at: 392, text: '太阳：光柱落下，灰雾被烧穿。'},
            {at: 530, text: '窃取：从漩涡里偷出一块权柄。'},
            {at: 650, text: '心灵：偷到手的，是上帝让祂想象出来的东西——'},
            {at: 705, text: '在天尊手里炸开。'},
          ]}
        />
      </Field>
    </Shake>
  );
};

/* ---------------- 第三回合：额外的源质 ---------------- */

const R3: React.FC = () => {
  const f = useCurrentFrame();
  const dark = prog(f, 110, 60) * (1 - prog(f, 330, 40));
  const seaGray = prog(f, 170, 40) * (1 - prog(f, 300, 40));
  const tzCast = interpolate(f, [40, 55, 110, 130, 400, 415, 490, 505], [0, 1, 1, 0, 0, 1, 1, 0], clamp);
  const bound = f >= 440 && f < 625;
  const gdCast = bound ? 0 : interpolate(f, [250, 265, 300, 320, 560, 575, 650, 665], [0, 1, 1, 0, 0, 1, 1, 0], clamp);
  const gdCrack = f >= 500 ? 0.4 : f >= 230 ? 0.25 : 0.1;
  const tzCrack = f >= 690 ? 0.42 : 0.28;
  const hit = (at: number) => (f >= at && f < at + 12 ? 1 - (f - at) / 12 : 0);
  const skew = f >= 490 && f < 565 ? Math.sin((f - 490) * 0.3) * 10 * (1 - prog(f, 540, 25)) : 0;
  const drag = prog(f, 660, 50);
  return (
    <Shake hits={[[230, 18], [360, 34], [500, 20], [625, 22], [690, 18]]}>
      <Field
        tz={{x: TZ.x + tzCast * 40 - recoil(f, [360, 690]) * 90, cast: tzCast, crack: tzCrack, flash: hit(690), y: TZ.y + drag * 30, lean: -drag * 4 + tzCast * 3, glow: 0.5 + 0.5 * tzCast}}
        gd={{x: GD.x - gdCast * 40 + recoil(f, [230, 360, 500]) * 90, cast: gdCast, crack: gdCrack, flash: Math.max(hit(230), hit(500)), glow: 0.5 + 0.5 * gdCast, lean: recoil(f, [230, 500]) * 6}}
        seaGray={seaGray}
        dark={dark}
        gdWrap={{transform: `skewX(${skew}deg)`, transformOrigin: '1450px 600px'}}
      >
        {/* 交锋四：永暗之河 → 灾祸之城 */}
        <Glyph char="夜" from={{x: 360, y: 360}} at={50} hold={50} size={170} color="#7f95ff" />
        <Glyph char="暮" from={{x: 560, y: 300}} at={70} hold={40} size={170} color="#d9a35a" />
        <Glyph char="死" from={{x: 760, y: 360}} at={90} hold={30} size={170} color="#9fb0c0" />
        <River at={110} until={380} y={800} />
        <Glyph char="战" from={{x: 1260, y: 330}} at={262} hold={30} size={170} color={C.calamity} />
        <Glyph char="祸" from={{x: 1560, y: 300}} at={282} hold={24} size={170} color="#ff3a3a" />
        <FireCity at={280} until={400} />
        <Shock x={960} y={800} at={360} r={1000} color="#ffffff" dur={30} />
        <Flash at={360} dur={18} />
        <FogVeil at={360} until={430} peak={0.55} />
        <Invoke side="L" at={50} until={250} name="永暗之河" path="不眠者（黑暗）· 战士（黄昏巨人）· 收尸人（死神）" note="天尊第一纪容纳过——正文" color="#7f95ff" />
        <Invoke side="R" at={262} until={400} name="灾祸之城" path="猎人（红祭司）· 魔女" note="上帝容纳过它？——愚者的推测" color={C.calamity} />

        {/* 交锋五：失序之国 → 知识、堕落 */}
        <Glyph char="律" from={tzHand} to={gdChest} at={405} hold={14} fly={16} color="#e0546f" />
        <Chains x={gdChest.x} y={gdChest.y} at={420} until={660} snap={625} />
        <Glyph char="曲" from={tzHand} to={gdChest} at={478} hold={10} fly={14} color="#c48bff" />
        <Shock x={gdChest.x} y={gdChest.y} at={500} color="#c48bff" />
        <Glyph char="知" from={{x: 1300, y: 330}} at={565} hold={16} color="#f4f2ea" />
        <Eyes at={575} until={665} />
        <Shock x={gdChest.x} y={gdChest.y} at={625} color="#f4f2ea" r={360} />
        <Glyph char="堕" from={{x: 1300, y: 330}} at={655} hold={16} color="#9a5cff" />
        <Shadows x0={250} x1={700} at={662} until={720} />
        <Invoke side="L" at={405} until={560} name="失序之国" path="仲裁人（审判者）· 律师（黑皇帝）" note="天尊第一纪容纳过——正文" color="#e0546f" />
        <Invoke side="R" at={565} until={650} name="知识 · 解析" path="混沌海 · 阅读者途径（序列0：白塔）" color="#f4f2ea" />
        <Invoke side="R" at={655} until={720} name="堕落 · 阴影" path="混沌海 · 秘祈人途径（序列0：倒吊人）" color="#9a5cff" />

        <HUD
          flare={{
            river: flareAt(f, 50, 250),
            calamity: flareAt(f, 262, 400),
            disorder: flareAt(f, 405, 560),
            chaos: Math.max(flareAt(f, 565, 650), flareAt(f, 655, 720)),
          }}
        />
        <RoundTitle no="第三回合" name="额外的源质" out={32} />
        <Sub
          lines={[
            {at: 56, text: '天尊动用第一纪容纳过的源质：永暗之河。'},
            {at: 130, text: '黑暗，黄昏，死亡——连混沌海都开始凝固。'},
            {at: 268, text: '上帝那边升起灾祸之城。注意：祂容纳过它，只是愚者的推测。'},
            {at: 345, text: '长河撞上火城。'},
            {at: 410, text: '失序之国：律令锁住上帝，'},
            {at: 482, text: '再把祂那一片的秩序扭曲。'},
            {at: 570, text: '全知：每一条律令都被读穿，锁链崩断。'},
            {at: 660, text: '堕落：阴影把天尊往下拖。'},
          ]}
        />
      </Field>
    </Shake>
  );
};

/* ---------------- 第四回合：聚合失控，撕裂 ---------------- */

const R4: React.FC = () => {
  const f = useCurrentFrame();
  const close = prog(f, 30, 140);
  const blast = prog(f, 330, 26);
  const tzX = TZ.x + close * 290 - blast * 240;
  const gdX = GD.x - close * 290 + blast * 240;
  const crack = interpolate(f, [0, 260, 330], [0.42, 0.5, 0.78], clamp);
  const flick = f >= 260 && f < 330 && Math.floor(f / 3) % 2 === 0 ? 0.6 : 0;
  return (
    <Shake hits={[[270, 20], [300, 20], [330, 44]]} dur={22}>
      <Field tz={{x: tzX, crack, glow: 1, flash: Math.max(flick, f >= 330 ? 1 - prog(f, 330, 14) : 0)}} gd={{x: gdX, crack, glow: 1, cast: close, flash: Math.max(flick, f >= 330 ? 1 - prog(f, 330, 14) : 0)}} ybGlow={0.6}>
        <Converge x={960} y={560} at={40} until={330} colors={[C.bronze, '#7f95ff', '#e0546f', '#c78bff', C.calamity, '#ffffff']} />
        <Core x={960} y={560} at={120} until={336} r={80} />
        <Glyph char="错" from={{x: tzX + 40, y: 560}} to={{x: gdX - 40, y: 430}} at={262} hold={10} fly={18} size={190} color="#ff3a3a" />
        <Shock x={960} y={560} at={330} r={1100} color="#ffffff" dur={34} />
        <Flash at={330} dur={20} />
        <Label x={960} y={236} a="c" text="“源质和唯一性一样，不可分割，不能复制，永不损毁，" at={120} until={262} size={30} font={F.serif} w={700} bg />
        <Label x={960} y={284} a="c" text="只有一种情况能造成它们的撕裂，那就是遇上最初那位造物主时。”" at={128} until={262} size={30} font={F.serif} w={700} bg />
        <Label x={960} y={330} a="c" text="——愚者，《宿命之环》第七卷《序列之上》" at={140} until={262} size={18} color={C.dim} bg />
        <Label x={960} y={250} a="c" text="上帝从天尊身上撕下了“错误”？" at={272} until={400} size={34} font={F.serif} w={900} color="#ffb0b0" bg />
        <Label x={960} y={300} a="c" text="这是克莱恩的推断（《诡秘之主》第八部《大手笔》）" at={282} until={400} size={18} color={C.dim} bg />
        <HUD flare={{yuanbao: close, river: close, disorder: close, chaos: close, calamity: close}} />
        <RoundTitle no="第四回合" name="聚合失控" />
        <Sub
          lines={[
            {at: 40, text: '本能失控。黑夜女神说，“应该”是额外的源质害的。'},
            {at: 160, text: '可祂们本就是那一位的不同人格——越靠越近，开始撕扯彼此。'},
            {at: 268, text: '偷盗者途径的唯一性，被撕了下来——'},
            {at: 345, text: '两败俱伤。'},
          ]}
        />
      </Field>
    </Shake>
  );
};

/* ---------------- 陨落 ---------------- */

const SEALED = ['母巢', '失序之国', '暗影世界', '灾祸之城', '永暗之河', '知识荒野', '光之钥'];
const LAND = 'M40 900 C 80 840 180 820 260 836 C 340 800 470 812 540 850 C 600 880 610 950 560 990 C 480 1040 300 1050 180 1030 C 90 1016 20 970 40 900 Z';

const Fall: React.FC = () => {
  const f = useCurrentFrame();
  const tzFade = prog(f, 300, 130);
  const gdSink = prog(f, 310, 130);
  const tablet = prog(f, 400, 30);
  const src = {x: 520, y: 560};
  return (
    <Field
      tz={{x: 520, crack: 0.78, glow: 0.8 + 0.2 * Math.sin(f * 0.2), fade: tzFade}}
      gd={{x: 1400, crack: 0.78, glow: 0.4, y: GD.y + gdSink * 240, fade: gdSink * 0.95}}
      ybGlow={0.3 * (1 - tzFade)}
      ybFade={tzFade}
      seaLevel={0.82 - gdSink * 0.06}
      dark={0.3}
    >
      {/* 西大陆：左下角的一块陆地 */}
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: prog(f, 80, 30) * (1 - prog(f, 470, 40))}}>
        <path d={LAND} fill="rgba(30,34,40,0.85)" stroke={C.bronze} strokeWidth={2} style={{filter: `drop-shadow(0 0 16px ${C.bronze})`}} />
      </svg>
      <Label x={300} y={800} a="c" text="西大陆 · 蓬莱看守" at={90} until={480} size={24} font={F.serif} w={700} color={C.bronze} glow={C.bronze} />
      {SEALED.map((name, i) => {
        const at = 110 + i * 22;
        const k = prog(f, at, 36);
        if (f < at) return null;
        const tx = 110 + (i % 4) * 130 + (i >= 4 ? 60 : 0);
        const ty = 900 + Math.floor(i / 4) * 80;
        const x = src.x + (tx - src.x) * k;
        const y = src.y + (ty - src.y) * k - Math.sin(Math.PI * k) * 200;
        const landed = k >= 1;
        const o = 1 - prog(f, 470, 40);
        return (
          <React.Fragment key={name}>
            <div style={{position: 'absolute', left: x, top: y, width: 22, height: 22, borderRadius: '50%', transform: 'translate(-50%, -50%)', background: '#fff', boxShadow: `0 0 24px 6px ${C.bronze}`, opacity: o}} />
            {landed && <Label x={tx} y={ty + 26} a="c" text={name} at={at + 36} until={480} size={18} w={700} />}
          </React.Fragment>
        );
      })}
      <Label x={960} y={250} a="c" text="陨落之前，天尊把七份源质封印到了西大陆" at={70} until={300} size={34} font={F.serif} w={700} bg />
      <Label x={960} y={300} a="c" text="——愚者，《宿命之环》第七卷《序列之上》" at={80} until={300} size={18} color={C.dim} bg />
      {/* 第一块亵渎石板 */}
      <svg width={1920} height={1080} style={{position: 'absolute', inset: 0, opacity: tablet}}>
        <g transform={`translate(1400 ${900 - tablet * 40})`} style={{filter: 'drop-shadow(0 0 20px #c78bff)'}}>
          <path d="M-70 0 L -70 -150 Q 0 -200 70 -150 L 70 0 Z" fill="#2b2a30" stroke="#c78bff" strokeWidth={2} />
          {[0, 1, 2, 3, 4].map((i) => (
            <line key={i} x1={-48} y1={-130 + i * 26} x2={48 - (i % 2) * 20} y2={-130 + i * 26} stroke="#e8d8ff" strokeWidth={2} opacity={0.6} />
          ))}
        </g>
      </svg>
      <Label x={1400} y={700} a="c" text="第一块亵渎石板" at={410} until={540} size={28} font={F.serif} w={700} glow="#c78bff" />
      <Label x={1400} y={740} a="c" text="上帝的尸骸在混沌海中形成——黑夜女神" at={420} until={540} size={17} color={C.dim} />
      <Title x={960} y={480} text="近乎同归于尽" at={460} size={96} color={C.ink} glow="rgba(255,255,255,0.4)" />
      <Label x={960} y={560} a="c" text="——愚者，《宿命之环》第七卷《序列之上》" at={474} size={18} color={C.dim} />
      <RoundTitle no="终局" name="陨落" />
      <Sub
        lines={[
          {at: 90, text: '陨落之前，天尊封印了七份源质，交给蓬莱看守。'},
          {at: 300, text: '然后，灰雾散了，混沌海也平静了。'},
          {at: 405, text: '上帝的尸骸，在混沌海里成了第一块亵渎石板。'},
        ]}
        end={455}
      />
    </Field>
  );
};

export const SCENES_FIGHT: Record<string, React.FC> = {r1: R1, r2: R2, r3: R3, r4: R4, fall: Fall};
