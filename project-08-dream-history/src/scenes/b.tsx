import React from 'react';
import {interpolate} from 'remotion';
import {God, Scholar, TianZun} from '../puppet';
import {Bolt, Butterfly, Candle, Continent, Desk, Fog, Heart, Leather, OilLamp, Say, Slab, Source, SourceKind, Tears, WaveBand} from '../props';
import {ActCard, Big, Narr, Seal, Stage} from '../stage';
import {C, F, rnd} from '../theme';
import {useClock} from './a';

const clamp = {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'} as const;
const SIX: SourceKind[] = ['永暗之河', '失序之国', '母巢', '暗影世界', '知识荒野', '光之钥'];

/* ================= 第四折 120–158.5s：婚后（上帝是个 M；那不是战争；玩大了） ================= */
export const Play: React.FC = () => {
  const {f, at, L, p} = useClock(120);
  const lines = L([
    [123.5, 127.4, '婚后，梦境显示：上帝有一个特殊的爱好。'],
    [127.6, 131.5, '上帝，是个 M。'],
    [131.5, 136.5, '史学界一直以为，第一纪是上帝与天尊对抗的历史。'],
    [136.5, 141, '梦境纠正了这一点：那不是战争。'],
    [141, 144.5, '那是情趣。'],
    [150, 154, '有一天，天尊玩大了。'],
    [154.4, 158.5, '（梦境在这里，抖了一下。）'],
  ]);
  const dim = interpolate(f, [0, at(121), at(127.3), at(127.6)], [0, 0.55, 0.55, 0], clamp);
  // 打戏：天尊出招的时刻（踩拍子），一次比一次大
  const hits = [132.4, 134.3, 136.2, 138.1, 145.4, 147.3, 149.2, 151.1, 152.9].map(at);
  const lastHit = hits.filter((h) => f >= h).pop() ?? -999;
  const ht = f - lastHit;
  const strike = ht >= 0 && ht < 14 ? Math.sin((ht / 14) * Math.PI) : 0;
  const idx = hits.indexOf(lastHit);
  const power = idx < 0 ? 0 : 0.6 + idx * 0.18;
  const big = p(153.6, 0.5);
  const apart = p(156.2, 2.2);
  const shake = f > at(153.6) && f < at(157) ? Math.sin(f * 2.3) * 14 * (1 - p(155, 2)) : 0;
  const fighting = f > at(131.5) && f < at(156);
  const tzX = fighting ? 520 + strike * 40 : 600;
  const gdX = fighting ? 1180 - strike * 30 * power : 1100;
  return (
    <Stage dim={dim} narr={<Narr lines={lines} />}>
      <div style={{position: 'absolute', inset: 0, transform: `translate(${shake}px, ${shake * 0.4}px)`}}>
        <ActCard at={at(120.1)} until={at(123.4)} no="第四折" title="婚后" />
        {f < at(127.6) && (
          <Leather opacity={1 - p(127.2, 0.4)}>
            <Candle x={870} y={640} s={1.1} />
          </Leather>
        )}
        {fighting && f > at(132) && <Leather><WaveBand y={620 - power * 40 * (idx >= 0 && idx % 2 ? 1 : 0.5)} amp={1 + power * 0.5} /></Leather>}
        <TianZun x={tzX} y={350} s={0.85} pose={{near: {sh: 40 + strike * 70, el: 20 + strike * 30}, lean: -strike * 8}} />
        <God x={gdX} y={360} s={0.85} face={-1} rods={apart <= 0} pose={{apart, lean: strike * 14 * power, near: {sh: 60 + strike * 40, el: 50}, far: {sh: 40, el: 40}, head: strike * 10}} />
        {strike > 0 && (
          <Leather>
            {idx % 3 === 0 && <Bolt x={gdX - 10} y={250} s={0.8 + power * 0.4} rot={10} />}
            {idx % 3 === 1 && <Fog x={gdX - 60} y={340} s={0.6 + power * 0.3} op={strike} />}
            {idx % 3 === 2 && <Bolt x={gdX + 20} y={270} s={0.8 + power * 0.4} rot={-14} />}
          </Leather>
        )}
        {/* 情趣：拳脚之间冒出来的心 */}
        {f > at(141) && f < at(153.5) && (
          <Leather>
            {[0, 1, 2, 3, 4].map((i) => {
              const t = ((f - at(141) + i * 13) % 65) / 65;
              return <Heart key={i} x={(tzX + gdX) / 2 + (rnd(i) - 0.5) * 300} y={420 - t * 320} s={0.5 + rnd(i + 3) * 0.5} rot={(rnd(i + 7) - 0.5) * 40} />;
            })}
          </Leather>
        )}
        <Say x={gdX - 40} y={110} at={at(145.6)} until={at(147.2)} text="再来。" tail="r" />
        <Say x={tzX + 90} y={110} at={at(147.4)} until={at(149)} text="好。" />
        <Say x={gdX - 40} y={110} at={at(149.3)} until={at(151.3)} text="再用力一点。" tail="r" />
        {big > 0 && apart < 1 && (
          <Leather>
            <Bolt x={gdX} y={260} s={2.2 * big} rot={4} />
            <Fog x={gdX - 40} y={400} s={1.4 * big} op={big} />
          </Leather>
        )}
        <Big at={at(127.6)} until={at(131.4)} text="上帝，是个 M。" x={870} y={100} size={130} color={C.red} rot={-3} font={F.kai} />
        <Big at={at(141)} until={at(144.6)} text="情趣" x={870} y={110} size={170} color={C.red} rot={5} />
        <Seal at={at(128.4)} until={at(131.4)} kind="梦" text="此条，正史未载" x={40} y={30} />
        <Seal at={at(132)} until={at(140.5)} kind="正文" text="《宿命之环》六卷126《解读象征》：“魔术师”女士说，第一纪的历史或许就是上帝与天尊对抗的历史，“不保证正确”" x={40} y={30} w={800} />
        {big > 0 && <div style={{position: 'absolute', inset: 0, background: '#fff8e0', opacity: Math.max(0, 1 - Math.abs(f - at(154.2)) / 10) * 0.9}} />}
      </div>
    </Stage>
  );
};

/* ================= 158.5–191s：悲痛；不可思议也救不回；亵渎石板；聚合 ================= */
export const Grief: React.FC = () => {
  const {f, at, L, p} = useClock(158.5);
  const lines = L([
    [159, 163, '上帝，被整死了。'],
    [163, 167.5, '天尊悲痛欲绝。'],
    [167.5, 172.5, '祂想救。可即使以祂的不可思议，'],
    [172.5, 176, '也无法复活一位完整的支柱。'],
    [176, 180.5, '上帝的遗骸，在混沌海中化作了第一块亵渎石板。'],
    [180.5, 184, '梦里，石板上刻的是两位的婚书。'],
    [184, 187.5, '更糟的是，六份源质的聚合倾向越来越强。'],
    [187.5, 191.2, '天尊觉得，自己正在背离上帝。'],
  ]);
  // 散了的皮子：被拼起来一点，又掉下去
  const lift = interpolate(f, [at(168), at(171), at(171.6), at(172.6)], [0, 0.72, 0.72, 0], clamp);
  const wob = lift > 0 ? Math.sin(f * 0.9) * 0.04 : 0;
  const apart = 1 - lift + wob;
  const toSlab = p(176.2, 1.6);
  const orbit = p(184, 1.5);
  const drag = p(187.5, 3.5);
  const kneel = p(163, 1.2);
  const tzX = 560 - drag * 200;
  return (
    <Stage dim={0.4} narr={<Narr lines={lines} />}>
      {toSlab < 1 && <God x={1100} y={310} s={0.95} face={-1} rods={false} opacity={1 - toSlab} pose={{apart: Math.min(1, apart), near: {sh: 60, el: 50}, far: {sh: 40, el: 40}}} />}
      {toSlab > 0 && (
        <Leather opacity={toSlab}>
          <WaveBand y={690} amp={0.6} />
          <Slab x={1100} y={700 - toSlab * 40} s={1.1} text={f > at(180.5)} />
        </Leather>
      )}
      <TianZun x={tzX} y={300 + kneel * 60} s={0.95} pose={{lean: kneel * 24 - drag * 10, head: kneel * 16, near: {sh: 30 + lift * 60, el: 40 + lift * 30}, far: {sh: 10, el: 30}}} />
      {f > at(164) && f < at(184) && (
        <Leather>
          <Tears x={tzX + 70} y={300 + kneel * 60 - 30} at={at(164)} />
        </Leather>
      )}
      {orbit > 0 && (
        <Leather opacity={orbit}>
          {SIX.map((k, i) => {
            const a = f * (0.05 + drag * 0.05) + (i * Math.PI) / 3;
            const r = 220 - drag * 50;
            return <Source key={k} kind={k} x={tzX + Math.cos(a) * r} y={330 + Math.sin(a) * r * 0.45} r={40} label={false} glow={1} />;
          })}
          {drag > 0 && <path d={`M${tzX + 60} 360 Q ${(tzX + 1100) / 2} ${300 + drag * 60} 1040 560`} stroke={C.red} strokeWidth={4} fill="none" strokeDasharray="14 12" opacity={1 - drag * 0.6} />}
        </Leather>
      )}
      <Seal at={at(173)} until={at(180)} kind="梦" text="正文里没有这条规矩；“不可思议”在正文里也只是个形容词" x={40} y={30} w={760} />
      <Seal at={at(176.5)} until={at(183.8)} kind="正文" text="《诡秘之主》八部16《大手笔》：黑夜女神说，那位“上帝”的尸骸在“混沌海”中形成了第一块“亵渎石板”" x={40} y={110} w={780} />
      <Seal at={at(180.8)} until={at(184)} kind="梦" text="婚书这一条，是梦" x={40} y={30} />
      <Seal at={at(184.3)} until={at(191)} kind="正文" text="八部16：黑夜女神说，两位“应该”掌握了额外的复数源质，聚合本能变得无法自控" x={40} y={30} w={760} />
    </Stage>
  );
};

/* ================= 第五折 191–213s：嫁接、殉情、化蝶 ================= */
export const Graft: React.FC = () => {
  const {f, at, L, p} = useClock(191);
  const lines = L([
    [191.5, 195, '内疚之下，天尊做了两件事。'],
    [195, 200, '第一，把六份彩礼，连同上帝的灾祸之城，封进西大陆，交给蓬莱看守。'],
    [200, 204.5, '第二，嫁接：把上帝“死亡”的结果，嫁接到自己身上。'],
    [204.5, 208, '双双殉情。'],
    [208, 210.5, '据正文，两位的精神，至今仍残留在各自的源质里。'],
    [210.5, 213.2, '某种意义上，祂们还在一起。'],
  ]);
  const seal = p(195.4, 3.6);
  const graft = p(200.6, 2.6);
  const down = p(204.6, 2.2);
  const dim = 0.35 + down * 0.25;
  const fly = Math.min(1, Math.max(0, (f - at(206.3)) / (6.9 * 30)));
  const SEVEN: SourceKind[] = [...SIX, '灾祸之城'];
  return (
    <Stage dim={dim} narr={<Narr lines={lines} size={44} />}>
      <ActCard at={at(191.1)} until={at(194.6)} no="第五折" title="嫁接" x={960} />
      <Leather>
        <WaveBand y={690} amp={0.5} />
        <Slab x={1120} y={660} s={1.1} text />
      </Leather>
      {seal > 0 && seal < 1 && (
        <Leather opacity={Math.min(1, seal * 4) * (1 - p(199.4, 0.6))}>
          <Continent x={1400} y={250} s={0.7} />
          {SEVEN.map((k, i) => {
            const t = Math.min(1, Math.max(0, seal * 1.6 - i * 0.08));
            const sx = 360 + i * 90;
            const sy = 200;
            return <Source key={k} kind={k} x={sx + (1400 + (i - 3) * 30 - sx) * t} y={sy + (240 - sy) * t - Math.sin(t * Math.PI) * 120} r={36 * (1 - t * 0.6)} label={false} />;
          })}
        </Leather>
      )}
      {seal > 0.5 && f < at(200) && (
        <div style={{position: 'absolute', left: 1520, top: 330, fontFamily: F.kai, fontSize: 40, color: '#fff2da', background: C.seal, padding: '6px 14px', borderRadius: 4, transform: `rotate(-8deg) scale(${interpolate(seal, [0.5, 0.6, 0.7], [1.8, 0.9, 1], clamp)})`, mixBlendMode: 'multiply'}}>蓬莱看守</div>
      )}
      {/* 嫁接：一根红线从石板接到天尊身上，黑色的“死亡”顺着线过去 */}
      {graft > 0 && down < 1 && (
        <Leather opacity={1 - down}>
          <path d="M1050 520 C 900 380 760 380 640 420" stroke={C.red} strokeWidth={6} fill="none" strokeDasharray={`${graft * 700} 700`} />
          {graft > 0.5 &&
            [0, 1, 2].map((i) => {
              const t = ((f - at(201.8) + i * 12) % 36) / 36;
              const x = 1050 + (640 - 1050) * t;
              const y = 520 - Math.sin(t * Math.PI) * 130 + (420 - 520) * t;
              return <circle key={i} cx={x} cy={y} r={12} fill={C.black} />;
            })}
        </Leather>
      )}
      {down < 1 ? (
        <TianZun x={560} y={300 + down * 260} s={0.95} opacity={1 - down * 0.2} pose={{lean: down * 80, head: 10 + down * 20, near: {sh: 20 + graft * 30, el: 30}, far: {sh: 10, el: 30}}} />
      ) : (
        <TianZun x={560} y={560} s={0.95} rods={false} opacity={0.8} pose={{lean: 80, head: 30, near: {sh: 10, el: 20}}} />
      )}
      {fly > 0 && (
        <Leather>
          {[0, 1].map((i) => {
            // 一只从天尊身上、一只从石板上飞起，绕着彼此慢慢往上
            const t = fly;
            const sx = i ? 1120 : 600;
            const ang = t * Math.PI * 2.5 + i * Math.PI;
            const r = 110 * Math.min(1, t * 2.5);
            const cx = sx + (850 - sx) * Math.min(1, t * 1.8) + Math.cos(ang) * r;
            const cy = 560 - t * 330 + Math.sin(ang) * r * 0.35;
            return <Butterfly key={i} x={cx} y={cy} s={1.15} color={i ? C.blue : C.yellow} phase={i * 1.3} />;
          })}
        </Leather>
      )}
      <Seal at={at(195.6)} until={at(200.4)} kind="正文" text="《宿命之环》七卷55《序列之上》：天尊陨落前，把这七份源质封印到西大陆，由蓬莱看守" x={40} y={30} w={760} />
      <Seal at={at(200.8)} until={at(205)} kind="正文" text="《诡秘之主》七部40《封印》：克莱恩借“诡秘侍者”特性和源堡，把罗塞尔的“新生”和“死亡”嫁接在一起" x={40} y={30} w={780} />
      <Seal at={at(205)} until={at(208)} kind="正文" text="六卷126：“魔术师”说，第一纪末尾两位同时陨落（“不保证正确”）" x={40} y={30} w={760} />
      <Seal at={at(208.3)} until={at(213)} kind="正文" text="七卷54：天尊和上帝的精神、意识，仍残留在相应的非凡特性、唯一性和源质中" x={40} y={30} w={760} />
    </Stage>
  );
};

/* ================= 尾声 213–222s：梦醒 ================= */
export const Wake: React.FC = () => {
  const {f, at, L, p} = useClock(213);
  const lines = L([
    [213.3, 217, '以上，就是本人通过梦境占卜法获得的第一纪隐秘历史。'],
    [217, 220.5, '正文有云：“占卜不是万能的，你只会获得象征，一不小心就解读错误。”'],
    [220.5, 222.2, '本人解读得很认真。'],
  ]);
  const wake = p(213.4, 1.2);
  const out = p(221.2, 0.7);
  const light = interpolate(f, [0, at(213.6)], [0.5, 1], clamp) * (1 - out);
  return (
    <Stage light={light} narr={<Narr lines={lines} size={42} />}>
      <Scholar x={760} y={360 - wake * 30} s={0.95} pose={{head: -28 + wake * 28, lean: -12 + wake * 12, near: {sh: 10 + wake * 20, el: 50}, far: {sh: 10, el: 30}}} />
      <Leather>
        <Desk x={820} y={560} />
        <OilLamp x={980} y={560} lit={1 - out} />
      </Leather>
      <Seal at={at(217.2)} until={at(222)} kind="正文" text="《诡秘之主》一卷126《占卜不是万能的》" x={40} y={30} />
      <div style={{position: 'absolute', left: 0, right: 0, bottom: 26, textAlign: 'center', fontFamily: F.serif, fontWeight: 700, fontSize: 24, color: '#5a2a10', opacity: p(213.8, 0.8) * (1 - out), mixBlendMode: 'multiply'}}>
        本片所述，为梦境所得之野史，与正史无关　·　音乐：《青衣》DJ版（0.95x，B站 @一支yuuu）
      </div>
    </Stage>
  );
};
