import { Header } from './Header';
import { SummaryCard } from './SummaryCard';
import { LessonCard } from './LessonCard';
import { EmptyState } from './Ui';
import { ArrowRightLeft, ChevronRight } from 'lucide-react';
import { useApp } from '../context/AppContext';

export function ScheduleDay({ tomorrow = false }: { tomorrow?: boolean }) {
  const { schedule, info, setActiveTab } = useApp();
  const day = tomorrow ? info.tomorrowName : info.todayName;
  const red = tomorrow ? (info.tomorrowRedWeek ?? info.isRedWeek) : info.isRedWeek;
  const lessons = (schedule[day]?.lessons || []).filter((lesson) => !lesson.weekType || lesson.weekType === (red ? 'red' : 'blue'));
  const changed = lessons.some((lesson) => lesson.changed);
  return <div className="page animate-fade-in">
    <Header title={tomorrow ? 'Завтра' : 'Сегодня'} showGreeting={!tomorrow} />
    {day ? <>
      {tomorrow && <p className="text-sm text-theme-subtext mb-4">{day} · {red ? 'Красная' : 'Синяя'} неделя</p>}
      {changed && (
        <button
          className="mb-3.5 p-3 rounded-2xl bg-amber-500/[0.08] border border-amber-500/25 text-amber-500 dark:text-amber-400 flex items-center gap-2.5 text-xs font-semibold w-full text-left active:scale-[0.99] transition shadow-sm"
          onClick={() => setActiveTab('changes')}
        >
          <ArrowRightLeft size={16} className="text-amber-500 shrink-0" />
          <span className="flex-1">Есть изменения в расписании</span>
          <ChevronRight size={16} className="opacity-50" />
        </button>
      )}
      <SummaryCard lessons={lessons} dayName={day} />
      {lessons.length ? lessons.map((lesson, index) => <LessonCard key={`${lesson.lessonNumber}-${lesson.groupName}-${index}`} lesson={lesson} highlightCurrent={!tomorrow} />)
        : <EmptyState>На этот день занятий не запланировано.</EmptyState>}
    </> : <><EmptyState title="Выходной">Занятий нет. Расписание на другие дни доступно во вкладке «Неделя».</EmptyState>
      <button className="button-quiet w-full" onClick={() => setActiveTab('week')}>Расписание на неделю</button></>}
  </div>;
}
