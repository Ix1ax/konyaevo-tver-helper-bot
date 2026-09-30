import { CircleHelp } from 'lucide-react';
import { useApp } from '../context/AppContext';

export function HelpPanel() {
  const { isAdmin } = useApp();
  return <details className="settings-group mb-6">
    <summary className="settings-row cursor-pointer">
      <span className="icon-badge"><CircleHelp size={20} /></span>
      <span><span className="block font-semibold text-sm">Помощь</span>
        <span className="block text-xs text-theme-subtext mt-1">Разделы приложения и команды бота</span></span>
    </summary>
    <div className="px-5 pb-5 text-sm text-theme-subtext space-y-3">
      <p><strong>Сегодня и завтра.</strong> Ваше расписание с учётом опубликованных замен. Раздел «Неделя» показывает основное расписание без замен и отмен. Отменённые пары отмечены отдельно. Раздел «Изменения» показывает замены Вашей группы или преподавателя.</p>
      <p><strong>Кабинеты.</strong> Свободные аудитории для выбранного дня текущей недели и номера пары. Расчёт по основному расписанию, без замен; дата соответствует выбранному дню.</p>
      <p><strong>Поиск.</strong> Расписание преподавателей и выбор преподавателя для Вашего профиля.</p>
      <p><strong>Профиль.</strong> Выбор роли, группы или преподавателя, темы и уведомлений. Уведомления приходят в чат бота; время указано по Москве. Настройки уведомлений общие с ботом и сохраняются при открытии приложения через Telegram.</p>
      <p><strong>Команды бота:</strong> <code>/start</code> — меню; <code>/help</code> — помощь; <code>/cancel</code> — отмена ввода и возврат в меню. Расписание и уведомления доступны через кнопки меню.</p>
      {isAdmin && <p><strong>Администратору:</strong> <code>/admin</code> — управление; <code>/stats</code> — статистика; <code>/refresh</code> — обновление данных; <code>/broadcast текст</code> — текстовая рассылка; <code>/broadcast</code> и прикреплённое медиа — рассылка фото, видео, GIF или файла после подтверждения. В админке приложения доступны активность, состояние расписания, общие замены и отзывы с пагинацией.</p>}
    </div>
  </details>;
}
