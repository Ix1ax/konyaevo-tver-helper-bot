package dev.ix1ax.main.service;

import dev.ix1ax.main.dto.DayScheduleDto;
import dev.ix1ax.main.dto.LessonDto;
import dev.ix1ax.main.model.ShortDay;
import dev.ix1ax.main.repository.ShortDayRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ShortDayServiceTest {
    private static final LocalDate THU = LocalDate.of(2026, 11, 5);
    private final List<ShortDay> stored = new ArrayList<>();
    private final ShortDayRepository repository = mock(ShortDayRepository.class);
    private final ShortDayService service = new ShortDayService(repository);

    ShortDayServiceTest() {
        when(repository.findAll()).thenAnswer(call -> List.copyOf(stored));
        when(repository.save(any())).thenAnswer(call -> { stored.add(call.getArgument(0)); return call.getArgument(0); });
        doAnswer(call -> stored.removeIf(d -> d.getDay().equals(call.getArgument(0)))).when(repository).deleteById(any());
    }

    private static DayScheduleDto day(String name, int... numbers) {
        return DayScheduleDto.builder().dayName(name).hasLessons(true).lessons(Arrays.stream(numbers)
                .mapToObj(n -> LessonDto.builder().lessonNumber(n).time("normal-" + n).subject("Предмет").build()).toList()).build();
    }

    @Test void replacesTimesOnlyOnShortTodayOrTomorrowAndKeepsCache() {
        Map<String, DayScheduleDto> week = new LinkedHashMap<>();
        week.put("Четверг", day("Четверг", 1, 3));
        week.put("Пятница", day("Пятница", 2, 6));
        week.put("Понедельник", day("Понедельник", 1));
        assertSame(week, service.apply(week, THU, "Четверг", "Пятница"), "без сокращённых дней — тот же объект");

        service.add(THU.plusDays(1)); // пятница
        var result = service.apply(week, THU, "Четверг", "Пятница");
        assertEquals(List.of("9:40 - 10:40", "14:20 - 15:20"), result.get("Пятница").getLessons().stream().map(LessonDto::getTime).toList());
        assertEquals("normal-1", result.get("Четверг").getLessons().get(0).getTime());
        assertEquals("normal-2", week.get("Пятница").getLessons().get(0).getTime(), "кеш расписания не меняется");
        assertEquals("Предмет", result.get("Пятница").getLessons().get(0).getSubject());

        assertTrue(service.isShort(THU.plusDays(1)));
        assertEquals(List.of(THU.plusDays(1)), service.upcoming(THU));
        service.remove(THU.plusDays(1));
        assertFalse(service.isShort(THU.plusDays(1)));
    }

    @Test void weekendTomorrowIsIgnored() {
        service.add(THU.plusDays(2));
        var week = Map.of("Четверг", day("Четверг", 1));
        assertSame(week, service.apply(week, THU, "Четверг", ""));
    }
}
