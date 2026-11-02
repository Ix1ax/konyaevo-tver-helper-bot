package dev.ix1ax.main.repository;

import dev.ix1ax.main.model.DayOff;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;

public interface DayOffRepository extends JpaRepository<DayOff, LocalDate> {}
