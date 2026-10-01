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

export function getApiBaseUrl(): string {
  const base = ((import.meta.env.VITE_API_URL as string) || '/api').trim().replace(/\/$/, '');
  return base.endsWith('/api') ? base : `${base}/api`;
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
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const res = await fetch(`${getApiBaseUrl()}/profile`, {
      method: update ? 'PUT' : 'GET', signal: controller.signal,
      headers: { 'X-Telegram-Init-Data': data, 'Content-Type': 'application/json' },
      body: update ? JSON.stringify(update) : undefined,
    });
    if (!res.ok) throw new Error(res.status === 401 ? 'Откройте приложение заново через Telegram.' : 'Не удалось сохранить настройки. Попробуйте ещё раз.');
    return await res.json();
  } finally { clearTimeout(timeout); }
}

export async function signedRequest<T>(path: string, body?: unknown): Promise<T> {
  const initData = window.Telegram?.WebApp?.initData;
  if (!initData) throw new Error('Откройте приложение через Telegram.');
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${getApiBaseUrl()}${path}`, {
      method: body === undefined ? 'GET' : 'POST', signal: controller.signal, cache: 'no-store',
      headers: { 'X-Telegram-Init-Data': initData, 'Content-Type': 'application/json' },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
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
  } finally { clearTimeout(timeout); }
}

// Helper to fetch JSON with timeout and console logging
async function fetchWithTimeout<T>(url: string, timeoutMs = 6000): Promise<T> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const res = await fetch(url, { signal: controller.signal });
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`);
    }
    const json = (await res.json()) as T;
    return json;
  } catch (err) {
    console.error(`[API] Failed request to ${url}:`, err);
    throw err;
  } finally {
    clearTimeout(timeoutId);
  }
}

export const api = {
  refreshCatalogs() { catalogs.clear(); },
  async isAdmin(): Promise<boolean> {
    const initData = window.Telegram?.WebApp?.initData;
    if (!initData) return false;
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    try {
      const response = await fetch(`${getApiBaseUrl()}/admin/overview`, { headers: { 'X-Telegram-Init-Data': initData }, signal: controller.signal, cache: 'no-store' });
      return response.ok;
    } catch { return false; }
    finally { clearTimeout(timeout); }
  },
  async getInfo(): Promise<InfoResponse> {
    try {
      const data = await fetchWithTimeout<InfoResponse & { redWeek?: boolean }>(`${getApiBaseUrl()}/info`);
      // Старый сервер использует redWeek; совместимость нужна на время обновления.
      return { ...data, isRedWeek: data.isRedWeek ?? data.redWeek ?? false };
    } catch (e) {
      console.warn('[API] Could not fetch info from', getApiBaseUrl(), e);
      throw e;
    }
  },

  async getGroups(): Promise<GroupsResponse> {
    try {
      const data = await catalogs.load(`${getApiBaseUrl()}/groups`, 300000, () => fetchWithTimeout<GroupsResponse>(`${getApiBaseUrl()}/groups`));
      return data;
    } catch {
      throw new Error('Не удалось загрузить группы');
    }
  },

  async getTeachers(): Promise<TeachersResponse> {
    try {
      const data = await catalogs.load(`${getApiBaseUrl()}/teachers`, 300000, () => fetchWithTimeout<TeachersResponse>(`${getApiBaseUrl()}/teachers`));
      return data;
    } catch {
      throw new Error('Не удалось загрузить преподавателей');
    }
  },

  async getGroupSchedule(groupName: string): Promise<ScheduleMap> {
    try {
      const data = await fetchWithTimeout<ScheduleMap>(`${getApiBaseUrl()}/schedule/group/${encodeURIComponent(groupName)}`);
      return data;
    } catch {
      throw new Error('Не удалось загрузить расписание');
    }
  },

  async getTeacherSchedule(teacherName: string): Promise<ScheduleMap> {
    try {
      const data = await fetchWithTimeout<ScheduleMap>(`${getApiBaseUrl()}/schedule/teacher/${encodeURIComponent(teacherName)}`);
      return data;
    } catch {
      throw new Error('Не удалось загрузить расписание');
    }
  },

  async getBaseSchedule(role: 'student' | 'teacher', target: string): Promise<ScheduleMap> {
    const url = `${getApiBaseUrl()}/schedule/base/${role === 'student' ? 'group' : 'teacher'}/${encodeURIComponent(target)}`;
    return catalogs.load(url, 300000, () => fetchWithTimeout<ScheduleMap>(url));
  },

  async getGroupChanges(groupName: string): Promise<ChangesResponse> {
    try {
      return await fetchWithTimeout<ChangesResponse>(`${getApiBaseUrl()}/changes/group/${encodeURIComponent(groupName)}`);
    } catch {
      throw new Error('Не удалось загрузить замены');
    }
  },

  async getAllChanges(): Promise<ChangeItem[]> {
    const initData = window.Telegram?.WebApp?.initData;
    if (!initData) throw new Error('Откройте приложение через Telegram.');
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    try {
      const response = await fetch(`${getApiBaseUrl()}/admin/changes`, { headers: { 'X-Telegram-Init-Data': initData }, signal: controller.signal, cache: 'no-store' });
      if (!response.ok) throw new Error('Общий список доступен только администратору.');
      return await response.json();
    } finally { clearTimeout(timeout); }
  },

  async getFreeClassrooms(day: string, slot: number, weekType: string): Promise<ClassroomsResponse> {
    try {
      const params = new URLSearchParams({ day, slot: String(slot), weekType });
      const data = await fetchWithTimeout<ClassroomsResponse>(`${getApiBaseUrl()}/classrooms/free?${params.toString()}`);
      return data;
    } catch {
      throw new Error('Не удалось загрузить аудитории');
    }
  },

  async searchTeachers(query: string): Promise<string[]> {
    try {
      return await fetchWithTimeout<string[]>(`${getApiBaseUrl()}/teachers/search?q=${encodeURIComponent(query)}`);
    } catch {
      throw new Error('Поиск преподавателей недоступен');
    }
  },
};
