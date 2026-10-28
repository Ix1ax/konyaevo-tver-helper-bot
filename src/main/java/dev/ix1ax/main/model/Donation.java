package dev.ix1ax.main.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Пожертвование звёздами Telegram. Номер платежа Telegram — ключ: повторное уведомление не засчитается дважды. */
@Entity
@Table(name = "donation", indexes = @Index(name = "idx_donation_chat", columnList = "chat_id"))
public class Donation {
    @Id @Column(name = "charge_id") private String chargeId;
    @Column(name = "chat_id", nullable = false) private long chatId;
    @Column(nullable = false) private int stars;
    @Column(name = "paid_at", nullable = false) private Instant paidAt;
    protected Donation() {}
    public Donation(String chargeId, long chatId, int stars, Instant paidAt) {
        this.chargeId = chargeId; this.chatId = chatId; this.stars = stars; this.paidAt = paidAt;
    }
    public String getChargeId() { return chargeId; }
    public long getChatId() { return chatId; }
    public int getStars() { return stars; }
    public Instant getPaidAt() { return paidAt; }
}
