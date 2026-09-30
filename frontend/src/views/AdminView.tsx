import { useState } from 'react';
import { ArrowLeft } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { AdminPanel } from '../components/AdminPanel';
import { AdminFeedback } from '../components/AdminFeedback';

const sections = [['users', 'Пользователи'], ['schedule', 'Данные'], ['feedback', 'Отзывы']] as const;
type Section = typeof sections[number][0];

export default function AdminView() {
  const { isAdmin, setActiveTab } = useApp();
  const [section, setSection] = useState<Section>('users');
  // Даже при ручном переключении вкладки запросы не выполняются без разрешения сервера.
  if (!isAdmin) return <div className="page"><p>Раздел доступен только администратору.</p>
    <button className="button-quiet mt-4" onClick={() => setActiveTab('profile')}>К профилю</button></div>;
  return <div className="page">
    <button className="flex items-center gap-2 text-sm text-theme-subtext mb-5" onClick={() => setActiveTab('today')}>
      <ArrowLeft size={20} /> К расписанию
    </button>
    <h1 className="text-2xl font-bold mb-5">Администрирование</h1>
    <div role="group" aria-label="Разделы администрирования" className="grid grid-cols-3 gap-2 mb-5">
      {sections.map(([id, label]) => <button key={id} className="choice text-xs" aria-pressed={section === id}
        onClick={() => setSection(id)}>{label}</button>)}
    </div>
    {section === 'feedback' ? <div className="settings-group p-5"><AdminFeedback /></div>
      : <AdminPanel section={section} />}
  </div>;
}
