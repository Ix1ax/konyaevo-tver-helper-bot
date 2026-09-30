import { LoadingIndicator } from './Ui';
import { useEffect, useState } from 'react';
import { signedRequest } from '../api/client';
interface Review { id: number; userId: number; stars: number; comment: string; createdAt: string }
interface Reviews { items: Review[]; page: number; pages: number; total: number; average: number; distribution: Record<number, number> }
export function AdminFeedback() {
  const [page, setPage] = useState(0);
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<Reviews>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    let active = true; setLoading(true); setError('');
    signedRequest<Reviews>(`/admin/feedback?page=${page}&size=10`).then(value => { if (active) setData(value); })
      .catch(e => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [page, revision]);
  return <section className="mt-6" aria-label="Оценки пользователей">
    <div className="flex items-center justify-between mb-3"><h3 className="font-semibold text-sm">Отзывы пользователей</h3>
      <button className="button-quiet text-xs" disabled={loading} onClick={() => setRevision(v => v + 1)}>Обновить</button>
    </div>
    {data && <>
      <p className="text-sm mb-3">{data.total ? `${data.average.toFixed(1)} из 5 · Оценок: ${data.total}` : 'Оценок пока нет'}</p>
      <p className="text-xs text-theme-subtext mb-3">{[5,4,3,2,1].map(stars => `${stars}★: ${data.distribution[stars] || 0}`).join(' · ')}</p>
      <div aria-busy={loading}>{data.items.map(item => <article key={item.id} className="py-3 border-t border-theme-border">
        <p className="text-sm font-semibold">{'★'.repeat(item.stars)}{'☆'.repeat(5 - item.stars)}</p>
        <p className="text-xs text-theme-subtext mt-1">Пользователь {item.userId} · {new Date(item.createdAt).toLocaleDateString('ru-RU', { timeZone: 'Europe/Moscow' })}</p>
        <p className="text-sm mt-2 whitespace-pre-wrap break-words">{item.comment || 'Без комментария'}</p>
      </article>)}</div>
      {data.pages > 1 && <div className="flex items-center justify-between mt-3">
        <button className="button-quiet" disabled={loading || page === 0} onClick={() => setPage(p => p - 1)}>Назад</button>
        <span className="text-xs">{page + 1} / {data.pages}</span>
        <button className="button-quiet" disabled={loading || page + 1 >= data.pages} onClick={() => setPage(p => p + 1)}>Далее</button>
      </div>}
    </>}
    {loading && <LoadingIndicator compact={Boolean(data)} label="Загружаем отзывы…" />}
    {error && <p className="text-sm mt-3" role="alert">{error}</p>}
  </section>;
}
