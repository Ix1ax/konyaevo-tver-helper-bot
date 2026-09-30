package dev.ix1ax.main.service;

import com.opencsv.CSVReader;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.Lesson;
import dev.ix1ax.main.util.HtmlUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Parses schedule changes from Google Sheets.
 * The changes sheet has a single page that gets overwritten each day.
 *
 * Structure:
 * Row 0: Date + day of week (e.g. "7 сентября\n(понедельник)")
 * Row 1: "Группа", "1", "2", "3", "4", "5", "6"
 * Row 2+: Group name, lesson data for slots 1-6
 */
@Service
public class ChangesParserService {

    private static final Logger log = LoggerFactory.getLogger(ChangesParserService.class);

    private static final String CSV_URL_TEMPLATE =
            "https://docs.google.com/spreadsheets/d/%s/gviz/tq?tqx=out:csv&gid=%s";

    @Value("${changes.spreadsheet.id}")
    private String spreadsheetId;

    @Value("${changes.sheet.gid}")
    private String sheetGid;

    /**
     * The date/title of the changes.
     */
    private volatile String changesDate = "";

    /**
     * Map: groupName -> Map: lessonNumber -> change description
     */
    private volatile Map<String, Map<Integer, String>> changesByGroup = new ConcurrentHashMap<>();

    /**
     * Reusable HTTP client with timeouts.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @PostConstruct
    public void init() {
        refreshChanges();
    }

    @Scheduled(fixedRateString = "${changes.refresh.interval}", initialDelayString = "${changes.refresh.interval}")
    public void refreshChanges() {
        log.info("[CHANGES] Refreshing changes data from Google Sheets...");
        try {
            String url = String.format(CSV_URL_TEMPLATE, spreadsheetId, sheetGid);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.warn("[CHANGES WARNING] HTTP {} fetching changes. Keeping current changes.", response.statusCode());
                return;
            }

            List<String[]> csv;
            try (CSVReader reader = new CSVReader(new InputStreamReader(
                    new ByteArrayInputStream(response.body()), StandardCharsets.UTF_8))) {
                csv = reader.readAll();
            }

            if (csv == null || csv.size() < 2) {
                log.warn("[CHANGES WARNING] Changes CSV is empty or too small. Keeping current changes in memory.");
                return;
            }

            Map<String, Map<Integer, String>> newChanges = new LinkedHashMap<>();

            // Row 0: Date
            String date = csv.get(0)[0].trim().replace("\n", " ");
            changesDate = date;

            // Row 1: Header ("Группа", "1", "2", ...)
            // Row 2+: Data
            for (int row = 2; row < csv.size(); row++) {
                String[] line = csv.get(row);
                if (line.length < 1) continue;

                String groupName = line[0].trim();
                if (groupName.isEmpty()) continue;

                Map<Integer, String> groupChanges = new LinkedHashMap<>();

                for (int slot = 1; slot <= 6; slot++) {
                    if (slot < line.length) {
                        String change = line[slot].trim();
                        if (!change.isEmpty()) {
                            groupChanges.put(slot, change);
                        }
                    }
                }

                if (!groupChanges.isEmpty()) {
                    newChanges.put(groupName, groupChanges);
                }
            }

            // Atomic snapshot publication
            this.changesByGroup = newChanges;

            log.info("[CHANGES SUCCESS] Updated for date '{}': {} groups with changes",
                    changesDate, changesByGroup.size());
        } catch (Exception e) {
            log.warn("[CHANGES ERROR] Failed to fetch changes: {}. Retaining previous changes in memory.", e.getMessage());
        }
    }

    // ===== Public API =====

    public String getChangesDate() {
        return changesDate;
    }

    public Map<String, Map<Integer, String>> getAllChanges() {
        return Collections.unmodifiableMap(changesByGroup);
    }

    public Map<Integer, String> getChangesForGroup(String groupName) {
        return changesByGroup.getOrDefault(groupName, Collections.emptyMap());
    }

    /**
     * Get formatted changes text for a specific group.
     */
    public String getFormattedChanges(String groupName) {
        Map<Integer, String> changes = getChangesForGroup(groupName);
        if (changes.isEmpty()) {
            return "<b>Изменения на " + HtmlUtils.escapeHtml(changesDate) + "</b>\n\n" +
                    "<b>Группа: " + HtmlUtils.escapeHtml(groupName) + "</b>\n\n" +
                    "<i>Изменений нет</i>";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("<b>Изменения на ").append(HtmlUtils.escapeHtml(changesDate)).append("</b>\n\n");
        sb.append("<b>Группа: ").append(HtmlUtils.escapeHtml(groupName)).append("</b>\n\n");

        List<Integer> slots = new ArrayList<>(changes.keySet());
        Collections.sort(slots);

        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            String changeText = changes.get(slot);
            sb.append("<b>").append(slot).append(" пара</b>:\n");
            String[] lines = isCancellation(changeText) ? new String[]{"ОТМЕНА"} : changeText.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    sb.append("   ").append(HtmlUtils.escapeHtml(trimmed)).append("\n");
                }
            }
            if (i < slots.size() - 1) {
                sb.append("\n");
            }
        }

        return sb.toString().trim();
    }

    private ScheduleParserService scheduleParser;

    private final java.time.Clock clock;

    public ChangesParserService() {
        this(null, java.time.Clock.system(MOSCOW));
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ChangesParserService(@org.springframework.lang.Nullable ScheduleParserService scheduleParser) {
        this(scheduleParser, java.time.Clock.system(MOSCOW));
    }

    ChangesParserService(ScheduleParserService scheduleParser, java.time.Clock clock) {
        this.scheduleParser = scheduleParser;
        this.clock = clock;
    }

    public void setScheduleParser(ScheduleParserService scheduleParser) {
        this.scheduleParser = scheduleParser;
    }

    /**
     * Get formatted changes for a teacher (search all groups for mentions and main schedule cancellations).
     */
    public String getFormattedChangesForTeacher(String teacherName) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>Изменения на ").append(HtmlUtils.escapeHtml(changesDate)).append("</b>\n\n");
        sb.append("<b>").append(HtmlUtils.escapeHtml(teacherName)).append("</b>\n\n");

        List<TeacherChangeEntry> changeEntries = new ArrayList<>();
        Set<String> addedKeys = new HashSet<>();

        // 1. Direct mentions in changes
        for (Map.Entry<String, Map<Integer, String>> groupEntry : changesByGroup.entrySet()) {
            String groupName = groupEntry.getKey();
            Map<Integer, String> groupSlots = groupEntry.getValue();

            for (Map.Entry<Integer, String> slotEntry : groupSlots.entrySet()) {
                int slot = slotEntry.getKey();
                String val = slotEntry.getValue();
                if (teacherMatches(val, teacherName)) {
                    addedKeys.add(slot + ":" + groupName);
                    StringBuilder entrySb = new StringBuilder();
                    entrySb.append("<b>").append(HtmlUtils.escapeHtml(groupName)).append("</b> — <b>")
                            .append(slot).append(" пара</b>:\n");
                    for (String line : isCancellation(val) ? new String[]{"ОТМЕНА"} : val.split("\n")) {
                        String trimmed = line.trim();
                        if (!trimmed.isEmpty()) {
                            entrySb.append("   ").append(HtmlUtils.escapeHtml(trimmed)).append("\n");
                        }
                    }
                    changeEntries.add(new TeacherChangeEntry(slot, groupName, entrySb.toString().trim()));
                }
            }
        }

        // 2. Cancellations without explicit teacher name, matched via main schedule
        if (scheduleParser != null) {
            String dayName = extractDayName(changesDate);
            String weekType = detectWeekTypeForDate(changesDate);

            if (dayName != null) {
                for (Map.Entry<String, Map<Integer, String>> groupEntry : changesByGroup.entrySet()) {
                    String groupName = groupEntry.getKey();
                    Map<Integer, String> groupSlots = groupEntry.getValue();

                    for (Map.Entry<Integer, String> slotEntry : groupSlots.entrySet()) {
                        int slot = slotEntry.getKey();
                        String val = slotEntry.getValue();

                        String key = slot + ":" + groupName;
                        if (addedKeys.contains(key)) {
                            continue;
                        }

                        // If not already added (not directly mentioning this teacher),
                        // check if the teacher had a scheduled lesson in this slot for this group:
                        DaySchedule groupSchedule = scheduleParser.getScheduleForGroupAndDay(groupName, dayName);
                        if (groupSchedule != null) {
                            boolean hasLesson = false;
                            for (dev.ix1ax.main.model.Lesson lesson : groupSchedule.getLessons()) {
                                if (lesson.getLessonNumber() == slot &&
                                        teacherMatches(lesson.getTeacher(), teacherName)) {
                                    if (lesson.getWeekType() == null ||
                                            weekType == null ||
                                            lesson.getWeekType().equalsIgnoreCase(weekType)) {
                                        hasLesson = true;
                                        break;
                                    }
                                }
                            }

                            if (hasLesson) {
                                addedKeys.add(key);
                                StringBuilder entrySb = new StringBuilder();
                                entrySb.append("<b>").append(HtmlUtils.escapeHtml(groupName)).append("</b> — <b>")
                                        .append(slot).append(" пара</b>:\n");
                                entrySb.append("   <b>ОТМЕНА</b>\n");
                                changeEntries.add(new TeacherChangeEntry(slot, groupName, entrySb.toString().trim()));
                            }
                        }
                    }
                }
            }
        }

        // Strict sorting: first by lesson slot ascending (1..6), then by group name alphabetically
        changeEntries.sort(Comparator
                .comparingInt((TeacherChangeEntry e) -> e.slot)
                .thenComparing(e -> e.groupName)
        );

        if (changeEntries.isEmpty()) {
            sb.append("<i>Изменений нет</i>");
        } else {
            for (int i = 0; i < changeEntries.size(); i++) {
                sb.append(changeEntries.get(i).formattedText);
                if (i < changeEntries.size() - 1) {
                    sb.append("\n\n");
                }
            }
        }

        return sb.toString().trim();
    }

    private static class TeacherChangeEntry {
        final int slot;
        final String groupName;
        final String formattedText;

        TeacherChangeEntry(int slot, String groupName, String formattedText) {
            this.slot = slot;
            this.groupName = groupName;
            this.formattedText = formattedText;
        }
    }

    private static final java.time.ZoneId MOSCOW = java.time.ZoneId.of("Europe/Moscow");
    private static final java.util.regex.Pattern DATE_PATTERN =
            java.util.regex.Pattern.compile("(\\d{1,2})\\s+([а-яёА-ЯЁ]+)(?:\\s+(\\d{4}))?");
    private static final java.util.regex.Pattern TEACHER_INITIALS_PATTERN =
            java.util.regex.Pattern.compile("(?U)[А-ЯЁ][а-яё]{2,}\\s+[А-ЯЁ]\\.\\s*[А-ЯЁ]?\\.?");

    public String getTargetDayName() {
        return extractDayName(changesDate);
    }

    /** Старые замены остаются в списке публикаций, но не попадают в текущую неделю. */
    public String getOverlayDayName() {
        java.time.LocalDate date = parseDateFromChangesHeader(changesDate);
        if (date == null) return null;
        java.time.LocalDate today = java.time.LocalDate.now(clock);
        java.time.LocalDate monday = today.with(java.time.DayOfWeek.MONDAY);
        java.time.LocalDate lastDay = monday.plusDays(6);
        if (today.plusDays(1).isAfter(lastDay)) lastDay = today.plusDays(1);
        if (date.isBefore(monday) || date.isAfter(lastDay)) return null;
        return switch (date.getDayOfWeek()) {
            case MONDAY -> "Понедельник";
            case TUESDAY -> "Вторник";
            case WEDNESDAY -> "Среда";
            case THURSDAY -> "Четверг";
            case FRIDAY -> "Пятница";
            case SATURDAY -> "Суббота";
            case SUNDAY -> "Воскресенье";
        };
    }

    public boolean isCancellation(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();
        return lower.contains("отмен") || lower.contains("снят")
                || lower.matches("(?s).*\\bпар\\s+нет\\b.*");
    }

    private boolean mentionsOtherTeacher(String text, String teacherName) {
        if (text == null) return false;
        java.util.regex.Matcher m = TEACHER_INITIALS_PATTERN.matcher(text);
        while (m.find()) {
            String found = m.group();
            if (!containsTeacherLastName(found, teacherName)) {
                return true;
            }
        }
        return false;
    }

    public boolean teacherMatches(String text, String teacherName) {
        if (text == null || teacherName == null || teacherName.isBlank()) return false;
        var names = TEACHER_INITIALS_PATTERN.matcher(text);
        boolean foundNames = false;
        String target = teacherName.replaceAll("[\\s.]", "").toLowerCase(Locale.ROOT);
        while (names.find()) {
            foundNames = true;
            String candidate = names.group().replaceAll("[\\s.]", "").toLowerCase(Locale.ROOT);
            if (candidate.equals(target)) return true;
        }
        // По одной фамилии ищем лишь тогда, когда в ячейке нет ФИО с инициалами.
        return !foundNames && containsTeacherLastName(text, teacherName);
    }

    public boolean containsTeacherLastName(String text, String teacherName) {
        if (text == null || teacherName == null) return false;
        String[] parts = teacherName.trim().split("\\s+");
        if (parts.length > 0 && !parts[0].isEmpty()) {
            String lastName = parts[0];
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                    "(?U)(?<=^|[^а-яёА-ЯЁa-zA-Z])" + java.util.regex.Pattern.quote(lastName) + "(?=[^а-яёА-ЯЁa-zA-Z]|$)");
            return pattern.matcher(text).find();
        }
        return false;
    }

    public String extractDayName(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        String lower = dateStr.toLowerCase();
        if (lower.contains("понедельник")) return "Понедельник";
        if (lower.contains("вторник")) return "Вторник";
        if (lower.contains("среда") || lower.contains("среду")) return "Среда";
        if (lower.contains("четверг")) return "Четверг";
        if (lower.contains("пятница") || lower.contains("пятницу")) return "Пятница";
        if (lower.contains("суббота") || lower.contains("субботу")) return "Суббота";

        java.time.LocalDate date = parseDateFromChangesHeader(dateStr);
        if (date != null) {
            return switch (date.getDayOfWeek()) {
                case MONDAY -> "Понедельник";
                case TUESDAY -> "Вторник";
                case WEDNESDAY -> "Среда";
                case THURSDAY -> "Четверг";
                case FRIDAY -> "Пятница";
                case SATURDAY -> "Суббота";
                default -> null;
            };
        }
        return null;
    }

    private java.time.LocalDate parseDateFromChangesHeader(String header) {
        if (header == null) return null;
        java.util.regex.Matcher m = DATE_PATTERN.matcher(header);
        if (!m.find()) return null;
        try {
            int day = Integer.parseInt(m.group(1));
            String monthStr = m.group(2).toLowerCase();
            int month;
            if (monthStr.startsWith("янв")) month = 1;
            else if (monthStr.startsWith("фев")) month = 2;
            else if (monthStr.startsWith("мар")) month = 3;
            else if (monthStr.startsWith("апр")) month = 4;
            else if (monthStr.startsWith("ма")) month = 5;
            else if (monthStr.startsWith("июн")) month = 6;
            else if (monthStr.startsWith("июл")) month = 7;
            else if (monthStr.startsWith("авг")) month = 8;
            else if (monthStr.startsWith("сен")) month = 9;
            else if (monthStr.startsWith("окт")) month = 10;
            else if (monthStr.startsWith("ноя")) month = 11;
            else if (monthStr.startsWith("дек")) month = 12;
            else return null;

            java.time.LocalDate now = java.time.LocalDate.now(clock);
            int year = m.group(3) != null ? Integer.parseInt(m.group(3)) : now.getYear();
            if (m.group(3) == null) {
                if (now.getMonthValue() == 12 && month == 1) year++;
                if (now.getMonthValue() == 1 && month == 12) year--;
            }
            return java.time.LocalDate.of(year, month, day);
        } catch (Exception e) {
            return null;
        }
    }

    private String detectWeekTypeForDate(String dateStr) {
        java.time.LocalDate date = parseDateFromChangesHeader(dateStr);
        if (date == null) {
            date = java.time.LocalDate.now(clock);
        }
        return AcademicWeek.typeFor(date);
    }

    public String getTargetWeekType() {
        return detectWeekTypeForDate(changesDate);
    }
}
