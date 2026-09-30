import { Calendar, CalendarCheck, CalendarDays, Building2, Search, UserRound, ShieldCheck } from 'lucide-react';
import type { ActiveTab } from '../types';
import { useApp } from '../context/AppContext';

const items = [
  ['today', 'Сегодня', Calendar], ['tomorrow', 'Завтра', CalendarCheck], ['week', 'Неделя', CalendarDays],
  ['classrooms', 'Кабинеты', Building2], ['teachers', 'Поиск', Search], ['profile', 'Профиль', UserRound],
] as const;

export function BottomNav() {
  const { activeTab, setActiveTab, isAdmin } = useApp();
  return <nav aria-label="Основная навигация" className="fixed bottom-0 inset-x-0 glass-nav border-t border-theme-border z-50">
    {isAdmin && <div className="max-w-lg mx-auto px-3 pt-2">
      <button className="button-quiet w-full flex items-center justify-center gap-2 text-xs"
        aria-current={activeTab === 'admin' ? 'page' : undefined} onClick={() => setActiveTab('admin')}>
        <ShieldCheck size={20} /> Администрирование
      </button>
    </div>}
    <div className="max-w-lg mx-auto flex pt-2 pb-[max(env(safe-area-inset-bottom),10px)] px-2">
      {items.map(([tab, label, Icon]) => <button key={tab} onClick={() => setActiveTab(tab as ActiveTab)}
        aria-current={activeTab === tab ? 'page' : undefined}
        className={`flex-1 flex flex-col items-center justify-center gap-1.5 min-h-[52px] rounded-xl ${activeTab === tab ? 'text-theme-accent' : 'text-theme-subtext'}`}>
        <Icon aria-hidden="true" /><span className="text-[10px] font-medium">{label}</span>
      </button>)}
    </div>
  </nav>;
}
