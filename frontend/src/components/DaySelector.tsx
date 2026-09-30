export function DaySelector({ days, selectedDay, onSelectDay, todayName }: {
  days: string[]; selectedDay: string; onSelectDay: (day: string) => void; todayName: string;
}) {
  const names = ['Пн', 'Вт', 'Ср', 'Чт', 'Пт'];
  return <div className="flex gap-2 mb-2" aria-label="День недели">
    {days.map((day, index) => <button key={day} className="choice flex-1 !px-1"
      aria-pressed={selectedDay === day} aria-label={`${day}${day === todayName ? ', сегодня' : ''}`}
      onClick={() => onSelectDay(day)}>{names[index] || day}</button>)}
  </div>;
}
