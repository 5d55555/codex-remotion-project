// 关键帧插画：public/frames/<id>.jpg（16:9）。一次加载；第一次绘制时浏览器再解码。
import {staticFile} from 'remotion';

const imgs = new Map<string, HTMLImageElement>();
let loading: Promise<void> | null = null;
let ready = false;

export const stillsReady = () => ready;

export const loadStills = (ids: string[]) => {
  loading ??= (async () => {
    await Promise.all(
      [...new Set(ids)].map(
        (id) =>
          new Promise<void>((res) => {
            const im = new Image();
            im.onload = () => {
              imgs.set(id, im);
              res();
            };
            im.onerror = () => {
              console.error('missing still', id);
              res();
            };
            im.src = staticFile(`frames/${id}.jpg`);
          }),
      ),
    );
    ready = true;
  })();
  return loading;
};

export const still = (id: string) => imgs.get(id);
