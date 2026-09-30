import { useEffect, useState } from 'react';
import { ArrowRightLeft, Ban, ArrowLeft } from 'lucide-react';
import { Header } from '../components/Header';
import { LoadingIndicator, EmptyState, IconBadge } from '../components/Ui';
import { useApp } from '../context/AppContext';
import { api } from '../api/client';
import type { ChangeItem } from '../types';

export function ChangesView() {
  const { role, selectedGroup, selectedTeacher, isAdmin, info, setActiveTab } = useApp();
  const [items, setItems] = useState<ChangeItem[]>([]);
  const [all, setAll] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let active = true;
    setLoading(true); setError(''); setItems([]);
    async function load(): Promise<ChangeItem[]> {
      if (all && isAdmin) return api.getAllChanges();
      if (role === 'student') return selectedGroup ? (await api.getGroupChanges(selectedGroup)).items : [];
      if (!selectedTeacher) return [];
      const schedule = await api.getTeacherSchedule(selectedTeacher);
      // Отмены берём из собранного расписания: в тексте отмены фамилии может не быть.
      const changes = new Map<string, ChangeItem>();
      Object.values(schedule).flatMap(day => day.lessons).filter(lesson => lesson.changed).forEach(lesson => {
        const item = { groupName: lesson.groupName || '', slot: lesson.lessonNumber, text: lesson.changeText || '', canceled: Boolean(lesson.canceled) };
        changes.set(`${item.groupName}:${item.slot}`, item);
      });
      return [...changes.values()].sort((a, b) => a.groupName.localeCompare(b.groupName, 'ru') || a.slot - b.slot);
    }
    load().then(data => { if (active) setItems(data); })
      .catch(e => { if (active) setError(e instanceof Error ? e.message : 'Не удалось загрузить замены.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [role, selectedGroup, selectedTeacher, all, isAdmin, info.changesDate]);
  const visible = items;
  return <div className="page animate-fade-in">
    <button className="text-sm text-theme-subtext flex items-center gap-2 mb-5" onClick={() => setActiveTab('today')}><ArrowLeft />К расписанию</button>
    <Header title="Изменения" showGreeting={false} />
    <p className="text-sm text-theme-subtext mb-4">{info.changesDate || 'Дата ещё не опубликована'}</p>
    {isAdmin && <div className="grid grid-cols-2 gap-2 mb-5"><button className="choice" aria-pressed={!all} onClick={() => setAll(false)}>Мои</button>
      <button className="choice" aria-pressed={all} onClick={() => setAll(true)}>Весь колледж</button></div>}
    {error ? <p role="alert" className="notice error-notice">{error}</p> : loading ? <LoadingIndicator label="Загружаем изменения…" />
      : visible.length ? <div className="settings-group">{visible.map((item) => <div className="settings-row !items-start" key={`${item.groupName}:${item.slot}`}>
        <IconBadge icon={item.canceled ? Ban : ArrowRightLeft} /><div><h2 className="font-semibold text-sm">{item.groupName} · {item.slot} пара</h2>
          <p className="mt-2 text-sm text-theme-subtext whitespace-pre-line leading-relaxed">{item.canceled ? 'Отмена' : item.text}</p></div>
      </div>)}</div> : <EmptyState title="Изменений нет">Для выбранного расписания замены не опубликованы.</EmptyState>}
  </div>;
}
