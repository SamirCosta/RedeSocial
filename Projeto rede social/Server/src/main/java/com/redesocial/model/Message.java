package com.redesocial.model;

import java.io.Serializable;
import java.time.LocalDateTime;

public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final String senderUsername;
    private final String receiverUsername;
    private final String content;
    private final LocalDateTime sentAt;
    private boolean read;
    private LocalDateTime readAt;

    public Message(String id, String senderUsername, String receiverUsername, String content) {
        this.id = id;
        this.senderUsername = senderUsername;
        this.receiverUsername = receiverUsername;
        this.content = content;
        this.sentAt = LocalDateTime.now();
        this.read = false;
        this.readAt = null;
    }

    public String getId() {
        return id;
    }

    public String getSenderUsername() {
        return senderUsername;
    }

    public String getReceiverUsername() {
        return receiverUsername;
    }

    public String getContent() {
        return content;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public boolean isRead() {
        return read;
    }

    public void markAsRead() {
        if (!this.read) {
            this.read = true;
            this.readAt = LocalDateTime.now();
        }
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", senderUsername='" + senderUsername + '\'' +
                ", receiverUsername='" + receiverUsername + '\'' +
                ", sentAt=" + sentAt +
                ", read=" + read +
                ", readAt=" + readAt +
                '}';
    }
}