package dev.ix1ax.main.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name="feedback", uniqueConstraints=@UniqueConstraint(name="uq_feedback_user", columnNames="user_id"),
       indexes={@Index(name="idx_feedback_created", columnList="created_at,id"), @Index(name="idx_feedback_stars", columnList="stars")})
public class FeedbackEntry {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="user_id", nullable=false)
    private long userId;
    @Column(nullable=false)
    private int stars;
    @Column(length=2000, nullable=false)
    private String comment;
    @Column(name="created_at", nullable=false)
    private Instant createdAt;
    public FeedbackEntry() {}
    public FeedbackEntry(long userId, int stars, String comment) {
        this.userId=userId; this.stars=stars; this.comment=comment; this.createdAt=Instant.now();
    }
    public Long getId() { return id; }
    public long getUserId() { return userId; }
    public int getStars() { return stars; }
    public String getComment() { return comment; }
    public Instant getCreatedAt() { return createdAt; }
}
