import React from 'react';
import {useCurrentFrame} from 'remotion';
import {Badge, C, ChapterCard, circ, Dot, F, Figure, LogoCard, P, pol, prog, Svg, Txt} from '../kit';
import {TOTAL} from '../parts';

const Logo: React.FC = () => <LogoCard question="序列2之前，都是孙子？" />;

const Ch1: React.FC = () => <ChapterCard glyphs="辈分" no="第一章" name="辈分" line1="一条途径，五代人" foot="孙子 · 哥哥 · 爹 · 爷爷 · 列祖列宗" />;

/* 图01 命运女神：纺锤与万物之线 */
const F01: React.FC = () => {
  const f = useCurrentFrame();
  const cx = 1290;
  const cy = 520;
  const rays = Array.from({length: 14}, (_, i) => -160 + i * 24.5);
  return (
    <Figure
      theme="navy"
      rays={{cx, cy}}
      chrome={{section: '旧日', value: '旧日 · 命运女神', node: 10, prev: 0, fig: 1, total: TOTAL}}
      tag={['旧日', '外神恩赐途径', '命运女神']}
      title="命运女神"
      sub="纺织万物命运的旧日"
    >
      <Svg>
        <P d={`M${cx} 240 L${cx} 800`} at={10} dur={30} c={C.cream} w={3} />
        <P d={`M${cx} 400 L${cx + 52} ${cy} L${cx} 640 L${cx - 52} ${cy} Z`} at={24} dur={28} c={C.gold} w={3} />
        {[0, 1, 2, 3, 4].map((i) => (
          <P key={i} d={`M${cx - 40 + i * 4} ${cy - 60 + i * 30} L${cx + 40 - i * 4} ${cy - 60 + i * 30}`} at={40 + i * 4} dur={10} c={C.gold} w={1.5} op={0.7} />
        ))}
        <g transform={`rotate(${f * 0.05} ${cx} ${cy})`}>
          {rays.map((a, i) => {
            const [x1, y1] = pol(cx, cy, 70, a);
            const [qx, qy] = pol(cx, cy, 190, a + 10);
            const [x2, y2] = pol(cx, cy, 300, a + 4);
            return <P key={i} d={`M${x1} ${y1} Q${qx} ${qy} ${x2} ${y2}`} at={60 + i * 4} dur={26} c="#e3dac4" w={1.3} op={0.6} />;
          })}
        </g>
        <P d={circ(cx, cy, 300)} at={110} dash="2 10" c="#e3dac4" w={1.5} />
        <Dot x={cx} y={240} r={8} at={36} c={C.cream} />
        <Dot x={cx} y={800} r={8} at={40} c={C.cream} />
      </Svg>
      <Txt x={1560} y={300} text="旧日 · 命运女神" at={120} size={30} font={F.serif} w={900} />
      <Txt x={1560} y={340} text="万物命运的纺织者" at={128} size={20} color="#d2c9b4" />
      <Txt x={1560} y={374} text="传为最初造物主与堕落母神的长女" at={136} size={18} color="#968f7c" stagger={0.8} />
      <Txt x={1000} y={856} text="≠ 命运之轮（那是怪物途径的序列0）" at={170} size={24} font={F.serif} w={900} color={C.gold} stagger={1} />
      <Badge x={1290} y={912} a="c" at={190} kind="整理" src="诡秘之主 Wiki · 转录作者公众号设定" />
    </Figure>
  );
};

/* 图02 十个序列：一路走，一路失去 */
const F02: React.FC = () => {
  const f = useCurrentFrame();
  const names = ['失梦人', '乐师', '窥命师', '哑巴', '逝者', '改命人', '命运侍者', '织网人', '命运之刃', '永生律'];
  const losses = ['失去做梦能力', '部分自刺双眼', '', '施术后禁言', '假死', '', '可能反噬致残', '无眼者', '神话形态无头', '永生'];
  const y = (i: number) => 820 - i * 64;
  const climb = prog(f, 150, 90);
  const cy = y(0) - (y(0) - y(9)) * climb;
  return (
    <Figure chrome={{section: '序列', value: '9 → 0', node: 0, prev: 10, fig: 2, total: TOTAL}} tag={['序列', '9 → 0', '命运女神途径']} title="十个序列" sub="一路走，一路失去">
      <Svg>
        <P d={`M1180 ${y(0) + 40} L1180 ${y(9) - 40}`} at={10} dur={30} w={3} />
        <P d={`M1290 ${y(0) + 40} L1290 ${y(9) - 40}`} at={14} dur={30} w={3} />
        {names.map((_, i) => (
          <P key={i} d={`M1180 ${y(i)} L1290 ${y(i)}`} at={30 + i * 5} dur={10} w={2} />
        ))}
        {losses.map((l, i) => (l ? <P key={i} d={`M1580 ${y(i)} L1610 ${y(i)}`} at={90 + i * 6} dur={8} c={C.red} w={2} /> : null))}
        <P d={`M1235 ${y(9) - 40} L1235 ${y(9) - 100}`} at={100} dash="4 8" w={2} />
        {f > 150 && <P d={`M1300 ${y(0)} L1300 ${cy}`} at={150} dur={1} c={C.red} w={4} />}
        {f > 150 && <circle cx={1300} cy={cy} r={9} fill={C.red} />}
      </Svg>
      {names.map((n, i) => (
        <React.Fragment key={n}>
          <Txt x={1160} y={y(i)} a="r" text={`序列${9 - i}`} at={34 + i * 5} size={17} color={C.gray} />
          <Txt x={1322} y={y(i)} text={n} at={36 + i * 5} size={27} font={F.serif} w={900} />
        </React.Fragment>
      ))}
      {losses.map((l, i) => (l ? <Txt key={i} x={1622} y={y(i)} text={l} at={94 + i * 6} size={20} font={F.serif} w={900} color={C.red} /> : null))}
      <Txt x={1235} y={y(9) - 124} a="c" text="旧日 · 命运女神" at={106} size={22} font={F.serif} w={900} />
      <Badge x={1290} y={920} a="c" at={200} kind="整理" src="诡秘之主 Wiki「命运女神途径」" />
    </Figure>
  );
};

/* 图03 辈分表：饭桌线 */
const F03: React.FC = () => {
  const rows = [
    {rank: '列祖列宗', who: '旧日 · 命运女神', y: 270},
    {rank: '爷爷', who: '序列0 · 永生律', y: 372},
    {rank: '爹', who: '序列1 · 命运之刃', y: 474},
    {rank: '哥哥', who: '序列2 · 织网人', y: 576},
  ];
  const kids = ['失梦人', '乐师', '窥命师', '哑巴', '逝者', '改命人', '命运侍者'];
  return (
    <Figure chrome={{section: '辈分', value: '五代人', node: 7, prev: 0, fig: 3, total: TOTAL}} tag={['辈分表', '锐评', '五代人']} title="辈分表" sub="序列2，才能上桌吃饭">
      <Svg>
        {rows.map((r, i) => (
          <P key={r.rank} d={`M1000 ${r.y - 40} L1840 ${r.y - 40} L1840 ${r.y + 40} L1000 ${r.y + 40} Z`} at={14 + i * 12} dur={20} w={i === 0 ? 3.5 : 2.5} c={i === 0 ? C.red : undefined} />
        ))}
        <P d="M980 668 L1860 668" at={90} dash="10 8" c={C.red} w={3} />
        <P d="M1000 712 L1840 712 L1840 858 L1000 858 Z" at={110} dur={20} w={1.5} op={0.6} />
      </Svg>
      {rows.map((r, i) => (
        <React.Fragment key={r.rank}>
          <Txt x={1030} y={r.y} text={r.rank} at={20 + i * 12} size={36} font={F.serif} w={900} color={i === 0 ? C.red : undefined} />
          <Txt x={1300} y={r.y} text={r.who} at={26 + i * 12} size={24} w={700} color={C.soft} />
        </React.Fragment>
      ))}
      <Txt x={1856} y={646} a="r" text="饭桌线 · 序列2 才能上桌" at={96} size={22} font={F.serif} w={900} color={C.red} />
      <Txt x={1030} y={746} text="孙子" at={116} size={36} font={F.serif} w={900} />
      <Txt x={1160} y={746} text="序列9—3 · 七个序列挤一桌" at={122} size={20} color={C.soft} />
      {kids.map((k, i) => (
        <Txt key={k} x={1030 + i * 116} y={812} text={k} at={130 + i * 5} size={20} font={F.serif} w={900} color={C.gray} />
      ))}
      <Badge x={1000} y={920} at={170} kind="观点" src="按实际战力排辈分，个人强度评价，非官方设定" />
    </Figure>
  );
};

export const SCENES_A: Record<string, React.FC> = {logo: Logo, ch1: Ch1, f01: F01, f02: F02, f03: F03};
