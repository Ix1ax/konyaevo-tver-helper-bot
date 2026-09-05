# 🏛 Konyaevo Schedule Bot (ТК им. А.Н. Коняева)

Telegram-бот для удобного просмотра расписания занятий и ежедневных замен Тверского колледжа им. А.Н. Коняева.

Работает в режиме одного интерактивного сообщения (UI на Inline-кнопках, без спама в чат) с автоматическим парсингом таблиц расписания из Google Sheets.

---

## ✨ Возможности

- **Для студентов:**
  - Выбор курса и группы с сохранением выбора (автологин при следующем входе)
  - Расписание на сегодня и завтра (с автоматическим пропуском выходных)
  - Полное расписание на всю неделю
  - Оперативные ежедневные замены по группе
  - Определение типа недели (🔴 Красная / 🔵 Синяя неделя) по московскому времени
- **Для преподавателей:**
  - Поиск преподавателя по алфавитному рубрикатору фамилий
  - Инвертированное расписание занятий преподавателя (с указанием групп и аудиторий)
  - Выборка замен преподавателя по всем группам колледжа
- **Технические преимущества:**
  - **Single Message UI:** работа в рамках одного сообщения через `EditMessageText` с fallback-механизмами (если сообщение удалено)
  - **Двухуровневый парсинг:** потоковый разбор XLSX через Apache POI с определением чередования недель по вертикальному выравниванию ячеек (TOP / BOTTOM), резервный fallback на CSV
  - **Сетевая устойчивость:** `HttpClient` с таймаутами подключения и чтения, защита от зависания фоновых потоков
  - **Кэширование в памяти:** расписание обновляется в фоне без блокировок и простоев (Atomic snapshot update)
  - **Персистентность:** база данных H2 для хранения пользовательских настроек

---

## 🛠 Стек технологий

- **Java 17**
- **Spring Boot 3.2.5** (Spring Data JPA, Scheduling)
- **TelegramBots 6.9.7.1**
- **Apache POI 5.2.5** (разбор XLSX стилей и выравнивания)
- **OpenCSV 5.9** (разбор CSV)
- **H2 Database** (встроенная реляционная БД)
- **spring-dotenv** (поддержка переменных окружения через `.env`)

---

## 🚀 Быстрый запуск

### 1. Клонирование репозитория
```bash
git git clone https://github.com/ix1ax/konyaevo-tver-help.git
cd konyaevo-tver-help
```

### 2. Настройка окружения
Скопируйте пример файла конфигурации:
```bash
cp .env.example .env
```
Заполните переменные в `.env`:
```env
# Токен бота от @BotFather (обязательно)
BOT_TOKEN=1234567890:ABCdefGHIjklMNOpqrSTUvwxYZ

# Юзернейм бота (без @)
BOT_USERNAME=konyaevo_tver_helper_bot

# База данных H2 (путь и пароль)
DB_URL=jdbc:h2:file:./data/konyaevo-bot
DB_PASSWORD=
```

### 3. Сборка
```bash
./mvnw clean package -DskipTests
```
Собранный JAR-файл будет находиться в папке `target/konyaevo-bot-1.0.0.jar`.

### 4. Запуск

#### На Linux / macOS:
```bash
chmod +x start.sh
./start.sh
```

#### На Windows:
Запустите `start.bat` двойным кликом или из командной строки.

---

## 📁 Структура проекта

```
src/main/java/dev/ix1ax/main/
├── KonyaevoApplication.java          # Точка входа Spring Boot
├── bot/
│   ├── KonyaevoBot.java              # Тонкий Telegram-роутер
│   ├── CallbackRouter.java           # Маршрутизатор callback-действий и экранов
│   ├── MessageSender.java            # Отправка/редактирование сообщений и fallback
│   ├── KeyboardFactory.java          # Фабрика Inline-клавиатур
│   └── TelegramErrorClassifier.java  # Классификатор ошибок Telegram API
├── config/
│   └── BotConfig.java                # Регистрация сессии TelegramBotsApi
├── model/
│   ├── DaySchedule.java              # Расписание дня
│   ├── Lesson.java                   # Модель занятия / пары
│   └── UserSettings.java             # JPA-сущность настроек пользователя
├── repository/
│   └── UserSettingsRepository.java   # Репозиторий настроек
├── service/
│   ├── ChangesParserService.java     # Сервис парсинга замен
│   ├── ScheduleParserService.java    # Сервис парсинга основного расписания
│   └── ScheduleService.java          # Бизнес-логика расписания и пользователей
└── util/
    └── HtmlUtils.java                # Утилита экранирования HTML для Telegram
```

---

## 📄 Лицензия

MIT License
