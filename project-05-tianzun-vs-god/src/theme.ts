/* 配色：天尊是灰雾与青铜，上帝是包容所有颜色的混沌海；推演内容统一用暗红标签。 */
export const C = {
  void: '#040406',
  ink: '#ecebe6',
  dim: '#8d8f96',
  fog: '#cfd3d8',
  bronze: '#c9a45c',
  bronzeDeep: '#5a4626',
  chaos: '#b46cff',
  chaosHot: '#ff5fd2',
  night: '#2a3550',
  disorder: '#a3203f',
  calamity: '#ff6a2a',
  sun: '#ffd36a',
  tag: '#c0392b',
};

export const F = {
  serif: "'Noto Serif SC', 'Noto Sans SC', serif",
  sans: "'Noto Sans SC', sans-serif",
  brush: "'Ma Shan Zheng', 'Noto Serif SC', serif",
  mono: "'IBM Plex Mono', monospace",
};

// 确定性伪随机，保证每次渲染一致。
export const rnd = (n: number) => {
  const x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
  return x - Math.floor(x);
};
