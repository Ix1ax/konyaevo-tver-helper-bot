import { useEffect, useState } from 'react';
import { Smartphone, ChevronRight } from 'lucide-react';
import { useTelegram } from '../hooks/useTelegram';

type ShortcutStatus = 'unsupported' | 'unknown' | 'added' | 'missed';

export function HomeScreenShortcut() {
  const { tg } = useTelegram();
  const [status, setStatus] = useState<ShortcutStatus>('unsupported');
  const [error, setError] = useState('');
  useEffect(() => {
    if (!tg?.isVersionAtLeast?.('8.0') || !tg?.checkHomeScreenStatus || !tg?.addToHomeScreen) return;
    let active = true;
    const added = () => { if (active) setStatus('added'); };
    try {
      tg.checkHomeScreenStatus((value: ShortcutStatus) => { if (active) setStatus(value); });
      tg.onEvent?.('homeScreenAdded', added);
    } catch { /* Старый клиент может не поддерживать запрос ярлыка. */ }
    return () => { active = false; tg.offEvent?.('homeScreenAdded', added); };
  }, [tg]);
  if (status === 'unsupported') return null;
  return <section className="mb-6">
    <h3 className="section-label">Быстрый доступ</h3>
    <div className="settings-group">
      <button className="settings-row w-full text-left" disabled={status === 'added'} onClick={() => {
        setError('');
        try { tg.addToHomeScreen(); }
        catch { setError('Не удалось создать ярлык. Попробуйте обновить Telegram.'); }
      }}>
        <div className="icon-badge"><Smartphone size={20} /></div>
        <div className="flex-1 min-w-0"><span className="block text-sm font-semibold">
          {status === 'added' ? 'Ярлык уже добавлен' : 'Добавить на главный экран'}
        </span><span className="block text-xs text-theme-subtext mt-1">Открывайте расписание с экрана телефона</span></div>
        <ChevronRight size={18} className="text-theme-subtext" />
      </button>
    </div>
    {error && <p role="alert" className="notice mt-2">{error}</p>}
  </section>;
}
