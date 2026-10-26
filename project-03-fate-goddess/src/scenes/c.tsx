import React from 'react';
import {useCurrentFrame} from 'remotion';
import {Badge, C, ChapterCard, Dot, F, Figure, LogoCard, P, Person, pol, prog, Svg, Txt} from '../kit';
import {SeqFigure, stars, Thread, TOTAL} from '../parts';

const WIKI = {kind: '整理' as const, src: '诡秘之主 Wiki「命运女神途径」'};

const Ch3: React.FC = () => <ChapterCard glyphs="上桌" no="第三章" name="上桌" line1="序列2 → 旧日" foot="哥哥 · 爹 · 爷爷 · 列祖列宗" />;

/* 序列2 织网人：一张命运之网 */
const F13: React.FC = () => {
  const cx = 1130;
  const cy = 480;
  const outer = Array.from({length: 10}, (_, i) => pol(cx, cy, 160, i * 36 - 90));
  const inner = Array.from({length: 5}, (_, i) => pol(cx, cy, 80, i * 72 - 54));
  return (
    <SeqFigure
      seq={2}
      name="织网人"
      sub="不再反噬，上桌吃饭"
      theme="navy"
      rank="哥哥"
      power={3}
      fig={13}
      abilities={['“无眼者”', '初步接触纺织命运的权柄', '编织大量目标，交汇成网', '织网时不再遭到反噬']}
      source={WIKI}
      note={{text: '神话形态三身并立，无臂与无头之躯尚未完全成形', ...WIKI}}
    >
      <Svg>
        {outer.map(([x, y], i) => {
          const [x2, y2] = outer[(i + 1) % 10];
          return <P key={`o${i}`} d={`M${x} ${y} L${x2} ${y2}`} at={20 + i * 4} dur={12} c={C.gold} w={2} />;
        })}
        {outer.map(([x, y], i) => {
          const [x2, y2] = inner[Math.floor(i / 2)];
          return <P key={`s${i}`} d={`M${x} ${y} L${x2} ${y2}`} at={60 + i * 3} dur={12} c="#e3dac4" w={1.3} op={0.7} />;
        })}
        {inner.map(([x, y], i) => (
          <P key={`c${i}`} d={`M${x} ${y} L${cx} ${cy}`} at={90 + i * 4} dur={12} c={C.gold} w={1.5} />
        ))}
        {outer.map(([x, y], i) => (
          <Dot key={`d${i}`} x={x} y={y} r={7} at={16 + i * 4} c={C.cream} />
        ))}
        <Dot x={cx} y={cy} r={12} at={110} c={C.gold} />
      </Svg>
    </SeqFigure>
  );
};

/* 序列1 命运之刃：一刀两断 */
const F14: React.FC = () => {
  const f = useCurrentFrame();
  const drop = prog(f, 130, 30);
  const ys = [340, 400, 460, 520, 580, 640];
  return (
    <SeqFigure seq={1} name="命运之刃" sub="一刀，剪断你的命" theme="navy" rank="爹" power={4} fig={14} abilities={['初步掌控裁决命运的权柄', '凝聚命运之刃', '切断命运之线与命运支流', '神话形态：无头之躯']} source={WIKI}>
      <Svg>
        {ys.map((y, i) => (
          <P key={`l${i}`} d={`M990 ${y} L1140 ${y + (i % 2 ? 6 : -6)}`} at={10 + i * 3} dur={18} c="#e3dac4" w={1.8} />
        ))}
        <g transform={`translate(0 ${drop * 70})`} opacity={1 - drop}>
          {ys.map((y, i) => (
            <P key={`r${i}`} d={`M1140 ${y + (i % 2 ? 6 : -6)} L1290 ${y}`} at={14 + i * 3} dur={18} c="#e3dac4" w={1.8} />
          ))}
        </g>
        {f > 96 && <path d="M1180 280 L1196 280 L1110 700 L1098 700 Z" fill={C.gold} opacity={prog(f, 96, 14)} />}
        <P d="M1196 270 L1092 712" at={116} dur={10} c={C.red} w={3} />
      </Svg>
      <Txt x={1130} y={740} a="c" text="命运之线 · 断" at={150} size={22} font={F.serif} w={900} color={C.gold} />
    </SeqFigure>
  );
};

/* 序列0 永生律：从命运长河里回来 */
const F15: React.FC = () => {
  const f = useCurrentFrame();
  const gather = prog(f, 100, 50);
  const seeds = Array.from({length: 16}, (_, i) => [990 + ((i * 97) % 300), 330 + ((i * 53) % 320)]);
  return (
    <SeqFigure
      seq={0}
      name="永生律"
      sub="命运长河不乱，祂就不死"
      theme="navy"
      rank="爷爷"
      power={5}
      fig={15}
      abilities={['编织、汇聚、裁决命运', '万物命运仍交织成河，就能回归']}
      source={WIKI}
    >
      <Svg>
        {[0, 1, 2, 3, 4, 5].map((i) => (
          <Thread key={i} x1={990} x2={1290} y={350 + i * 56} amp={14} phase={i * 1.1} at={8 + i * 4} op={0.45} />
        ))}
        {seeds.map(([x, y], i) => (
          <circle key={i} cx={x + (1130 - x) * gather} cy={y + (500 - y) * gather} r={5} fill={C.gold} opacity={(1 - gather * 0.8) * prog(f, 60, 20)} />
        ))}
        <Person x={1130} y={560} at={150} pose="up" s={1.1} c={C.cream} />
      </Svg>
      <Txt x={1130} y={680} a="c" text="命运长河" at={40} size={20} font={F.serif} w={900} color={C.gold} />
    </SeqFigure>
  );
};

/* 图16 旧日 · 命运女神：列祖列宗 */
const F16: React.FC = () => {
  const f = useCurrentFrame();
  const cx = 1290;
  const cy = 520;
  const bodies = [-90, 30, 150].map((a) => pol(cx, cy, 230, a));
  return (
    <Figure
      theme="navy"
      rays={{cx, cy}}
      chrome={{section: '列祖列宗', value: '旧日 · 命运女神', node: 10, prev: 9, fig: 16, total: TOTAL}}
      tag={['旧日', '命运女神', '列祖列宗']}
      title="列祖列宗"
      sub="万物的命，都是祂织的"
    >
      <Svg>
        <g transform={`rotate(${f * 0.06} ${cx} ${cy})`}>
          {Array.from({length: 24}, (_, i) => {
            const [x1, y1] = pol(cx, cy, 60, i * 15);
            const [x2, y2] = pol(cx, cy, 420, i * 15 + 6);
            return <P key={i} d={`M${x1} ${y1} L${x2} ${y2}`} at={40 + i * 2} dur={30} c="#e3dac4" w={1} op={0.4} />;
          })}
        </g>
        <P d={`M${cx} ${cy - 90} L${cx + 40} ${cy} L${cx} ${cy + 90} L${cx - 40} ${cy} Z`} at={10} dur={24} c={C.gold} w={3} />
        {bodies.map(([x, y], i) => (
          <P key={i} d={`M${cx} ${cy} L${x} ${y + 20}`} at={70 + i * 8} dur={18} c={C.gold} w={2} />
        ))}
        {bodies.map(([x, y], i) => (
          <Person key={i} x={x} y={y + 60} at={80 + i * 10} pose="up" s={0.7} c={C.cream} />
        ))}
      </Svg>
      <Txt x={1580} y={300} text="所有事物命运的纺织者" at={120} size={24} font={F.serif} w={900} />
      <Txt x={1580} y={340} text="最终裁决的象征" at={128} size={20} color="#d2c9b4" />
      <Txt x={1580} y={374} text="Wiki：安排命运的能力强于上帝" at={136} size={18} color="#968f7c" />
      <Txt x={1580} y={408} text="长河短暂完全混乱，才可能杀死祂" at={144} size={18} color="#968f7c" />
      <Badge x={1000} y={860} at={150} kind="观点" src="" />
      <Txt x={1090} y={860} text={`辈分：列祖列宗    战力  ${stars(5)}+`} at={154} size={26} font={F.serif} w={900} color={C.gold} stagger={1} />
      <Badge x={1000} y={910} at={170} kind="整理" src="诡秘之主 Wiki「命运女神途径」" />
    </Figure>
  );
};

/* 图17 战力曲线：先当孙子，再当祖宗 */
const F17: React.FC = () => {
  const names = ['失梦人', '乐师', '窥命师', '哑巴', '逝者', '改命人', '命运侍者', '织网人', '命运之刃', '永生律', '旧日'];
  const v = [0.04, 0.05, 0.06, 0.07, 0.07, 0.1, 0.18, 0.42, 0.62, 0.82, 1];
  const x = (i: number) => 1040 + i * 78;
  const y = (val: number) => 760 - val * 470;
  const curve = v.map((val, i) => `${i ? 'L' : 'M'}${x(i)} ${y(val)}`).join(' ');
  const marks = [
    {i: 3, t: '孙子'},
    {i: 7, t: '哥哥'},
    {i: 8, t: '爹'},
    {i: 9, t: '爷爷'},
    {i: 10, t: '列祖列宗'},
  ];
  return (
    <Figure chrome={{section: '总结', value: '9 → 旧日', node: 10, prev: 10, fig: 17, total: TOTAL}} tag={['总结', '战力曲线', '锐评']} title="先当孙子，再当祖宗" sub="前面全是代价，后面全是权柄">
      <Svg>
        <P d="M1010 260 L1010 760 L1850 760" at={8} dur={26} w={2.5} />
        <P d={`M1010 ${y(0.3)} L1850 ${y(0.3)}`} at={40} dash="10 8" c={C.red} w={2.5} />
        <P d={curve} at={60} dur={90} c={C.red} w={4.5} />
        {v.map((val, i) => (
          <Dot key={i} x={x(i)} y={y(val)} r={i >= 7 ? 8 : 5} at={60 + i * 8} c={i >= 7 ? C.red : C.ink} />
        ))}
      </Svg>
      {names.map((n, i) => (
        <Txt key={n} x={x(i)} y={788} a="c" text={n} at={20 + i * 3} size={15} color={C.soft} />
      ))}
      <Txt x={1846} y={y(0.3) - 22} a="r" text="饭桌线" at={46} size={20} font={F.serif} w={900} color={C.red} />
      <Txt x={1000} y={244} a="r" text="战力" at={10} size={18} color={C.gray} />
      {marks.map((m) => (
        <Txt key={m.t} x={x(m.i) - 14} y={y(v[m.i]) - 34} a="r" text={m.t} at={110 + m.i * 6} size={22} font={F.serif} w={900} color={m.i >= 7 ? C.red : C.ink} />
      ))}
      <Badge x={1000} y={850} at={180} kind="观点" src="示意曲线，表达个人强度评价，不是原作数据" />
    </Figure>
  );
};

const Outro: React.FC = () => (
  <LogoCard
    question="先当孙子，再当祖宗。"
    under={
      <>
        <Txt x={960} y={900} a="c" width={1500} fade at={110} size={16} color={C.soft} lh={1.6} text="资料：诡秘之主 Wiki「命运女神途径」「战士途径」（转录作者公众号设定）；《宿命之环》第三卷第81章《拳套》；百度贴吧讨论。" />
        <Txt x={960} y={948} a="c" width={1500} fade at={120} size={16} color={C.soft} lh={1.6} text="辈分、星级与战力曲线为个人锐评，不是官方设定；“二手整理”来自 Wiki 与论坛。本片为同人科普作品。" />
      </>
    }
  />
);

export const SCENES_C: Record<string, React.FC> = {ch3: Ch3, f13: F13, f14: F14, f15: F15, f16: F16, f17: F17, outro: Outro};
