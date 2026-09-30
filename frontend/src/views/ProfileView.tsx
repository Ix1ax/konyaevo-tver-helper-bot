import React, { useState, useEffect } from 'react';
import {
  UserRound,
  GraduationCap,
  ChevronRight,
  RefreshCw,
  Check,
  X,
  Search,
} from 'lucide-react';
import { useApp } from '../context/AppContext';
import { useTheme, THEMES } from '../context/ThemeContext';
import { useTelegram } from '../hooks/useTelegram';
import { profileRequest } from '../api/client';
import { HelpPanel } from '../components/HelpPanel';
import { NotificationSettings } from '../components/NotificationSettings';

export function ProfileView() {
  const {
    role,
    setRole,
    selectedGroup,
    setSelectedGroup,
    selectedTeacher,
    setSelectedTeacher,
    groupsData,
    teachersData,
    refreshAll,
    refreshing,
  } = useApp();
  const { theme, setTheme } = useTheme();
  const { user, haptic } = useTelegram();

  const [profileError, setProfileError] = useState('');
  const [savingProfile, setSavingProfile] = useState(false);
  const [editing, setEditing] = useState(false);
  const [search, setSearch] = useState('');
  const [course, setCourse] = useState(() =>
    Object.keys(groupsData.groupsByCourse).find((key) =>
      groupsData.groupsByCourse[key].includes(selectedGroup)
    ) || groupsData.courses[0] || ''
  );

  const activeCourse = groupsData.courses.includes(course) ? course : groupsData.courses[0] || '';
  const teachers = teachersData.teachers.filter((name) =>
    name.toLowerCase().includes(search.trim().toLowerCase())
  );

  const displayName = user?.first_name || (role === 'student' ? 'Студент' : 'Преподаватель');
  const initial = (displayName.charAt(0) || 'И').toUpperCase();
  const subtitle = role === 'student'
    ? (selectedGroup ? `${selectedGroup}` : 'Группа не выбрана')
    : (selectedTeacher ? `${selectedTeacher}` : 'Преподаватель не выбран');

  async function choose(name: string) {
    setSavingProfile(true); setProfileError('');
    try {
      if (window.Telegram?.WebApp?.initData) {
        await profileRequest(role === 'student' ? { role, group: name } : { role, teacher: name });
      }
      if (role === 'student') setSelectedGroup(name); else setSelectedTeacher(name);
      setEditing(false); haptic.notification('success');
    } catch (e) { setProfileError(e instanceof Error ? e.message : 'Не удалось сохранить профиль.'); }
    finally { setSavingProfile(false); }
  }

  return (
    <div className="page profile-settings animate-fade-in select-none">
      <div className="flex items-center justify-between mb-5">
        <div>
          <h1 className="text-xl font-black font-unbounded tracking-tight text-theme-text uppercase leading-none">
            КОНЯЕВО
          </h1>
          <p className="text-xs text-theme-subtext font-semibold mt-1">
            {subtitle}
          </p>
        </div>

        <div className="w-10 h-10 rounded-full bg-blue-500 text-white font-unbounded font-black text-sm flex items-center justify-center shadow-lg shadow-blue-500/25 overflow-hidden">
          {user?.photo_url ? (
            <img src={user.photo_url} alt="Avatar" className="w-full h-full object-cover" />
          ) : (
            <span>{initial}</span>
          )}
        </div>
      </div>
      <div className="flex items-center gap-4 mb-6">
        <div className="w-16 h-16 rounded-[22px] bg-blue-500 text-white font-unbounded text-2xl font-black shadow-lg shadow-blue-500/30 flex items-center justify-center shrink-0">
          {user?.photo_url ? (
            <img src={user.photo_url} alt="Avatar" className="w-full h-full object-cover rounded-[22px]" />
          ) : (
            <span>{initial}</span>
          )}
        </div>
        <div className="min-w-0">
          <h2 className="text-2xl font-black font-unbounded text-theme-text truncate leading-tight">
            {displayName}
          </h2>
          <p className="text-xs font-semibold text-theme-subtext mt-1 opacity-70">
            {subtitle}
          </p>
        </div>
      </div>
      <h3 className="section-label">Настройки</h3>
      <div className="settings-group mb-4">
        <button
          className="settings-row"
          onClick={() => { haptic.impact('light'); setEditing(!editing); }}
          aria-expanded={editing}
        >
          <div className="icon-badge">
            <GraduationCap size={20} />
          </div>
          <div className="flex-1 min-w-0">
            <span className="block font-semibold truncate text-sm">
              {(role === 'student' ? selectedGroup : selectedTeacher) || 'Выбрать расписание'}
            </span>
            <span className="block text-xs text-theme-subtext mt-0.5 opacity-80">
              {role === 'student' ? 'Сменить группу или роль' : 'Сменить преподавателя или роль'}
            </span>
          </div>
          <ChevronRight size={18} className="text-theme-subtext opacity-50" />
        </button>

      </div>
      {editing && (
        <section className="surface rounded-3xl p-4 mb-4 border border-blue-500/30 shadow-xl" aria-label="Выбор расписания">
          <div className="flex justify-between items-center mb-3">
            <h4 className="font-bold text-sm font-unbounded text-theme-text">Выбор расписания</h4>
            <button className="icon-button" aria-label="Закрыть выбор" onClick={() => setEditing(false)}>
              <X size={18} />
            </button>
          </div>

          <div className="grid grid-cols-2 gap-2 mb-4">
            <button
              className="choice font-semibold text-xs"
              aria-pressed={role === 'student'}
              disabled={savingProfile} onClick={() => setRole('student')}
            >
              Студент
            </button>
            <button
              className="choice font-semibold text-xs"
              aria-pressed={role === 'teacher'}
              disabled={savingProfile} onClick={() => setRole('teacher')}
            >
              Преподаватель
            </button>
          </div>

          {role === 'student' ? (
            <>
              <label htmlFor="profile-course" className="block text-xs text-theme-subtext mb-1.5 font-medium">
                Курс
              </label>
              <select
                id="profile-course"
                className="field mb-3 text-xs"
                value={activeCourse}
                onChange={(event) => setCourse(event.target.value)}
              >
                {groupsData.courses.map((name) => (
                  <option key={name} value={name}>{name}</option>
                ))}
              </select>

              <div className="grid grid-cols-3 gap-2">
                {(groupsData.groupsByCourse[activeCourse] || []).map((group) => (
                  <button
                    key={group}
                    className="choice font-bold text-xs"
                    aria-pressed={selectedGroup === group}
                    disabled={savingProfile} onClick={() => choose(group)}
                  >
                    {group}
                  </button>
                ))}
              </div>
            </>
          ) : (
            <>
              <label htmlFor="profile-teacher" className="block text-xs text-theme-subtext mb-1.5 font-medium">
                Фамилия или инициалы
              </label>
              <div className="relative mb-3">
                <Search size={16} className="absolute left-3.5 top-3.5 text-theme-subtext opacity-60" />
                <input
                  id="profile-teacher"
                  className="field !pl-10 text-xs font-semibold"
                  placeholder="Найти преподавателя"
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                />
              </div>
              <div className="max-h-60 overflow-y-auto space-y-1">
                {teachers.map((teacher) => (
                  <button
                    key={teacher}
                    className="settings-row !p-2.5 text-xs rounded-xl"
                    disabled={savingProfile} onClick={() => choose(teacher)}
                  >
                    <span className="flex-1 text-left font-medium">{teacher}</span>
                    {selectedTeacher === teacher && <Check size={16} className="text-theme-accent" />}
                  </button>
                ))}
                {!teachers.length && (
                  <p className="text-xs text-theme-subtext py-4 text-center">Ничего не найдено</p>
                )}
              </div>
            </>
          )}
        </section>
      )}

      {profileError && <p role="alert" className="text-sm mb-4">{profileError}</p>}
      <NotificationSettings />
      <h3 className="section-label">Оформление</h3>
      <section className="settings-group theme-settings mb-4" aria-labelledby="interface-heading">
        <h4 id="interface-heading" className="text-base font-semibold mb-3">Интерфейс</h4>
        <div className="theme-segments" role="group" aria-label="Тема интерфейса">
          {THEMES.map((item) => (
            <button
              key={item.id}
              type="button"
              className="theme-segment"
              aria-pressed={theme === item.id}
              onClick={() => { haptic.impact('light'); setTheme(item.id); }}
            >
              {item.name}
            </button>
          ))}
        </div>
      </section>
      <h3 className="section-label">Приложение</h3>
      <div className="settings-group mb-6">
        <button className="settings-row" disabled={refreshing} onClick={() => { haptic.impact('light'); refreshAll(); }}>
          <div className="icon-badge">
            <RefreshCw size={20} className={refreshing ? 'animate-spin' : ''} />
          </div>
          <div className="flex-1 min-w-0">
            <span className="block font-semibold truncate text-sm">
              {refreshing ? 'Обновляем данные…' : 'Обновить расписание'}
            </span>
            <span className="block text-xs text-theme-subtext mt-0.5 opacity-80">
              Загрузить актуальные данные с сервера
            </span>
          </div>
          <ChevronRight size={18} className="text-theme-subtext opacity-50" />
        </button>

      </div>

      <HelpPanel />
    </div>
  );
}
