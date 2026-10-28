const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { execFileSync } = require('node:child_process');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'konyaevo-helpers-'));
process.on('exit', () => fs.rmSync(output, { recursive: true, force: true }));
execFileSync(process.execPath, [require.resolve('typescript/bin/tsc'),
  path.join(__dirname, '../src/utils/calendar.ts'), path.join(__dirname, '../src/utils/requestCache.ts'),
  path.join(__dirname, '../src/utils/mascotTip.ts'), path.join(__dirname, '../src/utils/snapshot.ts'),
  path.join(__dirname, '../src/utils/pagination.ts'),
  '--target', 'ES2020', '--module', 'commonjs', '--rootDir', path.join(__dirname, '../src'), '--outDir', output, '--skipLibCheck'], { stdio: 'inherit' });
const { selectedWeekDate, moscowDayDate } = require(path.join(output, 'utils', 'calendar.js'));
const { RequestCache } = require(path.join(output, 'utils', 'requestCache.js'));
const { mascotTip } = require(path.join(output, 'utils', 'mascotTip.js'));
const { snapshotFile, nameHash, weekType, liveInfo } = require(path.join(output, 'utils', 'snapshot.js'));
const { pageWindow, pageRange } = require(path.join(output, 'utils', 'pagination.js'));

async function main() {
  assert.equal(selectedWeekDate('Понедельник', '2026-09-30T12:00:00+03:00'), '28.09.2026');
  assert.equal(selectedWeekDate('Пятница', '2026-09-30T12:00:00+03:00'), '02.10.2026');
  assert.equal(selectedWeekDate('Понедельник', '2026-09-27T21:30:00Z'), '28.09.2026');
  assert.equal(selectedWeekDate('Пятница', '2027-01-01T00:15:00+03:00'), '01.01.2027');
  assert.equal(selectedWeekDate('Суббота', '2027-01-01T00:15:00+03:00'), '02.01.2027');
  assert.equal(selectedWeekDate('Другой день', '2026-09-30T12:00:00+03:00'), '');
  assert.equal(selectedWeekDate('Понедельник', '2026-09-30T12:00:00+03:00', 1), '05.10.2026');
  assert.equal(moscowDayDate('2026-09-30T22:30:00Z'), '01.10.2026');
  assert.equal(moscowDayDate('2026-12-31T20:00:00Z', 1), '01.01.2027');
  assert.equal(moscowDayDate('2026-10-04T12:00:00+03:00', 1), '05.10.2026');
  let now = 0, calls = 0;
  const cache = new RequestCache(() => now);
  const fetch = async () => ++calls;
  const first = cache.load('groups', 100, fetch);
  assert.equal(first, cache.load('groups', 100, fetch));
  assert.equal(await first, 1);
  assert.equal(await cache.load('groups', 100, fetch), 1);
  now = 101;
  assert.equal(await cache.load('groups', 100, fetch), 2);
  cache.clear();
  assert.equal(await cache.load('groups', 100, fetch), 3);
  await assert.rejects(cache.load('bad', 100, async () => { throw new Error('network'); }));
  assert.equal(await cache.load('bad', 100, async () => 'retry'), 'retry');
  let resolveOld;
  const old = cache.load('teachers', 100, () => new Promise(resolve => { resolveOld = resolve; }));
  await Promise.resolve();
  cache.clear();
  assert.equal(await cache.load('teachers', 100, async () => 'new'), 'new');
  resolveOld('old'); await old;
  assert.equal(await cache.load('teachers', 100, async () => 'unexpected'), 'new');
  const at = (h, m) => h * 60 + m;
  const day = [
    { lessonNumber: 1, time: '08:30-10:00', subject: 'МДК 01.01', room: '305', type: 'Пара' },
    { lessonNumber: 2, time: '10:10 – 11:40', subject: 'Математика', room: '214', type: 'Пара' },
    { lessonNumber: 2, time: '10:10-11:40', subject: 'Математика', room: '214', type: 'Пара' },
    { lessonNumber: 3, time: '12:10-13:40', subject: 'Физика', subgroups: [{ number: 1, teacher: 'А', room: '101' }, { number: 2, teacher: 'Б', room: '102' }], type: 'Замена' },
    { lessonNumber: 4, time: '13:50-15:20', subject: 'Отменённая', room: '1', type: 'Отмена', canceled: true },
  ];
  assert.equal(mascotTip([], at(9, 0), 'student'), null);
  assert.equal(mascotTip(day, at(7, 0), 'student').text, 'Первая пара в **08:30** — МДК 01.01 в **каб. 305**.');
  assert.equal(mascotTip(day, at(8, 15), 'student').text, 'Через **15 мин** — МДК 01.01 в **каб. 305**. Успеваешь?');
  assert.equal(mascotTip(day, at(8, 15), 'student').mood, 'hurry');
  assert.equal(mascotTip(day, at(9, 0), 'student').text, 'Дальше — Математика в **каб. 214** в **10:10**.');
  assert.equal(mascotTip(day, at(11, 0), 'student').text, 'Дальше — Физика в **каб. 101 / 102** в **12:10**. Это **замена**.');
  assert.equal(mascotTip(day, at(13, 0), 'student').text, 'Это последняя пара на сегодня. В **13:40** — свобода!');
  assert.equal(mascotTip(day, at(16, 0), 'student').pose, 'wave');
  assert.equal(mascotTip(day, null, 'student').text, 'Завтра 3 пары, первая в **08:30** — МДК 01.01 в **каб. 305**.');
  const gap = [day[0], { ...day[3], time: '12:10-13:40', type: 'Пара' }];
  assert.equal(mascotTip(gap, at(10, 30), 'student').text, 'Перерыв до **12:10**. Дальше — Физика в **каб. 101 / 102**.');
  assert.equal(mascotTip([{ ...day[0], groupName: 'ИСп-21' }], at(8, 25), 'teacher').text, 'Через **5 мин** — МДК 01.01 у ИСп-21 в **каб. 305**. Успеваешь?');
  assert.equal(mascotTip([day[0]], null, 'student').text, 'Завтра 1 пара, первая в **08:30** — МДК 01.01 в **каб. 305**.');
  // Копия расписания: пути API и пересчёт дня по часам телефона.
  assert.equal(snapshotFile('/info'), 'info.json');
  assert.equal(snapshotFile('/groups'), 'groups.json');
  assert.equal(snapshotFile(`/schedule/group/${encodeURIComponent('ИСп-21')}`), `g/${nameHash('ИСп-21')}.json`);
  assert.equal(snapshotFile(`/schedule/base/teacher/${encodeURIComponent('Иванова А.С.')}`), `bt/${nameHash('Иванова А.С.')}.json`);
  assert.equal(snapshotFile(`/changes/group/${encodeURIComponent('1-БД')}`), `c/${nameHash('1-БД')}.json`);
  assert.equal(snapshotFile(`/classrooms/free?day=${encodeURIComponent('Среда')}&slot=2&weekType=red`), 'rooms/2-2-red.json');
  assert.equal(snapshotFile('/classrooms/free?day=Суббота&slot=2&weekType=red'), 'rooms/5-2-red.json');
  assert.equal(snapshotFile('/classrooms/free?day=Среда&slot=2&weekType=current'), null);
  assert.equal(snapshotFile('/profile'), null);
  assert.equal(snapshotFile('/mascot/event'), null);
  assert.equal(nameHash('ИСп-21'), nameHash('ИСп-21'));
  assert.notEqual(nameHash('ИСп-21'), nameHash('ИСп-22'));
  // Совпадает с AcademicWeek на сервере: 28.10.2026 — красная, 2.11 — синяя, 2.02.2027 — красная (начало семестра).
  assert.equal(weekType(new Date(Date.UTC(2026, 9, 28))), 'red');
  assert.equal(weekType(new Date(Date.UTC(2026, 10, 2))), 'blue');
  assert.equal(weekType(new Date(Date.UTC(2027, 1, 2))), 'red');
  assert.equal(weekType(new Date(Date.UTC(2027, 0, 15))), weekType(new Date(Date.UTC(2027, 0, 11))));
  const wednesday = liveInfo({ changesDate: '28.10' }, new Date('2026-10-28T08:00:00Z'));
  assert.deepEqual([wednesday.todayName, wednesday.tomorrowName, wednesday.isRedWeek, wednesday.tomorrowRedWeek, wednesday.changesDate],
    ['Среда', 'Четверг', true, true, '28.10']);
  assert.equal(liveInfo({ changesDate: '' }, new Date('2026-10-28T21:30:00Z')).todayName, 'Четверг');
  const saturday = liveInfo({ changesDate: '' }, new Date('2026-10-31T10:00:00Z'));
  assert.deepEqual([saturday.todayName, saturday.tomorrowName], ['', '']);
  assert.equal(liveInfo({ changesDate: '' }, new Date('2026-11-01T10:00:00Z')).tomorrowName, 'Понедельник');
  assert.equal(liveInfo({ changesDate: '' }, new Date('2026-11-01T10:00:00Z')).tomorrowRedWeek, false);
  assert.deepEqual(pageWindow(0, 1), [0]);
  assert.deepEqual(pageWindow(3, 7), [0, 1, 2, 3, 4, 5, 6]);
  assert.deepEqual(pageWindow(0, 10), [0, 1, 2, 3, null, 9]);
  assert.deepEqual(pageWindow(5, 10), [0, null, 4, 5, 6, null, 9]);
  assert.deepEqual(pageWindow(9, 10), [0, null, 6, 7, 8, 9]);
  assert.deepEqual(pageWindow(2, 8), [0, 1, 2, 3, null, 7]);
  assert.deepEqual(pageWindow(3, 10), [0, 1, 2, 3, 4, null, 9]);
  assert.equal(pageRange(1, 12, 75), '13–24 из 75');
  assert.equal(pageRange(6, 12, 75), '73–75 из 75');
  console.log('Frontend helpers: даты выбранной недели, кеш, повторный запрос, сброс, подсказки маскота, копия расписания и страницы списков — проверки пройдены.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
