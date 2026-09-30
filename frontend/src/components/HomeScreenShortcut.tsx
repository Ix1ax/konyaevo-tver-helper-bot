import { useEffect, useRef, useState } from 'react';
import { Smartphone, ChevronRight } from 'lucide-react';
import { useTelegram } from '../hooks/useTelegram';

type ShortcutStatus = 'unsupported' | 'unknown' | 'added' | 'missed';

export function HomeScreenShortcut() {
  const { tg } = useTelegram();
  const [status, setStatus] = useState<ShortcutStatus>('unsupported');
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);
  const timer = useRef<ReturnType<typeof setTimeout>>();
  const mounted = useRef(false);
  useEffect(() => {
    if (!tg?.isVersionAtLeast?.('8.0') || !tg?.checkHomeScreenStatus || !tg?.addToHomeScreen) return;
    let active = true;
    mounted.current = true;
    const added = () => {
      if (!active) return;
      clearTimeout(timer.current);
      setStatus('added');
      setPending(false);
      setError('Ярлык добавлен на главный экран.');
    };
    try {
      tg.checkHomeScreenStatus((value: ShortcutStatus) => { if (active) setStatus(value); });
      tg.onEvent?.('homeScreenAdded', added);
    } catch { /* Старый клиент может не поддерживать запрос ярлыка. */ }
    return () => { active = false; mounted.current = false; clearTimeout(timer.current); tg.offEvent?.('homeScreenAdded', added); };
  }, [tg]);
  if (status === 'unsupported' && !error) return null;
  return <section className="mb-6">
    <h3 className="section-label">Быстрый доступ</h3>
    <div className="settings-group">
      <button className="settings-row w-full text-left" disabled={status === 'added' || pending} onClick={() => {
        setError('');
        setPending(true);
        setError('Подтвердите добавление в окне Telegram, если оно появится.');
        try {
          tg.addToHomeScreen();
          timer.current = setTimeout(() => {
            if (!mounted.current) return;
            setPending(false);
            setError('Telegram не подтвердил добавление. Проверьте главный экран телефона. Если ярлыка нет, обновите Telegram и проверьте разрешение на создание ярлыков в настройках телефона для Telegram.');
            try {
              tg.checkHomeScreenStatus((value: ShortcutStatus) => {
                if (!mounted.current) return;
                setStatus(value);
                if (value === 'added') setError('Ярлык уже добавлен на главный экран.');
                if (value === 'unsupported') setError('Этот клиент Telegram не поддерживает добавление ярлыка.');
              });
            } catch { /* Сообщение выше остаётся доступным без ответа клиента. */ }
          }, 8000);
        } catch {
          setPending(false);
          setError('Не удалось запросить ярлык. Попробуйте обновить Telegram.');
        }
      }}>
        <div className="icon-badge"><Smartphone size={20} /></div>
        <div className="flex-1 min-w-0"><span className="block text-sm font-semibold">
          {pending ? 'Ожидаем ответ Telegram…' : status === 'added' ? 'Ярлык уже добавлен' : 'Добавить на главный экран'}
        </span><span className="block text-xs text-theme-subtext mt-1">Открывайте расписание с экрана телефона</span></div>
        <ChevronRight size={18} className="text-theme-subtext" />
      </button>
    </div>
    {error && <p role="status" className="notice mt-2">{error}</p>}
  </section>;
}
