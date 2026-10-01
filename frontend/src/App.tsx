import { lazy, Suspense, useState } from 'react';
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

const AdminView = lazy(() => import('./views/AdminView'));

export function App() {
  const { user } = useTelegram();
  const noticeKey = `konyaevo:chat-notice:v1:${user?.id ?? 'browser'}`;
  const [dismissedNotice, setDismissedNotice] = useState<string | null>(null);
  const noticeOpen = dismissedNotice !== noticeKey && !hasSeenChatNotice(noticeKey);
  const { activeTab, setActiveTab, loadError, loading, role, selectedGroup, selectedTeacher, isAdmin, refreshing } = useApp();
  const views = { today: TodayView, tomorrow: TomorrowView, week: WeekView, classrooms: ClassroomsView,
    teachers: TeachersView, changes: ChangesView, profile: ProfileView, admin: AdminView };
  const View = views[activeTab];
  const needsProfile = ['today', 'tomorrow', 'week'].includes(activeTab);
  const selected = role === 'student' ? selectedGroup : selectedTeacher;

  return <div className={`app-shell min-h-screen text-theme-text has-share-navigation ${isAdmin ? 'has-admin-navigation' : ''}`}>
    <main className={`max-w-lg mx-auto min-h-screen ${isAdmin ? 'pb-28' : 'pb-14'}`}>
      {refreshing && <LoadingIndicator compact label="Обновляем расписание…" />}
      {loadError && <p role="alert" className="notice error-notice m-5">{loadError}</p>}
      {loading ? <LoadingIndicator /> : needsProfile && (loadError || !selected)
        ? <div className="page"><EmptyState title={loading ? 'Загружаем расписание' : loadError ? 'Данные недоступны' : 'Ваше расписание'}>
            {loading ? 'Это займёт несколько секунд.' : loadError ? 'Повторите загрузку в профиле.' : 'Выберите группу или преподавателя.'}
          </EmptyState><button className="button-quiet w-full" onClick={() => setActiveTab('profile')}>Открыть профиль</button></div>
        : <Suspense fallback={<LoadingIndicator label="Загружаем раздел…" />}><View /></Suspense>}
    </main>
    <BottomNav />
    {noticeOpen && <ChatVersionNotice storageKey={noticeKey} onDismiss={() => setDismissedNotice(noticeKey)} />}
    <FeedbackPrompt enabled={!noticeOpen} />
  </div>;
}
