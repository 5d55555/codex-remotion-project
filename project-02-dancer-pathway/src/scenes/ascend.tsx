import React from 'react';
import {useCurrentFrame} from 'remotion';
import {Arrow, Badge, C, ChapterCard, circ, Dot, F, Figure, Fill, Ghost, LogoCard, P, pol, Person, prog, Svg, Theme, Txt} from '../kit';
import {TOTAL} from './intro';

export const Ch4: React.FC = () => <ChapterCard glyphs="晋升" no="第四章" name="向上" line1="序列8 → 序列0" foot="以下内容含终局剧透" />;

type Case = {text: string; kind: '原文' | '摘要' | '未找到'; src: string};

// 序列 8—0 共用的版式：左侧标题，右侧图标 + 能力清单，底部案例。
const SeqFigure: React.FC<{seq: number; name: string; sub: string; theme?: Theme; abilities: string[]; cases: Case[]; children: React.ReactNode}> = ({seq, name, sub, theme = 'paper', abilities, cases, children}) => {
  const node = 9 - seq;
  const accent = theme === 'paper' ? C.red : C.gold;
  return (
    <Figure
      theme={theme}
      rays={theme === 'navy' ? {cx: 1130, cy: 480} : undefined}
      chrome={{section: '晋升', value: `${seq} · ${name}`, node, prev: node - 1, fig: 21 - seq, total: TOTAL}}
      tag={[`序列${seq}`, name, '宿命之环途径']}
      title={name}
      sub={sub}
    >
      {children}
      <Svg>
        <P d="M1000 752 L1860 752" at={90} dur={24} w={1.2} op={0.5} />
      </Svg>
      {abilities.map((a, i) => (
        <React.Fragment key={a}>
          <Txt x={1360} y={300 + i * 78} text={String(i + 1).padStart(2, '0')} at={36 + i * 14} size={18} font={F.mono} w={500} color={accent} fade />
          <Txt x={1410} y={300 + i * 78} text={a} at={38 + i * 14} size={26} font={F.serif} w={900} stagger={1} />
        </React.Fragment>
      ))}
      {cases.map((c, i) => (
        <React.Fragment key={c.text}>
          {i === 0 && <Txt x={1000} y={796} text="案例" at={100} size={22} font={F.serif} w={900} color={accent} />}
          <Txt x={1080} y={796 + i * 72} text={c.text} at={106 + i * 20} size={21} stagger={0.6} />
          <Badge x={1080} y={832 + i * 72} at={130 + i * 20} kind={c.kind} src={c.src} />
        </React.Fragment>
      ))}
    </Figure>
  );
};

/* 序列8 托钵僧侣：一只钵，五种仪式 */
const F13: React.FC = () => {
  const rituals = ['造畜', '转运', '预言', '替代', '驱鬼'];
  return (
    <SeqFigure
      seq={8}
      name="托钵僧侣"
      sub="越清贫谦卑，力量越好用"
      abilities={['灵性增强，耐受极端环境', '直觉感应粗略运势', '祭祀知识与五种仪式魔法', '守贫 · 谦卑 · 化缘 · 传教']}
      cases={[{text: '卢米安得到托钵僧侣的力量：祭祀知识与五种仪式魔法', kind: '摘要', src: '《五个仪式魔法》'}]}
    >
      <Svg>
        <P d="M1020 500 L1240 500" at={10} dur={16} w={3.5} />
        <P d="M1030 500 Q1130 640 1230 500" at={18} dur={22} w={3.5} />
        <P d="M1095 600 L1165 600" at={30} dur={10} w={3.5} />
        {rituals.map((r, i) => {
          const [x, y] = pol(1130, 500, 170, -160 + i * 35);
          return <P key={r} d={`M1130 470 L${x} ${y + 18}`} at={50 + i * 8} dash="3 7" w={1.5} />;
        })}
      </Svg>
      {rituals.map((r, i) => {
        const [x, y] = pol(1130, 500, 190, -160 + i * 35);
        return <Txt key={r} x={x} y={y} a="c" text={r} at={56 + i * 8} size={24} font={F.serif} w={900} color={i === 1 ? C.red : undefined} />;
      })}
    </SeqFigure>
  );
};

/* 序列7 受契之人：契约书 + 灵体签名 */
const F14: React.FC = () => (
  <SeqFigure
    seq={7}
    name="受契之人"
    sub="签下契约，借来能力"
    abilities={['献祭对应事物，与生物签约', '付出代价，换取能力', '招摄之舞范围扩大', '灵体化、隐身、影子替换……']}
    cases={[{text: '与“脓肿断手”签约，先以“成为半神的命运”作代价', kind: '摘要', src: '《代价》'}]}
  >
    <Svg>
      <P d="M1030 330 L1200 330 L1200 640 L1030 640 Z" at={10} dur={30} w={3} />
      {[0, 1, 2, 3, 4].map((i) => (
        <P key={i} d={`M1056 ${380 + i * 40} L${1174 - (i % 2) * 30} ${380 + i * 40}`} at={30 + i * 5} dur={10} w={2} op={0.6} />
      ))}
      <P d="M1060 600 C1080 570 1090 620 1110 590 C1125 570 1130 610 1150 595 C1160 588 1170 598 1180 592" at={110} dur={24} c={C.red} w={3} />
      <Ghost x={1260} y={410} s={36} at={50} />
      <Arrow pts={[[1252, 460], [1240, 560], [1180, 592]]} at={86} dur={22} c={C.red} w={2} dash="5 7" />
    </Svg>
  </SeqFigure>
);

/* 序列6 苦修士：积压 */
const F15: React.FC = () => {
  const f = useCurrentFrame();
  const bands = ['灵性', '力量', '情绪', '欲望', '仪式'];
  const burst = prog(f, 170, 24);
  return (
    <SeqFigure
      seq={6}
      name="苦修士"
      sub="先压下去，再一口气放出来"
      abilities={['积压：灵性、力量、情绪、欲望', '积压仪式步骤，实战中施展', '释放时可短暂巨型化', '降低契约能力的负面影响']}
      cases={[{text: '未找到可靠出处的苦修士阶段剧情案例', kind: '未找到', src: '待原著核实'}]}
    >
      <Svg>
        <P d="M1070 330 L1070 360 C1030 380 1030 420 1030 470 L1030 620 Q1030 650 1060 650 L1200 650 Q1230 650 1230 620 L1230 470 C1230 420 1230 380 1190 360 L1190 330" at={10} dur={36} w={3.5} />
        {bands.map((b, i) => (
          <rect key={b} x={1042} y={596 - i * 50} width={176} height={44} fill={i % 2 ? 'rgba(195,38,28,0.16)' : 'rgba(43,38,32,0.12)'} opacity={prog(f, 50 + i * 16, 12)} />
        ))}
        {burst > 0 &&
          Array.from({length: 9}, (_, i) => {
            const a = -170 + i * 20;
            const [x1, y1] = pol(1130, 320, 30, a);
            const [x2, y2] = pol(1130, 320, 30 + 70 * burst, a);
            return <line key={i} x1={x1} y1={y1} x2={x2} y2={y2} stroke={C.red} strokeWidth={3} opacity={1 - 0.4 * burst} strokeLinecap="round" />;
          })}
      </Svg>
      {bands.map((b, i) => (
        <Txt key={b} x={1130} y={618 - i * 50} a="c" text={b} at={54 + i * 16} size={19} w={700} />
      ))}
    </SeqFigure>
  );
};

/* 序列5 猎命师：命运之河 + 积压槽 */
const F16: React.FC = () => {
  const f = useCurrentFrame();
  const lift = prog(f, 90, 40);
  return (
    <SeqFigure
      seq={5}
      name="猎命师"
      theme="navy"
      sub="命运，也能被猎取"
      abilities={['观察命运', '猎取命运片段', '用积压的片段交换命运', '消耗灵性，推动命运']}
      cases={[
        {text: '猎取莫兰·阿维尼的一段命运，积压进自身灵体', kind: '摘要', src: '《命运片段》'},
        {text: '积压上限三个；卢米安当时实际能长期积压两个', kind: '原文', src: '第673章'},
      ]}
    >
      <Svg>
        {[0, 1, 2, 3, 4].map((k) => (
          <P key={k} d={`M980 ${360 + k * 34} C1060 ${330 + k * 34} 1180 ${400 + k * 34} 1290 ${360 + k * 34}`} at={10 + k * 6} dur={30} c={k === 2 ? C.gold : '#d2c9b4'} w={k === 2 ? 3 : 1.5} op={k === 2 ? 1 : 0.55} />
        ))}
        {lift > 0 && <path d={`M1130 ${450 - 0} Q${1130 + 40 * lift} ${520} 1130 ${450 + 170 * lift}`} stroke={C.gold} strokeWidth={3} fill="none" strokeDasharray="6 6" />}
        <Dot x={1060} y={650} r={20} at={130} c={C.gold} />
        <Dot x={1130} y={650} r={20} at={150} c={C.gold} />
        <P d={circ(1200, 650, 20)} at={160} dash="4 6" c={C.gold} w={2} />
      </Svg>
      <Txt x={1130} y={700} a="c" text="积压上限 3" at={170} size={18} color="#d2c9b4" />
      <Txt x={986} y={318} text="命运长河" at={30} size={20} font={F.serif} w={900} color={C.gold} />
    </SeqFigure>
  );
};

/* 序列4 环中人：三个环 */
const F17: React.FC = () => {
  const f = useCurrentFrame();
  const rings = [
    {cx: 1080, cy: 450, n: '命运'},
    {cx: 1180, cy: 450, n: '因果'},
    {cx: 1130, cy: 536, n: '过去↔现在'},
  ];
  return (
    <SeqFigure
      seq={4}
      name="环中人"
      sub="把一段时间，绕成一个环"
      abilities={['制造环：命运、因果、过去与现在', '施加于敌人、环境或自身', '触发后，一切回到初始状态', '循环上限九次']}
      cases={[{text: '瓦赞·桑松以“环中人”的循环困住卢米安', kind: '原文', src: '《解除封印》'}]}
    >
      <Svg>
        {rings.map((r, i) => {
          const [ax, ay] = pol(r.cx, r.cy, 72, f * 2.4 + i * 120);
          return (
            <g key={r.n}>
              <P d={circ(r.cx, r.cy, 72)} at={10 + i * 12} dur={30} w={3} c={i === 2 ? C.red : undefined} />
              {f > 50 && <circle cx={ax} cy={ay} r={7} fill={i === 2 ? C.red : C.ink} opacity={prog(f, 50, 12)} />}
            </g>
          );
        })}
      </Svg>
      <Txt x={1040} y={360} a="c" text="命运" at={40} size={22} font={F.serif} w={900} />
      <Txt x={1222} y={360} a="c" text="因果" at={46} size={22} font={F.serif} w={900} />
      <Txt x={1130} y={630} a="c" text="过去 ↔ 现在" at={52} size={22} font={F.serif} w={900} color={C.red} />
      <Txt x={1130} y={690} a="c" text="× 9" at={120} size={40} font={F.serif} w={900} color={C.red} />
    </SeqFigure>
  );
};

/* 序列3 受难者：把苦难转给对方 */
const F18: React.FC = () => {
  const f = useCurrentFrame();
  const wave = Array.from({length: 41}, (_, i) => {
    const x = 1080 + i * 3.6;
    const y = 470 + Math.sin(i * 0.9 - f * 0.25) * 14;
    return `${i ? 'L' : 'M'}${x} ${y}`;
  }).join(' ');
  return (
    <SeqFigure
      seq={3}
      name="受难者"
      sub="我受过的苦，你也受一遍"
      abilities={['把自身承受过的苦难施加给目标', '苦难越重，攻击越强', '设定自身的“现在状态”', '象征“现在”']}
      cases={[{text: '终局中，宿命之环以此把源质撕裂之痛转加给卢米安', kind: '原文', src: '章号待查'}]}
    >
      <Svg>
        <Person x={1030} y={560} at={10} pose="stand" s={1} />
        <Person x={1240} y={560} at={18} pose="slump" s={1} />
        {[0, 1, 2].map((i) => (
          <P key={i} d={`M${1000 + i * 30} ${400 - (i % 2) * 10} l8 -14 l8 14 l8 -14`} at={40 + i * 6} dur={8} c={C.red} w={2} />
        ))}
        {f > 80 && <path d={wave} stroke={C.red} strokeWidth={3} fill="none" opacity={prog(f, 80, 14)} />}
        <Arrow pts={[[1210, 470], [1226, 470]]} at={86} dur={4} c={C.red} />
      </Svg>
      <Txt x={1030} y={650} a="c" text="受难者" at={30} size={20} w={700} />
      <Txt x={1240} y={650} a="c" text="目标" at={36} size={20} w={700} />
    </SeqFigure>
  );
};

/* 序列2 罪人：表针倒转 */
const F19: React.FC = () => {
  const f = useCurrentFrame();
  const cx = 1130;
  const cy = 480;
  const back = prog(f, 60, 120);
  const hand = -60 - back * 300;
  const [hx, hy] = pol(cx, cy, 90, hand);
  const [mx, my] = pol(cx, cy, 60, hand * 0.3 - 90);
  return (
    <SeqFigure
      seq={2}
      name="罪人"
      sub="往回拨，回到过去"
      abilities={['初步接触“过去”与“原因”', '让自身状态回到过去某一时刻', '锚定目标过去的罪，推动结果', '向过去植入虚假命运片段']}
      cases={[{text: '正文说明：罪人能让自身状态回到过去的某一时刻', kind: '原文', src: '《罪人》'}]}
    >
      <Svg>
        <P d={circ(cx, cy, 120)} at={8} dur={30} w={3.5} />
        {Array.from({length: 12}, (_, i) => {
          const [x1, y1] = pol(cx, cy, 102, i * 30);
          const [x2, y2] = pol(cx, cy, 116, i * 30);
          return <P key={i} d={`M${x1} ${y1} L${x2} ${y2}`} at={30 + i * 2} dur={6} w={i % 3 ? 1.5 : 3} />;
        })}
        {f > 50 && back > 0.02 && <path d={`M${pol(cx, cy, 140, -60).join(' ')} A140 140 0 ${back * 300 > 180 ? 1 : 0} 0 ${pol(cx, cy, 140, hand).join(' ')}`} stroke={C.red} strokeWidth={3} fill="none" strokeDasharray="6 7" />}
        {f > 40 && <line x1={cx} y1={cy} x2={hx} y2={hy} stroke={C.red} strokeWidth={4} strokeLinecap="round" />}
        {f > 40 && <line x1={cx} y1={cy} x2={mx} y2={my} stroke={C.ink} strokeWidth={5} strokeLinecap="round" />}
        <Dot x={cx} y={cy} r={8} at={40} />
      </Svg>
      <Txt x={cx} y={660} a="c" text="逆时针 · 回到过去" at={120} size={22} font={F.serif} w={900} color={C.red} />
    </SeqFigure>
  );
};

/* 序列1 救赎天使：在注定命运里开出支流 */
const F20: React.FC = () => (
  <SeqFigure
    seq={1}
    name="救赎天使"
    theme="navy"
    sub="在注定里，开一条新路"
    abilities={['初步掌控“未来”与“结果”', '在注定命运中开出支流', '把目标固定在某条支流上', '位格更高者可能反噬']}
    cases={[{text: '终局中，救赎天使分身替卢米安承受“终结”的力量', kind: '原文', src: '《永劫者》'}]}
  >
    <Svg>
      <P d="M980 480 L1100 480" at={10} dur={16} c="#d2c9b4" w={3} />
      <P d="M1100 480 C1160 480 1200 380 1280 380" at={24} dur={22} c="#d2c9b4" w={2} op={0.6} />
      <P d="M1100 480 L1280 480" at={30} dur={22} c="#d2c9b4" w={2} op={0.6} />
      <P d="M1100 480 C1160 480 1200 560 1280 560" at={36} dur={22} c="#d2c9b4" w={2} op={0.6} />
      <P d="M1150 500 C1190 560 1220 640 1280 640" at={100} dur={30} c={C.gold} w={3.5} />
      <Dot x={1280} y={640} r={10} at={130} c={C.red} ring={C.gold} />
    </Svg>
    <Txt x={1000} y={448} text="注定命运" at={20} size={20} font={F.serif} w={900} color="#d2c9b4" />
    <Txt x={1170} y={690} a="c" text="新的支流 · 被固定" at={140} size={20} font={F.serif} w={900} color={C.gold} />
  </SeqFigure>
);

/* 序列0 永劫者：过去现在未来 / 原因过程结果 合一 */
const F21: React.FC = () => {
  const f = useCurrentFrame();
  const cx = 1130;
  const cy = 480;
  const pts = ['现在', '未来', '过去'];
  const inner = ['原因', '过程', '结果'];
  return (
    <SeqFigure
      seq={0}
      name="永劫者"
      theme="navy"
      sub="首尾相接，永无尽头"
      abilities={['轮回与宿命的权柄', '过去 · 现在 · 未来，合为一体', '原因 · 过程 · 结果，合为一体']}
      cases={[{text: '终局中，卢米安在“永劫”里反复承受毁灭与重置，形成每日轮回', kind: '原文', src: '《永劫者》'}]}
    >
      <Svg>
        {f > 70 && <circle cx={pol(cx, cy, 150, -90 + f * 1.2)[0]} cy={pol(cx, cy, 150, -90 + f * 1.2)[1]} r={7} fill={C.gold} opacity={prog(f, 70, 16)} />}
        <g>
          <P d={circ(cx, cy, 150)} at={8} dur={30} c={C.cream} w={2.5} />
          {pts.map((_, i) => {
            const [x, y] = pol(cx, cy, 150, -90 + i * 120);
            const [x2, y2] = pol(cx, cy, 150, -90 + (i + 1) * 120);
            return (
              <g key={i}>
                <P d={`M${x} ${y} L${x2} ${y2}`} at={40 + i * 8} dur={18} c={C.gold} w={2} />
                <Dot x={x} y={y} r={9} at={30 + i * 6} c={C.cream} />
              </g>
            );
          })}
        </g>
        <g transform={`rotate(${-f * 0.6} ${cx} ${cy})`}>
          <P d={circ(cx, cy, 80)} at={20} dur={26} c={C.gold} w={1.5} />
          {inner.map((_, i) => {
            const [x, y] = pol(cx, cy, 80, 90 + i * 120);
            return <Dot key={i} x={x} y={y} r={6} at={50 + i * 6} c={C.gold} />;
          })}
        </g>
        <Fill d={circ(cx, cy, 26)} at={60} c={C.gold} op={0.9} />
      </Svg>
      {pts.map((p, i) => {
        const [x, y] = pol(cx, cy, 196, -90 + i * 120);
        return <Txt key={p} x={x} y={y} a="c" text={p} at={34 + i * 6} size={24} font={F.serif} w={900} />;
      })}
      <Txt x={cx} y={710} a="c" text={inner.join(' · ')} at={80} size={20} w={700} color={C.gold} />
    </SeqFigure>
  );
};

/* 图22 一个环：十个序列首尾相接 */
const F22: React.FC = () => {
  const names = ['舞蹈家', '托钵僧侣', '受契之人', '苦修士', '猎命师', '环中人', '受难者', '罪人', '救赎天使', '永劫者'];
  const cx = 1290;
  const cy = 510;
  const ang = (i: number) => 90 + i * 36;
  return (
    <Figure chrome={{section: '总结', value: '旧日 · 宿命之环', node: 10, prev: 9, fig: 22, total: TOTAL}} tag={['总结', '十个序列', '一个环']} title="一个环" sub="从一支舞，走到永劫">
      <Svg>
        {names.slice(0, 9).map((_, i) => (
          <P key={i} d={`M${pol(cx, cy, 250, ang(i)).join(' ')} A250 250 0 0 1 ${pol(cx, cy, 250, ang(i + 1)).join(' ')}`} at={20 + i * 10} dur={12} w={3} />
        ))}
        <P d={`M${pol(cx, cy, 250, ang(9)).join(' ')} A250 250 0 0 1 ${pol(cx, cy, 250, ang(10)).join(' ')}`} at={120} dash="6 8" c={C.red} w={3} />
        {names.map((_, i) => {
          const [x, y] = pol(cx, cy, 250, ang(i));
          return <Dot key={i} x={x} y={y} r={i === 0 || i === 9 ? 11 : 8} at={16 + i * 10} c={i === 0 ? C.red : undefined} />;
        })}
      </Svg>
      {names.map((n, i) => {
        const [x, y] = pol(cx, cy, 318, ang(i));
        return (
          <React.Fragment key={n}>
            <Txt x={x} y={y - 14} a="c" text={n} at={22 + i * 10} size={25} font={F.serif} w={900} color={i === 0 ? C.red : undefined} />
            <Txt x={x} y={y + 18} a="c" text={`序列${9 - i}`} at={26 + i * 10} size={15} color={C.soft} />
          </React.Fragment>
        );
      })}
      <Txt x={cx} y={490} a="c" text="宿命之环" at={140} size={44} font={F.serif} w={900} color={C.red} />
      <Txt x={cx} y={540} a="c" text="旧日支配者" at={150} size={20} color={C.soft} />
      <Txt x={pol(cx, cy, 250, 72)[0] + 10} y={pol(cx, cy, 250, 72)[1] + 50} a="c" text="轮回" at={130} size={20} font={F.serif} w={900} color={C.red} />
    </Figure>
  );
};

const Outro: React.FC = () => (
  <LogoCard
    question="宿命，从来不是一条直线。"
    under={
      <>
        <Txt x={960} y={900} a="c" width={1500} fade at={110} size={16} color={C.soft} lh={1.6} text="资料：《宿命之环》第52、62、72、73、80、83、673、1040章，《五个仪式魔法》《代价》《命运片段》《解除封印》《罪人》《永劫者》等章节；诡秘之主 Wiki；百度百科。" />
        <Txt x={960} y={948} a="c" width={1500} fade at={120} size={16} color={C.soft} lh={1.6} text="标“原文”者已读到章节正文，标“摘要”者仅见搜索摘要，待原著核实。本片为同人科普作品。" />
      </>
    }
  />
);

export const SCENES: Record<string, React.FC> = {ch4: Ch4, f13: F13, f14: F14, f15: F15, f16: F16, f17: F17, f18: F18, f19: F19, f20: F20, f21: F21, f22: F22, outro: Outro};
