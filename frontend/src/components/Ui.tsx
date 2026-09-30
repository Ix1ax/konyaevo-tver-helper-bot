import { type LucideIcon, CalendarOff } from 'lucide-react';
import type { ReactNode } from 'react';

export function IconBadge({ icon: Icon }: { icon: LucideIcon }) {
  return <span className="icon-badge"><Icon aria-hidden="true" /></span>;
}

export function EmptyState({ title = 'Пар нет', children }: { title?: string; children?: ReactNode }) {
  return <div className="empty-state"><IconBadge icon={CalendarOff} /><h3>{title}</h3>{children && <p>{children}</p>}</div>;
}
