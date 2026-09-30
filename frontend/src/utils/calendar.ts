const DAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница', 'Суббота', 'Воскресенье'];
const moscowParts = new Intl.DateTimeFormat('en-US', { timeZone: 'Europe/Moscow', year: 'numeric', month: 'numeric', day: 'numeric' });
const display = new Intl.DateTimeFormat('ru-RU', { timeZone: 'UTC', day: '2-digit', month: '2-digit', year: 'numeric' });

/** Дата выбранного дня текущей московской недели, независимо от часового пояса телефона. */
export function selectedWeekDate(day: string, serverTime?: string): string {
  const reference = serverTime ? new Date(serverTime) : new Date();
  if (Number.isNaN(reference.getTime())) return selectedWeekDate(day);
  const parts = moscowParts.formatToParts(reference);
  const part = (name: string) => Number(parts.find(p => p.type === name)?.value);
  const date = new Date(Date.UTC(part('year'), part('month') - 1, part('day')));
  const selected = DAYS.indexOf(day);
  if (selected < 0) return '';
  const current = (date.getUTCDay() + 6) % 7;
  date.setUTCDate(date.getUTCDate() - current + selected);
  return display.format(date);
}
