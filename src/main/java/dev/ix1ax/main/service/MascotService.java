package dev.ix1ax.main.service;

import dev.ix1ax.main.bot.KeyboardFactory;
import dev.ix1ax.main.bot.MessageSender;
import dev.ix1ax.main.dto.DayScheduleDto;
import dev.ix1ax.main.model.MascotProgress;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.MascotProgressRepository;
import dev.ix1ax.main.repository.UserSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Огонёк Парыча: серия учебных дней, в которые пользователь открыл расписание в боте или мини-аппе.
 * Выходные, дни без пар и каникулы серию не прерывают; пропуск закрывается заморозкой, если она есть.
 */
@Service
public class MascotService {
    private static final Logger log = LoggerFactory.getLogger(MascotService.class);
    static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    static final int MAX_FREEZES = 2, MILESTONE = 7, MILESTONE_XP = 50, MAX_LEVEL = 10;
    private static final String[] DAY_NAMES = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"};
    private static final String[] DAY_LABELS = {"ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"};
    private static final String[] LEVEL_NAMES = {"Первокурсник", "Слушатель", "Активист", "Староста", "Отличник",
            "Знаток", "Ветеран", "Мастер", "Легенда", "Выпускник"};

    enum Task {
        OPEN(1, "Открыть расписание", null, 10),
        TOMORROW(2, "Заглянуть во «Завтра» вечером", "после 18:00", 15),
        SHARE(4, "Поделиться расписанием", "отправить в чат или сохранить картинку", 20);
        final int bit, xp; final String title, hint;
        Task(int bit, String title, String hint, int xp) { this.bit = bit; this.title = title; this.hint = hint; this.xp = xp; }
    }
    record Look(String id, String name, int level) {}
    static final List<Look> LOOKS = List.of(new Look("classic", "Обычный", 1), new Look("autumn", "Осень", 2),
            new Look("winter", "Зима", 3), new Look("spring", "Весна", 4), new Look("summer", "Лето", 5),
            new Look("graduate", "Выпускник", MAX_LEVEL));

    public record WeekDay(String label, String state) {}
    public record TaskView(String id, String title, String hint, int xp, boolean done) {}
    public record LookView(String id, String name, int level, boolean unlocked) {}
    public record Reward(String type, int value, String look) {}
    public record State(int streak, int best, int freezes, int maxFreezes, boolean countedToday, boolean studyDay,
                        List<WeekDay> week, int xp, int level, String levelName, int levelXp, int nextLevelXp,
                        List<TaskView> tasks, String look, List<LookView> looks, boolean reminder, List<Reward> rewards) {}

    private final MascotProgressRepository repository;
    private final UserSettingsRepository users;
    private final ScheduleService schedule;
    private final MessageSender sender;
    /** Кому огонёк уже засчитан сегодня: повторная активность не обращается к базе. */
    private final Map<Long, LocalDate> checkedToday = new ConcurrentHashMap<>();

    public MascotService(MascotProgressRepository repository, UserSettingsRepository users, ScheduleService schedule, MessageSender sender) {
        this.repository = repository; this.users = users; this.schedule = schedule; this.sender = sender;
    }

    /** Опыт, с которого начинается уровень: 2 — 60, 3 — 180, 4 — 360 … 10 — 2700. */
    static int levelStart(int level) { return 30 * level * (level - 1); }
    static int levelFor(int xp) {
        int level = 1;
        while (level < MAX_LEVEL && xp >= levelStart(level + 1)) level++;
        return level;
    }

    /** Летние и новогодние каникулы не считаются учебными днями, даже если основное расписание опубликовано. */
    static boolean isHoliday(LocalDate date) {
        int month = date.getMonthValue(), day = date.getDayOfMonth();
        return month == 7 || month == 8 || (month == 12 && day == 31) || (month == 1 && day <= 8);
    }

    static boolean isStudyDay(Map<String, DayScheduleDto> week, LocalDate date) {
        int index = date.getDayOfWeek().getValue() - 1;
        if (index > 4 || isHoliday(date)) return false;
        DayScheduleDto day = week.get(DAY_NAMES[index]);
        if (day == null || day.getLessons() == null) return false;
        String type = AcademicWeek.typeFor(date);
        return day.getLessons().stream().anyMatch(lesson -> !lesson.isCanceled() &&
                (lesson.getWeekType() == null || "all".equals(lesson.getWeekType()) || type.equals(lesson.getWeekType())));
    }

    private Predicate<LocalDate> studyDays(UserSettings user) {
        if (user == null) return date -> false;
        Map<String, DayScheduleDto> week = null;
        if ("student".equals(user.getRole()) && user.getGroupName() != null && !user.getGroupName().isBlank())
            week = schedule.getBaseGroupSchedule(user.getGroupName());
        else if ("teacher".equals(user.getRole()) && user.getTeacherName() != null && !user.getTeacherName().isBlank())
            week = schedule.getBaseTeacherSchedule(user.getTeacherName());
        if (week == null || week.isEmpty()) return date -> false;
        Map<String, DayScheduleDto> days = week;
        return date -> isStudyDay(days, date);
    }

    /** Учебные дни строго между двумя датами. */
    private static int studyDaysBetween(Predicate<LocalDate> studyDay, LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate date = from.plusDays(1); date.isBefore(to) && count < 400; date = date.plusDays(1))
            if (studyDay.test(date)) count++;
        return count;
    }

    private static int bit(LocalDate date) { return 1 << (date.getDayOfWeek().getValue() - 1); }

    private static void rollWeek(MascotProgress progress, LocalDate today) {
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        if (monday.equals(progress.getWeekStart())) return;
        progress.setWeekStart(monday); progress.setWeekDone(0); progress.setWeekFrozen(0);
    }

    private MascotProgress load(long id) {
        return repository.findById(id).orElseGet(() -> new MascotProgress(id));
    }

    private static void addXp(MascotProgress progress, int xp) {
        int before = levelFor(progress.getXp());
        progress.setXp(progress.getXp() + xp);
        int after = levelFor(progress.getXp());
        if (after > before) addReward(progress, "level:" + after);
    }

    private static void addReward(MascotProgress progress, String reward) {
        String pending = progress.getPendingReward();
        progress.setPendingReward(pending == null || pending.isBlank() ? reward : pending + "," + reward);
    }

    private static boolean completeTask(MascotProgress progress, Task task, LocalDate today) {
        if (!today.equals(progress.getTaskDay())) { progress.setTaskDay(today); progress.setTasksDone(0); }
        if ((progress.getTasksDone() & task.bit) != 0) return false;
        progress.setTasksDone(progress.getTasksDone() | task.bit);
        addXp(progress, task.xp);
        return true;
    }

    /** Вызывается при любой активности пользователя; база затрагивается не чаще раза в день. */
    public void recordActivity(long id) {
        LocalDate today = LocalDate.now(MOSCOW);
        if (today.equals(checkedToday.get(id))) return;
        try {
            checkIn(id, today);
            if (checkedToday.size() > 50000) checkedToday.clear();
            checkedToday.put(id, today);
        } catch (Exception e) {
            log.warn("Не удалось засчитать огонёк пользователю {}", id, e);
        }
    }

    public synchronized MascotProgress checkIn(long id, LocalDate today) {
        MascotProgress progress = load(id);
        completeTask(progress, Task.OPEN, today);
        Predicate<LocalDate> studyDay = studyDays(users.findById(id).orElse(null));
        if (studyDay.test(today) && !today.equals(progress.getLastDay())) {
            LocalDate last = progress.getLastDay();
            int missed = last == null || progress.getStreak() == 0 ? 0 : studyDaysBetween(studyDay, last, today);
            rollWeek(progress, today);
            if (missed <= progress.getFreezes()) {
                // Пропущенные учебные дни этой недели показываем закрытыми заморозкой.
                if (missed > 0) for (LocalDate date = last.plusDays(1); date.isBefore(today); date = date.plusDays(1))
                    if (!date.isBefore(progress.getWeekStart()) && studyDay.test(date)) progress.setWeekFrozen(progress.getWeekFrozen() | bit(date));
                progress.setFreezes(progress.getFreezes() - missed);
                progress.setStreak(progress.getStreak() + 1);
            } else progress.setStreak(1);
            progress.setLastDay(today);
            progress.setWeekDone(progress.getWeekDone() | bit(today));
            progress.setBestStreak(Math.max(progress.getBestStreak(), progress.getStreak()));
            if (progress.getStreak() % MILESTONE == 0) {
                progress.setFreezes(Math.min(MAX_FREEZES, progress.getFreezes() + 1));
                addReward(progress, "streak:" + progress.getStreak());
                addXp(progress, MILESTONE_XP);
            }
        }
        return repository.save(progress);
    }

    public synchronized State event(long id, String type, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        if ("open".equals(type)) {
            checkIn(id, today);
            checkedToday.put(id, today);
        } else {
            MascotProgress progress = load(id);
            boolean done = switch (type) {
                case "tomorrow" -> now.getHour() >= 18 && completeTask(progress, Task.TOMORROW, today);
                case "share" -> completeTask(progress, Task.SHARE, today);
                default -> throw new IllegalArgumentException("Неизвестное событие");
            };
            if (done) repository.save(progress);
        }
        return state(id, today);
    }

    public synchronized State settings(long id, String look, Boolean reminder, LocalDate today) {
        MascotProgress progress = load(id);
        if (look != null) {
            Look selected = LOOKS.stream().filter(item -> item.id().equals(look)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Неизвестный облик"));
            if (selected.level() > levelFor(progress.getXp())) throw new IllegalArgumentException("Облик ещё не открыт");
            progress.setLook(look);
        }
        if (reminder != null) progress.setReminderEnabled(reminder);
        repository.save(progress);
        return state(id, today);
    }

    public synchronized State acknowledgeRewards(long id, LocalDate today) {
        MascotProgress progress = load(id);
        if (progress.getPendingReward() != null) { progress.setPendingReward(null); repository.save(progress); }
        return state(id, today);
    }

    public State state(long id, LocalDate today) {
        MascotProgress progress = load(id);
        Predicate<LocalDate> studyDay = studyDays(users.findById(id).orElse(null));
        LocalDate last = progress.getLastDay();
        boolean counted = today.equals(last);
        // Серия жива, пока пропущенные учебные дни до сегодняшнего покрываются заморозками.
        boolean alive = last != null && (counted || studyDaysBetween(studyDay, last, today) <= progress.getFreezes());
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        boolean currentWeek = monday.equals(progress.getWeekStart());
        List<WeekDay> week = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            int mask = 1 << i;
            String state = !studyDay.test(date) ? "off"
                    : currentWeek && (progress.getWeekDone() & mask) != 0 ? "done"
                    : currentWeek && (progress.getWeekFrozen() & mask) != 0 ? "freeze"
                    : date.isBefore(today) ? "missed" : date.equals(today) ? "today" : "future";
            week.add(new WeekDay(DAY_LABELS[i], state));
        }
        int tasksDone = today.equals(progress.getTaskDay()) ? progress.getTasksDone() : 0;
        List<TaskView> tasks = Arrays.stream(Task.values()).map(task -> new TaskView(task.name().toLowerCase(Locale.ROOT),
                task.title, task.hint, task.xp, (tasksDone & task.bit) != 0)).toList();
        int level = levelFor(progress.getXp());
        List<LookView> looks = LOOKS.stream().map(look -> new LookView(look.id(), look.name(), look.level(), look.level() <= level)).toList();
        List<Reward> rewards = new ArrayList<>();
        if (progress.getPendingReward() != null) for (String item : progress.getPendingReward().split(",")) {
            String[] parts = item.split(":");
            if (parts.length != 2) continue;
            int value = Integer.parseInt(parts[1]);
            String look = "level".equals(parts[0]) ? LOOKS.stream().filter(l -> l.level() == value).map(Look::id).findFirst().orElse(null) : null;
            rewards.add(new Reward(parts[0], value, look));
        }
        return new State(alive ? progress.getStreak() : 0, progress.getBestStreak(), progress.getFreezes(), MAX_FREEZES,
                counted, studyDay.test(today), week, progress.getXp(), level, LEVEL_NAMES[level - 1], levelStart(level),
                level == MAX_LEVEL ? levelStart(level) : levelStart(level + 1), tasks, progress.getLook(), looks,
                progress.isReminderEnabled(), rewards);
    }

    @Scheduled(cron = "0 0 21 * * MON-FRI", zone = "Europe/Moscow")
    public void sendReminders() { sendReminders(LocalDate.now(MOSCOW)); }

    /**
     * Напоминает только тем, у кого сейчас горит огонёк (серия от 1 дня), в тот учебный день,
     * пропуск которого его погасит, и не больше раза в день. При серии 0 напоминаний нет.
     */
    public void sendReminders(LocalDate today) {
        int sent = 0;
        for (MascotProgress progress : repository.findByReminderEnabledTrueAndStreakGreaterThan(0)) {
            LocalDate last = progress.getLastDay();
            if (progress.getStreak() < 1 || last == null || today.equals(last) || today.equals(progress.getLastReminder())) continue;
            Predicate<LocalDate> studyDay = studyDays(users.findById(progress.getChatId()).orElse(null));
            // В базе после пропуска остаётся прежнее число, хотя пользователь видит 0. Пропущено больше,
            // чем покрывают заморозки, — огонёк уже погас, напоминать не о чем.
            if (!studyDay.test(today) || studyDaysBetween(studyDay, last, today) != progress.getFreezes()) continue;
            var result = sender.sendDirectMessage(progress.getChatId(), reminderText(progress.getStreak()), reminderKeyboard());
            if (result == MessageSender.DirectSendResult.BLOCKED) progress.setReminderEnabled(false);
            if (result == MessageSender.DirectSendResult.SUCCESS) sent++;
            progress.setLastReminder(today);
            repository.save(progress);
            try { Thread.sleep(40); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        }
        if (sent > 0) log.info("[MASCOT] Напоминаний об огоньке: {}", sent);
    }

    public void disableReminder(long id) {
        MascotProgress progress = load(id);
        progress.setReminderEnabled(false);
        repository.save(progress);
    }

    static String days(int count) {
        int tens = count % 100, units = count % 10;
        String word = tens >= 11 && tens <= 14 ? "дней" : units == 1 ? "день" : units >= 2 && units <= 4 ? "дня" : "дней";
        return count + " " + word;
    }

    static String reminderText(int streak) {
        return "🔥 <b>Огонёк " + days(streak) + " скоро погаснет!</b>\n" +
                "Загляни в расписание до 23:59 — Парыч сохранит серию.";
    }

    private static InlineKeyboardMarkup reminderKeyboard() {
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        String url = KeyboardFactory.getMiniAppUrl();
        if (url != null) rows.add(List.of(KeyboardFactory.webAppButton("📱 Открыть расписание", url)));
        rows.add(List.of(KeyboardFactory.button("🔕 Не напоминать", "mascot:reminder_off")));
        return new InlineKeyboardMarkup(rows);
    }
}
