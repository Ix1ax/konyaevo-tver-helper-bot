import { useEffect, useState } from 'react';
import { ChevronRight, Smartphone } from 'lucide-react';
import { useTelegram } from '../hooks/useTelegram';
import { Mascot } from './Mascot';
import { useMascot } from '../context/MascotContext';

type Status = 'unsupported' | 'unknown' | 'added' | 'missed';
const OFFER_KEY = 'konyaevo_home_offer_closed';

/**
 * Ярлык Mini App на главном экране телефона (Telegram 8.0+): addToHomeScreen и проверка,
 * не добавлен ли он уже. В старых клиентах и в браузере функция недоступна.
 */
export function useHomeScreen() {
  const tg = window.Telegram?.WebApp;
  const supported = typeof tg?.addToHomeScreen === 'function' && tg?.isVersionAtLeast?.('8.0');
  const [status, setStatus] = useState<Status>(supported ? 'unknown' : 'unsupported');
  useEffect(() => {
    if (!supported) return;
    const added = () => setStatus('added');
    const checked = ({ status: value }: { status?: Status }) => { if (value) setStatus(value); };
    tg.onEvent?.('homeScreenAdded', added);
    tg.onEvent?.('homeScreenChecked', checked);
    try { tg.checkHomeScreenStatus?.((value: Status) => setStatus(value)); } catch { /* Статус останется неизвестным. */ }
    return () => { tg.offEvent?.('homeScreenAdded', added); tg.offEvent?.('homeScreenChecked', checked); };
  }, [supported]);
  return { status, add: () => { try { tg?.addToHomeScreen?.(); } catch { /* Клиент не поддерживает ярлыки. */ } } };
}

/** Парыч один раз предлагает поставить приложение на главный экран. */
export function HomeScreenOffer() {
  const { status, add } = useHomeScreen();
  const { look } = useMascot();
  const { haptic } = useTelegram();
  const [closed, setClosed] = useState(() => { try { return localStorage.getItem(OFFER_KEY) === '1'; } catch { return false; } });
  if (closed || (status !== 'missed' && status !== 'unknown')) return null;
  const close = () => { setClosed(true); try { localStorage.setItem(OFFER_KEY, '1'); } catch { /* Покажется ещё раз — не страшно. */ } };
  return <section className="home-offer surface">
    <Mascot size={72} look={look} pose="wave" mood="happy" />
    <div className="min-w-0">
      <p className="home-offer-title">Поставь меня на главный экран</p>
      <p className="home-offer-text">Расписание в одно касание — как обычное приложение.</p>
      <div className="flex gap-2 mt-2.5">
        <button type="button" className="home-offer-add" onClick={() => { haptic.impact('light'); add(); close(); }}>Добавить</button>
        <button type="button" className="home-offer-later" onClick={close}>Не сейчас</button>
      </div>
    </div>
  </section>;
}

/** Строка в профиле: пока ярлыка нет. */
export function HomeScreenRow() {
  const { status, add } = useHomeScreen();
  const { haptic } = useTelegram();
  if (status === 'unsupported' || status === 'added') return null;
  return <button className="settings-row" onClick={() => { haptic.impact('light'); add(); }}>
    <div className="icon-badge"><Smartphone size={20} /></div>
    <div className="flex-1 min-w-0">
      <span className="block font-semibold truncate text-sm">Добавить на главный экран</span>
      <span className="block text-xs text-theme-subtext mt-0.5 opacity-80">Открывать расписание иконкой, как приложение</span>
    </div>
    <ChevronRight size={18} className="text-theme-subtext opacity-50" />
  </button>;
}
