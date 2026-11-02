package dev.ix1ax.main.repository;

import dev.ix1ax.main.model.ActivityDay;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

public interface ActivityDayRepository extends JpaRepository<ActivityDay, ActivityDay.Key> {
    /** Отмечает день без ошибки при повторе (MERGE в H2). */
    @Modifying
    @Transactional
    @Query(value = "MERGE INTO activity_day (active_on, chat_id) KEY (active_on, chat_id) VALUES (:day, :id)", nativeQuery = true)
    void mark(@Param("day") LocalDate day, @Param("id") long id);

    /** Сколько разных людей заходило в каждый день, начиная с from. */
    @Query("select a.day, count(a) from ActivityDay a where a.day >= :from group by a.day")
    List<Object[]> countByDay(@Param("from") LocalDate from);
}
