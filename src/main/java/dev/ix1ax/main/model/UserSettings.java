package dev.ix1ax.main.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Stores user preferences (selected role, course, group, teacher).
 */
@Entity
@Table(name = "user_settings")
public class UserSettings {

    @Id
    @Column(name = "chat_id")
    private Long chatId;

    /**
     * Last message ID sent by bot (for editing).
     */
    @Column(name = "message_id")
    private Integer messageId;

    /**
     * "student" or "teacher"
     */
    @Column(name = "role")
    private String role;

    /**
     * Selected course number (1-4) for students.
     */
    @Column(name = "course")
    private Integer course;

    /**
     * Selected group name (e.g. "1-ТМС").
     */
    @Column(name = "group_name")
    private String groupName;

    /**
     * Selected teacher name for teachers.
     */
    @Column(name = "teacher_name")
    private String teacherName;

    public UserSettings() {
    }

    public UserSettings(Long chatId) {
        this.chatId = chatId;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public Integer getMessageId() {
        return messageId;
    }

    public void setMessageId(Integer messageId) {
        this.messageId = messageId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getCourse() {
        return course;
    }

    public void setCourse(Integer course) {
        this.course = course;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getTeacherName() {
        return teacherName;
    }

    public void setTeacherName(String teacherName) {
        this.teacherName = teacherName;
    }
}
