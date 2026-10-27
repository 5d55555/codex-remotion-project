import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {Border, Chip, DateStamp, FilmLook, Intertitle, Kind, Narr, Quote, Slogan, Src} from '../reel';
import {prog} from '../kit';
import {Airships, Chapel, Charter, Clause, Emblem, GoldLayer, March, Newspaper, Priest, Skyline, SkyWar} from '../props';
import {C, F, rnd} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

const NightSky: React.FC = () => <AbsoluteFill style={{background: 'linear-gradient(180deg, #0e0b08 0%, #2a2118 100%)'}} />;

// 爆炸时的震动
const Quake: React.FC<{hits: number[]; children: React.ReactNode}> = ({hits, children}) => {
  const f = useCurrentFrame();
  const k = hits.reduce((v, at) => (f >= at && f < at + 14 ? Math.max(v, (1 - (f - at) / 14) * 14) : v), 0);
  return <AbsoluteFill style={{transform: `translate(${(rnd(f) - 0.5) * 2 * k}px, ${(rnd(f + 7) - 0.5) * 2 * k}px)`}}>{children}</AbsoluteFill>;
};

/* 战争：会长的“潮流”预言，开战，国王驾崩 */
const War: React.FC = () => {
  const f = useCurrentFrame();
  const bombs = [40, 60, 75, 95, 120, 140];
  return (
    <FilmLook>
      {f < 130 && (
        <>
          <NightSky />
          <AbsoluteFill style={{opacity: 0.35}}>
            <Skyline base={900} smoke={0.4} windows={0.3} shade="#120d08" />
          </AbsoluteFill>
          <Quote y={480} at={10} until={130} text="“一场战争，一场席卷全世界的战争。”" kind="原文" who="赫温·兰比斯（黄昏隐士会会长）谈接下来的潮流" src="第五部《红祭司》第107章《会长》" size={64} />
        </>
      )}
      {f >= 130 && f < 300 && (
        <Quake hits={bombs.map((b) => 130 + b + 14)}>
          <NightSky />
          <Skyline base={900} smoke={0.6} windows={0.35} shade="#120d08" />
          <Airships at={130} bombs={bombs} />
          <DateStamp text="一三五〇年十月" at={134} sub="战争爆发" />
          <Newspaper at={214} until={300} date="第五纪一三五〇年十月" headline="开　战" subs={['弗萨克空袭贝克兰德', '鲁恩宣战']} note="年份与经过据诡秘之主 Wiki 整理" rot={2} />
        </Quake>
      )}
      {f >= 300 && (
        <>
          <NightSky />
          <AbsoluteFill style={{opacity: 0.3}}>
            <Skyline base={900} smoke={0.4} windows={0.1} shade="#120d08" />
          </AbsoluteFill>
          <DateStamp text="一三五〇年十二月" at={302} />
          <Newspaper at={304} until={398} date="第五纪一三五〇年十二月" headline="国王驾崩" subs={['乔治三世逝世']} note="真相：晋升黑皇帝的仪式失败，失控身亡（第五部第203章《逃》）" rot={-2} />
          <Quote y={480} at={402} text="“这样的结局，你接受吗？”" kind="原话" who="亚当，对克莱恩说" src="第五部《红祭司》第203章《逃》——祂说，国王一死，战争才真正席卷世界" size={70} />
        </>
      )}
    </FilmLook>
  );
};

/* 神战：诸神下场，战神陨落，战争结束 */
const GodWar: React.FC = () => {
  const f = useCurrentFrame();
  return (
    <FilmLook>
      <NightSky />
      <SkyWar at={0} />
      <March at={0} rows={4} speed={3} />
      <DateStamp text="一三五一 — 一三五二年" at={4} />
      <Narr
        lines={[
          {at: 20, text: '诸神下场。'},
          {at: 120, text: '战神陨落，战争结束。'},
        ]}
      />
      <Src at={30} text="第六部《逐光者》第108—116章" />
      <Slogan text="潮流来了，就挡不住。" at={170} y={440} size={92} />
      {f >= 150 && <AbsoluteFill style={{backgroundColor: '#fff4d6', opacity: interpolate(f, [150, 156, 176], [0, 0.6, 0], clamp)}} />}
    </FilmLook>
  );
};

/* 宗旨：黄昏隐士会干涉历史；观众途径的成神仪式（推测） */
const Creed: React.FC = () => {
  const f = useCurrentFrame();
  const out = 1 - prog(f, 350, 16);
  const src = '第五部《红祭司》第90章《“预想”的发展》';
  return (
    <FilmLook>
      <AbsoluteFill style={{background: 'radial-gradient(ellipse at 50% 50%, #3a2d20, #0e0a06 75%)'}} />
      <AbsoluteFill style={{opacity: out}}>
        <Charter at={0} title="黄昏隐士会">
          <Clause at={26} text="“他们干涉着历史的进展，让它符合自身的需要，以此在某个节点完成目标。”" kind="原文" who="欲望使徒（被通灵时说出的情报）" src="第二部《无面人》第214章《时代的选择》" size={32} />
          <div style={{opacity: prog(f, 140, 12), fontFamily: F.serif, fontWeight: 900, fontSize: 28, textAlign: 'center', letterSpacing: '0.3em', margin: '6px 0 18px'}}>— 观众途径的成神仪式 —</div>
          <Clause at={164} text="“‘观众’的成神仪式就是让时代的发展符合自己的空想？”" kind="推断" who="克莱恩（读罗塞尔日记后）" src={src} size={30} />
          <Clause at={214} text="“……在规定的时代潮流中，服食魔药晋升？”" kind="推断" who="罗塞尔日记" src={src} size={30} />
          <Clause at={264} text="“等到时代浪潮滚滚而来，短时间内无法更改，祂就将登临神位？”" kind="推断" who="克莱恩" src={src} size={30} />
        </Charter>
      </AbsoluteFill>
      <Slogan text="你以为是你的选择——" at={360} y={470} size={80} note={false} />
      <Slogan text="其实，是时代的选择。" at={392} y={590} size={80} />
    </FilmLook>
  );
};

/* 小教堂：白袍、淡金胡须的神父，饮下金色魔药 */
const ChapelScene: React.FC = () => {
  const f = useCurrentFrame();
  const lift = prog(f, 60, 50);
  const drink = prog(f, 140, 50);
  const cup = 1 - prog(f, 200, 20);
  const eyes = prog(f, 212, 26);
  const flood = prog(f, 232, 110);
  const title = f >= 296;
  const pr = {x: 960, y: 1000, s: 1.1};
  return (
    <FilmLook
      sepia={1}
      color={
        <>
          <GoldLayer x={pr.x} y={pr.y} s={pr.s} lift={lift} drink={drink} cup={cup} eyes={eyes} flood={flood * (title ? 0.75 : 1)} />
          {title && (
            <>
              <div style={{position: 'absolute', left: 0, right: 0, top: 230, textAlign: 'center', fontFamily: F.serif, fontWeight: 900, fontSize: 150, letterSpacing: '0.3em', color: C.gold, opacity: prog(f, 300, 20), textShadow: `0 0 40px ${C.goldDeep}, 0 0 12px #000`}}>空想家</div>
              <div style={{position: 'absolute', left: 0, right: 0, top: 410, textAlign: 'center', fontFamily: F.serif, fontWeight: 900, fontSize: 60, letterSpacing: '0.6em', color: '#fff2c4', opacity: prog(f, 320, 20), textShadow: '0 0 12px #000'}}>亚当</div>
            </>
          )}
        </>
      }
    >
      <Chapel light={1} />
      <Priest x={pr.x} y={pr.y} s={pr.s} lift={lift} drink={drink} />
      {title && <AbsoluteFill style={{background: '#0b0907', opacity: prog(f, 296, 20) * 0.55}} />}
      <DateStamp text="一三五二年初" at={6} sub="贝克兰德郊外，一座小教堂" />
      {!title && (
        <Narr
          lines={[
            {at: 60, text: '白袍、淡金胡须的神父，举起一杯金黄色的魔药。'},
            {at: 150, text: '饮下。'},
          ]}
        />
      )}
      {!title && <Src at={70} y={890} text="第六部《逐光者》第116章《光》，叙述者直接描写" />}
      {title && <Quote y={720} at={346} text="“亚当已借助之前的全面战争晋升序列0，成为真神，可以被称为‘空想家’了。”" kind="原文" who="克莱恩写下的分析前提" src="第七部《倒吊人》第53章《“保命咒语”》" size={34} width={1600} />}
    </FilmLook>
  );
};

/* 口号 */
const SloganCard: React.FC = () => (
  <FilmLook>
    <Intertitle
      lines={[
        {at: 16, text: '时代潮流，', size: 120},
        {at: 46, text: '不可抗拒。', size: 120},
        {at: 110, text: '顺应它。', size: 80, color: C.sepia},
      ]}
      foot={{at: 140, text: '宣传词为本片所写　·　黄昏隐士会徽记为本片设计'}}
    />
    <Emblem x={960} y={190} r={60} at={6} />
  </FilmLook>
);

/* 片尾：哪些是原话 */
const Credits: React.FC = () => {
  const f = useCurrentFrame();
  const cols: {head: string; kind: Kind; rows: string[]}[] = [
    {head: '亚当的原话', kind: '原话', rows: ['“所有命运的馈赠，早已在暗中标注好了价格，不是吗？”（第四部第213章）', '“这样的结局，你接受吗？”（第五部第203章）']},
    {
      head: '原作里的原文与推断',
      kind: '原文',
      rows: ['欲望使徒：干涉历史的进展（第二部第214章）', '赫温·兰比斯：席卷全世界的战争（第五部第107章）', '罗塞尔日记、克莱恩：成神仪式的推测（第五部第90章）', '叙述：白袍神父饮下金色魔药（第六部第116章）', '克莱恩：借全面战争成为空想家（第七部第53章）'],
    },
    {head: '本片宣传词', kind: '宣传词', rows: ['时代，正在前进。', '铁轨，不会为谁转弯。', '雾会散。时代，不会停下。', '潮流来了，就挡不住。', '你以为是你的选择——其实，是时代的选择。', '时代潮流，不可抗拒。顺应它。']},
  ];
  return (
    <FilmLook>
      <AbsoluteFill style={{backgroundColor: C.black}} />
      <Border inset={50} />
      <div style={{position: 'absolute', left: 0, right: 0, top: 110, textAlign: 'center', fontFamily: F.serif, fontWeight: 900, fontSize: 64, color: C.cream, letterSpacing: '0.2em', opacity: prog(f, 4, 12)}}>哪些是原话</div>
      {cols.map((c, i) => (
        <div key={c.head} style={{position: 'absolute', left: 130 + i * 570, top: 220, width: 520, opacity: prog(f, 20 + i * 24, 14)}}>
          <div style={{display: 'flex', alignItems: 'center', gap: 12, borderBottom: `2px solid ${C.sepia}`, paddingBottom: 8}}>
            <Chip kind={c.kind} />
            <span style={{fontFamily: F.serif, fontWeight: 900, fontSize: 32, color: C.cream}}>{c.head}</span>
          </div>
          {c.rows.map((r, j) => (
            <div key={r} style={{fontFamily: F.sans, fontWeight: 500, fontSize: 22, color: C.cream, marginTop: 18, lineHeight: 1.45, opacity: prog(f, 30 + i * 24 + j * 8, 10)}}>
              {r}
            </div>
          ))}
        </div>
      ))}
      <div style={{position: 'absolute', left: 130, right: 130, top: 930, textAlign: 'center', fontFamily: F.sans, fontWeight: 500, fontSize: 17, color: C.sepia, lineHeight: 1.5, opacity: prog(f, 120, 16)}}>
        资料：《诡秘之主》第一部第4章《占卜》、第二部第214章《时代的选择》、第三部第135章《神秘的亚当》、第四部第213章《故事的尾声》、第五部第15章《告知式提醒》、第88章《收获》、第90章《“预想”的发展》、第107章《会长》、第203章《逃》、第六部第108—116章、第七部第53章《“保命咒语”》；开战、大雾霾、乔治三世的年份据诡秘之主 Wiki 年表。
      </div>
    </FilmLook>
  );
};

export const SCENES_B: Record<string, React.FC> = {war: War, godwar: GodWar, creed: Creed, chapel: ChapelScene, slogan: SloganCard, credits: Credits};
