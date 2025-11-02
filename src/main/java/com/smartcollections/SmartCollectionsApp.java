package com.smartcollections;

// Core application imports
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import com.smartcollections.model.Category;
import com.smartcollections.model.Item;
import com.smartcollections.model.Task;
import com.smartcollections.service.FileImportService;
import com.smartcollections.service.LibraryService;
import com.smartcollections.service.PersistenceService;
import com.smartcollections.util.AnimationUtils;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ToolBar;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.text.FontWeight;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public class SmartCollectionsApp extends Application {
    private LibraryService libraryService;
    private PersistenceService persistenceService;
    private FileImportService fileImportService;
    
    private TableView<Item> itemTable;
    private ObservableList<Item> itemList;
    private ListView<Task> taskListView;
    private ListView<Item> recentlyViewedList;
    private TextArea mediaPreview;
    private TextField searchField;
    private Label statusLabel;
    private StackPane mediaPane;
    private MediaPlayer currentMediaPlayer;
    private ComboBox<Category> categoryFilterCombo;
    private ComboBox<String> tagFilterCombo;
    private Label nextTaskLabel;
    private Button backButton;
    private Item activeItem;

    private TextField detailTitleField;
    private ComboBox<Category> detailCategoryCombo;
    private ListView<String> detailTagsList;
    private ObservableList<String> detailTags;
    private TextField newTagField;
    private Slider detailRatingSlider;
    private Label ratingValueLabel;
    private TextField detailFilePathField;
    private Label detailFileTypeLabel;
    private TextField detailMediaUrlField;
    private Label createdAtLabel;
    private Button detailSaveButton;
    private Button detailResetButton;
    private boolean detailDirty;
    
    // Performance optimisation: cache extracted text for reuse
    private final Map<String, String> textCache = new HashMap<>();
    private final Map<String, Long> cacheTimestamps = new HashMap<>();
    private static final long CACHE_VALIDITY_MS = 300000; // 5 minutes
    private static final long MAX_STREAM_DOWNLOAD_BYTES = 30L * 1024 * 1024; // 30 MB cap for inline playback
    private static final int PDF_PREVIEW_PAGE_LIMIT = 5;
    private static final float PDF_PREVIEW_DPI = 140f;
    private final Map<String, Path> remoteMediaCache = new LinkedHashMap<>(8, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Path> eldest) {
            if (size() > 6) {
                tryDeleteTemp(eldest.getValue());
                return true;
            }
            return false;
        }
    };
    private final Set<String> activeDownloads = new HashSet<>();
    
    @Override
    public void start(Stage primaryStage) {
        libraryService = new LibraryService();
        persistenceService = new PersistenceService(libraryService);
        fileImportService = new FileImportService(libraryService);
        
        try {
            persistenceService.load();
        } catch (IOException e) {
            showAlert("Load Error", "Could not load previous library: " + e.getMessage(), Alert.AlertType.WARNING);
        } catch (ClassNotFoundException e) {
            showAlert("Data Error", "Library data format not recognized: " + e.getMessage(), Alert.AlertType.WARNING);
        }
        
        BorderPane root = new BorderPane();
        root.setTop(createMenuBar());
        root.setCenter(createMainContent());
        root.setBottom(createStatusBar());
        
        Scene scene = new Scene(root, 1400, 900);
        scene.getStylesheets().add(getClass().getResource("/styles.css").toExternalForm());
        
        primaryStage.setTitle("Smart Collections Manager");
        primaryStage.setScene(scene);
        primaryStage.setOnCloseRequest(e -> handleExit());
        primaryStage.show();
        
        refreshItemTable();
    refreshTagFilters();
    }
    
    private MenuBar createMenuBar() {
        MenuBar menuBar = new MenuBar();
        
        Menu fileMenu = new Menu("File");
        MenuItem importFolderItem = new MenuItem("Import Folder...");
        importFolderItem.setOnAction(e -> handleImportFolder());
        MenuItem importFileItem = new MenuItem("Import File...");
        importFileItem.setOnAction(e -> handleImportFile());
        MenuItem saveItem = new MenuItem("Save");
        saveItem.setOnAction(e -> handleSave());
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> handleExit());
        
        fileMenu.getItems().addAll(importFolderItem, importFileItem, new SeparatorMenuItem(), 
                                    saveItem, new SeparatorMenuItem(), exitItem);
        
        Menu editMenu = new Menu("Edit");
        MenuItem undoItem = new MenuItem("Undo");
        undoItem.setOnAction(e -> handleUndo());
        MenuItem addItemMenu = new MenuItem("Add Item...");
        addItemMenu.setOnAction(e -> showAddItemDialog());
        
        editMenu.getItems().addAll(undoItem, new SeparatorMenuItem(), addItemMenu);
        
    // View menu configuration
        Menu viewMenu = new Menu("View");
        
    // Theme toggle
        Menu themeMenu = new Menu("Theme");
        RadioMenuItem lightThemeItem = new RadioMenuItem("Light Theme");
        RadioMenuItem darkThemeItem = new RadioMenuItem("Dark Theme");
        ToggleGroup themeGroup = new ToggleGroup();
        lightThemeItem.setToggleGroup(themeGroup);
        darkThemeItem.setToggleGroup(themeGroup);
        lightThemeItem.setSelected(true);
        
        lightThemeItem.setOnAction(e -> switchTheme("light"));
        darkThemeItem.setOnAction(e -> switchTheme("dark"));
        
        themeMenu.getItems().addAll(lightThemeItem, darkThemeItem);
        
    // Refresh command
        MenuItem refreshItem = new MenuItem("Refresh");
        refreshItem.setOnAction(e -> refreshView());
        
    // Statistics dialogue
        MenuItem statsItem = new MenuItem("Show Statistics");
        statsItem.setOnAction(e -> showStatisticsDialog());
        
        viewMenu.getItems().addAll(themeMenu, new SeparatorMenuItem(), 
                                    refreshItem, statsItem);
        
        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showAboutDialog());
        helpMenu.getItems().add(aboutItem);
        
        menuBar.getMenus().addAll(fileMenu, editMenu, viewMenu, helpMenu);
        return menuBar;
    }
    
    private Node createMainContent() {
        SplitPane mainSplit = new SplitPane();
        mainSplit.setOrientation(Orientation.HORIZONTAL);
        
        VBox leftPanel = createLeftPanel();
        VBox centerPanel = createCenterPanel();
        VBox rightPanel = createRightPanel();
        
        mainSplit.getItems().addAll(leftPanel, centerPanel, rightPanel);
        mainSplit.setDividerPositions(0.25, 0.60);
        
        return mainSplit;
    }
    
    private VBox createLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(300);
        
        Label libraryLabel = new Label("Library");
        libraryLabel.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 16));
        
        searchField = new TextField();
        searchField.setPromptText("Search items...");
    searchField.textProperty().addListener((obs, old, newVal) -> handleSearch());
        
        categoryFilterCombo = new ComboBox<>();
        categoryFilterCombo.setPromptText("All categories");
        categoryFilterCombo.getItems().addAll(Category.values());
        categoryFilterCombo.valueProperty().addListener((obs, old, val) -> applyFilters());

        tagFilterCombo = new ComboBox<>();
        tagFilterCombo.setPromptText("All tags");
        tagFilterCombo.setOnAction(e -> applyFilters());

        Button clearFiltersBtn = new Button("Clear Filters");
        clearFiltersBtn.setOnAction(e -> {
            categoryFilterCombo.setValue(null);
            tagFilterCombo.getSelectionModel().clearSelection();
            applyFilters();
        });

        HBox filters = new HBox(8, categoryFilterCombo, tagFilterCombo, clearFiltersBtn);
        filters.setAlignment(Pos.CENTER_LEFT);

        itemList = FXCollections.observableArrayList();
        itemTable = new TableView<>(itemList);
        itemTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);
        
        TableColumn<Item, String> titleCol = new TableColumn<>("Title");
        titleCol.setCellValueFactory(new PropertyValueFactory<>("title"));
        titleCol.setPrefWidth(150);

    TableColumn<Item, String> typeCol = new TableColumn<>("Type");
    typeCol.setCellValueFactory(cell -> new SimpleStringProperty(formatFileType(cell.getValue())));
    typeCol.setPrefWidth(110);
    typeCol.setReorderable(false);
    typeCol.setSortable(false);
        
        TableColumn<Item, Category> categoryCol = new TableColumn<>("Category");
        categoryCol.setCellValueFactory(new PropertyValueFactory<>("category"));
        categoryCol.setPrefWidth(100);
        
        TableColumn<Item, Integer> ratingCol = new TableColumn<>("Rating");
        ratingCol.setCellValueFactory(new PropertyValueFactory<>("rating"));
        ratingCol.setPrefWidth(50);
        
        itemTable.getColumns().clear();
    itemTable.getColumns().add(titleCol);
    itemTable.getColumns().add(typeCol);
    itemTable.getColumns().add(categoryCol);
    itemTable.getColumns().add(ratingCol);
        itemTable.getSelectionModel().selectedItemProperty().addListener((obs, old, selected) -> {
            if (selected != null) {
                handleItemSelection(selected);
            } else {
                clearDetailPane();
            }
        });
        
        Button addButton = new Button("Add Item");
        addButton.setOnAction(e -> showAddItemDialog());
        
        Button editButton = new Button("Edit");
        editButton.setOnAction(e -> handleEditItem());
        
        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> handleDeleteItem());
        
        HBox buttonBox = new HBox(5, addButton, editButton, deleteButton);
        
        panel.getChildren().addAll(libraryLabel, searchField, filters, itemTable, buttonBox);
        VBox.setVgrow(itemTable, Priority.ALWAYS);
        
        return panel;
    }
    
    private VBox createCenterPanel() {
        detailTags = FXCollections.observableArrayList();

        VBox panel = new VBox(15);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(620);

        VBox detailPane = buildDetailPane();
        VBox previewPane = buildPreviewPane();

        panel.getChildren().addAll(detailPane, new Separator(), previewPane);
        VBox.setVgrow(previewPane, Priority.ALWAYS);

        return panel;
    }

    private VBox buildDetailPane() {
        Label detailLabel = new Label("Item Details");
        detailLabel.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 16));

        detailTitleField = new TextField();
        detailTitleField.setPromptText("Title");
        detailTitleField.textProperty().addListener((obs, old, val) -> markDetailDirty());

        detailCategoryCombo = new ComboBox<>();
        detailCategoryCombo.getItems().addAll(Category.values());
        detailCategoryCombo.valueProperty().addListener((obs, old, val) -> markDetailDirty());

        detailTagsList = new ListView<>(detailTags);
        detailTagsList.setPrefHeight(100);

        newTagField = new TextField();
        newTagField.setPromptText("New tag");
        newTagField.setOnAction(e -> addTagFromField());

        Button addTagButton = new Button("Add");
        addTagButton.setOnAction(e -> addTagFromField());

        Button removeTagButton = new Button("Remove");
        removeTagButton.setOnAction(e -> removeSelectedTag());

        detailRatingSlider = new Slider(1, 5, 3);
        detailRatingSlider.setMajorTickUnit(1);
        detailRatingSlider.setMinorTickCount(0);
        detailRatingSlider.setShowTickLabels(true);
        detailRatingSlider.setShowTickMarks(true);
        detailRatingSlider.setSnapToTicks(true);
        ratingValueLabel = new Label("3");
        detailRatingSlider.valueProperty().addListener((obs, old, val) -> {
            ratingValueLabel.setText(String.valueOf(val.intValue()));
            markDetailDirty();
        });

        detailFilePathField = new TextField();
        detailFilePathField.setPromptText("File path");
        detailFilePathField.textProperty().addListener((obs, old, val) -> markDetailDirty());
        Button browseButton = new Button("Browse...");
        browseButton.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File file = fc.showOpenDialog(detailFilePathField.getScene().getWindow());
            if (file != null) {
                detailFilePathField.setText(file.getAbsolutePath());
            }
        });

        detailFileTypeLabel = new Label("-");
        detailFileTypeLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");

        detailMediaUrlField = new TextField();
        detailMediaUrlField.setPromptText("Media URL (optional)");
        detailMediaUrlField.textProperty().addListener((obs, old, val) -> markDetailDirty());

        createdAtLabel = new Label("-");

        detailSaveButton = new Button("Save Changes");
        detailSaveButton.setDisable(true);
        detailSaveButton.setOnAction(e -> saveDetailChanges());

        detailResetButton = new Button("Reset");
        detailResetButton.setDisable(true);
        detailResetButton.setOnAction(e -> resetDetailFields());

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(8);
        form.add(new Label("Title"), 0, 0);
        form.add(detailTitleField, 1, 0, 2, 1);
        form.add(new Label("Category"), 0, 1);
        form.add(detailCategoryCombo, 1, 1);
        form.add(new Label("Rating"), 0, 2);
        form.add(new HBox(10, detailRatingSlider, ratingValueLabel), 1, 2, 2, 1);
        form.add(new Label("Tags"), 0, 3);
        form.add(detailTagsList, 1, 3, 2, 1);
        form.add(new HBox(8, newTagField, addTagButton, removeTagButton), 1, 4, 2, 1);
        form.add(new Label("File"), 0, 5);
        form.add(new HBox(8, detailFilePathField, browseButton), 1, 5, 2, 1);
    form.add(new Label("File Type"), 0, 6);
    form.add(detailFileTypeLabel, 1, 6, 2, 1);
    form.add(new Label("Media URL"), 0, 7);
    form.add(detailMediaUrlField, 1, 7, 2, 1);
    form.add(new Label("Created"), 0, 8);
    form.add(createdAtLabel, 1, 8);

        HBox actions = new HBox(10, detailSaveButton, detailResetButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        VBox container = new VBox(10, detailLabel, form, actions);
        container.setFillWidth(true);
        setDetailControlsDisabled(true);
        return container;
    }

    private VBox buildPreviewPane() {
        Label previewLabel = new Label("Media Preview");
        previewLabel.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 16));

        mediaPane = new StackPane();
        mediaPane.setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #444; -fx-border-width: 1;");
        mediaPane.setPrefHeight(500);

        mediaPreview = new TextArea();
        mediaPreview.setEditable(false);
        mediaPreview.setWrapText(true);
        mediaPreview.setPrefHeight(500);
        mediaPreview.setVisible(false);

        StackPane previewStack = new StackPane(mediaPane, mediaPreview);

        VBox previewBox = new VBox(10, previewLabel, previewStack);
        VBox.setVgrow(previewStack, Priority.ALWAYS);
        return previewBox;
    }

    private void setDetailControlsDisabled(boolean disabled) {
        if (detailTitleField == null) return;
        detailTitleField.setDisable(disabled);
        detailCategoryCombo.setDisable(disabled);
        detailTagsList.setDisable(disabled);
        newTagField.setDisable(disabled);
        detailRatingSlider.setDisable(disabled);
        detailFilePathField.setDisable(disabled);
        detailMediaUrlField.setDisable(disabled);
        detailSaveButton.setDisable(disabled || !detailDirty);
        detailResetButton.setDisable(disabled || !detailDirty);
    }

    private void markDetailDirty() {
        if (activeItem == null) {
            return;
        }
        detailDirty = true;
        detailSaveButton.setDisable(false);
        detailResetButton.setDisable(false);
    }

    private void addTagFromField() {
        if (newTagField == null) return;
        String candidate = newTagField.getText();
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String normalised = candidate.trim().toLowerCase(Locale.ROOT);
        if (!detailTags.contains(normalised)) {
            detailTags.add(normalised);
            markDetailDirty();
        }
        newTagField.clear();
    }

    private void removeSelectedTag() {
        String selected = detailTagsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            detailTags.remove(selected);
            markDetailDirty();
        }
    }

    private void populateDetail(Item item) {
        if (item == null) {
            clearDetailPane();
            return;
        }
        activeItem = item;
        detailTitleField.setText(item.getTitle());
        detailCategoryCombo.setValue(item.getCategory());
        detailTags.setAll(item.getTags().stream()
            .map(tag -> tag.toLowerCase(Locale.ROOT))
            .sorted()
            .toList());
        detailRatingSlider.setValue(item.getRating());
        ratingValueLabel.setText(String.valueOf(item.getRating()));
        detailFilePathField.setText(item.getFilePath() == null ? "" : item.getFilePath());
    detailFileTypeLabel.setText(formatFileType(item));
        detailMediaUrlField.setText(item.getMediaUrl() == null ? "" : item.getMediaUrl());
        createdAtLabel.setText(item.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
        detailDirty = false;
        detailSaveButton.setDisable(true);
        detailResetButton.setDisable(true);
        setDetailControlsDisabled(false);
        AnimationUtils.fadeTransition(detailTitleField, true);
    }

    private void clearDetailPane() {
        activeItem = null;
        detailTitleField.clear();
        detailCategoryCombo.getSelectionModel().clearSelection();
        detailTags.clear();
        detailRatingSlider.setValue(3);
        ratingValueLabel.setText("3");
        detailFilePathField.clear();
    detailFileTypeLabel.setText("-");
        detailMediaUrlField.clear();
        createdAtLabel.setText("-");
        detailDirty = false;
        setDetailControlsDisabled(true);
    }

    private void resetDetailFields() {
        if (activeItem != null) {
            populateDetail(activeItem);
        }
    }

    private void saveDetailChanges() {
        if (activeItem == null) {
            return;
        }
        try {
            libraryService.editItem(activeItem, updated -> {
                updated.setTitle(detailTitleField.getText().trim());
                updated.setCategory(detailCategoryCombo.getValue() == null ? Category.OTHER : detailCategoryCombo.getValue());
                updated.setTags(new LinkedHashSet<>(detailTags));
                updated.setRating((int) detailRatingSlider.getValue());
                updated.setFilePath(detailFilePathField.getText().isBlank() ? null : detailFilePathField.getText().trim());
                updated.setMediaUrl(detailMediaUrlField.getText().isBlank() ? null : detailMediaUrlField.getText().trim());
            });
            detailDirty = false;
            detailSaveButton.setDisable(true);
            detailResetButton.setDisable(true);
            populateDetail(activeItem);
            applyFilters();
            refreshTagFilters();
            statusLabel.setText("Item updated");
        } catch (IllegalArgumentException ex) {
            showAlert("Duplicate Path", ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void handleItemSelection(Item item) {
        if (item == null) {
            clearDetailPane();
            return;
        }
        libraryService.markAsViewed(item);
        populateDetail(item);
        displayItem(item);
        refreshRecentlyViewed();
        updateBackButtonState();
    }

    private void handleNavigateBack() {
        libraryService.navigateBack(activeItem).ifPresent(previous -> {
            ensureItemVisible(previous);
            itemTable.getSelectionModel().select(previous);
            itemTable.scrollTo(previous);
        });
        updateBackButtonState();
    }

    private void ensureItemVisible(Item item) {
        if (item == null) return;
        if (!itemList.contains(item)) {
            categoryFilterCombo.setValue(null);
            tagFilterCombo.getSelectionModel().clearSelection();
            if (!libraryService.search(searchField.getText()).contains(item)) {
                searchField.clear();
            }
            applyFilters();
        }
    }

    private void updateBackButtonState() {
        if (backButton == null) return;
        int recentSize = libraryService.getRecentlyViewed().size();
        backButton.setDisable(recentSize <= 1);
    }

    private void applyFilters() {
        if (libraryService == null) {
            return;
        }
        String query = searchField != null ? searchField.getText() : "";
        List<Item> filtered = new ArrayList<>(libraryService.search(query));
        Category selectedCategory = categoryFilterCombo != null ? categoryFilterCombo.getValue() : null;
        if (selectedCategory != null) {
            filtered.removeIf(item -> item.getCategory() != selectedCategory);
        }
        String selectedTag = tagFilterCombo != null ? tagFilterCombo.getSelectionModel().getSelectedItem() : null;
        if (selectedTag != null && !selectedTag.isBlank()) {
            String tag = selectedTag.toLowerCase(Locale.ROOT);
            filtered.removeIf(item -> !item.getTags().contains(tag));
        }
        filtered.sort(Comparator.comparing(Item::getTitle, String.CASE_INSENSITIVE_ORDER));
        itemList.setAll(filtered);
        AnimationUtils.fadeTransition(itemTable, true);
    }

    private void refreshTagFilters() {
        if (tagFilterCombo == null) return;
        String selection = tagFilterCombo.getSelectionModel().getSelectedItem();
        List<String> tags = libraryService.getTagFrequency().keySet().stream()
            .sorted()
            .toList();
        tagFilterCombo.getItems().setAll(tags);
        if (selection != null && tags.contains(selection)) {
            tagFilterCombo.getSelectionModel().select(selection);
        } else {
            tagFilterCombo.getSelectionModel().clearSelection();
        }
    }
    
    private VBox createRightPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(350);
        
        Label tasksLabel = new Label("Priority Tasks");
        tasksLabel.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 14));
    nextTaskLabel = new Label("Next up: -");
        
        taskListView = new ListView<>();
        taskListView.setPrefHeight(250);
        taskListView.setCellFactory(lv -> new ListCell<Task>() {
            @Override
            protected void updateItem(Task task, boolean empty) {
                super.updateItem(task, empty);
                if (empty || task == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(task.getDescription() + "\nDue: " + task.getTimeRemaining());
                    if (task.isOverdue()) {
                        setStyle("-fx-background-color: #ffcccc;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        Button addTaskBtn = new Button("Add Task");
        addTaskBtn.setOnAction(e -> showAddTaskDialog());
        
        Button deleteTaskBtn = new Button("Complete Task");
        deleteTaskBtn.setOnAction(e -> handleCompleteTask());
        
        HBox taskButtons = new HBox(5, addTaskBtn, deleteTaskBtn);
        
        Label recentLabel = new Label("Recently Viewed");
        recentLabel.setFont(javafx.scene.text.Font.font("Arial", FontWeight.BOLD, 14));

        backButton = new Button("⬅ Back");
        backButton.setDisable(true);
        backButton.setOnAction(e -> handleNavigateBack());
        HBox recentHeader = new HBox(10, recentLabel, backButton);
        recentHeader.setAlignment(Pos.CENTER_LEFT);
        
        recentlyViewedList = new ListView<>();
        recentlyViewedList.setPrefHeight(250);
        recentlyViewedList.setCellFactory(lv -> new ListCell<Item>() {
            @Override
            protected void updateItem(Item item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getTitle() + " · " + formatFileType(item));
                }
            }
        });
        recentlyViewedList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                Item item = recentlyViewedList.getSelectionModel().getSelectedItem();
                if (item != null) {
                    itemTable.getSelectionModel().select(item);
                    handleItemSelection(item);
                }
            }
        });
        
        panel.getChildren().addAll(tasksLabel, nextTaskLabel, taskListView, taskButtons, 
                                    new Separator(), recentHeader, recentlyViewedList);
        VBox.setVgrow(taskListView, Priority.ALWAYS);
        VBox.setVgrow(recentlyViewedList, Priority.ALWAYS);
        
        return panel;
    }
    
    private HBox createStatusBar() {
        HBox statusBar = new HBox();
        statusBar.setPadding(new Insets(5));
        statusBar.setStyle("-fx-background-color: #e0e0e0;");
        
        statusLabel = new Label("Ready");
        statusBar.getChildren().add(statusLabel);
        
        return statusBar;
    }
    
    private void handleImportFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder to Import");
        File folder = chooser.showDialog(itemTable.getScene().getWindow());
        
        if (folder != null) {
            FileImportService.ImportResult result = fileImportService.importFromDirectory(folder.toPath());
            statusLabel.setText(result.toString());
            refreshItemTable();
            refreshTagFilters();
            showAlert("Import Complete", result.toString(), Alert.AlertType.INFORMATION);
        }
    }
    
    private void handleImportFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select File to Import");
        chooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Supported", 
                "*.txt", "*.md", "*.pdf", "*.doc", "*.docx", "*.rtf", "*.xlsx", "*.xls",
                "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp",
                "*.mp3", "*.wav", "*.m4a", "*.ogg",
                "*.mp4", "*.avi", "*.mov", "*.mkv"),
            new FileChooser.ExtensionFilter("Documents", "*.txt", "*.md", "*.pdf", "*.doc", "*.docx", "*.rtf"),
            new FileChooser.ExtensionFilter("Spreadsheets", "*.xlsx", "*.xls"),
            new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.gif", "*.bmp"),
            new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a", "*.ogg"),
            new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.avi", "*.mov", "*.mkv")
        );
        
        File file = chooser.showOpenDialog(itemTable.getScene().getWindow());
        if (file != null) {
            boolean success = fileImportService.importFile(file.toPath());
            if (success) {
                statusLabel.setText("Imported: " + file.getName());
                refreshItemTable();
                refreshTagFilters();
            } else {
                showAlert("Import Failed", "Could not import file (duplicate or unsupported type)", Alert.AlertType.WARNING);
            }
        }
    }
    
    private void handleSave() {
        try {
            persistenceService.save();
            statusLabel.setText("Library saved");
        } catch (IOException e) {
            showAlert("Save Error", "Could not save library: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    private void handleExit() {
        try {
            persistenceService.save();
            persistenceService.autoBackup();
        } catch (IOException e) {
            if (statusLabel != null) {
                statusLabel.setText("Failed to persist library on exit: " + e.getMessage());
            }
        }
        
        if (currentMediaPlayer != null) {
            try {
                currentMediaPlayer.stop();
                currentMediaPlayer.dispose();
            } catch (IllegalStateException ignore) {
                // Ignore disposal issues on exit
            }
        }

        remoteMediaCache.values().forEach(this::tryDeleteTemp);
        remoteMediaCache.clear();
        activeDownloads.clear();
        
        Platform.exit();
    }
    
    private void handleUndo() {
        if (libraryService.undo()) {
            refreshItemTable();
            refreshTaskList();
             refreshTagFilters();
            statusLabel.setText("Undo successful");
            updateBackButtonState();
        } else {
            statusLabel.setText("Nothing to undo");
        }
    }
    
    private void handleSearch() {
        applyFilters();
    }
    
    private void displayItem(Item item) {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
            currentMediaPlayer = null;
        }
        
        mediaPane.getChildren().clear();
        mediaPreview.setVisible(false);
        mediaPreview.clear();
    statusLabel.setText("Previewing " + formatFileType(item));
        
        String path = item.getFilePath();
        String mediaUrl = item.getMediaUrl();

        if (isLikelyUrl(path)) {
            displayRemoteContent(path);
            return;
        }

        if (path == null || path.isBlank()) {
            if (isLikelyUrl(mediaUrl)) {
                displayRemoteContent(mediaUrl);
            } else {
                mediaPreview.setText("No media source associated with this item.");
                mediaPreview.setVisible(true);
            }
            return;
        }

        java.nio.file.Path filePath;
        try {
            filePath = Paths.get(path);
        } catch (RuntimeException ex) {
            if (isLikelyUrl(mediaUrl)) {
                displayRemoteContent(mediaUrl);
            } else {
                mediaPreview.setText("Invalid file path: " + ex.getMessage());
                mediaPreview.setVisible(true);
            }
            return;
        }

        if (!Files.isRegularFile(filePath)) {
            if (isLikelyUrl(mediaUrl)) {
                displayRemoteContent(mediaUrl);
            } else {
                displayFileFallback(path, "File not found or not a regular file");
            }
            return;
        }

        int dotIndex = path.lastIndexOf('.');
        if (dotIndex <= 0 || dotIndex == path.length() - 1) {
            if (isLikelyUrl(mediaUrl)) {
                displayRemoteContent(mediaUrl);
            } else {
                displayFileFallback(path, "Unsupported file type or missing extension");
            }
            return;
        }

        String extension = path.substring(dotIndex + 1).toLowerCase(Locale.ROOT);

        Item.FileType fileType = item.getFileType();
        if (fileType == Item.FileType.AUDIO) {
            displayMedia(path);
            return;
        }
        if (fileType == Item.FileType.VIDEO) {
            displayMedia(path);
            return;
        }
        if (fileType == Item.FileType.PDF) {
            displayPDF(path);
            return;
        }
        if (fileType == Item.FileType.TEXT || fileType == Item.FileType.MARKDOWN) {
            displayText(path);
            return;
        }

        if (isAudioExtension(extension)) {
            displayMedia(path);
            return;
        }

        if (isVideoExtension(extension)) {
            displayMedia(path);
            return;
        }

        switch (extension) {
            // Text files
            case "txt", "md", "rtf" -> displayText(path);
            // PDF files
            case "pdf" -> displayPDF(path);
            // Word documents
            case "docx" -> displayDocument(path, "docx");
            case "doc" -> displayDocument(path, "doc");
            // Excel spreadsheets
            case "xlsx", "xls" -> displayDocument(path, "excel");
            // Image files
            case "jpg", "jpeg", "png", "gif", "bmp" -> displayImage(path);
            // Unsupported
            default -> {
                if (isLikelyUrl(mediaUrl)) {
                    displayRemoteContent(mediaUrl);
                } else {
                    mediaPreview.setText("Preview not available for this file type.\n\nFile: " + path);
                    mediaPreview.setVisible(true);
                }
            }
        }
    }
    
    private void displayText(String path) {
        try {
            File file = new File(path);
            
            // Limit file size for performance (max 1MB for prompt display)
            if (file.length() > 1_000_000) {
                // Large files read partially for speed
                byte[] buffer = new byte[500_000]; // Read first 500KB
                try (FileInputStream fis = new FileInputStream(file)) {
                    int bytesRead = fis.read(buffer);
                    String content = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);
                    content += "\n\n[Large file truncated at 500KB for performance. Total size: " + formatFileSize(file.length()) + "]";
                    displayTextContent(path, content);
                }
                return;
            }
            
            String content = Files.readString(Paths.get(path));
            displayTextContent(path, content);
            
        } catch (IOException e) {
            mediaPreview.setText("Error reading file: " + e.getMessage());
            mediaPreview.setVisible(true);
        }
    }
    
    private void displayTextContent(String path, String content) {
        // Create toolbar for text viewer
        ToolBar textToolbar = new ToolBar();
        Button copyBtn = new Button("📋 Copy");
        Button saveBtn = new Button("💾 Save");
        Button zoomInBtn = new Button("🔍+");
        Button zoomOutBtn = new Button("🔍-");
        Label fileLabel = new Label("📄 " + Paths.get(path).getFileName());
        
        textToolbar.getItems().addAll(fileLabel, new Separator(), 
            copyBtn, saveBtn, new Separator(), zoomInBtn, zoomOutBtn);
        
        // Setup text area with content
        mediaPreview.setText(content);
        mediaPreview.setStyle("-fx-font-size: 14px; -fx-font-family: 'Consolas', 'Monaco', monospace;");
        
        // Copy button action
        copyBtn.setOnAction(e -> {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent clipContent = new javafx.scene.input.ClipboardContent();
            clipContent.putString(mediaPreview.getSelectedText().isEmpty() ? 
                mediaPreview.getText() : mediaPreview.getSelectedText());
            clipboard.setContent(clipContent);
            statusLabel.setText("Text copied to clipboard");
        });
        
        // Save button action
        saveBtn.setOnAction(e -> {
            try {
                Files.writeString(Paths.get(path), mediaPreview.getText());
                statusLabel.setText("File saved successfully");
                } catch (IOException ex) {
                    statusLabel.setText("Error saving file: " + ex.getMessage());
                }
            });
            
            // Zoom actions
            final double[] fontSize = {14.0};
            zoomInBtn.setOnAction(e -> {
                fontSize[0] = Math.min(fontSize[0] + 2, 32);
                mediaPreview.setStyle("-fx-font-size: " + fontSize[0] + "px; -fx-font-family: 'Consolas', 'Monaco', monospace;");
            });
            
            zoomOutBtn.setOnAction(e -> {
                fontSize[0] = Math.max(fontSize[0] - 2, 8);
                mediaPreview.setStyle("-fx-font-size: " + fontSize[0] + "px; -fx-font-family: 'Consolas', 'Monaco', monospace;");
            });
            
            VBox textViewerBox = new VBox(textToolbar, mediaPreview);
            VBox.setVgrow(mediaPreview, Priority.ALWAYS);
            mediaPane.getChildren().add(textViewerBox);
            mediaPreview.setVisible(true);
    }
    
    /**
     * Display document files (PDF, Word, Excel) with extracted text
     */
    private void displayDocument(String path, String type) {
        try {
            String content;
            String icon;
            String typeName;
            
            // Extract text based on document type
            if (type.equals("pdf")) {
                content = extractPDFText(path);
                icon = "📕";
                typeName = "PDF Document";
            } else if (type.equals("docx")) {
                content = extractDocxText(path);
                icon = "📘";
                typeName = "Word Document (DOCX)";
            } else if (type.equals("doc")) {
                content = extractDocText(path);
                icon = "📘";
                typeName = "Word Document (DOC)";
            } else if (type.equals("excel")) {
                content = extractExcelText(path);
                icon = "📊";
                typeName = "Excel Spreadsheet";
            } else {
                content = "Unknown document type";
                icon = "📄";
                typeName = "Document";
            }
            
            // Create toolbar for document viewer
            ToolBar docToolbar = new ToolBar();
            Label docLabel = new Label(icon + " " + Paths.get(path).getFileName() + " (" + typeName + ")");
            Button openExternalBtn = new Button("🚀 Open in External App");
            Button copyBtn = new Button("📋 Copy Text");
            Button saveBtn = new Button("💾 Save as TXT");
            Button zoomInBtn = new Button("🔍+");
            Button zoomOutBtn = new Button("🔍-");
            
            docToolbar.getItems().addAll(docLabel, new Separator(), 
                openExternalBtn, copyBtn, saveBtn, new Separator(), zoomInBtn, zoomOutBtn);
            
            // Open in external application
            openExternalBtn.setOnAction(e -> {
                try {
                    java.awt.Desktop.getDesktop().open(new File(path));
                    statusLabel.setText("Opened in external application");
                } catch (IOException | IllegalArgumentException ex) {
                    statusLabel.setText("Could not open external application: " + ex.getMessage());
                }
            });
            
            // Copy button action
            copyBtn.setOnAction(e -> {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent clipContent = new javafx.scene.input.ClipboardContent();
                clipContent.putString(mediaPreview.getSelectedText().isEmpty() ? 
                    mediaPreview.getText() : mediaPreview.getSelectedText());
                clipboard.setContent(clipContent);
                statusLabel.setText("Text copied to clipboard");
            });
            
            // Save as TXT button
            saveBtn.setOnAction(e -> {
                FileChooser fileChooser = new FileChooser();
                fileChooser.setTitle("Save Extracted Text");
                fileChooser.setInitialFileName(Paths.get(path).getFileName().toString().replaceAll("\\.[^.]+$", ".txt"));
                fileChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Text Files", "*.txt")
                );
                
                File file = fileChooser.showSaveDialog(mediaPane.getScene().getWindow());
                if (file != null) {
                    try {
                        Files.writeString(file.toPath(), mediaPreview.getText());
                        statusLabel.setText("Text saved to: " + file.getName());
                    } catch (IOException ex) {
                        statusLabel.setText("Error saving file: " + ex.getMessage());
                    }
                }
            });
            
            // Zoom actions
            final double[] fontSize = {14.0};
            zoomInBtn.setOnAction(e -> {
                fontSize[0] = Math.min(fontSize[0] + 2, 32);
                mediaPreview.setStyle("-fx-font-size: " + fontSize[0] + "px; -fx-font-family: 'Consolas', 'Monaco', monospace;");
            });
            
            zoomOutBtn.setOnAction(e -> {
                fontSize[0] = Math.max(fontSize[0] - 2, 8);
                mediaPreview.setStyle("-fx-font-size: " + fontSize[0] + "px; -fx-font-family: 'Consolas', 'Monaco', monospace;");
            });
            
            // Setup text area with extracted content
            mediaPreview.setText(content);
            mediaPreview.setEditable(false);
            mediaPreview.setWrapText(true);
            mediaPreview.setStyle("-fx-font-size: 14px; -fx-font-family: 'Consolas', 'Monaco', monospace;");
            
            VBox docViewerBox = new VBox(docToolbar, mediaPreview);
            VBox.setVgrow(mediaPreview, Priority.ALWAYS);
            mediaPane.getChildren().add(docViewerBox);
            mediaPreview.setVisible(true);
            
            statusLabel.setText("Loaded " + typeName + " - " + content.length() + " characters");
            
        } catch (Exception e) {
            displayFileFallback(path, "Error loading document: " + e.getMessage());
        }
    }
    
    private void displayImage(String path) {
        try {
            javafx.scene.image.Image image = new javafx.scene.image.Image("file:" + path);
            
            // Check if image loaded successfully
            if (image.isError()) {
                displayFileFallback(path, "Image format not supported or file corrupted");
                return;
            }
            
            javafx.scene.image.ImageView imageView = new javafx.scene.image.ImageView(image);
            
            // Make image responsive
            imageView.setPreserveRatio(true);
            imageView.fitWidthProperty().bind(mediaPane.widthProperty().subtract(20));
            imageView.fitHeightProperty().bind(mediaPane.heightProperty().subtract(80));
            
            // Create image viewer toolbar
            ToolBar imageToolbar = new ToolBar();
            Label imageLabel = new Label("🖼️ " + Paths.get(path).getFileName());
            Label sizeLabel = new Label(String.format("%.0f x %.0f", image.getWidth(), image.getHeight()));
            Button fitBtn = new Button("⬜ Fit");
            Button actualBtn = new Button("1:1 Actual");
            Button zoomInBtn = new Button("🔍+");
            Button zoomOutBtn = new Button("🔍-");
            
            imageToolbar.getItems().addAll(imageLabel, new Separator(), sizeLabel, 
                new Separator(), fitBtn, actualBtn, zoomInBtn, zoomOutBtn);
            
            // Zoom controls
            final double[] scale = {1.0};
            zoomInBtn.setOnAction(e -> {
                scale[0] = Math.min(scale[0] + 0.2, 5.0);
                imageView.setScaleX(scale[0]);
                imageView.setScaleY(scale[0]);
            });
            
            zoomOutBtn.setOnAction(e -> {
                scale[0] = Math.max(scale[0] - 0.2, 0.2);
                imageView.setScaleX(scale[0]);
                imageView.setScaleY(scale[0]);
            });
            
            fitBtn.setOnAction(e -> {
                scale[0] = 1.0;
                imageView.setScaleX(1.0);
                imageView.setScaleY(1.0);
                imageView.fitWidthProperty().bind(mediaPane.widthProperty().subtract(20));
                imageView.fitHeightProperty().bind(mediaPane.heightProperty().subtract(80));
            });
            
            actualBtn.setOnAction(e -> {
                scale[0] = 1.0;
                imageView.setScaleX(1.0);
                imageView.setScaleY(1.0);
                imageView.fitWidthProperty().unbind();
                imageView.fitHeightProperty().unbind();
                imageView.setFitWidth(image.getWidth());
                imageView.setFitHeight(image.getHeight());
            });
            
            ScrollPane scrollPane = new ScrollPane(imageView);
            scrollPane.setFitToWidth(true);
            scrollPane.setFitToHeight(true);
            scrollPane.setStyle("-fx-background-color: #2b2b2b;");
            
            VBox imageViewerBox = new VBox(imageToolbar, scrollPane);
            VBox.setVgrow(scrollPane, Priority.ALWAYS);
            mediaPane.getChildren().add(imageViewerBox);
            
        } catch (Exception e) {
            displayFileFallback(path, "Error loading image: " + e.getMessage());
        }
    }
    
    private void displayPDF(String path) {
        mediaPane.getChildren().clear();
        mediaPreview.setVisible(false);
        mediaPreview.clear();

        ToolBar pdfToolbar = new ToolBar();
        Label pdfLabel = new Label("📕 " + Paths.get(path).getFileName());
        Button openExternalBtn = new Button("🚀 Open in External App");
        Button showContentBtn = new Button("📄 Show Text Content");
        Button refreshBtn = new Button("🔄 Refresh");
        pdfToolbar.getItems().addAll(pdfLabel, new Separator(), openExternalBtn, showContentBtn, refreshBtn);

        openExternalBtn.setOnAction(e -> {
            try {
                java.awt.Desktop.getDesktop().open(new File(path));
                statusLabel.setText("Opened PDF in external application");
            } catch (IOException | IllegalArgumentException ex) {
                statusLabel.setText("Could not open external application: " + ex.getMessage());
            }
        });

        showContentBtn.setOnAction(e -> displayDocument(path, "pdf"));
        refreshBtn.setOnAction(e -> displayPDF(path));

        VBox pagesContainer = new VBox(18);
        pagesContainer.setAlignment(Pos.TOP_CENTER);
        pagesContainer.setStyle("-fx-background-color: #1f1f1f; -fx-padding: 20;");

        boolean rendered = renderPdfPreview(path, pagesContainer);
        if (!rendered) {
            return;
        }

        ScrollPane scrollPane = new ScrollPane(pagesContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: #1f1f1f;");

        VBox pdfViewerBox = new VBox(pdfToolbar, scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        mediaPane.getChildren().add(pdfViewerBox);
    }

    private boolean renderPdfPreview(String path, VBox pagesContainer) {
        try (PDDocument document = PDDocument.load(new File(path))) {
            int totalPages = document.getNumberOfPages();
            if (totalPages <= 0) {
                statusLabel.setText("PDF has no pages");
                displayDocument(path, "pdf");
                return false;
            }

            int pagesToRender = Math.min(totalPages, PDF_PREVIEW_PAGE_LIMIT);
            PDFRenderer renderer = new PDFRenderer(document);

            for (int i = 0; i < pagesToRender; i++) {
                BufferedImage bufferedImage = renderer.renderImageWithDPI(i, PDF_PREVIEW_DPI, ImageType.RGB);
                javafx.scene.image.Image fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

                Label pageLabel = new Label("Page " + (i + 1));
                pageLabel.setStyle("-fx-text-fill: #cccccc; -fx-font-size: 14px;");

                javafx.scene.image.ImageView pageView = new javafx.scene.image.ImageView(fxImage);
                pageView.setSmooth(true);
                pageView.setPreserveRatio(true);
                pageView.setFitWidth(900);
                pageView.fitWidthProperty().bind(mediaPane.widthProperty().subtract(60));

                VBox pageBox = new VBox(6, pageLabel, pageView);
                pageBox.setAlignment(Pos.CENTER);
                pagesContainer.getChildren().add(pageBox);
            }

            if (totalPages > pagesToRender) {
                Label truncatedLabel = new Label("Showing first " + pagesToRender + " of " + totalPages + " pages");
                truncatedLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-padding: 10 0 0 0;");
                pagesContainer.getChildren().add(truncatedLabel);
            }

            String status = String.format(Locale.ROOT, "Previewing PDF (%d/%d pages)", pagesToRender, totalPages);
            statusLabel.setText(status);
            return true;

        } catch (IOException ex) {
            displayDocument(path, "pdf");
            statusLabel.setText("Showing extracted text for PDF (preview failed: " + ex.getMessage() + ")");
            return false;
        }
    }
    
    private void displayMedia(String path) {
    String extension = getFileExtension(path);
    boolean videoContent = isVideoExtension(extension);

        Path localPath = Paths.get(path);
        String mediaUri = localPath.toUri().toString();

        try {
            Media media = new Media(mediaUri);
            currentMediaPlayer = new MediaPlayer(media);

            currentMediaPlayer.setOnError(() -> {
                MediaException mediaError = currentMediaPlayer.getError();
                String errorMessage = mediaError != null ? mediaError.getMessage() : "Unknown media error";
                Platform.runLater(() -> displayFileFallback(path, "Media codec not supported: " + errorMessage));
            });

            HBox controls = createMediaControls(currentMediaPlayer);
            controls.setAlignment(Pos.CENTER);
            controls.setPadding(new Insets(10));

            VBox mediaBox = new VBox(15);
            mediaBox.setAlignment(Pos.CENTER);
            mediaBox.setFillWidth(true);
            mediaBox.setPadding(new Insets(20));

            if (videoContent) {
                MediaView mediaView = new MediaView(currentMediaPlayer);
                mediaView.setFitWidth(800);
                mediaView.setFitHeight(450);
                mediaView.setPreserveRatio(true);
                mediaView.fitWidthProperty().bind(mediaPane.widthProperty().subtract(20));
                VBox.setVgrow(mediaView, Priority.ALWAYS);
                mediaBox.getChildren().addAll(mediaView, controls);
            } else {
                Label audioLabel = new Label("🎧 " + localPath.getFileName());
                audioLabel.setWrapText(true);
                audioLabel.setAlignment(Pos.CENTER);
                audioLabel.setMaxWidth(Double.MAX_VALUE);
                audioLabel.setStyle("-fx-text-fill: #f2f2f2; -fx-font-size: 18px;");
                mediaBox.getChildren().addAll(audioLabel, controls);
            }

            mediaPane.getChildren().add(mediaBox);

            currentMediaPlayer.setAutoPlay(true);

        } catch (MediaException | IllegalArgumentException e) {
            showEmbeddedMediaPlayer(mediaUri, videoContent, e.getMessage());
        }
    }

    private void displayStreamingMedia(String url, boolean video) {
        try {
            Media media = new Media(url);
            currentMediaPlayer = new MediaPlayer(media);

            currentMediaPlayer.setOnError(() -> {
                MediaException mediaError = currentMediaPlayer.getError();
                String errorMessage = mediaError != null ? mediaError.getMessage() : "Unknown media error";
                Platform.runLater(() -> {
                    statusLabel.setText("Native streaming failed: " + errorMessage);
                    downloadAndPlayRemoteMedia(url, video);
                });
            });

            MediaView mediaView = new MediaView(currentMediaPlayer);
            mediaView.setFitWidth(800);
            mediaView.setFitHeight(450);
            mediaView.setPreserveRatio(true);
            mediaView.fitWidthProperty().bind(mediaPane.widthProperty().subtract(20));

            HBox controls = createMediaControls(currentMediaPlayer);
            controls.setAlignment(Pos.CENTER);
            controls.setPadding(new javafx.geometry.Insets(10));

            VBox mediaBox = new VBox(10, mediaView, controls);
            mediaBox.setAlignment(Pos.CENTER);
            mediaBox.setFillWidth(true);
            VBox.setVgrow(mediaView, Priority.ALWAYS);

            mediaPane.getChildren().add(mediaBox);

            currentMediaPlayer.setAutoPlay(true);
            statusLabel.setText("Streaming media from URL");

        } catch (MediaException | IllegalArgumentException e) {
            downloadAndPlayRemoteMedia(url, video);
        }
    }

    private void downloadAndPlayRemoteMedia(String url, boolean video) {
        if (url == null || url.isBlank()) {
            showEmbeddedMediaPlayer(url, video, "No media URL provided");
            return;
        }

        if (currentMediaPlayer != null) {
            try {
                currentMediaPlayer.stop();
                currentMediaPlayer.dispose();
            } catch (IllegalStateException ignore) {
                // Safe to ignore issues while disposing
            }
            currentMediaPlayer = null;
        }

        String cacheKey = normaliseStreamKey(url);
        Path cachedPath = remoteMediaCache.get(cacheKey);
        if (cachedPath != null && Files.exists(cachedPath)) {
            statusLabel.setText("Using cached media preview");
            displayMedia(cachedPath.toString());
            return;
        }

        if (activeDownloads.contains(cacheKey)) {
            statusLabel.setText("Preparing media preview...");
            return;
        }

        activeDownloads.add(cacheKey);

        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setPrefSize(120, 120);
        Label progressLabel = new Label("Connecting...");
        progressLabel.setStyle("-fx-text-fill: white;");
        VBox progressBox = new VBox(12, indicator, progressLabel);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(20));
        mediaPane.getChildren().clear();
        mediaPane.getChildren().add(progressBox);
        mediaPreview.setVisible(false);
        statusLabel.setText("Downloading media for playback...");

        javafx.concurrent.Task<Path> downloadTask = new javafx.concurrent.Task<>() {
            @Override
            protected Path call() throws Exception {
                updateMessage("Connecting...");
                URI uri = buildMediaUri(url);
                HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build();

                HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status >= 400) {
                    throw new IOException("HTTP " + status + " while fetching media");
                }

                long contentLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
                if (contentLength > MAX_STREAM_DOWNLOAD_BYTES) {
                    throw new IOException("Media size " + formatFileSize(contentLength) + " exceeds inline limit of " + formatFileSize(MAX_STREAM_DOWNLOAD_BYTES));
                }

                Path tempFile = Files.createTempFile("smart-collections-media-", video ? ".mp4" : ".mp3");
                tempFile.toFile().deleteOnExit();

                try (InputStream in = response.body();
                     OutputStream out = Files.newOutputStream(tempFile)) {
                    byte[] buffer = new byte[8192];
                    long downloaded = 0;
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        downloaded += read;
                        if (downloaded > MAX_STREAM_DOWNLOAD_BYTES) {
                            throw new IOException("Media exceeds inline limit of " + formatFileSize(MAX_STREAM_DOWNLOAD_BYTES));
                        }
                        out.write(buffer, 0, read);
                        if (contentLength > 0) {
                            updateProgress(downloaded, contentLength);
                            updateMessage("Downloaded " + formatFileSize(downloaded) + " / " + formatFileSize(contentLength));
                        } else {
                            updateProgress(downloaded, MAX_STREAM_DOWNLOAD_BYTES);
                            updateMessage("Downloaded " + formatFileSize(downloaded));
                        }
                    }
                } catch (IOException ex) {
                    Files.deleteIfExists(tempFile);
                    throw ex;
                }

                return tempFile;
            }
        };

        indicator.progressProperty().bind(downloadTask.progressProperty());
        downloadTask.messageProperty().addListener((obs, old, val) -> {
            if (val == null || val.isBlank()) {
                progressLabel.setText("Connecting...");
            } else {
                progressLabel.setText(val);
            }
        });

        downloadTask.setOnSucceeded(evt -> {
            activeDownloads.remove(cacheKey);
            Path tempFile = downloadTask.getValue();
            if (tempFile != null && Files.exists(tempFile)) {
                Path previous = remoteMediaCache.put(cacheKey, tempFile);
                if (previous != null && !previous.equals(tempFile)) {
                    tryDeleteTemp(previous);
                }
                statusLabel.setText("Playing downloaded copy (cached temporarily)");
                displayMedia(tempFile.toString());
            } else {
                statusLabel.setText("Unable to prepare media preview");
                showEmbeddedMediaPlayer(url, video, "Download produced no file");
            }
        });

        downloadTask.setOnFailed(evt -> {
            activeDownloads.remove(cacheKey);
            Throwable ex = downloadTask.getException();
            String message = ex != null ? ex.getMessage() : "Unknown error";
            statusLabel.setText("Streaming failed: " + message);
            showEmbeddedMediaPlayer(url, video, message);
        });

        downloadTask.setOnCancelled(evt -> {
            activeDownloads.remove(cacheKey);
            statusLabel.setText("Streaming cancelled");
            showEmbeddedMediaPlayer(url, video, "Download cancelled");
        });

        Thread downloader = new Thread(downloadTask, "media-download-" + Math.abs(cacheKey.hashCode()));
        downloader.setDaemon(true);
        downloader.start();
    }

    private String normaliseStreamKey(String url) {
        if (url == null) {
            return "";
        }
        String trimmed = url.trim();
        int fragmentIndex = trimmed.indexOf('#');
        if (fragmentIndex >= 0) {
            trimmed = trimmed.substring(0, fragmentIndex);
        }
        return trimmed;
    }

    private URI buildMediaUri(String url) throws URISyntaxException {
        if (url == null) {
            throw new URISyntaxException("null", "URL is null");
        }
        try {
            return new URI(url);
        } catch (URISyntaxException ex) {
            String sanitised = url.trim().replace(" ", "%20");
            return new URI(sanitised);
        }
    }

    private void tryDeleteTemp(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignore) {
            // best-effort cleanup
        }
    }

    private void showEmbeddedMediaPlayer(String url, boolean video, String diagnostic) {
        if (currentMediaPlayer != null) {
            currentMediaPlayer.dispose();
            currentMediaPlayer = null;
        }

        mediaPane.getChildren().clear();
        mediaPreview.setVisible(false);
        String safeUrl = "";
        if (url != null) {
            try {
                safeUrl = buildMediaUri(url).toASCIIString();
            } catch (URISyntaxException ex) {
                safeUrl = url.trim().replace("\"", "&quot;").replace(" ", "%20");
            }
        }
        String mimeType = guessMimeType(url, video);
        String element = video
            ? "<video controls playsinline style=\"max-width:100%; max-height:100%; background:#000;\"><source src=\"" + safeUrl + "\" type=\"" + mimeType + "\"></video>"
            : "<audio controls style=\"width:100%;\"><source src=\"" + safeUrl + "\" type=\"" + mimeType + "\"></audio>";
        String html = "<html><body style='margin:0;background:#1f1f1f;display:flex;justify-content:center;align-items:center;height:100%;'>"
            + element + "</body></html>";

        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().loadContent(html);

        Label diagnosticLabel = new Label();
        diagnosticLabel.setWrapText(true);
        diagnosticLabel.setStyle("-fx-text-fill: #bbbbbb; -fx-padding: 8;");

        if (diagnostic != null && !diagnostic.isBlank()) {
            diagnosticLabel.setText("Fallback player active: " + diagnostic);
        }

        VBox container = new VBox(8, webView);
        VBox.setVgrow(webView, Priority.ALWAYS);
        if (diagnosticLabel.getText() != null && !diagnosticLabel.getText().isBlank()) {
            container.getChildren().add(diagnosticLabel);
        }
        mediaPane.getChildren().add(container);

        if (diagnostic != null && !diagnostic.isBlank()) {
            statusLabel.setText("Streaming via embedded player (fallback): " + diagnostic);
        } else {
            statusLabel.setText("Streaming via embedded player");
        }
    }

    private String guessMimeType(String url, boolean video) {
        if (url == null) {
            return video ? "video/mp4" : "audio/mpeg";
        }
        String lower = url.toLowerCase(Locale.ROOT);
        int queryIdx = lower.indexOf('?');
        if (queryIdx >= 0) {
            lower = lower.substring(0, queryIdx);
        }
        int fragmentIdx = lower.indexOf('#');
        if (fragmentIdx >= 0) {
            lower = lower.substring(0, fragmentIdx);
        }
        if (video) {
            if (lower.endsWith(".mp4")) return "video/mp4";
            if (lower.endsWith(".mov")) return "video/quicktime";
            if (lower.endsWith(".mkv")) return "video/x-matroska";
            if (lower.endsWith(".avi")) return "video/x-msvideo";
            return "video/mp4";
        }
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".m4a")) return "audio/mp4";
        if (lower.endsWith(".ogg")) return "audio/ogg";
        return "audio/mpeg";
    }

    private boolean isYouTubeUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.contains("youtube.com") || lower.contains("youtu.be") || lower.contains("youtube-nocookie.com");
    }

    private Optional<String> extractYouTubeVideoId(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = buildMediaUri(url);
            String host = uri.getHost();
            String path = uri.getPath() == null ? "" : uri.getPath();
            if (host == null) {
                return Optional.empty();
            }
            host = host.toLowerCase(Locale.ROOT);
            String candidate = null;

            if (host.contains("youtu.be")) {
                candidate = path.startsWith("/") ? path.substring(1) : path;
            } else if (host.contains("youtube")) {
                if (path.startsWith("/watch")) {
                    String query = uri.getQuery();
                    if (query != null) {
                        for (String param : query.split("&")) {
                            int eq = param.indexOf('=');
                            if (eq > 0) {
                                String name = param.substring(0, eq);
                                if ("v".equals(name)) {
                                    candidate = param.substring(eq + 1);
                                    break;
                                }
                            }
                        }
                    }
                } else if (path.startsWith("/embed/")) {
                    candidate = path.substring("/embed/".length());
                } else if (path.startsWith("/shorts/")) {
                    candidate = path.substring("/shorts/".length());
                }
            }

            if (candidate == null || candidate.isBlank()) {
                return Optional.empty();
            }

            int terminator = candidate.indexOf('/');
            if (terminator > 0) {
                candidate = candidate.substring(0, terminator);
            }
            terminator = candidate.indexOf('?');
            if (terminator > 0) {
                candidate = candidate.substring(0, terminator);
            }
            terminator = candidate.indexOf('&');
            if (terminator > 0) {
                candidate = candidate.substring(0, terminator);
            }

            StringBuilder clean = new StringBuilder();
            for (char c : candidate.toCharArray()) {
                if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                    clean.append(c);
                }
            }

            if (clean.length() < 6) {
                return Optional.empty();
            }
            return Optional.of(clean.toString());
        } catch (URISyntaxException ex) {
            return Optional.empty();
        }
    }

    private void displayYouTubeEmbed(String url) {
        mediaPane.getChildren().clear();
        mediaPreview.setVisible(false);

        Optional<String> videoId = extractYouTubeVideoId(url);
        if (videoId.isEmpty()) {
            mediaPreview.setText("Unable to embed YouTube URL: " + url);
            mediaPreview.setVisible(true);
            statusLabel.setText("YouTube link unsupported");
            return;
        }

        String embedUrl = "https://www.youtube.com/embed/" + videoId.get() + "?rel=0&modestbranding=1";
        String html = "<!DOCTYPE html><html><body style='margin:0;background:#000;display:flex;align-items:center;justify-content:center;height:100%;'>"
            + "<iframe src='" + embedUrl + "' frameborder='0' allow='accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share'"
            + " allowfullscreen style='width:100%;height:100%;'></iframe></body></html>";

        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().setOnError(event -> {
            mediaPane.getChildren().clear();
            mediaPreview.setText("Unable to load YouTube video: " + event.getMessage());
            mediaPreview.setVisible(true);
        });
        webView.getEngine().loadContent(html);

        VBox container = new VBox(webView);
        VBox.setVgrow(webView, Priority.ALWAYS);
        mediaPane.getChildren().add(container);
        statusLabel.setText("Embedded YouTube player");
    }

    private void displayRemoteContent(String url) {
        if (url == null || url.isBlank()) {
            mediaPreview.setText("No media source associated with this item.");
            mediaPreview.setVisible(true);
            return;
        }

        String trimmed = url.trim();
        if (!isLikelyUrl(trimmed)) {
            mediaPreview.setText("Unsupported media URL: " + trimmed);
            mediaPreview.setVisible(true);
            return;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);
        int queryIndex = lower.indexOf('?');
        String normalised = queryIndex > 0 ? lower.substring(0, queryIndex) : lower;

        if (isYouTubeUrl(trimmed)) {
            displayYouTubeEmbed(trimmed);
            return;
        }

        if (normalised.endsWith(".mp3") || normalised.endsWith(".wav") || normalised.endsWith(".m4a") || normalised.endsWith(".ogg")
            || normalised.endsWith(".mp4") || normalised.endsWith(".avi") || normalised.endsWith(".mov") || normalised.endsWith(".mkv")) {
            boolean video = normalised.endsWith(".mp4") || normalised.endsWith(".avi") || normalised.endsWith(".mov") || normalised.endsWith(".mkv");
            String cacheKey = normaliseStreamKey(trimmed);
            Path cachedPath = remoteMediaCache.get(cacheKey);
            if (cachedPath != null && Files.exists(cachedPath)) {
                statusLabel.setText("Using cached media preview");
                displayMedia(cachedPath.toString());
                return;
            }
            displayStreamingMedia(trimmed, video);
            return;
        }

        WebView webView = new WebView();
        webView.setContextMenuEnabled(false);
        webView.getEngine().setOnError(event -> {
            mediaPane.getChildren().clear();
            mediaPreview.setText("Unable to load remote content: " + event.getMessage());
            mediaPreview.setVisible(true);
        });

        try {
            webView.getEngine().load(trimmed);
            VBox container = new VBox(webView);
            VBox.setVgrow(webView, Priority.ALWAYS);
            mediaPane.getChildren().add(container);
            statusLabel.setText("Loaded remote content");
        } catch (RuntimeException ex) {
            mediaPreview.setText("Unable to load remote content: " + ex.getMessage());
            mediaPreview.setVisible(true);
        }
    }

    private boolean isLikelyUrl(String value) {
        if (value == null) {
            return false;
        }
        String trimmed = value.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("file://");
    }
    
    private HBox createMediaControls(MediaPlayer player) {
        // Playback buttons
        Button playPauseBtn = new Button("▶");
        playPauseBtn.setPrefSize(40, 40);
        Button stopBtn = new Button("⏹");
        stopBtn.setPrefSize(40, 40);
        
        // Time labels
        Label timeLabel = new Label("00:00");
        Label durationLabel = new Label("00:00");
        Label separatorLabel = new Label(" / ");
        
        // Progress slider
        Slider progressSlider = new Slider();
        progressSlider.setPrefWidth(400);
        progressSlider.setMin(0);
        progressSlider.setValue(0);
        
        // Volume controls
        Button muteBtn = new Button("🔊");
        muteBtn.setPrefSize(40, 40);
        Slider volumeSlider = new Slider(0, 1, 0.5);
        volumeSlider.setPrefWidth(100);
        Label volumeLabel = new Label("50%");
        
        // Playback control logic
        final boolean[] isPlaying = {false};
        playPauseBtn.setOnAction(e -> {
            if (isPlaying[0]) {
                player.pause();
                playPauseBtn.setText("▶");
                isPlaying[0] = false;
            } else {
                player.play();
                playPauseBtn.setText("⏸");
                isPlaying[0] = true;
            }
        });
        
        stopBtn.setOnAction(e -> {
            player.stop();
            playPauseBtn.setText("▶");
            isPlaying[0] = false;
            progressSlider.setValue(0);
            timeLabel.setText("00:00");
        });
        
        // Volume control logic
        player.volumeProperty().bind(volumeSlider.valueProperty());
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            int percent = (int) (newVal.doubleValue() * 100);
            volumeLabel.setText(percent + "%");
            if (percent == 0) {
                muteBtn.setText("🔇");
            } else if (percent < 50) {
                muteBtn.setText("🔉");
            } else {
                muteBtn.setText("🔊");
            }
        });
        
        // Mute toggle
        final double[] previousVolume = {0.5};
        muteBtn.setOnAction(e -> {
            if (volumeSlider.getValue() > 0) {
                previousVolume[0] = volumeSlider.getValue();
                volumeSlider.setValue(0);
            } else {
                volumeSlider.setValue(previousVolume[0]);
            }
        });
        
        // Progress slider logic
        player.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!progressSlider.isValueChanging()) {
                progressSlider.setValue(newTime.toSeconds());
                timeLabel.setText(formatTime(newTime.toSeconds()));
            }
        });
        
        progressSlider.valueChangingProperty().addListener((obs, wasChanging, isNowChanging) -> {
            if (!isNowChanging) {
                player.seek(javafx.util.Duration.seconds(progressSlider.getValue()));
            }
        });
        
        // Set duration when media is ready
        player.setOnReady(() -> {
            double duration = player.getTotalDuration().toSeconds();
            progressSlider.setMax(duration);
            durationLabel.setText(formatTime(duration));
        });
        
        // Auto-play
        player.setOnPlaying(() -> {
            playPauseBtn.setText("⏸");
            isPlaying[0] = true;
        });
        
        player.setOnPaused(() -> {
            playPauseBtn.setText("▶");
            isPlaying[0] = false;
        });
        
        player.setOnEndOfMedia(() -> {
            playPauseBtn.setText("▶");
            isPlaying[0] = false;
            progressSlider.setValue(0);
            timeLabel.setText("00:00");
        });
        
        // Layout controls
        HBox playbackControls = new HBox(10, playPauseBtn, stopBtn);
        playbackControls.setAlignment(Pos.CENTER_LEFT);
        
        HBox timeControls = new HBox(5, timeLabel, separatorLabel, durationLabel);
        timeControls.setAlignment(Pos.CENTER);
        
        HBox volumeControls = new HBox(5, muteBtn, volumeSlider, volumeLabel);
        volumeControls.setAlignment(Pos.CENTER_RIGHT);
        
        VBox progressBox = new VBox(5, progressSlider, 
            new HBox(10, playbackControls, timeControls, volumeControls));
        HBox.setHgrow(timeControls, Priority.ALWAYS);
        
        HBox controls = new HBox(10, progressBox);
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-background-color: #2b2b2b; -fx-text-fill: white;");
        HBox.setHgrow(progressBox, Priority.ALWAYS);
        progressSlider.setMaxWidth(Double.MAX_VALUE);
        
        return controls;
    }
    
    private String formatTime(double seconds) {
        int mins = (int) seconds / 60;
        int secs = (int) seconds % 60;
        return String.format("%02d:%02d", mins, secs);
    }

    private String getFileExtension(String path) {
        if (path == null) {
            return "";
        }
        int dotIndex = path.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == path.length() - 1) {
            return "";
        }
        return path.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String formatFileType(Item item) {
        if (item == null) {
            return "Unknown";
        }
        return formatFileType(item.getFileType());
    }

    private String formatFileType(Item.FileType fileType) {
        if (fileType == null) {
            return "Unknown";
        }
        return switch (fileType) {
            case PDF -> "📕 PDF";
            case TEXT, MARKDOWN -> "📝 Text";
            case AUDIO -> "🎧 Audio";
            case VIDEO -> "🎬 Video";
            default -> "❓ Unknown";
        };
    }

    private boolean isAudioExtension(String extension) {
        return switch (extension) {
            case "mp3", "wav", "m4a", "ogg", "aac", "flac" -> true;
            default -> false;
        };
    }

    private boolean isVideoExtension(String extension) {
        return switch (extension) {
            case "mp4", "m4v", "mov", "mkv", "avi", "webm" -> true;
            default -> false;
        };
    }
    
    /**
     * Fallback method to display file content when native viewer fails
     * Shows file info, metadata, and content in multiple formats
     */
    private void displayFileFallback(String path, String errorMessage) {
        try {
            File file = new File(path);
            StringBuilder content = new StringBuilder();
            
            // Header with error message
            content.append("═══════════════════════════════════════════════\n");
            content.append("⚠️  FALLBACK VIEWER ⚠️\n");
            content.append("═══════════════════════════════════════════════\n\n");
            content.append("Reason: ").append(errorMessage).append("\n\n");
            
            // File information
            content.append("📁 FILE INFORMATION:\n");
            content.append("─────────────────────────────────────────────\n");
            content.append("Name: ").append(file.getName()).append("\n");
            content.append("Path: ").append(file.getAbsolutePath()).append("\n");
            content.append("Size: ").append(formatFileSize(file.length())).append("\n");
            content.append("Modified: ").append(new java.util.Date(file.lastModified())).append("\n");
            content.append("Readable: ").append(file.canRead() ? "✓" : "✗").append("\n");
            content.append("Writable: ").append(file.canWrite() ? "✓" : "✗").append("\n\n");
            
            // Try to read as text
            content.append("📄 CONTENT PREVIEW (Text):\n");
            content.append("─────────────────────────────────────────────\n");
            try {
                byte[] bytes = Files.readAllBytes(Paths.get(path));
                
                // Try to detect if it's text or binary
                boolean isBinary = false;
                for (int i = 0; i < Math.min(512, bytes.length); i++) {
                    byte b = bytes[i];
                    if (b == 0 || (b < 32 && b != 9 && b != 10 && b != 13)) {
                        isBinary = true;
                        break;
                    }
                }
                
                if (!isBinary && bytes.length > 0) {
                    // Show as text (first 10000 characters)
                    String textContent = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                    if (textContent.length() > 10000) {
                        content.append(textContent, 0, 10000);
                        content.append("\n\n[... truncated ").append(textContent.length() - 10000).append(" characters ...]");
                    } else {
                        content.append(textContent);
                    }
                } else {
                    // Show as hex dump for binary files
                    content.append("⚠️ Binary file detected. Showing hex dump:\n\n");
                    int limit = Math.min(512, bytes.length);
                    for (int i = 0; i < limit; i += 16) {
                        content.append(String.format("%08X  ", i));
                        
                        // Hex values
                        for (int j = 0; j < 16; j++) {
                            if (i + j < limit) {
                                content.append(String.format("%02X ", bytes[i + j]));
                            } else {
                                content.append("   ");
                            }
                            if (j == 7) content.append(" ");
                        }
                        
                        content.append(" |");
                        
                        // ASCII representation
                        for (int j = 0; j < 16 && i + j < limit; j++) {
                            byte b = bytes[i + j];
                            if (b >= 32 && b < 127) {
                                content.append((char) b);
                            } else {
                                content.append(".");
                            }
                        }
                        content.append("|\n");
                    }
                    
                    if (bytes.length > limit) {
                        content.append("\n[... showing first ").append(limit).append(" of ").append(bytes.length).append(" bytes ...]");
                    }
                }
            } catch (Exception e) {
                content.append("Could not read file content: ").append(e.getMessage());
            }
            
            // Create toolbar
            ToolBar toolbar = new ToolBar();
            Button openExternalBtn = new Button("🚀 Open in System App");
            Button copyPathBtn = new Button("📋 Copy Path");
            Button refreshBtn = new Button("🔄 Retry");
            Label fileLabel = new Label("📄 " + file.getName());
            
            toolbar.getItems().addAll(fileLabel, new Separator(), 
                openExternalBtn, copyPathBtn, refreshBtn);
            
            openExternalBtn.setOnAction(e -> {
                try {
                    java.awt.Desktop.getDesktop().open(file);
                    statusLabel.setText("Opening in system default application...");
                } catch (Exception ex) {
                    statusLabel.setText("Error opening file: " + ex.getMessage());
                }
            });
            
            copyPathBtn.setOnAction(e -> {
                javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
                javafx.scene.input.ClipboardContent clipContent = new javafx.scene.input.ClipboardContent();
                clipContent.putString(file.getAbsolutePath());
                clipboard.setContent(clipContent);
                statusLabel.setText("File path copied to clipboard");
            });
            
            refreshBtn.setOnAction(e -> {
                // Re-attempt to display the item
                Item item = itemTable.getSelectionModel().getSelectedItem();
                if (item != null) {
                    displayItem(item);
                }
            });
            
            // Display content
            mediaPreview.setText(content.toString());
            mediaPreview.setStyle("-fx-font-size: 12px; -fx-font-family: 'Consolas', 'Monaco', monospace;");
            
            VBox fallbackBox = new VBox(toolbar, mediaPreview);
            VBox.setVgrow(mediaPreview, Priority.ALWAYS);
            mediaPane.getChildren().add(fallbackBox);
            mediaPreview.setVisible(true);
            
            statusLabel.setText("Using fallback viewer - " + errorMessage);
            
        } catch (Exception e) {
            mediaPreview.setText("Critical error displaying file:\n\n" + 
                "File: " + path + "\n" +
                "Error: " + e.getMessage() + "\n\n" +
                "The file exists but cannot be read or displayed.");
            mediaPreview.setVisible(true);
        }
    }
    
    /**
     * Extract text content from PDF file with caching
     */
    private String extractPDFText(String path) {
        // Check cache first
        String cached = getCachedText(path);
        if (cached != null) {
            return cached;
        }
        
        try (PDDocument document = PDDocument.load(new File(path))) {
            PDFTextStripper stripper = new PDFTextStripper();
            
            // Limit pages for faster extraction (first 100 pages)
            int totalPages = document.getNumberOfPages();
            if (totalPages > 100) {
                stripper.setEndPage(100);
            }
            
            String text = stripper.getText(document);
            String result = text != null && !text.trim().isEmpty() ? text : "No text content found in PDF";
            
            if (totalPages > 100) {
                result = result + "\n\n[Showing first 100 of " + totalPages + " pages for performance. Open in external app for full document.]";
            }
            
            // Cache the result
            cacheText(path, result);
            return result;
        } catch (Exception e) {
            return "Error extracting PDF text: " + e.getMessage();
        }
    }
    
    /**
     * Extract text content from Word document (.docx) with caching
     */
    private String extractDocxText(String path) {
        // Check cache first
        String cached = getCachedText(path);
        if (cached != null) {
            return cached;
        }
        
        try (FileInputStream fis = new FileInputStream(path);
             XWPFDocument document = new XWPFDocument(fis)) {
            
            StringBuilder text = new StringBuilder();
            int paragraphCount = 0;
            int maxParagraphs = 500; // Limit for performance
            
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                if (paragraphCount++ >= maxParagraphs) {
                    text.append("\n\n[Truncated for performance. Open in external app for full document.]");
                    break;
                }
                text.append(paragraph.getText()).append("\n");
            }
            
            String result = text.toString().trim();
            result = !result.isEmpty() ? result : "No text content found in document";
            
            // Cache the result
            cacheText(path, result);
            return result;
        } catch (Exception e) {
            return "Error extracting DOCX text: " + e.getMessage();
        }
    }
    
    /**
     * Extract text content from old Word document (.doc) with caching
     */
    private String extractDocText(String path) {
        // Check cache first
        String cached = getCachedText(path);
        if (cached != null) {
            return cached;
        }
        
        try (FileInputStream fis = new FileInputStream(path);
             HWPFDocument document = new HWPFDocument(fis);
             WordExtractor extractor = new WordExtractor(document)) {
            String text = extractor.getText();
            String result = text != null && !text.trim().isEmpty() ? text : "No text content found in document";
            
            // Truncate if too long
            if (result.length() > 100000) {
                result = result.substring(0, 100000) + "\n\n[Truncated for performance. Open in external app for full document.]";
            }
            
            // Cache the result
            cacheText(path, result);
            return result;
        } catch (IOException | RuntimeException e) {
            return "Error extracting DOC text: " + e.getMessage();
        }
    }
    
    /**
     * Extract text content from Excel file (.xlsx or .xls) with caching
     */
    private String extractExcelText(String path) {
        // Check cache first
        String cached = getCachedText(path);
        if (cached != null) {
            return cached;
        }
        
        try (FileInputStream fis = new FileInputStream(path)) {
            Workbook workbook;
            
            // Try XLSX first, then XLS
            if (path.toLowerCase().endsWith(".xlsx")) {
                workbook = new XSSFWorkbook(fis);
            } else {
                workbook = new HSSFWorkbook(fis);
            }
            
            StringBuilder text = new StringBuilder();
            int sheetCount = workbook.getNumberOfSheets();
            int maxSheets = Math.min(sheetCount, 10); // Limit to 10 sheets for performance
            
            for (int i = 0; i < maxSheets; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                text.append("═══ Sheet: ").append(sheet.getSheetName()).append(" ═══\n\n");
                
                int rowCount = 0;
                int maxRows = 1000; // Limit rows for performance
                
                for (Row row : sheet) {
                    if (rowCount++ >= maxRows) {
                        text.append("[Showing first ").append(maxRows).append(" rows. Open in external app for full sheet.]\n\n");
                        break;
                    }
                    
                    for (org.apache.poi.ss.usermodel.Cell cell : row) {
                        switch (cell.getCellType()) {
                            case STRING:
                                text.append(cell.getStringCellValue()).append("\t");
                                break;
                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    text.append(cell.getDateCellValue()).append("\t");
                                } else {
                                    text.append(cell.getNumericCellValue()).append("\t");
                                }
                                break;
                            case BOOLEAN:
                                text.append(cell.getBooleanCellValue()).append("\t");
                                break;
                            case FORMULA:
                                text.append(cell.getCellFormula()).append("\t");
                                break;
                            default:
                                text.append("\t");
                        }
                    }
                    text.append("\n");
                }
                text.append("\n");
            }
            
            if (sheetCount > maxSheets) {
                text.append("\n[Showing first ").append(maxSheets).append(" of ").append(sheetCount).append(" sheets. Open in external app for all sheets.]\n");
            }
            
            workbook.close();
            String result = text.toString().trim();
            result = !result.isEmpty() ? result : "No data found in spreadsheet";
            
            // Cache the result
            cacheText(path, result);
            return result;
            
        } catch (Exception e) {
            return "Error extracting Excel text: " + e.getMessage();
        }
    }
    
    /**
     * Cache extracted text for performance
     */
    private void cacheText(String path, String text) {
        textCache.put(path, text);
        cacheTimestamps.put(path, System.currentTimeMillis());
    }
    
    /**
     * Get cached text if available and still valid
     */
    private String getCachedText(String path) {
        if (!textCache.containsKey(path)) {
            return null;
        }
        
        // Check if cache is still valid
        Long timestamp = cacheTimestamps.get(path);
        if (timestamp == null || (System.currentTimeMillis() - timestamp) > CACHE_VALIDITY_MS) {
            // Cache expired
            textCache.remove(path);
            cacheTimestamps.remove(path);
            return null;
        }
        
        return textCache.get(path);
    }
    
    /**
     * Format file size in human-readable format
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.2f %sB", bytes / Math.pow(1024, exp), pre);
    }
    
    private void showAddItemDialog() {
        Dialog<Item> dialog = new Dialog<>();
        dialog.setTitle("Add New Item");
        dialog.setHeaderText("Enter item details");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        TextField titleField = new TextField();
        ComboBox<Category> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(Category.values());
        categoryCombo.setValue(Category.OTHER);
        
        TextField tagsField = new TextField();
        tagsField.setPromptText("comma-separated");
        
    Slider ratingSlider = new Slider(1, 5, 3);
        ratingSlider.setShowTickLabels(true);
        ratingSlider.setShowTickMarks(true);
        ratingSlider.setMajorTickUnit(1);
        ratingSlider.setBlockIncrement(1);
        ratingSlider.setSnapToTicks(true);
        
        TextField filePathField = new TextField();
        Button browseBtn = new Button("Browse...");
        browseBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            File file = fc.showOpenDialog(dialog.getOwner());
            if (file != null) {
                filePathField.setText(file.getAbsolutePath());
                if (titleField.getText().isEmpty()) {
                    titleField.setText(file.getName());
                }
            }
        });
        
        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("Tags:"), 0, 2);
        grid.add(tagsField, 1, 2);
        grid.add(new Label("Rating:"), 0, 3);
        grid.add(ratingSlider, 1, 3);
        grid.add(new Label("File:"), 0, 4);
        grid.add(filePathField, 1, 4);
        grid.add(browseBtn, 2, 4);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                Item item = new Item(titleField.getText(), categoryCombo.getValue(), filePathField.getText());
                item.setRating((int) ratingSlider.getValue());
                if (!tagsField.getText().isEmpty()) {
                    for (String tag : tagsField.getText().split(",")) {
                        item.addTag(tag.trim());
                    }
                }
                return item;
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(item -> {
            if (libraryService.addItem(item)) {
                refreshItemTable();
                refreshTagFilters();
                itemTable.getSelectionModel().select(item);
                statusLabel.setText("Item added: " + item.getTitle());
            } else {
                showAlert("Duplicate", "An item with this path already exists.", Alert.AlertType.WARNING);
            }
        });
    }
    
    private void handleEditItem() {
        Item item = itemTable.getSelectionModel().getSelectedItem();
        if (item == null) {
            showAlert("No Selection", "Please select an item to edit", Alert.AlertType.WARNING);
            return;
        }
        
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit Item");
        dialog.setHeaderText("Edit: " + item.getTitle());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        TextField titleField = new TextField(item.getTitle());
        ComboBox<Category> categoryCombo = new ComboBox<>();
        categoryCombo.getItems().addAll(Category.values());
        categoryCombo.setValue(item.getCategory());
        
        TextField tagsField = new TextField(String.join(", ", item.getTags()));
        Slider ratingSlider = new Slider(0, 5, item.getRating());
        ratingSlider.setShowTickLabels(true);
        ratingSlider.setShowTickMarks(true);
        ratingSlider.setMajorTickUnit(1);
        ratingSlider.setSnapToTicks(true);
        
        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Category:"), 0, 1);
        grid.add(categoryCombo, 1, 1);
        grid.add(new Label("Tags:"), 0, 2);
        grid.add(tagsField, 1, 2);
        grid.add(new Label("Rating:"), 0, 3);
        grid.add(ratingSlider, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(button -> button == ButtonType.OK);
        
        dialog.showAndWait().ifPresent(result -> {
            if (result) {
                try {
                    libraryService.editItem(item, updated -> {
                        updated.setTitle(titleField.getText().trim());
                        updated.setCategory(categoryCombo.getValue());
                        updated.setRating((int) ratingSlider.getValue());
                        Set<String> newTags = new LinkedHashSet<>();
                        for (String tag : tagsField.getText().split(",")) {
                            if (!tag.isBlank()) {
                                newTags.add(tag.trim().toLowerCase(Locale.ROOT));
                            }
                        }
                        updated.setTags(newTags);
                    });
                    populateDetail(item);
                    applyFilters();
                    refreshTagFilters();
                    statusLabel.setText("Item updated");
                } catch (IllegalArgumentException ex) {
                    showAlert("Duplicate Path", ex.getMessage(), Alert.AlertType.ERROR);
                }
            }
        });
    }
    
    private void handleDeleteItem() {
        Item item = itemTable.getSelectionModel().getSelectedItem();
        if (item == null) {
            showAlert("No Selection", "Please select an item to delete", Alert.AlertType.WARNING);
            return;
        }
        
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete item: " + item.getTitle() + "?");
        confirm.setContentText("This can be undone using the Undo function.");
        
        confirm.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                libraryService.deleteItem(item);
                refreshItemTable();
                refreshTagFilters();
                if (item.equals(activeItem)) {
                    clearDetailPane();
                    mediaPane.getChildren().clear();
                    mediaPreview.clear();
                    mediaPreview.setVisible(false);
                }
                statusLabel.setText("Item deleted (undo available)");
            }
        });
    }
    
    private void showAddTaskDialog() {
        Item item = itemTable.getSelectionModel().getSelectedItem();
        if (item == null) {
            showAlert("No Selection", "Please select an item to create a task for", Alert.AlertType.WARNING);
            return;
        }
        
        Dialog<Task> dialog = new Dialog<>();
        dialog.setTitle("Add Task");
        dialog.setHeaderText("Create task for: " + item.getTitle());
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        TextArea descField = new TextArea();
        descField.setPrefRowCount(3);
        descField.setPromptText("e.g., Read chapter 5, Complete assignment");
        
        DatePicker deadlinePicker = new DatePicker(LocalDateTime.now().plusDays(7).toLocalDate());
        
        Spinner<Integer> hourSpinner = new Spinner<>(0, 23, 23);
        Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, 59);
        
        ComboBox<Task.Priority> priorityCombo = new ComboBox<>();
        priorityCombo.getItems().addAll(Task.Priority.values());
        priorityCombo.setValue(Task.Priority.MEDIUM);
        
        grid.add(new Label("Description:"), 0, 0);
        grid.add(descField, 1, 0, 2, 1);
        grid.add(new Label("Deadline Date:"), 0, 1);
        grid.add(deadlinePicker, 1, 1);
        grid.add(new Label("Time:"), 0, 2);
        HBox timeBox = new HBox(5, hourSpinner, new Label(":"), minuteSpinner);
        grid.add(timeBox, 1, 2);
        grid.add(new Label("Priority:"), 0, 3);
        grid.add(priorityCombo, 1, 3);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(button -> {
            if (button == ButtonType.OK) {
                LocalDateTime deadline = deadlinePicker.getValue().atTime(
                    hourSpinner.getValue(), minuteSpinner.getValue());
                return new Task(item.getId(), descField.getText(), deadline, priorityCombo.getValue());
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(task -> {
            libraryService.addTask(task);
            refreshTaskList();
            statusLabel.setText("Task added");
        });
    }
    
    private void handleCompleteTask() {
        Task task = taskListView.getSelectionModel().getSelectedItem();
        if (task != null) {
            libraryService.deleteTask(task);
            refreshTaskList();
            statusLabel.setText("Task completed");
        }
    }
    
    private void refreshItemTable() {
        applyFilters();
        updateBackButtonState();
    }
    
    private void refreshTaskList() {
        taskListView.getItems().setAll(libraryService.getAllTasks());
        updateNextTaskBanner();
    }

    private void updateNextTaskBanner() {
        if (nextTaskLabel == null) return;
        libraryService.peekNextTask()
            .map(task -> "Next up: " + task.getDescription() + " (" + task.getPriority() + ")")
            .ifPresentOrElse(nextTaskLabel::setText, () -> nextTaskLabel.setText("Next up: -"));
    }
    
    private void refreshRecentlyViewed() {
        recentlyViewedList.getItems().setAll(libraryService.getRecentlyViewed());
        updateBackButtonState();
    }
    
    private void showAboutDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Smart Collections Manager v2.0");
        alert.setContentText("A comprehensive JavaFX application for managing student study materials.\n\n" +
                             "Features:\n" +
                             "- Library management with search\n" +
                             "- Recursive folder import\n" +
                             "- Priority task queue\n" +
                             "- Media preview\n" +
                             "- Undo functionality\n" +
                             "- Binary persistence\n\n" +
                             "ITEC627 Advanced Programming Assessment 2");
        alert.showAndWait();
    }
    
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
    
    // Theme Switcher - NEW FEATURE!
    private void switchTheme(String theme) {
        Scene scene = itemTable.getScene();
        if (scene != null) {
            scene.getStylesheets().clear();
            String cssFile = theme.equals("dark") ? "/styles-dark.css" : "/styles.css";
            scene.getStylesheets().add(getClass().getResource(cssFile).toExternalForm());
            statusLabel.setText("Theme switched to " + theme);
        }
    }
    
    // Refresh View - NEW FEATURE!
    private void refreshView() {
        refreshItemTable();
        refreshTaskList();
        refreshRecentlyViewed();
        refreshTagFilters();
        statusLabel.setText("View refreshed");
    }
    
    // Statistics Dialog - NEW FEATURE!
    private void showStatisticsDialog() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Library Statistics");
        alert.setHeaderText("Smart Collections Statistics");
        
        // Calculate statistics
        int totalItems = libraryService.getAllItems().size();
        int totalTasks = libraryService.getAllTasks().size();
        
        Map<Category, Long> categoryCount = new HashMap<>();
        for (Item item : libraryService.getAllItems()) {
            categoryCount.put(item.getCategory(), 
                categoryCount.getOrDefault(item.getCategory(), 0L) + 1);
        }
        
        // Build statistics text
        StringBuilder stats = new StringBuilder();
        stats.append("📊 LIBRARY OVERVIEW\n");
        stats.append("═══════════════════\n\n");
        stats.append("Total Items: ").append(totalItems).append("\n");
        stats.append("Active Tasks: ").append(totalTasks).append("\n\n");
        
        stats.append("📁 BY CATEGORY\n");
        stats.append("═══════════════════\n");
        for (Category category : Category.values()) {
            long count = categoryCount.getOrDefault(category, 0L);
            if (count > 0) {
                String icon = getCategoryIcon(category);
                double percentage = (count * 100.0) / totalItems;
                stats.append(String.format("%s %s: %d (%.1f%%)\n", 
                    icon, category.name(), count, percentage));
            }
        }
        
        stats.append("\n⭐ RATINGS\n");
        stats.append("═══════════════════\n");
        double avgRating = libraryService.getAllItems().stream()
            .mapToDouble(Item::getRating)
            .average()
            .orElse(0.0);
        long highRated = libraryService.getAllItems().stream()
            .filter(item -> item.getRating() >= 8)
            .count();
        stats.append(String.format("Average Rating: %.1f/10\n", avgRating));
        stats.append(String.format("Highly Rated (≥8): %d items\n", highRated));
        
        stats.append("\n🔍 RECENTLY VIEWED\n");
        stats.append("═══════════════════\n");
        stats.append("Recent Items: ").append(libraryService.getRecentlyViewed().size()).append("\n");
        
        alert.setContentText(stats.toString());
        alert.getDialogPane().setMinWidth(500);
        alert.showAndWait();
    }
    
    private String getCategoryIcon(Category category) {
        return switch (category) {
            case ASSIGNMENT -> "�";
            case LECTURE_NOTES -> "📓";
            case TUTORIAL -> "�";
            case REFERENCE -> "📖";
            case AUDIO_RECORDING -> "🎵";
            case VIDEO_TUTORIAL -> "🎬";
            case PAPER -> "📄";
            case TEXTBOOK -> "📘";
            case OTHER -> "📦";
        };
    }
    
    public static void main(String[] args) {
        launch(args);
    }
}
