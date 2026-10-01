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
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScheduleShareController.class);
    public record Request(String jpeg, String caption) {}
    public record Diagnostic(String event, String imageId, String detail, String platform, String version) {}
    private static String safe(String value) {
        if (value == null) return "";
        return value.replaceAll("[^a-zA-Z0-9_.: -]", "_").substring(0, Math.min(100, value.length()));
    }
    @PostMapping("/diagnostic")
    public ResponseEntity<Void> diagnostic(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData,
            @RequestBody Diagnostic request) {
        long user = auth.requireUserId(initData);
        if (!java.util.Set.of("share_call", "share_callback", "share_sent", "share_failed", "download_call", "download_callback", "client_exception").contains(request.event()))
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST);
        log.info("[SCHEDULE SHARE] client user={} event={} image={} detail={} platform={} version={}",
                user, request.event(), safe(request.imageId()), safe(request.detail()), safe(request.platform()), safe(request.version()));
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/prepare")
    public ResponseEntity<ScheduleShareService.Prepared> prepare(
            @RequestHeader(value = "X-Telegram-Init-Data", required = false) String initData, @RequestBody Request request) {
        long user = auth.requireUserId(initData);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(shares.prepare(user, request.jpeg(), request.caption()));
    }
    @GetMapping(value = "/images/{id}.jpg", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> image(@PathVariable String id, jakarta.servlet.http.HttpServletRequest request) {
        long started = System.nanoTime();
        byte[] bytes;
        try { bytes = shares.image(id); }
        catch (org.springframework.web.server.ResponseStatusException error) {
            log.info("[SCHEDULE SHARE] image={} method={} status={} agent={}", safe(id), request.getMethod(), error.getStatusCode().value(), safe(request.getHeader("User-Agent")));
            throw error;
        }
        log.info("[SCHEDULE SHARE] image={} method={} status=200 bytes={} elapsedMs={} agent={}",
                safe(id), request.getMethod(), bytes.length, (System.nanoTime() - started) / 1_000_000, safe(request.getHeader("User-Agent")));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=konyaevo-schedule.jpg")
                .body(bytes);
    }
}
