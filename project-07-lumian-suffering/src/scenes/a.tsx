import React from 'react';
import {useCurrentFrame} from 'remotion';
import {Face, Lumian, SisterSilhouette} from '../lumian';
import {Bubble, Caption, Panel, Paper, Sfx, SpeedLines, Splat, Src, Stamp, Tone} from '../manga';
import {Bolt, Clock, Cracks, DogSkin, Report, SealMark} from '../props';
import {prog} from '../kit';
import {C, F} from '../theme';
import {Page} from './page';

/* 封面 */
const Cover: React.FC = () => {
  const f = useCurrentFrame();
  return (
    <Paper>
      <SpeedLines n={120} inner={0.3} seed={3} />
      <AbsoluteBox>
        <Lumian x={960} y={560} s={1.5} face="smirk" />
      </AbsoluteBox>
      <div style={{position: 'absolute', left: 0, right: 0, top: 70, textAlign: 'center', opacity: prog(f, 4, 10)}}>
        <span style={{fontFamily: F.pop, fontSize: 150, color: C.ink, WebkitTextStroke: `12px ${C.paper}`, paintOrder: 'stroke fill', letterSpacing: '0.06em'}}>卤面受苦实录</span>
      </div>
      <div style={{position: 'absolute', left: 0, right: 0, top: 270, textAlign: 'center', opacity: prog(f, 16, 10), fontFamily: F.serif, fontWeight: 900, fontSize: 40, color: C.ink}}>
        <span style={{background: C.paper, padding: '2px 20px', border: `4px solid ${C.ink}`}}>《宿命之环》· 卢米安·李</span>
      </div>
      <div style={{position: 'absolute', left: 60, bottom: 60, opacity: prog(f, 30, 10), fontFamily: F.sans, fontWeight: 700, fontSize: 22, color: C.ink, background: '#fff', border: `3px solid ${C.ink}`, padding: '6px 14px'}}>
        “卤面”是读者给卢米安起的谐音外号，官方微博也这么叫　·　情节逐条标出处
      </div>
      <Stamp x={1560} y={900} text="第 1 话" at={40} color={C.ink} rot={6} size={56} />
    </Paper>
  );
};

const AbsoluteBox: React.FC<{children: React.ReactNode}> = ({children}) => <div style={{position: 'absolute', inset: 0}}>{children}</div>;

/* 第一页：立 flag；晋升挑衅者；被控制的奥萝尔 */
const P1: React.FC = () => (
  <Page vol="第一卷" name="梦魇" base={0} events={[262, 472]}>
    <Panel x={40} y={150} w={1840} h={430} at={10} from="t">
      <Caption x={50} y={60} w={900} at={20} text="奥萝尔讲起一个人：他走了“刺客”途径，喝下序列7“女巫”魔药——身体变成了女性。" size={32} />
      <Lumian x={1400} y={210} s={0.78} face={useFaceAt([[0, 'shock'], [110, 'relief']])} sweat />
      <Sfx x={1130} y={120} text="嘶……" at={110} size={96} rot={-10} />
      <Caption x={50} y={250} w={1000} at={130} text="“……分外庆幸自己获得的第一份材料是‘猎人’非凡特性。”" size={32} dark={false} />
      <Stamp x={1700} y={330} text="立 flag" at={200} size={60} />
      <Src x={50} y={380} at={140} ev="正文" text="第一卷第69章《他？》（叙述）" />
    </Panel>
    <Panel x={40} y={600} w={900} h={440} at={240} from="l">
      <SpeedLines w={900} h={440} n={70} inner={0.3} seed={5} />
      <Lumian x={450} y={190} s={0.72} face="pain" shake={4} />
      <Sfx x={210} y={110} text="呃——" at={262} size={88} color={C.red} rot={-12} />
      <Caption x={30} y={330} w={840} at={280} text="“灼烧感、撕裂感、濒临失控的幻觉轮流来袭”" size={30} dark={false} />
      <Src x={30} y={395} at={290} ev="正文" text="第一卷第102章《转移》，晋升“挑衅者”" />
    </Panel>
    <Panel x={980} y={600} w={900} h={440} at={380} from="r" bg={C.ink}>
      <SisterSilhouette x={220} y={150} s={0.62} color="#3a3a3a" />
      <svg width={900} height={440} style={{position: 'absolute', inset: 0}}>
        <ellipse cx={196} cy={140} rx={14} ry={10} fill="none" stroke="#fff" strokeWidth={3} />
        <ellipse cx={244} cy={140} rx={14} ry={10} fill="none" stroke="#fff" strokeWidth={3} />
      </svg>
      <Bolt x1={640} y1={-20} x2={620} y2={140} at={470} />
      <Lumian x={630} y={220} s={0.6} face={useFaceAt([[0, 'shock'], [472, 'dead']])} />
      <Sfx x={760} y={70} text="轰" at={470} size={110} color="#fff" stroke={C.ink} rot={8} />
      <Caption x={20} y={20} w={560} at={400} text="“奥萝尔浅蓝色的眼眸不知什么时候也变得异常空洞”" size={24} dark={false} />
      <Caption x={300} y={330} w={580} at={500} text="“刚才那个不是奥萝尔，是被怪物控制的她！”" size={24} dark={false} />
      <Src x={20} y={402} at={510} ev="正文" text="第一卷第101、102章（循环梦境里）" />
    </Panel>
  </Page>
);

// 按时间切换表情：[[帧, 表情], ...]
function useFaceAt(list: [number, Face][]): Face {
  const f = useCurrentFrame();
  return list.reduce((v, [t, x]) => (f >= t ? x : v), list[0][1]);
}

/* 第二页：破碎；报告；封印与六点重置 */
const P2: React.FC = () => (
  <Page vol="第一卷" name="梦魇" base={2} events={[130, 340, 430]}>
    <Panel x={40} y={150} w={900} h={890} at={10} from="l" bg={C.ink}>
      <SisterSilhouette x={560} y={300} s={0.9} color="#2e2e2e" />
      <Cracks x={560} y={330} at={120} dur={40} n={14} len={340} />
      <Lumian x={210} y={560} s={0.62} face="shock" tilt={-18} />
      <Sfx x={420} y={480} text="推" at={40} size={120} color="#fff" stroke={C.ink} rot={-14} />
      <Caption x={30} y={30} w={820} at={40} text="仪式失控。奥萝尔把他推出了祭坛——" size={30} dark={false} />
      <Caption x={30} y={700} w={820} at={120} text="“奥萝尔体表出现了一道道裂缝，很快崩解成数不清的血肉。”" size={28} dark={false} />
      <Src x={30} y={830} at={130} ev="正文" text="第一卷第107章《破碎》" />
    </Panel>
    <Panel x={980} y={150} w={900} h={300} at={200} from="t">
      <Report x={30} y={24} w={830} h={240} at={210} title="联合调查组报告" lines={['科尔杜被失败的邪神仪式摧毁。', '绝大部分村民成了失败造物的养分，', '只有极少数幸存。']} size={24} />
      <Src x={500} y={258} at={240} ev="正文" text="第一卷第108章《报告》（概述）" />
    </Panel>
    <Panel x={980} y={480} w={900} h={560} at={300} from="b">
      <Tone opacity={0.18} />
      <Lumian x={300} y={240} s={0.66} face={useFaceAt([[0, 'calm'], [430, 'dead']])} gloom={false} />
      <SealMark x={260} y={355} at={330} r={30} />
      <Clock x={740} y={150} at={420} r={64} />
      <Caption x={30} y={20} w={560} at={330} text="佛尔思：“这些灵魂残片和带着强烈污染性的力量都被我主封印在了你的左胸。”" size={22} />
      <Caption x={30} y={410} w={840} at={420} text="“……每天凌晨六点重置身体状态，回归第十二夜结束时的凌晨六点”" size={24} />
      <Bubble x={500} y={300} w={360} h={110} at={500} text="永远都在那一天啊……" size={26} tail={{x: 400, y: 230}} />
      <Src x={30} y={505} at={430} ev="正文" text="第一卷第109章《微小的希望》（说话人：佛尔思）" />
    </Panel>
  </Page>
);

/* 第三页：鬼魂重伤；断骨与烧伤；体内的“祂”的算计 */
const P3: React.FC = () => (
  <Page vol="第二卷" name="逐光者" base={5} events={[60, 220, 400]}>
    <Panel x={40} y={150} w={880} h={430} at={10} from="l">
      <Tone opacity={0.25} />
      <svg width={880} height={430} style={{position: 'absolute', inset: 0}}>
        <path d="M640 60 C 720 40 800 120 770 220 C 760 300 700 360 660 420 C 640 330 600 300 560 260 C 560 160 580 80 640 60 Z" fill="#fff" stroke={C.ink} strokeWidth={4} opacity={0.85} />
        <circle cx={650} cy={140} r={10} fill={C.ink} />
        <circle cx={700} cy={140} r={10} fill={C.ink} />
      </svg>
      <Lumian x={300} y={200} s={0.62} face={useFaceAt([[0, 'pain'], [200, 'calm']])} bandage />
      <Splat x={360} y={300} at={60} r={50} seed={4} />
      <Caption x={20} y={330} w={840} at={60} text="“对正常人来说，这是连抢救都没有必要，可以等死的伤势。”" size={24} dark={false} />
      <Src x={20} y={20} at={70} ev="正文" text="第二卷第16章《挣扎》（蒙苏里鬼魂）" />
      <Sfx x={520} y={250} text="06:00 重置" at={170} size={52} rot={-6} />
    </Panel>
    <Panel x={960} y={150} w={920} h={430} at={180} from="r">
      <SpeedLines w={920} h={430} n={80} inner={0.25} seed={9} />
      <Lumian x={460} y={200} s={0.64} face="pain" bandage shake={6} />
      <Splat x={560} y={160} at={220} r={70} seed={7} />
      <Sfx x={200} y={110} text="轰！！" at={220} size={110} rot={-12} />
      <Caption x={20} y={300} w={880} at={240} text="“这撞得他肋骨断折，手腕裂开”" size={26} dark={false} />
      <Caption x={20} y={360} w={880} at={280} text="烧伤“于‘纵火家’而言顶多算需要躺一两个月的那种”" size={22} />
      <Src x={500} y={20} at={250} ev="正文" text="第二卷第151、152章" />
    </Panel>
    <Panel x={40} y={610} w={1840} h={430} at={360} from="b">
      <Tone opacity={0.15} />
      <Lumian x={300} y={200} s={0.66} face="angry" />
      <svg width={1840} height={430} style={{position: 'absolute', inset: 0}}>
        <circle cx={262} cy={330} r={34} fill={C.ink} />
        <circle cx={250} cy={326} r={6} fill="#fff" />
        <circle cx={274} cy={326} r={6} fill="#fff" />
      </svg>
      <Caption x={620} y={50} w={1180} at={380} text="“逼迫我向祂求助，答应祂提出来的某些不平等条件，为祂真正脱困创造机会。”" size={32} dark={false} />
      <Caption x={620} y={220} w={1180} at={440} text="当时以为“祂”是封在体内的忒尔弥波洛斯——第五卷才揭晓：那一直是阿蒙。" size={26} />
      <Src x={620} y={370} at={400} ev="正文" text="第二卷第153章《复盘》（内心分析）；第五卷第141、142章" />
    </Panel>
  </Page>
);

/* 第四页：变狗（自愿）；“百分之一百” */
const P4: React.FC = () => (
  <Page vol="第三、四卷" name="阴谋家 · 罪人" base={8} events={[250]}>
    <Panel x={40} y={150} w={900} h={890} at={10} from="l">
      <Lumian x={450} y={380} s={0.8} face="smirk" />
      <DogSkin x={450} y={430} at={40} s={1.3} />
      <Sfx x={250} y={140} text="噗" at={40} size={110} rot={-10} />
      <Caption x={30} y={640} w={840} at={60} text="“那张棕黄色的狗皮从中裂开，显露出了卢米安的身体。”" size={28} dark={false} />
      <Src x={30} y={800} at={70} ev="正文" text="第三卷第103章《魔女教派》（造畜之术，他自己用来潜入）" />
      <Stamp x={700} y={160} text="自愿 · 不计" at={110} color={C.gray} size={46} rot={8} />
    </Panel>
    <Panel x={980} y={150} w={900} h={890} at={200} from="r">
      <SpeedLines w={900} h={890} n={90} inner={0.35} seed={11} />
      <Lumian x={450} y={420} s={0.8} face="shock" sweat />
      <Sfx x={450} y={170} text="100%" at={250} size={170} color={C.red} rot={-6} />
      <Caption x={30} y={640} w={840} at={260} text="“……死在他手上的概率是百分之一百。”" size={30} dark={false} />
      <Caption x={30} y={730} w={840} at={300} text="他＝“西索”特瓦纳科。前提是没察觉对方的恶意——好在他察觉了。" size={22} />
      <Src x={30} y={840} at={270} ev="正文" text="第四卷第157章《未能实现的阴谋》" />
    </Panel>
  </Page>
);

export const SCENES_A: Record<string, React.FC> = {cover: Cover, p1: P1, p2: P2, p3: P3, p4: P4};
