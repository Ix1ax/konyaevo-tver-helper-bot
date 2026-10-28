import type { Lesson, UserRole } from '../types';

export interface MascotTip {
  mood: 'idle' | 'happy' | 'wink' | 'hurry';
  pose: 'wave' | 'run' | 'hold-list';
  extra?: ('sweat' | 'sparkles' | 'motion')[];
  /** Текст подсказки; фрагменты в **звёздочках** выделяются. */
  text: string;
}

/** Через сколько минут до начала пары маскот начинает торопить. */
export const HURRY_MINUTES = 20;

const toMinutes = (value = '') => {
  const match = value.trim().match(/^(\d{1,2}):(\d{2})/);
  return match ? Number(match[1]) * 60 + Number(match[2]) : -1;
};
const clock = (minutes: number) => `${String(Math.floor(minutes / 60)).padStart(2, '0')}:${String(minutes % 60).padStart(2, '0')}`;
const plural = (count: number, one: string, few: string, many: string) => {
  const tens = count % 100, units = count % 10;
  if (tens >= 11 && tens <= 14) return many;
  return units === 1 ? one : units >= 2 && units <= 4 ? few : many;
};

interface Slot { lesson: Lesson; start: number; end: number }

// Пары с одним временем (подгруппы, несколько строк) считаем одним слотом.
function slots(lessons: Lesson[]): Slot[] {
  const valid = lessons
    .filter(lesson => !lesson.canceled)
    .map(lesson => {
      const [start, end] = lesson.time.split(/[-–—]/);
      return { lesson, start: toMinutes(start), end: toMinutes(end) };
    })
    .filter(slot => slot.start >= 0 && slot.end > slot.start)
    .sort((a, b) => a.start - b.start);
  return valid.filter((slot, index) => index === 0 || slot.start !== valid[index - 1].start);
}

function describe({ lesson }: Slot, role: UserRole) {
  const rooms = [...new Set(lesson.room ? [lesson.room] : (lesson.subgroups || []).map(subgroup => subgroup.room).filter(Boolean))];
  const group = role === 'teacher' && lesson.groupName ? ` у ${lesson.groupName}` : '';
  const room = rooms.length ? ` в **каб. ${rooms.join(' / ')}**` : '';
  const replacement = lesson.changed || lesson.type === 'Замена' ? ' Это **замена**.' : '';
  return { what: `${lesson.subject}${group}${room}`, replacement };
}

/**
 * Подсказка маскота для дня. now — минуты московского времени для «Сегодня»,
 * null для «Завтра». Возвращает null, если подсказывать нечего.
 */
export function mascotTip(lessons: Lesson[], now: number | null, role: UserRole): MascotTip | null {
  const day = slots(lessons);
  if (!day.length) return null;

  if (now === null) {
    const first = day[0], { what, replacement } = describe(first, role);
    return { mood: 'wink', pose: 'hold-list',
      text: `Завтра ${day.length} ${plural(day.length, 'пара', 'пары', 'пар')}, первая в **${clock(first.start)}** — ${what}.${replacement}` };
  }

  const current = day.find(slot => slot.start <= now && now < slot.end);
  if (current) {
    const next = day.find(slot => slot.start >= current.end);
    if (!next) return { mood: 'happy', pose: 'wave', extra: ['sparkles'],
      text: `Это последняя пара на сегодня. В **${clock(current.end)}** — свобода!` };
    const { what, replacement } = describe(next, role);
    return { mood: 'idle', pose: 'wave', text: `Дальше — ${what} в **${clock(next.start)}**.${replacement}` };
  }

  const next = day.find(slot => slot.start > now);
  if (!next) return { mood: 'happy', pose: 'wave', text: 'Пары на сегодня закончились. Загляни во «Завтра», чтобы не проспать.' };

  const { what, replacement } = describe(next, role);
  const wait = next.start - now;
  if (wait <= HURRY_MINUTES) return { mood: 'hurry', pose: 'run', extra: ['sweat', 'motion'],
    text: `Через **${wait} мин** — ${what}. Успеваешь?${replacement}` };
  if (next === day[0]) return { mood: 'wink', pose: 'hold-list', text: `Первая пара в **${clock(next.start)}** — ${what}.${replacement}` };
  return { mood: 'idle', pose: 'wave', text: `Перерыв до **${clock(next.start)}**. Дальше — ${what}.${replacement}` };
}
