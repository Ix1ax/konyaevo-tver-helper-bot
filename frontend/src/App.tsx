import { useState } from 'react';
import { useApp } from './context/AppContext';
import { BottomNav } from './components/BottomNav';
import { TodayView } from './views/TodayView';
import { TomorrowView } from './views/TomorrowView';
import { WeekView } from './views/WeekView';
import { ClassroomsView } from './views/ClassroomsView';
import { TeachersView } from './views/TeachersView';
import { ChangesView } from './views/ChangesView';
import { ProfileView } from './views/ProfileView';
import { FeedbackPrompt } from './components/FeedbackPrompt';
import { ChatVersionNotice, hasSeenChatNotice } from './components/ChatVersionNotice';
import { useTelegram } from './hooks/useTelegram';
import { EmptyState, LoadingIndicator } from './components/Ui';
import { LoadErrorNotice } from './components/LoadErrorNotice';

import AdminView from './views/AdminView';
import { MascotView } from './views/MascotView';
import { MascotReward } from './components/MascotReward';
import { ViewErrorBoundary } from './components/ViewErrorBoundary';

export function App() {
  const { user } = useTelegram();
  const noticeKey = `konyaevo:chat-notice:v1:${user?.id ?? 'browser'}`;
  const [dismissedNotice, setDismissedNotice] = useState<string | null>(null);
  const noticeOpen = dismissedNotice !== noticeKey && !hasSeenChatNotice(noticeKey);
  const { activeTab, setActiveTab, loadError, loading, role, selectedGroup, selectedTeacher, isAdmin, refreshing } = useApp();
  const views = { today: TodayView, tomorrow: TomorrowView, week: WeekView, classrooms: ClassroomsView,
    teachers: TeachersView, changes: ChangesView, profile: ProfileView, admin: AdminView, mascot: MascotView };
  const View = views[activeTab];
  const needsProfile = ['today', 'tomorrow', 'week'].includes(activeTab);
  const selected = role === 'student' ? selectedGroup : selectedTeacher;

  return <div className={`app-shell min-h-screen text-theme-text has-share-navigation ${isAdmin ? 'has-admin-navigation' : ''}`}>
    <main className={`max-w-lg mx-auto min-h-screen ${isAdmin ? 'pb-28' : 'pb-14'}`}>
      {refreshing && <LoadingIndicator compact label="Обновляем расписание…" />}
      {loadError && !loading && !needsProfile && <div className="px-5 pt-5"><LoadErrorNotice message={loadError} /></div>}
      {loading ? <LoadingIndicator fullscreen /> : needsProfile && loadError
        ? <div className="page"><LoadErrorNotice message={loadError} /></div>
        : needsProfile && !selected
        ? <div className="page"><EmptyState title="Ваше расписание">Выберите группу или преподавателя.</EmptyState>
          <button className="button-quiet w-full" onClick={() => setActiveTab('profile')}>Открыть профиль</button></div>
        : <ViewErrorBoundary key={activeTab} onBack={() => setActiveTab('profile')}><View /></ViewErrorBoundary>}
    </main>
    <BottomNav />
    {noticeOpen && <ChatVersionNotice storageKey={noticeKey} onDismiss={() => setDismissedNotice(noticeKey)} />}
    <FeedbackPrompt enabled={!noticeOpen} />
    {!loading && !noticeOpen && <MascotReward />}
  </div>;
}
