import { ShareSchedule } from './ShareSchedule';
import { useTheme } from '../context/ThemeContext';
import { moscowDayDate } from '../utils/calendar';
import { useApp } from '../context/AppContext';
import { useMascot } from '../context/MascotContext';

/** «Поделиться расписанием» в шапке дня: картинка на сегодня или на завтра. */
export function ShareButton() {
  const { role, selectedGroup, selectedTeacher, schedule, info, refreshing, loading } = useApp();
  const { currentThemeDef } = useTheme();
  const { sendEvent } = useMascot();
  const choices = [false, true].map(tomorrow => {
    const day = tomorrow ? info.tomorrowName : info.todayName;
    const red = tomorrow ? info.tomorrowRedWeek ?? info.isRedWeek : info.isRedWeek;
    return {label: tomorrow ? 'Завтра' : 'Сегодня', options: {target: role === 'student' ? selectedGroup : selectedTeacher,
      role, day: day || 'Выходной', date: moscowDayDate(info.serverTime, tomorrow ? 1 : 0), red, isLight: currentThemeDef.isLight,
      highlightCurrent: !tomorrow, lessons: (schedule[day]?.lessons || []).filter(lesson => !lesson.weekType || lesson.weekType === (red ? 'red' : 'blue'))}};
  });
  return <ShareSchedule options={choices[0].options} choices={choices} disabled={refreshing || loading || !choices[0].options.target}
    onShared={() => sendEvent('share')} />;
}
