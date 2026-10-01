import { createContext, useContext, useLayoutEffect, useState, type ReactNode } from 'react';
import { ThemeId } from '../types';

interface ThemeDefinition {
  id: ThemeId;
  name: string;
  isLight: boolean;
  bgClass: string;
  cardClass: string;
}

// Сохраняем старые ключи, чтобы выбранная тема не сбросилась после обновления.
export const THEMES: ThemeDefinition[] = [
  { id: 'liquid-glass-light', name: 'Светлая', isLight: true, bgClass: 'bg-theme-bg', cardClass: 'surface' },
  { id: 'liquid-glass-dark', name: 'Тёмная', isLight: false, bgClass: 'bg-theme-bg', cardClass: 'surface' },
];

interface ThemeContextType {
  theme: ThemeId;
  setTheme: (theme: ThemeId) => void;
  currentThemeDef: ThemeDefinition;
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<ThemeId>(() =>
    localStorage.getItem('konyaevo_theme') === 'liquid-glass-light' ? 'liquid-glass-light' : 'liquid-glass-dark');
  const currentThemeDef = THEMES.find((item) => item.id === theme)!;

  const setTheme = (value: ThemeId) => {
    setThemeState(value);
    localStorage.setItem('konyaevo_theme', value);
  };

  useLayoutEffect(() => {
    document.documentElement.classList.toggle('theme-light', currentThemeDef.isLight);
    document.documentElement.classList.toggle('theme-dark', !currentThemeDef.isLight);
    document.documentElement.style.colorScheme = currentThemeDef.isLight ? 'light' : 'dark';
    const background = currentThemeDef.isLight ? '#f8fafc' : '#090a0b';
    document.documentElement.style.backgroundColor = background;
    document.body.style.backgroundColor = background;
    document.querySelector('meta[name="theme-color"]')?.setAttribute('content', background);
    const tg = window.Telegram?.WebApp;
    const applyColor = (method: string, version: string) => {
      try {
        if (tg?.isVersionAtLeast?.(version)) tg[method]?.(background);
      } catch {
        // Старые клиенты могут не поддерживать окрашивание оболочки.
      }
    };
    applyColor('setBackgroundColor', '6.1');
    applyColor('setHeaderColor', '6.9');
    applyColor('setBottomBarColor', '7.10');
    document.body.style.color = currentThemeDef.isLight ? '#18202c' : '#eef0f4';
  }, [currentThemeDef]);

  return <ThemeContext.Provider value={{ theme, setTheme, currentThemeDef }}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('ThemeProvider не подключён');
  return context;
}
