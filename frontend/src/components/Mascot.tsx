import { useId } from 'react';
import type { MascotLookId } from '../types';

export type MascotMood = 'idle' | 'happy' | 'wink' | 'sleepy' | 'think' | 'hurry' | 'proud';
export type MascotPose = 'idle' | 'wave' | 'run' | 'hold-list';
export type MascotExtra = 'zzz' | 'question' | 'sweat' | 'sparkles' | 'motion' | 'flame';

export interface MascotLook { mood?: MascotMood; pose?: MascotPose; extra?: MascotExtra[]; look?: MascotLookId }

const BLUE = '#3d5afe', BLUE_LIGHT = '#7d8fff', INK = '#141a3a';
const EYE_L = 76, EYE_R = 112, EYE_Y = 92;

const ovalEye = (x: number) => `<ellipse cx="${x}" cy="${EYE_Y}" rx="6.2" ry="10" fill="${INK}"/><ellipse cx="${x + 1.8}" cy="${EYE_Y - 4}" rx="2" ry="2.8" fill="#fff"/>`;
const arcEye = (x: number) => `<path d="M${x - 7} ${EYE_Y + 3} Q${x} ${EYE_Y - 8} ${x + 7} ${EYE_Y + 3}" stroke="${INK}" stroke-width="5" fill="none" stroke-linecap="round"/>`;
const closedEye = (x: number) => `<path d="M${x - 7} ${EYE_Y} Q${x} ${EYE_Y + 7} ${x + 7} ${EYE_Y}" stroke="${INK}" stroke-width="4.5" fill="none" stroke-linecap="round"/>`;
const squintEye = (x: number, left: boolean) => {
  const d = left ? `M${x - 6} ${EYE_Y - 7} L${x + 5} ${EYE_Y} L${x - 6} ${EYE_Y + 7}` : `M${x + 6} ${EYE_Y - 7} L${x - 5} ${EYE_Y} L${x + 6} ${EYE_Y + 7}`;
  return `<path d="${d}" stroke="${INK}" stroke-width="5" fill="none" stroke-linecap="round" stroke-linejoin="round"/>`;
};
const MOUTHS = {
  smile: `<path d="M88 110 Q94 116 100 110" stroke="${BLUE}" stroke-width="4" fill="none" stroke-linecap="round"/>`,
  open: `<path d="M85 107 Q94 107 103 107 Q101 121 94 121 Q87 121 85 107 Z" fill="${INK}"/><path d="M89 116 Q94 113 99 116 Q97 120 94 120 Q91 120 89 116Z" fill="#ff6b8a"/>`,
  wavy: `<path d="M86 112 Q90 108 94 112 Q98 116 102 112" stroke="${BLUE}" stroke-width="3.6" fill="none" stroke-linecap="round"/>`,
  round: `<ellipse cx="94" cy="113" rx="4" ry="5" fill="${INK}"/>`,
  flat: `<path d="M89 112 L99 112" stroke="${BLUE}" stroke-width="4" stroke-linecap="round"/>`,
};
const FACES: Record<MascotMood, string> = {
  idle: ovalEye(EYE_L) + ovalEye(EYE_R) + MOUTHS.smile,
  happy: squintEye(EYE_L, true) + squintEye(EYE_R, false) + MOUTHS.open,
  wink: ovalEye(EYE_L) + squintEye(EYE_R, false) + MOUTHS.open,
  sleepy: closedEye(EYE_L) + closedEye(EYE_R) + MOUTHS.wavy,
  think: ovalEye(EYE_L) + ovalEye(EYE_R) + MOUTHS.flat,
  hurry: ovalEye(EYE_L) + ovalEye(EYE_R) + MOUTHS.round,
  proud: arcEye(EYE_L) + arcEye(EYE_R) + MOUTHS.open,
};

const limb = (d: string) => `<path d="${d}" stroke="${BLUE}" stroke-width="11" fill="none" stroke-linecap="round" stroke-linejoin="round"/>`;
const hand = (x: number, y: number) => `<circle cx="${x}" cy="${y}" r="8.5" fill="#fff" stroke="#dfe5ff" stroke-width="1.5"/>`;
const shoe = (x: number, y: number, angle = 0) => `<ellipse cx="${x}" cy="${y}" rx="12" ry="7" fill="#fff" stroke="#dfe5ff" stroke-width="1.5" transform="rotate(${angle} ${x} ${y})"/>`;
const checklist = `<g transform="translate(142 84) rotate(12)"><rect width="56" height="72" rx="7" fill="#fff" stroke="#d7defc" stroke-width="2"/>${[0, 1, 2].map(i =>
  `<rect x="8" y="${12 + i * 20}" width="11" height="11" rx="3" fill="${BLUE}"/><path d="M10.5 ${17.5 + i * 20} l2.5 2.5 l4 -5" stroke="#fff" stroke-width="2" fill="none" stroke-linecap="round"/><rect x="24" y="${14 + i * 20}" width="24" height="6" rx="3" fill="#c9d2ff"/>`).join('')}</g>`;

// Руки за телом, предмет в руках и руки перед телом.
const POSES: Record<MascotPose, { back: string; held?: string; front: string }> = {
  idle: { back: limb('M52 118 Q38 132 40 148') + hand(40, 150), front: limb('M140 116 Q154 130 150 148') + hand(150, 150) },
  wave: { back: limb('M52 118 Q34 128 34 146') + hand(34, 148), front: limb('M140 100 Q156 90 160 68') + hand(161, 64) },
  run: { back: limb('M54 112 Q30 104 26 122') + hand(25, 124), front: limb('M140 116 Q160 130 166 116') + hand(167, 113) },
  'hold-list': { back: limb('M52 118 Q38 132 42 148') + hand(42, 150) + limb('M136 116 Q150 128 142 146'), held: checklist + hand(141, 148), front: '' },
};

const FLAME_PATH = 'M14 0 C18 12 30 16 28 32 C27 42 20 48 13 48 C5 48 -2 42 -1 32 C0 24 6 22 7 14 C10 18 11 20 12 22 C14 16 15 8 14 0Z';
const FLAME_CORE = 'M13 22 C16 30 22 32 20 40 C19 45 16 47 13 47 C9 47 6 44 6 40 C6 35 10 33 13 22Z';
const flameGradient = (id: string) => `<linearGradient id="${id}" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffb347"/><stop offset=".6" stop-color="#ff6a2b"/><stop offset="1" stop-color="#ff3d5a"/></linearGradient>`;

// Облики рисуются поверх карточки и открываются уровнями (звёздный и корона — поддержкой).
const LOOKS: Record<Exclude<MascotLookId, 'star' | 'crown'>, string> = {
  classic: '',
  autumn: `<path d="M48 132 Q96 150 146 128 L148 144 Q96 166 46 148Z" fill="#ff9a2e"/><path d="M118 142 L124 172 L110 172 L106 146Z" fill="#f08419"/><path d="M60 140 L60 152 M80 146 L80 157 M100 147 L100 158" stroke="#ffc07a" stroke-width="3"/>`,
  winter: `<g transform="rotate(-6 96 104)"><path d="M54 66 Q58 26 98 24 Q138 26 140 64 Z" fill="${BLUE}"/><rect x="48" y="58" width="98" height="16" rx="8" fill="#2a3fd6"/><path d="M64 60 L64 30 M78 58 L78 26 M94 58 L94 24 M110 58 L110 26 M126 60 L126 32" stroke="#2a3fd6" stroke-width="3" opacity=".5"/><circle cx="98" cy="20" r="11" fill="${BLUE_LIGHT}"/></g>`,
  spring: `<g transform="translate(58 62)">${[0, 72, 144, 216, 288].map(angle => `<ellipse cx="0" cy="-9" rx="6" ry="9" fill="#ff9fc0" transform="rotate(${angle})"/>`).join('')}<circle r="5.5" fill="#ffd36b"/></g>`,
  summer: `<g transform="rotate(-6 96 104)"><rect x="62" y="82" width="28" height="18" rx="8" fill="${INK}"/><rect x="98" y="82" width="28" height="18" rx="8" fill="${INK}"/><path d="M90 88 L98 88" stroke="${INK}" stroke-width="4"/><path d="M66 86 L74 86 M102 86 L110 86" stroke="#fff" stroke-opacity=".6" stroke-width="3" stroke-linecap="round"/></g>`,
  graduate: `<g transform="rotate(-10 96 60)"><path d="M58 52 L96 38 L134 52 L96 66 Z" fill="${INK}" stroke="#3a4280" stroke-width="1.5"/><path d="M72 58 L72 72 Q96 82 120 72 L120 58 L96 66Z" fill="#22285a"/><path d="M134 52 L136 74" stroke="#ffc94a" stroke-width="3"/><circle cx="136" cy="76" r="4" fill="#ffc94a"/></g>`,
};

/** Пятиконечная звезда с центром (x, y) и внешним радиусом r. */
const starPath = (x: number, y: number, r: number) => 'M' + Array.from({ length: 10 }, (_, i) => {
  const a = -Math.PI / 2 + i * Math.PI / 5, d = i % 2 ? r * .45 : r;
  return `${(x + Math.cos(a) * d).toFixed(1)} ${(y + Math.sin(a) * d).toFixed(1)}`;
}).join(' L') + 'Z';
const GOLD = '#ffc94a', GOLD_DARK = '#e09a00';
// Облики поддержки: открываются пожертвованием звёздами.
const SUPPORT_LOOKS = {
  star: `<g transform="rotate(-6 96 104)"><path d="M88 91 L100 91" stroke="${GOLD_DARK}" stroke-width="3.5" stroke-linecap="round"/>${[EYE_L, EYE_R].map(x =>
    `<path d="${starPath(x, EYE_Y, 16)}" fill="${GOLD}" stroke="${GOLD_DARK}" stroke-width="2.5" stroke-linejoin="round"/><path d="${starPath(x, EYE_Y + .5, 9.5)}" fill="${INK}"/><circle cx="${x - 2.5}" cy="${EYE_Y - 3}" r="2" fill="#fff"/>`).join('')}</g>
    ${[[34, 46, 9], [162, 38, 7], [178, 70, 5]].map(([x, y, r]) => `<path d="${starPath(x, y, r)}" fill="${GOLD}" stroke="${GOLD_DARK}" stroke-width="1.5" stroke-linejoin="round"/>`).join('')}`,
  crown: `<g transform="rotate(-8 96 50)"><path d="M66 60 L69 30 L83 45 L96 22 L109 45 L123 30 L126 60 Z" fill="${GOLD}" stroke="${GOLD_DARK}" stroke-width="2.5" stroke-linejoin="round"/>
    <rect x="65" y="52" width="62" height="11" rx="4" fill="#ffb020" stroke="${GOLD_DARK}" stroke-width="2"/>
    ${[[69, 30], [96, 22], [123, 30]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="4.5" fill="${GOLD}" stroke="${GOLD_DARK}" stroke-width="2"/>`).join('')}
    <circle cx="96" cy="57.5" r="4.5" fill="#ff4d6d"/><circle cx="79" cy="57.5" r="3.5" fill="${BLUE}"/><circle cx="113" cy="57.5" r="3.5" fill="${BLUE}"/>
    <path d="M74 40 L76 50" stroke="#fff" stroke-opacity=".6" stroke-width="2.5" stroke-linecap="round"/></g>`,
};

const EXTRAS: Record<Exclude<MascotExtra, 'flame'>, string> = {
  zzz: `<g fill="${BLUE_LIGHT}" font-family="Unbounded,sans-serif" font-weight="800"><text x="148" y="56" font-size="22">Z</text><text x="168" y="40" font-size="15">z</text><text x="180" y="28" font-size="11">z</text></g>`,
  question: `<text x="150" y="58" font-family="Unbounded,sans-serif" font-weight="900" font-size="40" fill="${BLUE}">?</text>`,
  sweat: `<path d="M158 34 C164 44 168 50 166 56 C164 62 156 62 154 56 C152 50 156 44 158 34Z" fill="${BLUE}"/><path d="M172 58 C175 63 177 66 176 69 C175 72 171 72 170 69 C169 66 171 63 172 58Z" fill="${BLUE}"/>`,
  sparkles: [[30, 40, 1], [168, 120, .7], [24, 100, .6], [176, 26, .55]].map(([x, y, s]) =>
    `<path transform="translate(${x} ${y}) scale(${s})" d="M0 -12 Q2 -2 12 0 Q2 2 0 12 Q-2 2 -12 0 Q-2 -2 0 -12Z" fill="${BLUE_LIGHT}"/>`).join(''),
  motion: `<g stroke="${BLUE_LIGHT}" stroke-width="4" stroke-linecap="round" opacity=".7"><path d="M6 96 L22 96"/><path d="M2 112 L18 112"/><path d="M10 128 L24 128"/></g>`,
};

function mascotSvg(id: string, { mood = 'idle', pose = 'idle', extra = [], look = 'classic' }: MascotLook) {
  const { back, held = '', front } = POSES[pose];
  const legs = pose === 'run'
    ? limb('M80 150 Q70 166 60 170') + limb('M108 150 Q120 162 132 158') + shoe(56, 172) + shoe(136, 158, -20)
    : limb('M82 150 L78 172') + limb('M108 150 L114 172') + shoe(74, 176) + shoe(119, 176);
  const behind = extra.includes('motion') ? EXTRAS.motion : '';
  const above = extra.map(item => item === 'motion' ? '' : item === 'flame'
    ? `<g transform="translate(150 30)"><path d="${FLAME_PATH}" fill="url(#${id}f)"/><path d="${FLAME_CORE}" fill="#ffe08a"/></g>`
    : EXTRAS[item]).join('');
  return `<defs><linearGradient id="${id}b" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="#e3e9ff"/></linearGradient>
${flameGradient(id + 'f')}<filter id="${id}s" x="-20%" y="-20%" width="140%" height="140%"><feDropShadow dx="0" dy="6" stdDeviation="6" flood-color="#1b2a8a" flood-opacity=".28"/></filter></defs>
<ellipse cx="98" cy="182" rx="52" ry="7" fill="#000" opacity=".22"/>${behind}${legs}${back}
<rect x="26" y="44" width="104" height="90" rx="18" fill="${BLUE}" opacity=".22" transform="rotate(-16 78 89)"/>
<rect x="34" y="50" width="106" height="92" rx="18" fill="${BLUE_LIGHT}" opacity=".38" transform="rotate(-11 87 96)"/>
<g filter="url(#${id}s)"><rect x="44" y="56" width="104" height="94" rx="19" fill="url(#${id}b)" stroke="#fff" stroke-width="2" transform="rotate(-6 96 103)"/></g>
<g transform="rotate(-6 96 104)">${FACES[mood]}</g>${look === 'star' || look === 'crown' ? SUPPORT_LOOKS[look] : LOOKS[look] ?? ''}${held}${front}${above}`;
}

/** Парыч: стопка карточек расписания с лицом. Разметка статична, пользовательских данных в ней нет. */
export function Mascot({ size = 96, className = '', ...look }: MascotLook & { size?: number; className?: string }) {
  const id = 'mascot' + useId().replace(/[^a-zA-Z0-9]/g, '');
  return <svg viewBox="0 0 200 200" width={size} height={size} className={`mascot ${className}`} aria-hidden="true"
    dangerouslySetInnerHTML={{ __html: mascotSvg(id, look) }} />;
}

/** Огонёк в цветах маскота для бейджей и счётчиков. */
export function FlameIcon({ size = 18 }: { size?: number }) {
  const id = 'flame' + useId().replace(/[^a-zA-Z0-9]/g, '');
  return <svg viewBox="-2 0 32 48" width={size * 0.66} height={size} aria-hidden="true"
    dangerouslySetInnerHTML={{ __html: `<defs>${flameGradient(id)}</defs><path d="${FLAME_PATH}" fill="url(#${id})"/><path d="${FLAME_CORE}" fill="#ffe08a"/>` }} />;
}
