package dev.ix1ax.main.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import dev.ix1ax.main.model.UserSettings;

import java.util.List;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {

    long countByRole(String role);

    long countByCourse(Integer course);

    @Query("SELECT u.chatId FROM UserSettings u")
    List<Long> findAllChatIds();

    @Query("SELECT u.groupName, COUNT(u) as cnt FROM UserSettings u " +
           "WHERE u.groupName IS NOT NULL AND u.groupName <> '' " +
           "GROUP BY u.groupName ORDER BY cnt DESC")
    List<Object[]> findTopGroups(Pageable pageable);

    List<UserSettings> findByNotifyEnabledTrueAndNotifyTime(String time);

    long countByNotifyEnabledTrue();
}
