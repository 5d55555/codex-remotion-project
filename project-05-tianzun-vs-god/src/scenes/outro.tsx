import React from 'react';
import {AbsoluteFill, useCurrentFrame} from 'remotion';
import {ChaosSea, FogBank, Space} from '../bg';
import {prog} from '../kit';
import {Label, Sub, Title} from '../text';
import {C, F} from '../theme';

type Item = {text: string; note?: string; src: string; tag: '正文' | '角色推测'};
const TAG_COLOR = {正文: C.bronze, 角色推测: '#ff9a7a'};

/* 之后：两位陨落留下了什么。 */
const Legacy: React.FC = () => {
  const f = useCurrentFrame();
  const items: Item[] = [
    {text: '除了源堡和混沌海，其余源质都被天尊封印在了西大陆，由蓬莱看守。', src: '黑夜女神，《大手笔》；愚者，《宿命之环》第七卷《序列之上》', tag: '正文'},
    {text: '上帝的尸骸在混沌海形成第一块亵渎石板，旁边是偷盗者途径的唯一性。', note: '“是上帝从天尊身上撕下来的”——这一句是克莱恩的推断', src: '黑夜女神，《诡秘之主》第八部《大手笔》', tag: '正文'},
    {text: '两位都留了复活的布置；天尊还有一手，专门干扰上帝复活。', src: '愚者，《宿命之环》第七卷《序列之上》', tag: '正文'},
    {text: '封印带来末日的压力，推着众神培养新的支柱——给旧意识苏醒创造条件。', src: '黑夜女神，《诡秘之主》第八部《大手笔》', tag: '正文'},
    {text: '没有魔药体系、非凡特性稀少的西大陆，一代代人牺牲，摸索出了天师、冥道人的路。', src: '愚者，《宿命之环》第七卷《序列之上》', tag: '正文'},
  ];
  return (
    <AbsoluteFill>
      <Space dim={0.5} />
      <AbsoluteFill style={{opacity: 0.35}}>
        <FogBank level={0.8} side="left" />
        <ChaosSea level={0.88} side="right" />
      </AbsoluteFill>
      <Title x={160} y={120} a="l" text="之后" at={4} size={80} glow="rgba(255,255,255,0.4)" />
      {items.map((it, i) => {
        const at = 30 + i * 70;
        const p = prog(f, at, 14);
        return (
          <div key={i} style={{position: 'absolute', left: 160, top: 210 + i * 140, width: 1600, opacity: p, transform: `translateX(${(1 - p) * -24}px)`, display: 'flex', gap: 24}}>
            <div style={{flex: 'none', fontFamily: F.serif, fontWeight: 900, fontSize: 44, color: TAG_COLOR[it.tag], width: 50}}>{i + 1}</div>
            <div>
              <div style={{fontFamily: F.sans, fontWeight: 700, fontSize: 30, color: C.ink, lineHeight: 1.4}}>{it.text}</div>
              {it.note && <div style={{fontFamily: F.sans, fontWeight: 700, fontSize: 20, color: '#ff9a7a', marginTop: 4}}>{it.note}</div>}
              <div style={{fontFamily: F.sans, fontWeight: 500, fontSize: 17, color: C.dim, marginTop: 4}}>{it.src}</div>
            </div>
          </div>
        );
      })}
    </AbsoluteFill>
  );
};

/* 账本：哪些是正文，哪些是角色推测，哪些是本片推演。 */
const Ledger: React.FC = () => {
  const f = useCurrentFrame();
  const cols: {head: string; color: string; rows: string[]}[] = [
    {
      head: '正文明写',
      color: C.bronze,
      rows: ['聚合是本能，层次越高本能越强', '天尊是源堡的化身，上帝是混沌海的象征', '天尊第一纪容纳过永暗之河、失序之国', '陨落前，天尊把七份源质封印到西大陆', '两位近乎同归于尽', '那场战斗的细节，没谁知晓'],
    },
    {
      head: '角色推测',
      color: '#ff9a7a',
      rows: ['上帝容纳过灾祸之城（愚者）', '上帝撕下了“错误”（克莱恩）', '额外源质让本能失控（黑夜女神：“应该”）', '第一纪末同时陨落（佛尔思：“不保证正确”）', '“异常激烈却动静不大”（克莱恩）'],
    },
    {
      head: '本片推演',
      color: C.tag,
      rows: ['四个回合与出招顺序', '每个权柄的画面：门、愚弄、窃取、风暴、太阳……', '战场的样子', '两位的样貌：冕旒、云座、漩涡脸', '谁先出手、谁先倒下'],
    },
  ];
  return (
    <AbsoluteFill>
      <Space dim={0.4} />
      <Title x={960} y={110} text="哪些是真的" at={4} size={70} glow="rgba(255,255,255,0.4)" />
      {cols.map((c, i) => {
        const at = 24 + i * 30;
        const p = prog(f, at, 14);
        return (
          <div key={c.head} style={{position: 'absolute', left: 110 + i * 580, top: 200, width: 540, opacity: p}}>
            <div style={{fontFamily: F.serif, fontWeight: 900, fontSize: 36, color: c.color, borderBottom: `3px solid ${c.color}`, paddingBottom: 8}}>{c.head}</div>
            {c.rows.map((r, j) => (
              <div key={r} style={{fontFamily: F.sans, fontWeight: 500, fontSize: 24, color: C.ink, marginTop: 20, opacity: prog(f, at + 10 + j * 8, 10), lineHeight: 1.4}}>
                · {r}
              </div>
            ))}
          </div>
        );
      })}
      <Sub lines={[{at: 120, text: '能确认的只有开头和结尾。中间那一段，是推演。'}]} />
    </AbsoluteFill>
  );
};

/* 片尾 */
const End: React.FC = () => (
  <AbsoluteFill style={{backgroundColor: '#000'}}>
    <AbsoluteFill style={{opacity: 0.3}}>
      <Space dim={0.6} />
    </AbsoluteFill>
    <Label x={960} y={380} a="c" text="“那场战斗具体的情况怎么样，没谁知晓。”" at={6} size={44} font={F.serif} w={700} />
    <Label x={960} y={470} a="c" text="这只是其中一种可能。" at={60} size={34} font={F.serif} w={500} color={C.dim} />
    <Label
      x={960}
      y={940}
      a="c"
      width={1600}
      text="资料：《诡秘之主》第一部《小丑》第五章《仪式》、第六章《非凡者》，第六部《逐光者》第一百一十三章《最后的守护》，第八部《愚者》第十六章《大手笔》；《宿命之环》第六卷《织梦人》第一百二十六章《解读象征》，第七卷《第二定律》第五十四章《神性与人性》、第五十五章《序列之上》，第八卷《永劫者》第七章《选择》；起点《诡秘之主》设定集（混沌海、源堡、各途径条目）。战斗过程为推演，原作没有写过。"
      at={110}
      size={16}
      color={C.dim}
    />
  </AbsoluteFill>
);

export const SCENES_OUTRO: Record<string, React.FC> = {legacy: Legacy, ledger: Ledger, end: End};
