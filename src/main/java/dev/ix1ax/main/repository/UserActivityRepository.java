package dev.ix1ax.main.repository;

import dev.ix1ax.main.model.UserSettings;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

public interface UserActivityRepository extends Repository<UserSettings, Long> {
    @Modifying
    @Transactional
    @Query("UPDATE UserSettings u SET u.lastActive = :now WHERE u.chatId = :id")
    int touch(@Param("id") long id, @Param("now") Instant now);

    long countByLastActiveGreaterThanEqual(Instant since);
    long countByFirstSeenGreaterThanEqual(Instant since);
}
