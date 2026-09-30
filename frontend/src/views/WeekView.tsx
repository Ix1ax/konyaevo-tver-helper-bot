import { useEffect, useState } from 'react';
import { ArrowLeftRight } from 'lucide-react';
import { Header } from '../components/Header';
import { DaySelector } from '../components/DaySelector';
import { SummaryCard } from '../components/SummaryCard';
import { LessonCard } from '../components/LessonCard';
import { EmptyState } from '../components/Ui';
import { useApp } from '../context/AppContext';

import { api } from '../api/client';
import type { ScheduleMap } from '../types';

const DAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница'];
export function WeekView() {
  const { info, role, selectedGroup, selectedTeacher, refreshing } = useApp();
  const target = role === 'student' ? selectedGroup : selectedTeacher;
  const [schedule, setSchedule] = useState<ScheduleMap>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true;
    setSchedule({}); setLoading(true); setError('');
    if (!target) { setLoading(false); return; }
    api.getBaseSchedule(role, target).then(data => { if (active) setSchedule(data); })
      .catch(() => { if (active) setError('Не удалось загрузить основное расписание. Обновите данные.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [role, target, refreshing]);
  const [day, setDay] = useState(info.todayName || DAYS[0]);
  const [alternate, setAlternate] = useState(false);
  const red = alternate ? !info.isRedWeek : info.isRedWeek;
  const lessons = (schedule[day]?.lessons || []).filter((lesson) => !lesson.weekType || lesson.weekType === (red ? 'red' : 'blue'));
  return <div className="page animate-fade-in">
    <Header title="Неделя" showGreeting={false} />
    <p className="text-sm text-theme-subtext mb-4">Основное расписание без замен и отмен. Актуальные изменения — в разделах «Сегодня», «Завтра» и «Изменения».</p>
    <button onClick={() => setAlternate(!alternate)} className="button-quiet mb-5 w-full justify-between">
      <span>{red ? 'Красная' : 'Синяя'} неделя · {alternate ? 'другая' : 'текущая'}</span><ArrowLeftRight aria-hidden="true" />
    </button>
    <DaySelector days={DAYS} selectedDay={day} onSelectDay={setDay} todayName={info.todayName} />
    <SummaryCard lessons={lessons} dayName={day} />
    {loading ? <EmptyState>Загружаем основное расписание…</EmptyState> : error ? <EmptyState>{error}</EmptyState> : lessons.length ? lessons.map((lesson, index) => <LessonCard key={`${lesson.lessonNumber}-${index}`} lesson={lesson} />)
      : <EmptyState>На выбранный день и неделю занятий нет.</EmptyState>}
  </div>;
}
