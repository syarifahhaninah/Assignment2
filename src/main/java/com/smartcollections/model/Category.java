package com.smartcollections.model;

import java.io.Serializable;

public enum Category implements Serializable {
    ASSIGNMENT("Assignment"),
    LECTURE_NOTES("Lecture Notes"),
    TUTORIAL("Tutorial"),
    REFERENCE("Reference"),
    AUDIO_RECORDING("Audio Recording"),
    VIDEO_TUTORIAL("Video Tutorial"),
    PAPER("Paper/Article"),
    TEXTBOOK("Textbook"),
    OTHER("Other");
    
    private final String displayName;
    
    Category(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
