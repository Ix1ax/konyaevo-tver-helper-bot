/**
 * Номера страниц для переключателя: первая, последняя и соседи текущей; null — пропуск («…»).
 * Страницы считаются с нуля. До семи страниц показываются все.
 */
export function pageWindow(page: number, pages: number): (number | null)[] {
  if (pages <= 7) return Array.from({ length: pages }, (_, index) => index);
  const shown = new Set([0, pages - 1, page - 1, page, page + 1]);
  // У края держим пять номеров подряд, чтобы ширина переключателя не прыгала.
  if (page <= 2) [1, 2, 3].forEach(index => shown.add(index));
  if (page >= pages - 3) [pages - 4, pages - 3, pages - 2].forEach(index => shown.add(index));
  const sorted = [...shown].filter(index => index >= 0 && index < pages).sort((a, b) => a - b);
  // Пропуск из одной страницы показываем её номером: «…» занимает столько же места.
  return sorted.flatMap((index, i) => {
    const gap = i ? index - sorted[i - 1] : 1;
    return gap === 2 ? [index - 1, index] : gap > 2 ? [null, index] : [index];
  });
}

/** Подпись «13–24 из 75» для текущей страницы. */
export function pageRange(page: number, size: number, total: number): string {
  return `${page * size + 1}–${Math.min(total, (page + 1) * size)} из ${total}`;
}
