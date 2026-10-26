import React from 'react';
import {AbsoluteFill, interpolate, useCurrentFrame} from 'remotion';
import {Chaos, ChaosSea, Fog, FogBank, Space} from '../bg';
import {BronzeTable, God, Tianzun, Yuanbao} from '../figures';
import {Core, Flash, Orb} from '../fx';
import {prog} from '../kit';
import {Label, Sub, Tag, Title, Typer} from '../text';
import {C, F} from '../theme';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;

/* 冷开场：原作原话——没人知道那一战怎么打的。 */
const Cold: React.FC = () => {
  const f = useCurrentFrame();
  const out = 1 - prog(f, 250, 20);
  return (
    <AbsoluteFill style={{backgroundColor: '#000'}}>
      <AbsoluteFill style={{opacity: 0.35 * out}}>
        <Space dim={0.6} />
      </AbsoluteFill>
      <AbsoluteFill style={{opacity: out}}>
        <Typer x={960} y={400} a="c" text="“那场战斗具体的情况怎么样，没谁知晓，" at={14} size={50} cps={2.2} />
        <Typer x={960} y={480} a="c" text="就连灵界七光都无从了解。”" at={58} size={50} cps={2.2} />
        <Label x={960} y={566} a="c" text="——黑夜女神，《诡秘之主》第八部《愚者》第十六章《大手笔》" at={110} size={20} color={C.dim} />
        <Title x={960} y={730} text="那就推演一遍。" at={170} size={72} color={C.ink} glow="rgba(192,57,43,0.8)" />
      </AbsoluteFill>
    </AbsoluteFill>
  );
};

/* 标题：两位的剪影从两侧亮起。 */
const TitleCard: React.FC = () => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill>
      <Space dim={0.7} />
      <AbsoluteFill style={{opacity: 0.55, filter: 'brightness(0.55)'}}>
        <FogBank level={0.7} side="left" />
        <ChaosSea level={0.84} side="right" />
        <Tianzun x={330} y={1060} h={900} at={0} glow={0.3} />
        <God x={1600} y={1050} h={940} at={0} glow={0.3} />
      </AbsoluteFill>
      <Title x={700} y={440} text="天尊" at={20} size={170} color={C.bronze} glow="rgba(201,164,92,0.7)" />
      <Title x={1240} y={440} text="上帝" at={40} size={170} color="#ffffff" glow="rgba(199,139,255,0.8)" />
      <div style={{position: 'absolute', left: 960, top: 300, width: 2, height: 300 * prog(f, 30, 20), background: 'linear-gradient(180deg, transparent, #fff, transparent)'}} />
      <Label x={960} y={640} a="c" text="第一纪 · 两根支柱的同归于尽" at={70} size={34} font={F.serif} w={700} ls={0.2} />
      <Tag text="象征 · 权柄 · 源质 —— 推演" at={90} x={812} y={700} />
      <Flash at={20} dur={10} color={C.bronze} peak={0.4} />
      <Flash at={40} dur={10} color="#c78bff" peak={0.4} />
    </AbsoluteFill>
  );
};

/* 起源：最初造物主的不同人格；聚合是本能。 */
const Origin: React.FC = () => {
  const f = useCurrentFrame();
  const split = prog(f, 70, 80);
  const pull = prog(f, 330, 100);
  const lx = 960 - split * 520 + pull * 200;
  const rx = 960 + split * 520 - pull * 200;
  return (
    <AbsoluteFill>
      <Space dim={0.8} />
      <Core x={960} y={470} at={0} until={90} r={120} />
      {f >= 70 && (
        <>
          <div style={{position: 'absolute', left: lx, top: 470, width: 260, height: 260, borderRadius: '50%', transform: 'translate(-50%, -50%)', overflow: 'hidden', opacity: split, boxShadow: `0 0 80px ${C.bronze}`, border: `2px solid ${C.bronze}`}}>
            <AbsoluteFill style={{backgroundColor: '#22262d'}} />
            <Fog opacity={1} speed={2} scale={0.6} />
          </div>
          <div style={{position: 'absolute', left: rx, top: 470, width: 260, height: 260, borderRadius: '50%', transform: 'translate(-50%, -50%)', overflow: 'hidden', opacity: split, boxShadow: '0 0 80px #c78bff', border: '2px solid #fff'}}>
            <Chaos opacity={1} speed={2} scale={0.5} />
          </div>
          <Label x={lx} y={650} a="c" text="诡秘之主（天尊）" at={110} size={26} font={F.serif} w={700} color={C.bronze} />
          <Label x={rx} y={650} a="c" text="古老年代的“上帝”" at={118} size={26} font={F.serif} w={700} />
        </>
      )}
      <Label x={960} y={150} a="c" text="最初造物主" at={4} until={100} size={40} font={F.serif} w={900} />
      <Label x={960} y={130} a="c" text="“用同一个存在的不同人格来比喻可能更加准确。”" at={120} until={250} size={32} font={F.serif} w={700} />
      <Label x={960} y={130} a="c" text="“……都有无法遏制的重新聚合在一起，成为整个宇宙化身，" at={250} until={440} size={30} font={F.serif} w={700} />
      <Label x={960} y={180} a="c" text="也就是最初造物主的本能。”" at={258} until={440} size={30} font={F.serif} w={700} />
      <Label x={960} y={790} a="c" text="“聚合是本能。层次越高，本能越强。”" at={340} until={450} size={34} font={F.serif} w={900} color="#ffe9b0" />
      <Label x={960} y={850} a="c" text="以上引文均出自黑夜女神，《诡秘之主》第八部《愚者》第十六章《大手笔》" at={130} until={450} size={17} color={C.dim} />
      <Sub
        lines={[
          {at: 20, text: '一切的源头：最初造物主。'},
          {at: 120, text: '天尊与上帝，像是祂的不同人格。'},
          {at: 260, text: '祂们都想重新聚合，变回那一位。'},
          {at: 360, text: '第一纪中前期，两位又各自掌握了额外的源质。'},
        ]}
      />
    </AbsoluteFill>
  );
};

/* 资料卡的一行：标题、内容、出处。 */
const Row: React.FC<{x: number; y: number; at: number; head: string; body: string; src: string; color: string; dashed?: boolean; w?: number}> = ({x, y, at, head, body, src, color, dashed, w = 760}) => {
  const f = useCurrentFrame();
  const p = prog(f, at, 12);
  return (
    <div style={{position: 'absolute', left: x, top: y, width: w, opacity: p, transform: `translateY(${(1 - p) * 16}px)`, borderLeft: `4px ${dashed ? 'dashed' : 'solid'} ${color}`, paddingLeft: 18}}>
      <div style={{fontFamily: F.serif, fontWeight: 900, fontSize: 30, color}}>{head}</div>
      <div style={{fontFamily: F.sans, fontWeight: 500, fontSize: 24, color: C.ink, marginTop: 6, lineHeight: 1.5}}>{body}</div>
      <div style={{fontFamily: F.sans, fontWeight: 500, fontSize: 16, color: C.dim, marginTop: 6}}>{src}</div>
    </div>
  );
};

/* 天尊资料卡 */
const TzCard: React.FC = () => {
  const f = useCurrentFrame();
  const orbit = (i: number, r: number) => {
    const a = f * 0.012 + (i * Math.PI * 2) / 3;
    return {x: 470 + Math.cos(a) * r, y: 560 + Math.sin(a) * r * 0.35};
  };
  return (
    <AbsoluteFill>
      <Space dim={0.8} />
      <Yuanbao x={520} y={300} w={1000} opacity={0.55} at={0} />
      <FogBank level={0.66} />
      <BronzeTable x={470} y={985} w={1000} at={200} />
      <Tianzun x={470} y={1010} h={820} at={0} glow={0.7} />
      {(['yuanbao', 'river', 'disorder'] as const).map((k, i) => {
        const p = orbit(i, 330);
        return <Orb key={k} x={p.x} y={p.y} r={30} kind={k} at={60 + i * 50} label={['源堡', '永暗之河', '失序之国'][i]} />;
      })}
      <Title x={1000} y={130} a="l" text="天尊" at={4} size={96} color={C.bronze} glow="rgba(201,164,92,0.6)" />
      <Label x={1240} y={140} text="福生玄黄天尊" at={20} size={44} font={F.brush} w={400} color={C.ink} />
      <Label x={1240} y={192} text="尊名见《诡秘之主》第一部《小丑》第五章《仪式》" at={26} size={16} color={C.dim} />
      <Row x={1000} y={270} at={50} head="化身：源堡" body="灰雾之上的古老宫殿。对应途径：占卜家、学徒、偷盗者（序列0：愚者、门、错误）" src="“天尊是‘源堡’的化身”——愚者，《宿命之环》第七卷第五十四章《神性与人性》" color={C.bronze} />
      <Row x={1000} y={470} at={100} head="第一纪容纳过：永暗之河" body="不眠者、战士、收尸人（序列0：黑暗、黄昏巨人、死神）" src="同章，愚者明言" color="#7f95ff" />
      <Row x={1000} y={640} at={150} head="第一纪容纳过：失序之国" body="仲裁人、律师（序列0：审判者、黑皇帝）" src="同章，愚者明言；途径对应见起点设定集" color="#e0546f" />
      <Label x={60} y={60} text="形象为设计：原作没有写过天尊的样貌；冕旒、云座都是本片的处理" at={240} size={17} color={C.dim} />
      <Sub
        lines={[
          {at: 40, text: '天尊：源堡的化身。'},
          {at: 200, text: '灰雾之上，青铜长桌，高背椅——那是源堡里的样子。'},
          {at: 300, text: '第一纪，祂还容纳过永暗之河、失序之国。'},
        ]}
      />
    </AbsoluteFill>
  );
};

/* 上帝资料卡 */
const GodCard: React.FC = () => {
  const f = useCurrentFrame();
  const orbit = (i: number, r: number) => {
    const a = -f * 0.012 + i * Math.PI;
    return {x: 1450 + Math.cos(a) * r, y: 560 + Math.sin(a) * r * 0.35};
  };
  return (
    <AbsoluteFill>
      <Space dim={0.8} />
      <ChaosSea level={0.76} />
      <God x={1450} y={990} h={860} at={0} glow={0.7} cast={interpolate(f, [80, 120], [0, 0.7], clamp)} />
      {(['chaos', 'calamity'] as const).map((k, i) => {
        const p = orbit(i, 340);
        return <Orb key={k} x={p.x} y={p.y} r={30} kind={k} at={60 + i * 70} dashed={k === 'calamity'} label={k === 'chaos' ? '混沌海' : '灾祸之城（推测）'} />;
      })}
      <Title x={80} y={130} a="l" text="上帝" at={4} size={96} color="#ffffff" glow="rgba(199,139,255,0.7)" />
      <Label x={320} y={140} text="原初上帝" at={20} size={40} font={F.serif} w={700} color={C.ink} />
      <Label x={320} y={190} text="上帝 · 创造者 · 造物主 · 全知全能者 · 星界之主（起点设定集“混沌海”条目）" at={26} size={16} color={C.dim} />
      <Row x={80} y={270} at={50} head="象征：混沌海" body="包容所有颜色的海。对应途径：观众、水手、阅读者、秘祈人、歌颂者（序列0：空想家、暴君、白塔、倒吊人、太阳）" src="“原初那位上帝是‘混沌海’的象征”——愚者，《宿命之环》第七卷第五十四章《神性与人性》" color="#c78bff" />
      <Row x={80} y={500} at={120} head="可能容纳过：灾祸之城" body="猎人、魔女（序列0：红祭司、魔女）" src="同章，愚者原话是“推测”——不是定论" color={C.calamity} dashed />
      <Row x={80} y={680} at={190} head="漩涡脸" body="没有眼睛、鼻子、嘴巴，由包容所有颜色的混沌液体构成" src="《宿命之环》第八卷《永劫者》第七章《选择》中的显现；本片借来当形象，不等于本体样貌" color="#ffffff" />
      <Sub
        lines={[
          {at: 40, text: '上帝：混沌海的象征。'},
          {at: 140, text: '祂可能还容纳过灾祸之城——这一条只是愚者的推测。'},
          {at: 260, text: '两位都握着复数的源质。聚合的本能，压不住了。'},
        ]}
      />
    </AbsoluteFill>
  );
};

export const SCENES_INTRO: Record<string, React.FC> = {cold: Cold, title: TitleCard, origin: Origin, tz: TzCard, god: GodCard};
