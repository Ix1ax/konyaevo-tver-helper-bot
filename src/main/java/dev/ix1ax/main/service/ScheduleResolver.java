package dev.ix1ax.main.service;

import dev.ix1ax.main.util.Subgroups;

import dev.ix1ax.main.dto.DayScheduleDto;
import dev.ix1ax.main.dto.LessonDto;
import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.Lesson;
import java.util.*;

/** Собирает расписание с заменами для бота и Mini App по одним правилам. */
final class ScheduleResolver {
    private final ScheduleParserService scheduleParser;
    private final ChangesParserService changesParser;

    ScheduleResolver(ScheduleParserService scheduleParser, ChangesParserService changesParser) {
        this.scheduleParser = scheduleParser;
        this.changesParser = changesParser;
    }

    public Map<String, DayScheduleDto> getScheduleForGroup(String groupName) {
        return getScheduleForGroup(groupName, true);
    }

    public Map<String, DayScheduleDto> getScheduleForGroup(String groupName, boolean includeChanges) {
        Map<String, DaySchedule> groupSchedule = scheduleParser.getScheduleForGroup(groupName);
        Map<Integer, String> groupChanges = includeChanges ? changesParser.getChangesForGroup(groupName) : Map.of();
        String targetDay = includeChanges ? changesParser.getOverlayDayName() : null;

        Map<String, DayScheduleDto> result = new LinkedHashMap<>();
        for (String day : scheduleParser.getDays()) {
            DaySchedule daySchedule = groupSchedule.get(day);
            List<LessonDto> lessonDtos = new ArrayList<>();
            boolean isChangesDay = targetDay != null && targetDay.equalsIgnoreCase(day);

            Set<Integer> handledSlots = new HashSet<>();

            if (daySchedule != null && daySchedule.hasLessons()) {
                for (Lesson lesson : daySchedule.getLessons()) {
                    int slot = lesson.getLessonNumber();
                    if (lesson.getWeekType() == null || lesson.getWeekType().equals(changesParser.getTargetWeekType())) {
                        handledSlots.add(slot);
                    }

                    boolean changed = false;
                    String changeText = null;
                    boolean canceled = false;
                    String subject = lesson.getSubject();
                    String teacher = lesson.getTeacher();
                    String room = lesson.getRoom();
                    String type = "Пара";

                    String originalSubject = null;
                    String originalTeacher = null;
                    String originalRoom = null;

                    if (isChangesDay && groupChanges.containsKey(slot)
                            && (lesson.getWeekType() == null || lesson.getWeekType().equals(changesParser.getTargetWeekType()))) {
                        String ch = groupChanges.get(slot);
                        if (ch != null && !ch.isBlank()) {
                            changed = true;
                            changeText = ch;
                            originalSubject = lesson.getSubject();
                            originalTeacher = lesson.getTeacher();
                            originalRoom = lesson.getRoom();
                            canceled = changesParser.isCancellation(ch);
                            if (canceled) {
                                type = "Отмена";
                            } else {
                                type = "Замена";
                                ParsedChange parsed = parseChangeCell(ch);
                                if (!parsed.subject.isEmpty()) subject = parsed.subject;
                                teacher = parsed.teacher;
                                room = parsed.room;
                            }
                        }
                    }

                    lessonDtos.add(LessonDto.builder()
                            .lessonNumber(slot)
                            .time(lesson.getTime())
                            .subject(subject)
                            .teacher(teacher)
                            .room(room)
                            .weekType(lesson.getWeekType())
                            .groupName(groupName)
                            .type(type)
                            .changed(changed)
                            .changeText(changeText)
                            .canceled(canceled)
                            .originalSubject(originalSubject)
                            .originalTeacher(originalTeacher)
                            .originalRoom(originalRoom)
                            .build());
                }
            }

            // Добавляем назначенные пары в свободные слоты основного расписания.
            if (isChangesDay && !groupChanges.isEmpty()) {
                for (Map.Entry<Integer, String> entry : groupChanges.entrySet()) {
                    int slot = entry.getKey();
                    if (!handledSlots.contains(slot)) {
                        String ch = entry.getValue();
                        if (ch != null && !ch.isBlank()) {
                            boolean canceled = changesParser.isCancellation(ch);
                            if (canceled) continue;
                            ParsedChange parsed = parseChangeCell(ch);
                            lessonDtos.add(LessonDto.builder()
                                    .lessonNumber(slot)
                                    .time(getSlotTime(groupName, day, slot))
                                    .weekType(changesParser.getTargetWeekType())
                                    .subject(parsed.subject.isEmpty() ? "Замена" : parsed.subject)
                                    .teacher(parsed.teacher)
                                    .room(parsed.room)
                                    .groupName(groupName)
                                    .type(canceled ? "Отмена" : "Замена")
                                    .changed(true)
                                    .changeText(ch)
                                    .canceled(canceled)
                                    .build());
                        }
                    }
                }
            }

            lessonDtos.sort(Comparator.comparingInt(LessonDto::getLessonNumber)
                    .thenComparing(LessonDto::getGroupName));

            result.put(day, DayScheduleDto.builder()
                    .dayName(day)
                    .lessons(lessonDtos)
                    .hasLessons(!lessonDtos.isEmpty())
                    .build());
        }

        return result;
    }

    public Map<String, DayScheduleDto> getScheduleForTeacher(String teacherName) {
        return getScheduleForTeacher(teacherName, true);
    }

    public Map<String, DayScheduleDto> getScheduleForTeacher(String teacherName, boolean includeChanges) {
        Map<String, DaySchedule> teacherSchedule = scheduleParser.getScheduleForTeacher(teacherName);
        Map<String, Map<Integer, String>> allChanges = includeChanges ? changesParser.getAllChanges() : Map.of();
        String targetDay = includeChanges ? changesParser.getOverlayDayName() : null;

        Map<String, DayScheduleDto> result = new LinkedHashMap<>();
        for (String day : scheduleParser.getDays()) {
            DaySchedule daySchedule = teacherSchedule.get(day);
            List<LessonDto> lessonDtos = new ArrayList<>();
            boolean isChangesDay = targetDay != null && targetDay.equalsIgnoreCase(day);

            Set<String> addedKeys = new HashSet<>();

            if (daySchedule != null && daySchedule.hasLessons()) {
                for (Lesson lesson : daySchedule.getLessons()) {
                    int slot = lesson.getLessonNumber();
                    String grp = lesson.getTeacher(); // В расписании преподавателя здесь хранится группа.
                    boolean changed = false;
                    boolean canceled = false;
                    String changeText = null;
                    String subject = lesson.getSubject();
                    String room = lesson.getRoom();
                    String type = "Пара";
                    String originalSubject = null;
                    String originalRoom = null;

                    if (isChangesDay && (lesson.getWeekType() == null
                            || lesson.getWeekType().equals(changesParser.getTargetWeekType()))) {
                        Map<Integer, String> grpChanges = changesParser.getChangesForGroup(grp);
                        if (grpChanges.containsKey(slot)) {
                            String ch = grpChanges.get(slot);
                            if (ch != null && !ch.isBlank()) {
                                changed = true;
                                changeText = ch;
                                originalSubject = lesson.getSubject();
                                originalRoom = lesson.getRoom();
                                if (changesParser.isCancellation(ch) || !changesParser.teacherMatches(ch, teacherName)) {
                                    // У этого преподавателя пара отменена, даже если группа занимается с другим.
                                    canceled = true;
                                    type = "Отмена";
                                } else {
                                    type = "Замена";
                                    ParsedChange parsed = parseChangeCell(ch);
                                    if (!parsed.subject.isEmpty()) subject = parsed.subject;
                                    room = Subgroups.teacherRoom(parsed.teacher, parsed.room, teacherName);
                                }
                            }
                        }
                    }

                    if (lesson.getWeekType() == null || lesson.getWeekType().equals(changesParser.getTargetWeekType())) {
                        addedKeys.add(slot + ":" + grp);
                    }

                    lessonDtos.add(LessonDto.builder()
                            .lessonNumber(slot)
                            .time(lesson.getTime())
                            .subject(subject)
                            .teacher(teacherName)
                            .room(room)
                            .weekType(lesson.getWeekType())
                            .groupName(grp)
                            .type(type)
                            .changed(changed)
                            .changeText(changeText)
                            .canceled(canceled)
                            .originalSubject(originalSubject)
                            .originalTeacher(changed ? teacherName : null)
                            .originalRoom(originalRoom)
                            .build());
                }
            }

            // Ищем назначения преподавателя в остальных группах.
            if (isChangesDay && !allChanges.isEmpty()) {
                for (Map.Entry<String, Map<Integer, String>> grpEntry : allChanges.entrySet()) {
                    String grpName = grpEntry.getKey();
                    for (Map.Entry<Integer, String> slotEntry : grpEntry.getValue().entrySet()) {
                        int slot = slotEntry.getKey();
                        String val = slotEntry.getValue();

                        if (!changesParser.isCancellation(val) && changesParser.teacherMatches(val, teacherName)) {
                            String key = slot + ":" + grpName;
                            if (!addedKeys.contains(key)) {
                                addedKeys.add(key);
                                ParsedChange parsed = parseChangeCell(val);
                                lessonDtos.add(LessonDto.builder()
                                        .lessonNumber(slot)
                                        .time(getSlotTime(grpName, day, slot))
                                    .weekType(changesParser.getTargetWeekType())
                                        .subject(parsed.subject.isEmpty() ? "Замена" : parsed.subject)
                                        .teacher(teacherName)
                                        .groupName(grpName)
                                        .room(Subgroups.teacherRoom(parsed.teacher, parsed.room, teacherName))
                                        .type("Замена")
                                        .changed(true)
                                        .changeText(val)
                                        .canceled(false)
                                        .build());
                            }
                        }
                    }
                }
            }

            lessonDtos.sort(Comparator.comparingInt(LessonDto::getLessonNumber)
                    .thenComparing(LessonDto::getGroupName));

            result.put(day, DayScheduleDto.builder()
                    .dayName(day)
                    .lessons(lessonDtos)
                    .hasLessons(!lessonDtos.isEmpty())
                    .build());
        }

        return result;
    }

    private static class ParsedChange {
        String subject = "";
        String teacher = "";
        String room = "";
    }

    private ParsedChange parseChangeCell(String text) {
        ParsedChange p = new ParsedChange();
        if (text == null || text.isBlank()) return p;
        String[] lines = text.split("\n");
        List<String> nonBlank = new ArrayList<>();
        for (String l : lines) {
            String tr = l.trim();
            if (!tr.isEmpty()) nonBlank.add(tr);
        }
        if (nonBlank.isEmpty()) return p;

        p.subject = nonBlank.get(0);

        for (int i = 1; i < nonBlank.size(); i++) {
            String line = nonBlank.get(i);
            if (line.toLowerCase().contains("ауд") || line.matches("(?iu)^(?:[0-9]{1,4}(?:-?[а-яa-z])?(?:\\s*/\\s*[0-9]{1,4}(?:-?[а-яa-z])?)*|с/[зл]|ч/з)$")) {
                p.room = line.replaceAll("(?i)ауд\\.?", "").trim();
            } else if (p.teacher.isEmpty()) {
                p.teacher = line;
            }
        }
        return p;
    }

    private String getSlotTime(String groupName, String day, int slot) {
        DaySchedule schedule = scheduleParser.getScheduleForGroup(groupName).get(day);
        if (schedule != null) {
            String bellTime = schedule.getSlotTime(slot);
            if (bellTime != null && !bellTime.isBlank()) return bellTime;
            for (Lesson lesson : schedule.getLessons()) {
                if (lesson.getLessonNumber() == slot) return lesson.getTime();
            }
        }
        // Если звонков действительно нет в источнике, не подставляем время другого дня или курса.
        return "Время уточняется";
    }
}
