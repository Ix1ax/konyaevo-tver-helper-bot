import { useEffect, useState, type RefObject } from 'react';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { pageRange, pageWindow } from '../utils/pagination';
import { useTelegram } from '../hooks/useTelegram';

/** Текущая страница списка; при смене фильтра (reset) возвращается на первую. */
export function usePage<T>(items: T[], size: number, reset: string) {
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [reset]);
  const pages = Math.max(1, Math.ceil(items.length / size));
  const current = Math.min(page, pages - 1);
  return { items: items.slice(current * size, (current + 1) * size), page: current, pages, size, total: items.length, setPage };
}

export function Pager({ page, pages, size, total, setPage, anchor, label }: ReturnType<typeof usePage> & {
  anchor?: RefObject<HTMLElement>; label: string;
}) {
  const { haptic } = useTelegram();
  if (pages < 2) return null;
  const go = (next: number) => {
    if (next < 0 || next >= pages || next === page) return;
    haptic.selection();
    setPage(next);
    // Новая страница начинается с верха списка, а не там, где был переключатель.
    anchor?.current?.scrollIntoView({ block: 'start', behavior: 'smooth' });
  };
  return <nav className="pager" aria-label={label}>
    <button type="button" className="pager-step" onClick={() => go(page - 1)} disabled={page === 0} aria-label="Предыдущая страница"><ChevronLeft size={18} /></button>
    <div className="pager-pages">
      {pageWindow(page, pages).map((index, i) => index === null
        ? <span key={`gap-${i}`} className="pager-gap">…</span>
        : <button type="button" key={index} onClick={() => go(index)} aria-current={index === page ? 'page' : undefined}
            aria-label={`Страница ${index + 1}`}>{index + 1}</button>)}
    </div>
    <button type="button" className="pager-step" onClick={() => go(page + 1)} disabled={page === pages - 1} aria-label="Следующая страница"><ChevronRight size={18} /></button>
    <p className="pager-range">{pageRange(page, size, total)}</p>
  </nav>;
}
