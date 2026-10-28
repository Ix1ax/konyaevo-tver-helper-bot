package dev.ix1ax.main.repository;

import dev.ix1ax.main.model.Donation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DonationRepository extends JpaRepository<Donation, String> {
    @Query("select coalesce(sum(d.stars), 0) from Donation d")
    long totalStars();

    @Query("select count(distinct d.chatId) from Donation d")
    long supporters();
}
