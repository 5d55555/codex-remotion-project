import React from 'react';
import {useCurrentFrame} from 'remotion';
import {prog} from '../kit';
import {Face, Lumian} from '../lumian';
import {Bubble, Caption, Panel, Paper, Sfx, SpeedLines, Src, Stamp, Tally, Tone} from '../manga';
import {AmonSilhouette, ExtraHead, Flashback, Report, Storm, Thought} from '../props';
import {C, F} from '../theme';
import {Page} from './page';

function useFaceAt(list: [number, Face][]): Face {
  const f = useCurrentFrame();
  return list.reduce((v, [t, x]) => (f >= t ? x : v), list[0][1]);
}

// 第一卷那一格的缩小回放：松了口气的卢米安。
const RelievedMemory: React.FC = () => (
  <>
    <Lumian x={150} y={110} s={0.42} face="relief" sweat />
    <div style={{position: 'absolute', left: 250, top: 40, fontFamily: F.pop, fontSize: 40, color: C.ink}}>嘶……</div>
  </>
);

/* 第五页：规则；病人；体内一直是阿蒙；去摩罗拉 */
const P5: React.FC = () => (
  <Page vol="第五卷" name="魔女" base={9} events={[150, 270]}>
    <Panel x={40} y={150} w={1840} h={260} at={10} from="t">
      <Caption x={40} y={40} w={1300} at={20} text="“序列4开始，‘猎人’和‘魔女’可以互相跳转时，性别以最高的那个序列为准”" size={32} dark={false} />
      <Src x={40} y={190} at={30} ev="正文" text="第五卷第14章《共轭的队长和队员》" />
      <Flashback x={1420} y={20} w={380} h={210} at={60} label="第一卷第69章">
        <RelievedMemory />
      </Flashback>
    </Panel>
    <Panel x={40} y={440} w={900} h={600} at={110} from="l">
      <Report x={30} y={30} w={520} h={250} at={120} title="心理治疗报告" lines={['“有强烈的自毁倾向，', '失去了回应他人情感的能力。”']} size={26} />
      <Lumian x={700} y={190} s={0.5} face={useFaceAt([[0, 'dead'], [270, 'shock']])} gloom />
      <AmonSilhouette x={150} y={430} s={0.5} at={240} />
      <Bubble x={300} y={330} w={580} h={160} at={260} text="你第一次知道这个名字的时候，忒尔弥波洛斯就已经等于我。" size={24} tail={{x: 230, y: 420}} />
      <Src x={30} y={540} at={160} ev="正文" text="第五卷第141章《病人》（阿蒙念报告）、第142章" />
    </Panel>
    <Panel x={980} y={440} w={900} h={600} at={380} from="r">
      <Tone opacity={0.15} />
      <AmonSilhouette x={180} y={230} s={0.55} at={380} />
      <Bubble x={330} y={20} w={540} h={170} at={400} text="我只负责把你送到摩罗拉，让你成为‘绝望魔女’" size={26} tail={{x: 260, y: 150}} />
      <Lumian x={640} y={360} s={0.55} face="angry" />
      <Bubble x={40} y={300} w={460} h={210} at={490} text="我愿意去，去摩罗拉……但是，我不会接受祂预定的结局！" size={26} tail={{x: 540, y: 360}} shout />
      <Src x={30} y={545} at={420} ev="正文" text="第五卷第143章《“罪人”》（阿蒙与卢米安）" />
    </Panel>
  </Page>
);

/* 第六页：喝下；变成了“她”，flag 回收；作者原话 */
const P6: React.FC = () => {
  const f = useCurrentFrame();
  const drink = prog(f, 100, 20);
  return (
    <Page vol="第五卷" name="魔女" base={11} events={[300]}>
      <Panel x={40} y={150} w={760} h={890} at={10} from="l">
        <Caption x={30} y={30} w={700} at={20} text="晋升仪式：“让超过三万人卷入一场剧烈的瘟疫……”" size={26} />
        <Lumian x={380} y={380} s={0.72} face={drink > 0.5 ? 'pain' : 'angry'} />
        <svg width={760} height={890} style={{position: 'absolute', inset: 0}}>
          <g transform={`translate(${470 - drink * 46} ${440 - drink * 14}) rotate(${-drink * 75})`}>
            <rect x={-18} y={-60} width={36} height={80} rx={8} fill="#fff" stroke={C.ink} strokeWidth={5} />
            <rect x={-10} y={-80} width={20} height={22} fill={C.ink} />
            <rect x={-14} y={-30} width={28} height={46} fill={C.red} opacity={1 - drink * 0.8} />
          </g>
        </svg>
        <Caption x={30} y={590} w={700} at={100} text="“一边将瓶子凑到嘴边，仰头喝了下去。”" size={26} dark={false} />
        <Caption x={30} y={680} w={700} at={170} text="“但这是为了将来不再被安排，为了自己选择想要的结局！”" size={28} />
        <Src x={30} y={830} at={110} ev="正文" text="第五卷第13章《绝望的仪式》、第144章《“绝望魔女”》" />
      </Panel>
      <Panel x={830} y={150} w={1050} h={560} at={280} from="r">
        <SpeedLines w={1050} h={560} n={90} inner={0.3} seed={13} />
        <Lumian x={330} y={250} s={0.7} face="shock" longHair />
        <Sfx x={620} y={110} text="？！" at={300} size={130} rot={8} />
        <Caption x={540} y={200} w={480} at={310} text="“她又长又直的黑色头发正在爆炸的余波里轻轻飘荡。”" size={26} dark={false} />
        <Src x={540} y={500} at={320} ev="正文" text="第五卷第145章《行走的毁灭》" />
        <Flashback x={600} y={330} w={380} h={150} at={360} label="第一卷第69章">
          <RelievedMemory />
        </Flashback>
        <Stamp x={820} y={420} text="flag 回收" at={380} size={56} rot={-12} />
      </Panel>
      <Panel x={830} y={740} w={1050} h={300} at={460} from="b" bg="#fff">
        <div style={{position: 'absolute', left: 40, top: 40, right: 40, fontFamily: F.serif, fontWeight: 900, fontSize: 40, color: C.ink, lineHeight: 1.4, opacity: prog(f, 470, 12)}}>“四是卢米安被逼开始一段‘魔女’人生。”</div>
        <Src x={40} y={200} at={480} ev="作者" text="作者第五卷卷末总结《第五部总结兼请假》" />
      </Panel>
    </Page>
  );
};

/* 第七页：芙兰卡看出来的压抑；两条途径来回跳 */
const P7: React.FC = () => (
  <Page vol="第五卷" name="魔女" base={12} events={[250]}>
    <Panel x={40} y={150} w={900} h={890} at={10} from="l">
      <Tone opacity={0.3} />
      <Lumian x={450} y={360} s={0.8} face="calm" hood longHair />
      <Caption x={30} y={620} w={840} at={40} text="“真要是自己选择的，卢米安平静之下不会藏着压抑和痛苦！”" size={30} dark={false} />
      <Src x={30} y={820} at={50} ev="正文" text="第五卷第149章《祂宣称的未来》（芙兰卡看出来的）" />
    </Panel>
    <Panel x={980} y={150} w={900} h={890} at={200} from="r">
      <Lumian x={230} y={330} s={0.5} face="calm" />
      <Lumian x={670} y={330} s={0.5} face="shock" longHair />
      <svg width={900} height={890} style={{position: 'absolute', inset: 0}}>
        <path d="M330 300 L 570 300 M 550 280 L 575 300 L 550 320 M 350 360 L 330 380 L 350 400 M 330 380 L 570 380" stroke={C.ink} strokeWidth={6} fill="none" />
      </svg>
      <div style={{position: 'absolute', left: 140, top: 500, fontFamily: F.sans, fontWeight: 900, fontSize: 34}}>猎人</div>
      <div style={{position: 'absolute', left: 610, top: 500, fontFamily: F.sans, fontWeight: 900, fontSize: 34}}>魔女</div>
      <Sfx x={450} y={130} text="反复横跳" at={250} size={90} color={C.red} rot={-6} />
      <Caption x={30} y={590} w={840} at={260} text="阿蒙念的安排：“孤阴不长，独阳不生，阴中蕴阳，阳中有阴，阴阳合而万事起。”" size={24} dark={false} />
      <Caption x={30} y={720} w={840} at={320} text="跳回“猎人”，就能重新变成男人。" size={24} />
      <Src x={30} y={820} at={270} ev="正文" text="第五卷第142、149章；“反复横跳”是读者说法" />
    </Panel>
  </Page>
);

/* 第八页：为什么是我；等台风；肩上长头；差点爆炸 */
const P8: React.FC = () => (
  <Page vol="第六至八卷" name="织梦人 · 第二定律 · 永劫者" base={13} events={[200, 380, 560]}>
    <Panel x={40} y={150} w={900} h={430} at={10} from="l">
      <Lumian x={180} y={260} s={0.55} face="sad" />
      <Thought x={330} y={40} w={540} h={260} at={30} text="为什么非得安排我？" size={38} from={{x: 230, y: 180}} />
      <Src x={330} y={370} at={40} ev="正文" text="第六卷第4章（内心疑问）" />
    </Panel>
    <Panel x={980} y={150} w={900} h={430} at={180} from="r">
      <Storm cx={450} cy={220} at={190} w={900} />
      <Lumian x={250} y={220} s={0.55} face="smirk" />
      <Bubble x={400} y={40} w={470} h={190} at={200} text="先搜集剩余的材料，然后，等一场台风或者飓风。" size={26} tail={{x: 330, y: 200}} />
      <Src x={400} y={370} at={210} ev="正文" text="第七卷第50章（为晋升“天气术士”）" />
    </Panel>
    <Panel x={40} y={610} w={900} h={430} at={360} from="b">
      <Lumian x={300} y={210} s={0.55} face="shock" sweat />
      <ExtraHead x={420} y={190} at={380} s={0.6} />
      <Sfx x={620} y={90} text="咕噜" at={380} size={80} rot={10} />
      <Caption x={480} y={170} w={400} at={390} text="“卢米安的右肩，血肉又有蠕动，骤然长出了一颗新的脑袋”" size={22} dark={false} />
      <Src x={30} y={370} at={400} ev="正文" text="第七卷第78章" />
    </Panel>
    <Panel x={980} y={610} w={900} h={430} at={540} from="r">
      <SpeedLines w={900} h={430} n={80} inner={0.28} seed={21} color={C.red} />
      <Lumian x={200} y={220} s={0.55} face="pain" shake={5} />
      <Sfx x={560} y={90} text="要炸了？" at={560} size={80} rot={-8} />
      <Caption x={360} y={160} w={520} at={570} text="克莱恩决定再等一个月——“让他不在容纳‘灾祸之城’后直接爆炸，变成失控的‘旧日支配者’”" size={22} dark={false} />
      <Src x={360} y={370} at={580} ev="正文" text="第八卷第35章《诡秘》（叙述）" />
    </Panel>
  </Page>
);

/* 结尾：计数定格 + 他自己那句话 */
const End: React.FC = () => {
  const f = useCurrentFrame();
  return (
    <Page base={16} turn>
      <SpeedLines n={100} inner={0.34} seed={31} color="#d9d4ca" />
      <div style={{position: 'absolute', left: 0, right: 0, top: 120, textAlign: 'center', opacity: prog(f, 10, 10)}}>
        <span style={{fontFamily: F.pop, fontSize: 96, color: C.ink, WebkitTextStroke: `9px ${C.paper}`, paintOrder: 'stroke fill'}}>受苦计数：16</span>
      </div>
      <div style={{position: 'absolute', left: 760, top: 250, transform: 'scale(1.6)', transformOrigin: 'left top', opacity: prog(f, 20, 10)}}>
        <Tally count={16} x={0} y={0} label="本片统计" />
      </div>
      <Lumian x={420} y={700} s={0.9} face="smirk" bandage />
      <Caption x={760} y={560} w={1060} at={80} text="“但这是为了将来不再被安排，为了自己选择想要的结局！”" size={40} />
      <div style={{position: 'absolute', left: 760, top: 760, fontFamily: F.sans, fontWeight: 700, fontSize: 22, color: C.ink, opacity: prog(f, 110, 10)}}>——卢米安，第五卷第144章《“绝望魔女”》</div>
      <div style={{position: 'absolute', left: 760, top: 820, width: 1060, fontFamily: F.sans, fontWeight: 500, fontSize: 19, color: C.gray, opacity: prog(f, 140, 10)}}>计数是本片的统计方式：核实过的事件一件记一次；自愿变狗不计，第六卷的“为什么是我”不计。</div>
    </Page>
  );
};

/* 片尾：哪些是梗 */
const Credits: React.FC = () => {
  const f = useCurrentFrame();
  const rows: [string, string][] = [
    ['“卤面”', '读者给卢米安起的谐音外号，官方微博也这么叫'],
    ['“强转刺客途径”', '读者说法。作者原话是“被逼开始一段‘魔女’人生”；他是在序列4转成“绝望魔女”，不是从刺客序列9重修'],
    ['“反复横跳”', '读者说法。正文是阿蒙宣读的阴阳安排：在“魔女”和“猎人”之间来回跳'],
    ['“立 flag／回收”', '本片的说法：第一卷第69章他庆幸自己是“猎人”，第五卷变成了“她”'],
    ['受苦计数', '本片的统计，不是原作数据'],
  ];
  return (
    <Paper dark>
      <div style={{position: 'absolute', left: 0, right: 0, top: 90, textAlign: 'center', fontFamily: F.pop, fontSize: 80, color: '#fff', opacity: prog(f, 4, 10)}}>哪些是梗</div>
      {rows.map(([k, v], i) => (
        <div key={k} style={{position: 'absolute', left: 200, right: 200, top: 230 + i * 110, display: 'flex', gap: 30, opacity: prog(f, 20 + i * 14, 10)}}>
          <div style={{flex: 'none', width: 340, fontFamily: F.sans, fontWeight: 900, fontSize: 34, color: C.red}}>{k}</div>
          <div style={{fontFamily: F.sans, fontWeight: 500, fontSize: 26, color: '#fff', lineHeight: 1.45}}>{v}</div>
        </div>
      ))}
      <div style={{position: 'absolute', left: 160, right: 160, top: 860, textAlign: 'center', fontFamily: F.sans, fontWeight: 500, fontSize: 17, color: '#aaa', lineHeight: 1.6, opacity: prog(f, 110, 12)}}>
        资料：《宿命之环》第一卷第69、101、102、107—109章，第二卷第16、151—153章，第三卷第103章，第四卷第157章，第五卷第13、14、141—145、149章，第六卷第4章，第七卷第50、78章，第八卷第35章；作者《第五部总结兼请假》。外貌：第一卷第1章“黑色短发，浅蓝色双眸”。
      </div>
    </Paper>
  );
};

export const SCENES_B: Record<string, React.FC> = {p5: P5, p6: P6, p7: P7, p8: P8, end: End, credits: Credits};
