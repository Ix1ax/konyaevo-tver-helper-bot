// Выгружает копию открытых данных API в статические файлы для GitHub Pages.
// Запуск: SNAPSHOT_API_URL=https://… PREVIOUS_SNAPSHOT_URL=https://…/data/ node scripts/snapshot.cjs dist/data
// Если API во время сборки недоступен, переносится прошлая опубликованная копия, чтобы деплой её не стёр.
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { execFileSync } = require('node:child_process');

const compiled = fs.mkdtempSync(path.join(os.tmpdir(), 'konyaevo-snapshot-'));
process.on('exit', () => fs.rmSync(compiled, { recursive: true, force: true }));
execFileSync(process.execPath, [require.resolve('typescript/bin/tsc'), path.join(__dirname, '../src/utils/snapshot.ts'),
  '--target', 'ES2020', '--module', 'commonjs', '--rootDir', path.join(__dirname, '../src'), '--outDir', compiled, '--skipLibCheck'], { stdio: 'inherit' });
const { snapshotFile } = require(path.join(compiled, 'utils', 'snapshot.js'));

const base = (process.env.SNAPSHOT_API_URL || '').trim().replace(/\/$/, '');
if (!/^https?:\/\//.test(base)) { console.error('Укажите SNAPSHOT_API_URL.'); process.exit(1); }
const api = base.endsWith('/api') ? base : `${base}/api`;
const out = path.resolve(process.argv[2] || 'dist/data');
const DAYS = ['Понедельник', 'Вторник', 'Среда', 'Четверг', 'Пятница'];

async function get(apiPath) {
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      const response = await fetch(api + apiPath, { signal: AbortSignal.timeout(20000) });
      if (response.ok) return await response.json();
      if (response.status < 500) return null;
    } catch { /* Повторим ниже. */ }
    await new Promise(resolve => setTimeout(resolve, 1000 * (attempt + 1)));
  }
  return null;
}

const written = [];
function writeFile(relative, body) {
  const file = path.join(out, relative);
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, body);
  written.push(relative);
}
const save = (apiPath, data) => writeFile(snapshotFile(apiPath), JSON.stringify(data));

async function restorePrevious(reason) {
  const previous = (process.env.PREVIOUS_SNAPSHOT_URL || '').replace(/\/?$/, '/');
  console.error(reason);
  if (previous === '/') process.exit(1);
  const index = await fetch(previous + 'index.json').then(r => r.ok ? r.json() : null).catch(() => null);
  if (!index) { console.error('Прошлой копии нет.'); process.exit(1); }
  fs.rmSync(out, { recursive: true, force: true });
  written.length = 0;
  for (const relative of index.filter(item => item !== 'index.json')) {
    const response = await fetch(previous + relative).catch(() => null);
    if (response?.ok) writeFile(relative, Buffer.from(await response.arrayBuffer()));
  }
  writeFile('index.json', JSON.stringify([...written, 'index.json']));
  console.log(`Перенесена прошлая копия: ${written.length} файлов.`);
  process.exit(0);
}

async function main() {
  const [info, groups, teachers] = await Promise.all([get('/info'), get('/groups'), get('/teachers')]);
  if (!info || !groups || !teachers) return restorePrevious('API не отдал справочники.');
  fs.rmSync(out, { recursive: true, force: true });
  save('/info', info); save('/groups', groups); save('/teachers', teachers);

  const e = encodeURIComponent;
  const paths = [];
  for (const group of Object.values(groups.groupsByCourse).flat())
    paths.push(`/schedule/group/${e(group)}`, `/schedule/base/group/${e(group)}`, `/changes/group/${e(group)}`);
  for (const teacher of teachers.teachers) paths.push(`/schedule/teacher/${e(teacher)}`, `/schedule/base/teacher/${e(teacher)}`);
  for (const day of DAYS) for (let slot = 1; slot <= 6; slot++) for (const week of ['red', 'blue'])
    paths.push(`/classrooms/free?day=${e(day)}&slot=${slot}&weekType=${week}`);

  // Немного параллельности: копия собирается за минуту и не нагружает сервер.
  let failed = 0, next = 0;
  await Promise.all(Array.from({ length: 6 }, async () => {
    while (next < paths.length) {
      const apiPath = paths[next++];
      const data = await get(apiPath);
      if (data === null) { failed++; console.warn('Пропущено:', apiPath); } else save(apiPath, data);
    }
  }));
  if (failed > paths.length * 0.2) return restorePrevious(`API не отдал ${failed} из ${paths.length} файлов.`);
  writeFile('meta.json', JSON.stringify({ generatedAt: new Date().toISOString(), failed }));
  writeFile('index.json', JSON.stringify([...written, 'index.json']));
  console.log(`Копия расписания: ${written.length} файлов, пропущено ${failed}.`);
}

main().catch(error => { console.error(error); process.exit(1); });
