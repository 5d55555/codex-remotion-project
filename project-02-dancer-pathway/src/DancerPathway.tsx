import React from 'react';
import {AbsoluteFill, Img, Sequence, interpolate, spring, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';

const FONT = "'WenQuanYi Zen Hei', 'Noto Sans SC', 'PingFang SC', 'Microsoft YaHei', sans-serif";
const C = {ink: '#f3efe6', muted: '#a6adbb', gold: '#e8c27a', amber: '#e6b35c', green: '#8fd49f', line: 'rgba(232,194,122,0.35)'};

// 每个分镜的帧数（30fps）。合计 84 秒。
const SCENES = [150, 270, 360, 360, 360, 300, 300, 300, 120];
const STARTS = SCENES.map((_, i) => SCENES.slice(0, i).reduce((a, b) => a + b, 0));
export const DANCER_PATHWAY_DURATION = SCENES.reduce((a, b) => a + b, 0);

// 序列表（序列9 → 序列0），名称见研究结论。
const LADDER = [
  {seq: 9, name: '舞蹈家'},
  {seq: 8, name: '托钵僧侣'},
  {seq: 7, name: '受契之人'},
  {seq: 6, name: '苦修士'},
  {seq: 5, name: '猎命师'},
  {seq: 4, name: '环中人'},
  {seq: 3, name: '受难者'},
  {seq: 2, name: '罪人'},
  {seq: 1, name: '救赎天使'},
  {seq: 0, name: '永劫者'},
];

type Verify = '原文' | '摘要';

const Reveal: React.FC<{delay?: number; style?: React.CSSProperties; children?: React.ReactNode}> = ({delay = 0, style, children}) => {
  const frame = useCurrentFrame();
  const {fps} = useVideoConfig();
  const p = spring({frame: Math.max(0, frame - delay), fps, config: {damping: 200}});
  return <div style={{opacity: p, transform: `translateY(${(1 - p) * 36}px)`, ...style}}>{children}</div>;
};

const Backdrop: React.FC<{src: string}> = ({src}) => {
  const frame = useCurrentFrame();
  const scale = interpolate(frame, [0, 900], [1, 1.12], {extrapolateRight: 'clamp'});
  return (
    <AbsoluteFill style={{backgroundColor: '#05070d'}}>
      <Img src={staticFile(src)} style={{width: '100%', height: '100%', objectFit: 'cover', opacity: 0.5, transform: `scale(${scale})`}} />
      <AbsoluteFill style={{background: 'linear-gradient(180deg, rgba(5,7,13,0.85) 0%, rgba(5,7,13,0.35) 45%, rgba(5,7,13,0.92) 100%)'}} />
    </AbsoluteFill>
  );
};

const Scene: React.FC<{src: string; children: React.ReactNode}> = ({src, children}) => (
  <AbsoluteFill>
    <Backdrop src={src} />
    <AbsoluteFill style={{padding: '240px 90px 260px', display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 40}}>
      {children}
    </AbsoluteFill>
  </AbsoluteFill>
);

const Badge: React.FC<{kind: Verify}> = ({kind}) => {
  const color = kind === '原文' ? C.green : C.amber;
  return (
    <span style={{display: 'inline-block', padding: '8px 22px', border: `2px solid ${color}`, borderRadius: 999, color, fontSize: 30, letterSpacing: 2}}>
      {kind === '原文' ? '原文核对' : '摘要 · 待核实'}
    </span>
  );
};

const Label: React.FC<{text: string; delay?: number}> = ({text, delay = 0}) => (
  <Reveal delay={delay} style={{fontSize: 34, color: C.gold, letterSpacing: 4}}>{text}</Reveal>
);

const Bullets: React.FC<{items: string[]; delay: number}> = ({items, delay}) => (
  <div style={{display: 'flex', flexDirection: 'column', gap: 30}}>
    {items.map((t, i) => (
      <Reveal key={t} delay={delay + i * 16} style={{fontSize: 42, lineHeight: 1.55, paddingLeft: 36, borderLeft: `4px solid ${C.gold}`}}>
        {t}
      </Reveal>
    ))}
  </div>
);

const Source: React.FC<{text: string; delay?: number}> = ({text, delay = 0}) => (
  <Reveal delay={delay} style={{position: 'absolute', left: 90, right: 90, bottom: 150, fontSize: 28, lineHeight: 1.5, color: C.muted}}>
    {text}
  </Reveal>
);

const Intro: React.FC = () => (
  <Scene src="bg-ring.png">
    <Label text="诡秘之主 · 宿命之环" />
    <Reveal delay={10} style={{fontSize: 230, fontWeight: 700, lineHeight: 1}}>舞蹈家</Reveal>
    <Reveal delay={30} style={{fontSize: 52, color: C.ink}}>宿命之环恩赐途径 · 序列9</Reveal>
    <Reveal delay={50} style={{width: 260, height: 3, background: C.gold}} />
    <Reveal delay={60} style={{fontSize: 44, color: C.gold}}>能力与案例</Reveal>
  </Scene>
);

const LadderScene: React.FC = () => {
  const rows = [
    {key: 'old', tag: '旧日', name: '宿命之环', delay: 10 * 14, hi: true},
    ...LADDER.slice().reverse().map((r) => ({key: `s${r.seq}`, tag: `序列${r.seq}`, name: r.name, delay: (9 - r.seq) * 14, hi: r.seq === 9})),
  ];
  return (
    <AbsoluteFill>
      <Backdrop src="ladder.png" />
      <AbsoluteFill style={{padding: '180px 90px 180px', display: 'flex', flexDirection: 'column', gap: 0}}>
        <Reveal style={{fontSize: 52, marginBottom: 40}}>恩赐途径 · 从序列9到序列0</Reveal>
        {rows.map((r) => (
          <Reveal
            key={r.key}
            delay={r.delay}
            style={{display: 'flex', alignItems: 'center', gap: 34, height: 110, borderBottom: `1px solid ${C.line}`, color: r.hi ? C.gold : C.ink}}
          >
            <span style={{width: 190, fontSize: 34, color: C.muted}}>{r.tag}</span>
            <span style={{fontSize: 62, fontWeight: r.hi ? 700 : 400}}>{r.name}</span>
          </Reveal>
        ))}
        <Reveal delay={200} style={{marginTop: 50, fontSize: 40, color: C.gold}}>本视频主线：序列9 舞蹈家</Reveal>
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

const AbilityOne: React.FC = () => (
  <Scene src="dancer.png">
    <Label text="能力 · 01" />
    <Reveal delay={8} style={{fontSize: 80, fontWeight: 700, lineHeight: 1.25}}>身体柔韧性大幅提升</Reveal>
    <Reveal delay={40} style={{fontSize: 46, lineHeight: 1.6, color: C.ink}}>
      晋升序列还会提升灵性，也更容易掌握神秘舞蹈。
    </Reveal>
    <Reveal delay={70}><Badge kind="摘要" /></Reveal>
    <Source text="来源：《宿命之环》第62章《舞蹈家》（搜索摘要，待原著核实）" delay={90} />
  </Scene>
);

const AbilityTwo: React.FC = () => (
  <Scene src="dancer.png">
    <Label text="能力 · 02" />
    <Reveal delay={8} style={{fontSize: 80, fontWeight: 700, lineHeight: 1.25}}>以舞调动自然力量</Reveal>
    <Bullets delay={40} items={['以舞蹈、韵律与灵性，调动周围的自然力量', '借此向不特定的存在祈祷，产生不同效果']} />
    <Reveal delay={110} style={{display: 'flex', gap: 24}}>
      {['祭舞', '招摄之舞'].map((t) => (
        <span key={t} style={{padding: '14px 34px', border: `2px solid ${C.gold}`, borderRadius: 999, color: C.gold, fontSize: 42}}>{t}</span>
      ))}
    </Reveal>
    <Reveal delay={130}><Badge kind="摘要" /></Reveal>
    <Source text="来源：诡秘之主 Wiki「宿命之环途径」（搜索摘要，待原著核实）" delay={150} />
  </Scene>
);

const CaseOne: React.FC = () => (
  <Scene src="bg-ring.png">
    <Label text="案例 · 第80章《配合》" />
    <Reveal delay={8} style={{fontSize: 74, fontWeight: 700, lineHeight: 1.3}}>招摄之舞：召来水中的怪物</Reveal>
    <Bullets
      delay={40}
      items={['卢米安在梦境废墟起舞，召来水洼中的怪物', '为芙兰卡下水寻找镜子争取时间', '他承担了拖住怪物的风险']}
    />
    <Reveal delay={120}><Badge kind="摘要" /></Reveal>
    <Source text="来源：《宿命之环》第80章（搜索摘要，待原著核实）" delay={140} />
  </Scene>
);

const CaseTwo: React.FC = () => (
  <Scene src="bg-ring.png">
    <Label text="案例 · 第52章" />
    <Reveal delay={8} style={{fontSize: 74, fontWeight: 700, lineHeight: 1.3}}>招摄之舞：尝试让灵附身</Reveal>
    <Bullets
      delay={40}
      items={['在岩洞起舞，尝试让一名男子的灵附身', '借此读取对方关于剧场女演员的记忆', '当时尚未晋升受契之人，无法签订长期借用能力的契约']}
    />
    <Reveal delay={120}><Badge kind="摘要" /></Reveal>
    <Source text="来源：《宿命之环》第52章相关情节（搜索摘要，待原著核实）" delay={140} />
  </Scene>
);

const CaseThree: React.FC = () => (
  <Scene src="dancer.png">
    <Label text="案例 · 第83章《突遇》" />
    <Reveal delay={8} style={{fontSize: 74, fontWeight: 700, lineHeight: 1.3}}>祭舞：半激发黑色荆棘符号</Reveal>
    <Bullets
      delay={40}
      items={['卢米安已能借祭舞，半激发胸口的黑色荆棘符号', '他判断自身实力，仍认为高位格怪物危险，不贸然以其作试验']}
    />
    <Reveal delay={120}><Badge kind="原文" /></Reveal>
    <Source text="来源：《宿命之环》第一卷第83章《突遇》（章节正文）" delay={140} />
  </Scene>
);

const Limits: React.FC = () => (
  <Scene src="bg-ring.png">
    <Label text="代价与限制" />
    <Bullets
      delay={8}
      items={[
        '仪式后会精神疲惫、身体酸痛',
        '招摄之舞有风险：怪物可能附身，需要队友配合',
        '体内污染受封印，不能自由调动其中的力量',
      ]}
    />
    <Reveal delay={110}><Badge kind="摘要" /></Reveal>
    <Source text="不能把舞蹈动作视为必然奏效；以上限制均来自章节摘要，待原著核实。" delay={130} />
  </Scene>
);

const Outro: React.FC = () => (
  <Scene src="bg-ring.png">
    <Reveal style={{fontSize: 150, fontWeight: 700}}>舞蹈家</Reveal>
    <Reveal delay={20} style={{fontSize: 46, color: C.gold}}>宿命之环恩赐途径 · 序列9</Reveal>
    <Reveal delay={50} style={{fontSize: 34, lineHeight: 1.7, color: C.ink}}>
      【原文核对】：章节正文已读到。<br />【摘要 · 待核实】：搜索摘要，需对照原著。
    </Reveal>
  </Scene>
);

export const DancerPathway: React.FC = () => (
  <AbsoluteFill style={{backgroundColor: '#05070d', fontFamily: FONT, color: C.ink}}>
    <Sequence from={STARTS[0]} durationInFrames={SCENES[0]}><Intro /></Sequence>
    <Sequence from={STARTS[1]} durationInFrames={SCENES[1]}><LadderScene /></Sequence>
    <Sequence from={STARTS[2]} durationInFrames={SCENES[2]}><AbilityOne /></Sequence>
    <Sequence from={STARTS[3]} durationInFrames={SCENES[3]}><AbilityTwo /></Sequence>
    <Sequence from={STARTS[4]} durationInFrames={SCENES[4]}><CaseOne /></Sequence>
    <Sequence from={STARTS[5]} durationInFrames={SCENES[5]}><CaseTwo /></Sequence>
    <Sequence from={STARTS[6]} durationInFrames={SCENES[6]}><CaseThree /></Sequence>
    <Sequence from={STARTS[7]} durationInFrames={SCENES[7]}><Limits /></Sequence>
    <Sequence from={STARTS[8]} durationInFrames={SCENES[8]}><Outro /></Sequence>
  </AbsoluteFill>
);
