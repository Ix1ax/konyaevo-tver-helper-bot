package dev.ix1ax.main.repository;

import dev.ix1ax.main.model.SharedImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SharedImageRepository extends JpaRepository<SharedImage, String> {
    List<SharedImage> findTop50ByExpiresAtLessThanOrderByExpiresAtAsc(long now);
}
