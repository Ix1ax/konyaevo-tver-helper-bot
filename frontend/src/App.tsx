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
import { EmptyState } from './components/Ui';

export function App() {
  const { activeTab, setActiveTab, loadError, loading, role, selectedGroup, selectedTeacher } = useApp();
  const views = { today: TodayView, tomorrow: TomorrowView, week: WeekView, classrooms: ClassroomsView,
    teachers: TeachersView, changes: ChangesView, profile: ProfileView };
  const View = views[activeTab];
  const needsProfile = ['today', 'tomorrow', 'week'].includes(activeTab);
  const selected = role === 'student' ? selectedGroup : selectedTeacher;

  return <div className="app-shell min-h-screen text-theme-text">
    <main className="max-w-lg mx-auto min-h-screen">
      {loadError && <p role="alert" className="notice error-notice m-5">{loadError}</p>}
      {needsProfile && (loading || loadError || !selected)
        ? <div className="page"><EmptyState title={loading ? 'Загружаем расписание' : loadError ? 'Данные недоступны' : 'Ваше расписание'}>
            {loading ? 'Это займёт несколько секунд.' : loadError ? 'Повторите загрузку в профиле.' : 'Выберите группу или преподавателя.'}
          </EmptyState><button className="button-quiet w-full" onClick={() => setActiveTab('profile')}>Открыть профиль</button></div>
        : <View />}
    </main>
    <BottomNav />
    <FeedbackPrompt />
  </div>;
}
