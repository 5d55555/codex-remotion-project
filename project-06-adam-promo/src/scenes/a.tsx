import React from 'react';
import {AbsoluteFill, staticFile, useCurrentFrame} from 'remotion';
import {DateStamp, FilmLook, Intertitle, Leader, Narr, Quote, Slogan, Src} from '../reel';
import {prog} from '../kit';
import {Emblem, Fog, Newspaper, Quill, Skyline, SteamEngine, Train} from '../props';
import {C} from '../theme';


// 白天的天空：上浅下深的灰黄。
const Sky: React.FC<{night?: number}> = ({night = 0}) => (
  <AbsoluteFill style={{background: `linear-gradient(180deg, rgb(${200 - night * 170},${190 - night * 165},${170 - night * 150}) 0%, rgb(${150 - night * 130},${140 - night * 125},${120 - night * 110}) 100%)`}} />
);

/* 片头倒计时 */
const LeaderScene: React.FC = () => (
  <FilmLook>
    <Leader from={5} per={24} />
  </FilmLook>
);

/* 开场字幕卡 */
const Open: React.FC = () => (
  <FilmLook>
    <Intertitle
      lines={[
        {at: 14, text: '黄昏隐士会　敬献', size: 40, color: C.sepia},
        {at: 60, text: '时代，正在前进。', size: 110},
      ]}
      foot={{at: 120, text: '二创宣传片　·　标“原话”“原文”“推断”的出自《诡秘之主》，“宣传词”为本片所写'}}
    />
    <Emblem x={960} y={290} r={64} at={20} />
  </FilmLook>
);

/* 蒸汽时代：图纸上的蒸汽机，然后是烟囱林立的城市和火车 */
const Steam: React.FC = () => {
  const f = useCurrentFrame();
  const city = f >= 190;
  return (
    <FilmLook>
      {!city && <SteamEngine at={0} />}
      {city && (
        <>
          <Sky />
          <Skyline base={880} smoke={1} windows={0.2} />
          <Train at={200} dur={150} y={980} />
        </>
      )}
      {!city && <Narr lines={[{at: 30, text: '第五纪。罗塞尔发明了蒸汽机。'}]} end={186} />}
      {!city && <Src at={50} until={186} text="《诡秘之主》第一部第4章《占卜》，克莱恩回忆的历史常识" />}
      {city && <DateStamp text="第五纪　一三四九年" at={196} sub="克莱恩在这一年苏醒" />}
      <Slogan text="铁轨，不会为谁转弯。" at={236} y={400} size={92} />
    </FilmLook>
  );
};

/* 大雾霾 */
const Smog: React.FC = () => {
  const f = useCurrentFrame();
  const fog = prog(f, 20, 130);
  return (
    <FilmLook>
      <Sky />
      <Skyline base={880} smoke={1} windows={0.15} />
      <Fog amount={fog} />
      <AbsoluteFill style={{background: '#d8ccb0', opacity: fog * 0.55}} />
      <DateStamp text="一三四九年末" at={8} sub="贝克兰德" />
      <Narr
        lines={[
          {at: 30, text: '这一年冬天，贝克兰德起了一场雾。'},
          {at: 210, text: '黑夜教会查到：乔治三世勾结魔女教派、心理炼金会，贩卖人口，酿成惨案。'},
        ]}
        end={332}
      />
      <Newspaper at={150} until={332} date="第五纪一三四九年末" headline="大雾霾" subs={['直接死亡，超过二万一千人', '随后的瘟疫，又带走近四万人']} note="数字是奥黛丽回想的报纸初步统计；幕后据伦纳德汇报的情报（第五部第88章《收获》）" />
      <Slogan text="雾会散。时代，不会停下。" at={334} y={460} size={88} />
    </FilmLook>
  );
};

/* 羽毛笔：0-08 */
const QuillScene: React.FC = () => {
  const f = useCurrentFrame();
  const dim = prog(f, 126, 16);
  return (
    <FilmLook>
      <AbsoluteFill style={{background: 'radial-gradient(ellipse at 50% 55%, #4a3a28, #1a120b 75%)'}} />
      <div
        style={{
          position: 'absolute',
          left: 960,
          top: 560,
          width: 1260,
          height: 640,
          transform: 'translate(-50%, -50%) rotate(-1.5deg)',
          backgroundImage: `url(${staticFile('tex/poster.png')})`,
          backgroundSize: 'cover',
          boxShadow: '0 20px 60px rgba(0,0,0,0.7)',
        }}
      />
      <Quill at={10} dur={116} />
      <AbsoluteFill style={{background: '#0b0907', opacity: dim * 0.82}} />
      <DateStamp text="一三五〇年" at={6} sub="一支羽毛笔，“0-08”，落进了亚当手里" />
      <Quote y={500} at={140} text="“所有命运的馈赠，早已在暗中标注好了价格，不是吗？”" kind="原话" who="亚当" src="第四部《不死者》第213章《故事的尾声》——取走羽毛笔之后" size={56} />
      <Narr lines={[{at: 250, text: '“亚当距离神位更近了。”——克莱恩以“愚者”身份告知塔罗会'}]} />
      <Src at={262} y={900} kind="原文" text="第五部《红祭司》第15章《告知式提醒》" />
    </FilmLook>
  );
};

export const SCENES_A: Record<string, React.FC> = {leader: LeaderScene, open: Open, steam: Steam, smog: Smog, quill: QuillScene};
