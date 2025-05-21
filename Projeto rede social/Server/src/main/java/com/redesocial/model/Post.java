package com.redesocial.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Post implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String username;
    private String content;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Post(String id, String username, String content) {
        this.id = id;
        this.username = username;
        this.content = content;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Post{" +
                "id='" + id + '\'' +
                ", username='" + username + '\'' +
                ", content='" + content + '\'' +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }
}