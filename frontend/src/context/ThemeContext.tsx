import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
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

  useEffect(() => {
    document.documentElement.classList.toggle('theme-light', currentThemeDef.isLight);
    document.documentElement.classList.toggle('theme-dark', !currentThemeDef.isLight);
    document.documentElement.style.colorScheme = currentThemeDef.isLight ? 'light' : 'dark';
    document.body.style.backgroundColor = currentThemeDef.isLight ? '#f5f6f8' : '#090a0b';
    document.body.style.color = currentThemeDef.isLight ? '#18202c' : '#eef0f4';
  }, [currentThemeDef]);

  return <ThemeContext.Provider value={{ theme, setTheme, currentThemeDef }}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const context = useContext(ThemeContext);
  if (!context) throw new Error('ThemeProvider не подключён');
  return context;
}
