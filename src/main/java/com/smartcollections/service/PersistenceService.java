package com.smartcollections.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.smartcollections.model.Item;
import com.smartcollections.model.Task;

public class PersistenceService {
    private static final String DEFAULT_SAVE_DIR = System.getProperty("user.home") + "/.smartcollections";
    private static final String DEFAULT_SAVE_FILE = "library.dat";
    private static final int FILE_VERSION = 1;
    private static final String MAGIC_NUMBER = "SMARTCOL";
    
    private final LibraryService libraryService;
    private Path saveFile;
    
    public PersistenceService(LibraryService libraryService) {
        this.libraryService = libraryService;
        Path targetPath;
        try {
            Files.createDirectories(Paths.get(DEFAULT_SAVE_DIR));
            targetPath = Paths.get(DEFAULT_SAVE_DIR, DEFAULT_SAVE_FILE);
        } catch (IOException e) {
            targetPath = Paths.get(DEFAULT_SAVE_FILE);
        }
        this.saveFile = targetPath;
    }
    
    public void save() throws IOException {
        LibraryData data = new LibraryData(
            libraryService.getAllItems(),
            libraryService.getAllTasks()
        );
        
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(saveFile.toFile())))) {
            oos.writeUTF(MAGIC_NUMBER);
            oos.writeInt(FILE_VERSION);
            oos.writeLong(System.currentTimeMillis());
            oos.writeObject(data);
        }
    }
    
    public void load() throws IOException, ClassNotFoundException {
        if (!Files.exists(saveFile)) return;
        
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(saveFile.toFile())))) {
            String magic = ois.readUTF();
            if (!MAGIC_NUMBER.equals(magic)) {
                throw new IOException("Invalid file format");
            }
            
            // Read version and timestamp for future compatibility
            @SuppressWarnings("unused")
            int version = ois.readInt();
            @SuppressWarnings("unused")
            long timestamp = ois.readLong();
            
            LibraryData data = (LibraryData) ois.readObject();
            libraryService.clear();
            data.items.forEach(libraryService::addItemSilently);
            data.tasks.forEach(libraryService::addTask);
        }
    }
    
    public void autoBackup() {
        try {
            String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            Path backupFile = Paths.get(DEFAULT_SAVE_DIR, "backup_" + timestamp + ".dat");
            Files.copy(saveFile, backupFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {}
    }
    
    private static class LibraryData implements Serializable {
        private static final long serialVersionUID = 1L;
        final List<Item> items;
        final List<Task> tasks;
        
        LibraryData(List<Item> items, List<Task> tasks) {
            this.items = items;
            this.tasks = tasks;
        }
    }
}
