package dev.ix1ax.main.controller;

import dev.ix1ax.main.service.ScheduleShareService;
import dev.ix1ax.main.service.TelegramMiniAppAuth;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/share")
public class ScheduleShareController {
    private final TelegramMiniAppAuth auth;
    private final ScheduleShareService shares;
    public ScheduleShareController(TelegramMiniAppAuth auth, ScheduleShareService shares) { this.auth = auth; this.shares = shares; }
    public record Request(String jpeg, String caption) {}
    @PostMapping("/prepare")
    public ResponseEntity<ScheduleShareService.Prepared> prepare(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData, @RequestBody Request request) {
        long user = auth.requireUserId(initData);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(shares.prepare(user, request.jpeg(), request.caption()));
    }
    @GetMapping(value = "/images/{id}.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=konyaevo-schedule.jpg")
                .body(shares.image(id));
    }
}
