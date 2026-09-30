import React, { useEffect, useState } from 'react';
import { Search } from 'lucide-react';
import { Header } from '../components/Header';
import { useApp } from '../context/AppContext';
import { useTheme } from '../context/ThemeContext';
import { selectedWeekDate } from '../utils/calendar';
import { api } from '../api/client';
import type { ClassroomsResponse } from '../types';

const SLOTS = [1, 2, 3, 4, 5, 6];
const DAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница'];

export function ClassroomsView() {
  const { info } = useApp();
  const { currentThemeDef } = useTheme();

  const [day, setDay] = useState(info.todayName || DAYS[0]);
  const [slot, setSlot] = useState(2);
  const [query, setQuery] = useState('');
  const [data, setData] = useState<ClassroomsResponse | null>(null);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);

  const weekType = info.isRedWeek ? 'red' : 'blue';

  const slotTimes: Record<number, string> = {
    1: '08:30 — 10:05',
    2: '10:15 — 11:50',
    3: '12:20 — 13:55',
    4: '14:30 — 16:00',
    5: '16:10 — 17:40',
    6: '17:50 — 19:20',
  };

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api.getFreeClassrooms(day, slot, weekType)
      .then((value) => { if (active) setData(value); })
      .catch(() => { if (active) setError('Не удалось загрузить аудитории.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [day, slot, weekType]);

  const free = (data?.freeRooms || []).filter((room) =>
    room.toLowerCase().includes(query.toLowerCase())
  );
  const occupied = (data?.occupiedRooms || []).filter((room) =>
    [room.room, room.teacher, room.subject, room.groupName].some((value) =>
      value.toLowerCase().includes(query.toLowerCase())
    )
  );

  const selectedDate = selectedWeekDate(day, info.serverTime);

  return (
    <div className="page animate-fade-in select-none">
      <Header title="Свободные аудитории" showGreeting={false} />

      {/* Days chips row */}
      <div className="flex gap-1.5 mb-3 overflow-x-auto no-scrollbar">
        {DAYS.map((name) => (
          <button
            key={name}
            onClick={() => setDay(name)}
            className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition active:scale-95 ${
              day === name
                ? 'bg-blue-600 text-white font-bold shadow-md shadow-blue-500/25'
                : 'surface opacity-75'
            }`}
          >
            {name}
          </button>
        ))}
      </div>

      {/* Slot Numbers: [ 1 ] [ 2 ] [ 3 ] [ 4 ] [ 5 ] [ 6 ] */}
      <div className="grid grid-cols-6 gap-2 mb-4">
        {SLOTS.map((s) => {
          const isSelected = slot === s;
          return (
            <button
              key={s}
              onClick={() => setSlot(s)}
              className={`py-3 rounded-2xl text-base font-black font-unbounded transition active:scale-95 ${
                isSelected
                  ? 'bg-blue-500 text-white shadow-lg shadow-blue-500/35 ring-2 ring-blue-400'
                  : 'surface opacity-80 hover:opacity-100'
              }`}
            >
              {s}
            </button>
          );
        })}
      </div>

      {/* Time & Slot Range Slider Card (matching reference media_1790757520750.png) */}
      <div className="surface rounded-3xl p-4 mb-3">
        <div className="text-center font-black font-unbounded text-base tracking-wide text-theme-text mb-2.5">
          {slotTimes[slot] || '08:30 — 18:40'}
        </div>

        {/* Progress track */}
        <div className="relative w-full h-2 bg-white/10 rounded-full overflow-hidden mb-1">
          <div
            className="absolute top-0 bottom-0 left-0 bg-blue-500 rounded-full transition-all duration-300 shadow-[0_0_12px_rgba(59,130,246,0.8)]"
            style={{ width: `${(slot / 6) * 100}%` }}
          />
        </div>
        <div className="flex justify-between items-center text-[10px] font-semibold opacity-50 px-1">
          <span>1 пара (08:30)</span>
          <span>{slot} пара</span>
          <span>6 пара (19:20)</span>
        </div>
      </div>

      {/* Date Card (matching reference media_1790757520750.png) */}
      <div className="surface rounded-2xl p-3.5 mb-4 flex items-center justify-between">
        <span className="text-xs font-black font-unbounded uppercase tracking-wider text-theme-subtext">
          ДАТА
        </span>
        <span className="px-3.5 py-1 rounded-xl bg-white/5 border border-white/10 text-xs font-black font-unbounded text-theme-text">
          {selectedDate}
        </span>
      </div>

      {/* Search Input */}
      <div className="relative mb-5">
        <Search size={16} className="absolute left-3.5 top-3.5 text-theme-subtext opacity-60" />
        <input
          aria-label="Поиск аудитории"
          className="field !pl-10 text-xs font-semibold rounded-2xl"
          placeholder="Поиск по номеру аудитории или предмету..."
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </div>

      {error ? (
        <p role="alert" className="notice error-notice">{error}</p>
      ) : loading ? (
        <p className="text-sm text-theme-subtext py-8 text-center font-medium">Загружаем данные аудиторий…</p>
      ) : (
        <>
          {/* SECTION: СВОБОДНО (matching blue-bordered badge grid in reference) */}
          <div className="mb-6">
            <h2 className="text-xs font-black font-unbounded uppercase tracking-wider text-theme-subtext opacity-80 mb-3 flex items-center gap-1.5">
              <span>СВОБОДНО</span>
              <span className="opacity-40">·</span>
              <span className="text-blue-400">{free.length}</span>
            </h2>

            {free.length ? (
              <div className="grid grid-cols-4 sm:grid-cols-5 gap-2.5">
                {free.map((room) => (
                  <div
                    key={room}
                    className="py-2.5 px-2 rounded-2xl border-2 border-blue-500/70 text-blue-400 bg-blue-500/10 font-black text-lg font-unbounded flex items-center justify-center shadow-[0_0_15px_rgba(59,130,246,0.15)] active:scale-95 transition cursor-default"
                  >
                    {room}
                  </div>
                ))}
              </div>
            ) : (
              <div className="surface rounded-2xl p-4 text-center text-xs opacity-60">
                На эту пару свободных аудиторий не найдено
              </div>
            )}
          </div>

          {/* SECTION: ЗАНЯТО */}
          <div>
            <h2 className="text-xs font-black font-unbounded uppercase tracking-wider text-theme-subtext opacity-80 mb-3 flex items-center gap-1.5">
              <span>ЗАНЯТО</span>
              <span className="opacity-40">·</span>
              <span>{occupied.length}</span>
            </h2>

            <div className="space-y-2 room-results">
              {occupied.map((room, idx) => {
                const endTime = room.time?.includes('-') ? room.time.split('-')[1].trim() : '14:30';
                return (
                  <div
                    key={`${room.room}-${idx}`}
                    className="surface rounded-2xl p-3.5 flex items-center justify-between transition"
                  >
                    <div className="flex items-center gap-3 min-w-0">
                      <div className="text-lg font-black font-unbounded text-theme-text min-w-[52px]">
                        {room.room}
                      </div>
                      <div className="min-w-0">
                        <div className="text-xs font-bold text-theme-text truncate">
                          {room.subject}
                        </div>
                        <div className="text-[11px] text-theme-subtext opacity-70 truncate mt-0.5">
                          {room.teacher} · {room.groupName}
                        </div>
                      </div>
                    </div>

                    <div className="text-right whitespace-nowrap pl-2 text-[11px] font-semibold opacity-65">
                      до {endTime}
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
