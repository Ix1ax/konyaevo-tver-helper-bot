import { AdminFeedback } from './AdminFeedback';
import { useEffect, useState } from 'react';
import { ShieldCheck, RefreshCw } from 'lucide-react';
import { useTelegram } from '../hooks/useTelegram';
import { useTheme } from '../context/ThemeContext';
import { getApiBaseUrl } from '../api/client';

interface Overview {
  activeDay: number;
  activeWeek: number;
  activeMonth: number;
  newWeek: number;
  users: number;
  students: number;
  teachersUsingBot: number;
  unconfigured: number;
  notifications: number;
  groups: number;
  teachers: number;
  changedGroups: number;
  changes: number;
  changesDate: string;
}

export function AdminPanel() {
  const { tg } = useTelegram();
  const { currentThemeDef } = useTheme();
  const [data, setData] = useState<Overview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const initData = tg?.initData as string | undefined;

  useEffect(() => {
    if (!initData) return;
    const controller = new AbortController();
    let active = true;
    const timeout = setTimeout(() => controller.abort(), 8000);
    setLoading(true);
    setError('');
    fetch(`${getApiBaseUrl()}/admin/overview`, {
      headers: { 'X-Telegram-Init-Data': initData },
      signal: controller.signal,
      cache: 'no-store',
    }).then(async (response) => {
      if (response.status === 401 || response.status === 403) {
        if (active) setData(null);
        return;
      }
      if (!response.ok) throw new Error('Не удалось обновить сводку');
      const overview = await response.json() as Overview;
      if (active) setData(overview);
    }).catch(() => {
      if (active) setError('Сводка не обновилась. Попробуйте ещё раз.');
    }).finally(() => {
      clearTimeout(timeout);
      if (active) setLoading(false);
    });
    return () => { active = false; clearTimeout(timeout); controller.abort(); };
  }, [initData, revision]);

  // Панель появляется только после разрешённого сервером ответа.
  if (!data) return null;

  const stats = [
    ['Пользователей бота', data.users],
    ['Активных за сутки', data.activeDay],
    ['Активных за неделю', data.activeWeek],
    ['Активных за месяц', data.activeMonth],
    ['Новых за неделю', data.newWeek],
    ['С уведомлениями', data.notifications],
    ['Студентов', data.students],
    ['Преподавателей в боте', data.teachersUsingBot],
    ['Без настроенного профиля', data.unconfigured],
    ['Групп в расписании', data.groups],
    ['Преподавателей', data.teachers],
  ] as const;

  return (
    <section aria-label="Панель администратора" className={`p-5 rounded-[20px] mt-7 ${currentThemeDef.cardClass}`}>
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="flex items-center gap-2 text-theme-text font-bold text-sm">
          <ShieldCheck size={18} /> Администратор
        </div>
        <button type="button" aria-label="Обновить сводку" disabled={loading}
          onClick={() => setRevision((value) => value + 1)}
          className="p-2 rounded-xl text-theme-text disabled:opacity-50">
          <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
        </button>
      </div>
      <dl className="grid grid-cols-2 gap-3">
        {stats.map(([label, value]) => (
          <div key={label} className={`p-3 rounded-2xl ${currentThemeDef.isLight ? 'bg-theme-bg' : 'bg-theme-bg'}`}>
            <dt className="text-xs text-theme-subtext">{label}</dt>
            <dd className="mt-1 text-xl font-bold text-theme-text">{value}</dd>
          </div>
        ))}
      </dl>
      <div className="mt-4 text-sm text-theme-text">
        <p className="font-semibold">Замены: {data.changesDate || 'ещё не загружены'}</p>
        <p className="mt-1 text-xs text-theme-subtext">Изменений: {data.changes} · Групп: {data.changedGroups}</p>
      </div>
      <AdminFeedback />
      {error && <p role="alert" className="mt-3 text-sm text-theme-text">{error}</p>}
    </section>
  );
}
