import { useEffect, useState } from 'react';
import { Bell, Moon, CircleAlert, CircleCheck, LoaderCircle } from 'lucide-react';
import { profileRequest, SavedProfile } from '../api/client';
import { TimePicker } from './TimePicker';
import { useApp } from '../context/AppContext';

const notificationKey = (profile: SavedProfile) => JSON.stringify([profile.changes, profile.tomorrow, profile.time, [...profile.days].sort((a,b)=>a-b)]);

export function NotificationSettings() {
  const { role, selectedGroup, selectedTeacher } = useApp();
  const [settings, setSettings] = useState<SavedProfile>();
  const [error, setError] = useState('');
  const [baseline, setBaseline] = useState('');
  const dirty = Boolean(settings && baseline && notificationKey(settings) !== baseline);
  const [saved, setSaved] = useState(false);
  const [saving, setSaving] = useState(false);
  useEffect(() => {
    let active = true;
    profileRequest().then(data => { if (active) { setSettings(data); setBaseline(notificationKey(data)); } })
      .catch(e => { if (active) setError(e.message); });
    return () => { active = false; };
  }, []);
  async function save() {
    if (!settings) return;
    setSaving(true); setError(''); setSaved(false);
    try { const data = await profileRequest({ changes: settings.changes, tomorrow: settings.tomorrow, time: settings.time, days: settings.days, role, ...(role === 'student' ? { group: selectedGroup } : { teacher: selectedTeacher }) }); setSettings(data); setBaseline(notificationKey(data)); setSaved(true); }
    catch (e) { setError(e instanceof Error ? e.message : 'Не удалось сохранить настройки.'); }
    finally { setSaving(false); }
  }
  return <>
    <h3 className="section-label">Уведомления</h3>
    <div className="settings-group mb-4">
      {settings && <>
        {([{ key: 'changes', text: 'Изменения расписания', description: 'Ежедневная сводка замен', icon: Bell }, { key: 'tomorrow', text: 'Завтрашнее расписание', description: 'Напоминание о парах', icon: Moon }] as const).map(item => <button
          key={item.key} className="settings-row" type="button" disabled={saving}
          role="switch" aria-checked={settings[item.key]}
          onClick={() => { setSaved(false); setSettings({ ...settings, [item.key]: !settings[item.key] }); }}>
          <div className="icon-badge"><item.icon size={20} /></div>
          <span className="flex-1 min-w-0 text-left">
            <span className="block text-sm font-semibold leading-snug">{item.text}</span>
            <span className="block text-xs text-theme-subtext mt-1 leading-snug">{item.description}</span>
          </span>
          <span className={`text-xs font-semibold shrink-0 ${settings[item.key] ? 'text-theme-accent' : 'text-theme-subtext'}`}>{settings[item.key] ? 'ВКЛ' : 'ВЫКЛ'}</span>
        </button>)}
        <div className="p-4 space-y-3">
          <div className="block text-xs text-theme-subtext">Время отправки · Москва
            <TimePicker value={settings.time} disabled={saving} onChange={time => { setSaved(false); setSettings({ ...settings, time }); }} />
          </div>
          <div className="flex gap-1 flex-wrap" aria-label="Дни отправки">
            {['Пн','Вт','Ср','Чт','Пт','Сб','Вс'].map((day, i) => <button type="button" key={day}
              className="choice text-xs" disabled={saving} aria-pressed={settings.days.includes(i + 1)}
              onClick={() => { setSaved(false); setSettings({ ...settings, days: settings.days.includes(i + 1) ? settings.days.filter(d => d !== i + 1) : [...settings.days, i + 1] }); }}>{day}</button>)}
          </div>
          {!settings.days.length && <p className="text-xs text-theme-subtext">Дни не выбраны: уведомления не будут приходить.</p>}
          <p className="text-xs text-theme-subtext">Бот отправит сообщения для выбранного в профиле расписания. Изменения вступят в силу после сохранения.</p>
          {(dirty || saved || saving) && <div role="status" aria-live="polite" className={`notification-status ${saving ? 'is-saving' : dirty ? 'is-dirty' : 'is-saved'}`}>
            {saving ? <LoaderCircle size={20} className="animate-spin" aria-hidden="true" /> : dirty ? <CircleAlert size={20} aria-hidden="true" /> : <CircleCheck size={20} aria-hidden="true" />}
            <div><p className="text-sm font-semibold">{saving ? 'Сохраняем настройки' : dirty ? 'Есть несохранённые изменения' : 'Настройки сохранены'}</p>
              <p className="text-xs text-theme-subtext mt-1">{saving ? 'Подождите немного' : dirty ? 'Нажмите «Сохранить уведомления», чтобы применить их.' : 'Уведомления будут приходить по этим настройкам.'}</p></div>
          </div>}
          <button type="button" className="button-primary w-full" disabled={saving || !settings.time} onClick={save}>{saving ? 'Сохраняем…' : 'Сохранить уведомления'}</button>
        </div>
      </>}
      {error && <p role="alert" className="p-4 text-sm text-theme-subtext">{error}</p>}
    </div>
  </>;
}
