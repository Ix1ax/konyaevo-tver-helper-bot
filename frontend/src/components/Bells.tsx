import { useEffect } from 'react';
import { createPortal } from 'react-dom';
import { X } from 'lucide-react';
import type { Lesson, ScheduleMap } from '../types';

/** Звонки сокращённого дня — одинаковые для всех курсов (приказ № 88-кл от 28.09.2026). */
export const SHORT_BELLS: Record<number, string> = { 1: '8:30 - 9:30', 2: '9:40 - 10:40', 3: '10:50 - 11:50',
  4: '12:00 - 13:00', 5: '13:10 - 14:10', 6: '14:20 - 15:20' };
// Обычные звонки, если пары с таким номером нет ни в этот день, ни в неделе группы.
const DEFAULT_BELLS: Record<number, string> = { 1: '8:30 - 10:05', 2: '10:15 - 11:50', 3: '12:20 - 13:55',
  4: '14:30 - 16:00', 5: '16:10 - 17:40', 6: '17:50 - 19:20' };
const NUMBERS = [1, 2, 3, 4, 5, 6];

const pretty = (time: string) => time.replace(/\s*[-–—]\s*/, ' – ');

/**
 * Обычные звонки для дня: время пар этого дня, а для отсутствующих номеров — самое частое
 * время этой пары за неделю группы (у курсов разные обеды). В сокращённый день время пар
 * в расписании уже подменено, поэтому обычное берётся из остальных дней недели.
 */
export function normalBells(schedule: ScheduleMap, day: string, short: boolean): Record<number, string> {
  const result: Record<number, string> = {};
  const fromDay = (lessons: Lesson[] = []) => lessons.forEach(l => { if (!result[l.lessonNumber]) result[l.lessonNumber] = l.time; });
  if (!short) fromDay(schedule[day]?.lessons);
  for (const number of NUMBERS) {
    if (result[number]) continue;
    const counts = new Map<string, number>();
    Object.entries(schedule).forEach(([name, value]) => {
      if (short && name === day) return;
      value.lessons.filter(l => l.lessonNumber === number).forEach(l => counts.set(l.time, (counts.get(l.time) ?? 0) + 1));
    });
    result[number] = [...counts].sort((a, b) => b[1] - a[1])[0]?.[0] ?? DEFAULT_BELLS[number];
  }
  return result;
}

const minutes = (hhmm: string) => { const [h, m] = hhmm.trim().split(':').map(Number); return h * 60 + m; };
const isNow = (time: string, now: Date) => {
  const [from, to] = time.split(/[-–—]/);
  const moscow = (now.getUTCHours() * 60 + now.getUTCMinutes() + 180) % 1440;
  return to !== undefined && moscow >= minutes(from) && moscow < minutes(to);
};

/** Звонки на день: обычные и сокращённые, текущая пара подсвечена. */
export function BellsSheet({ schedule, day, short, today, onClose }: {
  schedule: ScheduleMap; day: string; short: boolean; today: boolean; onClose: () => void;
}) {
  useEffect(() => {
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); };
    document.addEventListener('keydown', close);
    return () => document.removeEventListener('keydown', close);
  }, [onClose]);
  const normal = normalBells(schedule, day, short);
  const now = new Date();
  return createPortal(<div className="share-overlay" onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section role="dialog" aria-modal="true" aria-labelledby="bells-title" className="share-dialog surface">
      <div className="flex items-center justify-between gap-3 mb-1">
        <h2 id="bells-title" className="font-semibold">Звонки · {day.toLowerCase()}</h2>
        <button type="button" className="icon-button" onClick={onClose} aria-label="Закрыть"><X /></button>
      </div>
      <p className="text-xs text-theme-subtext mb-3">{short ? 'Сегодня действуют сокращённые пары.' : 'Сокращённые пары бывают по приказу колледжа — в такой день Парыч предупредит на главной.'}</p>
      <table className="bells-table">
        <thead><tr><th>Пара</th><th data-active={!short || undefined}>Обычный день</th><th data-active={short || undefined}>Сокращённый</th></tr></thead>
        <tbody>{NUMBERS.map(number => {
          const current = short ? SHORT_BELLS[number] : normal[number];
          return <tr key={number} data-now={(today && isNow(current, now)) || undefined}>
            <td className="font-unbounded">{number}</td>
            <td data-active={!short || undefined}>{pretty(normal[number])}</td>
            <td data-active={short || undefined}>{pretty(SHORT_BELLS[number])}</td>
          </tr>;
        })}</tbody>
      </table>
    </section>
  </div>, document.body);
}
