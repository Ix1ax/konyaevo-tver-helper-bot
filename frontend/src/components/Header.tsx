import React from 'react';
import { RefreshCw, CalendarDays } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { useTelegram } from '../hooks/useTelegram';

export function Header({ title, showGreeting = true, action }: { title?: string; showGreeting?: boolean; action?: React.ReactNode }) {
  const { role, selectedGroup, selectedTeacher, info, refreshing, refreshAll, setActiveTab } = useApp();
  const { user, haptic } = useTelegram();
  const name = (role === 'student' ? selectedGroup : selectedTeacher) || 'Выбрать';
  const initial = user?.first_name ? user.first_name.charAt(0).toUpperCase() : (name.charAt(0) || 'К');

  return <HeaderContent title={title} showGreeting={showGreeting} action={action} name={name} initial={initial}
    photoUrl={user?.photo_url} red={info.isRedWeek} refreshing={refreshing}
    onProfile={() => { haptic.impact('light'); setActiveTab('profile'); }}
    onWeek={() => { haptic.impact('light'); setActiveTab('week'); }}
    onRefresh={() => { haptic.impact('light'); refreshAll(); }} />;
}

export function HeaderContent({title, showGreeting = true, action, name, initial = 'К', photoUrl, red = false, refreshing = false, hideAvatar = false, subtitle, identityLabel,
  onProfile = () => {}, onWeek = () => {}, onRefresh = () => {}}: {
  title?: string; showGreeting?: boolean; action?: React.ReactNode; name: string; initial?: string; photoUrl?: string;
  identityLabel?: string; hideAvatar?: boolean; subtitle?: string; red?: boolean; refreshing?: boolean; onProfile?: () => void; onWeek?: () => void; onRefresh?: () => void;
}) {
  const [avatarError, setAvatarError] = React.useState(false);
  const getGreeting = () => {
    const hour = (new Date().getUTCHours() + 3) % 24;
    if (hour >= 5 && hour < 12) return 'Доброе утро';
    if (hour >= 12 && hour < 18) return 'Добрый день';
    if (hour >= 18 && hour < 23) return 'Добрый вечер';
    return 'Доброй ночи';
  };

  return (
    <header className="mb-5 select-none">
      {/* Top brand row with avatar */}
      <div className="flex items-center justify-between gap-3 mb-4">
        <div className="cursor-pointer" onClick={onProfile}>
          <h1 className="text-xl font-black font-unbounded tracking-tight text-theme-text uppercase leading-none">
            КОНЯЕВО
          </h1>
          {!identityLabel && <p className="text-xs text-theme-subtext font-semibold mt-1 truncate max-w-[200px]">
            {name}
          </p>}
        </div>

        {identityLabel && <div className="schedule-identity">
          <p className="schedule-identity-label">{identityLabel}</p>
          <p className="schedule-identity-name">{name}</p>
        </div>}

        {/* Circular Avatar */}
        {!hideAvatar && <button
          onClick={onProfile}
          className="w-10 h-10 rounded-full bg-blue-500 text-white font-unbounded font-black text-sm flex items-center justify-center shadow-lg shadow-blue-500/25 active:scale-95 transition overflow-hidden"
          title="Профиль"
        >
          {photoUrl && !avatarError ? (
            <img
              src={photoUrl}
              alt="Avatar"
              className="w-full h-full object-cover"
              onError={() => setAvatarError(true)}
            />
          ) : (
            <span>{initial}</span>
          )}
        </button>}
      </div>

      {/* Main Title Row with Square Action Buttons */}
      <div className="flex items-end justify-between gap-3">
        <div>
          <h2 className="text-3xl font-black font-unbounded tracking-tight text-theme-text leading-tight">
            {title}
          </h2>
          {subtitle ? <p className="text-sm font-semibold text-theme-subtext mt-0.5">{subtitle}</p> : showGreeting ? (
            <p className="text-sm font-semibold text-theme-subtext mt-0.5">
              {getGreeting()}
            </p>
          ) : (
            <p className="text-xs font-semibold text-theme-subtext mt-0.5 flex items-center gap-1.5">
              <span className={`w-2 h-2 rounded-full ${red ? 'bg-rose-500 shadow-[0_0_8px_rgba(244,63,94,0.6)]' : 'bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.6)]'}`} />
              <span>{red ? 'Красная неделя' : 'Синяя неделя'}</span>
            </p>
          )}
        </div>

        {/* Action Buttons: Week switcher / Refresh */}
        <div className="flex items-center gap-2">
          {action}
          <button
            onClick={onWeek}
            className="w-10 h-10 rounded-2xl bg-white/[0.06] border border-white/10 flex items-center justify-center text-theme-text active:scale-95 transition shadow-sm hover:bg-white/10"
            title="Расписание на неделю"
          >
            <CalendarDays size={18} className="opacity-80" />
          </button>

          <button
            onClick={onRefresh}
            disabled={refreshing}
            className="w-10 h-10 rounded-2xl bg-white/[0.06] border border-white/10 flex items-center justify-center text-theme-text active:scale-95 transition shadow-sm hover:bg-white/10 disabled:opacity-50"
            title="Обновить данные"
          >
            <RefreshCw size={17} className={`opacity-80 ${refreshing ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>
    </header>
  );
}
