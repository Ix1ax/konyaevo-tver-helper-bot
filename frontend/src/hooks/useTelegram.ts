import { useEffect, useMemo } from 'react';

declare global {
  interface Window {
    Telegram?: {
      WebApp?: any;
    };
  }
}

export function useTelegram() {
  const tg = useMemo(() => {
    return typeof window !== 'undefined' ? window.Telegram?.WebApp : undefined;
  }, []);

  useEffect(() => {
    if (tg) {
      try {
        tg.ready();
        tg.expand();
      } catch (e) {
        console.warn('Telegram WebApp initialization error:', e);
      }
    }
  }, [tg]);

  const haptic = useMemo(() => ({
    impact: (style: 'light' | 'medium' | 'heavy' | 'rigid' | 'soft' = 'light') => {
      try {
        (tg?.isVersionAtLeast?.('6.1') ? tg : undefined)?.HapticFeedback?.impactOccurred(style);
      } catch (e) {
        // В обычном браузере вибрация недоступна.
      }
    },
    notification: (type: 'error' | 'success' | 'warning' = 'success') => {
      try {
        (tg?.isVersionAtLeast?.('6.1') ? tg : undefined)?.HapticFeedback?.notificationOccurred(type);
      } catch (e) {
        // Telegram может не поддерживать вибрацию на этом устройстве.
      }
    },
    selection: () => {
      try {
        (tg?.isVersionAtLeast?.('6.1') ? tg : undefined)?.HapticFeedback?.selectionChanged();
      } catch (e) {
        // Telegram может не поддерживать вибрацию на этом устройстве.
      }
    }
  }), [tg]);

  const user = useMemo(() => {
    if (tg?.initDataUnsafe?.user) {
      return tg.initDataUnsafe.user;
    }
    return undefined;
  }, [tg]);

  return {
    tg,
    user,
    haptic,
    themeParams: tg?.themeParams,
    colorScheme: tg?.colorScheme,
    isExpanded: tg?.isExpanded,
    close: () => tg?.close(),
  };
}
