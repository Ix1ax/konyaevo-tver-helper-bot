import { createRoot } from 'react-dom/client';
import { flushSync } from 'react-dom';
import type { Lesson } from '../types';
import { LessonCardContent } from '../components/LessonCard';
import { HeaderContent } from '../components/Header';
import { ArrowRightLeft, ChevronRight } from 'lucide-react';
import { SummaryCardContent } from '../components/SummaryCard';

export const BOT_HANDLE = 'konyaevo_tver_helper_bot';
export const BOT_LINK = `https://t.me/${BOT_HANDLE}`;
export interface ScheduleImageOptions {
  target: string;
  highlightCurrent?: boolean;
  role?: 'student' | 'teacher';
  day: string;
  date: string;
  red: boolean;
  lessons: Lesson[];
  baseSchedule?: boolean;
  isLight?: boolean;
}
export function scheduleCaption(options: ScheduleImageOptions): string {
  return `Ваше расписание на ${options.date} (${options.day.toLowerCase()})\n${options.role === 'teacher' ? 'Преподаватель' : 'Группа'}: ${options.target}${options.baseSchedule ? '\nОсновное расписание без замен и отмен.' : ''}\n\nРасписание Коняево — @${BOT_HANDLE}\n${BOT_LINK}`;
}

let embeddedFonts: Promise<string> | undefined;

/** Uses the same React cards and CSS as the application; only navigation is omitted. */
export async function renderScheduleImages(options: ScheduleImageOptions, includeJpeg = false): Promise<{png: Blob; jpeg: Blob}> {
  await Promise.all([document.fonts.load('900 20px Unbounded', 'Коняево'), document.fonts.load('700 16px "Plus Jakarta Sans"', 'Расписание')]);
  await document.fonts.ready;
  const { toCanvas, getFontEmbedCSS } = await import('html-to-image');
  const host = document.createElement('div');
  host.style.cssText = 'position:fixed;left:-10000px;top:0;width:390px;pointer-events:none;';
  host.className = options.isLight ? 'theme-light' : 'theme-dark dark';
  document.body.append(host);
  const root = createRoot(host);
  try {
    flushSync(() => root.render(<div data-export="schedule" className="app-shell" style={{ width:390, padding:14, minHeight:0, color: 'var(--color-text)' }}>
      <HeaderContent title={options.day} subtitle={options.date} hideAvatar identityLabel={options.role === 'teacher' ? 'Преподаватель' : 'Группа'} name={options.target} red={options.red} />
      {options.baseSchedule && <p className="text-sm text-theme-subtext mb-4">Основное расписание без замен и отмен</p>}
      {options.lessons.some(lesson => lesson.changed) && <div className="mb-3.5 p-3 rounded-2xl bg-amber-500/[0.08] border border-amber-500/25 text-amber-500 dark:text-amber-400 flex items-center gap-2.5 text-xs font-semibold w-full text-left shadow-sm">
        <ArrowRightLeft size={16} className="text-amber-500 shrink-0" /><span className="flex-1">Есть изменения в расписании</span><ChevronRight size={16} className="opacity-50" />
      </div>}
      <SummaryCardContent lessons={options.lessons} isLight={options.isLight} />
      {options.lessons.length ? options.lessons.map((lesson, index) => <LessonCardContent key={index} lesson={lesson} highlightCurrent={options.highlightCurrent} isLight={options.isLight} role={options.role} />) : <div className="surface rounded-2xl p-8 text-center text-theme-subtext">На этот день занятий не запланировано.</div>}
      <div className="rounded-2xl p-3.5 mt-5" style={{ background:'#192840', border:'1px solid #304e79', color:'#eef0f4' }}>
        <p className="font-unbounded text-sm font-bold mb-2">Твоё расписание — в Telegram</p>
        <p className="font-semibold text-sm mb-1" style={{ color:'#a9c2ff' }}>@{BOT_HANDLE}</p>
        <p className="text-[11px]" style={{color:'#a8b7d2'}}>Сегодня, завтра, замены и свободные кабинеты</p>
      </div>
    </div>));
    await document.fonts.ready;
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
    const element = host.firstElementChild as HTMLElement;
    // Browser-native SVG rendering preserves font baselines and icon alignment.
    // Reuse embedded fonts across exports, but allow retry after a network failure.
    if (!embeddedFonts) embeddedFonts = getFontEmbedCSS(element).catch(error => { embeddedFonts = undefined; throw error; });
    const fontEmbedCSS = await embeddedFonts;
    const canvas = await toCanvas(element, { pixelRatio:1080 / 390, backgroundColor: options.isLight ? '#f8fafc' : '#090a0b',
      width:390, fontEmbedCSS });
    const encode = (format: 'image/png' | 'image/jpeg') => new Promise<Blob>((resolve,reject) => canvas.toBlob(blob => blob ? resolve(blob) : reject(new Error('Не удалось сохранить картинку')), format, 0.9));
    const [png, jpeg] = await Promise.all([encode('image/png'), includeJpeg ? encode('image/jpeg') : Promise.resolve(null)]);
    return {png, jpeg:jpeg || png};
  } finally { root.unmount(); host.remove(); }
}

export async function renderScheduleImage(options: ScheduleImageOptions, format: 'image/png' | 'image/jpeg' = 'image/png'): Promise<Blob> {
  const images = await renderScheduleImages(options, format === 'image/jpeg');
  return format === 'image/jpeg' ? images.jpeg : images.png;
}
