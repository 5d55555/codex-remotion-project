// 第二版的文字轨：时间取自分镜表里写了字幕的那些切点（秒），日文原文 + 中文。
import {TextItem} from '../engine/stage';

type T = {at: number; dur: number; item: Omit<TextItem, 'age' | 'dur'>};
const G = '#5ab8f2', S = '#e0303f';

export const TEXTS: T[] = [
  {at: 0.3, dur: 4.6, item: {kind: 'card', jp: '東京 · 新宿', cn: '2018 年 12 月 24 日'}},
  {at: 15.356, dur: 2.4, item: {kind: 'tech', jp: '帳', cn: '伊地知布下的“帐”：遮住咒力，掩护先手', x: 0.2, y: 0.42, size: 150, color: '#b9a8ff'}},
  {at: 17.913, dur: 2.5, item: {kind: 'quote', jp: 'まあ勝つさ', cn: '总之，我会赢的。', color: G, size: 52}},
  {at: 28.14, dur: 3.2, item: {kind: 'chant', jp: '九綱', cn: '九纲', x: 0.86, y: 0.16, size: 150, color: '#dbe8ff'}},
  {at: 31.549, dur: 2.5, item: {kind: 'chant', jp: '偏光', cn: '偏光', x: 0.86, y: 0.16, size: 150, color: '#dbe8ff'}},
  {at: 34.106, dur: 3.3, item: {kind: 'chant', jp: '烏と声明', cn: '乌与声明', x: 0.86, y: 0.16, size: 110, color: '#dbe8ff'}},
  {at: 37.515, dur: 3.3, item: {kind: 'chant', jp: '表裏の間', cn: '表里之间', x: 0.86, y: 0.16, size: 110, color: '#dbe8ff'}},
  {at: 46.89, dur: 2.2, item: {kind: 'tech', jp: '出力 200%', cn: '两倍出力', x: 0.74, y: 0.3, size: 110, color: '#c79bff'}},
  {at: 51.152, dur: 1.7, item: {kind: 'chant', jp: '虚式', x: 0.84, y: 0.16, size: 160, color: '#e0b7ff'}},
  {at: 52.856, dur: 2.3, item: {kind: 'chant', jp: '茈', cn: '“茈”', x: 0.86, y: 0.16, size: 220, color: '#d9b0ff'}},
  {at: 69.902, dur: 2.0, item: {kind: 'quote', jp: 'そっちが挑戦者だから', cn: '你才是挑战者。', color: G, size: 52}},
  {at: 83.965, dur: 0.5, item: {kind: 'tech', jp: '領域展開', cn: '领域展开', x: 0.5, y: 0.22, size: 140, color: '#ffffff'}},
  {at: 84.391, dur: 1.9, item: {kind: 'tech', jp: '無量空処', cn: '无量空处', x: 0.07, y: 0.24, size: 120, color: '#9fd0ff', align: 'left'}},
  {at: 84.817, dur: 1.5, item: {kind: 'tech', jp: '伏魔御廚子', cn: '伏魔御厨子', x: 0.93, y: 0.24, size: 120, color: '#ff6a6a', align: 'right'}},
  {at: 93.34, dur: 1.2, item: {kind: 'sub', jp: '第二輪', cn: '第二轮', x: 0.07, y: 0.1, size: 36, align: 'left'}},
  {at: 96.749, dur: 1.2, item: {kind: 'sub', jp: '第三輪', cn: '第三轮', x: 0.07, y: 0.1, size: 36, align: 'left'}},
  {at: 100.159, dur: 1.2, item: {kind: 'sub', jp: '第四輪', cn: '第四轮', x: 0.07, y: 0.1, size: 36, align: 'left'}},
  {at: 103.141, dur: 1.2, item: {kind: 'sub', jp: '第五輪', cn: '第五轮', x: 0.07, y: 0.1, size: 36, align: 'left'}},
  {at: 104.42, dur: 1.9, item: {kind: 'quote', jp: '最低でも心臓！！', cn: '至少也要捏碎心脏！', color: G, size: 56}},
  {at: 113.369, dur: 3.3, item: {kind: 'quote', jp: 'あとは貴様を切り刻みながら その無限にも適応させてもらう', cn: '接下来，就一边把你切碎，一边让它适应你的无下限。', color: S, size: 40}},
  {at: 116.778, dur: 2.9, item: {kind: 'quote', jp: 'じゃあな最強 俺がいない時代に生まれただけの凡夫', cn: '再见了，最强。你不过是生在没有我的时代的凡夫。', color: S, size: 40}},
  {at: 151.296, dur: 1.85, item: {kind: 'chant', jp: '位相・黄昏・智慧の瞳', cn: '位相 · 黄昏 · 智慧之瞳', x: 0.88, y: 0.12, size: 72, color: '#9fd0ff'}},
  {at: 153.214, dur: 1.6, item: {kind: 'tech', jp: '術式順転『蒼』', cn: '术式顺转“苍”', x: 0.5, y: 0.8, size: 100, color: '#7fbfff'}},
  {at: 156.836, dur: 1.6, item: {kind: 'chant', jp: '位相・波羅蜜・光の柱', cn: '位相 · 波罗蜜 · 光之柱', x: 0.12, y: 0.12, size: 72, color: '#ffb0a0'}},
  {at: 158.54, dur: 1.2, item: {kind: 'tech', jp: '術式反転『赫』', cn: '术式反转“赫”', x: 0.5, y: 0.8, size: 100, color: '#ff7a6a'}},
  {at: 159.819, dur: 1.8, item: {kind: 'tech', jp: '虚式『茈』', cn: '虚式“茈”', x: 0.5, y: 0.78, size: 130, color: '#d9b0ff'}},
  {at: 167.063, dur: 1.3, item: {kind: 'inner', jp: '五条の勝ちだ', cn: '——五条，赢了。', x: 0.5, y: 0.5, size: 84}},
  {at: 169.194, dur: 2.6, item: {kind: 'sub', jp: '対象を、空間そのものへ', cn: '斩击的对象——是空间本身', x: 0.5, y: 0.88, size: 34}},
  {at: 172.177, dur: 0.42, item: {kind: 'quote', jp: 'まあ勝つさ', color: G, size: 52}},
  {at: 179.421, dur: 1.6, item: {kind: 'inner', jp: '南へ', cn: '向南', x: 0.7, y: 0.36, size: 110, dark: true}},
  {at: 188.37, dur: 1.65, item: {kind: 'quote', jp: '天晴れだ 五条悟', cn: '了不起，五条悟。', color: S, size: 50}},
  {at: 190.075, dur: 2.5, item: {kind: 'quote', jp: '生涯 貴様を忘れることはないだろう', cn: '此生，我都不会忘记你。', color: S, size: 46}},
  {at: 193.484, dur: 3.5, item: {kind: 'title', jp: '新宿決戦', cn: '五条悟　对　两面宿傩'}},
];
