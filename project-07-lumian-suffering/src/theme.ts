/* 黑白漫画：纸白、墨黑，红色只用来表示痛，浅蓝是卢米安的眼睛。 */
export const C = {
  paper: '#f6f3ec',
  ink: '#141414',
  gray: '#8a8a8a',
  tone: '#bdbdbd',
  red: '#d6262b',
  blue: '#6fc3ff',
  hair: '#1b1b1f',
};

export const F = {
  serif: "'Noto Serif SC', 'Noto Sans SC', serif",
  sans: "'Noto Sans SC', sans-serif",
  brush: "'Ma Shan Zheng', 'Noto Serif SC', serif",
  pop: "'ZCOOL QingKe HuangYou', 'Noto Sans SC', sans-serif",
  mono: "'IBM Plex Mono', monospace",
};

// 确定性伪随机，保证每次渲染一致。
export const rnd = (n: number) => {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
};
