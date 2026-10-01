import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Clock, X } from 'lucide-react';

export function TimePicker({ value, onChange, disabled = false }: {value: string; onChange: (value: string) => void; disabled?: boolean}) {
  const [open, setOpen] = useState(false);
  const [hour, setHour] = useState('18');
  const [minute, setMinute] = useState('00');
  const trigger = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLElement>(null);
  useEffect(() => {
    if (!open) return;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog.current?.querySelector<HTMLButtonElement>('[aria-label="Закрыть"]')?.focus();
    dialog.current?.querySelectorAll<HTMLElement>('[aria-pressed="true"]').forEach(el => el.scrollIntoView({block:'center'}));
    const key = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
      if (e.key !== 'Tab') return;
      const buttons = Array.from(dialog.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)') || []);
      const first = buttons[0], last = buttons[buttons.length-1];
      if (e.shiftKey && document.activeElement === first) { e.preventDefault(); last?.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown',key);
    return () => { document.body.style.overflow = overflow; document.removeEventListener('keydown',key); trigger.current?.focus(); };
  },[open]);
  return <>
    <button ref={trigger} type="button" className="field mt-2 flex items-center justify-between" disabled={disabled} aria-haspopup="dialog"
      onClick={() => { const parts = /^\d{2}:\d{2}$/.test(value) ? value.split(':') : ['18','00']; setHour(parts[0]); setMinute(parts[1]); setOpen(true); }}>
      <span className="font-unbounded font-bold">{value || 'Выбрать время'}</span><Clock size={18} />
    </button>
    {open && createPortal(<div className="share-overlay" onClick={e => { if (e.target === e.currentTarget) setOpen(false); }}>
      <section ref={dialog} role="dialog" aria-modal="true" aria-labelledby="time-title" className="share-dialog surface time-dialog">
        <div className="flex items-center justify-between"><h2 id="time-title" className="font-semibold">Время уведомлений</h2><button type="button" className="icon-button" aria-label="Закрыть" onClick={() => setOpen(false)}><X /></button></div>
        <p className="text-xs text-theme-subtext mt-1">По московскому времени</p>
        <p className="font-unbounded font-bold text-3xl text-center my-5" aria-live="polite">{hour}:{minute}</p>
        <div className="grid grid-cols-2 gap-3">
          {([{label:'Часы', count:24, selected:hour, set:setHour},{label:'Минуты', count:60, selected:minute, set:setMinute}]).map(column => <div key={column.label}>
            <p className="text-xs text-center text-theme-subtext mb-2">{column.label}</p>
            <div className="time-options" role="group" aria-label={column.label}>
              {Array.from({length:column.count},(_,i)=>String(i).padStart(2,'0')).map(item=><button type="button" className="time-option" key={item} aria-label={`${column.label}: ${item}`} aria-pressed={column.selected===item} onClick={()=>column.set(item)}>{item}</button>)}
            </div>
          </div>)}
        </div>
        <div className="grid grid-cols-2 gap-3 mt-5"><button type="button" className="button-quiet" onClick={()=>setOpen(false)}>Отмена</button><button type="button" className="button-primary" onClick={()=>{onChange(`${hour}:${minute}`);setOpen(false);}}>Выбрать</button></div>
      </section>
    </div>,document.body)}
  </>;
}
