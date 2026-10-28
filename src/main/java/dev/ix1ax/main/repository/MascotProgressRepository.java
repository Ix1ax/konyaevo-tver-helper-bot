package dev.ix1ax.main.repository;

import dev.ix1ax.main.model.MascotProgress;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MascotProgressRepository extends JpaRepository<MascotProgress, Long> {
    List<MascotProgress> findByReminderEnabledTrueAndStreakGreaterThan(int streak);
}
