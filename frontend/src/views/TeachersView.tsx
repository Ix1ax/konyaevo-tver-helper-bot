import { useEffect, useState } from 'react';
import { Search, ChevronRight, ArrowLeft, Check, UserRound } from 'lucide-react';
import { Header } from '../components/Header';
import { LessonCard } from '../components/LessonCard';
import { DaySelector } from '../components/DaySelector';
import { LoadingIndicator, EmptyState, IconBadge } from '../components/Ui';
import { useApp } from '../context/AppContext';
import { api, profileRequest } from '../api/client';
import type { ScheduleMap } from '../types';

const DAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница'];
export function TeachersView() {
  const { teachersData, selectedTeacher, setSelectedTeacher, setRole, role, setActiveTab, info } = useApp();
  const [selecting, setSelecting] = useState(false);
  const [query, setQuery] = useState('');
  const [letter, setLetter] = useState('');
  const [teacher, setTeacher] = useState<string | null>(selectedTeacher || null);
  const [schedule, setSchedule] = useState<ScheduleMap>({});
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [day, setDay] = useState(info.todayName || DAYS[0]);
  useEffect(() => {
    if (!teacher) return;
    let active = true;
    setLoading(true); setError(''); setSchedule({});
    api.getTeacherSchedule(teacher).then((data) => { if (active) setSchedule(data); })
      .catch(() => { if (active) setError('Не удалось загрузить расписание преподавателя.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [teacher]);
  const teachers = teachersData.teachers.filter((name) => query.trim()
    ? name.toLowerCase().includes(query.trim().toLowerCase()) : !letter || name.startsWith(letter));
  const lessons = (schedule[day]?.lessons || []).filter((lesson) => !lesson.weekType || lesson.weekType === (info.isRedWeek ? 'red' : 'blue'));
  async function chooseTeacher() {
    if (!teacher) return;
    setSelecting(true); setError('');
    try {
      if (window.Telegram?.WebApp?.initData) await profileRequest({ role: 'teacher', teacher });
      setSelectedTeacher(teacher); setRole('teacher'); setActiveTab('today');
    } catch (e) { setError(e instanceof Error ? e.message : 'Не удалось сохранить расписание.'); }
    finally { setSelecting(false); }
  }
  return <div className="page animate-fade-in">
    {teacher ? <>
      <button className="flex items-center gap-2 text-sm text-theme-subtext mb-6" onClick={() => setTeacher(null)}><ArrowLeft />Все преподаватели</button>
      <div className="flex items-center gap-3 mb-5"><IconBadge icon={UserRound} /><h1 className="font-semibold text-xl">{teacher}</h1></div>
      <button className="button-quiet mb-6" disabled={selecting} onClick={chooseTeacher}>
        <Check />{selecting ? 'Сохраняем…' : role === 'teacher' && selectedTeacher === teacher ? 'Моё расписание' : 'Выбрать моим расписанием'}
      </button>
      <DaySelector days={DAYS} selectedDay={day} onSelectDay={setDay} todayName={info.todayName} />
      <div className="mt-4">{error ? <p role="alert" className="notice error-notice">{error}</p>
        : loading ? <LoadingIndicator />
        : lessons.length ? lessons.map((lesson, index) => <LessonCard key={index} lesson={lesson} highlightCurrent={day === info.todayName} />) : <EmptyState />}</div>
    </> : <>
      <Header title="Преподаватели" showGreeting={false} />
      <label htmlFor="teacher-search" className="sr-only">Поиск преподавателя</label>
      <div className="relative mb-4"><Search className="absolute left-3 top-3 text-theme-subtext" />
        <input id="teacher-search" className="field !pl-10" placeholder="Фамилия или инициалы" value={query} onChange={(event) => setQuery(event.target.value)} /></div>
      <div className="flex gap-2 overflow-x-auto no-scrollbar mb-4 pb-1">
        {['', ...teachersData.letters].map((value) => <button className="choice" key={value} aria-pressed={letter === value}
          onClick={() => { setLetter(value); setQuery(''); }}>{value || 'Все'}</button>)}
      </div>
      <div className="settings-group">{teachers.map((name) => <button key={name} className="settings-row" onClick={() => setTeacher(name)}>
        <span className="flex-1 text-sm">{name}</span><ChevronRight className="text-theme-subtext" />
      </button>)}</div>
      {!teachers.length && <EmptyState title="Ничего не найдено">Попробуйте изменить запрос.</EmptyState>}
    </>}
  </div>;
}
