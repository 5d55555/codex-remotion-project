/* 老电影配色：画面整体泛黄单色，唯一的颜色是结尾那杯金色魔药。 */
export const C = {
  black: '#0b0907',
  ink: '#1c1611',
  cream: '#efe4cc',
  paper: '#e8dcc0',
  sepia: '#a88b62',
  dim: '#8c7a5e',
  gold: '#ffc83a',
  goldDeep: '#c98a12',
};

export const F = {
  serif: "'Noto Serif SC', 'Noto Sans SC', serif",
  sans: "'Noto Sans SC', sans-serif",
  mono: "'IBM Plex Mono', monospace",
};

// 确定性伪随机，保证每次渲染一致。
export const rnd = (n: number) => {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
};
