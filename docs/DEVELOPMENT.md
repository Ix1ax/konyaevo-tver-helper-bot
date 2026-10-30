# Коняево — документация для разработки

Описание проекта для пользователей — в [README](../README.md).

Telegram-бот [@konyaevo_tver_helper_bot](https://t.me/konyaevo_tver_helper_bot) и Mini App
с расписанием занятий и заменами. Данные загружаются из Google Таблиц колледжа и обновляются
в фоне. В приложении живёт маскот Парыч: подсказывает, когда и куда идти, ведёт огонёк
учебных дней и открывает облики за опыт и за поддержку звёздами.

- Mini App: <https://ix1ax.github.io/konyaevo-tver-helper-bot/>
- API: `https://konyaevo-api.ixlax.space/api`
- Текущая версия: **2.2.5**

## Содержание

- [Что умеет](#что-умеет)
- [Как устроено](#как-устроено)
- [Быстрый старт](#быстрый-старт)
- [Переменные окружения](#переменные-окружения)
- [Mini App: сборка и публикация](#mini-app-сборка-и-публикация)
- [Продакшен-сервер](#продакшен-сервер)
- [Маршруты до Telegram и VPN](#маршруты-до-telegram-и-vpn)
- [Доступность Mini App](#доступность-mini-app)
- [Парыч и огонёк](#парыч-и-огонёк)
- [Поддержка Парыча звёздами](#поддержка-парыча-звёздами)
- [Администрирование](#администрирование)
- [REST API](#rest-api)
- [Статистика из базы](#статистика-из-базы)
- [Проверки и тесты](#проверки-и-тесты)
- [Структура проекта](#структура-проекта)
- [Решение проблем](#решение-проблем)
- [История версий](#история-версий)
- [Лицензия](#лицензия)

## Что умеет

### Mini App

- **Расписание**: вкладка с переключателем «Сегодня / Завтра», сводка дня («начнёшь в… ·
  закончишь в… · N пар»), текущая пара подсвечена, замены и отмены учтены. «Неделя» —
  основное расписание по дням.
- **Поиск**: преподаватели (алфавитный фильтр, поиск, полное расписание) и свободные
  или занятые аудитории по дню и паре. Длинные списки разбиты на страницы, аудитории
  отсортированы по номеру.
- **Парыч** в центре нижней панели: подсказка над расписанием, огонёк, задания, уровни,
  облики, награды (см. [Парыч и огонёк](#парыч-и-огонёк)).
- **Поделиться расписанием**: картинка на сегодня или завтра — отправить в чат Telegram
  или сохранить.
- **Профиль**: роль, группа или преподаватель, уведомления (общие с ботом), тема, помощь.
- Светлая и тёмная темы Liquid Glass, тактильная отдача, работа при недоступном сервере
  (см. [Доступность Mini App](#доступность-mini-app)).

### Чат-бот

Интерфейс построен на inline-кнопках и редактирует одно сообщение, чтобы не засорять чат.

```
[📱 Открыть приложение]
[📅 Сегодня] [⏭ Завтра] [🗓 Неделя]
[🔄 Замены] [⚙️ Ещё]
[⭐ Поддержать Парыча]
```

«⚙️ Ещё» — уведомления, смена группы, преподавателя или роли и справка.

| Команда | Что делает |
|---|---|
| `/start` | Меню; при первом запуске — выбор роли, курса и группы или преподавателя |
| `/help` | Справка |
| `/cancel` | Отменить ввод (время уведомления, своя сумма, рассылка) и вернуться в меню |
| `/donate` | Поддержать Парыча звёздами (также ссылка `t.me/<бот>?start=donate`) |
| `/paysupport` | Вопросы по оплате; `/paysupport текст` пересылает вопрос администраторам |
| `/admin`, `/stats`, `/refresh`, `/broadcast` | Только администраторам, см. [Администрирование](#администрирование) |

Уведомления: замены и расписание на завтра в выбранное время по Москве и в выбранные дни.
Напоминание об огоньке приходит в 21:00 по будням, только когда серия может прерваться.

## Как устроено

```
Google Таблицы ──► Spring Boot (Java 17) ──► H2 (файл ./data)
                    │   ├─ REST API /api/*  ◄── nginx (HTTPS) ◄── Mini App (GitHub Pages)
                    │   └─ Telegram-бот (long polling)                 │
                    │        ├─ основной маршрут: Cloudflare Worker    └─ резервно: тот же Worker /api/*
                    │        └─ запасной: api.telegram.org через xray     и копия расписания ./data/
                    └─ ImageKit (картинки «Поделиться»)
```

- **Бэкенд**: Java 17, Spring Boot 3.2 (REST API и бот в одном процессе), H2,
  Apache POI, telegrambots 6.9. Расписание обновляется каждые 15 минут, замены — каждые 10.
- **Mini App**: React 18, Vite, TypeScript, Tailwind CSS, Lucide, Telegram WebApp SDK.
  Публикуется на GitHub Pages workflow `.github/workflows/deploy-pages.yml`.
- **Инфраструктура**: nginx с HTTPS перед портом `8090`, systemd-сервис `konyaevo-bot`,
  Cloudflare Worker (`deploy/cloudflare-worker`) и локальный xray (`deploy/xray`).

## Быстрый старт

Нужны Java 17 и Node.js 20.

```bash
cp .env.example .env          # впишите BOT_TOKEN и остальное, см. ниже
./mvnw clean package -DskipTests
./start.sh                    # Windows: start.bat
```

`start.sh` загружает `.env`, берёт первый `target/konyaevo-bot-*.jar` и запускает его
с `-Duser.timezone=Europe/Moscow`. Напрямую: `java -jar target/konyaevo-bot-2.2.5.jar`.

Mini App локально:

```bash
cd frontend
npm install
npm run dev                   # http://localhost:5173, /api проксируется на 127.0.0.1:8090
```

> Не запускайте локальную копию с тем же реальным токеном, что и на сервере: Telegram
> отдаёт обновления только одному опросу, бот на сервере перестанет получать сообщения.

## Переменные окружения

Бэкенд читает `.env` из рабочего каталога (spring-dotenv) и переменные окружения
(`start.sh` загружает `.env` через `source`). Кавычки вокруг значений бэкенд снимает
сам, но `start.sh` выполняет файл как bash, поэтому значения с `&`, `#`, пробелами
берите в одинарные кавычки.

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `BOT_TOKEN` | — | Токен бота от @BotFather. **Обязательно** |
| `BOT_USERNAME` | `konyaevo_tver_helper_bot` | Имя бота |
| `BOT_ADMIN_IDS` | `1669683599` | Telegram ID администраторов через запятую |
| `BOT_BASE_URL` | `https://api.telegram.org/bot` | Основной маршрут до Bot API (например, Cloudflare Worker `https://…workers.dev/bot`) |
| `BOT_PROXY_TYPE`, `BOT_PROXY_HOST`, `BOT_PROXY_PORT` | `NO_PROXY`, `127.0.0.1`, `10808` | Прокси основного маршрута: `NO_PROXY`, `SOCKS5`, `SOCKS4`, `HTTP` |
| `BOT_FALLBACK_BASE_URL` | пусто | Запасной маршрут; если задан только тип прокси — `api.telegram.org` |
| `BOT_FALLBACK_PROXY_TYPE`, `…_HOST`, `…_PORT` | `NO_PROXY`, `127.0.0.1`, `10808` | Прокси запасного маршрута (обычно локальный xray, `SOCKS5`) |
| `BOT_MAX_THREADS` | `16` | Параллельная обработка чатов (4–64); сообщения одного чата идут по порядку |
| `VPN_URL` | пусто | Ссылка `vless://` для локального xray, **в одинарных кавычках**; применяется `deploy/xray/set-vpn.sh` |
| `SERVER_PORT` | `8090` | Порт REST API |
| `DB_URL` | `jdbc:h2:file:./data/konyaevo-bot` | Файл базы H2 |
| `DB_PASSWORD` | пусто | Пароль базы |
| `MINI_APP_URL` | пусто | Адрес Mini App: кнопка «📱 Открыть приложение» в боте |
| `IMAGEKIT_PRIVATE_KEY`, `IMAGEKIT_URL_ENDPOINT` | пусто | Хранилище картинок «Поделиться» (без ключа картинки отдаёт сам бэкенд). Секрет только в `.env` бэкенда, никогда в `VITE_*` |
| `SHARE_PUBLIC_API_URL` | `https://konyaevo-api.ixlax.space/api` | Публичный адрес API для картинок «Поделиться» без ImageKit |

Переменные Mini App — это переменные репозитория на GitHub (Settings → Secrets and
variables → Actions → Variables), они попадают в сборку:

| Переменная | Назначение |
|---|---|
| `VITE_API_URL` | HTTPS-адрес API, например `https://konyaevo-api.ixlax.space/api`. Обязательно |
| `VITE_API_FALLBACK_URL` | Резервный адрес API (Cloudflare Worker), например `https://konyaevo-tver-helper-bot.ixlax.workers.dev/api` |

## Mini App: сборка и публикация

Workflow `deploy-pages.yml` собирает `frontend/` и публикует его на GitHub Pages:
при пуше в `main` с изменениями во `frontend/**`, вручную (Run workflow) и каждые
30 минут — по расписанию обновляется только копия расписания (`frontend/scripts/snapshot.cjs`,
см. [Доступность Mini App](#доступность-mini-app)).

Подключение к боту: укажите адрес в `MINI_APP_URL` и перезапустите бота. Постоянную
кнопку меню можно задать в @BotFather командой `/setmenubutton`.

Альтернатива — Cloudflare Pages: корень `frontend`, команда `npm run build`, каталог `dist`.
`frontend/functions/api/[[path]].ts` (Pages Functions) проксирует `/api` на `BACKEND_URL`.

## Продакшен-сервер

Бот работает на Linux-сервере в `/opt/konyaevo-tver-helper-bot` (клон репозитория)
как systemd-сервис:

```ini
# /etc/systemd/system/konyaevo-bot.service
[Unit]
Description=Konyaevo Telegram Schedule Bot
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=/opt/konyaevo-tver-helper-bot
ExecStart=/opt/konyaevo-tver-helper-bot/start.sh
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
```

nginx принимает HTTPS на `konyaevo-api.ixlax.space` и проксирует на `http://127.0.0.1:8090`.

### Обновление

```bash
cd /opt/konyaevo-tver-helper-bot
git pull
systemctl stop konyaevo-bot
tar czf /opt/db_backups/konyaevo-$(date +%F).tgz data .env   # перед изменением схемы
./mvnw clean package -DskipTests
systemctl start konyaevo-bot
```

`clean` удаляет старый JAR, поэтому `start.sh` гарантированно возьмёт новый. Схему базы
бэкенд расширяет сам (`ddl-auto: update`); файл H2 копируйте только при остановленном боте.

### Проверка

```bash
curl -s https://konyaevo-api.ixlax.space/api/health     # {"api":"ok","telegram":"ok","route":"primary"}
journalctl -u konyaevo-bot -n 80 --no-pager              # «Started KonyaevoApplication», без ERROR
journalctl -u konyaevo-bot --since "-10min" --no-pager | grep -E 'TELEGRAM|DONATE|ERROR'
```

### Откат

Остановите сервис, восстановите `data` и `.env` из бэкапа, переключитесь на прежний тег
(`git checkout vX.Y.Z`), соберите и запустите. JAR и база должны быть одной версии:
новые версии расширяют схему H2.

## Маршруты до Telegram и VPN

С сервера `api.telegram.org` напрямую недоступен, поэтому бот ходит в Bot API одним
из двух маршрутов (`TelegramRoutes`):

```
BOT_BASE_URL=https://konyaevo-tver-helper-bot.ixlax.workers.dev/bot
BOT_PROXY_TYPE=NO_PROXY
BOT_FALLBACK_PROXY_TYPE=SOCKS5
BOT_FALLBACK_PROXY_HOST=127.0.0.1
BOT_FALLBACK_PROXY_PORT=10808
```

Основной маршрут — Cloudflare Worker, запасной — `api.telegram.org` через локальный xray.
Без `BOT_FALLBACK_*` бот работает одним маршрутом.

- Отправка переходит на запасной маршрут, только если до Telegram не удалось подключиться
  (отказ соединения, недоступный прокси, страница шлюза вместо ответа Telegram). Если
  Telegram ответил или соединение оборвалось после отправки, запрос не повторяется:
  сообщение не придёт дважды.
- Опрос обновлений (`TelegramPoller`) переключается при любой сетевой ошибке.
- На запасном маршруте основной проверяется каждые 5 минут; когда он отвечает, бот
  возвращается на него. Переключения пишутся в журнал с меткой `[TELEGRAM ROUTE]`.
- ImageKit пробует прокси маршрутов в том же порядке.
- Опрос запускается в фоне после старта: API и Mini App поднимаются сразу, даже если
  Telegram недоступен; бот подключится, как только ответит любой маршрут (повтор с паузой
  до 30 секунд).

### Cloudflare Worker

`deploy/cloudflare-worker/worker.js` обслуживает два пути: `/api/*` пересылает на
`BACKEND_URL` (резервный вход в API для Mini App; подпись Telegram и CORS проверяет
бэкенд), `/bot*` — в `api.telegram.org` (маршрут бота).

```bash
cd deploy/cloudflare-worker
npx wrangler deploy
```

Или вставьте код в редакторе Worker в панели Cloudflare. Свой домен к Worker можно
привязать, только если DNS домена обслуживает Cloudflare.

### VPN для xray

Ссылку `vless://` (её можно скопировать в Happ) положите в `.env` **в одинарных кавычках** —
в ней есть `&` и `#`:

```
VPN_URL='vless://...'
```

```bash
./deploy/xray/set-vpn.sh                 # применить VPN_URL из .env
./deploy/xray/set-vpn.sh 'vless://...'   # записать ссылку в .env и применить
./deploy/xray/set-vpn.sh --check         # только проверить текущий VPN
```

Скрипт собирает `/usr/local/etc/xray/config.json` (SOCKS5 `127.0.0.1:10808`, HTTP
`127.0.0.1:10809`; путь меняется переменной `XRAY_CONFIG`), проверяет его через
`xray run -test` и показывает ошибку xray, если тот не принял конфиг. Затем сохраняет
прежний конфиг рядом, перезапускает xray и проверяет доступ к Telegram. Если Telegram
через новый VPN недоступен, прежний конфиг возвращается. Поддерживаются VLESS с
`reality`/`tls` и транспортами `tcp`, `ws`, `grpc`, `xhttp`. Перезапускать бота не нужно.

## Доступность Mini App

В части мобильных сетей и при VPN, который направляет российские адреса мимо туннеля,
телефон не достаёт до сервера API, хотя страница и бот работают. Клиент
(`frontend/src/api/client.ts`) получает данные по цепочке:

1. Основной адрес `VITE_API_URL`.
2. Резервный адрес `VITE_API_FALLBACK_URL`. Клиент запоминает адрес, который ответил,
   и при ошибке сети делает ещё один круг.
3. Копия расписания на GitHub Pages (`./data/`). Если сервер не ответил за 5 секунд,
   данные берутся из копии, сверху видно время копии. День и цвет недели пересчитываются
   на телефоне.
4. Если недоступно всё — экран с кнопками «Повторить» и «Расписание в чате».

Копию выгружает `frontend/scripts/snapshot.cjs` при каждом деплое и каждые 30 минут.
Если API во время сборки недоступен, переносится прошлая опубликованная копия. Профиль,
уведомления, огонёк, поддержка и «Поделиться» работают только через сервер.

## Парыч и огонёк

Парыч нарисован в SVG (`frontend/src/components/Mascot.tsx`) и живёт в центре нижней
панели (`BottomNav`): кнопка показывает облик и огонёк. Серый значок с оранжевым кольцом —
сегодняшний учебный день ещё не засчитан; золотая обводка — пользователь поддержал проект.

**Подсказка** над расписанием выбирается по московскому времени
(`frontend/src/utils/mascotTip.ts`): скоро пара (через сколько минут и в каком кабинете),
идёт пара, перерыв, пары закончились; для «Завтра» — сводка дня. Замены отмечаются отдельно.

**Огонёк** считает учебные дни подряд, в которые пользователь открыл расписание в Mini App
или в боте (активность учитывает `UserActivityService`). Учебный день — будний день, в
который у группы или преподавателя есть пары по основному расписанию. Выходные, дни без
пар и каникулы (1 июля — 31 августа, 31 декабря — 8 января) серию не прерывают. Каждые
7 дней подряд дают заморозку (не больше двух): она закрывает пропущенный учебный день.

**Опыт** начисляется раз в день за задания:

| Задание | Опыт |
|---|---|
| Открыть расписание | +10 |
| Заглянуть во «Завтра» после 18:00 | +15 |
| Поделиться расписанием или сохранить картинку | +20 |
| Каждые 7 дней огонька | +50 и заморозка |

**Уровни и облики**: уровень *n* начинается с 30·*n*·(*n*−1) опыта (2 — 60, 5 — 600,
10 — 2700).

| Облик | Как открыть |
|---|---|
| Обычный | сразу |
| Осень, Зима, Весна, Лето | уровни 2, 3, 4, 5 |
| Выпускник | уровень 10 |
| Звёздный | любая поддержка звёздами |
| Корона | поддержка от 250 ⭐ всего |

**Напоминание** приходит в боте в 21:00 по будням, только если серия не меньше 1 дня и
пропуск сегодняшнего дня погасит огонёк. При серии 0 и после того, как огонёк погас, бот
не пишет. Отключается кнопкой «Не напоминать» или на экране Парыча; при блокировке бота —
автоматически.

Логика — `MascotService`, прогресс — таблица `mascot_progress`, API — `/api/mascot`.

## Поддержка Парыча звёздами

Пользователи поддерживают проект звёздами Telegram (валюта `XTR`): платёжный провайдер,
токен и кошелёк не нужны, звёзды приходят на баланс бота. Логика — `DonationService`,
платежи — таблица `donation` (ключ — номер платежа Telegram, повторное уведомление не
засчитывается).

- **Mini App**: плашка «Поддержать» в шапке дня, карточка «Поддержи Парыча» под парами
  на сегодня (скрывается на две недели, поддержавшим не показывается) и блок на экране
  Парыча. Счёт создаёт `POST /api/donate`, оплата открывается через
  `Telegram.WebApp.openInvoice`; после оплаты окно закрывается и показывается «Спасибо!».
- **Бот**: кнопка «⭐ Поддержать Парыча», `/donate`, суммы 25 / 50 / 100 / 250 или
  «✏️ Своя сумма» (1–10 000).
- За поддержку открываются облики «Звёздный» и «Корона» (см. таблицу выше); новый облик
  надевается сразу.
- `/paysupport` объясняет, как задать вопрос по оплате; `/paysupport текст` пересылает его
  администраторам.

**Баланс** виден в @BotFather → бот → Balance. Звёзды становятся доступны через 21 день:
их можно потратить на рекламу (Buy Ads) или вывести через Fragment в TON (нужен TON-кошелёк,
например Tonkeeper; у Fragment есть минимальная сумма вывода). Секретную фразу кошелька
никому не сообщайте.

## Администрирование

Администраторы задаются в `BOT_ADMIN_IDS`.

- `/admin` — панель управления, `/stats` — пользователи, активность, подписки, сумма
  поддержки и активный маршрут до Telegram, `/refresh` — перечитать расписание и замены.
- `/broadcast текст` — текстовая рассылка; `/broadcast` и затем одно фото, видео, GIF или
  документ с подписью (или ответ этой командой на сообщение с медиа) — медиа-рассылка.
  Перед отправкой бот показывает предпросмотр и ждёт подтверждения. Файлы не скачиваются:
  используется Telegram `file_id`.
- В профиле Mini App администратору видны активность за сутки, неделю и месяц, состояние
  расписания, общие замены и отзывы с пагинацией. Права проверяет сервер
  (`TelegramMiniAppAuth`: подпись `initData`, срок запуска не старше часа, `AdminService`);
  смена роли в профиле прав не даёт.
- Оценка приложения появляется через минуту использования; для 1–4 звёзд нужен
  комментарий. Отзывы хранятся в базе бэкенда.

## REST API

Префикс `/api`. «Подпись» — заголовок `X-Telegram-Init-Data` с `Telegram.WebApp.initData`.

| Метод и путь | Доступ | Назначение |
|---|---|---|
| `GET /info` | все | День, цвет недели, дата замен, время сервера |
| `GET /groups`, `GET /teachers`, `GET /teachers/search` | все | Списки групп и преподавателей |
| `GET /schedule/group/{группа}`, `/schedule/teacher/{фио}` | все | Расписание с заменами |
| `GET /schedule/base/group/{группа}`, `/schedule/base/teacher/{фио}` | все | Основное расписание |
| `GET /changes/group/{группа}` | все | Замены группы |
| `GET /classrooms/free?day=&slot=&weekType=` | все | Свободные и занятые аудитории |
| `GET /health` | все | Состояние API и связи с Telegram |
| `GET, PUT /profile`, `POST /profile/activity` | подпись | Профиль и уведомления |
| `GET /mascot`, `POST /mascot/event`, `/mascot/settings`, `/mascot/rewards/ack` | подпись | Огонёк, задания, облики, награды |
| `POST /donate` | подпись | Ссылка на счёт в звёздах (`{"stars": 50}`) |
| `POST /share/prepare`, `GET /share/images/{id}.jpg` | подпись / по ссылке | «Поделиться расписанием» |
| `GET /feedback/status`, `POST /feedback` | подпись | Оценка приложения |
| `GET /admin/overview`, `/admin/changes`, `/admin/feedback` | администратор | Админ-сводка |

## Статистика из базы

Бот хранит у пользователя время последнего захода (`last_active`), роль, группу или
преподавателя — не число заходов. Команды читают копию базы, бота останавливать не нужно.
Выполняйте на сервере от root.

Кто пользовался ботом сегодня, по группам и преподавателям:

```bash
(cd /opt/konyaevo-tver-helper-bot && set -a && . ./.env && set +a && D=$(TZ=Europe/Moscow date +%F) && cp data/konyaevo-bot.mv.db /tmp/kstat.mv.db && java -cp $(ls ~/.m2/repository/com/h2database/h2/*/h2-*.jar | tail -1) org.h2.tools.Shell -url jdbc:h2:file:/tmp/kstat -user sa -password "$DB_PASSWORD" -sql "SELECT COUNT(*) AS \"Всего за день\" FROM user_settings WHERE last_active >= TIMESTAMP WITH TIME ZONE '$D 00:00:00+03'; SELECT CASE WHEN role = 'teacher' THEN 'Преп. ' || teacher_name ELSE COALESCE(group_name, 'не выбрал') END AS \"Расписание\", COUNT(*) AS \"Человек\" FROM user_settings WHERE last_active >= TIMESTAMP WITH TIME ZONE '$D 00:00:00+03' GROUP BY 1 ORDER BY 2 DESC"; rm -f /tmp/kstat.mv.db)
```

Огонёк по группам — сколько человек с огоньком, у скольких засчитан сегодня, максимальная серия:

```bash
(cd /opt/konyaevo-tver-helper-bot && set -a && . ./.env && set +a && D=$(TZ=Europe/Moscow date +%F) && cp data/konyaevo-bot.mv.db /tmp/kmas.mv.db && java -cp $(ls ~/.m2/repository/com/h2database/h2/*/h2-*.jar | tail -1) org.h2.tools.Shell -url jdbc:h2:file:/tmp/kmas -user sa -password "$DB_PASSWORD" -sql "SELECT who AS \"Расписание\", COUNT(*) AS \"С огоньком\", SUM(CASE WHEN last_day = DATE '$D' THEN 1 ELSE 0 END) AS \"Сегодня\", MAX(streak) AS \"Макс. серия\" FROM (SELECT CASE WHEN u.role = 'teacher' THEN 'Преп. ' || u.teacher_name ELSE COALESCE(u.group_name, 'не выбрал') END AS who, m.last_day, m.streak FROM mascot_progress m LEFT JOIN user_settings u ON u.chat_id = m.chat_id WHERE m.best_streak >= 1) t GROUP BY who ORDER BY 2 DESC, who"; rm -f /tmp/kmas.mv.db)
```

Для другого дня замените `D=$(TZ=Europe/Moscow date +%F)` на дату, например `D=2026-10-28`
(покажет всех, кто заходил начиная с этого дня). В запросах с `JOIN` группируйте через
подзапрос: H2 не принимает `GROUP BY 1` по выражению из соединённых таблиц.

## Проверки и тесты

```bash
./mvnw test                              # бэкенд: JUnit + Mockito
npm run build --prefix frontend          # типы TypeScript и сборка
npm run test:helpers --prefix frontend   # даты, кеш, подсказки Парыча, копия, страницы списков
```

Ключевые классы: `ScheduleResolver` собирает пары с заменами для API и бота,
`AcademicWeek` считает цвет недели, `TelegramRoutes`/`TelegramPoller` — связь с Telegram,
`MascotService` — огонёк и облики, `DonationService` — поддержка звёздами,
`TelegramMiniAppAuth` — проверка подписи Mini App
([документация Telegram](https://core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app)).

## Структура проекта

```
konyaevo-tver-help/
├── src/main/java/dev/ix1ax/main/
│   ├── bot/            # KonyaevoBot, TelegramRoutes, TelegramPoller, CallbackRouter, MessageSender, KeyboardFactory
│   ├── controller/     # REST API: расписание, профиль, Парыч, поддержка, «Поделиться», админка, health
│   ├── service/        # расписание и замены, MascotService, DonationService, уведомления, ImageKit
│   ├── model/          # JPA-сущности (UserSettings, MascotProgress, Donation, …)
│   ├── repository/     # Spring Data репозитории
│   ├── dto/, util/, config/
├── src/test/java/      # тесты бэкенда
├── frontend/
│   ├── src/
│   │   ├── api/        # клиент API с резервным адресом и копией расписания
│   │   ├── components/ # BottomNav, Header, Mascot, SupportParych, Pager, TabSwitch, ShareSchedule, …
│   │   ├── context/    # AppContext, ThemeContext, MascotContext
│   │   ├── views/      # Сегодня, Завтра, Неделя, Поиск (преподаватели, аудитории), Парыч, Профиль
│   │   ├── utils/      # подсказки Парыча, копия расписания, календарь, страницы
│   │   └── styles/
│   ├── scripts/snapshot.cjs     # копия расписания для GitHub Pages
│   ├── functions/api/           # прокси для Cloudflare Pages (альтернативный хостинг)
│   └── tests/helpers.cjs
├── deploy/
│   ├── cloudflare-worker/       # Worker: /api/* → бэкенд, /bot* → Telegram
│   └── xray/set-vpn.sh          # VPN для xray из ссылки vless://
├── .github/workflows/deploy-pages.yml
├── start.sh, start.bat
└── pom.xml
```

## Решение проблем

| Симптом | Причина и решение |
|---|---|
| API отвечает 502 сразу после перезапуска | Приложение ещё стартует (около 10 секунд). Если дольше — смотрите `journalctl -u konyaevo-bot`. С 2.2.1 API поднимается, даже когда Telegram недоступен |
| `/api/health` → `"telegram":"down"` | Не отвечает ни один маршрут: проверьте Worker (`curl https://…workers.dev/bot<токен>/getMe`) и VPN (`./deploy/xray/set-vpn.sh --check`) |
| `IMAGEKIT_URL_ENDPOINT must be https://ik.imagekit.io/your_id` | Неверный адрес ImageKit. С 2.2.1 кавычки вокруг значения не мешают |
| `set-vpn.sh`: «xray не принял конфиг» | Над сообщением — ошибка xray. Для REALITY нужен xray 1.8 или новее |
| `set-vpn.sh`: `FAIL: Telegram недоступен через VPN` | Этот сервер VPN не пускает в Telegram; прежний конфиг уже возвращён. Возьмите другую ссылку |
| Новый эндпоинт отвечает 404 | Работает старый JAR: `./mvnw clean package -DskipTests && systemctl restart konyaevo-bot` |
| `start.sh` падает на строке с `VPN_URL` | Ссылка без кавычек: в `.env` должно быть `VPN_URL='vless://…'` |
| Кнопка в старом сообщении бота показывает меню вместо действия | Так задумано: старые сообщения не меняют настройки. Переходы («Меню», «Сегодня», «Справка») работают из любого сообщения |
| Звёзды пришли, но Available Balance 0 | Telegram держит звёзды 21 день |

## История версий

Каждый релиз — тег `vX.Y.Z`. Обновление сервера — по разделу [Обновление](#обновление);
ниже — что меняется и нужно ли что-то кроме него.

| Версия | Что нового | Что сделать |
|---|---|---|
| **2.2.5** | Кнопки «Меню», «Сегодня / Завтра / Неделя» и справка работают из любого сообщения бота; окно поддержки закрывается после оплаты | Обновить бэкенд |
| **2.2.4** | Поддержка Парыча звёздами, облики «Звёздный» и «Корона», короткое меню бота с «⚙️ Ещё» | Обновить бэкенд (таблица `donation` и колонка `mascot_progress.donated_stars` создаются сами) |
| **2.2.3** | Парыч в центре нижней панели, «Сегодня / Завтра» и «Преподаватели / Кабинеты» в одной вкладке, страницы в списках | Только Mini App, сервер не трогать |
| **2.2.2** | Задание «Поделиться» засчитывается в любом Telegram и при сохранении картинки | Mini App; бэкенд — для подсказки к заданию |
| **2.2.1** | Два маршрута до Telegram с переключением, API стартует без Telegram, `/api/health`, VPN из `VPN_URL` | Обновить Worker (`/api/*` и `/bot*`), добавить в `.env` маршруты и `VPN_URL`, запустить `set-vpn.sh`, обновить бэкенд |
| **2.2.0** | Парыч, огонёк, задания, уровни, облики, напоминание; резервный адрес API и копия расписания на GitHub Pages | Обновить бэкенд (таблица `mascot_progress`), переменные `VITE_API_URL` и `VITE_API_FALLBACK_URL` |
| **2.0.0** | REST API на порту `8090`, Mini App, админка | В `.env` явно `SERVER_PORT=8090`, nginx — на `http://127.0.0.1:8090` |

## Лицензия

[MIT](../LICENSE)
