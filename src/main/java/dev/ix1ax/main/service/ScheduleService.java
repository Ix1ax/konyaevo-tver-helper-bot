package dev.ix1ax.main.service;

import org.springframework.stereotype.Service;
import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.UserSettingsRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Business logic layer: combines schedule + changes data,
 * manages user settings.
 */
@Service
public class ScheduleService {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final ScheduleParserService scheduleParser;
    private final ChangesParserService changesParser;
    private final UserSettingsRepository userSettingsRepo;

    private static final Map<DayOfWeek, String> DAY_NAMES = Map.of(
            DayOfWeek.MONDAY, "Понедельник",
            DayOfWeek.TUESDAY, "Вторник",
            DayOfWeek.WEDNESDAY, "Среда",
            DayOfWeek.THURSDAY, "Четверг",
            DayOfWeek.FRIDAY, "Пятница",
            DayOfWeek.SATURDAY, "Суббота",
            DayOfWeek.SUNDAY, "Воскресенье"
    );

    public ScheduleService(ScheduleParserService scheduleParser,
                           ChangesParserService changesParser,
                           UserSettingsRepository userSettingsRepo) {
        this.scheduleParser = scheduleParser;
        this.changesParser = changesParser;
        this.userSettingsRepo = userSettingsRepo;
    }

    // ===== User Settings =====

    public UserSettings getOrCreateUser(Long chatId) {
        return userSettingsRepo.findById(chatId)
                .orElseGet(() -> {
                    UserSettings settings = new UserSettings(chatId);
                    return userSettingsRepo.save(settings);
                });
    }

    public UserSettings saveUser(UserSettings settings) {
        return userSettingsRepo.save(settings);
    }

    public UserSettings resetUser(Long chatId) {
        UserSettings settings = getOrCreateUser(chatId);
        settings.setRole(null);
        settings.setCourse(null);
        settings.setGroupName(null);
        settings.setTeacherName(null);
        return userSettingsRepo.save(settings);
    }

    /**
     * Get current week type indicator (Red or Blue week).
     * Uses Moscow timezone to ensure correct date on any server.
     */
    public String getCurrentWeekBadge() {
        LocalDate now = LocalDate.now(MOSCOW);
        int year = now.getYear();
        LocalDate semesterStart;
        if (now.getMonthValue() >= 9) {
            semesterStart = LocalDate.of(year, 9, 1);
        } else if (now.getMonthValue() <= 1) {
            semesterStart = LocalDate.of(year - 1, 9, 1);
        } else {
            semesterStart = LocalDate.of(year, 2, 1);
        }
        LocalDate startMonday = semesterStart.with(DayOfWeek.MONDAY);
        long daysBetween = ChronoUnit.DAYS.between(startMonday, now);
        long weekNumber = (daysBetween / 7) + 1;
        boolean isRed = (weekNumber % 2 != 0);
        return isRed ? "🔴 Красная неделя" : "🔵 Синяя неделя";
    }

    // ===== Schedule for Students =====

    public Map<String, List<String>> getGroupsByCourse() {
        return scheduleParser.getGroupsByCourse();
    }

    public List<String> getCourseNames() {
        List<String> list = new ArrayList<>(scheduleParser.getGroupsByCourse().keySet());
        list.sort(Comparator.comparingInt(name -> {
            String digits = name.replaceAll("\\D", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        }));
        return list;
    }

    public List<String> getGroupsForCourse(String courseName) {
        return scheduleParser.getGroupsForCourse(courseName);
    }

    /**
     * Get today's day name. Returns empty string for Saturday and Sunday (weekends).
     */
    public String getTodayName() {
        DayOfWeek dow = LocalDate.now(MOSCOW).getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return "";
        }
        return DAY_NAMES.getOrDefault(dow, "");
    }

    /**
     * Get tomorrow's day name. Skips Saturday/Sunday → returns Monday.
     */
    public String getTomorrowName() {
        LocalDate tomorrow = LocalDate.now(MOSCOW).plusDays(1);
        if (tomorrow.getDayOfWeek() == DayOfWeek.SATURDAY) {
            tomorrow = tomorrow.plusDays(2);
        } else if (tomorrow.getDayOfWeek() == DayOfWeek.SUNDAY) {
            tomorrow = tomorrow.plusDays(1);
        }
        return DAY_NAMES.getOrDefault(tomorrow.getDayOfWeek(), "");
    }

    public String getScheduleTextForGroup(String groupName, String dayName) {
        DaySchedule schedule = scheduleParser.getScheduleForGroupAndDay(groupName, dayName);
        String weekInfo = getCurrentWeekBadge();
        if (schedule == null || !schedule.hasLessons()) {
            return "👥 <b>Группа: " + groupName + "</b>\n" +
                    "📅 <b>" + dayName + "</b> (" + weekInfo + ")\n\n" +
                    "✨ <i>Пар нет — свободный день</i>";
        }

        String formatted = schedule.format();
        int idx = formatted.indexOf("\n\n");
        String lessonsOnly = (idx != -1) ? formatted.substring(idx + 2) : formatted;
        return "👥 <b>Группа: " + groupName + "</b>\n" +
                "📅 <b>" + dayName + "</b> (" + weekInfo + ")\n\n" +
                lessonsOnly;
    }

    public String getWeekScheduleTextForGroup(String groupName) {
        String weekInfo = getCurrentWeekBadge();
        StringBuilder sb = new StringBuilder();
        sb.append("👥 <b>Группа: ").append(groupName).append("</b>\n");
        sb.append("🗓 <b>Расписание на неделю</b> (текущая: ").append(weekInfo).append(")\n");

        String[] days = scheduleParser.getDays();
        for (String day : days) {
            sb.append("\n──────────────────\n\n");
            DaySchedule schedule = scheduleParser.getScheduleForGroupAndDay(groupName, day);
            if (schedule == null || !schedule.hasLessons()) {
                sb.append("<b>").append(day).append("</b>\n✨ <i>Пар нет — свободный день</i>");
            } else {
                sb.append(schedule.format());
            }
        }

        return sb.toString();
    }

    public String getChangesTextForGroup(String groupName) {
        return changesParser.getFormattedChanges(groupName);
    }

    // ===== Schedule for Teachers =====

    public List<String> getTeacherFirstLetters() {
        return scheduleParser.getTeacherFirstLetters();
    }

    public List<String> getTeachersByLetter(String letter) {
        return scheduleParser.getTeachersByLetter(letter);
    }

    public String getScheduleTextForTeacher(String teacherName, String dayName) {
        DaySchedule schedule = scheduleParser.getScheduleForTeacherAndDay(teacherName, dayName);
        String weekInfo = getCurrentWeekBadge();
        if (schedule == null || !schedule.hasLessons()) {
            return "👨‍🏫 <b>" + teacherName + "</b>\n" +
                    "📅 <b>" + dayName + "</b> (" + weekInfo + ")\n\n" +
                    "✨ <i>Пар нет — свободный день</i>";
        }

        String formatted = schedule.formatForTeacher();
        int idx = formatted.indexOf("\n\n");
        String lessonsOnly = (idx != -1) ? formatted.substring(idx + 2) : formatted;
        return "👨‍🏫 <b>" + teacherName + "</b>\n" +
                "📅 <b>" + dayName + "</b> (" + weekInfo + ")\n\n" +
                lessonsOnly;
    }

    public String getWeekScheduleTextForTeacher(String teacherName) {
        String weekInfo = getCurrentWeekBadge();
        StringBuilder sb = new StringBuilder();
        sb.append("👨‍🏫 <b>").append(teacherName).append("</b>\n");
        sb.append("🗓 <b>Расписание на неделю</b> (текущая: ").append(weekInfo).append(")\n");

        String[] days = scheduleParser.getDays();
        for (String day : days) {
            sb.append("\n──────────────────\n\n");
            DaySchedule schedule = scheduleParser.getScheduleForTeacherAndDay(teacherName, day);
            if (schedule == null || !schedule.hasLessons()) {
                sb.append("<b>").append(day).append("</b>\n✨ <i>Пар нет — свободный день</i>");
            } else {
                sb.append(schedule.formatForTeacher());
            }
        }

        return sb.toString();
    }

    public String getChangesTextForTeacher(String teacherName) {
        return changesParser.getFormattedChangesForTeacher(teacherName);
    }

    /**
     * Check if today is a weekday (has schedule).
     */
    public boolean isWeekday() {
        DayOfWeek dow = LocalDate.now(MOSCOW).getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
    }
}
