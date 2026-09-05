package dev.ix1ax.main.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.apache.poi.ss.usermodel.*;
import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.Lesson;

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
import java.util.regex.Pattern;

/**
 * Parses schedule data from Google Sheets (prioritizing in-memory streaming XLSX with POI,
 * falling back to CSV).
 * Maintains an in-memory cache that refreshes periodically.
 */
@Service
public class ScheduleParserService {

    private static final Logger log = LoggerFactory.getLogger(ScheduleParserService.class);

    private static final String XLSX_URL_TEMPLATE =
            "https://docs.google.com/spreadsheets/d/%s/export?format=xlsx";

    private static final String CSV_URL_TEMPLATE =
            "https://docs.google.com/spreadsheets/d/%s/gviz/tq?tqx=out:csv&gid=%s";

    @Value("${schedule.spreadsheet.id}")
    private String spreadsheetId;

    @Value("${schedule.sheet.gids}")
    private String sheetGidsStr;

    @Value("${schedule.sheet.names}")
    private String sheetNamesStr;

    /**
     * Map: groupName -> Map: dayName -> DaySchedule
     */
    private volatile Map<String, Map<String, DaySchedule>> scheduleByGroup = new ConcurrentHashMap<>();

    /**
     * Map: courseName -> List of group names
     */
    private volatile Map<String, List<String>> groupsByCourse = new LinkedHashMap<>();

    /**
     * Set of all unique teacher names.
     */
    private volatile Set<String> allTeachers = new TreeSet<>();

    /**
     * Map: teacherName -> Map: dayName -> List of lessons (with group info)
     */
    private volatile Map<String, Map<String, DaySchedule>> scheduleByTeacher = new ConcurrentHashMap<>();

    private static final String[] DAYS = {"Понедельник", "Вторник", "Среда", "Четверг", "Пятница"};

    /**
     * Precompiled pattern for detecting teacher names (e.g. "Козлов А.Н.").
     */
    private static final Pattern TEACHER_PATTERN = Pattern.compile("[А-ЯЁа-яё]+\\s+[А-ЯЁ]\\.");

    /**
     * Reusable HTTP client for all network requests.
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @PostConstruct
    public void init() {
        refreshSchedule();
    }

    @Scheduled(fixedRateString = "${schedule.refresh.interval}", initialDelayString = "${schedule.refresh.interval}")
    public void refreshSchedule() {
        log.info("Refreshing schedule data from Google Sheets...");
        try {
            String[] gids = sheetGidsStr.split(",");
            String[] names = sheetNamesStr.split(",");

            Map<String, Map<String, DaySchedule>> newScheduleByGroup = new HashMap<>();
            Map<String, List<String>> newGroupsByCourse = new LinkedHashMap<>();
            Set<String> newTeachers = new TreeSet<>();

            boolean xlsxSuccess = false;
            try {
                byte[] xlsxBytes = fetchXlsxBytes();
                if (xlsxBytes != null && xlsxBytes.length > 0) {
                    parseWorkbook(xlsxBytes, names, newScheduleByGroup, newGroupsByCourse, newTeachers);
                    xlsxSuccess = !newScheduleByGroup.isEmpty();
                    if (xlsxSuccess) {
                        log.info("Successfully parsed XLSX workbook: {} groups, {} teachers",
                                newScheduleByGroup.size(), newTeachers.size());
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to stream or parse XLSX workbook ({}), falling back to CSV...", e.getMessage());
            }

            if (!xlsxSuccess) {
                log.info("[SCHEDULE] Using CSV fallback parser...");
                fallbackParseCsv(gids, names, newScheduleByGroup, newGroupsByCourse, newTeachers);
            }

            if (newScheduleByGroup.isEmpty()) {
                log.warn("[SCHEDULE WARNING] Refreshed schedule data is empty! Preserving existing in-memory cache ({} groups, {} teachers) to prevent downtime.",
                        scheduleByGroup.size(), allTeachers.size());
                return;
            }

            // Build teacher schedule
            Map<String, Map<String, DaySchedule>> newScheduleByTeacher = buildTeacherSchedule(newScheduleByGroup);

            // Atomic snapshot publication (zero lock, no empty cache window)
            this.scheduleByGroup = newScheduleByGroup;
            this.groupsByCourse = newGroupsByCourse;
            this.allTeachers = newTeachers;
            this.scheduleByTeacher = newScheduleByTeacher;

            log.info("[SCHEDULE SUCCESS] Cache updated: {} groups, {} teachers ready in memory",
                    scheduleByGroup.size(), allTeachers.size());
        } catch (Exception e) {
            log.error("[SCHEDULE ERROR] Failed to refresh schedule: {}. Keeping current schedule in memory.", e.getMessage(), e);
        }
    }

    private byte[] fetchXlsxBytes() throws IOException, InterruptedException {
        String url = String.format(XLSX_URL_TEMPLATE, spreadsheetId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() == 200) {
            return response.body();
        }
        throw new IOException("HTTP " + response.statusCode() + " fetching XLSX from Google Sheets");
    }

    private void parseWorkbook(byte[] bytes, String[] names,
                              Map<String, Map<String, DaySchedule>> scheduleMap,
                              Map<String, List<String>> groupsMap,
                              Set<String> teachers) throws IOException {
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            int sheetCount = wb.getNumberOfSheets();
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = wb.getSheetAt(i);
                String courseName = i < names.length ? names[i].trim() : sheet.getSheetName();
                log.info("Parsing XLSX sheet: {} (index={})", courseName, i);
                parseSheetPoi(sheet, courseName, scheduleMap, groupsMap, teachers);
            }
        }
    }

    void parseSheetPoi(Sheet sheet, String courseName,
                       Map<String, Map<String, DaySchedule>> scheduleMap,
                       Map<String, List<String>> groupsMap,
                       Set<String> teachers) {
        DataFormatter df = new DataFormatter();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return;

        List<String> groupNames = new ArrayList<>();
        List<Integer> groupColumns = new ArrayList<>();

        int lastCol = headerRow.getLastCellNum();
        for (int col = 3; col < lastCol; col += 2) {
            Cell cell = headerRow.getCell(col);
            if (cell != null) {
                String name = df.formatCellValue(cell).trim();
                if (!name.isEmpty()) {
                    groupNames.add(name);
                    groupColumns.add(col);
                }
            }
        }

        List<String> sortedGroupNames = new ArrayList<>(groupNames);
        Collections.sort(sortedGroupNames);
        groupsMap.put(courseName, sortedGroupNames);

        for (String group : groupNames) {
            scheduleMap.putIfAbsent(group, new LinkedHashMap<>());
        }

        String currentDay = null;
        int lastRowNum = sheet.getLastRowNum();
        for (int r = 1; r <= lastRowNum; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;

            Cell dayCell = row.getCell(0);
            if (dayCell != null) {
                String dayText = df.formatCellValue(dayCell).trim();
                if (!dayText.isEmpty()) {
                    currentDay = dayText;
                }
            }
            if (currentDay == null) continue;

            Cell timeCell = row.getCell(1);
            String time = timeCell != null ? df.formatCellValue(timeCell).trim() : "";
            if (time.isEmpty()) continue;

            Cell numCell = row.getCell(2);
            if (numCell == null) continue;
            String numText = df.formatCellValue(numCell).trim();
            int lessonNum;
            try {
                lessonNum = Integer.parseInt(numText);
            } catch (NumberFormatException e) {
                continue; // Skip breaks or invalid lesson numbers
            }

            for (int g = 0; g < groupNames.size(); g++) {
                int subjectCol = groupColumns.get(g);
                int roomCol = subjectCol + 1;

                Cell sCell = row.getCell(subjectCol);
                Cell rCell = row.getCell(roomCol);

                String subjectCell = sCell != null ? df.formatCellValue(sCell).trim() : "";
                String roomCell = rCell != null ? df.formatCellValue(rCell).trim() : "";

                if (subjectCell.isEmpty() || subjectCell.equalsIgnoreCase("ПРАКТИКА")) {
                    if (subjectCell.equalsIgnoreCase("ПРАКТИКА")) {
                        Lesson lesson = new Lesson(lessonNum, time, "ПРАКТИКА", "", "");
                        scheduleMap.get(groupNames.get(g))
                                .computeIfAbsent(currentDay, DaySchedule::new)
                                .addLesson(lesson);
                    }
                    continue;
                }

                ParsedCell parsed = parseSubjectCell(subjectCell);
                int entryCount = parsed.entries.size();

                List<String> roomLines = new ArrayList<>();
                for (String rLine : roomCell.split("\n")) {
                    String trimmed = rLine.trim();
                    if (!trimmed.isEmpty()) {
                        roomLines.add(trimmed);
                    }
                }

                VerticalAlignment vAlign = (sCell != null && sCell.getCellStyle() != null)
                        ? sCell.getCellStyle().getVerticalAlignment()
                        : VerticalAlignment.CENTER;

                for (int e = 0; e < entryCount; e++) {
                    ParsedCell.Entry entry = parsed.entries.get(e);
                    if (entry.teacher != null && !entry.teacher.isBlank()) {
                        for (String t : entry.teacher.split(",\\s*")) {
                            String trimmedT = t.trim();
                            if (!trimmedT.isEmpty()) {
                                teachers.add(trimmedT);
                            }
                        }
                    }

                    String assignedRoom = roomCell;
                    if (entryCount > 1 && !roomLines.isEmpty()) {
                        assignedRoom = (e < roomLines.size()) ? roomLines.get(e) : roomLines.get(roomLines.size() - 1);
                    }

                    String weekType = null;
                    if (entryCount == 2) {
                        weekType = (e == 0) ? Lesson.WEEK_RED : Lesson.WEEK_BLUE;
                    } else if (entryCount == 1) {
                        if (vAlign == VerticalAlignment.TOP) {
                            weekType = Lesson.WEEK_RED;
                        } else if (vAlign == VerticalAlignment.BOTTOM) {
                            weekType = Lesson.WEEK_BLUE;
                        }
                    }

                    Lesson lesson = new Lesson(lessonNum, time, entry.subject, entry.teacher, assignedRoom, weekType);
                    scheduleMap.get(groupNames.get(g))
                            .computeIfAbsent(currentDay, DaySchedule::new)
                            .addLesson(lesson);
                }
            }
        }
    }

    private void fallbackParseCsv(String[] gids, String[] names,
                                  Map<String, Map<String, DaySchedule>> newScheduleByGroup,
                                  Map<String, List<String>> newGroupsByCourse,
                                  Set<String> newTeachers) {
        for (int i = 0; i < gids.length; i++) {
            String gid = gids[i].trim();
            String courseName = i < names.length ? names[i].trim() : "Курс " + (i + 1);

            log.info("Parsing CSV sheet: {} (gid={})", courseName, gid);
            List<String[]> csv = fetchCsv(gid);
            if (csv == null || csv.isEmpty()) {
                log.warn("Empty CSV for gid={}", gid);
                continue;
            }

            parseSheet(csv, courseName, newScheduleByGroup, newGroupsByCourse, newTeachers);
        }
    }

    private List<String[]> fetchCsv(String gid) {
        String url = String.format(CSV_URL_TEMPLATE, spreadsheetId, gid);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("User-Agent", "Mozilla/5.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                log.error("Failed to fetch CSV from gid={}: HTTP {}", gid, response.statusCode());
                return null;
            }
            try (CSVReader reader = new CSVReader(new InputStreamReader(
                    new ByteArrayInputStream(response.body()), StandardCharsets.UTF_8))) {
                return reader.readAll();
            }
        } catch (IOException | InterruptedException | com.opencsv.exceptions.CsvException e) {
            log.error("Failed to fetch CSV from gid={}: {}", gid, e.getMessage());
            return null;
        }
    }

    private void parseSheet(List<String[]> csv, String courseName,
                            Map<String, Map<String, DaySchedule>> scheduleMap,
                            Map<String, List<String>> groupsMap,
                            Set<String> teachers) {
        if (csv.size() < 2) return;

        // First row contains group names in columns 3, 5, 7, ...
        String[] header = csv.get(0);
        List<String> groupNames = new ArrayList<>();
        List<Integer> groupColumns = new ArrayList<>();

        for (int col = 3; col < header.length; col += 2) {
            String name = header[col].trim();
            if (!name.isEmpty()) {
                groupNames.add(name);
                groupColumns.add(col);
            }
        }

        List<String> sortedGroupNames = new ArrayList<>(groupNames);
        Collections.sort(sortedGroupNames);
        groupsMap.put(courseName, sortedGroupNames);

        // Initialize schedule maps for each group
        for (String group : groupNames) {
            scheduleMap.putIfAbsent(group, new LinkedHashMap<>());
        }

        // Parse schedule rows
        String currentDay = null;
        for (int row = 1; row < csv.size(); row++) {
            String[] line = csv.get(row);
            if (line.length < 3) continue;

            // Column 0: day name (only in first row of the day block)
            String dayCell = line[0].trim();
            if (!dayCell.isEmpty()) {
                currentDay = dayCell;
            }
            if (currentDay == null) continue;

            // Column 1: time
            String time = line[1].trim();
            if (time.isEmpty()) continue;

            // Column 2: lesson number
            String lessonNumStr = line[2].trim();
            int lessonNum;
            try {
                lessonNum = Integer.parseInt(lessonNumStr);
            } catch (NumberFormatException e) {
                continue; // Skip non-lesson rows (like "Семьеведение" break row)
            }

            // Parse each group's lesson
            for (int g = 0; g < groupNames.size(); g++) {
                int subjectCol = groupColumns.get(g);
                int roomCol = subjectCol + 1;

                String subjectCell = subjectCol < line.length ? line[subjectCol].trim() : "";
                String roomCell = roomCol < line.length ? line[roomCol].trim() : "";

                if (subjectCell.isEmpty() || subjectCell.equalsIgnoreCase("ПРАКТИКА")) {
                    if (subjectCell.equalsIgnoreCase("ПРАКТИКА")) {
                        Lesson lesson = new Lesson(lessonNum, time, "ПРАКТИКА", "", "");
                        scheduleMap.get(groupNames.get(g))
                                .computeIfAbsent(currentDay, DaySchedule::new)
                                .addLesson(lesson);
                    }
                    continue;
                }

                // Parse subject cell: handles multi-line subjects and alternating weeks
                ParsedCell parsed = parseSubjectCell(subjectCell);
                int entryCount = parsed.entries.size();

                // Parse room lines if multiple entries exist
                List<String> roomLines = new ArrayList<>();
                for (String r : roomCell.split("\n")) {
                    String trimmed = r.trim();
                    if (!trimmed.isEmpty()) {
                        roomLines.add(trimmed);
                    }
                }

                for (int e = 0; e < entryCount; e++) {
                    ParsedCell.Entry entry = parsed.entries.get(e);
                    if (entry.teacher != null && !entry.teacher.isBlank()) {
                        for (String t : entry.teacher.split(",\\s*")) {
                            String trimmedT = t.trim();
                            if (!trimmedT.isEmpty()) {
                                teachers.add(trimmedT);
                            }
                        }
                    }

                    String assignedRoom = roomCell;
                    if (entryCount > 1 && !roomLines.isEmpty()) {
                        assignedRoom = (e < roomLines.size()) ? roomLines.get(e) : roomLines.get(roomLines.size() - 1);
                    }

                    String weekType = null;
                    if (entryCount == 2) {
                        weekType = (e == 0) ? Lesson.WEEK_RED : Lesson.WEEK_BLUE;
                    }

                    Lesson lesson = new Lesson(lessonNum, time, entry.subject, entry.teacher, assignedRoom, weekType);
                    scheduleMap.get(groupNames.get(g))
                            .computeIfAbsent(currentDay, DaySchedule::new)
                            .addLesson(lesson);
                }
            }
        }
    }

    /**
     * Parse a subject cell that may contain multiple subjects/teachers and multiline subject names.
     */
    private ParsedCell parseSubjectCell(String cell) {
        ParsedCell result = new ParsedCell();
        String[] lines = cell.split("\n");

        // Clean up lines
        List<String> cleanLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                cleanLines.add(trimmed);
            }
        }

        if (cleanLines.isEmpty()) return result;

        List<String> curSubjectLines = new ArrayList<>();
        for (String line : cleanLines) {
            if (looksLikeTeacherName(line)) {
                String subject = String.join(" ", curSubjectLines);
                result.entries.add(new ParsedCell.Entry(subject, line));
                curSubjectLines.clear();
            } else {
                curSubjectLines.add(line);
            }
        }

        if (!curSubjectLines.isEmpty()) {
            String subject = String.join(" ", curSubjectLines);
            result.entries.add(new ParsedCell.Entry(subject, ""));
        }

        return result;
    }

    /**
     * Check if a string looks like a teacher name (contains Cyrillic + initials pattern).
     * Examples: "Козлов А.Н.", "Кудрявцева Л.И., Тормосина Т.М."
     */
    private boolean looksLikeTeacherName(String s) {
        return TEACHER_PATTERN.matcher(s).find();
    }

    /**
     * Build teacher schedule by iterating over all groups and collecting lessons.
     */
    private Map<String, Map<String, DaySchedule>> buildTeacherSchedule(
            Map<String, Map<String, DaySchedule>> scheduleByGroup) {

        Map<String, Map<String, DaySchedule>> result = new HashMap<>();

        for (Map.Entry<String, Map<String, DaySchedule>> groupEntry : scheduleByGroup.entrySet()) {
            String groupName = groupEntry.getKey();

            for (Map.Entry<String, DaySchedule> dayEntry : groupEntry.getValue().entrySet()) {
                String dayName = dayEntry.getKey();
                DaySchedule daySchedule = dayEntry.getValue();

                for (Lesson lesson : daySchedule.getLessons()) {
                    String teacher = lesson.getTeacher();
                    if (teacher == null || teacher.isBlank()) continue;

                    // Handle multiple teachers separated by comma
                    String[] teacherNames = teacher.split(",\\s*");
                    for (String tName : teacherNames) {
                        tName = tName.trim();
                        if (tName.isEmpty()) continue;

                        result.computeIfAbsent(tName, k -> new LinkedHashMap<>());

                        Lesson teacherLesson = new Lesson(
                                lesson.getLessonNumber(),
                                lesson.getTime(),
                                lesson.getSubject(),
                                groupName, // Store group name instead of teacher
                                lesson.getRoom(),
                                lesson.getWeekType()
                        );

                        result.get(tName)
                                .computeIfAbsent(dayName, DaySchedule::new)
                                .addLesson(teacherLesson);
                    }
                }
            }
        }

        // Ensure all day schedules in teacher schedule are sorted
        for (var dayMap : result.values()) {
            for (DaySchedule ds : dayMap.values()) {
                ds.sortLessons();
            }
        }

        return result;
    }

    // ===== Public API =====

    public Map<String, List<String>> getGroupsByCourse() {
        return Collections.unmodifiableMap(groupsByCourse);
    }

    public List<String> getGroupsForCourse(String courseName) {
        return groupsByCourse.getOrDefault(courseName, Collections.emptyList());
    }

    public Map<String, DaySchedule> getScheduleForGroup(String groupName) {
        return scheduleByGroup.getOrDefault(groupName, Collections.emptyMap());
    }

    public DaySchedule getScheduleForGroupAndDay(String groupName, String dayName) {
        Map<String, DaySchedule> groupSchedule = scheduleByGroup.get(groupName);
        if (groupSchedule == null) return null;
        return groupSchedule.get(dayName);
    }

    public Set<String> getAllTeachers() {
        return Collections.unmodifiableSet(new TreeSet<>(allTeachers));
    }

    /**
     * Get teachers whose last name starts with the given letter.
     */
    public List<String> getTeachersByLetter(String letter) {
        List<String> result = new ArrayList<>();
        for (String teacher : allTeachers) {
            if (teacher.toUpperCase().startsWith(letter.toUpperCase())) {
                result.add(teacher);
            }
        }
        Collections.sort(result);
        return result;
    }

    /**
     * Get unique first letters of teacher last names.
     */
    public List<String> getTeacherFirstLetters() {
        Set<String> letters = new TreeSet<>();
        for (String teacher : allTeachers) {
            if (!teacher.isEmpty()) {
                letters.add(teacher.substring(0, 1).toUpperCase());
            }
        }
        return new ArrayList<>(letters);
    }

    public Map<String, DaySchedule> getScheduleForTeacher(String teacherName) {
        return scheduleByTeacher.getOrDefault(teacherName, Collections.emptyMap());
    }

    public DaySchedule getScheduleForTeacherAndDay(String teacherName, String dayName) {
        Map<String, DaySchedule> teacherSchedule = scheduleByTeacher.get(teacherName);
        if (teacherSchedule == null) return null;
        return teacherSchedule.get(dayName);
    }

    public String[] getDays() {
        return DAYS;
    }

    // ===== Inner classes =====

    private static class ParsedCell {
        List<Entry> entries = new ArrayList<>();

        static class Entry {
            String subject;
            String teacher;

            Entry(String subject, String teacher) {
                this.subject = subject;
                this.teacher = teacher;
            }
        }
    }
}
