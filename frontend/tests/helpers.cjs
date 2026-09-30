const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const { execFileSync } = require('node:child_process');
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'konyaevo-helpers-'));
process.on('exit', () => fs.rmSync(output, { recursive: true, force: true }));
execFileSync(process.execPath, [require.resolve('typescript/bin/tsc'),
  path.join(__dirname, '../src/utils/calendar.ts'), path.join(__dirname, '../src/utils/requestCache.ts'),
  '--target', 'ES2020', '--module', 'commonjs', '--outDir', output, '--skipLibCheck'], { stdio: 'inherit' });
const { selectedWeekDate } = require(path.join(output, 'calendar.js'));
const { RequestCache } = require(path.join(output, 'requestCache.js'));

async function main() {
  assert.equal(selectedWeekDate('Понедельник', '2026-09-30T12:00:00+03:00'), '28.09.2026');
  assert.equal(selectedWeekDate('Пятница', '2026-09-30T12:00:00+03:00'), '02.10.2026');
  assert.equal(selectedWeekDate('Понедельник', '2026-09-27T21:30:00Z'), '28.09.2026');
  assert.equal(selectedWeekDate('Пятница', '2027-01-01T00:15:00+03:00'), '01.01.2027');
  assert.equal(selectedWeekDate('Суббота', '2027-01-01T00:15:00+03:00'), '02.01.2027');
  assert.equal(selectedWeekDate('Другой день', '2026-09-30T12:00:00+03:00'), '');
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
  console.log('Frontend helpers: даты выбранной недели, кеш, повторный запрос и сброс — проверки пройдены.');
}
main().catch(error => { console.error(error); process.exitCode = 1; });
