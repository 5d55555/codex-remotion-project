import React from 'react';
import {useCurrentFrame} from 'remotion';
import {Arrow, Badge, C, ChapterCard, circ, Dot, F, Figure, LogoCard, P, pol, Person, prog, RingIcon, Svg, Txt} from '../kit';

export const TOTAL = 22;

export const Logo: React.FC = () => <LogoCard question="一支舞，凭什么撬动命运？" />;

export const Ch1: React.FC = () => <ChapterCard glyphs="恩赐" no="第一章" name="恩赐" line1="二十二条途径之外的路" foot="宿命之环 · 旧日支配者" />;

/* 图01 宿命之环：过去·现在·未来 / 原因·过程·结果 */
export const F01: React.FC = () => {
  const f = useCurrentFrame();
  const cx = 1290;
  const cy = 525;
  const outer = [
    {deg: -90, name: '现在'},
    {deg: 30, name: '未来'},
    {deg: 150, name: '过去'},
  ];
  const inner = [
    {deg: 90, name: '原因'},
    {deg: 210, name: '过程'},
    {deg: 330, name: '结果'},
  ];
  const orbit = pol(cx, cy, 250, -90 + f * 1.1);
  const vt = (col: string, x: number, at: number) =>
    [...col].map((ch, i) => <Txt key={x + i} x={x} y={190 + i * 44} a="c" text={ch} at={at + i * 4} size={32} font={F.serif} w={900} />);
  return (
    <Figure
      theme="navy"
      rays={{cx, cy}}
      chrome={{section: '旧日', value: '旧日 · 宿命之环', node: 10, prev: 0, fig: 1, total: TOTAL}}
      tag={['旧日', '外神', '宿命之环']}
      title="宿命之环"
      sub="旧日支配者，在序列之上"
    >
      <Svg>
        <P d={circ(cx, cy, 250)} at={14} dur={40} w={2.5} />
        <g transform={`rotate(${f * 0.12} ${cx} ${cy})`}>
          <P d={circ(cx, cy, 285)} at={40} dash="3 12" w={2} />
        </g>
        <P d={circ(cx, cy, 150)} at={26} dur={34} w={1.5} />
        {Array.from({length: 12}, (_, i) => {
          const [x1, y1] = pol(cx, cy, 232, i * 30);
          const [x2, y2] = pol(cx, cy, 250, i * 30);
          return <P key={i} d={`M${x1} ${y1} L${x2} ${y2}`} at={50 + i * 2} dur={8} w={i % 3 === 0 ? 3 : 1.5} />;
        })}
        {inner.map((n, i) => {
          const [x1, y1] = pol(cx, cy, 150, n.deg);
          const [x2, y2] = pol(cx, cy, 150, inner[(i + 1) % 3].deg);
          return <P key={n.name} d={`M${x1} ${y1} L${x2} ${y2}`} at={96 + i * 10} dur={20} c={C.gold} w={2} />;
        })}
        {outer.map((n, i) => {
          const [x, y] = pol(cx, cy, 250, n.deg);
          return <Dot key={n.name} x={x} y={y} r={10} at={66 + i * 8} c={C.cream} />;
        })}
        {inner.map((n, i) => {
          const [x, y] = pol(cx, cy, 150, n.deg);
          return <Dot key={n.name} x={x} y={y} r={7} at={90 + i * 8} c={C.gold} />;
        })}
        <P d={circ(cx, cy, 42)} at={30} dur={24} c={C.gold} w={3} />
        <Dot x={cx} y={cy} r={8} at={52} c={C.gold} />
        {f > 70 && <circle cx={orbit[0]} cy={orbit[1]} r={7} fill={C.gold} opacity={prog(f, 70, 20)} />}
        <P d={`M${pol(cx, cy, 285, 38).join(' ')} L1600 770 L1640 770`} at={150} dur={18} w={1.5} />
      </Svg>
      {outer.map((n, i) => {
        const [x, y] = pol(cx, cy, 300, n.deg);
        return <Txt key={n.name} x={x} y={y} a="c" text={n.name} at={70 + i * 8} size={32} font={F.serif} w={900} />;
      })}
      {inner.map((n, i) => {
        const [x, y] = pol(cx, cy, 104, n.deg);
        return <Txt key={n.name} x={x} y={y} a="c" text={n.name} at={94 + i * 8} size={22} w={700} color={C.gold} />;
      })}
      <Txt x={1650} y={752} text="尊名意象" at={160} size={18} color="#968f7c" />
      <Txt x={1650} y={788} text="永恒轮回 · 注定命运" at={166} size={26} font={F.serif} w={900} />
      {vt('轮回与宿命', 1800, 120)}
      <Badge x={1290} y={880} a="c" at={180} kind="正文" src="第二卷第41章《声音》 · 称其为旧日支配者" />
    </Figure>
  );
};

/* 图02 恩赐途径：神之途径 vs 被赐予的路 */
export const F02: React.FC = () => {
  const f = useCurrentFrame();
  const squares = Array.from({length: 22}, (_, i) => ({x: 1010 + (i % 11) * 52, y: 262 + Math.floor(i / 11) * 52}));
  return (
    <Figure chrome={{section: '恩赐', value: '恩赐途径', node: 10, prev: 10, fig: 2, total: TOTAL}} tag={['恩赐', '外神', '第一卷第60章']} title="恩赐途径" sub="不是喝下魔药，是被赐予">
      <Svg>
        {squares.map((s, i) => (
          <P key={i} d={`M${s.x} ${s.y} h38 v38 h-38 Z`} at={14 + i * 1.5} dur={12} w={2} />
        ))}
        <P d="M1000 410 L1584 410" at={70} dash="6 8" w={1.5} />
        <RingIcon cx={1100} cy={575} r={62} at={84} />
        <Person x={1480} y={600} at={92} pose="up" s={0.9} />
        <Arrow pts={[[1446, 520], [1300, 470], [1176, 528]]} at={118} dur={26} c="#7b7366" w={2.5} dash="8 8" />
        <Arrow pts={[[1172, 628], [1300, 690], [1440, 640]]} at={140} dur={26} c={C.red} w={3} />
      </Svg>
      <Txt x={1010} y={224} text="神之途径 · 22 条" at={10} size={28} font={F.serif} w={900} />
      <Txt x={1010} y={370} text="服食魔药  →  消化  →  晋升" at={40} size={20} color="#4b453b" />
      <Txt x={1100} y={672} a="c" text="外神 / 旧日" at={100} size={20} color="#4b453b" />
      <Txt x={1480} y={680} a="c" text="受赐者" at={104} size={20} color="#4b453b" />
      <Txt x={1300} y={456} a="c" text="祈求" at={128} size={24} font={F.serif} w={900} color="#4b453b" />
      <Txt x={1300} y={712} a="c" text="赐予力量" at={152} size={24} font={F.serif} w={900} color={C.red} />
      <Txt x={1010} y={772} text="同样按序列9—0划分，方便对应与参考" at={176} size={20} color="#4b453b" />
      <div style={{position: 'absolute', left: 1006, top: 812, width: 6, height: 70, background: C.red, opacity: prog(f, 196, 12)}} />
      <Txt x={1030} y={830} text="恩赐的本质，更接近受控制的、可以回收的污染" at={200} size={28} font={F.serif} w={900} stagger={1.2} />
      <Txt x={1030} y={868} text="第1040章《诱惑之泉》 · 正文概括" at={236} size={17} color="#7b7366" fade />
    </Figure>
  );
};

/* 图03 卢米安：猎人途径 + 舞蹈家恩赐 */
export const F03: React.FC = () => {
  const L = [
    {y: 660, n: '9', name: '猎人'},
    {y: 510, n: '8', name: '挑衅者'},
    {y: 360, n: '7', name: '纵火家'},
  ];
  const R = [
    {y: 660, n: '9', name: '舞蹈家'},
    {y: 510, n: '8', name: '托钵僧侣'},
    {y: 360, n: '7', name: '受契之人'},
  ];
  return (
    <Figure chrome={{section: '恩赐', value: '卢米安 · 第三卷时', node: 2, prev: 10, fig: 3, total: TOTAL}} tag={['宿命之环', '主角', '卢米安·李']} title="卢米安·李" sub="猎人之外，还有一支舞">
      <Svg>
        <P d="M1100 700 L1100 330" at={14} dur={30} w={3} />
        <P d="M1480 700 L1480 330" at={20} dur={30} w={3} c={C.red} />
        {L.map((s, i) => (
          <Dot key={s.n} x={1100} y={s.y} r={9} at={40 + i * 10} />
        ))}
        {R.map((s, i) => (
          <Dot key={s.n} x={1480} y={s.y} r={9} at={46 + i * 10} c={C.red} />
        ))}
        <Person x={1290} y={790} at={30} pose="stand" s={0.75} />
        <P d="M1267 763 Q1160 760 1100 700" at={80} dur={16} w={2} />
        <P d="M1313 763 Q1420 760 1480 700" at={86} dur={16} w={2} c={C.red} />
        <P d="M1100 330 L1100 305 L1480 305 L1480 330" at={150} dur={24} c={C.red} w={2} />
      </Svg>
      <Txt x={1100} y={232} a="c" text="猎人途径" at={18} size={28} font={F.serif} w={900} />
      <Txt x={1480} y={232} a="c" text="宿命之环 · 恩赐" at={24} size={28} font={F.serif} w={900} color={C.red} />
      {L.map((s, i) => (
        <Txt key={s.n} x={1076} y={s.y} a="r" text={`${s.n}  ${s.name}`} at={44 + i * 10} size={24} font={F.serif} w={900} />
      ))}
      {R.map((s, i) => (
        <Txt key={s.n} x={1504} y={s.y} text={`${s.n}  ${s.name}`} at={50 + i * 10} size={24} font={F.serif} w={900} />
      ))}
      <Txt x={1290} y={280} a="c" text="双序列7" at={166} size={26} font={F.serif} w={900} color={C.red} />
      <Txt x={1290} y={880} a="c" text="他向愚者先生与体内污染祈求，而不是直接向宿命之环" at={190} size={21} color="#4b453b" stagger={1} />
      <Badge x={1290} y={924} a="c" at={220} kind="正文" src="第三卷第73章 · 此阶段为双序列7" />
    </Figure>
  );
};

/* 图04 十个序列：梯子 + 层级 */
export const F04: React.FC = () => {
  const f = useCurrentFrame();
  const names = ['舞蹈家', '托钵僧侣', '受契之人', '苦修士', '猎命师', '环中人', '受难者', '罪人', '救赎天使', '永劫者'];
  const y = (i: number) => 820 - i * 64;
  const tiers = [
    {from: 0, to: 1, name: '低序列'},
    {from: 2, to: 4, name: '中序列'},
    {from: 5, to: 6, name: '圣者'},
    {from: 7, to: 8, name: '天使'},
    {from: 9, to: 9, name: '真神'},
  ];
  const climb = prog(f, 120, 90);
  const cy = y(0) - (y(0) - y(9)) * climb;
  return (
    <Figure chrome={{section: '序列', value: '9 → 0', node: 0, prev: 2, fig: 4, total: TOTAL}} tag={['序列', '9 → 0', '宿命之环途径']} title="十个序列" sub="从舞蹈家，到永劫者">
      <Svg>
        <P d={`M1180 ${y(0) + 40} L1180 ${y(9) - 40}`} at={10} dur={30} w={3} />
        <P d={`M1290 ${y(0) + 40} L1290 ${y(9) - 40}`} at={14} dur={30} w={3} />
        {names.map((_, i) => (
          <P key={i} d={`M1180 ${y(i)} L1290 ${y(i)}`} at={30 + i * 5} dur={10} w={i === 0 ? 4 : 2} c={i === 0 ? C.red : undefined} />
        ))}
        {tiers.map((t, i) => (
          <P key={t.name} d={`M1150 ${y(t.from) + 16} L1140 ${y(t.from) + 16} L1140 ${y(t.to) - 16} L1150 ${y(t.to) - 16}`} at={90 + i * 6} dur={12} w={2} c="#7b7366" />
        ))}
        <P d={`M1235 ${y(9) - 40} L1235 ${y(9) - 100}`} at={100} dash="4 8" w={2} />
        {f > 120 && <P d={`M1300 ${y(0)} L1300 ${cy}`} at={120} dur={1} c={C.red} w={4} />}
        {f > 120 && <circle cx={1300} cy={cy} r={9} fill={C.red} />}
      </Svg>
      {names.map((n, i) => (
        <React.Fragment key={n}>
          <Txt x={1322} y={y(i)} text={`序列${9 - i}`} at={34 + i * 5} size={17} color="#7b7366" />
          <Txt x={1410} y={y(i)} text={n} at={36 + i * 5} size={27} font={F.serif} w={900} color={i === 0 ? C.red : undefined} />
        </React.Fragment>
      ))}
      {tiers.map((t, i) => (
        <Txt key={t.name} x={1124} y={(y(t.from) + y(t.to)) / 2} a="r" text={t.name} at={94 + i * 6} size={21} w={700} color="#4b453b" />
      ))}
      <Txt x={1235} y={y(9) - 124} a="c" text="旧日 · 宿命之环" at={106} size={22} font={F.serif} w={900} />
    </Figure>
  );
};
