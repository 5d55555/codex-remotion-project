/*
 * 满配牧羊人放牧的七个序列5灵魂。
 * 每个灵魂的三种能力是我替牧羊人挑的（推演），挑法只考虑怎么对付逝者；
 * 能力本身按出处标注，详见 notes/souls/。
 */

export type Src = '正文' | '设定集' | 'Wiki';
export type Ability = {name: string; seq?: string; desc: string; src: Src};
export type Soul = {id: string; name: string; path: string; color: string; role: string; abilities: Ability[]};

export const SOULS: Soul[] = [
  {
    id: 'light',
    name: '光之祭司',
    path: '太阳途径',
    color: '#ffe14a',
    role: '抗恐惧',
    abilities: [
      {name: '免疫恐惧', seq: '序列7', desc: '自身不再感到恐惧', src: '设定集'},
      {name: '净化光环', desc: '带来强烈的勇气，驱散邪恶', src: 'Wiki'},
      {name: '神圣之光', desc: '光柱从天而降，要后仰张开双臂', src: 'Wiki'},
    ],
  },
  {
    id: 'sea',
    name: '海洋歌者',
    path: '水手途径',
    color: '#3d9bff',
    role: '追击',
    abilities: [
      {name: '风之束缚', desc: '用风缠住目标', src: '设定集'},
      {name: '借风奔跑', seq: '序列6', desc: '脚下生风，跑速提高一倍以上', src: '设定集'},
      {name: '雷击', desc: '银白电光，可致麻痹', src: 'Wiki'},
    ],
  },
  {
    id: 'puppet',
    name: '秘偶大师',
    path: '占卜家途径',
    color: '#ff6fae',
    role: '减速',
    abilities: [
      {name: '灵体之线', desc: '先迟缓、僵硬；刚晋升时初步控制约20秒', src: '正文'},
      {name: '空气弹', seq: '序列7', desc: '威力接近特制左轮子弹', src: '正文'},
      {name: '纸人替身', seq: '序列7', desc: '关键时刻替自己挨一下', src: '正文'},
    ],
  },
  {
    id: 'travel',
    name: '旅行家',
    path: '学徒途径',
    color: '#5dff7a',
    role: '堵路',
    abilities: [
      {name: '闪现', desc: '短距离连续改变位置', src: '设定集'},
      {name: '旅行', desc: '灵界穿梭；不会自动追踪别人', src: 'Wiki'},
      {name: '开门', seq: '序列9', desc: '穿过不太厚的墙和门', src: 'Wiki'},
    ],
  },
  {
    id: 'gate',
    name: '看门人',
    path: '收尸人途径',
    color: '#9fb0c0',
    role: '关门',
    abilities: [
      {name: '冥界之门', desc: '有限度召出门，把目标吸进去', src: 'Wiki'},
      {name: '灵视', seq: '序列9', desc: '看见部分恶灵；识破假死未找到', src: 'Wiki'},
      {name: '亡者之语', seq: '序列6', desc: '驱使灵体；对活人有没有用不明', src: 'Wiki'},
    ],
  },
  {
    id: 'luck',
    name: '赢家',
    path: '怪物途径',
    color: '#b98cff',
    role: '运气',
    abilities: [
      {name: '好运', desc: '积攒幸运，关键时刻化险为夷', src: '设定集'},
      {name: '精神风暴', seq: '序列6', desc: '让对方眩晕、心智迷失', src: '设定集'},
      {name: '引来灾祸', seq: '序列6', desc: '让灾祸波及目标', src: '设定集'},
    ],
  },
  {
    id: 'guard',
    name: '守护者',
    path: '战士途径',
    color: '#ff8a4c',
    role: '打手',
    abilities: [
      {name: '黎明骑士体魄', seq: '序列6', desc: '巨人般的力量，奔袭快', src: 'Wiki'},
      {name: '黎明之剑', seq: '序列6', desc: '每一击都带净化', src: 'Wiki'},
      {name: '守护', desc: '放弃攻击，换来极强的防御', src: '设定集'},
    ],
  },
];

export const SOUL = Object.fromEntries(SOULS.map((s) => [s.id, s])) as Record<string, Soul>;
