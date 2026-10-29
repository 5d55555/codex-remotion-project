// 皮影戏：油灯照亮的幕布 + 半透明彩色驴皮人偶。
export const C = {
  night: '#120a06', // 戏台四周的暗处
  wood: '#3b170c',
  woodHi: '#6e3218',
  gold: '#d6a54c',
  cream: '#f6e2b4',
  screenHot: '#fff0c9',
  screen: '#f3cf8c',
  screenEdge: '#9c5a24',
  edge: '#24130a', // 人偶描边
  // 皮子的颜色（叠在幕布上会被油灯染暖）
  red: '#bf2d22',
  yellow: '#e8ac38',
  black: '#22170f',
  green: '#3f8f55',
  blue: '#3f86b8',
  teal: '#2e948c',
  sleeve: '#d9ecef',
  seal: '#c0281c',
};

export const F = {
  narr: '"ZCOOL XiaoWei", "Noto Serif SC", serif',
  brush: '"Zhi Mang Xing", "Ma Shan Zheng", cursive',
  kai: '"Ma Shan Zheng", "Noto Serif SC", serif',
  serif: '"Noto Serif SC", serif',
  sans: '"Noto Sans SC", sans-serif',
};

export const rnd = (i: number) => {
  const x = Math.sin(i * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
};

// 平滑噪声：油灯火苗的明暗
export const noise1 = (t: number) => {
  const i = Math.floor(t);
  const k = t - i;
  const s = k * k * (3 - 2 * k);
  return rnd(i) * (1 - s) + rnd(i + 1) * s;
};

// 幕布在画面中的位置
export const SCREEN = {x: 90, y: 66, w: 1740, h: 812};
