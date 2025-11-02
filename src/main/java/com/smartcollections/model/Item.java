package com.smartcollections.model;

import java.io.Serializable;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class Item implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private final String id;
    private String title;
    private Category category;
    private Set<String> tags;
    private int rating;
    private final LocalDateTime createdAt;
    private String filePath;
    private String mediaUrl;
    private FileType fileType;
    
    public enum FileType {
        TEXT, PDF, AUDIO, VIDEO, MARKDOWN, UNKNOWN
    }
    
    public Item(String title, Category category, String filePath) {
        this(UUID.randomUUID().toString(), title, category, new LinkedHashSet<>(), 3,
            LocalDateTime.now(), filePath, null);
    }

    private Item(String id, String title, Category category, Set<String> tags, int rating,
                 LocalDateTime createdAt, String filePath, String mediaUrl) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.tags = new LinkedHashSet<>();
        if (tags != null) {
            tags.forEach(this::addTagInternal);
        }
        this.createdAt = createdAt;
        this.rating = clampRating(rating);
        this.filePath = filePath;
        this.mediaUrl = mediaUrl;
        this.fileType = determineFileType(filePath);
    }
    
    private FileType determineFileType(String path) {
        if (path == null) return FileType.UNKNOWN;
        String lower = path.toLowerCase(Locale.ROOT);
        if (endsWithAny(lower, ".txt", ".rtf")) return FileType.TEXT;
        if (lower.endsWith(".md")) return FileType.MARKDOWN;
        if (lower.endsWith(".pdf")) return FileType.PDF;
        if (endsWithAny(lower, ".mp3", ".wav", ".m4a", ".ogg", ".aac", ".flac")) return FileType.AUDIO;
        if (endsWithAny(lower, ".mp4", ".avi", ".mov", ".mkv", ".m4v", ".webm")) return FileType.VIDEO;
        return FileType.UNKNOWN;
    }
    
    public void addTag(String tag) {
        addTagInternal(tag);
    }

    private void addTagInternal(String tag) {
        if (tag != null && !tag.trim().isEmpty()) {
            tags.add(normaliseTag(tag));
        }
    }
    
    public void removeTag(String tag) {
        if (tag != null) {
            tags.remove(normaliseTag(tag));
        }
    }
    
    public void setRating(int rating) {
        this.rating = clampRating(rating);
    }
    
    public String getId() { return id; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public Set<String> getTags() { return new LinkedHashSet<>(tags); }
    public void setTags(Set<String> tags) { 
        this.tags = new LinkedHashSet<>();
        if (tags != null) {
            tags.forEach(this::addTagInternal);
        }
    }
    public int getRating() { return rating; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { 
        this.filePath = filePath;
        this.fileType = determineFileType(filePath);
    }
    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }
    public FileType getFileType() { return fileType; }

    public Item copy() {
        return new Item(id, title, category, tags, rating, createdAt, filePath, mediaUrl);
    }

    public Set<String> keywordTokens() {
        Set<String> tokens = new LinkedHashSet<>();
        if (title != null) {
            tokens.addAll(tokenise(title));
        }
        if (tags != null) {
            tokens.addAll(tags);
        }
        if (filePath != null) {
            Path file = Paths.get(filePath);
            tokens.addAll(tokenise(file.getFileName().toString()));
        }
        return tokens;
    }

    public Set<String> keywordTokensForSearch() {
        return Collections.unmodifiableSet(keywordTokens());
    }
    
    public Memento createMemento() {
        return new Memento(this.copy());
    }
    
    public void restoreFromMemento(Memento memento) {
        if (memento != null && memento.getItemId().equals(this.id)) {
            this.title = memento.getTitle();
            this.category = memento.getCategory();
            this.tags = new LinkedHashSet<>(memento.getTags());
            this.rating = clampRating(memento.getRating());
            this.filePath = memento.getFilePath();
            this.mediaUrl = memento.getMediaUrl();
            this.fileType = determineFileType(filePath);
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(id, ((Item) o).id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return title + " (" + category + ")";
    }

    private int clampRating(int value) {
        return Math.max(1, Math.min(5, value));
    }

    private String normaliseTag(String tag) {
        return tag.toLowerCase(Locale.ROOT).trim();
    }

    private Set<String> tokenise(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        String[] parts = value.toLowerCase(Locale.ROOT).split("[^a-z0-9]+");
        for (String part : parts) {
            if (part.length() > 2) {
                tokens.add(part);
            }
        }
        return tokens;
    }

    private boolean endsWithAny(String value, String... suffixes) {
        for (String suffix : suffixes) {
            if (value.endsWith(suffix)) {
                return true;
            }
        }
        return false;
    }
}
