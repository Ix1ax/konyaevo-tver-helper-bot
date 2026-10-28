import { type LucideIcon, CalendarOff } from 'lucide-react';
import type { ReactNode } from 'react';
import { Mascot } from './Mascot';
import { useMascotLook } from '../context/MascotContext';

export function IconBadge({ icon: Icon }: { icon: LucideIcon }) {
  return <span className="icon-badge"><Icon aria-hidden="true" /></span>;
}

/** mascot — спящий Парыч вместо иконки для дней без пар. */
export function EmptyState({ title = 'Пар нет', mascot = false, children }: { title?: string; mascot?: boolean; children?: ReactNode }) {
  const look = useMascotLook();
  return <div className="empty-state">
    {mascot ? <Mascot size={150} look={look} mood="sleepy" extra={['zzz']} className="empty-state-mascot" /> : <IconBadge icon={CalendarOff} />}
    <h3>{title}</h3>{children && <p>{children}</p>}
  </div>;
}

export function LoadingIndicator({ label = 'Загружаем расписание', compact = false }: { label?: string; compact?: boolean }) {
  const look = useMascotLook();
  return <div className={`loading-state ${compact ? 'loading-compact' : ''}`} role="status" aria-live="polite">
    {!compact && <Mascot size={120} look={look} mood="wink" pose="hold-list" className="loading-mascot" />}
    <span className="loading-brand font-unbounded" aria-hidden="true">Коняево</span>
    <span className="loading-dots" aria-hidden="true"><i /><i /><i /></span>
    <span className="text-sm text-theme-subtext">{label}</span>
  </div>;
}
