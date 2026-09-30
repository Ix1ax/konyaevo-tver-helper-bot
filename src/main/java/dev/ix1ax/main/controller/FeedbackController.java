package dev.ix1ax.main.controller;

import dev.ix1ax.main.model.FeedbackEntry;
import dev.ix1ax.main.repository.FeedbackRepository;
import dev.ix1ax.main.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.data.domain.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.Instant;
import java.util.*;

@RestController
public class FeedbackController {
    private final TelegramMiniAppAuth auth;
    private final AdminService admins;
    private final FeedbackRepository feedback;
    public FeedbackController(TelegramMiniAppAuth auth, AdminService admins, FeedbackRepository feedback) {
        this.auth=auth; this.admins=admins; this.feedback=feedback;
    }
    public record Status(boolean submitted) {}
    public record Rating(int stars, String comment) {}
    public record Item(Long id, long userId, int stars, String comment, Instant createdAt) {}
    public record Reviews(List<Item> items, int page, int pages, long total, double average, Map<Integer, Long> distribution) {}

    @GetMapping("/api/feedback/status")
    public ResponseEntity<Status> status(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data) {
        long user=auth.requireUserId(data);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status(feedback.existsByUserId(user)));
    }
    @PostMapping("/api/feedback")
    public ResponseEntity<Status> submit(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data, @RequestBody Rating rating) {
        long user=auth.requireUserId(data);
        String text=rating.comment() == null ? "" : rating.comment().trim();
        if (rating.stars() < 1 || rating.stars() > 5 || text.length() > 2000 || rating.stars() < 5 && text.length() < 3)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для оценки 1–4 напишите, что стоит улучшить");
        if (feedback.existsByUserId(user)) throw new ResponseStatusException(HttpStatus.CONFLICT, "Оценка уже сохранена");
        try { feedback.saveAndFlush(new FeedbackEntry(user, rating.stars(), text)); }
        catch (DataIntegrityViolationException e) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Оценка уже сохранена"); }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status(true));
    }
    @GetMapping("/api/admin/feedback")
    public ResponseEntity<Reviews> reviews(@RequestHeader(value="X-Telegram-Init-Data", required=false) String data,
                                          @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        long user=auth.requireUserId(data);
        if (!admins.isAdmin(user)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        if (page < 0 || page > 100000 || size < 1 || size > 50) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var result=feedback.findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
        Map<Integer, Long> stars=new TreeMap<>();
        for (int value=1; value<=5; value++) stars.put(value, 0L);
        for (Object[] row: feedback.distribution()) stars.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        long count=stars.values().stream().mapToLong(Long::longValue).sum();
        double average=count == 0 ? 0 : stars.entrySet().stream().mapToDouble(e -> e.getKey() * e.getValue()).sum() / count;
        var items=result.getContent().stream().map(f -> new Item(f.getId(), f.getUserId(), f.getStars(), f.getComment(), f.getCreatedAt())).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Reviews(items, page, result.getTotalPages(), result.getTotalElements(), average, stars));
    }
}
