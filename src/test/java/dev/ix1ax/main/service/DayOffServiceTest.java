package dev.ix1ax.main.service;

import dev.ix1ax.main.model.DayOff;
import dev.ix1ax.main.repository.DayOffRepository;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DayOffServiceTest {
    @Test void builtInHolidaysAreKnown() {
        assertEquals("Новогодние каникулы", DayOffService.holiday(LocalDate.of(2027, 1, 5)));
        assertEquals("День Победы", DayOffService.holiday(LocalDate.of(2027, 5, 9)));
        assertEquals("Летние каникулы", DayOffService.holiday(LocalDate.of(2027, 7, 20)));
        assertNull(DayOffService.holiday(LocalDate.of(2027, 1, 11)));
    }

    @Test void upcomingGluesConsecutiveDaysIntoPeriods() {
        DayOffRepository repository = mock(DayOffRepository.class);
        // 29–30 декабря отмечены администратором, 31-е и 1–8 января — по календарю.
        when(repository.findAll()).thenReturn(List.of(new DayOff(LocalDate.of(2026, 12, 29)), new DayOff(LocalDate.of(2026, 12, 30))));
        DayOffService service = new DayOffService(repository);
        assertEquals(List.of(
                new DayOffService.Period("2026-12-29", "2026-12-30", "Выходной", false),
                new DayOffService.Period("2026-12-31", "2026-12-31", "Новый год", true),
                new DayOffService.Period("2027-01-01", "2027-01-08", "Новогодние каникулы", true)),
                service.upcoming(LocalDate.of(2026, 12, 20), 30));
        assertTrue(service.isExtra(LocalDate.of(2026, 12, 29)));
        assertFalse(service.isExtra(LocalDate.of(2026, 12, 31)), "встроенный праздник не хранится в базе");
    }
}
