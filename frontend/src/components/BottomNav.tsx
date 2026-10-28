import { Calendar, CalendarDays, Search, UserRound, ShieldCheck } from 'lucide-react';
import type { ActiveTab } from '../types';
import { useApp } from '../context/AppContext';
import { useMascot } from '../context/MascotContext';
import { useTelegram } from '../hooks/useTelegram';
import { FlameIcon, Mascot } from './Mascot';

// Соседние экраны живут в одной вкладке: «Завтра» — внутри «Сегодня», «Кабинеты» — внутри «Поиска».
const left = [['today', 'Расписание', Calendar, ['today', 'tomorrow']], ['week', 'Неделя', CalendarDays, ['week']]] as const;
const right = [['teachers', 'Поиск', Search, ['teachers', 'classrooms']], ['profile', 'Профиль', UserRound, ['profile']]] as const;

export function BottomNav() {
  const { activeTab, setActiveTab, isAdmin } = useApp();
  const { mascot, look } = useMascot();
  const { haptic } = useTelegram();
  const tab = ([id, label, Icon, group]: typeof left[number] | typeof right[number]) => {
    const current = (group as readonly string[]).includes(activeTab);
    return <button key={id} onClick={() => setActiveTab(id as ActiveTab)} aria-current={current ? 'page' : undefined}
      className={`nav-tab ${current ? 'text-theme-accent' : 'text-theme-subtext'}`}>
      <Icon aria-hidden="true" /><span>{label}</span>
    </button>;
  };
  // Огонёк ждёт: учебный день, а расписание сегодня ещё не открывали.
  const waiting = Boolean(mascot && mascot.studyDay && !mascot.countedToday);
  return <nav aria-label="Основная навигация" className="fixed bottom-0 inset-x-0 glass-nav border-t border-theme-border z-50">
    {isAdmin && <div className="max-w-lg mx-auto px-3 pt-2">
      <button className="button-quiet w-full flex items-center justify-center gap-2 text-xs"
        aria-current={activeTab === 'admin' ? 'page' : undefined} onClick={() => setActiveTab('admin')}>
        <ShieldCheck size={20} /> Администрирование
      </button>
    </div>}
    <div className="max-w-lg mx-auto flex items-end pt-1.5 pb-[max(env(safe-area-inset-bottom),10px)] px-2">
      {left.map(tab)}
      <button className="nav-parych" aria-current={activeTab === 'mascot' ? 'page' : undefined} data-waiting={waiting || undefined}
        data-supporter={(mascot?.donated ?? 0) > 0 || undefined}
        aria-label={mascot ? `Парыч. Огонёк: ${mascot.streak} дней подряд` : 'Парыч'}
        onClick={() => { haptic.impact('medium'); setActiveTab('mascot'); }}>
        <span className="nav-parych-orb">
          <Mascot size={66} look={look} pose="wave" mood={waiting ? 'think' : 'happy'} className="nav-parych-mascot" />
        </span>
        {mascot && <span className="nav-parych-streak font-unbounded" data-counted={mascot.countedToday || undefined}>
          <FlameIcon size={14} />{mascot.streak}
        </span>}
        <span className="nav-parych-label">Парыч</span>
      </button>
      {right.map(tab)}
    </div>
  </nav>;
}
