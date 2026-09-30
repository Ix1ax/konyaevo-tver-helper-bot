package dev.ix1ax.main.controller;

import dev.ix1ax.main.dto.*;
import dev.ix1ax.main.model.DaySchedule;
import dev.ix1ax.main.model.Lesson;
import dev.ix1ax.main.service.ChangesParserService;
import dev.ix1ax.main.service.ScheduleParserService;
import dev.ix1ax.main.service.ScheduleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class ScheduleApiController {

    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");

    private final ScheduleService scheduleService;
    private final ScheduleParserService scheduleParser;
    private final ChangesParserService changesParser;

    public ScheduleApiController(ScheduleService scheduleService,
                                 ScheduleParserService scheduleParser,
                                 ChangesParserService changesParser) {
        this.scheduleService = scheduleService;
        this.scheduleParser = scheduleParser;
        this.changesParser = changesParser;
    }

    @GetMapping("/info")
    public ResponseEntity<InfoResponseDto> getInfo() {
        String weekBadge = scheduleService.getCurrentWeekBadge();
        boolean isRed = weekBadge.contains("Красная");
        String today = scheduleService.getTodayName();
        String tomorrow = scheduleService.getTomorrowName();
        String changesDate = changesParser.getChangesDate();

        InfoResponseDto info = InfoResponseDto.builder()
                .weekBadge(weekBadge)
                .isRedWeek(isRed)
                .tomorrowRedWeek(scheduleService.isTomorrowRedWeek())
                .todayName(today)
                .tomorrowName(tomorrow)
                .changesDate(changesDate)
                .serverTime(OffsetDateTime.now(MOSCOW).toString())
                .build();

        return ResponseEntity.ok(info);
    }

    @GetMapping("/groups")
    public ResponseEntity<GroupsResponseDto> getGroups() {
        List<String> courses = scheduleService.getCourseNames();
        Map<String, List<String>> groupsByCourse = scheduleService.getGroupsByCourse();

        return ResponseEntity.ok(GroupsResponseDto.builder()
                .courses(courses)
                .groupsByCourse(groupsByCourse)
                .build());
    }

    @GetMapping("/teachers")
    public ResponseEntity<TeachersResponseDto> getTeachers() {
        List<String> letters = scheduleService.getTeacherFirstLetters();
        List<String> teachers = new ArrayList<>(scheduleParser.getAllTeachers());
        Collections.sort(teachers);

        return ResponseEntity.ok(TeachersResponseDto.builder()
                .letters(letters)
                .teachers(teachers)
                .build());
    }

    @GetMapping("/schedule/group/{groupName}")
    public ResponseEntity<Map<String, DayScheduleDto>> getScheduleForGroup(@PathVariable String groupName) {
        return ResponseEntity.ok(scheduleService.getResolvedGroupSchedule(groupName));
    }

    @GetMapping("/schedule/teacher/{teacherName}")
    public ResponseEntity<Map<String, DayScheduleDto>> getScheduleForTeacher(@PathVariable String teacherName) {
        return ResponseEntity.ok(scheduleService.getResolvedTeacherSchedule(teacherName));
    }

    @GetMapping("/schedule/base/group/{groupName}")
    public ResponseEntity<Map<String, DayScheduleDto>> getBaseGroup(@PathVariable String groupName) {
        return ResponseEntity.ok(scheduleService.getBaseGroupSchedule(groupName));
    }

    @GetMapping("/schedule/base/teacher/{teacherName}")
    public ResponseEntity<Map<String, DayScheduleDto>> getBaseTeacher(@PathVariable String teacherName) {
        return ResponseEntity.ok(scheduleService.getBaseTeacherSchedule(teacherName));
    }

    @GetMapping("/changes/group/{groupName}")
    public ResponseEntity<ChangesResponseDto> getChangesForGroup(@PathVariable String groupName) {
        Map<Integer, String> changes = changesParser.getChangesForGroup(groupName);
        List<ChangesResponseDto.ChangeItemDto> items = new ArrayList<>();

        for (Map.Entry<Integer, String> e : changes.entrySet()) {
            items.add(ChangesResponseDto.ChangeItemDto.builder()
                    .slot(e.getKey())
                    .groupName(groupName)
                    .text(e.getValue())
                    .canceled(isCancellation(e.getValue()))
                    .build());
        }
        items.sort(Comparator.comparingInt(ChangesResponseDto.ChangeItemDto::getSlot));

        return ResponseEntity.ok(ChangesResponseDto.builder()
                .date(changesParser.getChangesDate())
                .target(groupName)
                .items(items)
                .build());
    }

    @GetMapping("/classrooms/free")
    public ResponseEntity<ClassroomsResponseDto> getFreeClassrooms(
            @RequestParam(required = false) String day,
            @RequestParam(required = false, defaultValue = "1") int slot,
            @RequestParam(required = false) String weekType) {

        if (day == null || day.isBlank()) {
            day = scheduleService.getTodayName();
            if (day.isEmpty()) {
                day = "Понедельник";
            }
        }

        if (weekType == null || weekType.isBlank()) {
            weekType = scheduleService.getCurrentWeekBadge().contains("Красная") ? "red" : "blue";
        }

        if (slot < 1 || slot > 6 || !Arrays.asList(scheduleParser.getDays()).contains(day)
                || !(weekType.equalsIgnoreCase("red") || weekType.equalsIgnoreCase("blue"))) {
            return ResponseEntity.badRequest().build();
        }
        List<String> allRooms = scheduleParser.getAllRooms();
        Map<String, ClassroomsResponseDto.OccupiedRoomDto> occupiedMap = new LinkedHashMap<>();

        Map<String, List<String>> groupsByCourse = scheduleService.getGroupsByCourse();
        for (List<String> groups : groupsByCourse.values()) {
            for (String groupName : groups) {
                DaySchedule ds = scheduleParser.getScheduleForGroupAndDay(groupName, day);
                if (ds == null || !ds.hasLessons()) continue;

                for (Lesson lesson : ds.getLessons()) {
                    if (lesson.getLessonNumber() == slot) {
                        if (lesson.getWeekType() == null || lesson.getWeekType().equalsIgnoreCase(weekType)) {
                            String room = lesson.getRoom();
                            if (room != null && !room.isBlank()) {
                                occupiedMap.putIfAbsent(room.trim(), ClassroomsResponseDto.OccupiedRoomDto.builder()
                                        .room(room.trim())
                                        .subject(lesson.getSubject())
                                        .teacher(lesson.getTeacher())
                                        .groupName(groupName)
                                        .time(lesson.getTime())
                                        .build());
                            }
                        }
                    }
                }
            }
        }

        List<String> freeRooms = new ArrayList<>();
        for (String r : allRooms) {
            if (!occupiedMap.containsKey(r)) {
                freeRooms.add(r);
            }
        }

        List<ClassroomsResponseDto.OccupiedRoomDto> occupiedList = new ArrayList<>(occupiedMap.values());
        occupiedList.sort(Comparator.comparing(ClassroomsResponseDto.OccupiedRoomDto::getRoom));

        return ResponseEntity.ok(ClassroomsResponseDto.builder()
                .day(day)
                .slot(slot)
                .weekType(weekType)
                .allRooms(allRooms)
                .freeRooms(freeRooms)
                .occupiedRooms(occupiedList)
                .build());
    }

    @GetMapping("/teachers/search")
    public ResponseEntity<List<String>> searchTeachers(@RequestParam(defaultValue = "") String q) {
        String query = q.trim().toLowerCase();
        List<String> result = new ArrayList<>();
        for (String t : scheduleParser.getAllTeachers()) {
            if (t.toLowerCase().contains(query)) {
                result.add(t);
            }
        }
        Collections.sort(result);
        return ResponseEntity.ok(result);
    }

    private boolean isCancellation(String text) {
        return changesParser.isCancellation(text);
    }
}
