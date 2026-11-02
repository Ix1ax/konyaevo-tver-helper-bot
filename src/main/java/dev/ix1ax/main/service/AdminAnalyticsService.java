package dev.ix1ax.main.service;

import dev.ix1ax.main.model.Donation;
import dev.ix1ax.main.model.UserSettings;
import dev.ix1ax.main.repository.ActivityDayRepository;
import dev.ix1ax.main.repository.DonationRepository;
import dev.ix1ax.main.repository.UserSettingsRepository;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Сводка для графиков админки: активность по дням, курсы и группы, огонёк, поддержка.
 * Считается по запросу из базы; пользователей сотни, поэтому достаточно одного прохода в памяти.
 */
@Service
public class AdminAnalyticsService {
    static final ZoneId MOSCOW = ZoneId.of("Europe/Moscow");
    private static final int TOP = 10;

    public record DayPoint(String day, long active, long fresh) {}
    public record Bar(String label, long value, long today) {}
    public record GroupStreak(String name, long lit, long today, int longest) {}
    public record Streak(long lit, long burning, long countedToday, int record, List<Bar> buckets, List<GroupStreak> groups, List<Bar> looks) {}
    public record StarDay(String day, long stars) {}
    public record Payment(String at, int stars, String target) {}
    public record Support(long stars, long supporters, long payments, List<StarDay> days, List<Payment> recent) {}
    public record Analytics(int days, List<DayPoint> activity, long activeToday, long activeYesterday,
                            List<Bar> courses, List<Bar> groups, Streak streak, Support support) {}

    private final UserSettingsRepository users;
    private final ActivityDayRepository activity;
    private final MascotService mascot;
    private final DonationRepository donations;

    public AdminAnalyticsService(UserSettingsRepository users, ActivityDayRepository activity, MascotService mascot, DonationRepository donations) {
        this.users = users; this.activity = activity; this.mascot = mascot; this.donations = donations;
    }

    public Analytics analytics(int days, LocalDate today) {
        int range = Math.max(7, Math.min(90, days));
        LocalDate from = today.minusDays(range - 1);
        List<UserSettings> all = users.findAll();
        Map<Long, UserSettings> byId = all.stream().collect(Collectors.toMap(UserSettings::getChatId, Function.identity(), (a, b) -> a));

        // Активные — из отметок по дням, новые — по дате первого появления.
        Map<LocalDate, Long> active = new HashMap<>();
        for (Object[] row : activity.countByDay(from)) active.put((LocalDate) row[0], ((Number) row[1]).longValue());
        Map<LocalDate, Long> fresh = all.stream().filter(u -> u.getFirstSeen() != null)
                .map(u -> day(u.getFirstSeen())).filter(d -> !d.isBefore(from))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<DayPoint> points = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1))
            points.add(new DayPoint(d.toString(), active.getOrDefault(d, 0L), fresh.getOrDefault(d, 0L)));

        Instant startToday = today.atStartOfDay(MOSCOW).toInstant();
        java.util.function.Predicate<UserSettings> activeNow = u -> u.getLastActive() != null && !u.getLastActive().isBefore(startToday);
        long activeToday = Math.max(active.getOrDefault(today, 0L), all.stream().filter(activeNow).count());

        // Курсы и группы: сколько привязано и сколько из них заходили сегодня.
        Map<String, long[]> courses = new TreeMap<>(), groups = new HashMap<>();
        for (UserSettings user : all) {
            String target = target(user);
            if (target == null) continue;
            boolean now = activeNow.test(user);
            courses.computeIfAbsent(course(user), k -> new long[2]);
            long[] c = courses.get(course(user)); c[0]++; if (now) c[1]++;
            long[] g = groups.computeIfAbsent(target, k -> new long[2]); g[0]++; if (now) g[1]++;
        }

        return new Analytics(range, points, activeToday, active.getOrDefault(today.minusDays(1), 0L),
                courses.entrySet().stream().map(e -> new Bar(e.getKey(), e.getValue()[0], e.getValue()[1])).toList(),
                top(groups), streak(today, byId), support(byId));
    }

    private Streak streak(LocalDate today, Map<Long, UserSettings> byId) {
        var everyone = mascot.streakRows(today, byId);
        var rows = everyone.stream().filter(r -> r.best() >= 1).toList();
        long burning = rows.stream().filter(r -> r.streak() >= 1).count();
        String[][] ranges = {{"1 день", "1", "1"}, {"2–3 дня", "2", "3"}, {"4–6 дней", "4", "6"}, {"7–13 дней", "7", "13"}, {"14+ дней", "14", "100000"}};
        List<Bar> buckets = Arrays.stream(ranges).map(r -> new Bar(r[0], rows.stream()
                .filter(x -> x.streak() >= Integer.parseInt(r[1]) && x.streak() <= Integer.parseInt(r[2])).count(), 0)).toList();
        Map<String, List<MascotService.StreakRow>> byGroup = rows.stream()
                .collect(Collectors.groupingBy(r -> Optional.ofNullable(target(byId.get(r.chatId()))).orElse("Без расписания")));
        List<GroupStreak> groups = byGroup.entrySet().stream()
                .map(e -> new GroupStreak(e.getKey(), e.getValue().size(), e.getValue().stream().filter(MascotService.StreakRow::countedToday).count(),
                        e.getValue().stream().mapToInt(MascotService.StreakRow::streak).max().orElse(0)))
                .sorted(Comparator.comparingLong(GroupStreak::lit).reversed().thenComparing(GroupStreak::name)).limit(TOP).toList();
        Map<String, Long> looks = everyone.stream()
                .collect(Collectors.groupingBy(r -> r.look() == null ? "classic" : r.look(), Collectors.counting()));
        List<Bar> lookBars = looks.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(e -> new Bar(mascot.lookName(e.getKey()), e.getValue(), 0)).toList();
        return new Streak(rows.size(), burning, rows.stream().filter(MascotService.StreakRow::countedToday).count(),
                rows.stream().mapToInt(MascotService.StreakRow::best).max().orElse(0), buckets, groups, lookBars);
    }

    private Support support(Map<Long, UserSettings> byId) {
        List<Donation> all = donations.findAll();
        Map<LocalDate, Long> byDay = all.stream().collect(Collectors.groupingBy(d -> day(d.getPaidAt()), TreeMap::new, Collectors.summingLong(Donation::getStars)));
        List<Payment> recent = all.stream().sorted(Comparator.comparing(Donation::getPaidAt).reversed()).limit(TOP)
                .map(d -> new Payment(d.getPaidAt().toString(), d.getStars(), Optional.ofNullable(target(byId.get(d.getChatId()))).orElse("—"))).toList();
        return new Support(all.stream().mapToLong(Donation::getStars).sum(), all.stream().map(Donation::getChatId).distinct().count(), all.size(),
                byDay.entrySet().stream().map(e -> new StarDay(e.getKey().toString(), e.getValue())).toList(), recent);
    }

    private static List<Bar> top(Map<String, long[]> counts) {
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, long[]>>comparingLong(e -> e.getValue()[0]).reversed().thenComparing(Map.Entry::getKey))
                .limit(TOP).map(e -> new Bar(e.getKey(), e.getValue()[0], e.getValue()[1])).toList();
    }

    static LocalDate day(Instant instant) { return instant.atZone(MOSCOW).toLocalDate(); }

    /** Группа или «Преп. ФИО»; null — профиль не настроен. */
    static String target(UserSettings user) {
        if (user == null) return null;
        if ("teacher".equals(user.getRole()) && user.getTeacherName() != null && !user.getTeacherName().isBlank()) return "Преп. " + user.getTeacherName();
        if ("student".equals(user.getRole()) && user.getGroupName() != null && !user.getGroupName().isBlank()) return user.getGroupName();
        return null;
    }

    /** Курс из профиля или из первой цифры группы («2-МР3» — 2 курс). */
    static String course(UserSettings user) {
        if ("teacher".equals(user.getRole())) return "Преподаватели";
        if (user.getCourse() != null && user.getCourse() > 0) return user.getCourse() + " курс";
        String group = user.getGroupName();
        return group != null && !group.isEmpty() && Character.isDigit(group.charAt(0)) ? group.charAt(0) + " курс" : "Без курса";
    }
}
