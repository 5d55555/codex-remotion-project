import React from 'react';
import {Arena, H, Label} from '../hud';
import {PixelChar, PixelSheep} from '../pixel';

// 临时：像素角色造型测试。
const Test: React.FC = () => (
  <>
    <Arena left={1} right={1} split={1} />
    <PixelChar kind="dead" x={520} y={760} cell={26} />
    <PixelChar kind="shep" x={1360} y={760} cell={26} flip />
    <PixelSheep x={1700} y={820} at={0} cell={12} />
    <PixelSheep x={1080} y={840} at={0} cell={12} />
    <PixelChar kind="dead" x={300} y={1000} cell={10} eyes="x" walk={false} />
    <PixelChar kind="dead" x={560} y={1000} cell={10} eyes="peek" walk={false} />
    <Label x={520} y={300} a="c" text="逝者" at={0} size={60} color={H.dead} />
    <Label x={1360} y={300} a="c" text="牧羊人" at={0} size={60} color={H.shep} />
  </>
);

export const SCENES: Record<string, React.FC> = {cold: Test};
