import React from 'react';
import {useCurrentFrame} from 'remotion';
import {Arrow, Badge, C, ChapterCard, circ, Dot, F, Figure, Fill, Ghost, P, pol, Person, prog, RingIcon, Steps, Svg, Txt} from '../kit';
import {TOTAL} from './intro';

const ch = (fig: number, section: string, prev = 0) => ({section, value: '9 · 舞蹈家', node: 0, prev, fig, total: TOTAL});

// 名称 + 小字说明的标注。
const Note: React.FC<{x: number; y: number; at: number; title: string; desc?: string; a?: 'l' | 'c' | 'r'; red?: boolean; step?: string}> = ({x, y, at, title, desc, a = 'l', red, step}) => (
  <>
    {step && <Txt x={x} y={y - 32} a={a} text={step} at={at} size={18} font={F.serif} w={900} color={C.red} />}
    <Txt x={x} y={y} a={a} text={title} at={at + 2} size={27} font={F.serif} w={900} color={red ? C.red : undefined} />
    {desc && <Txt x={x} y={y + 34} a={a} text={desc} at={at + 8} size={18} color={C.soft} />}
  </>
);

export const Ch2: React.FC = () => <ChapterCard glyphs="舞蹈" no="第二章" name="舞蹈家" line1="序列9 · 一切从一支舞开始" foot="身体 · 祈祷 · 两支舞" />;

/* 图05 舞蹈家：身体与灵性 */
const F05: React.FC = () => (
  <Figure chrome={ch(5, '舞蹈家')} tag={['序列9', '舞蹈家', '能力一']} title="舞蹈家" sub="身体，就是仪式的道具">
    <Svg>
      <P d="M1110 760 A190 34 0 1 1 1490 760 A190 34 0 1 1 1110 760" at={10} dash="6 10" w={2} />
      <Person x={1300} y={640} at={18} pose="dance" s={2.2} w={4} />
      <Arrow pts={[[1120, 790], [1300, 860], [1480, 790]]} at={70} dur={26} c={C.red} w={3} />
      <P d="M1414 310 L1520 280" at={96} dur={12} w={1.5} />
      <P d="M1306 500 L1540 470" at={110} dur={12} w={1.5} />
      <P d="M1089 530 L1060 690" at={124} dur={12} w={1.5} />
      <Dot x={1414} y={310} r={5} at={96} />
      <Dot x={1306} y={500} r={5} at={110} />
      <Dot x={1089} y={530} r={5} at={124} />
    </Svg>
    <Note x={1530} y={272} at={100} title="神秘舞蹈" desc="更容易掌握" />
    <Note x={1550} y={462} at={114} title="灵性" desc="随晋升提升" />
    <Note x={1050} y={704} a="r" at={128} title="柔韧性" desc="大幅提升" red />
    <Badge x={1300} y={900} a="c" at={160} kind="正文" src="第一卷第60章《舞蹈家》" />
  </Figure>
);

/* 图06 以舞祈祷：舞蹈 + 韵律 + 灵性 → 祈祷 */
const F06: React.FC = () => {
  const ins = [
    {y: 330, n: '舞蹈', from: [1103, 363], to: [1207, 447]},
    {y: 520, n: '韵律', from: [1114, 520], to: [1182, 520]},
    {y: 710, n: '灵性', from: [1103, 677], to: [1207, 593]},
  ];
  return (
    <Figure chrome={ch(6, '舞蹈家')} tag={['序列9', '能力二', '祈祷']} title="以舞祈祷" sub="舞蹈、韵律、灵性，三者合一">
      <Svg>
        {ins.map((n, i) => (
          <P key={n.n} d={circ(1060, n.y, 50)} at={12 + i * 8} dur={22} w={3} />
        ))}
        {ins.map((n, i) => (
          <Arrow key={n.n} pts={[n.from as [number, number], n.to as [number, number]]} at={44 + i * 8} dur={16} w={2.5} />
        ))}
        <P d={circ(1300, 520, 74)} at={66} dur={26} c={C.red} w={3.5} />
        <Steps cx={1300} cy={520} r={104} n={10} at={96} every={4} />
        <Arrow pts={[[1399, 456], [1521, 378]]} at={130} dur={16} w={2.5} />
        <P d={circ(1580, 340, 62)} at={140} dash="6 8" w={2.5} />
        <Arrow pts={[[1580, 420], [1580, 592]]} at={160} dur={16} w={2.5} />
        <P d="M1480 600 L1680 600 L1680 690 L1480 690 Z" at={170} dur={22} w={2.5} />
      </Svg>
      {ins.map((n, i) => (
        <Txt key={n.n} x={1060} y={n.y} a="c" text={n.n} at={18 + i * 8} size={27} font={F.serif} w={900} />
      ))}
      <Txt x={1300} y={520} a="c" text="祈祷" at={76} size={36} font={F.serif} w={900} color={C.red} />
      <Txt x={1580} y={342} a="c" text="？" at={146} size={42} font={F.serif} w={900} />
      <Txt x={1660} y={292} text="不特定的存在" at={150} size={22} font={F.serif} w={900} />
      <Txt x={1580} y={630} a="c" text="调动自然力量" at={180} size={25} font={F.serif} w={900} />
      <Txt x={1580} y={664} a="c" text="产生不同效果" at={186} size={18} color={C.soft} />
      <Txt x={1300} y={836} a="c" text="祈祷的对象不限于宿命之环，也可以设计新的祭舞" at={196} size={19} color={C.soft} stagger={0.8} />
      <Badge x={1300} y={880} a="c" at={210} kind="整理" src="诡秘之主 Wiki · 百度百科" />
    </Figure>
  );
};

/* 图07 两支舞：祭舞 / 招摄之舞 */
const F07: React.FC = () => (
  <Figure chrome={ch(7, '舞蹈家')} tag={['序列9', '舞蹈', '两种']} title="两支舞" sub="本片介绍的两种舞">
    <Svg>
      <P d="M1290 230 L1290 810" at={8} dash="4 10" w={1.5} />
      <RingIcon cx={1120} cy={286} r={58} at={20} />
      <Person x={1120} y={650} at={30} pose="up" s={1.3} />
      <P d="M1120 470 L1120 352" at={70} dash="5 8" c={C.red} w={2.5} />
      <Ghost x={1610} y={320} s={46} at={40} />
      <Person x={1450} y={660} at={50} pose="dance" s={1.15} />
      <Arrow pts={[[1600, 380], [1630, 520], [1490, 512]]} at={100} dur={30} c={C.red} w={2.5} dash="6 8" />
    </Svg>
    <Txt x={1196} y={286} text="宿命" at={60} size={22} font={F.serif} w={900} />
    <Txt x={1120} y={748} a="c" text="祭舞" at={84} size={40} font={F.serif} w={900} />
    <Txt x={1120} y={796} a="c" text="以仪式舞蹈祈祷，产生相应效果" at={92} size={20} color={C.soft} />
    <Txt x={1460} y={748} a="c" text="招摄之舞" at={120} size={40} font={F.serif} w={900} color={C.red} />
    <Txt x={1460} y={796} a="c" text="召来灵体，可使其附身" at={128} size={20} color={C.soft} />
    <Badge x={1290} y={880} a="c" at={150} kind="整理" src="第一卷第60章 · 百度百科" />
  </Figure>
);

export const Ch3: React.FC = () => <ChapterCard glyphs="案例" no="第三章" name="案例" line1="卢米安的四个片段" foot="第一卷 72 · 83 章  ｜  第二卷 52 · 80 章" />;

/* 案例四 第二卷第80章《配合》：异常镜中空间 */
const Cooperation: React.FC = () => {
  const f = useCurrentFrame();
  const m = prog(f, 130, 80);
  const mx = 1350 + (1168 - 1350) * m;
  const my = 570 + (438 - 570) * m;
  const fr = prog(f, 210, 60);
  const fx = 1525 + (1405 - 1525) * fr;
  const fy = 740 + (612 - 740) * fr;
  const ripple = (k: number) => ((f - 60 + k * 30) % 90) / 90;
  const spikes = Array.from({length: 16}, (_, i) => pol(0, 0, i % 2 ? 11 : 20, i * 22.5).join(',')).join(' ');
  return (
    <Figure chrome={ch(11, '案例')} tag={['案例四', '卢米安', '第二卷第80章']} title="招摄之舞" sub="把怪物，引向自己">
      <Svg>
        {['M1000 240 L1260 240', 'M1320 240 L1580 240', 'M1580 240 L1580 520', 'M1580 580 L1580 800', 'M1580 800 L1150 800', 'M1080 800 L1000 800', 'M1000 800 L1000 240'].map((d, i) => (
          <P key={i} d={d} at={8 + i * 4} dur={20} w={4.5} />
        ))}
        <P d="M1014 254 L1566 254 L1566 786 L1014 786 Z" at={30} dur={30} w={1.2} op={0.45} />
        {[[1282, 250, 20], [1598, 548, -15], [1114, 812, 35]].map(([x, y, r], i) => (
          <rect key={i} x={x - 9} y={y - 7} width={18} height={14} transform={`rotate(${r} ${x} ${y})`} fill="#2b2620" opacity={prog(f, 40 + i * 4, 10) * 0.8} />
        ))}
        <Fill d="M1270 590 C1270 530 1340 515 1400 520 C1470 525 1500 560 1495 600 C1490 650 1420 670 1360 665 C1300 660 1270 630 1270 590 Z" at={44} c={C.water} />
        <P d="M1270 590 C1270 530 1340 515 1400 520 C1470 525 1500 560 1495 600 C1490 650 1420 670 1360 665 C1300 660 1270 630 1270 590 Z" at={40} dur={26} w={2} c="#6f7c80" />
        {f > 60 &&
          [0, 1, 2].map((k) => {
            const p = ripple(k);
            return <ellipse key={k} cx={1385} cy={595} rx={20 + p * 70} ry={10 + p * 34} fill="none" stroke="#6f7c80" strokeWidth={1.5} opacity={(1 - p) * 0.6} />;
          })}
        <rect x={1392} y={606} width={34} height={18} transform="rotate(20 1409 615)" fill="none" stroke="#2b2620" strokeWidth={2.5} opacity={prog(f, 70, 12)} />
        <Dot x={1120} y={410} r={12} at={50} ring="#2b2620" />
        <Steps cx={1120} cy={410} r={50} n={8} at={60} every={5} />
        {f > 110 && <circle cx={1350} cy={570} r={4} fill={C.red} opacity={prog(f, 110, 10)} />}
        {f > 130 && <line x1={1350} y1={570} x2={mx} y2={my} stroke={C.red} strokeWidth={3} />}
        {f > 110 && (
          <g transform={`translate(${mx} ${my}) scale(${prog(f, 110, 16)}) rotate(${f * 3})`}>
            <polygon points={spikes} fill={C.red} />
          </g>
        )}
        {f > 200 && <line x1={1525} y1={740} x2={fx} y2={fy} stroke="#4b453b" strokeWidth={2} strokeDasharray="5 7" />}
        {f > 196 && <circle cx={fx} cy={fy} r={10} fill="#4b453b" opacity={prog(f, 196, 10) * (1 - 0.7 * prog(f, 270, 20))} />}
      </Svg>
      <Note x={1120} y={316} a="c" at={56} step="第一步" title="卢米安起舞" />
      <Note x={1350} y={486} a="c" at={114} step="第二步" title="怪物现身" red />
      <Note x={1030} y={524} at={170} step="第三步" title="拖住怪物" />
      <Note x={1500} y={748} a="r" at={200} step="第四步" title="芙兰卡下水找镜子" />
      <Txt x={1448} y={636} text="镜子" at={74} size={18} color={C.soft} />
      <Txt x={1290} y={846} a="c" text="特里尔地下 · 异常镜中空间" at={20} size={19} color={C.soft} fade />
      <Badge x={1290} y={892} a="c" at={240} kind="正文" src="第二卷第80章《配合》" />
    </Figure>
  );
};

/* 案例三 第二卷第52章《特质》：灵附身 */
const Possession: React.FC = () => {
  const f = useCurrentFrame();
  const merge = prog(f, 150, 30);
  return (
    <Figure chrome={ch(10, '案例')} tag={['案例三', '卢米安', '第二卷第52章']} title="灵附身" sub="借一个灵，读它的记忆">
      <Svg>
        <P d="M990 800 L1050 640 C1070 540 1110 420 1190 340 C1260 270 1350 262 1420 300 C1500 345 1540 430 1560 520 L1590 800" at={8} dur={40} w={4.5} />
        <P d="M970 800 L1610 800" at={20} dur={24} w={1.5} />
        {[1230, 1300, 1370].map((x, i) => (
          <P key={x} d={`M${x - 12} ${290 + i * 2} L${x} ${330 + (i % 2) * 12} L${x + 12} ${292 + i * 2}`} at={30 + i * 4} dur={10} w={2} />
        ))}
        <Steps cx={1240} cy={784} r={20} rx={70} n={8} at={30} every={4} />
        <Person x={1240} y={730} at={24} pose="stand" s={1.1} />
        <g opacity={1 - 0.75 * merge}>
          <Ghost x={1460} y={430} s={46} at={56} />
        </g>
        <Arrow pts={[[1430, 486], [1360, 580], [1268, 612]]} at={100} dur={30} c={C.red} w={2.5} dash="6 8" />
        {[[1176, 520], [1240, 476], [1304, 520]].map(([x, y], i) => (
          <g key={i} opacity={prog(f, 190 + i * 10, 12)}>
            <rect x={x - 24} y={y - 17} width={48} height={34} fill="none" stroke="#2b2620" strokeWidth={2} />
            <line x1={x - 14} y1={y - 4} x2={x + 14} y2={y - 4} stroke="#2b2620" strokeWidth={1.5} />
            <line x1={x - 14} y1={y + 5} x2={x + 6} y2={y + 5} stroke="#2b2620" strokeWidth={1.5} />
          </g>
        ))}
      </Svg>
      <Note x={1590} y={420} at={64} title="死者的灵" desc="被招摄之舞召来" />
      <Txt x={1262} y={418} a="c" text="死者记忆里的剧场女演员" at={220} size={22} font={F.serif} w={900} color={C.red} />
      <Txt x={1290} y={850} a="c" text="当时尚未晋升受契之人，还不能签订长期借用能力的契约" at={244} size={20} color={C.soft} stagger={0.9} />
      <Badge x={1290} y={896} a="c" at={266} kind="正文" src="第二卷第52章《特质》" />
    </Figure>
  );
};

/* 案例一 第一卷第72章《祭舞》：担心封印挡住祈求，仪式最终成功 */
const Ritual: React.FC = () => {
  const f = useCurrentFrame();
  const pass = prog(f, 170, 20);
  const beam = prog(f, 196, 30);
  return (
    <Figure theme="navy" rays={{cx: 1290, cy: 270}} chrome={ch(8, '案例')} tag={['案例一', '第一卷第72章', '祭舞']} title="祭舞" sub="封印之下，祈求仍然成功">
      <Svg>
        <RingIcon cx={1290} cy={270} r={70} at={10} c={C.gold} />
        <P d="M1060 770 A230 56 0 1 1 1520 770 A230 56 0 1 1 1060 770" at={20} dur={36} c={C.gold} w={2} />
        <Steps cx={1290} cy={770} r={56} rx={230} n={12} at={50} every={4} c={C.cream} />
        <Person x={1290} y={760} at={30} pose="up" s={1.2} c={C.cream} />
        <P d="M1290 560 L1290 500" at={96} dash="5 8" c={C.gold} w={2.5} />
        <g transform="rotate(45 1290 455)">
          <P d="M1255 420 L1325 420 L1325 490 L1255 490 Z" at={120} dur={18} c={pass > 0.5 ? C.gold : C.red} w={3.5} />
        </g>
        <P d="M1290 410 L1290 350" at={170} dash="5 8" c={C.gold} w={2.5} />
        {beam > 0 && <line x1={1290} y1={340} x2={1290} y2={340 + 300 * beam} stroke={C.gold} strokeWidth={6} opacity={0.85} strokeLinecap="round" />}
        {beam > 0 && <circle cx={1290} cy={640} r={70 * beam} fill={C.gold} opacity={0.2 * beam} />}
      </Svg>
      <Txt x={1376} y={270} text="宿命" at={30} size={24} font={F.serif} w={900} color={C.gold} />
      <Txt x={1360} y={440} text="封印" at={130} size={28} font={F.serif} w={900} color={pass > 0.5 ? C.gold : C.red} />
      <Txt x={1360} y={476} text="担心它会挡住祈求" at={138} size={18} color="#d2c9b4" />
      <Txt x={1460} y={600} text="获准汲取恩赐" at={210} size={30} font={F.serif} w={900} color={C.gold} />
      <Txt x={1460} y={640} text="成为舞蹈家" at={218} size={20} color="#d2c9b4" />
      {[...'仪式成功'].map((c2, i) => (
        <Txt key={i} x={1800} y={200 + i * 46} a="c" text={c2} at={226 + i * 5} size={32} font={F.serif} w={900} />
      ))}
      <Badge x={1290} y={890} a="c" at={240} kind="正文" src="第一卷第72章《祭舞》" />
    </Figure>
  );
};

/* 案例二 第一卷第83章《突遇》：黑色荆棘符号，半激发 */
const Thorn: React.FC = () => {
  const f = useCurrentFrame();
  const half = prog(f, 120, 40);
  const cx = 1290;
  const cy = 660;
  const thorns = Array.from({length: 12}, (_, i) => {
    const [x1, y1] = pol(cx, cy, 64, i * 30 - 8);
    const [x2, y2] = pol(cx, cy, 88, i * 30);
    const [x3, y3] = pol(cx, cy, 64, i * 30 + 8);
    return `M${x1} ${y1} L${x2} ${y2} L${x3} ${y3}`;
  });
  const zig = Array.from({length: 25}, (_, i) => pol(cx, cy, i % 2 ? 40 : 50, i * 15).join(' ')).join(' L');
  return (
    <Figure chrome={ch(9, '案例')} tag={['案例二', '第一卷第83章', '成为舞蹈家后']} title="黑色荆棘" sub="借祭舞，半激发符号">
      <Svg>
        <defs>
          <clipPath id="thorn-half">
            <rect x={cx - 100} y={cy - 100} width={100 * half} height={200} />
          </clipPath>
          <filter id="glow" x="-50%" y="-50%" width="200%" height="200%">
            <feGaussianBlur stdDeviation={18} />
          </filter>
        </defs>
        <P d={circ(cx, 380, 72)} at={8} dur={24} w={3.5} />
        <P d="M1262 452 L1262 520 M1318 452 L1318 520" at={20} dur={12} w={3.5} />
        <P d="M1262 520 C1170 540 1080 600 1050 820" at={26} dur={24} w={3.5} />
        <P d="M1318 520 C1410 540 1500 600 1530 820" at={26} dur={24} w={3.5} />
        <g clipPath="url(#thorn-half)">
          <circle cx={cx} cy={cy} r={90} fill={C.red} opacity={0.35} filter="url(#glow)" />
          <circle cx={cx} cy={cy} r={64} fill="#1d1a16" />
        </g>
        <P d={circ(cx, cy, 64)} at={50} dur={28} w={3} />
        {thorns.map((d, i) => (
          <P key={i} d={d} at={66 + i * 2} dur={8} w={2.5} />
        ))}
        <P d={`M${zig}`} at={80} dur={30} w={2} c={half > 0.5 ? '#efe8d8' : '#2b2620'} />
        <P d="M1356 630 L1480 560" at={150} dur={12} w={1.5} />
        <P d="M1236 690 L1050 744" at={168} dur={12} w={1.5} />
      </Svg>
      <Note x={1490} y={552} at={154} title="黑色荆棘符号" desc="与宿命之环相连" />
      <Note x={1036} y={752} a="r" at={172} title="半激发" desc="借神秘祭舞" red />
      <Txt x={1290} y={866} a="c" text="他仍认为高位格怪物危险，不贸然拿它试验" at={200} size={20} color={C.soft} stagger={1} />
      <Badge x={1290} y={910} a="c" at={220} kind="正文" src="第一卷第83章《突遇》" />
    </Figure>
  );
};

/* 图12 代价 */
const F12: React.FC = () => {
  const cols = [
    {x: 1080, t: '精神疲惫', d: '仪式后身体酸痛'},
    {x: 1290, t: '附身风险', d: '招来的怪物可能附身'},
    {x: 1500, t: '封印限制', d: '卢米安体内污染被封印'},
  ];
  return (
    <Figure chrome={ch(12, '代价')} tag={['代价', '风险', '限制']} title="代价" sub="消耗、风险与个人限制">
      <Svg>
        <Person x={1080} y={560} at={12} pose="slump" s={1.1} />
        {[0, 1, 2].map((i) => (
          <P key={i} d={`M${1060 + i * 24} ${400 - (i % 2) * 8} l4 18`} at={44 + i * 4} dur={6} w={2} />
        ))}
        <Person x={1290} y={560} at={24} pose="stand" s={1.1} />
        <Ghost x={1316} y={436} s={34} at={50} c={C.red} />
        <g transform="rotate(45 1500 470)">
          <P d="M1450 420 L1550 420 L1550 520 L1450 520 Z" at={40} dur={20} c={C.red} w={3.5} />
        </g>
        <P d={circ(1500, 470, 22)} at={60} dur={14} w={2.5} />
        <P d="M1474 444 L1526 496" at={74} dur={8} c={C.red} w={3} />
        <P d="M1060 760 L1520 760" at={110} dash="4 8" w={1.5} />
      </Svg>
      {cols.map((c, i) => (
        <React.Fragment key={c.t}>
          <Txt x={c.x} y={650} a="c" text={c.t} at={70 + i * 10} size={28} font={F.serif} w={900} />
          <Txt x={c.x} y={690} a="c" text={c.d} at={78 + i * 10} size={18} color={C.soft} />
        </React.Fragment>
      ))}
      <Txt x={1290} y={796} a="c" text="恩赐的本质，更接近受控制、可回收的污染" at={130} size={30} font={F.serif} w={900} color={C.red} stagger={1.4} />
      <Txt x={1290} y={730} a="c" text="封印是卢米安的特殊处境，不是所有舞蹈家的共同代价" at={110} size={18} color={C.soft} stagger={0.8} />
      <Badge x={1290} y={850} a="c" at={170} kind="正文" src="第1040章《诱惑之泉》（概括）" />
      <Badge x={1290} y={892} a="c" at={180} kind="正文" src="疲惫：第一卷第72章 · 附身：第二卷第80章" />
    </Figure>
  );
};

export const SCENES: Record<string, React.FC> = {ch2: Ch2, f05: F05, f06: F06, f07: F07, ch3: Ch3, f08: Ritual, f09: Thorn, f10: Possession, f11: Cooperation, f12: F12};
