// 文字层：咏唱、招式名、台词、地点卡、片名。全部由“出现了几秒”决定样子。
import React from 'react';
import {TextItem} from './engine/stage';

const clamp = (v: number, a = 0, b = 1) => (v < a ? a : v > b ? b : v);
const out3 = (k: number) => 1 - Math.pow(1 - clamp(k), 3);

const JP = '"Noto Serif JP", "Noto Serif SC", serif';
const SC = '"Noto Serif SC", "Noto Serif JP", serif';
const BRUSH = '"Yuji Syuku", "Noto Serif JP", serif';
const ANTIQUE = '"Zen Antique", "Noto Serif JP", serif';

const fadeIO = (it: TextItem, fin = 0.25, fout = 0.35) => clamp(it.age / fin) * clamp((it.dur - it.age) / fout);

const Card: React.FC<{it: TextItem}> = ({it}) => {
  const a = fadeIO(it, 0.6, 0.6);
  const k = out3(it.age / 1.2);
  return (
    <div style={{position: 'absolute', left: 140, bottom: 190, opacity: a, color: '#e9eef7', fontFamily: JP}}>
      <div style={{fontSize: 26, letterSpacing: 10, fontWeight: 600, opacity: 0.85}}>{it.cn}</div>
      <div style={{height: 2, width: 420 * k, background: 'linear-gradient(90deg,#e9eef7,rgba(233,238,247,0))', margin: '14px 0'}} />
      <div style={{fontSize: 54, letterSpacing: 18 - 6 * k, fontWeight: 900, textShadow: '0 0 24px rgba(160,190,255,0.35)'}}>{it.jp}</div>
    </div>
  );
};

/** 咏唱：竖排毛笔字，从上往下“写”出来。 */
const Chant: React.FC<{it: TextItem}> = ({it}) => {
  const a = clamp((it.dur - it.age) / 0.3);
  const k = out3(it.age / 0.32);
  const color = it.color ?? '#dfe9ff';
  const size = it.size ?? 150;
  const x = (it.x ?? 0.85) * 1920, y = (it.y ?? 0.16) * 1080;
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translateX(-50%) scale(${1.08 - 0.08 * k})`, transformOrigin: '50% 0', opacity: a}}>
      <div
        style={{
          writingMode: 'vertical-rl', fontFamily: BRUSH, fontSize: size, lineHeight: 1, color, letterSpacing: size * 0.06,
          clipPath: `inset(0 0 ${100 - 100 * k}% 0)`,
          textShadow: `0 0 ${size * 0.25}px ${color}88, 0 0 3px #000, 3px 3px 0 #000`,
          filter: it.age < 0.07 ? 'brightness(2.2)' : 'none',
        }}
      >
        {it.jp}
      </div>
      {it.cn && (
        <div style={{fontFamily: SC, fontWeight: 700, fontSize: Math.max(30, size * 0.24), color: '#eef2fc', opacity: clamp((it.age - 0.15) / 0.3), marginTop: 14, textAlign: 'center', letterSpacing: 4, textShadow: '0 0 8px #000, 0 0 2px #000'}}>
          {it.cn}
        </div>
      )}
    </div>
  );
};

/** 招式名：横排大字，冲进来再落定。 */
const Tech: React.FC<{it: TextItem}> = ({it}) => {
  const a = clamp((it.dur - it.age) / 0.3);
  const k = out3(it.age / 0.18);
  const color = it.color ?? '#ffffff';
  const size = it.size ?? 120;
  const x = (it.x ?? 0.5) * 1920, y = (it.y ?? 0.5) * 1080;
  const align = it.align ?? 'center';
  const tx = align === 'center' ? '-50%' : align === 'right' ? '-100%' : '0';
  const flash = it.age < 0.05 ? 1 : 0;
  return (
    <div style={{position: 'absolute', left: x, top: y, transform: `translate(${tx}, -50%) scale(${1.35 - 0.35 * k})`, transformOrigin: align === 'left' ? '0 50%' : align === 'right' ? '100% 50%' : '50% 50%', opacity: a * clamp(it.age / 0.04), textAlign: align}}>
      <div
        style={{
          fontFamily: ANTIQUE, fontSize: size, lineHeight: 1.05, color: flash ? '#ffffff' : color, letterSpacing: size * 0.08, whiteSpace: 'nowrap',
          WebkitTextStroke: `${Math.max(2, size * 0.025)}px #000`, paintOrder: 'stroke fill',
          textShadow: `0 0 ${size * 0.3}px ${color}aa, 0 0 ${size * 0.08}px ${color}`,
        }}
      >
        {it.jp}
      </div>
      {it.cn && (
        <div style={{fontFamily: SC, fontWeight: 700, fontSize: Math.max(26, size * 0.24), color: '#f2f4fa', letterSpacing: 6, marginTop: 10, opacity: clamp((it.age - 0.12) / 0.25), textShadow: '0 0 8px #000, 0 0 2px #000'}}>
          {it.cn}
        </div>
      )}
    </div>
  );
};

/** 台词：底部字幕，日文一行 + 中文一行，说话人颜色的竖线。 */
const Quote: React.FC<{it: TextItem}> = ({it}) => {
  const a = fadeIO(it, 0.2, 0.3);
  const color = it.color ?? '#5ab8f2';
  const per = Math.min(0.045, 0.35 / Math.max(1, it.jp.length)); // 打字机：长句也在 0.35 秒内打完
  const shown = Math.min(it.jp.length, Math.floor(it.age / per + 1));
  const y = (it.y ?? 0.83) * 1080;
  return (
    <div style={{position: 'absolute', left: 0, right: 0, top: y, display: 'flex', justifyContent: 'center', opacity: a}}>
      <div style={{display: 'flex', alignItems: 'stretch', gap: 22, background: 'linear-gradient(90deg, rgba(0,0,0,0), rgba(0,0,0,0.55) 12%, rgba(0,0,0,0.55) 88%, rgba(0,0,0,0))', padding: '14px 70px'}}>
        <div style={{width: 5, background: color, boxShadow: `0 0 14px ${color}`}} />
        <div>
          <div style={{fontFamily: JP, fontWeight: 900, fontSize: it.size ?? 46, color: '#ffffff', letterSpacing: 3, textShadow: `0 0 18px ${color}66`}}>
            {it.jp.slice(0, shown)}
            <span style={{opacity: 0}}>{it.jp.slice(shown)}</span>
          </div>
          {it.cn && <div style={{fontFamily: SC, fontWeight: 700, fontSize: 32, color: '#e2e6ee', marginTop: 6, letterSpacing: 2, opacity: clamp((it.age - 0.1) / 0.25)}}>{it.cn}</div>}
        </div>
      </div>
    </div>
  );
};

/** 内心独白 / 大字：屏幕中间，细、字距大，慢慢浮现。 */
const Inner: React.FC<{it: TextItem}> = ({it}) => {
  const a = fadeIO(it, 0.5, 0.4);
  const k = out3(it.age / 1.5);
  const color = it.color ?? (it.dark ? '#18203a' : '#ffffff');
  const glow = it.dark ? '0 0 24px rgba(255,255,255,0.9), 0 0 4px rgba(255,255,255,0.9)' : `0 0 30px ${color}55`;
  return (
    <div style={{position: 'absolute', left: (it.x ?? 0.5) * 1920, top: (it.y ?? 0.5) * 1080, transform: 'translate(-50%,-50%)', textAlign: 'center', opacity: a}}>
      <div style={{fontFamily: JP, fontWeight: 600, fontSize: it.size ?? 96, color, letterSpacing: 40 - 16 * k, whiteSpace: 'nowrap', textShadow: glow}}>{it.jp}</div>
      {it.cn && <div style={{fontFamily: SC, fontWeight: 700, fontSize: 34, color: it.dark ? '#2a3350' : '#e4e8f0', letterSpacing: 12, marginTop: 18, opacity: clamp((it.age - 0.12) / 0.3), textShadow: it.dark ? '0 0 10px rgba(255,255,255,0.9)' : '0 0 8px #000'}}>{it.cn}</div>}
    </div>
  );
};

const Sub: React.FC<{it: TextItem}> = ({it}) => {
  const a = fadeIO(it, 0.3, 0.3);
  return (
    <div style={{position: 'absolute', left: (it.x ?? 0.5) * 1920, top: (it.y ?? 0.9) * 1080, transform: it.align === 'left' ? 'translateY(-50%)' : 'translate(-50%,-50%)', opacity: a, fontFamily: SC, fontWeight: 700, fontSize: it.size ?? 30, color: it.color ?? '#e8ecf4', letterSpacing: 4, textShadow: '0 0 8px #000, 0 0 2px #000', whiteSpace: 'nowrap'}}>
      {it.jp}
      {it.cn && <span style={{fontFamily: SC, fontSize: (it.size ?? 30) * 0.7, marginLeft: 16, opacity: 0.8}}>{it.cn}</span>}
    </div>
  );
};

const Title: React.FC<{it: TextItem}> = ({it}) => {
  const a = fadeIO(it, 1.0, 1.2);
  const k = out3(it.age / 2.2);
  return (
    <div style={{position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', opacity: a}}>
      <div style={{fontFamily: BRUSH, fontSize: 230, color: '#f4f1ff', letterSpacing: 60 - 30 * k, transform: `scale(${1.06 - 0.06 * k})`, textShadow: '0 0 40px rgba(168,77,255,0.55), 0 0 6px rgba(0,0,0,0.9)'}}>{it.jp}</div>
      <div style={{fontFamily: JP, fontWeight: 600, fontSize: 30, color: '#cfd6e8', letterSpacing: 26, marginTop: 20, opacity: clamp((it.age - 0.8) / 0.8)}}>SHINJUKU SHOWDOWN</div>
      {it.cn && <div style={{fontFamily: SC, fontWeight: 700, fontSize: 30, color: '#aab3c8', letterSpacing: 16, marginTop: 18, opacity: clamp((it.age - 1.2) / 0.8)}}>{it.cn}</div>}
    </div>
  );
};

export const TextLayer: React.FC<{items: TextItem[]}> = ({items}) => (
  <>
    {items.map((it, i) => {
      switch (it.kind) {
        case 'card':
          return <Card key={i} it={it} />;
        case 'chant':
          return <Chant key={i} it={it} />;
        case 'tech':
          return <Tech key={i} it={it} />;
        case 'quote':
          return <Quote key={i} it={it} />;
        case 'inner':
          return <Inner key={i} it={it} />;
        case 'title':
          return <Title key={i} it={it} />;
        default:
          return <Sub key={i} it={it} />;
      }
    })}
  </>
);
