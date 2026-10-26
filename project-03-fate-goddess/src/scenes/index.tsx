import React from 'react';
import {LogoCard} from '../kit';

// 各段场景按 timeline.json 的 id 注册；分镜确定后在这里补全。
const Logo: React.FC = () => <LogoCard question="序列2之前，都是孙子？" />;

export const SCENES: Record<string, React.FC> = {logo: Logo};
