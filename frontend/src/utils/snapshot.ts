import type { InfoResponse } from '../types';

/**
 * Копия расписания лежит на GitHub Pages рядом с приложением: если страница открылась,
 * открываются и данные, даже когда сервер API недоступен из сети пользователя.
 */

const DAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье'];

/** Короткое стабильное имя файла для группы или преподавателя (FNV-1a, 32 бита). */
export function nameHash(name: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < name.length; i++) {
    hash ^= name.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193) >>> 0;
  }
  return hash.toString(36);
}

/** Файл копии для пути API или null, если такие данные в копию не попадают. */
export function snapshotFile(path: string): string | null {
  const [pathname, query = ''] = path.split('?');
  const parts = pathname.split('/').filter(Boolean).map(decodeURIComponent);
  const key = parts.join('/');
  if (key === 'info' || key === 'groups' || key === 'teachers') return `${key}.json`;
  if (parts.length === 3 && parts[0] === 'schedule' && (parts[1] === 'group' || parts[1] === 'teacher'))
    return `${parts[1] === 'group' ? 'g' : 't'}/${nameHash(parts[2])}.json`;
  if (parts.length === 4 && parts[0] === 'schedule' && parts[1] === 'base' && (parts[2] === 'group' || parts[2] === 'teacher'))
    return `${parts[2] === 'group' ? 'bg' : 'bt'}/${nameHash(parts[3])}.json`;
  if (parts.length === 3 && parts[0] === 'changes' && parts[1] === 'group') return `c/${nameHash(parts[2])}.json`;
  if (key === 'classrooms/free') {
    const params = new URLSearchParams(query);
    const day = DAYS.indexOf(params.get('day') || '');
    const slot = Number(params.get('slot'));
    const week = params.get('weekType');
    if (day < 0 || !Number.isInteger(slot) || (week !== 'red' && week !== 'blue')) return null;
    return `rooms/${day}-${slot}-${week}.json`;
  }
  return null;
}

/** Календарная дата в Москве как UTC-полночь, чтобы считать дни без учёта пояса телефона. */
function moscowDate(now: Date, offsetDays = 0): Date {
  const shifted = new Date(now.getTime() + 3 * 3600000);
  return new Date(Date.UTC(shifted.getUTCFullYear(), shifted.getUTCMonth(), shifted.getUTCDate() + offsetDays));
}

const monday = (date: Date) => new Date(date.getTime() - ((date.getUTCDay() + 6) % 7) * 86400000);

/** Как AcademicWeek на сервере: первая неделя семестра красная, цвет меняется по понедельникам. */
export function weekType(date: Date): 'red' | 'blue' {
  const year = date.getUTCFullYear(), month = date.getUTCMonth() + 1;
  const start = month >= 9 ? Date.UTC(year, 8, 1) : month === 1 ? Date.UTC(year - 1, 8, 1) : Date.UTC(year, 1, 1);
  const weeks = Math.round((monday(date).getTime() - monday(new Date(start)).getTime()) / (7 * 86400000));
  return weeks % 2 === 0 ? 'red' : 'blue';
}

/** Как ScheduleService.getTodayName: в выходные пустая строка. */
const studyDayName = (date: Date) => {
  const index = (date.getUTCDay() + 6) % 7;
  return index > 4 ? '' : DAYS[index];
};

/** Сведения о дне из копии пересчитываются по часам телефона: копия может быть сделана вчера. */
export function liveInfo(saved: Pick<InfoResponse, 'changesDate'>, now = new Date()): InfoResponse {
  const today = moscowDate(now), tomorrow = moscowDate(now, 1);
  const isRedWeek = weekType(today) === 'red';
  return {
    weekBadge: isRedWeek ? '🔴 Красная неделя' : '🔵 Синяя неделя',
    isRedWeek,
    tomorrowRedWeek: weekType(tomorrow) === 'red',
    todayName: studyDayName(today),
    tomorrowName: studyDayName(tomorrow),
    changesDate: saved.changesDate,
    serverTime: now.toISOString(),
  };
}
