import React from 'react';
import type { Lesson, UserRole } from '../types';
import { mascotTip } from '../utils/mascotTip';
import { Mascot } from './Mascot';
import { useMascotLook } from '../context/MascotContext';

const moscowMinutes = () => {
  const now = new Date();
  return ((now.getUTCHours() + 3) % 24) * 60 + now.getUTCMinutes();
};

export function MascotTip({ lessons, role, tomorrow = false }: { lessons: Lesson[]; role: UserRole; tomorrow?: boolean }) {
  const look = useMascotLook();
  const [now, setNow] = React.useState(moscowMinutes);
  React.useEffect(() => {
    if (tomorrow) return;
    const timer = setInterval(() => setNow(moscowMinutes()), 30000);
    return () => clearInterval(timer);
  }, [tomorrow]);

  const tip = mascotTip(lessons, tomorrow ? null : now, role);
  if (!tip) return null;
  return <aside className="mascot-tip surface" aria-live="polite">
    <Mascot size={88} look={look} mood={tip.mood} pose={tip.pose} extra={tip.extra} className="mascot-tip-figure" />
    <div className="mascot-tip-body">
      <p className="mascot-tip-name">Парыч</p>
      <p className="mascot-tip-text">{tip.text.split('**').map((part, index) => index % 2 ? <b key={index}>{part}</b> : part)}</p>
    </div>
  </aside>;
}
