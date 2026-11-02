import React from 'react';
import type { Lesson, UserRole } from '../types';
import { mascotTip } from '../utils/mascotTip';
import { Mascot } from './Mascot';
import { useMascotLook } from '../context/MascotContext';
import { Timer } from 'lucide-react';

const moscowMinutes = () => {
  const now = new Date();
  return ((now.getUTCHours() + 3) % 24) * 60 + now.getUTCMinutes();
};

export function MascotTip({ lessons, role, tomorrow = false, short = false, onBells }: {
  lessons: Lesson[]; role: UserRole; tomorrow?: boolean; short?: boolean; onBells?: () => void;
}) {
  const look = useMascotLook();
  const [now, setNow] = React.useState(moscowMinutes);
  React.useEffect(() => {
    if (tomorrow) return;
    const timer = setInterval(() => setNow(moscowMinutes()), 30000);
    return () => clearInterval(timer);
  }, [tomorrow]);

  const tip = mascotTip(lessons, tomorrow ? null : now, role)
    ?? (short ? { mood: 'idle' as const, pose: 'wave' as const, extra: [], text: tomorrow ? 'Завтра пары по 60 минут.' : 'Сегодня пары по 60 минут.' } : null);
  if (!tip) return null;
  return <aside className="mascot-tip surface" aria-live="polite">
    <Mascot size={88} look={look} mood={tip.mood} pose={tip.pose} extra={tip.extra} className="mascot-tip-figure" />
    <div className="mascot-tip-body">
      <p className="mascot-tip-name">Парыч</p>
      <p className="mascot-tip-text">{tip.text.split('**').map((part, index) => index % 2 ? <b key={index}>{part}</b> : part)}</p>
      {/* Сокращённый день — строкой в подсказке, а не отдельной плашкой: экран остаётся про пары. */}
      {short && <button type="button" className="mascot-tip-short" onClick={onBells}><Timer size={14} />Сокращённые пары</button>}
    </div>
  </aside>;
}
