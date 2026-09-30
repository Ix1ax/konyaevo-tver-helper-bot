import { MessageCircle, X } from 'lucide-react';

export function hasSeenChatNotice(key: string): boolean {
  try { return localStorage.getItem(key) === 'seen'; }
  catch { return false; }
}

export function ChatVersionNotice({ storageKey, onDismiss }: { storageKey: string; onDismiss: () => void }) {
  const dismiss = () => {
    try { localStorage.setItem(storageKey, 'seen'); }
    catch { /* Закрытие работает и при недоступном хранилище. */ }
    onDismiss();
  };
  return <section role="dialog" aria-modal="false" aria-labelledby="chat-notice-title" className="surface feedback-popup">
    <button type="button" className="feedback-close" aria-label="Закрыть приветствие" onClick={dismiss}><X size={16} /></button>
    <div className="icon-badge mb-3"><MessageCircle size={22} /></div>
    <h2 id="chat-notice-title" className="text-base font-semibold pr-6">Чат-бот никуда не исчез</h2>
    <p className="text-sm text-theme-subtext mt-2 leading-relaxed">Mini App — дополнительный способ смотреть расписание. Все привычные функции остались в чате: сегодня, завтра, неделя, замены и уведомления.</p>
    <p className="text-sm text-theme-subtext mt-2 leading-relaxed">Выбирайте удобный Вам формат. Чтобы увидеть меню в чате, отправьте /start.</p>
    <button type="button" className="button-primary w-full mt-4" onClick={dismiss}>Понятно</button>
  </section>;
}
