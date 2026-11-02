import type { Lesson } from '../types';
import { Bell } from 'lucide-react';
import { useTheme } from '../context/ThemeContext';

export function SummaryCard({ lessons, onBells }: { lessons: Lesson[]; dayName?: string; onBells?: () => void }) {
  const { currentThemeDef } = useTheme();
  return <SummaryCardContent lessons={lessons} isLight={currentThemeDef.isLight} onBells={onBells} />;
}

/** onBells — сводка нажимается и открывает звонки на день (колокольчик в углу). */
export function SummaryCardContent({ lessons, isLight = false, onBells }: { lessons: Lesson[]; isLight?: boolean; onBells?: () => void }) {
  const currentThemeDef = { isLight };
  const active = lessons.filter((lesson) => !lesson.canceled);
  if (!active.length) return null;

  const start = active[0].time.split(/[-–—]/)[0].trim();
  const last = active[active.length - 1].time.split(/[-–—]/);
  const end = last[last.length - 1].trim();
  const count = new Set(active.map((lesson) => lesson.lessonNumber)).size;

  const Root = onBells ? 'button' : 'div';
  return (
    <Root
      {...(onBells ? { type: 'button' as const, onClick: onBells, 'aria-label': 'Сводка дня. Открыть звонки' } : {})}
      className={`relative w-full rounded-2xl px-2 py-3.5 mb-3.5 grid grid-cols-3 text-center text-theme-subtext transition select-none ${onBells ? 'active:scale-[0.99]' : ''} ${
        currentThemeDef?.isLight
          ? 'bg-white/75 border border-slate-200/90 shadow-sm'
          : 'bg-[#141516]/75 border border-white/10 shadow-inner'
      }`}
    >
      {[
        { label: 'Начнёте в', value: /^\d{1,2}:\d{2}$/.test(start) ? start : 'Уточняется' },
        { label: 'Закончите в', value: /^\d{1,2}:\d{2}$/.test(end) ? end : 'Уточняется' },
        { label: 'Всего пар', value: String(count) },
      ].map((item, index) => (
        <div key={item.label} className={`min-w-0 px-1 flex flex-col items-center gap-1.5 ${index ? 'border-l border-theme-border' : ''}`}>
          <span className="text-[11px] font-medium leading-tight whitespace-nowrap">{item.label}</span>
          <span className={`font-bold font-unbounded text-theme-text ${item.value === 'Уточняется' ? 'text-[10px] sm:text-xs' : 'text-sm sm:text-base'} leading-tight max-w-full break-words tabular-nums`}>{item.value}</span>
        </div>
      ))}
      {onBells && <Bell size={13} aria-hidden="true" className="absolute bottom-2 right-2.5 opacity-40" />}
    </Root>
  );
}
