import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import type { MascotLookId, MascotState } from '../types';
import { signedRequest } from '../api/client';
import { useApp } from './AppContext';

type MascotEvent = 'open' | 'tomorrow' | 'share';

interface MascotContextType {
  /** null — вне Telegram или пока сервер не ответил: огонёк не показываем. */
  mascot: MascotState | null;
  look: MascotLookId;
  sendEvent: (type: MascotEvent) => void;
  updateSettings: (settings: { look?: MascotLookId; reminder?: boolean }) => Promise<void>;
  acknowledgeRewards: () => void;
}

const MascotContext = createContext<MascotContextType | undefined>(undefined);

const moscowHour = () => (new Date().getUTCHours() + 3) % 24;

export const MascotProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { role, selectedGroup, selectedTeacher, activeTab } = useApp();
  const [mascot, setMascot] = useState<MascotState | null>(null);
  const signed = Boolean(window.Telegram?.WebApp?.initData);

  const sendEvent = useCallback((type: MascotEvent) => {
    if (!signed) return;
    signedRequest<MascotState>('/mascot/event', { type }).then(setMascot)
      .catch(() => { /* Огонёк не мешает расписанию, если сервер недоступен. */ });
  }, [signed]);

  // Учебные дни зависят от выбранной группы, поэтому огонёк пересчитывается при смене профиля.
  const target = role === 'student' ? selectedGroup : selectedTeacher;
  useEffect(() => { if (target) sendEvent('open'); }, [target, sendEvent]);
  useEffect(() => {
    const visible = () => { if (!document.hidden && target) sendEvent('open'); };
    document.addEventListener('visibilitychange', visible);
    return () => document.removeEventListener('visibilitychange', visible);
  }, [target, sendEvent]);
  useEffect(() => { if (activeTab === 'tomorrow' && moscowHour() >= 18) sendEvent('tomorrow'); }, [activeTab, sendEvent]);

  const updateSettings = useCallback(async (settings: { look?: MascotLookId; reminder?: boolean }) => {
    setMascot(await signedRequest<MascotState>('/mascot/settings', settings));
  }, []);

  const acknowledgeRewards = useCallback(() => {
    setMascot(current => current && { ...current, rewards: [] });
    signedRequest<MascotState>('/mascot/rewards/ack', {}).then(setMascot).catch(() => { /* Награда покажется снова при следующем входе. */ });
  }, []);

  return <MascotContext.Provider value={{ mascot, look: mascot?.look ?? 'classic', sendEvent, updateSettings, acknowledgeRewards }}>
    {children}
  </MascotContext.Provider>;
};

/** Облик для мест вне провайдера (например, картинка расписания) — обычный. */
export const useMascotLook = (): MascotLookId => useContext(MascotContext)?.look ?? 'classic';

export const useMascot = () => {
  const context = useContext(MascotContext);
  if (!context) throw new Error('useMascot must be used within MascotProvider');
  return context;
};
