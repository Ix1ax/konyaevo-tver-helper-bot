import { useEffect, useMemo, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Share2, Download, X } from 'lucide-react';
import { LoadingIndicator } from './Ui';
import { signedRequest } from '../api/client';
import { scheduleCaption, renderScheduleImages, type ScheduleImageOptions } from '../utils/scheduleImage';

export function ShareSchedule({ options: baseOptions, disabled = false, fullWidth = false, choices }: { options: ScheduleImageOptions; disabled?: boolean; fullWidth?: boolean; choices?: {label: string; options: ScheduleImageOptions}[] }) {
  const [choice, setChoice] = useState(0);
  // Parent renders recreate option objects; only changed export data should regenerate the image.
  const optionsKey = JSON.stringify(choices?.[choice]?.options || baseOptions);
  const options = useMemo<ScheduleImageOptions>(() => JSON.parse(optionsKey), [optionsKey]);
  const [open, setOpen] = useState(false);
  const [image, setImage] = useState<{ url: string; file: File; upload: Blob; canShare: boolean } | null>(null);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [prepared, setPrepared] = useState<{ id: string; imageUrl: string } | null>(null);
  const tg = window.Telegram?.WebApp;
  const telegramShare = Boolean(tg?.initData && tg?.isVersionAtLeast?.('8.0') && typeof tg?.shareMessage === 'function');
  const trigger = useRef<HTMLButtonElement>(null);
  const close = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    if (!open) return;
    let active = true;
    let url = '';
    setImage(null); setPrepared(null); setError('');
    renderScheduleImages(options, telegramShare).then(({png:blob, jpeg:upload}) => {
      if (!active) return;
      url = URL.createObjectURL(blob);
      const file = new File([blob], 'konyaevo-schedule.png', { type: 'image/png' });
      let canShare = false;
      try { canShare = Boolean(typeof navigator.share === 'function' && navigator.canShare?.({ files: [file] })); } catch { /* Download remains available. */ }
      if (active) setImage({ url, file, upload, canShare });
    }).catch(() => { if (active) setError('Не удалось подготовить картинку. Закройте окно и попробуйте ещё раз.'); });
    return () => { active = false; if (url) URL.revokeObjectURL(url); };
  }, [open, options, telegramShare]);
  useEffect(() => {
    if (!open) return;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    close.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false);
      if (event.key !== 'Tab') return;
      const dialog = close.current?.closest('[role="dialog"]');
      const buttons = Array.from(dialog?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') || []);
      const first = buttons[0], last = buttons[buttons.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => { document.body.style.overflow = overflow; document.removeEventListener('keydown', onKey); trigger.current?.focus(); };
  }, [open]);
  const prepare = async () => {
    if (prepared) return prepared;
    if (!image || image.upload.size > 650000) throw new Error('Картинка слишком большая');
    const jpeg = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = () => resolve(String(reader.result).split(',')[1]);
      reader.onerror = () => reject(new Error('Не удалось прочитать картинку'));
      reader.readAsDataURL(image.upload);
    });
    const message = await signedRequest<{ id: string; imageUrl: string }>('/share/prepare', { jpeg, caption: scheduleCaption(options) });
    setPrepared(message);
    return message;
  };
  const download = async () => {
    if (!image || busy) return;
    if (telegramShare && typeof tg.downloadFile === 'function') {
      setBusy(true); setError('');
      try { const message = await prepare(); tg.downloadFile({ url: message.imageUrl, file_name: 'konyaevo-schedule.jpg' }); }
      catch (error) { setError(error instanceof Error ? error.message : 'Не удалось сохранить через Telegram. Попробуйте ещё раз.'); }
      finally { setBusy(false); }
      return;
    }
    const a = document.createElement('a'); a.href = image.url; a.download = image.file.name;
    document.body.append(a); a.click(); a.remove();
  };
  const share = async () => {
    if (!image || busy) return;
    setBusy(true); setError('');
    try {
      if (telegramShare) {
        const message = await prepare();
        tg.shareMessage(message.id, () => setBusy(false));
      } else await navigator.share({ files: [image.file], title: `Коняево · ${options.target}`, text: scheduleCaption(options) });
    }
    catch (error) {
      if (!(error instanceof DOMException && error.name === 'AbortError')) setError(error instanceof Error ? error.message : 'Отправка недоступна. Сохраните картинку и прикрепите её в Telegram.');
    } finally { setBusy(false); }
  };
  return <>
    <button ref={trigger} type="button" className={fullWidth ? 'button-quiet w-full flex items-center justify-center gap-2 text-xs' : 'icon-button surface'} disabled={disabled} onClick={() => setOpen(true)} title="Поделиться расписанием" aria-label="Поделиться расписанием"><Share2 size={18} />{fullWidth && 'Поделиться расписанием'}</button>
    {open && createPortal(<div className="share-overlay" onClick={event => { if (event.target === event.currentTarget) setOpen(false); }}>
      <section role="dialog" aria-modal="true" aria-labelledby="share-title" className="share-dialog surface">
        <div className="flex items-center justify-between gap-3 mb-3"><h2 id="share-title" className="font-semibold">Поделиться расписанием</h2><button ref={close} type="button" className="icon-button" onClick={() => setOpen(false)} aria-label="Закрыть"><X /></button></div>
        {choices && <div className="grid grid-cols-2 gap-2 mb-3">{choices.map((item,index)=><button type="button" key={item.label} className="choice" aria-pressed={choice === index} disabled={busy} onClick={()=>setChoice(index)}>{item.label}</button>)}</div>}
        <div className="share-preview">{image ? <img src={image.url} alt={`Расписание: ${options.target}, ${options.day}, ${options.date}`} /> : !error && <LoadingIndicator compact label="Готовим картинку" />}</div>
        {error && <p role="alert" className="notice error-notice my-3">{error}</p>}
        {image && <div className="mt-4 space-y-2">
          <p className="text-xs text-theme-subtext whitespace-pre-line p-3 surface rounded-xl">{scheduleCaption(options)}</p>
          {(telegramShare || image.canShare) && <button type="button" className="button-primary w-full" disabled={busy} onClick={share}><Share2 size={18} />{busy ? 'Открываем отправку…' : telegramShare ? 'Отправить в Telegram' : 'Поделиться картинкой'}</button>}
          <button type="button" className={`${telegramShare || image.canShare ? 'button-quiet' : 'button-primary'} w-full`} disabled={busy} onClick={download}><Download size={18} />Сохранить картинку</button>
          <p className="text-xs text-theme-subtext leading-relaxed">{telegramShare ? 'Выберите чат — Telegram отправит картинку с кнопкой бота.' : image.canShare ? 'Выберите Telegram и получателя в меню отправки.' : 'Сохраните картинку, затем прикрепите её в нужный чат Telegram.'}</p>
        </div>}
      </section>
    </div>, document.body)}
  </>;
}
