package dev.ix1ax.main.controller;

import dev.ix1ax.main.service.AdminService;
import dev.ix1ax.main.service.DayOffService;
import dev.ix1ax.main.service.TelegramMiniAppAuth;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Праздники и каникулы для администратора: ближайшие периоды, добавить и убрать выходные. */
@RestController
@RequestMapping("/api/admin/days-off")
public class AdminDaysOffController {
    private static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    /** Сколько дней вперёд показывать и насколько назад можно отметить забытый выходной. */
    private static final int AHEAD = 120, BACK = 14;
    private final TelegramMiniAppAuth auth;
    private final AdminService admins;
    private final DayOffService daysOff;
    public AdminDaysOffController(TelegramMiniAppAuth auth, AdminService admins, DayOffService daysOff) {
        this.auth = auth; this.admins = admins; this.daysOff = daysOff;
    }
    public record Request(String from, String to) {}

    private void requireAdmin(String data) {
        if (!admins.isAdmin(auth.requireUserId(data))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<Map<String, Object>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(Map.of("periods", daysOff.upcoming(LocalDate.now(MOSCOW), AHEAD)));
    }

    private static LocalDate parse(String value) {
        try { return LocalDate.parse(value); }
        catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Дата в формате ГГГГ-ММ-ДД"); }
    }

    /** Период «с … по»; без «по» — один день. */
    private static LocalDate[] period(String from, String to) {
        LocalDate start = parse(from), end = to == null || to.isBlank() ? start : parse(to);
        if (end.isBefore(start)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Конец периода раньше начала");
        if (ChronoUnit.DAYS.between(start, end) >= DayOffService.MAX_PERIOD)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не больше " + DayOffService.MAX_PERIOD + " дней за раз");
        return new LocalDate[]{start, end};
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> get(@RequestHeader(value = "X-Telegram-Init-Data", required = false) String data) {
        requireAdmin(data);
        return list();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> add(@RequestHeader(value = "X-Telegram-Init-Data", required = false) String data, @RequestBody Request request) {
        requireAdmin(data);
        LocalDate[] period = period(request == null ? null : request.from(), request == null ? null : request.to());
        // Забытый выходной можно отметить задним числом: серии тех, кто ещё не заходил, оживут.
        if (period[0].isBefore(LocalDate.now(MOSCOW).minusDays(BACK)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Можно отметить не раньше чем за " + BACK + " дней");
        daysOff.add(period[0], period[1]);
        return list();
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> remove(@RequestHeader(value = "X-Telegram-Init-Data", required = false) String data,
                                                      @RequestParam String from, @RequestParam(required = false) String to) {
        requireAdmin(data);
        LocalDate[] period = period(from, to);
        daysOff.remove(period[0], period[1]);
        return list();
    }
}
