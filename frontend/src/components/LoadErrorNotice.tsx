import { useState } from 'react';
import { MessageCircle, RefreshCw, WifiOff } from 'lucide-react';
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
