package dev.ix1ax.main.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import dev.ix1ax.main.model.UserSettings;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
}
