import { type LucideIcon, CalendarOff } from 'lucide-react';
import type { ReactNode } from 'react';

export function IconBadge({ icon: Icon }: { icon: LucideIcon }) {
  return <span className="icon-badge"><Icon aria-hidden="true" /></span>;
}

export function EmptyState({ title = 'Пар нет', children }: { title?: string; children?: ReactNode }) {
  return <div className="empty-state"><IconBadge icon={CalendarOff} /><h3>{title}</h3>{children && <p>{children}</p>}</div>;
}

export function LoadingIndicator({ label = 'Загружаем расписание', compact = false }: { label?: string; compact?: boolean }) {
  return <div className={`loading-state ${compact ? 'loading-compact' : ''}`} role="status" aria-live="polite">
    <span className="loading-brand font-unbounded" aria-hidden="true">Коняево</span>
    <span className="loading-dots" aria-hidden="true"><i /><i /><i /></span>
    <span className="text-sm text-theme-subtext">{label}</span>
  </div>;
}
