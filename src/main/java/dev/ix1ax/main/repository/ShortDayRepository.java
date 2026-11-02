package dev.ix1ax.main.repository;

import dev.ix1ax.main.model.ShortDay;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface ShortDayRepository extends JpaRepository<ShortDay, LocalDate> {}
