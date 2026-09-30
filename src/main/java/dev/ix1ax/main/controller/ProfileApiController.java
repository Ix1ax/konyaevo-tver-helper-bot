package dev.ix1ax.main.controller;

import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Set;

/** Настройки миниапки и чат-бота хранятся в одной записи пользователя. */
@RestController
@RequestMapping("/api/profile")
public class ProfileApiController {
    private final UserActivityService activity;
    private final TelegramMiniAppAuth auth;
    private final ScheduleService users;
    private final ScheduleParserService schedule;
    public ProfileApiController(TelegramMiniAppAuth auth, ScheduleService users, ScheduleParserService schedule) {
        this(auth, users, schedule, null);
    }
    @org.springframework.beans.factory.annotation.Autowired
    public ProfileApiController(TelegramMiniAppAuth auth, ScheduleService users, ScheduleParserService schedule, UserActivityService activity) {
        this.activity = activity;
        this.auth = auth; this.users = users; this.schedule = schedule;
    }
    public record Profile(String role, String group, String teacher, boolean changes, boolean tomorrow,
                          String time, Set<Integer> days) {}
    public record Update(String role, String group, String teacher, Boolean changes, Boolean tomorrow,
                         String time, Set<Integer> days) {}
    @GetMapping
    public ResponseEntity<Profile> get(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data) {
        long id = auth.requireUserId(data);
        UserSettings user = users.getOrCreateUser(id);
        if (activity != null) activity.record(id);
        return response(user);
    }
    @PostMapping("/activity")
    public ResponseEntity<Void> activity(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data) {
        long id = auth.requireUserId(data);
        if (activity != null) activity.record(id);
        return ResponseEntity.noContent().build();
    }
    @PutMapping
    public ResponseEntity<Profile> put(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data,
                                       @RequestBody Update update) {
        long id = auth.requireUserId(data);
        // Сначала проверяем весь запрос: ошибка не должна частично менять настройки.
        if (update.role() != null && !Set.of("student", "teacher").contains(update.role())) bad();
        if (update.group() != null && !update.group().isEmpty() &&
                schedule.getGroupsByCourse().values().stream().noneMatch(g -> g.contains(update.group()))) bad();
        if (update.teacher() != null && !update.teacher().isEmpty() && !schedule.getAllTeachers().contains(update.teacher())) bad();
        if (update.time() != null && !update.time().matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) bad();
        if (update.days() != null && update.days().stream().anyMatch(d -> d == null || d < 1 || d > 7)) bad();
        UserSettings user = users.getOrCreateUser(id);
        String role = update.role() == null ? user.getRole() : update.role();
        String group = update.group() == null ? user.getGroupName() : update.group();
        String teacher = update.teacher() == null ? user.getTeacherName() : update.teacher();
        boolean changes = update.changes() == null ? Boolean.TRUE.equals(user.getNotifyEnabled()) : update.changes();
        boolean tomorrow = update.tomorrow() == null ? Boolean.TRUE.equals(user.getNotifyTomorrow()) : update.tomorrow();
        if ((changes || tomorrow) && !("student".equals(role) && group != null && !group.isBlank()) &&
                !("teacher".equals(role) && teacher != null && !teacher.isBlank())) bad();
        user.setRole(role); user.setGroupName(group); user.setTeacherName(teacher);
        user.setNotifyEnabled(changes); user.setNotifyTomorrow(tomorrow);
        if (update.time() != null) user.setNotifyTime(update.time());
        else if (user.getNotifyTime() == null) user.setNotifyTime("18:00");
        if (update.days() != null) user.setNotifyDaysSet(update.days());
        if (update.group() != null && !update.group().isBlank()) {
            schedule.getGroupsByCourse().entrySet().stream().filter(entry -> entry.getValue().contains(update.group())).findFirst().ifPresent(entry -> {
                String number = entry.getKey().replaceAll("\\D", "");
                if (!number.isEmpty()) user.setCourse(Integer.parseInt(number));
            });
        }
        users.saveUser(user);
        if (activity != null) activity.record(id);
        return response(user);
    }
    private static ResponseEntity<Profile> response(UserSettings u) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Profile(u.getRole(), u.getGroupName(),
                u.getTeacherName(), Boolean.TRUE.equals(u.getNotifyEnabled()), Boolean.TRUE.equals(u.getNotifyTomorrow()),
                u.getNotifyTime() == null ? "18:00" : u.getNotifyTime(), u.getNotifyDaysSet()));
    }
    private static void bad() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте расписание, время и дни уведомлений"); }
}
