package com.smartcollections.service;

import com.smartcollections.model.Category;
import com.smartcollections.model.Item;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileImportService {
    private final LibraryService libraryService;
    private final Set<String> supportedExtensions;
    
    public FileImportService(LibraryService libraryService) {
        this.libraryService = libraryService;
        this.supportedExtensions = new HashSet<>(
            Arrays.asList(".txt", ".md", ".pdf", ".mp3", ".mp4", ".wav", ".avi", ".mov")
        );
    }
    
    public ImportResult importFromDirectory(Path directory) {
        ImportResult result = new ImportResult();
        
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (attrs.isRegularFile()) {
                        String ext = getFileExtension(file);
                        if (supportedExtensions.contains(ext)) {
                            result.totalFiles++;
                            if (importFile(file)) {
                                result.importedFiles++;
                            } else {
                                result.skippedFiles++;
                            }
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
                
                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    result.errors.add("Cannot access: " + file);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            result.errors.add("Error walking directory: " + e.getMessage());
        }
        
        return result;
    }
    
    public boolean importFile(Path filePath) {
        if (!Files.isRegularFile(filePath)) return false;
        
        String extension = getFileExtension(filePath);
        if (!supportedExtensions.contains(extension)) return false;
        
        String fileName = filePath.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        String title = lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
        if (title.isBlank()) {
            title = fileName;
        }
        Category category = determineCategory(extension);
        
        Item item = new Item(title, category, filePath.toAbsolutePath().toString());
        if (!extension.isEmpty()) {
            item.addTag(extension.substring(1));
        }
        item.addTag(category.name().toLowerCase());
        return libraryService.addItem(item);
    }
    
    private String getFileExtension(Path path) {
        String fileName = path.getFileName().toString();
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot).toLowerCase() : "";
    }
    
    private Category determineCategory(String extension) {
        return switch (extension) {
            case ".pdf" -> Category.REFERENCE;
            case ".mp3", ".wav" -> Category.AUDIO_RECORDING;
            case ".mp4", ".avi", ".mov" -> Category.VIDEO_TUTORIAL;
            case ".txt", ".md" -> Category.LECTURE_NOTES;
            default -> Category.OTHER;
        };
    }
    
    public static class ImportResult {
        public int totalFiles = 0;
        public int importedFiles = 0;
        public int skippedFiles = 0;
        public final List<String> errors = new ArrayList<>();
        
        @Override
        public String toString() {
            return String.format("Imported: %d, Skipped: %d", importedFiles, skippedFiles);
        }
    }
}
