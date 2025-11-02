package com.smartcollections.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

public class Memento implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String itemId;
    private final String title;
    private final Category category;
    private final Set<String> tags;
    private final int rating;
    private final String filePath;
    private final String mediaUrl;
    private final LocalDateTime timestamp;
    private final OperationType operationType;
    private final Object operationData;
    
    public enum OperationType {
        ADD, EDIT, DELETE, TASK_DELETE, TASK_EDIT
    }
    
    public Memento(Item item) {
        this.itemId = item.getId();
        this.title = item.getTitle();
        this.category = item.getCategory();
        this.tags = new HashSet<>(item.getTags());
        this.rating = item.getRating();
        this.filePath = item.getFilePath();
        this.mediaUrl = item.getMediaUrl();
        this.timestamp = LocalDateTime.now();
        this.operationType = OperationType.EDIT;
        this.operationData = null;
    }
    
    public Memento(Item item, OperationType operationType, Object operationData) {
        this.itemId = item.getId();
        this.title = item.getTitle();
        this.category = item.getCategory();
        this.tags = new HashSet<>(item.getTags());
        this.rating = item.getRating();
        this.filePath = item.getFilePath();
        this.mediaUrl = item.getMediaUrl();
        this.timestamp = LocalDateTime.now();
        this.operationType = operationType;
        this.operationData = operationData;
    }
    
    public Memento(Task task, OperationType operationType) {
        this.itemId = task.getId();
        this.title = task.getDescription();
        this.category = null;
        this.tags = new HashSet<>();
        this.rating = 0;
        this.filePath = null;
        this.mediaUrl = null;
        this.timestamp = LocalDateTime.now();
        this.operationType = operationType;
        this.operationData = task;
    }
    
    public String getItemId() { return itemId; }
    public String getTitle() { return title; }
    public Category getCategory() { return category; }
    public Set<String> getTags() { return new HashSet<>(tags); }
    public int getRating() { return rating; }
    public String getFilePath() { return filePath; }
    public String getMediaUrl() { return mediaUrl; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public OperationType getOperationType() { return operationType; }
    public Object getOperationData() { return operationData; }
    
    @Override
    public String toString() {
        return operationType + ": " + title + " at " + timestamp;
    }
}
