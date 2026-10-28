import { useEffect, useState } from 'react';
import { createPortal } from 'react-dom';
import { Star, X } from 'lucide-react';
import { signedRequest } from '../api/client';
import { useMascot } from '../context/MascotContext';
import { useTelegram } from '../hooks/useTelegram';
import { Mascot } from './Mascot';

/** Суммы те же, что принимает сервер (DonationService.AMOUNTS). */
const AMOUNTS = [25, 50, 100, 250] as const;
const MIN = 1, MAX = 10000, CROWN = 250;
const DONATE_LINK = 'https://t.me/konyaevo_tver_helper_bot?start=donate';
const HIDE_KEY = 'konyaevo_support_hidden_until';

/** Поддержка звёздами: выбор суммы, счёт Telegram и благодарность. */
export function SupportPanel({ onDone }: { onDone?: () => void }) {
  const { mascot, refresh } = useMascot();
  const { haptic } = useTelegram();
  const [preset, setPreset] = useState<number | 'custom'>(50);
  const [custom, setCustom] = useState('');
  const customStars = Number(custom);
  const customValid = Number.isInteger(customStars) && customStars >= MIN && customStars <= MAX;
  const stars = preset === 'custom' ? customStars : preset;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [paid, setPaid] = useState(false);
  const donated = mascot?.donated ?? 0;
  const tg = window.Telegram?.WebApp;

  const pay = async () => {
    if (busy) return;
    setError('');
    if (preset === 'custom' && !customValid) { setError(`Введите целое число от ${MIN} до ${MAX.toLocaleString('ru-RU')}.`); return; }
    haptic.impact('medium');
    // Вне Telegram или в старом клиенте счёт откроется в чате бота.
    if (!tg?.initData || typeof tg.openInvoice !== 'function') {
      if (typeof tg?.openTelegramLink === 'function') tg.openTelegramLink(DONATE_LINK); else window.open(DONATE_LINK, '_blank');
      return;
    }
    setBusy(true);
    try {
      const { url } = await signedRequest<{ url: string }>('/donate', { stars });
      tg.openInvoice(url, (status: string) => {
        setBusy(false);
        if (status === 'paid') {
          haptic.notification('success');
          setPaid(true);
          // Облик открывает бот, когда Telegram пришлёт подтверждение платежа: перечитываем несколько раз.
          [1500, 4000, 9000].forEach(delay => setTimeout(() => { refresh().catch(() => {}); }, delay));
          onDone?.();
        } else if (status === 'failed') setError('Оплата не прошла. Попробуйте ещё раз.');
      });
    } catch (e) {
      setBusy(false);
      setError(e instanceof Error ? e.message : 'Не удалось создать счёт. Попробуйте ещё раз.');
    }
  };

  return <div className="support-panel">
    <div className="support-looks">
      <figure data-open={donated > 0 || undefined}><Mascot size={84} look="star" pose="wave" mood="happy" /><figcaption>Звёздный<small>за любую поддержку</small></figcaption></figure>
      <figure data-open={donated >= CROWN || undefined}><Mascot size={84} look="crown" pose="wave" mood="proud" /><figcaption>Корона<small>от {CROWN} ⭐ всего</small></figcaption></figure>
    </div>
    {paid ? <p className="support-thanks">💛 Спасибо! Парыч уже примеряет новый облик.</p>
      : donated > 0 && <p className="support-thanks">💛 Ты поддержал на {donated} ⭐{donated < CROWN ? ` — до короны ещё ${CROWN - donated} ⭐` : ' — корона твоя'}</p>}
    <div className="support-amounts" role="radiogroup" aria-label="Сколько звёзд">
      {AMOUNTS.map(value => <button key={value} type="button" role="radio" aria-checked={preset === value}
        onClick={() => { haptic.selection(); setPreset(value); setError(''); }}><Star size={15} fill="currentColor" />{value}</button>)}
      <button type="button" role="radio" aria-checked={preset === 'custom'} className="support-custom-chip"
        onClick={() => { haptic.selection(); setPreset('custom'); setError(''); }}>Своя</button>
    </div>
    {preset === 'custom' && <label className="support-custom">
      <Star size={18} fill="currentColor" />
      <input type="number" inputMode="numeric" min={MIN} max={MAX} step={1} autoFocus placeholder={`Сколько звёзд, до ${MAX.toLocaleString('ru-RU')}`}
        value={custom} onChange={event => setCustom(event.target.value.replace(/\D/g, '').slice(0, 5))} aria-label="Своя сумма в звёздах" />
    </label>}
    <button type="button" className="support-pay" disabled={busy || (preset === 'custom' && !customValid)} onClick={pay}>
      {busy ? 'Открываем оплату…' : preset === 'custom' && !customValid ? 'Введите сумму'
        : <>Поддержать · {stars.toLocaleString('ru-RU')} <Star size={18} fill="currentColor" /></>}
    </button>
    {error && <p role="alert" className="notice error-notice mt-3">{error}</p>}
    <p className="support-note">Звёзды Telegram, без карты. Бот бесплатный и без рекламы — поддержка добровольная.</p>
  </div>;
}

/** Окно поддержки поверх экрана. */
function SupportSheet({ onClose }: { onClose: () => void }) {
  useEffect(() => {
    const close = (event: KeyboardEvent) => { if (event.key === 'Escape') onClose(); };
    document.addEventListener('keydown', close);
    return () => document.removeEventListener('keydown', close);
  }, [onClose]);
  return createPortal(<div className="share-overlay" onClick={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section role="dialog" aria-modal="true" aria-labelledby="support-title" className="share-dialog surface">
      <div className="flex items-center justify-between gap-3 mb-2"><h2 id="support-title" className="font-semibold">Поддержать Парыча</h2>
        <button type="button" className="icon-button" onClick={onClose} aria-label="Закрыть"><X /></button></div>
      {/* После оплаты окно закрывается: под ним откроется «Спасибо!» с новым обликом. */}
      <SupportPanel onDone={onClose} />
    </section>
  </div>, document.body);
}

/** Плашка «Поддержать» в шапке главного экрана: видна всегда, у поддержавших — с сердцем. */
export function SupportButton() {
  const { mascot } = useMascot();
  const { haptic } = useTelegram();
  const [open, setOpen] = useState(false);
  const supporter = (mascot?.donated ?? 0) > 0;
  return <>
    <button type="button" className="support-pill" data-supporter={supporter || undefined}
      onClick={() => { haptic.impact('light'); setOpen(true); }} aria-label="Поддержать Парыча звёздами">
      <Star size={15} fill="currentColor" />{supporter ? 'Спасибо' : 'Поддержать'}
    </button>
    {open && <SupportSheet onClose={() => setOpen(false)} />}
  </>;
}

/** Карточка внизу главной: зовёт поддержать, открывает окно с выбором суммы. Поддержавшим не показывается. */
export function SupportCard() {
  const { mascot } = useMascot();
  const [open, setOpen] = useState(false);
  const [hidden, setHidden] = useState(() => {
    try { return Number(localStorage.getItem(HIDE_KEY) || 0) > Date.now(); } catch { return false; }
  });
  if (hidden || (mascot?.donated ?? 0) > 0) return null;
  const hide = () => {
    setHidden(true);
    try { localStorage.setItem(HIDE_KEY, String(Date.now() + 14 * 86400000)); } catch { /* Скроется до перезагрузки. */ }
  };
  return <>
    <section className="support-card">
      <button type="button" className="support-card-close" aria-label="Скрыть на две недели" onClick={hide}><X size={16} /></button>
      <Mascot size={78} look="star" pose="wave" mood="happy" />
      <div className="min-w-0">
        <p className="support-card-title font-unbounded">Поддержи Парыча</p>
        <p className="support-card-text">Бот без рекламы. За любую поддержку звёздами — Звёздный Парыч.</p>
        <button type="button" className="support-card-button" onClick={() => setOpen(true)}><Star size={15} fill="currentColor" />Поддержать</button>
      </div>
    </section>
    {open && <SupportSheet onClose={() => setOpen(false)} />}
  </>;
}
