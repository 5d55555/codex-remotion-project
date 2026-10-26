import React from 'react';
import {useCurrentFrame} from 'remotion';
import {Badge, C, ChapterCard, Dot, F, Figure, P, Person, prog, Svg, Txt} from '../kit';
import {SeqFigure, Thread, TOTAL} from '../parts';

const WIKI = {kind: '整理' as const, src: '诡秘之主 Wiki「命运女神途径」'};

const Ch2: React.FC = () => <ChapterCard glyphs="孙子" no="第二章" name="孙子辈" line1="序列9 → 序列3" foot="没有数值，没有拳头" />;

/* 图04 数值面板：战士序列9 vs 命运女神序列9—5 */
const F04: React.FC = () => {
  const f = useCurrentFrame();
  const cols = ['力量', '敏捷', '格斗', '直接攻击'];
  const colX = [1330, 1460, 1590, 1740];
  const rows = [
    {name: '失梦人', seq: 9},
    {name: '乐师', seq: 8},
    {name: '窥命师', seq: 7},
    {name: '哑巴', seq: 6},
    {name: '逝者', seq: 5},
  ];
  const bar = prog(f, 60, 30);
  return (
    <Figure chrome={{section: '孙子', value: '9—5 · 数值', node: 4, prev: 7, fig: 4, total: TOTAL}} tag={['孙子辈', '数值', '序列9—5']} title="数值：零" sub="序列9到5，一点没加">
      <Svg>
        <P d="M1000 312 L1840 312" at={10} dur={20} w={2} />
        <P d="M1000 392 L1840 392" at={50} dur={20} w={1.2} op={0.5} />
        {rows.map((_, i) => (
          <P key={i} d={`M1000 ${452 + i * 62} L1840 ${452 + i * 62}`} at={80 + i * 6} dur={16} w={1} op={0.35} />
        ))}
        {[0, 1, 2].map((i) => (
          <rect key={i} x={colX[i] - 48} y={340} width={96 * bar} height={22} fill={C.red} opacity={0.85} />
        ))}
      </Svg>
      {cols.map((c, i) => (
        <Txt key={c} x={colX[i]} y={286} a="c" text={c} at={12 + i * 4} size={22} w={700} color={C.soft} />
      ))}
      <Txt x={1020} y={352} text="战士" at={30} size={26} font={F.serif} w={900} color={C.red} />
      <Txt x={1100} y={352} text="战士途径 序列9" at={36} size={16} color={C.gray} />
      <Txt x={colX[3]} y={352} a="c" text="✓" at={70} size={28} font={F.serif} w={900} color={C.red} fade />
      {rows.map((r, i) => (
        <React.Fragment key={r.name}>
          <Txt x={1020} y={422 + i * 62} text={r.name} at={90 + i * 8} size={24} font={F.serif} w={900} />
          <Txt x={1130} y={422 + i * 62} text={`序列${r.seq}`} at={94 + i * 8} size={16} color={C.gray} />
          {colX.map((x, j) => (
            <Txt key={j} x={x} y={422 + i * 62} a="c" text="—" at={110 + i * 8 + j * 2} size={24} color={C.gray} fade />
          ))}
        </React.Fragment>
      ))}
      <Txt x={1000} y={760} text="资料没写，不等于绝对没有；但序列9—5的条目里，一项身体加成都没有列。" at={170} size={19} color={C.soft} stagger={0.5} />
      <Badge x={1000} y={812} at={200} kind="整理" src="诡秘之主 Wiki「命运女神途径」「战士途径」" />
    </Figure>
  );
};

/* 序列9 失梦人：闭上的眼，碎掉的梦 */
const F05: React.FC = () => (
  <SeqFigure seq={9} name="失梦人" sub="能感觉命运在动，然后呢？" rank="孙子" power={1} fig={5} abilities={['失去做梦的能力', '不再被强制拉入梦境', '初步感应命运的流动']} source={WIKI}>
    <Svg>
      <P d="M1040 300 Q1040 260 1080 262 Q1100 230 1140 244 Q1180 226 1200 262 Q1236 266 1226 300 Z" at={10} dash="5 7" w={2.5} />
      <P d="M1120 250 L1132 276 L1118 290 L1134 312" at={50} dur={12} c={C.red} w={3} />
      <P d="M1020 430 Q1130 500 1240 430" at={20} dur={24} w={3.5} />
      {[0, 1, 2, 3, 4].map((i) => (
        <P key={i} d={`M${1060 + i * 35} ${450 + (i === 2 ? 14 : i % 2 ? 10 : 4)} l${(i - 2) * 4} 18`} at={40 + i * 3} dur={6} w={2.5} />
      ))}
      <Thread x1={990} x2={1290} y={570} amp={10} at={70} />
      <Thread x1={990} x2={1290} y={610} amp={8} phase={1.5} at={80} />
      <Thread x1={990} x2={1290} y={650} amp={12} phase={3} at={90} />
    </Svg>
    <Txt x={1130} y={700} a="c" text="命运在流动" at={110} size={20} font={F.serif} w={900} color={C.gray} />
  </SeqFigure>
);

/* 序列8 乐师：七弦琴与沙漏 */
const F06: React.FC = () => {
  const f = useCurrentFrame();
  const strings = [0, 1, 2, 3, 4, 5].map((i) => {
    const x = 1088 + i * 17;
    const amp = f > 60 ? Math.sin(f * 0.5 + i) * 3 * prog(f, 60, 20) : 0;
    return `M${x} 322 Q${x + amp} 470 ${x} 620`;
  });
  return (
    <SeqFigure
      seq={8}
      name="乐师"
      sub="一曲弹完，人早跑了"
      rank="孙子"
      power={1}
      fig={6}
      abilities={['倾听命运的声音', '大量准备，长时间演奏', '复述命运之声，影响目标命运', '有人刺瞎双眼，专心倾听']}
      source={WIKI}
      note={{text: '贴吧评价：准备时间长，不便实战，缺乏体质加强', kind: '整理', src: '百度贴吧讨论（读者观点）'}}
    >
      <Svg>
        <P d="M1062 640 C1020 520 1040 380 1080 300" at={10} dur={20} w={3.5} />
        <P d="M1196 640 C1240 520 1220 380 1180 300" at={14} dur={20} w={3.5} />
        <P d="M1070 312 L1190 312" at={30} dur={10} w={3.5} />
        <P d="M1060 636 L1200 636" at={34} dur={10} w={3.5} />
        {f > 40 && strings.map((d, i) => <path key={i} d={d} stroke={C.ink} strokeWidth={1.5} fill="none" opacity={prog(f, 40 + i * 3, 10)} />)}
        <P d="M1250 560 L1310 560 L1280 600 L1310 640 L1250 640 L1280 600 Z" at={70} dur={20} w={2.5} c={C.red} />
      </Svg>
      <Txt x={1280} y={676} a="c" text="很久" at={90} size={18} font={F.serif} w={900} color={C.red} />
    </SeqFigure>
  );
};

/* 序列7 窥命师：睁开的眼 */
const F07: React.FC = () => {
  const f = useCurrentFrame();
  const ix = 1130 + Math.sin(f * 0.05) * 18;
  return (
    <SeqFigure seq={7} name="窥命师" sub="看得见，打不着" rank="孙子" power={1} fig={7} abilities={['不借助任何媒介', '直接看见命运启示', '直接听见命运启示']} source={WIKI}>
      <Svg>
        <Thread x1={990} x2={1290} y={330} amp={8} at={60} />
        <Thread x1={990} x2={1290} y={610} amp={8} phase={2} at={66} />
        <P d="M1010 470 Q1130 370 1250 470 Q1130 570 1010 470 Z" at={10} dur={26} w={3.5} />
        {f > 30 && <circle cx={ix} cy={470} r={42} fill="none" stroke={C.ink} strokeWidth={3} opacity={prog(f, 30, 12)} />}
        {f > 36 && <circle cx={ix} cy={470} r={14} fill={C.red} opacity={prog(f, 36, 12)} />}
      </Svg>
      <Txt x={1130} y={680} a="c" text="命运启示" at={90} size={20} font={F.serif} w={900} color={C.gray} />
    </SeqFigure>
  );
};

/* 序列6 哑巴：说出口，然后封口 */
const F08: React.FC = () => (
  <SeqFigure
    seq={6}
    name="哑巴"
    sub="说一句，闭嘴好几天"
    rank="孙子"
    power={1}
    fig={8}
    abilities={['把命运启示说出来', '说出口，就能影响目标', '说完必须长时间禁言', '否则遭到命运反噬']}
    source={WIKI}
  >
    <Svg>
      <P d="M1010 330 L1170 330 L1170 400 L1080 400 L1060 430 L1062 400 L1010 400 Z" at={10} dur={24} w={2.5} />
      <P d="M1030 500 Q1080 460 1130 485 Q1180 460 1230 500" at={30} dur={18} w={3.5} />
      <P d="M1030 500 Q1130 570 1230 500" at={40} dur={18} w={3.5} />
      <P d="M1050 470 L1210 540" at={100} dur={10} c={C.red} w={6} />
      <P d="M1210 470 L1050 540" at={108} dur={10} c={C.red} w={6} />
    </Svg>
    <Txt x={1090} y={365} a="c" text="命运说……" at={20} size={22} font={F.serif} w={900} />
    <Txt x={1130} y={640} a="c" text="禁言 · 很久" at={120} size={22} font={F.serif} w={900} color={C.red} />
  </SeqFigure>
);

/* 序列5 逝者：墓碑与起身的影子 */
const F09: React.FC = () => {
  const f = useCurrentFrame();
  const rise = prog(f, 100, 40);
  return (
    <SeqFigure
      seq={5}
      name="逝者"
      sub="打不过，但会装死"
      rank="孙子"
      power={1}
      fig={9}
      abilities={['以假死脱离原本的命运', '摆脱此前的限制', '较正常地窥视、影响命运', '假死瞒过占卜与预言']}
      source={WIKI}
      note={{text: '正文：卢米安回忆“正义”女士说过，“逝者”能利用死亡脱离原本的命运', kind: '正文', src: '第二卷第81章《拳套》'}}
    >
      <Svg>
        <P d="M990 660 L1290 660" at={8} dur={20} w={2} />
        <P d="M1050 660 L1050 430 Q1050 370 1110 370 Q1170 370 1170 430 L1170 660" at={14} dur={30} w={3.5} />
        <P d="M1110 420 L1110 500 M1080 446 L1140 446" at={40} dur={14} w={3} />
        <g opacity={rise} transform={`translate(${rise * 120} 0)`}>
          <Person x={1120} y={600} at={100} pose="stand" s={0.75} c={C.red} w={2.5} />
        </g>
      </Svg>
      <Txt x={1110} y={560} a="c" text="假死" at={60} size={24} font={F.serif} w={900} />
    </SeqFigure>
  );
};

/* 图10 逝者 vs 战士序列9 */
const F10: React.FC = () => {
  const f = useCurrentFrame();
  const fall = prog(f, 190, 18);
  const sneak = prog(f, 240, 60);
  const L = ['超越常人的力量', '超越常人的敏捷', '精通武器与格斗'];
  const R = ['无身体加成记载', '无直接攻击手段', '会假死'];
  return (
    <Figure theme="navy" rays={{cx: 1290, cy: 470}} chrome={{section: '孙子', value: '5 vs 战士9', node: 4, prev: 4, fig: 10, total: TOTAL}} tag={['逝者', 'vs', '战士途径序列9']} title="高四级，打不过" sub="逝者（序列5）vs 战士（序列9）">
      <Svg>
        <Person x={1100} y={620} at={20} pose="reach" s={1.25} c={C.cream} />
        <P d="M1165 500 L1212 452" at={50} dur={10} c={C.gold} w={4} />
        <g transform={`rotate(${fall * -85} 1470 682)`}>
          <Person x={1470} y={620} at={30} pose="stand" s={1.25} c={C.cream} />
        </g>
        {sneak > 0 && (
          <g opacity={0.7 * (1 - 0.6 * sneak)} transform={`translate(${sneak * 300} 0)`}>
            <circle cx={1500} cy={482} r={22} fill="none" stroke={C.cream} strokeWidth={2} strokeDasharray="4 5" />
            <path d="M1500 504 L1500 600 M1500 530 L1470 570 M1500 530 L1530 570 M1500 600 L1478 680 M1500 600 L1522 680" stroke={C.cream} strokeWidth={2} strokeDasharray="4 5" fill="none" />
          </g>
        )}
      </Svg>
      <Txt x={1100} y={300} a="c" text="序列9 · 战士" at={14} size={30} font={F.serif} w={900} color={C.gold} />
      <Txt x={1470} y={300} a="c" text="序列5 · 逝者" at={20} size={30} font={F.serif} w={900} />
      <Txt x={1290} y={480} a="c" text="VS" at={40} size={84} font={F.serif} w={900} color={C.red} />
      {L.map((t, i) => (
        <Txt key={t} x={1100} y={730 + i * 34} a="c" text={t} at={70 + i * 8} size={20} color="#d2c9b4" />
      ))}
      {R.map((t, i) => (
        <Txt key={t} x={1470} y={730 + i * 34} a="c" text={t} at={80 + i * 8} size={20} color={i === 2 ? C.gold : '#d2c9b4'} />
      ))}
      <Txt x={1290} y={866} a="c" text="正面打：战士大概率赢 —— 但逝者可以躺下装死，然后溜了" at={200} size={26} font={F.serif} w={900} color={C.gold} stagger={0.8} />
      <Badge x={1290} y={920} a="c" at={250} kind="观点" src="依据两条途径的序列描述推演，并非原作中的对战" />
    </Figure>
  );
};

/* 序列4 改命人：被拨动的那根线 */
const F11: React.FC = () => {
  const f = useCurrentFrame();
  const bend = prog(f, 80, 40);
  const cy = 490 - 150 * bend;
  return (
    <SeqFigure seq={4} name="改命人" sub="能改命了，还是孙子" rank="孙子" power={1} fig={11} abilities={['初步操纵命运', '不只是影响，而是改变', '能改自己的命，也能改别人的']} source={WIKI}>
      <Svg>
        <Thread x1={990} x2={1290} y={400} amp={6} at={10} c={C.ink} op={0.5} />
        <Thread x1={990} x2={1290} y={590} amp={6} phase={2} at={16} c={C.ink} op={0.5} />
        {f > 30 && <path d={`M990 490 Q1140 ${cy} 1290 ${490 - 100 * bend}`} stroke={C.red} strokeWidth={3.5} fill="none" opacity={prog(f, 30, 14)} />}
        <Dot x={1140} y={cy + (490 - cy) / 2} r={8} at={76} c={C.red} />
      </Svg>
      <Txt x={1130} y={680} a="c" text="原本的命 → 改过的命" at={130} size={20} font={F.serif} w={900} color={C.gray} />
    </SeqFigure>
  );
};

/* 序列3 命运侍者：织机与残缺 */
const F12: React.FC = () => {
  const f = useCurrentFrame();
  return (
    <SeqFigure
      seq={3}
      name="命运侍者"
      sub="终于能打，先把自己打残"
      rank="孙子"
      power={2}
      fig={12}
      abilities={['编织命运，类似“作家”的安排', '织高位格或大量目标会遭反噬', '反噬让自身残缺，严重时致死', '残缺很难恢复']}
      source={WIKI}
      note={{text: '锐评：攻击手段到这一级，才算有点用', kind: '观点', src: '个人强度评价'}}
    >
      <Svg>
        <P d="M1000 300 L1200 300 L1200 600 L1000 600 Z" at={8} dur={24} w={3} />
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <P key={`v${i}`} d={`M${1028 + i * 29} 300 L${1028 + i * 29} 600`} at={24 + i * 3} dur={14} w={1.5} op={0.6} />
        ))}
        {[0, 1, 2, 3, 4, 5, 6].map((i) => (
          <P key={`h${i}`} d={`M1000 ${330 + i * 40} L1200 ${330 + i * 40}`} at={50 + i * 8} dur={14} c={i % 2 ? C.red : C.gold} w={3} />
        ))}
        <Person x={1270} y={620} at={30} pose="stand" s={0.8} />
        {[0, 1, 2].map((i) => (
          <P key={`c${i}`} d={`M${1252 + i * 14} ${540 + i * 26} l10 8 l-6 10`} at={140 + i * 8} dur={8} c={C.red} w={3} />
        ))}
      </Svg>
      <Txt x={1270} y={700} a="c" text="反噬" at={150} size={20} font={F.serif} w={900} color={C.red} />
    </SeqFigure>
  );
};

export const SCENES_B: Record<string, React.FC> = {ch2: Ch2, f04: F04, f05: F05, f06: F06, f07: F07, f08: F08, f09: F09, f10: F10, f11: F11, f12: F12};
