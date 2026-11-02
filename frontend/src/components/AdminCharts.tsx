import { useState, type ReactNode } from 'react';

/** Круглая верхняя граница оси: 0 · половина · максимум читаются без дробей. */
export function niceMax(value: number) {
  // До 20 — чётный верх, чтобы середина шкалы была целой (16 → 8, а не 15 → 7.5).
  if (value <= 20) return Math.max(4, Math.ceil(value / 2) * 2);
  const power = 10 ** Math.floor(Math.log10(value));
  const step = [1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10].find(m => m * power >= value) ?? 10;
  return step * power;
}

export const compact = (value: number) => value >= 10000 ? `${Math.round(value / 1000)}K` : value >= 1000 ? `${(value / 1000).toFixed(1).replace('.0', '')}K` : String(value);

export function ChartCard({ title, subtitle, children, aside }: { title: string; subtitle?: string; children: ReactNode; aside?: ReactNode }) {
  return <section className="chart-card surface">
    <div className="chart-card-head">
      <div className="min-w-0"><h3>{title}</h3>{subtitle && <p>{subtitle}</p>}</div>
      {aside}
    </div>
    {children}
  </section>;
}

export function Kpi({ label, value, note, tone }: { label: string; value: ReactNode; note?: ReactNode; tone?: 'up' | 'down' }) {
  return <div className="kpi">
    <p className="kpi-label">{label}</p>
    <p className="kpi-value font-unbounded">{value}</p>
    {note && <p className="kpi-note" data-tone={tone}>{note}</p>}
  </div>;
}

export interface Column { key: string; axis: string; title: string; value: number; detail?: string }

/**
 * Столбцы по дням: одна серия, без легенды (её называет заголовок карточки).
 * Подсказка показывает выбранный день; по умолчанию — последний.
 */
export function ColumnChart({ columns, color, height = 150, unit }: { columns: Column[]; color: string; height?: number; unit: string }) {
  const [selected, setSelected] = useState<number | null>(null);
  const index = selected ?? columns.length - 1;
  const max = niceMax(Math.max(0, ...columns.map(c => c.value)));
  const current = columns[index];
  const labels = new Set([0, Math.floor((columns.length - 1) / 2), columns.length - 1]);
  return <div className="column-chart" style={{ ['--series' as string]: color }}>
    {current && <p className="column-chart-tip" aria-live="polite">
      <span>{current.title}</span><b className="font-unbounded">{current.value.toLocaleString('ru-RU')}</b> {unit}
      {current.detail && <em> · {current.detail}</em>}
    </p>}
    <div className="column-chart-plot" style={{ height }} onMouseLeave={() => setSelected(null)}>
      {[1, 0.5, 0].map(level => <div key={level} className="column-chart-grid" style={{ bottom: `${level * 100}%` }}><span>{compact(max * level)}</span></div>)}
      <div className="column-chart-bars" role="list" aria-label={`${unit} по дням`}>
        {columns.map((column, i) => <button key={column.key} type="button" role="listitem" className="column-chart-col"
          data-active={i === index || undefined} aria-label={`${column.title}: ${column.value} ${unit}`}
          onMouseEnter={() => setSelected(i)} onFocus={() => setSelected(i)} onClick={() => setSelected(i)}>
          <i style={{ height: column.value ? `max(3px, ${column.value / max * 100}%)` : 0 }} />
        </button>)}
      </div>
    </div>
    <div className="column-chart-axis">
      {columns.map((column, i) => <span key={column.key}>{labels.has(i) ? column.axis : ''}</span>)}
    </div>
  </div>;
}

export interface BarRow { label: string; value: number; extra?: string }

/** Горизонтальные полосы: подпись слева, значение справа, полоса — доля от максимума в списке. */
export function BarList({ rows, color, empty = 'Пока нет данных' }: { rows: BarRow[]; color: string; empty?: string }) {
  const max = Math.max(1, ...rows.map(r => r.value));
  if (!rows.length || rows.every(r => !r.value)) return <p className="chart-empty">{empty}</p>;
  return <ul className="bar-list" style={{ ['--series' as string]: color }}>
    {rows.map(row => <li key={row.label}>
      <div className="bar-list-head"><span className="bar-list-label">{row.label}</span>
        <span className="bar-list-value font-unbounded">{row.value.toLocaleString('ru-RU')}</span></div>
      <div className="bar-list-track"><i style={{ width: `${row.value / max * 100}%` }} /></div>
      {row.extra && <p className="bar-list-extra">{row.extra}</p>}
    </li>)}
  </ul>;
}
