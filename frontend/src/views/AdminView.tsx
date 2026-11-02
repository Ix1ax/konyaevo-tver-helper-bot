import { useCallback, useEffect, useState } from 'react';
import { ArrowLeft, RefreshCw } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { AdminPanel } from '../components/AdminPanel';
import { AdminFeedback } from '../components/AdminFeedback';
import { BarList, ChartCard, ColumnChart, Kpi, type Column } from '../components/AdminCharts';
import { LoadingIndicator } from '../components/Ui';
import { apiFetch, signedRequest } from '../api/client';

const sections = [['overview', 'Обзор'], ['streak', 'Огонёк'], ['support', 'Поддержка'], ['data', 'Данные'], ['feedback', 'Отзывы']] as const;
type Section = typeof sections[number][0];
const RANGES = [7, 30, 90] as const;

// Цвета серий: активность — синий приложения, огонёк — оранжевый, звёзды — золотой.
const BLUE = '#4c7dff', ORANGE = '#ff7a2b', GOLD = '#f0a500';

interface Overview { users: number; students: number; teachersUsingBot: number; unconfigured: number; notifications: number;
  activeDay: number; activeWeek: number; activeMonth: number; newWeek: number }
interface Bar { label: string; value: number; today: number }
interface Analytics {
  days: number; activeToday: number; activeYesterday: number;
  activity: { day: string; active: number; fresh: number }[];
  courses: Bar[]; groups: Bar[];
  streak: { lit: number; burning: number; countedToday: number; record: number; buckets: Bar[];
    groups: { name: string; lit: number; today: number; longest: number }[]; looks: Bar[] };
  support: { stars: number; supporters: number; payments: number; days: { day: string; stars: number }[];
    recent: { at: string; stars: number; target: string }[] };
}

const WEEKDAYS = ['вс', 'пн', 'вт', 'ср', 'чт', 'пт', 'сб'];
const MONTHS = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];
const parseDay = (iso: string) => { const [y, m, d] = iso.split('-').map(Number); return new Date(Date.UTC(y, m - 1, d)); };
const shortDay = (iso: string) => { const d = parseDay(iso); return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`; };
const longDay = (iso: string) => { const d = parseDay(iso); return `${WEEKDAYS[d.getUTCDay()]}, ${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]}`; };
const percent = (part: number, whole: number) => whole ? `${Math.round(part / whole * 100)}%` : '—';
const plural = (n: number, forms: [string, string, string]) => {
  const t = n % 100, u = n % 10;
  return forms[t >= 11 && t <= 14 ? 2 : u === 1 ? 0 : u >= 2 && u <= 4 ? 1 : 2];
};

/** Дни без звёзд тоже показываем: пустые столбцы видно так же, как и полные. */
function starColumns(data: Analytics): Column[] {
  const byDay = new Map(data.support.days.map(d => [d.day, d.stars]));
  return data.activity.map(d => ({ key: d.day, axis: shortDay(d.day), title: longDay(d.day), value: byDay.get(d.day) ?? 0 }));
}

export default function AdminView() {
  const { isAdmin, setActiveTab } = useApp();
  const [section, setSection] = useState<Section>('overview');
  const [days, setDays] = useState<number>(30);
  const [overview, setOverview] = useState<Overview | null>(null);
  const [data, setData] = useState<Analytics | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const load = useCallback(() => {
    setLoading(true); setError('');
    Promise.all([signedRequest<Overview>('/admin/overview'), signedRequest<Analytics>(`/admin/analytics?days=${days}`)])
      .then(([o, a]) => { setOverview(o); setData(a); })
      .catch(e => setError(e instanceof Error ? e.message : 'Сводка не загрузилась'))
      .finally(() => setLoading(false));
  }, [days]);
  useEffect(() => { if (isAdmin) load(); }, [isAdmin, load]);

  // Даже при ручном переключении вкладки запросы не выполняются без разрешения сервера.
  if (!isAdmin) return <div className="page"><p>Раздел доступен только администратору.</p>
    <button className="button-quiet mt-4" onClick={() => setActiveTab('profile')}>К профилю</button></div>;

  const delta = data ? data.activeToday - data.activeYesterday : 0;
  return <div className="page admin-page">
    <button className="flex items-center gap-2 text-sm text-theme-subtext mb-4" onClick={() => setActiveTab('today')}>
      <ArrowLeft size={20} /> К расписанию
    </button>
    <div className="flex items-center justify-between gap-3 mb-4">
      <h1 className="text-2xl font-bold">Админка</h1>
      <button type="button" className="icon-button surface" aria-label="Обновить" disabled={loading} onClick={load}>
        <RefreshCw size={18} className={loading ? 'animate-spin' : ''} />
      </button>
    </div>
    <div className="admin-sections no-scrollbar" role="tablist" aria-label="Разделы">
      {sections.map(([id, label]) => <button key={id} type="button" role="tab" aria-selected={section === id} onClick={() => setSection(id)}>{label}</button>)}
    </div>

    {section === 'feedback' ? <div className="settings-group p-5"><AdminFeedback /></div>
      : section === 'data' ? <><AdminPanel section="schedule" /><ShortDays /><DaysOff /><TelegramHealth /></>
      : !data || !overview ? (error ? <p role="alert" className="notice error-notice">{error}
          <button className="button-quiet ml-2" onClick={load}>Повторить</button></p> : <LoadingIndicator label="Загружаем сводку…" />)
      : <>
        {section !== 'streak' && <div className="admin-range" role="group" aria-label="Период">
          {RANGES.map(r => <button key={r} type="button" aria-pressed={days === r} onClick={() => setDays(r)}>{r} дней</button>)}
        </div>}

        {section === 'overview' && <>
          <div className="kpi-grid">
            <Kpi label="Сегодня" value={data.activeToday} tone={delta >= 0 ? 'up' : 'down'}
              note={`${delta >= 0 ? '▲' : '▼'} ${Math.abs(delta)} к вчера`} />
            <Kpi label="За неделю" value={overview.activeWeek} note={`${percent(overview.activeWeek, overview.users)} от всех`} />
            <Kpi label="За месяц" value={overview.activeMonth} note={`${percent(overview.activeMonth, overview.users)} от всех`} />
            <Kpi label="Всего" value={overview.users} note={`+${overview.newWeek} за неделю`} tone="up" />
          </div>
          <ChartCard title="Активные по дням" subtitle="Сколько разных людей открывали расписание">
            <ColumnChart color={BLUE} unit={plural(data.activity[data.activity.length - 1]?.active ?? 0, ['человек', 'человека', 'человек'])}
              columns={data.activity.map(d => ({ key: d.day, axis: shortDay(d.day), title: longDay(d.day), value: d.active,
                detail: d.fresh ? `${d.fresh} ${plural(d.fresh, ['новый', 'новых', 'новых'])}` : undefined }))} />
            <p className="chart-note">История заходов по дням собирается с версии 2.2.6.</p>
          </ChartCard>
          <ChartCard title="Новые пользователи">
            <ColumnChart color={BLUE} height={90} unit="новых"
              columns={data.activity.map(d => ({ key: d.day, axis: shortDay(d.day), title: longDay(d.day), value: d.fresh }))} />
          </ChartCard>
          <ChartCard title="По курсам" subtitle="Привязаны к расписанию · заходили сегодня">
            <BarList color={BLUE} rows={data.courses.map(c => ({ label: c.label, value: c.value, extra: `сегодня ${c.today}` }))} />
          </ChartCard>
          <ChartCard title="Топ групп" subtitle="Больше всего пользователей">
            <BarList color={BLUE} rows={data.groups.map(g => ({ label: g.label, value: g.value, extra: `сегодня ${g.today}` }))} />
          </ChartCard>
          <div className="kpi-grid kpi-grid-small">
            <Kpi label="Студентов" value={overview.students} />
            <Kpi label="Преподавателей" value={overview.teachersUsingBot} />
            <Kpi label="С уведомлениями" value={overview.notifications} />
            <Kpi label="Без профиля" value={overview.unconfigured} />
          </div>
        </>}

        {section === 'streak' && <>
          <div className="kpi-grid">
            <Kpi label="Горит сейчас" value={data.streak.burning} note={`${percent(data.streak.burning, overview.users)} пользователей`} />
            <Kpi label="Засчитан сегодня" value={data.streak.countedToday} />
            <Kpi label="Зажигали" value={data.streak.lit} note="хотя бы раз" />
            <Kpi label="Рекорд" value={`${data.streak.record} ${plural(data.streak.record, ['день', 'дня', 'дней'])}`} />
          </div>
          <ChartCard title="Длина серий" subtitle="У кого огонёк горит сейчас">
            <BarList color={ORANGE} rows={data.streak.buckets.map(b => ({ label: b.label, value: b.value }))} empty="Пока ни у кого не горит" />
          </ChartCard>
          <ChartCard title="Группы с огоньком" subtitle="Сколько человек зажигали огонёк">
            <BarList color={ORANGE} rows={data.streak.groups.map(g => ({ label: g.name, value: g.lit,
              extra: `сегодня ${g.today} · лучшая серия ${g.longest}` }))} />
          </ChartCard>
          <ChartCard title="Облики" subtitle="Что сейчас надето">
            <BarList color={ORANGE} rows={data.streak.looks.map(l => ({ label: l.label, value: l.value }))} />
          </ChartCard>
        </>}

        {section === 'support' && <>
          <div className="kpi-grid">
            <Kpi label="Звёзд всего" value={`${data.support.stars} ⭐`} />
            <Kpi label="Поддержали" value={data.support.supporters} note={plural(data.support.supporters, ['человек', 'человека', 'человек'])} />
            <Kpi label="Платежей" value={data.support.payments} />
            <Kpi label="В среднем" value={data.support.supporters ? `${Math.round(data.support.stars / data.support.supporters)} ⭐` : '—'} note="на человека" />
          </div>
          <ChartCard title="Звёзды по дням">
            <ColumnChart color={GOLD} unit="⭐" columns={starColumns(data)} />
          </ChartCard>
          <ChartCard title="Последние поддержки">
            {data.support.recent.length ? <ul className="admin-payments">{data.support.recent.map(p => {
              const at = new Date(p.at);
              return <li key={p.at + p.target}>
                <span className="font-unbounded">{p.stars} ⭐</span>
                <span className="min-w-0 truncate">{p.target}</span>
                <time>{at.toLocaleString('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' })}</time>
              </li>;
            })}</ul> : <p className="chart-empty">Пока никто не поддержал</p>}
            <p className="chart-note">Звёзды становятся доступны через 21 день: @BotFather → бот → Balance.</p>
          </ChartCard>
        </>}
      </>}
  </div>;
}

/** Связь с Telegram: тот же ответ, что у /api/health. */
function TelegramHealth() {
  const [health, setHealth] = useState<{ telegram: string; route: string } | null>(null);
  useEffect(() => { signedRequest<{ telegram: string; route: string }>('/health').then(setHealth).catch(() => setHealth(null)); }, []);
  if (!health) return null;
  return <ChartCard title="Связь с Telegram">
    <div className="kpi-grid kpi-grid-small">
      <Kpi label="Telegram" value={health.telegram === 'ok' ? 'на связи' : 'нет связи'} tone={health.telegram === 'ok' ? 'up' : 'down'} />
      <Kpi label="Маршрут" value={health.route === 'primary' ? 'основной' : 'запасной'} />
    </div>
  </ChartCard>;
}

interface DayOffPeriod { from: string; to: string; title: string; builtIn: boolean }
const period = ({ from, to }: { from: string; to: string }) => from === to ? longDay(from) : `${shortDay(from)} — ${shortDay(to)}`;

/** Праздники и каникулы: огонёк в эти дни не гаснет. Государственные праздники встроены, остальное — вручную. */
function DaysOff() {
  const [periods, setPeriods] = useState<DayOffPeriod[] | null>(null);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [error, setError] = useState('');
  const run = (request: Promise<{ periods: DayOffPeriod[] }>) => {
    setError('');
    request.then(r => { setPeriods(r.periods); setFrom(''); setTo(''); }).catch(e => setError(e instanceof Error ? e.message : 'Не удалось сохранить'));
  };
  useEffect(() => { run(signedRequest<{ periods: DayOffPeriod[] }>('/admin/days-off')); }, []);
  const remove = (item: DayOffPeriod) => {
    const initData = window.Telegram?.WebApp?.initData || '';
    run(apiFetch(`/admin/days-off?from=${item.from}&to=${item.to}`, { method: 'DELETE', headers: { 'X-Telegram-Init-Data': initData } })
      .then(r => { if (!r.ok) throw new Error('Не удалось убрать выходные'); return r.json(); }));
  };
  return <ChartCard title="Праздники и каникулы" subtitle="Огонёк в эти дни не гаснет и не напоминает о себе">
    {periods && (periods.length ? <ul className="short-days">{periods.map(item => <li key={item.from}>
      <span className="min-w-0">{period(item)}<em className="days-off-title">{item.title}</em></span>
      {item.builtIn ? <span className="days-off-tag">по календарю</span>
        : <button type="button" onClick={() => remove(item)} aria-label={`Убрать ${period(item)}`}>Убрать</button>}
    </li>)}</ul> : <p className="chart-empty">Ближайших выходных нет</p>)}
    <div className="short-days-add">
      <input type="date" value={from} onChange={e => { setFrom(e.target.value); if (!to || to < e.target.value) setTo(e.target.value); }} aria-label="Первый выходной день" />
      <input type="date" value={to} min={from || undefined} onChange={e => setTo(e.target.value)} aria-label="Последний выходной день" />
      <button type="button" disabled={!from} onClick={() => run(signedRequest<{ periods: DayOffPeriod[] }>('/admin/days-off', { from, to: to || from }))}>Добавить</button>
    </div>
    <p className="text-xs text-theme-subtext mt-2">Каникулы колледжа и переносы праздников. Можно отметить задним числом — до 14 дней назад.</p>
    {error && <p role="alert" className="notice error-notice mt-3">{error}</p>}
  </ChartCard>;
}

/** Сокращённые пары: администратор отмечает даты, в эти дни у всех время пар по 60 минут. */
function ShortDays() {
  const [days, setDays] = useState<string[] | null>(null);
  const [date, setDate] = useState('');
  const [error, setError] = useState('');
  const run = (request: Promise<{ days: string[] }>) => {
    setError('');
    request.then(r => { setDays(r.days); setDate(''); }).catch(e => setError(e instanceof Error ? e.message : 'Не удалось сохранить'));
  };
  useEffect(() => { run(signedRequest<{ days: string[] }>('/admin/short-days')); }, []);
  const remove = (day: string) => {
    const initData = window.Telegram?.WebApp?.initData || '';
    run(apiFetch(`/admin/short-days/${day}`, { method: 'DELETE', headers: { 'X-Telegram-Init-Data': initData } })
      .then(r => { if (!r.ok) throw new Error('Не удалось убрать дату'); return r.json(); }));
  };
  return <ChartCard title="Сокращённые пары" subtitle="В эти дни у всех пары по 60 минут: 8:30–9:30 … 14:20–15:20">
    {days && (days.length ? <ul className="short-days">{days.map(day => <li key={day}>
      <span>{longDay(day)}</span>
      <button type="button" onClick={() => remove(day)} aria-label={`Убрать ${longDay(day)}`}>Убрать</button>
    </li>)}</ul> : <p className="chart-empty">Ближайших сокращённых дней нет</p>)}
    <div className="short-days-add">
      <input type="date" value={date} min={new Date(Date.now() + 3 * 3600000).toISOString().slice(0, 10)} onChange={e => setDate(e.target.value)} aria-label="Дата сокращённых пар" />
      <button type="button" disabled={!date} onClick={() => run(signedRequest<{ days: string[] }>('/admin/short-days', { date }))}>Добавить</button>
    </div>
    {error && <p role="alert" className="notice error-notice mt-3">{error}</p>}
  </ChartCard>;
}
