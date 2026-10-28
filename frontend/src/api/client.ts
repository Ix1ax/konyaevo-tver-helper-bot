import { RequestCache } from '../utils/requestCache';
const catalogs = new RequestCache();
import {
  ClassroomsResponse,
  GroupsResponse,
  InfoResponse,
  ScheduleMap,
  TeachersResponse,
  ChangesResponse,
  ChangeItem,
} from '../types';

const normalizeBase = (base: string) => {
  const trimmed = base.trim().replace(/\/$/, '');
  return trimmed.endsWith('/api') ? trimmed : `${trimmed}/api`;
};
// Резервный адрес нужен, когда мобильная сеть или VPN не пропускают запросы к основному серверу.
const API_BASES = [...new Set([import.meta.env.VITE_API_URL || '/api', import.meta.env.VITE_API_FALLBACK_URL]
  .filter((base): base is string => Boolean(base?.trim())).map(normalizeBase))];
const PREFERRED_BASE_KEY = 'konyaevo_api_base';

function orderedBases(): string[] {
  let preferred: string | null = null;
  try { preferred = localStorage.getItem(PREFERRED_BASE_KEY); } catch { /* Хранилище может быть недоступно. */ }
  return preferred && API_BASES.includes(preferred) ? [preferred, ...API_BASES.filter(base => base !== preferred)] : API_BASES;
}

export function getApiBaseUrl(): string {
  return orderedBases()[0];
}

/** Ни один адрес API не ответил: сеть не пропускает запросы к серверу. */
export class ApiUnavailableError extends Error {
  constructor() { super('Нет связи с сервером расписания.'); this.name = 'ApiUnavailableError'; }
}

/**
 * Запрос к API: сначала адрес, который отвечал в прошлый раз, затем остальные; при полной
 * неудаче — ещё один круг через секунду. Любой полученный ответ, даже с ошибкой HTTP,
 * считается доступностью адреса и возвращается вызывающему коду.
 */
export async function apiFetch(path: string, init: RequestInit = {}, timeoutMs = 7000): Promise<Response> {
  const outer = init.signal;
  for (let round = 0; round < 2; round++) {
    for (const base of orderedBases()) {
      if (outer?.aborted) throw new DOMException('Aborted', 'AbortError');
      const controller = new AbortController();
      const abort = () => controller.abort();
      outer?.addEventListener('abort', abort);
      const timer = setTimeout(abort, timeoutMs);
      try {
        const response = await fetch(`${base}${path}`, { ...init, signal: controller.signal });
        try { localStorage.setItem(PREFERRED_BASE_KEY, base); } catch { /* Не критично. */ }
        return response;
      } catch (error) {
        console.warn(`[API] ${base}${path} недоступен`, error);
      } finally {
        clearTimeout(timer);
        outer?.removeEventListener('abort', abort);
      }
    }
    if (round === 0) await new Promise(resolve => setTimeout(resolve, 1000));
  }
  throw new ApiUnavailableError();
}

export interface SavedProfile {
  role: 'student' | 'teacher' | null;
  group: string | null;
  teacher: string | null;
  changes: boolean;
  tomorrow: boolean;
  time: string;
  days: number[];
}

export async function profileRequest(update?: Partial<SavedProfile>): Promise<SavedProfile> {
  const data = window.Telegram?.WebApp?.initData;
  if (!data) throw new Error('Откройте приложение через Telegram для настройки уведомлений.');
  const res = await apiFetch('/profile', {
    method: update ? 'PUT' : 'GET',
    headers: { 'X-Telegram-Init-Data': data, 'Content-Type': 'application/json' },
    body: update ? JSON.stringify(update) : undefined,
  }, 10000);
  if (!res.ok) throw new Error(res.status === 401 ? 'Откройте приложение заново через Telegram.' : 'Не удалось сохранить настройки. Попробуйте ещё раз.');
  return await res.json();
}

export async function signedRequest<T>(path: string, body?: unknown): Promise<T> {
  const initData = window.Telegram?.WebApp?.initData;
  if (!initData) throw new Error('Откройте приложение через Telegram.');
  const response = await apiFetch(path, {
    method: body === undefined ? 'GET' : 'POST', cache: 'no-store',
    headers: { 'X-Telegram-Init-Data': initData, 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  }, path === '/share/prepare' ? 40000 : 10000);
  if (!response.ok && path.startsWith('/share/')) {
    const message: Record<number, string> = {
      400: 'Сервер отклонил картинку расписания.',
      401: 'Откройте приложение заново через Telegram.',
      403: 'Нет доступа к подготовке картинки.',
      404: 'API отправки картинки недоступно на сервере.',
      413: 'Картинка слишком большая для отправки.',
      429: 'Подождите несколько секунд перед повторной отправкой.',
      502: 'Сервер не смог подготовить сообщение в Telegram. Нужна проверка серверных логов.',
      503: 'Сервис отправки занят. Попробуйте позже.',
    };
    throw new Error(message[response.status] || `Ошибка подготовки картинки: HTTP ${response.status}.`);
  }
  if (!response.ok) throw new Error(response.status === 401 ? 'Откройте приложение заново через Telegram.' : response.status === 409 ? 'Оценка уже сохранена.' : 'Не удалось выполнить запрос. Попробуйте ещё раз.');
  if (response.status === 204) return undefined as T;
  return await response.json();
}

// Открытый GET-запрос к API с JSON-ответом.
async function getJson<T>(path: string): Promise<T> {
  const res = await apiFetch(path);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return (await res.json()) as T;
}

// Сетевую ошибку пробрасываем как есть: интерфейс показывает для неё отдельную подсказку.
const keepNetworkError = (error: unknown, message: string) => error instanceof ApiUnavailableError ? error : new Error(message);

export const api = {
  refreshCatalogs() { catalogs.clear(); },
  async isAdmin(): Promise<boolean> {
    const initData = window.Telegram?.WebApp?.initData;
    if (!initData) return false;
    try {
      const response = await apiFetch('/admin/overview', { headers: { 'X-Telegram-Init-Data': initData }, cache: 'no-store' }, 8000);
      return response.ok;
    } catch { return false; }
  },
  async getInfo(): Promise<InfoResponse> {
    try {
      const data = await getJson<InfoResponse & { redWeek?: boolean }>('/info');
      // Старый сервер использует redWeek; совместимость нужна на время обновления.
      return { ...data, isRedWeek: data.isRedWeek ?? data.redWeek ?? false };
    } catch (e) {
      console.warn('[API] Could not fetch info', e);
      throw e;
    }
  },

  async getGroups(): Promise<GroupsResponse> {
    try {
      const data = await catalogs.load('/groups', 300000, () => getJson<GroupsResponse>('/groups'));
      return data;
    } catch (e) {
      throw keepNetworkError(e, 'Не удалось загрузить группы');
    }
  },

  async getTeachers(): Promise<TeachersResponse> {
    try {
      const data = await catalogs.load('/teachers', 300000, () => getJson<TeachersResponse>('/teachers'));
      return data;
    } catch (e) {
      throw keepNetworkError(e, 'Не удалось загрузить преподавателей');
    }
  },

  async getGroupSchedule(groupName: string): Promise<ScheduleMap> {
    try {
      const data = await getJson<ScheduleMap>(`/schedule/group/${encodeURIComponent(groupName)}`);
      return data;
    } catch (e) {
      throw keepNetworkError(e, 'Не удалось загрузить расписание');
    }
  },

  async getTeacherSchedule(teacherName: string): Promise<ScheduleMap> {
    try {
      const data = await getJson<ScheduleMap>(`/schedule/teacher/${encodeURIComponent(teacherName)}`);
      return data;
    } catch (e) {
      throw keepNetworkError(e, 'Не удалось загрузить расписание');
    }
  },

  async getBaseSchedule(role: 'student' | 'teacher', target: string): Promise<ScheduleMap> {
    const path = `/schedule/base/${role === 'student' ? 'group' : 'teacher'}/${encodeURIComponent(target)}`;
    return catalogs.load(path, 300000, () => getJson<ScheduleMap>(path));
  },

  async getGroupChanges(groupName: string): Promise<ChangesResponse> {
    try {
      return await getJson<ChangesResponse>(`/changes/group/${encodeURIComponent(groupName)}`);
    } catch (e) {
      throw keepNetworkError(e, 'Не удалось загрузить замены');
    }
  },

  async getAllChanges(): Promise<ChangeItem[]> {
    const initData = window.Telegram?.WebApp?.initData;
    if (!initData) throw new Error('Откройте приложение через Telegram.');
    const response = await apiFetch('/admin/changes', { headers: { 'X-Telegram-Init-Data': initData }, cache: 'no-store' }, 8000);
    if (!response.ok) throw new Error('Общий список доступен только администратору.');
    return await response.json();
  },

  async getFreeClassrooms(day: string, slot: number, weekType: string): Promise<ClassroomsResponse> {
    try {
      const params = new URLSearchParams({ day, slot: String(slot), weekType });
      const data = await getJson<ClassroomsResponse>(`/classrooms/free?${params.toString()}`);
      return data;
    } catch (e) {
      throw keepNetworkError(e, 'Не удалось загрузить аудитории');
    }
  },

  async searchTeachers(query: string): Promise<string[]> {
    try {
      return await getJson<string[]>(`/teachers/search?q=${encodeURIComponent(query)}`);
    } catch (e) {
      throw keepNetworkError(e, 'Поиск преподавателей недоступен');
    }
  },
};
