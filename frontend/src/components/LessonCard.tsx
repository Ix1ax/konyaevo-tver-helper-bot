import React from 'react';
import {
  Users,
  UserRound,
  MapPin,
  BookOpen,
  Languages,
  Code2,
  Calculator,
  Atom,
  Activity,
  ArrowRightLeft,
  XCircle,
  Clock,
} from 'lucide-react';
import type { Lesson } from '../types';
import { useApp } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';

export function LessonCard({ lesson, highlightCurrent = false }: { lesson: Lesson; highlightCurrent?: boolean }) {
  const { role, setPreviewTeacher, setActiveTab } = useApp();
  const { currentThemeDef } = useTheme();

  return <LessonCardContent lesson={lesson} highlightCurrent={highlightCurrent} role={role}
    isLight={currentThemeDef.isLight} onTeacher={teacher => { setPreviewTeacher(teacher); setActiveTab('teachers'); }} />;
}

/** Shared presentation for the screen and exported image. */
export function LessonCardContent({ lesson, highlightCurrent = false, role = 'student', isLight = false, onTeacher = () => {} }: {
  lesson: Lesson; highlightCurrent?: boolean; role?: string; isLight?: boolean; onTeacher?: (teacher: string) => void;
}) {
  const currentThemeDef = { isLight };
  const openTeacher = () => { if (lesson.teacher && role === 'student') onTeacher(lesson.teacher); };

  const [minute, setMinute] = React.useState(() => Math.floor(Date.now() / 60000));
  React.useEffect(() => {
    if (!highlightCurrent) return;
    const timer = setInterval(() => setMinute(Math.floor(Date.now() / 60000)), 30000);
    return () => clearInterval(timer);
  }, [highlightCurrent]);

  // Текущую пару определяем по московскому времени.
  const isOngoing = React.useMemo(() => {
    if (!highlightCurrent || lesson.canceled || !lesson.time || !lesson.time.includes('-')) return false;
    const now = new Date();
    const moscowMinutes = ((now.getUTCHours() + 3) % 24) * 60 + now.getUTCMinutes();
    const [startStr, endStr] = lesson.time.split('-').map(s => s.trim());
    const parseMins = (t: string) => {
      const parts = t.split(':');
      if (parts.length < 2) return -1;
      return parseInt(parts[0], 10) * 60 + parseInt(parts[1], 10);
    };
    const startM = parseMins(startStr);
    const endM = parseMins(endStr);
    return startM >= 0 && endM >= 0 && moscowMinutes >= startM && moscowMinutes < endM;
  }, [lesson.time, lesson.canceled, minute, highlightCurrent]);

  // Для предметов используем иконки из одного набора.
  const getSubjectIcon = (subj: string) => {
    const s = subj.toLowerCase();
    if (s.includes('англ') || s.includes('язык') || s.includes('русс') || s.includes('литер')) return Languages;
    if (s.includes('програм') || s.includes('разработ') || s.includes('информ') || s.includes('модул') || s.includes('мдк') || s.includes('web') || s.includes('сеть')) return Code2;
    if (s.includes('матем') || s.includes('моделир') || s.includes('стат') || s.includes('алгебр') || s.includes('геомет')) return Calculator;
    if (s.includes('физи') || s.includes('электрон') || s.includes('аппарат') || s.includes('схем')) return Atom;
    if (s.includes('физкульт') || s.includes('спорт')) return Activity;
    return BookOpen;
  };

  const SubjectIcon = getSubjectIcon(lesson.subject);
  const isReplacement = Boolean(lesson.changed || lesson.type === 'Замена');

  return (
    <article
      data-state={lesson.canceled ? 'canceled' : isOngoing ? 'ongoing' : 'default'}
      className={`glass-card rounded-[24px] p-4 sm:p-5 mb-3.5 relative overflow-hidden backdrop-blur-xl transition-all duration-200 ${
        lesson.canceled
          ? currentThemeDef?.isLight
            ? 'bg-rose-50/60 border border-rose-200/80 shadow-sm opacity-65'
            : 'bg-rose-950/20 border border-rose-500/30 shadow-none opacity-60'
          : isOngoing
          ? currentThemeDef?.isLight
            ? 'bg-blue-50/80 border-2 border-blue-500 shadow-[0_0_24px_rgba(59,130,246,0.2)]'
            : 'bg-[#111213]/90 border-2 border-blue-500 shadow-[0_0_28px_rgba(59,130,246,0.28)]'
          : currentThemeDef?.isLight
          ? 'bg-white/80 border border-slate-200/90 shadow-[0_6px_20px_rgba(0,0,0,0.04)] hover:border-slate-300'
          : 'bg-[#141516]/80 border border-white/[0.08] shadow-[0_6px_20px_rgba(0,0,0,0.25)] hover:border-white/15'
      }`}
    >
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <Clock size={16} className={isOngoing ? 'text-blue-500' : 'text-theme-subtext opacity-60'} />
          <span style={{ whiteSpace: 'nowrap', display: 'inline-block' }}
            className={`lesson-time text-xl font-black font-unbounded tracking-tight text-theme-text ${lesson.canceled ? 'line-through opacity-50' : ''}`}>
            {lesson.time}
          </span>
        </div>
        {lesson.canceled ? (
          <span className="px-3 py-1 rounded-full text-xs font-semibold font-sans bg-rose-500/15 border border-rose-500/30 text-rose-500 flex items-center gap-1.5 shadow-sm">
            <XCircle size={13} />
            <span>Отменена</span>
          </span>
        ) : isOngoing ? (
          <span className="px-3 py-1 rounded-full text-xs font-semibold font-sans bg-blue-500 text-white flex items-center gap-1.5 shadow-sm">
            <span className="w-1.5 h-1.5 rounded-full bg-white" />
            <span>Сейчас идёт</span>
          </span>
        ) : null}
      </div>
      <div className="text-xs font-semibold text-theme-subtext mt-1 mb-3 opacity-60">
        {lesson.lessonNumber} пара
      </div>
      <div className={`flex items-start gap-3 mb-3.5 ${lesson.canceled ? 'line-through opacity-60' : ''}`}>
        <div className="w-9 h-9 rounded-xl bg-blue-500/10 text-blue-500 flex items-center justify-center shrink-0 mt-0.5 border border-blue-500/15">
          <SubjectIcon size={18} />
        </div>
        <div className="min-w-0 flex-1">
          <h2 className="text-base font-bold text-theme-text font-sans leading-snug pt-0.5">
            {lesson.subject}
          </h2>
          {isReplacement && !lesson.canceled && (
            <span className="mt-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-semibold bg-amber-500/15 border border-amber-500/30 text-amber-600 dark:text-amber-400">
              <ArrowRightLeft size={13} aria-hidden="true" />
              <span>Замена</span>
            </span>
          )}
        </div>
      </div>
      {!!lesson.subgroups?.length && <div className="space-y-2 mb-3">
        {lesson.subgroups.map(subgroup => <div key={subgroup.number} className="surface rounded-xl p-3 text-xs">
          <p className="text-theme-subtext mb-2">{subgroup.number}-я подгруппа</p>
          <div className="flex flex-wrap items-center gap-3">
            <button type="button" disabled={role !== 'student'} className="inline-flex items-center gap-1.5 text-left"
              onClick={() => { onTeacher(subgroup.teacher); }}>
              <UserRound size={13} />{subgroup.teacher}
            </button>
            <span className="inline-flex items-center gap-1.5 font-semibold"><MapPin size={13} />{subgroup.room}</span>
          </div>
        </div>)}
      </div>}
      <div className="lesson-metadata flex flex-wrap items-center gap-1.5 text-xs font-medium">
        {!lesson.subgroups?.length && lesson.teacher && (
          <button
            data-meta="teacher"
            onClick={openTeacher}
            disabled={role !== 'student'}
            className={`px-2.5 py-1 rounded-xl flex items-center gap-1.5 transition active:scale-95 ${
              currentThemeDef?.isLight
                ? 'bg-slate-100 text-slate-700 border border-slate-200 hover:bg-slate-200'
                : 'bg-white/[0.06] text-slate-300 border border-white/10 hover:bg-white/10'
            }`}
          >
            <UserRound size={13} className="opacity-70" />
            <span>{lesson.teacher}</span>
          </button>
        )}
        {!lesson.subgroups?.length && lesson.room && (
          <span className={`px-2.5 py-1 rounded-xl flex items-center gap-1.5 font-bold ${
            currentThemeDef?.isLight
              ? 'bg-slate-100 text-slate-900 border border-slate-200'
              : 'bg-white/[0.06] text-white border border-white/10'
          }`}>
            <MapPin size={13} className="text-blue-400" />
            <span>{lesson.room}</span>
          </span>
        )}
        {lesson.groupName && (
          <span className={`px-2.5 py-1 rounded-xl flex items-center gap-1.5 ${
            currentThemeDef?.isLight
              ? 'bg-indigo-50 text-indigo-700 border border-indigo-200'
              : 'bg-white/[0.06] text-indigo-300 border border-white/10'
          }`}>
            <Users size={13} className="opacity-70" />
            <span>{lesson.groupName}</span>
          </span>
        )}
        {lesson.weekType === 'red' && (
          <span className="px-2.5 py-1 rounded-xl flex items-center gap-1.5 text-xs font-semibold bg-rose-500/10 text-rose-400 border border-rose-500/20">
            <span className="w-2 h-2 rounded-full bg-rose-500 shadow-[0_0_6px_rgba(244,63,94,0.6)]" />
            <span>Красная</span>
          </span>
        )}
        {lesson.weekType === 'blue' && (
          <span className="px-2.5 py-1 rounded-xl flex items-center gap-1.5 text-xs font-semibold bg-blue-500/10 text-blue-400 border border-blue-500/20">
            <span className="w-2 h-2 rounded-full bg-blue-500 shadow-[0_0_6px_rgba(59,130,246,0.6)]" />
            <span>Синяя</span>
          </span>
        )}
      </div>

    </article>
  );
}
