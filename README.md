# Smart Collections Manager

Smart Collections Manager is a desktop JavaFX application for organising study notes, documents, and media.

## Build prerequisites
- JDK 17 or later
- Maven 3.6 or later
- Internet access for Maven Central (first build only)

## Build and run
```bash
mvn clean package

# run via JavaFX plugin
mvn javafx:run

# run the shaded jar
java -jar target/smart-collections-manager-1.0.0-all.jar
```

`target/smart-collections-manager-1.0.0-all.jar` contains every dependency and is the recommended artefact for distribution. The slimmer `smart-collections-manager-1.0.0.jar` expects JavaFX modules on the module-path.

## Key capabilities
- Recursive folder import with duplicate protection
- Keyword and tag search backed by hash maps and priority queues
- Detail editing with undo support and recently viewed navigation
- Integrated preview for documents, spreadsheets, images, audio, and video
- Binary persistence with version header and automatic backup rollover

## Core modules
```
src/main/java/com/smartcollections/
├── SmartCollectionsApp.java      • JavaFX entry point and scene graph wiring
├── model/                        • Serializable domain records (Item, Task, Category, Memento)
├── service/                      • Library, import, and persistence services
└── util/                         • Animation helpers

src/main/resources/
├── styles.css                    • Light theme
└── styles-dark.css               • Dark theme
```

## Data structures and algorithms
- `ArrayList` for cache-friendly item storage
- `HashSet` for fast duplicate path detection
- `HashMap` for keyword and tag indices
- `ArrayDeque` for undo stack and recently viewed navigation
- `PriorityQueue` for task ordering by urgency

Recursive import uses `Files.walkFileTree` with a custom visitor. Undo support relies on the memento pattern with typed operation records.

## Persistence
`PersistenceService` serialises a versioned wrapper that stores items and tasks, with backups kept under `~/.smartcollections/`. All streams use buffered wrappers and try-with-resources for safety.

## Testing and validation
- `mvn clean package` (passes)
- Manual smoke test: launch via `mvn javafx:run`, import a sample directory, edit an item, perform undo, confirm media preview for common file types.

## Submission checklist
- Source compiles with Maven
- Shaded jar present in `target/`
- No Lab 2 artefacts in `src/main/java`
- README reflects current structure and build process

