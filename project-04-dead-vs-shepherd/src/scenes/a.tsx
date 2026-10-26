import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {Arena, Caption, Flash, H, HF, Label, Radar, Shake, Skill, Slam, Typer} from '../hud';
import {prog} from '../kit';
import {PixelChar, PixelSheep} from '../pixel';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const AXES = ['伤害', '控制', '防御', '机动', '续航', '情报'];

/* 冷开场：两行打字 */
const Cold: React.FC = () => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill style={{backgroundColor: '#000'}}>
      <Typer x={300} y={470} text="一个靠“说出命运”打架的，" at={10} size={62} cps={2} color={H.dead} cursor={f < 80} />
      <Typer x={300} y={576} text="撞上一个放牧了七个灵魂的。" at={82} size={62} cps={2} color={H.shep} />
      <Flash at={170} dur={10} />
    </AbsoluteFill>
  );
};

/* 标题：两个像素主角走进擂台，名字砸下来 */
const Title: React.FC = () => {
  const f = useCurrentFrame();
  const walkL = interpolate(f, [0, 36], [-200, 470], clamp);
  const walkR = interpolate(f, [0, 36], [2120, 1450], clamp);
  return (
    <AbsoluteFill>
      <Shake at={14} amp={22}>
        <Shake at={48} amp={16}>
          <Arena left={1} right={1} split={prog(f, 0, 10)} speed={2} />
          <PixelChar kind="dead" x={walkL} y={930} cell={20} walk={f < 36} />
          <PixelChar kind="shep" x={walkR} y={930} cell={20} flip walk={f < 36} />
          <Slam x={560} y={430} text="逝者" at={14} size={210} color={H.dead} />
          <Slam x={1370} y={470} text="牧羊人" at={30} size={186} color={H.shep} />
          <Slam x={960} y={450} text="VS" at={48} size={150} color={H.hit} rot={-6} />
          <Label x={960} y={170} a="c" text="序列5 擂台 · 第一场" at={60} size={26} color={H.dim} ls={0.3} />
        </Shake>
      </Shake>
      <Flash at={0} dur={8} />
    </AbsoluteFill>
  );
};

/* 选人卡：逝者 */
const P1: React.FC = () => (
  <AbsoluteFill>
    <Arena left={1} right={0.12} />
    <PixelChar kind="dead" x={400} y={860} cell={26} at={4} />
    <Slam x={110} y={170} a="l" text="逝者" at={6} size={110} color={H.dead} />
    <Label x={120} y={262} text="命运女神恩赐途径 · 序列5 · 魔药打满" at={16} size={24} color={H.dim} />
    <Skill x={760} y={240} at={40} glyph="死" name="假死" desc="完成假死，脱离原本命运，摆脱前几级的限制" color={H.dead} />
    <Skill x={760} y={380} at={58} glyph="窥" name="窥命" desc="不借助媒介，看见或听见命运启示" color={H.dead} />
    <Skill x={760} y={520} at={76} glyph="言" name="说出命运" desc="把看到的命运说出口，直接影响对手（腓力曾借此让安东尼陷入恐惧）" color={H.dead} />
    <Skill x={760} y={660} at={94} glyph="体" name="身体" desc="未见任何身体加成记载" color={H.dead} locked />
    <Radar cx={1610} cy={540} r={160} axes={AXES} values={[1, 4, 1, 2, 2, 5]} color={H.dead} at={120} />
    <Label x={1610} y={790} a="c" text="雷达：我的判断，不是原作数值" at={140} size={18} color={H.dim} />
    <Caption
      lines={[
        {at: 160, text: '完成假死以后：乐师不用久等，哑巴不用久哑。'},
        {at: 270, text: '有控制，有情报——就是没有伤害。'},
      ]}
    />
    <Flash at={0} dur={6} />
  </AbsoluteFill>
);

/* 选人卡：牧羊人 */
const P2: React.FC = () => (
  <AbsoluteFill>
    <Arena left={0.12} right={1} />
    <PixelChar kind="shep" x={1520} y={860} cell={26} flip at={4} />
    <PixelSheep x={1240} y={900} at={110} cell={11} />
    <PixelSheep x={1780} y={910} at={118} cell={11} />
    <Label x={1830} y={786} a="r" text="× 7" at={126} size={34} color={H.shep} font={HF.mono} />
    <Slam x={1810} y={170} a="r" text="牧羊人" at={6} size={110} color={H.shep} />
    <Label x={1800} y={262} a="r" text="秘祈人途径 · 序列5 · 魔药打满" at={16} size={24} color={H.dim} />
    <Radar cx={300} cy={540} r={150} axes={AXES} values={[5, 4, 5, 4, 6, 3]} color={H.shep} at={120} />
    <Label x={300} y={780} a="c" text="雷达：我的判断，不是原作数值" at={140} size={18} color={H.dim} />
    <Skill x={560} y={240} at={40} glyph="牧" name="放牧" desc="最多七个灵魂，一次驱使一个，瞬间切换" color={H.shep} />
    <Skill x={560} y={380} at={58} glyph="三" name="每魂三技" desc="初次放牧时选定三种能力，之后不能改" color={H.shep} />
    <Skill x={560} y={520} at={76} glyph="影" name="阴影" desc="藏入阴影、化为阴影" color={H.shep} />
    <Skill x={560} y={660} at={94} glyph="血" name="血肉魔法" desc="血肉化、藏进别人体内、血肉炸弹、自我修复" color={H.shep} />
    <Caption
      lines={[
        {at: 160, text: '七只羊轮流上场，换羊不用一秒。'},
        {at: 270, text: '额头中枪，血肉照样长回来。'},
      ]}
    />
    <Flash at={0} dur={6} />
  </AbsoluteFill>
);

/* 战绩档案：只列正文里有的 */
type Row = {chip: string; color: string; text: string; src: string};
const Record: React.FC = () => {
  const f = useCurrentFrame();
  const left: Row[] = [
    {chip: '压制', color: H.dead, text: '“说出命运”，令安东尼陷入恐惧、失去行动力', src: '宿命之环 第三卷《灾祸巨人》'},
    {chip: '被破', color: H.hit, text: '安东尼克服恐惧，以“狂乱”反击', src: '宿命之环 第三卷《狂乱》'},
    {chip: '身亡', color: H.hit, text: '遭卢米安、简娜、安东尼连续压制，最终身亡', src: '第三卷《命运的嘲弄》· 第四卷《分配》'},
  ];
  const right: Row[] = [
    {chip: '击杀', color: H.shep, text: 'A先生击杀贝克朗（猎人途径序列6）', src: '诡秘之主 第二卷《刺杀》'},
    {chip: '压制', color: H.shep, text: 'A先生压制序列6的克莱恩，对方毫无还手之力', src: '诡秘之主 第二卷《“0”级封印物在行动》'},
    {chip: '合击', color: H.shep, text: 'K先生近战压制疯女，与卢米安合力将其击杀', src: '宿命之环 第四卷《巢内之物》'},
    {chip: '未找到', color: '#666', text: '同级、正常状态、正面单挑落败的记录', src: '核查结论：没找到 ≠ 没输过'},
  ];
  const row = (r: Row, i: number, x: number, at: number) => {
    const p = prog(f, at, 12);
    return (
      <div key={r.text} style={{position: 'absolute', left: x, top: 330 + i * 140, width: 760, opacity: p, transform: `translateY(${(1 - p) * 20}px)`, display: 'flex', gap: 18}}>
        <div style={{flex: 'none', alignSelf: 'flex-start', background: r.color, color: '#0d0e12', fontFamily: HF.heavy, fontWeight: 900, fontSize: 22, lineHeight: '24px', padding: '6px 12px', transform: 'skewX(-10deg)'}}>{r.chip}</div>
        <div>
          <div style={{fontFamily: HF.heavy, fontWeight: 700, fontSize: 26, color: H.ink, lineHeight: 1.35}}>{r.text}</div>
          <div style={{fontFamily: HF.heavy, fontWeight: 500, fontSize: 17, color: H.dim, marginTop: 6}}>{r.src}</div>
        </div>
      </div>
    );
  };
  return (
    <AbsoluteFill>
      <Arena left={0.7} right={0.7} split={0.5} speed={0.5} />
      <Slam x={960} y={120} text="战绩档案" at={4} size={88} color={H.ink} />
      <Label x={120} y={246} text="逝者 · 腓力将军（双序列5：收割者＋逝者）" at={16} size={28} color={H.dead} />
      <Label x={1020} y={246} text="牧羊人 · A先生 / K先生" at={22} size={28} color={H.shep} />
      {left.map((r, i) => row(r, i, 120, 40 + i * 24))}
      {right.map((r, i) => row(r, i, 1020, 52 + i * 24))}
      <Label x={960} y={900} a="c" text="注意：腓力身兼猎人途径，不是纯粹的逝者样本；A先生压制克莱恩时，对方低一级" at={180} size={21} color={H.dim} />
      <Flash at={0} dur={6} />
    </AbsoluteFill>
  );
};

export const SCENES_A: Record<string, React.FC> = {cold: Cold, title: Title, p1: P1, p2: P2, record: Record};
