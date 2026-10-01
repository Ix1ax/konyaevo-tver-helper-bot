import type { Lesson } from '../types';
import { useTheme } from '../context/ThemeContext';

export function SummaryCard({ lessons }: { lessons: Lesson[]; dayName?: string }) {
  const { currentThemeDef } = useTheme();
  return <SummaryCardContent lessons={lessons} isLight={currentThemeDef.isLight} />;
}

export function SummaryCardContent({ lessons, isLight = false }: { lessons: Lesson[]; isLight?: boolean }) {
  const currentThemeDef = { isLight };
  const active = lessons.filter((lesson) => !lesson.canceled);
  if (!active.length) return null;

  const start = active[0].time.split(/[-–—]/)[0].trim();
  const last = active[active.length - 1].time.split(/[-–—]/);
  const end = last[last.length - 1].trim();
  const count = new Set(active.map((lesson) => lesson.lessonNumber)).size;

  return (
    <div
      className={`rounded-2xl px-2 py-3.5 mb-3.5 grid grid-cols-3 text-center text-theme-subtext transition select-none ${
        currentThemeDef?.isLight
          ? 'bg-white/75 border border-slate-200/90 shadow-sm'
          : 'bg-[#141516]/75 border border-white/10 shadow-inner'
      }`}
    >
      {[
        { label: 'Начнёте в', value: start },
        { label: 'Закончите в', value: end },
        { label: 'Всего пар', value: String(count) },
      ].map((item, index) => (
        <div key={item.label} className={`min-w-0 px-1 flex flex-col items-center gap-1.5 ${index ? 'border-l border-theme-border' : ''}`}>
          <span className="text-[11px] font-medium leading-tight whitespace-nowrap">{item.label}</span>
          <span className="font-bold font-unbounded text-theme-text text-sm sm:text-base leading-tight whitespace-nowrap tabular-nums">{item.value}</span>
        </div>
      ))}
    </div>
  );
}
