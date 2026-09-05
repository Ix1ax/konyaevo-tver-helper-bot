# Telegram-бот для просмотра расписания занятий и замен Тверского колледжа им. А.Н. Коняева.

Данные загружаются напрямую из Google Таблиц колледжа и периодически обновляются в фоне. Интерфейс построен на inline-кнопках через редактирование одного сообщения, чтобы не засорять историю чата.

## Функционал

- **Для студентов:**
  - Сохранение выбранного курса и группы
  - Расписание на сегодня, завтра и на всю неделю
  - Просмотр ежедневных замен
  - Определение типа недели (красная / синяя)

- **Для преподавателей:**
  - Поиск преподавателя по алфавитному рубрикатору
  - Расписание занятий с указанием групп и аудиторий
  - Просмотр замен по преподавателю

## Стек

- Java 17, Spring Boot 3
- telegrambots (Long Polling)
- Apache POI, OpenCSV (парсинг таблиц Google Sheets в форматах XLSX и CSV)
- H2 Database (хранение настроек и выбранных групп пользователей)

## Настройка и запуск

### 1. Переменные окружения

Скопируйте пример файла окружения:

```bash
cp .env.example .env
```

Параметры в `.env`:
- `BOT_TOKEN` — токен бота от @BotFather (обязательно)
- `BOT_USERNAME` — юзернейм бота
- `BOT_BASE_URL` — базовый URL Telegram Bot API (опционально, по умолчанию `https://api.telegram.org/bot`. Указывается при работе через Cloudflare Worker или reverse proxy)
- `DB_URL` — строка подключения к базе H2 (по умолчанию `jdbc:h2:file:./data/konyaevo-bot`)
- `DB_PASSWORD` — пароль к БД (если задан)

### 2. Сборка

```bash
./mvnw clean package -DskipTests
```

Собранный JAR появится в `target/konyaevo-bot-1.0.0.jar`.

### 3. Запуск

Linux / macOS:
```bash
chmod +x start.sh
./start.sh
```

Windows:
```cmd
start.bat
```

Либо напрямую через Java:
```bash
java -jar target/konyaevo-bot-1.0.0.jar
```

## Структура проекта

```
src/main/java/dev/ix1ax/main/
├── KonyaevoApplication.java          # точка входа Spring Boot
├── bot/
│   ├── KonyaevoBot.java              # обработка входящих сообщений и апдейтов
│   ├── CallbackRouter.java           # навигация по inline-кнопкам и экранам
│   ├── MessageSender.java            # отправка и редактирование сообщений
│   ├── KeyboardFactory.java          # сборка inline-клавиатур
│   └── TelegramErrorClassifier.java  # обработка сетевых ошибок и блокировок
├── service/
│   ├── ScheduleParserService.java    # парсинг основного расписания (XLSX/CSV)
│   ├── ChangesParserService.java     # парсинг ежедневных замен
│   └── ScheduleService.java          # кэш расписания в памяти и бизнес-логика
├── model/                            # модели расписания, занятий и настроек
└── repository/                       # репозиторий настроек пользователей (JPA)
```

## Лицензия

MIT license
