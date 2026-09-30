package dev.ix1ax.main.service;

import dev.ix1ax.main.repository.UserActivityRepository;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class UserActivityServiceTest {
    @Test void coalescesRepeatedClicksAndCachesStatistics() {
        var repository=mock(UserActivityRepository.class);
        when(repository.touch(eq(42L), any())).thenReturn(1);
        when(repository.countByLastActiveGreaterThanEqual(any(Instant.class))).thenReturn(12L);
        when(repository.countByFirstSeenGreaterThanEqual(any(Instant.class))).thenReturn(3L);
        var service=new UserActivityService(repository);
        for (int i=0; i<1000; i++) service.record(42);
        verify(repository).touch(eq(42L), any());
        var first=service.snapshot();
        assertSame(first, service.snapshot()); assertEquals(12, first.day()); assertEquals(3, first.newWeek());
        verify(repository, times(3)).countByLastActiveGreaterThanEqual(any());
        verify(repository).countByFirstSeenGreaterThanEqual(any());
    }
    @Test void failedWriteCanRetryWithoutBreakingBot() {
        var repository=mock(UserActivityRepository.class);
        when(repository.touch(eq(42L), any())).thenThrow(new IllegalStateException("db unavailable")).thenReturn(1);
        var service=new UserActivityService(repository);
        assertDoesNotThrow(() -> service.record(42));
        service.record(42); service.record(42);
        verify(repository, times(2)).touch(eq(42L), any());
    }
}
