package dev.ix1ax.main.controller;

import dev.ix1ax.main.repository.UserSettingsRepository;
import dev.ix1ax.main.service.*;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin")
public class AdminApiController {
    private final UserActivityService activity;
    private final TelegramMiniAppAuth auth;
    private final AdminService admins;
    private final UserSettingsRepository users;
    private final ScheduleParserService schedule;
    private final ChangesParserService changes;

    public AdminApiController(TelegramMiniAppAuth auth, AdminService admins, UserSettingsRepository users,
                              ScheduleParserService schedule, ChangesParserService changes) {
        this(auth, admins, users, schedule, changes, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AdminApiController(TelegramMiniAppAuth auth, AdminService admins, UserSettingsRepository users,
                              ScheduleParserService schedule, ChangesParserService changes, UserActivityService activity) {
        this.activity = activity;
        this.auth = auth;
        this.admins = admins;
        this.users = users;
        this.schedule = schedule;
        this.changes = changes;
    }

    public record Overview(long users, long students, long teachersUsingBot, long unconfigured, long notifications, int groups, int teachers,
                           int changedGroups, int changes, String changesDate, long activeDay, long activeWeek, long activeMonth, long newWeek) {}

    @GetMapping("/overview")
    public ResponseEntity<Overview> overview(@RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData) {
        long userId = auth.requireUserId(initData);
        if (!admins.isAdmin(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        var allChanges = changes.getAllChanges();
        long total = users.count();
        long students = users.countByRole("student");
        long teachers = users.countByRole("teacher");
        var active = activity == null ? new UserActivityService.Snapshot(0, 0, 0, 0, 0) : activity.snapshot();
        var overview = new Overview(total, students, teachers, Math.max(0, total - students - teachers), users.countWithNotifications(),
                schedule.getGroupsByCourse().values().stream().mapToInt(java.util.List::size).sum(),
                schedule.getAllTeachers().size(), allChanges.size(),
                allChanges.values().stream().mapToInt(java.util.Map::size).sum(), changes.getChangesDate(), active.day(), active.week(), active.month(), active.newWeek());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(overview);
    }
}
