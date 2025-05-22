package com.toyota.mainapp.model;

import java.time.LocalDateTime;

public class Message {
    private String id;
    private String content;
    private String sender;
    private LocalDateTime timestamp;
    
    public Message() {
        this.timestamp = LocalDateTime.now();
    }
    
    public Message(String id, String content, String sender) {
        this.id = id;
        this.content = content;
        this.sender = sender;
        this.timestamp = LocalDateTime.now();
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public String getSender() {
        return sender;
    }
    
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
    
    @Override
    public String toString() {
        return "Message{" +
                "id='" + id + '\'' +
                ", content='" + content + '\'' +
                ", sender='" + sender + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
