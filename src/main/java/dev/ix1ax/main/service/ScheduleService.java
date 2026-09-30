package dev.ix1ax.main.service;

import org.springframework.stereotype.Service;
import dev.ix1ax.main.dto.DayScheduleDto;
import dev.ix1ax.main.dto.LessonDto;
import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.Lesson;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.UserSettingsRepository;
import dev.ix1ax.main.util.HtmlUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Business logic layer: combines schedule + changes data,
 * manages user settings.
 */
@Service
public class ScheduleService {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final ScheduleResolver resolver;
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
        this.resolver = new ScheduleResolver(scheduleParser, changesParser);
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
        return AcademicWeek.typeFor(LocalDate.now(MOSCOW)).equals(Lesson.WEEK_RED)
                ? "🔴 Красная неделя" : "🔵 Синяя неделя";
    }

    public boolean isTomorrowRedWeek() {
        return AcademicWeek.typeFor(LocalDate.now(MOSCOW).plusDays(1)).equals(Lesson.WEEK_RED);
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
     * Get tomorrow's day name. Returns empty string for Saturday and Sunday (weekends).
     * On Sunday, tomorrow is Monday, so returns "Понедельник".
     * On Friday and Saturday, tomorrow is a weekend, so returns "".
     */
    public String getTomorrowName() {
        DayOfWeek dow = LocalDate.now(MOSCOW).plusDays(1).getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return "";
        }
        return DAY_NAMES.getOrDefault(dow, "");
    }

    public Map<String, DayScheduleDto> getResolvedGroupSchedule(String groupName) {
        return resolver.getScheduleForGroup(groupName);
    }

    public Map<String, DayScheduleDto> getResolvedTeacherSchedule(String teacherName) {
        return resolver.getScheduleForTeacher(teacherName);
    }

    public Map<String, DayScheduleDto> getBaseGroupSchedule(String groupName) {
        return resolver.getScheduleForGroup(groupName, false);
    }

    public Map<String, DayScheduleDto> getBaseTeacherSchedule(String teacherName) {
        return resolver.getScheduleForTeacher(teacherName, false);
    }

    public String getScheduleTextForGroup(String groupName, String dayName) {
        return formatResolvedDay(groupName, dayName, false, getResolvedGroupSchedule(groupName).get(dayName));
    }

    private String formatResolvedDay(String target, String dayName, boolean forTeacher, DayScheduleDto day) {
        LocalDate date = LocalDate.now(MOSCOW);
        if (dayName.equals(getTomorrowName())) date = date.plusDays(1);
        return formatResolvedDay(target, dayName, forTeacher, day, AcademicWeek.typeFor(date));
    }

    private String formatResolvedDay(String target, String dayName, boolean forTeacher, DayScheduleDto day, String weekType) {
        return formatResolvedDay(target, dayName, forTeacher, day, weekType, true);
    }

    private String formatResolvedDay(String target, String dayName, boolean forTeacher, DayScheduleDto day, String weekType, boolean includeTarget) {
        String weekBadge = weekType.equals(Lesson.WEEK_RED) ? "🔴 Красная неделя" : "🔵 Синяя неделя";
        StringBuilder text = new StringBuilder();
        if (includeTarget) {
            text.append(forTeacher ? "👨‍🏫 <b>" : "👥 <b>Группа: ")
                    .append(HtmlUtils.escapeHtml(target)).append("</b>\n📅 <b>")
                    .append(HtmlUtils.escapeHtml(dayName)).append("</b> (").append(weekBadge).append(")\n\n");
        } else {
            text.append("<b>").append(HtmlUtils.escapeHtml(dayName)).append("</b>\n\n");
        }
        if (day == null || !day.isHasLessons()) {
            return text.append("✨ <i>Пар нет — свободный день</i>").toString();
        }
        List<String> blocks = new ArrayList<>();
        for (LessonDto item : day.getLessons()) {
            if (item.getWeekType() != null && !item.getWeekType().equals(weekType)) continue;
            Lesson original = new Lesson(item.getLessonNumber(), item.getTime(),
                    item.getOriginalSubject() != null ? item.getOriginalSubject() : item.getSubject(),
                    item.getOriginalTeacher() != null ? item.getOriginalTeacher() : item.getTeacher(),
                    item.getOriginalRoom() != null ? item.getOriginalRoom() : item.getRoom(), item.getWeekType());
            String formatted = forTeacher ? original.formatForTeacher(item.getGroupName()) : original.format();
            if (!item.isChanged()) {
                blocks.add(formatted);
                continue;
            }
            StringBuilder block = new StringBuilder();
            if (item.isCanceled() && item.getOriginalSubject() != null) block.append("<s>").append(formatted).append("</s>\n");
            if (item.isCanceled()) {
                block.append("❌ <b>ОТМЕНА</b>");
            } else {
                block.append("⚡️ <b>ЗАМЕНА:</b>\n");
                Lesson replacement = new Lesson(item.getLessonNumber(), item.getTime(), item.getSubject(),
                        item.getTeacher(), item.getRoom());
                block.append(forTeacher ? replacement.formatForTeacher(item.getGroupName()) : replacement.format());
            }
            blocks.add(block.toString());
        }
        return text.append(blocks.isEmpty() ? "✨ <i>Пар нет — свободный день</i>" : String.join("\n\n", blocks)).toString();
    }

    public String getWeekScheduleTextForGroup(String groupName) {
        return formatWeek(groupName, false, getBaseGroupSchedule(groupName));
    }

    private String formatWeek(String name, boolean teacher, Map<String, DayScheduleDto> days) {
        String weekType = AcademicWeek.typeFor(LocalDate.now(MOSCOW));
        StringBuilder text = new StringBuilder("<b>" + HtmlUtils.escapeHtml(name) + "</b>\nРасписание на неделю · " + getCurrentWeekBadge()
                + "\n<i>Основное расписание без замен и отмен. Актуальные изменения — в «Сегодня», «Завтра» и «Замены».</i>");
        for (String day : scheduleParser.getDays()) {
            text.append("\n\n──────────────\n\n");
            text.append(formatResolvedDay(name, day, teacher, days.get(day), weekType, false));
        }
        return text.toString();
    }

    public String getChangesTextForGroup(String groupName) {
        return changesParser.getFormattedChanges(groupName);
    }

    // ===== Schedule for Teachers =====

    public List<String> getAllTeachers() {
        Set<String> teachers = new TreeSet<>(scheduleParser.getAllTeachers());
        if (changesParser.getOverlayDayName() != null) {
            var pattern = java.util.regex.Pattern.compile("[А-ЯЁ][а-яё-]+\\s+[А-ЯЁ]\\.\\s*[А-ЯЁ]\\.");
            for (var changes : changesParser.getAllChanges().values()) {
                for (String text : changes.values()) {
                    var matcher = pattern.matcher(text);
                    while (matcher.find()) teachers.add(matcher.group().replaceAll("\\.\\s+", "."));
                }
            }
        }
        return new ArrayList<>(teachers);
    }

    public List<String> getTeacherFirstLetters() {
        return getAllTeachers().stream().map(name -> name.substring(0, 1)).distinct().sorted().toList();
    }

    public List<String> getTeachersByLetter(String letter) {
        return getAllTeachers().stream().filter(name -> name.startsWith(letter)).toList();
    }

    public String getScheduleTextForTeacher(String teacherName, String dayName) {
        return formatResolvedDay(teacherName, dayName, true, getResolvedTeacherSchedule(teacherName).get(dayName));
    }

    public String getWeekScheduleTextForTeacher(String teacherName) {
        return formatWeek(teacherName, true, getBaseTeacherSchedule(teacherName));
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
