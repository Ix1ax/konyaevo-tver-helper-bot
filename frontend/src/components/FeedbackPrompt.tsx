import { useEffect, useRef, useState } from 'react';
import { X } from 'lucide-react';
import { signedRequest } from '../api/client';
import { useTelegram } from '../hooks/useTelegram';

export function FeedbackPrompt({ enabled = true }: { enabled?: boolean }) {
  const { tg } = useTelegram();
  const [open, setOpen] = useState(false);
  const [stars, setStars] = useState(0);
  const [comment, setComment] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [sent, setSent] = useState(false);
  const submitting = useRef(false);
  useEffect(() => {
    if (!enabled || !tg?.initData) return;
    let active = true;
    let timer: ReturnType<typeof setInterval> | undefined;
    let elapsed = 0;
    let last = Date.now();
    let visible = !document.hidden;
    const trackVisibility = () => { last = Date.now(); visible = !document.hidden; };
    document.addEventListener('visibilitychange', trackVisibility);
    signedRequest<{ submitted: boolean }>('/feedback/status').then(status => {
      if (!active || status.submitted) return;
      timer = setInterval(() => {
        const now = Date.now();
        if (visible) elapsed += Math.min(now - last, 5000);
        last = now;
        if (elapsed < 60000 || !visible) return;
        clearInterval(timer);
        if (active) setOpen(true);
      }, 1000);
    }).catch(() => { /* Ошибка проверки не должна мешать пользоваться расписанием. */ });
    return () => { active = false; clearInterval(timer); document.removeEventListener('visibilitychange', trackVisibility); };
  }, [tg, enabled]);
  async function submit(value: number) {
    if (submitting.current || sent || value < 1 || value > 5 || value < 5 && comment.trim().length < 3) return;
    submitting.current = true;
    setSaving(true); setError('');
    try {
      await signedRequest('/feedback', { stars: value, comment: value === 5 ? '' : comment.trim() });
      setSent(true);
    }
    catch (e) {
      if (e instanceof Error && e.message === 'Оценка уже сохранена.') setSent(true);
      else setError(e instanceof Error ? e.message : 'Не удалось отправить отзыв.');
    }
    finally { submitting.current = false; setSaving(false); }
  }
  if (!enabled || !open) return null;
  return <section role="dialog" aria-modal="false" className="surface feedback-popup feedback-compact" aria-labelledby="feedback-title" aria-busy={saving}>
    <button className="feedback-close" aria-label="Закрыть оценку" disabled={saving} onClick={() => setOpen(false)}><X size={16} /></button>
    <h2 id="feedback-title" className="feedback-heading">{sent ? 'Спасибо за оценку!' : 'Как Вам приложение?'}</h2>
    {sent ? <p role="status" className="text-center text-xs text-theme-subtext mt-2">Ваша оценка сохранена.</p> : <>
      <div className="feedback-ratings" role="group" aria-label="Оценка от 1 до 5">
        {[1,2,3,4,5].map(value => <button key={value} type="button" className="feedback-rating" aria-label={`${value} из 5`} aria-pressed={stars === value} disabled={saving}
          onClick={() => { if (submitting.current) return; setStars(value); setError(''); if (value === 5) void submit(5); }}>
          {value}
        </button>)}
      </div>
      {stars > 0 && stars < 5 && <div className="mt-4">
        <label className="block text-sm text-theme-subtext">Что не так?
          <textarea className="field mt-2" rows={3} maxLength={2000} value={comment} disabled={saving}
            onChange={e => setComment(e.target.value)} placeholder="Напишите, что было неудобно или не работало" />
        </label>
        <button type="button" className="button-primary w-full mt-3" disabled={saving || comment.trim().length < 3} onClick={() => void submit(stars)}>
          {saving ? 'Сохраняем…' : 'Отправить оценку'}
        </button>
      </div>}
      {saving && stars === 5 && <p role="status" className="text-center text-xs text-theme-subtext mt-3">Сохраняем оценку…</p>}
      {error && <p role="alert" className="text-sm mt-3 text-theme-text">{error}{stars === 5 ? ' Нажмите 5, чтобы повторить.' : ''}</p>}
    </>}
  </section>;
}
