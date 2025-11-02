package com.smartcollections.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

public class Task implements Serializable, Comparable<Task> {
    private static final long serialVersionUID = 1L;
    
    private final String id;
    private String itemId;
    private String description;
    private LocalDateTime deadline;
    private Priority priority;
    private boolean completed;
    private final LocalDateTime createdAt;
    
    public enum Priority {
        LOW, MEDIUM, HIGH, URGENT
    }
    
    public Task(String itemId, String description, LocalDateTime deadline, Priority priority) {
        this(UUID.randomUUID().toString(), itemId, description, deadline, priority, false, LocalDateTime.now());
    }

    private Task(String id, String itemId, String description, LocalDateTime deadline,
                 Priority priority, boolean completed, LocalDateTime createdAt) {
        this.id = id;
        this.itemId = itemId;
        this.description = description;
        this.deadline = deadline;
        this.priority = priority;
        this.completed = completed;
        this.createdAt = createdAt;
    }
    
    public double calculateUrgency() {
        long hoursUntilDeadline = ChronoUnit.HOURS.between(LocalDateTime.now(), deadline);
        
        double urgency;
        if (hoursUntilDeadline < 0) urgency = 1000;
        else if (hoursUntilDeadline < 24) urgency = 500;
        else if (hoursUntilDeadline < 72) urgency = 300;
        else if (hoursUntilDeadline < 168) urgency = 150;
        else urgency = 100.0 / (hoursUntilDeadline / 24.0);
        
        double priorityMultiplier = switch (priority) {
            case URGENT -> 2.0;
            case HIGH -> 1.5;
            case MEDIUM -> 1.0;
            case LOW -> 0.5;
        };
        
        return urgency * priorityMultiplier;
    }
    
    @Override
    public int compareTo(Task other) {
        return Double.compare(other.calculateUrgency(), this.calculateUrgency());
    }
    
    public boolean isOverdue() {
        return !completed && LocalDateTime.now().isAfter(deadline);
    }
    
    public String getTimeRemaining() {
        long hours = ChronoUnit.HOURS.between(LocalDateTime.now(), deadline);
        if (hours < 0) return "Overdue";
        if (hours < 24) return hours + " hours";
        long days = hours / 24;
        return days + " days";
    }
    
    public String getId() { return id; }
    public String getItemId() { return itemId; }
    public void setItemId(String itemId) { this.itemId = itemId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }
    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public Task copy() {
        return new Task(id, itemId, description, deadline, priority, completed, createdAt);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((Task) o).id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return description + " (Due: " + getTimeRemaining() + ")";
    }
}
