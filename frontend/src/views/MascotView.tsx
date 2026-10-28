import { ChevronLeft, Check, Lock, Snowflake, Star } from 'lucide-react';
import { useState } from 'react';
import { useApp } from '../context/AppContext';
import { useMascot } from '../context/MascotContext';
import { useTelegram } from '../hooks/useTelegram';
import { FlameIcon, Mascot } from '../components/Mascot';
import { EmptyState } from '../components/Ui';
import { SupportPanel } from '../components/SupportParych';
import type { MascotLookId } from '../types';

const moscowWeekday = () => { const now = new Date(Date.now() + 3 * 3600000); return (now.getUTCDay() + 6) % 7; };

export function MascotView() {
  const { setActiveTab } = useApp();
  const { mascot, updateSettings } = useMascot();
  const { haptic } = useTelegram();
  const [error, setError] = useState('');
  const back = <button className="icon-button" aria-label="Назад" onClick={() => setActiveTab('today')}><ChevronLeft /></button>;

  if (!mascot) return <div className="page animate-fade-in">
    <div className="flex items-center gap-2 mb-2">{back}<h1 className="font-unbounded text-base font-bold">Парыч</h1></div>
    <EmptyState mascot title="Парыч живёт в Telegram">Откройте приложение через Telegram, чтобы копить огонёк и открывать облики.</EmptyState>
  </div>;

  const save = (settings: { look?: MascotLookId; reminder?: boolean }) => {
    setError('');
    haptic.selection();
    updateSettings(settings).catch(e => setError(e instanceof Error ? e.message : 'Не удалось сохранить.'));
  };
  const progress = mascot.nextLevelXp > mascot.levelXp
    ? Math.min(100, Math.round((mascot.xp - mascot.levelXp) / (mascot.nextLevelXp - mascot.levelXp) * 100)) : 100;
  const today = moscowWeekday();
  const status = mascot.countedToday ? 'Сегодня засчитан — так держать!'
    : mascot.studyDay ? 'Открой расписание сегодня, чтобы огонёк не погас'
    : 'Сегодня пар нет — огонёк не гаснет';

  return <div className="page mascot-page animate-fade-in">
    <div className="flex items-center justify-between mb-1">{back}<h1 className="font-unbounded text-base font-bold">Парыч</h1><span className="w-10" /></div>

    <section className="mascot-hero">
      <Mascot size={176} look={mascot.look} pose="wave" mood={mascot.streak ? 'proud' : 'think'} extra={mascot.streak ? ['flame'] : ['question']} />
      <p className="mascot-streak font-unbounded"><FlameIcon size={36} />{mascot.streak}</p>
      <p className="text-sm font-semibold text-theme-subtext">дней подряд с расписанием</p>
      <p className="mascot-status">{status}</p>
    </section>

    <section className="surface mascot-card">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="font-unbounded text-sm font-bold">Уровень {mascot.level} · {mascot.levelName}</p>
          <p className="text-xs text-theme-subtext mt-1">{mascot.level < 10 ? `До следующего уровня ${mascot.nextLevelXp - mascot.xp} XP` : 'Максимальный уровень'}</p>
        </div>
        <p className="font-unbounded text-sm font-bold mascot-xp">{mascot.xp} XP</p>
      </div>
      <div className="mascot-bar" role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}><i style={{ width: `${progress}%` }} /></div>
      <div className="mascot-week">{mascot.week.map((day, index) => <div key={day.label} data-state={day.state} data-today={index === today || undefined}>
        <span>{day.state === 'done' ? <FlameIcon size={index === today ? 20 : 16} /> : day.state === 'freeze' ? <Snowflake size={15} /> : day.state === 'off' ? '–' : ''}</span>
        {day.label}
      </div>)}</div>
      <p className="mascot-freezes"><Snowflake size={14} />Заморозки: {mascot.freezes} из {mascot.maxFreezes}. Закрывают пропущенный учебный день, +1 за каждые 7 дней подряд.</p>
    </section>

    <p className="section-label">Задания на сегодня</p>
    <div className="settings-group">{mascot.tasks.map(task => <div key={task.id} className="mascot-task">
      <span className="mascot-check" data-done={task.done || undefined}>{task.done && <Check size={15} strokeWidth={3} />}</span>
      <div className="min-w-0"><p className="text-sm font-semibold">{task.title}</p>{task.hint && <p className="text-xs text-theme-subtext">{task.hint}</p>}</div>
      <span className="mascot-task-xp font-unbounded">+{task.xp} XP</span>
    </div>)}</div>

    <p className="section-label">Облики</p>
    <div className="mascot-looks">{mascot.looks.map(look => <button key={look.id} className="mascot-look surface"
      data-support={look.stars > 0 || undefined} aria-disabled={!look.unlocked || undefined}
      aria-pressed={mascot.look === look.id} onClick={() => {
        // Закрытый облик поддержки ведёт к блоку «Поддержать», остальные закрытые не нажимаются.
        if (!look.unlocked) { if (look.stars > 0) document.getElementById('support')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); return; }
        if (mascot.look !== look.id) save({ look: look.id });
      }}>
      <Mascot size={70} look={look.id} pose="wave" />
      {!look.unlocked && <span className="mascot-lock">{look.stars > 0 ? <Star size={13} fill="currentColor" /> : <Lock size={13} />}</span>}
      <span>{look.unlocked ? look.name : look.stars > 1 ? `${look.stars} ⭐` : look.stars ? 'Поддержка' : `Ур. ${look.level}`}</span>
    </button>)}</div>

    <p className="section-label" id="support">Поддержать Парыча</p>
    <section className="surface mascot-card support-section"><SupportPanel /></section>

    <p className="section-label">Напоминание</p>
    <div className="settings-group">
      <label className="settings-row cursor-pointer">
        <div className="min-w-0 flex-1"><p className="text-sm font-semibold">Напомнить, если огонёк гаснет</p>
          <p className="text-xs text-theme-subtext mt-0.5">Парыч напишет в боте в 21:00, только в день, когда серия может прерваться</p></div>
        <input type="checkbox" className="mascot-switch" checked={mascot.reminder} onChange={event => save({ reminder: event.target.checked })} />
      </label>
    </div>
    {error && <p role="alert" className="notice error-notice mt-3">{error}</p>}
    <p className="text-xs text-theme-subtext leading-relaxed mt-4 px-1">Огонёк считает учебные дни, в которые ты открыл расписание в приложении или в боте. Выходные, дни без пар и каникулы его не гасят. Лучший результат: {mascot.best}.</p>
  </div>;
}
