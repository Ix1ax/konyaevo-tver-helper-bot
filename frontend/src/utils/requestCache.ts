/** Небольшой кеш справочников: объединяет одинаковые запросы и не хранит ошибки. */
export class RequestCache {
  private values = new Map<string, { value: unknown; expires: number }>();
  private pending = new Map<string, Promise<unknown>>();
  private generation = 0;
  constructor(private now: () => number = Date.now) {}
  load<T>(key: string, ttl: number, request: () => Promise<T>): Promise<T> {
    const cached = this.values.get(key);
    if (cached && cached.expires > this.now()) return Promise.resolve(cached.value as T);
    const existing = this.pending.get(key);
    if (existing) return existing as Promise<T>;
    const version = this.generation;
    const pending = Promise.resolve().then(request).then(value => {
      if (version === this.generation) this.values.set(key, { value, expires: this.now() + ttl });
      return value;
    }).finally(() => { if (this.pending.get(key) === pending) this.pending.delete(key); });
    this.pending.set(key, pending);
    return pending;
  }
  clear() { this.generation++; this.values.clear(); this.pending.clear(); }
}
