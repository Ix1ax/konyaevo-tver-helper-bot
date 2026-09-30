package dev.ix1ax.main.repository;

import dev.ix1ax.main.model.FeedbackEntry;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface FeedbackRepository extends JpaRepository<FeedbackEntry, Long> {
    boolean existsByUserId(long userId);
    @Query("SELECT f.stars, COUNT(f) FROM FeedbackEntry f GROUP BY f.stars")
    List<Object[]> distribution();
}
