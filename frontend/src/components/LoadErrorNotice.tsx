import { useState } from 'react';
import { CloudOff, MessageCircle, RefreshCw, WifiOff } from 'lucide-react';
import { useApp } from '../context/AppContext';
import { useTelegram } from '../hooks/useTelegram';

const BOT_URL = 'https://t.me/konyaevo_tver_helper_bot';

/** Ошибка загрузки с выходом: повторить запрос или посмотреть расписание в чате, где бот работает через Telegram. */
export function LoadErrorNotice({ message }: { message: string }) {
  const { refreshAll } = useApp();
  const { tg } = useTelegram();
  const [retrying, setRetrying] = useState(false);
  const retry = async () => {
    setRetrying(true);
    try { await refreshAll(); } finally { setRetrying(false); }
  };
  const openChat = () => {
    if (tg?.initData) tg.close();
    else window.open(BOT_URL, '_blank', 'noopener');
  };
  return <div className="load-error surface" role="alert">
    <span className="icon-badge"><WifiOff aria-hidden="true" /></span>
    <h3>Расписание не загрузилось</h3>
    <p>{message}</p>
    <div className="load-error-actions">
      <button className="button-primary" onClick={retry} disabled={retrying}>
        <RefreshCw size={16} className={retrying ? 'animate-spin' : ''} aria-hidden="true" />{retrying ? 'Пробуем…' : 'Повторить'}
      </button>
      <button className="button-quiet" onClick={openChat}><MessageCircle size={16} aria-hidden="true" />Расписание в чате</button>
    </div>
  </div>;
}

const copyTime = (iso: string) => {
  const date = new Date(iso);
  const day = (d: Date) => d.toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow' });
  const time = date.toLocaleTimeString('ru-RU', { timeZone: 'Europe/Moscow', hour: '2-digit', minute: '2-digit' });
  return day(date) === day(new Date()) ? time : `${date.toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow', day: 'numeric', month: 'long' })}, ${time}`;
};

/** Сервер недоступен, но расписание показано из копии на GitHub Pages. */
export function OfflineCopyNotice({ at }: { at: string }) {
  const { refreshAll, refreshing } = useApp();
  return <div className="offline-copy" role="status">
    <CloudOff size={18} aria-hidden="true" />
    <p>Сервер недоступен — показана копия расписания от <b>{copyTime(at)}</b>. Свежие замены могут не попасть.</p>
    <button onClick={refreshAll} disabled={refreshing} aria-label="Обновить"><RefreshCw size={16} className={refreshing ? 'animate-spin' : ''} /></button>
  </div>;
}
