// 图片资源：一次加载，按名字取。像素部件额外生成“后排四肢”用的压暗版本（游戏里乘 0xB4B4BC）。
import {staticFile} from 'remotion';
import SPRITES from '../data/sprites.json';

export type SpriteMeta = {w: number; h: number; px: number; py: number; eyes?: number[][]};
export const SPRITE_META = SPRITES as Record<string, SpriteMeta>;

export const PLATES = {
  aerial: 'plates/aerial.jpg',
  street: 'plates/street.jpg',
  rooftop: 'plates/rooftop.jpg',
  crater: 'plates/crater.jpg',
  airport: 'plates/airport.jpg',
  skyplate: 'plates/skyplate.jpg',
  skysunset: 'plates/skysunset.jpg',
  voidplate: 'plates/voidplate.jpg',
  shrine: 'plates/shrine.png',
  void: 'plates/void.png',
} as const;
export type PlateName = keyof typeof PLATES;

export const PORTRAITS = {gojo: 'portraits/gojo.png', sukuna: 'portraits/sukuna.png'} as const;
export type PortraitName = keyof typeof PORTRAITS;

type Img = HTMLImageElement | HTMLCanvasElement;
const images = new Map<string, HTMLImageElement>();
const dark = new Map<string, HTMLCanvasElement>();
let loading: Promise<void> | null = null;
let ready = false;

const load = (src: string) =>
  new Promise<HTMLImageElement>((res, rej) => {
    const im = new Image();
    im.onload = () => res(im);
    im.onerror = () => rej(new Error('image failed: ' + src));
    im.src = staticFile(src);
  });

const makeDark = (im: HTMLImageElement) => {
  const c = document.createElement('canvas');
  c.width = im.width;
  c.height = im.height;
  const g = c.getContext('2d')!;
  g.drawImage(im, 0, 0);
  g.globalCompositeOperation = 'multiply';
  g.fillStyle = '#b4b4bc';
  g.fillRect(0, 0, c.width, c.height);
  g.globalCompositeOperation = 'destination-in';
  g.drawImage(im, 0, 0);
  return c;
};

export const assetsReady = () => ready;

export const loadAssets = () => {
  loading ??= (async () => {
    const jobs: Promise<void>[] = [];
    for (const name of Object.keys(SPRITE_META)) {
      jobs.push(
        load(`sprites/${name}.png`).then((im) => {
          images.set('sp:' + name, im);
          dark.set(name, makeDark(im));
        }),
      );
    }
    for (const [k, v] of Object.entries(PLATES)) jobs.push(load(v).then((im) => void images.set('pl:' + k, im)));
    for (const [k, v] of Object.entries(PORTRAITS)) jobs.push(load(v).then((im) => void images.set('po:' + k, im)));
    await Promise.all(jobs);
    // 解码好再用，避免第一次 drawImage 时同步解码卡住
    await Promise.all([...images.values()].map((im) => im.decode().catch(() => undefined)));
    ready = true;
  })();
  return loading;
};

export const sprite = (name: string): Img => images.get('sp:' + name)!;
export const spriteDark = (name: string): Img => dark.get(name)!;
export const plate = (name: PlateName): HTMLImageElement => images.get('pl:' + name)!;
export const portrait = (name: PortraitName): HTMLImageElement => images.get('po:' + name)!;
