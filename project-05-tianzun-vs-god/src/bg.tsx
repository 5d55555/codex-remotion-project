import React from 'react';
import {AbsoluteFill, Img, staticFile, useCurrentFrame} from 'remotion';
import {C} from './theme';

/* 星空：慢速漂移，可调亮度与色调。 */
export const Space: React.FC<{dim?: number; drift?: number; tint?: string}> = ({dim = 1, drift = 0.15, tint}) => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill style={{backgroundColor: C.void, overflow: 'hidden'}}>
      <Img src={staticFile('tex/stars.png')} style={{position: 'absolute', left: -40, top: -20, width: 2000, height: 1120, opacity: dim, transform: `translateX(${-(f * drift) % 40}px)`}} />
      {tint && <AbsoluteFill style={{background: tint, mixBlendMode: 'multiply'}} />}
    </AbsoluteFill>
  );
};

/* 灰雾：可平铺的雾纹，平移出流动感。 */
export const Fog: React.FC<{opacity?: number; speed?: number; scale?: number; style?: React.CSSProperties}> = ({opacity = 0.6, speed = 1, scale = 1, style}) => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill
      style={{
        backgroundImage: `url(${staticFile('tex/fog.png')})`,
        backgroundSize: `${1024 * scale}px ${1024 * scale}px`,
        backgroundPosition: `${f * 0.9 * speed}px ${Math.sin(f / 90) * 20}px`,
        opacity,
        ...style,
      }}
    />
  );
};

/* 混沌海：包容所有颜色的流纹，平移加色相缓转。 */
export const Chaos: React.FC<{opacity?: number; speed?: number; scale?: number; style?: React.CSSProperties}> = ({opacity = 0.8, speed = 1, scale = 1, style}) => {
  const f = useCurrentFrame();
  return (
    <AbsoluteFill
      style={{
        backgroundImage: `url(${staticFile('tex/chaos.png')})`,
        backgroundSize: `${1024 * scale}px ${1024 * scale}px`,
        backgroundPosition: `${-f * 1.1 * speed}px ${f * 0.4 * speed}px`,
        filter: `hue-rotate(${(f * 1.5 * speed) % 360}deg) saturate(1.1)`,
        opacity,
        ...style,
      }}
    />
  );
};

/* 混沌海的海面：画面下方一条起伏的彩色海带。 */
export const ChaosSea: React.FC<{level?: number; opacity?: number; side?: 'full' | 'right'}> = ({level = 0.75, opacity = 1, side = 'full'}) => {
  const f = useCurrentFrame();
  const top = 1080 * level;
  const pts = Array.from({length: 41}, (_, i) => {
    const x = (i / 40) * 1920;
    const y = top + Math.sin(i * 0.7 + f * 0.06) * 14 + Math.sin(i * 1.9 - f * 0.09) * 8;
    return `${x}px ${y}px`;
  }).join(', ');
  const fade = side === 'right' ? 'linear-gradient(90deg, transparent 20%, black 55%)' : undefined;
  return (
    <AbsoluteFill style={{clipPath: `polygon(0 1080px, ${pts}, 1920px 1080px)`, opacity, WebkitMaskImage: fade, maskImage: fade}}>
      <Chaos opacity={1} speed={1.4} scale={0.8} style={{filter: 'saturate(0.85) brightness(0.62)'}} />
      <AbsoluteFill style={{background: 'linear-gradient(180deg, rgba(255,255,255,0.22) 0%, transparent 4%, rgba(0,0,0,0.55) 100%)'}} />
    </AbsoluteFill>
  );
};

/* 灰雾之海：画面下方一层翻涌的雾。 */
export const FogBank: React.FC<{level?: number; opacity?: number; side?: 'full' | 'left'}> = ({level = 0.72, opacity = 1, side = 'full'}) => {
  const fade = side === 'left' ? 'linear-gradient(270deg, transparent 20%, black 55%)' : undefined;
  return (
    <AbsoluteFill style={{WebkitMaskImage: fade, maskImage: fade, opacity}}>
      <AbsoluteFill style={{WebkitMaskImage: `linear-gradient(180deg, transparent ${level * 100 - 12}%, black ${level * 100 + 6}%)`, maskImage: `linear-gradient(180deg, transparent ${level * 100 - 12}%, black ${level * 100 + 6}%)`}}>
        <Fog opacity={0.9} speed={0.8} scale={1.2} />
        <Fog opacity={0.6} speed={-1.3} scale={0.7} />
      </AbsoluteFill>
    </AbsoluteFill>
  );
};
