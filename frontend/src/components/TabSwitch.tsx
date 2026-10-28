import type { ActiveTab } from '../types';
import { useApp } from '../context/AppContext';

/** Переключатель соседних экранов, объединённых в одну вкладку нижней панели. */
export function TabSwitch({ items }: { items: readonly (readonly [ActiveTab, string])[] }) {
  const { activeTab, setActiveTab } = useApp();
  return <div className="tab-switch" role="tablist">
    {items.map(([tab, label]) => <button key={tab} type="button" role="tab" aria-selected={activeTab === tab}
      onClick={() => activeTab !== tab && setActiveTab(tab)}>{label}</button>)}
  </div>;
}

export const DAY_TABS = [['today', 'Сегодня'], ['tomorrow', 'Завтра']] as const;
export const SEARCH_TABS = [['teachers', 'Преподаватели'], ['classrooms', 'Кабинеты']] as const;
