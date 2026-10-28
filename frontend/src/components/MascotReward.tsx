import { useEffect } from 'react';
import { Gift, Snowflake } from 'lucide-react';
import { useMascot } from '../context/MascotContext';
import { useTelegram } from '../hooks/useTelegram';
import { Mascot } from './Mascot';

const CONFETTI = [['#3d5afe', 8, 12, 20], ['#ffb347', 86, 8, -30], ['#7d8fff', 92, 38, 40], ['#ff6a2b', 6, 46, -50], ['#ffe08a', 72, 22, 15], ['#3d5afe', 24, 6, 60]] as const;

/** Награда за серию или новый уровень; показывается один раз и подтверждается на сервере. */
export function MascotReward() {
  const { mascot, acknowledgeRewards, updateSettings } = useMascot();
  const { haptic } = useTelegram();
  const rewards = mascot?.rewards ?? [];
  const streak = rewards.find(reward => reward.type === 'streak');
  const level = rewards.find(reward => reward.type === 'level');
  const donation = rewards.find(reward => reward.type === 'donation');
  useEffect(() => { if (rewards.length) haptic.notification('success'); }, [rewards.length]);
  if (!mascot || !rewards.length) return null;

  // Облик за поддержку бот уже надел — кнопка «Надеть» не нужна.
  const gift = donation?.look ? mascot.looks.find(item => item.id === donation.look) : undefined;
  const look = level?.look ? mascot.looks.find(item => item.id === level.look) : undefined;
  if (donation) return <div className="share-overlay" role="dialog" aria-modal="true" aria-labelledby="mascot-reward-title">
    <div className="share-dialog mascot-reward support-reward">
      {CONFETTI.map(([color, x, y, angle], index) => <i key={index} className="mascot-confetti" style={{ background: color, left: `${x}%`, top: `${y}%`, transform: `rotate(${angle}deg)` }} />)}
      <Mascot size={170} look={gift?.id ?? mascot.look} mood="proud" pose="wave" extra={['sparkles']} className="mx-auto" />
      <h2 id="mascot-reward-title" className="font-unbounded text-2xl font-black mt-1">Спасибо!</h2>
      <p className="text-sm text-theme-subtext leading-relaxed mt-2">
        Ты поддержал Парыча на {donation.value} ⭐. {gift ? `Парыч получил облик «${gift.name}» и уже его надел.` : 'Парыч передаёт огромный привет!'}
      </p>
      <div className="mascot-reward-chips">
        <span className="font-unbounded">{donation.value} ⭐</span>
        {gift && <span className="mascot-reward-look"><Gift size={14} />Облик «{gift.name}»</span>}
      </div>
      <button className="button-primary w-full" onClick={acknowledgeRewards}>Ура!</button>
    </div>
  </div>;
  const wear = () => { if (look) updateSettings({ look: look.id }).catch(() => { /* Облик можно выбрать на экране Парыча. */ }); acknowledgeRewards(); };

  return <div className="share-overlay" role="dialog" aria-modal="true" aria-labelledby="mascot-reward-title">
    <div className="share-dialog mascot-reward">
      {CONFETTI.map(([color, x, y, angle], index) => <i key={index} className="mascot-confetti" style={{ background: color, left: `${x}%`, top: `${y}%`, transform: `rotate(${angle}deg)` }} />)}
      <Mascot size={160} look={look?.id ?? mascot.look} mood="happy" pose="wave" extra={streak ? ['flame', 'sparkles'] : ['sparkles']} className="mx-auto" />
      <h2 id="mascot-reward-title" className="font-unbounded text-2xl font-black mt-1">{streak ? `${streak.value} дней подряд!` : `Уровень ${level?.value}!`}</h2>
      <p className="text-sm text-theme-subtext leading-relaxed mt-2">
        {streak ? 'Огонёк разгорается: ни одного пропущенного учебного дня. Держи награду.' : `Теперь ты ${mascot.levelName.toLowerCase()}. Парыч гордится!`}
      </p>
      <div className="mascot-reward-chips">
        {streak && <><span className="font-unbounded">+50 XP</span><span><Snowflake size={14} />+1 заморозка</span></>}
        {level && <span className="font-unbounded">Ур. {level.value}</span>}
        {look && <span className="mascot-reward-look"><Gift size={14} />Облик «{look.name}»</span>}
      </div>
      {look ? <div className="grid grid-cols-2 gap-2">
        <button className="button-quiet" onClick={acknowledgeRewards}>Позже</button>
        <button className="button-primary" onClick={wear}>Надеть</button>
      </div> : <button className="button-primary w-full" onClick={acknowledgeRewards}>Забрать</button>}
    </div>
  </div>;
}
