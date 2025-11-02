# Smart Collections Manager

**ITEC627 Advanced Programming - Assignment 2**

Student: Syarifah Haninah

## Project Overview

Smart Collections Manager is a desktop JavaFX application for organising study notes, documents, and media. This project was developed as a requirement for ITEC627 Advanced Programming. It is designed to help a student manage a small library of items and demonstrates the practical application of advanced Java data structures, recursive algorithms, binary file persistence, and event-driven programming with JavaFX.

## Core Features

The application provides a comprehensive set of features for library management. Users can perform full **CRUD (Create, Read, Update, Delete)** operations on all items. A key feature is the **recursive folder import**, which scans nested directories for documents and media, using a `HashSet` to prevent duplicate file entries.

The system builds powerful search indices using a `HashMap` for $O(1)$ average time complexity, allowing for fast, case insensitive keyword searches. Search results are then ranked by relevance using a `PriorityQueue`. For usability, the application includes a multi level **undo system** (using an `ArrayDeque` as a stack) and a "Recently Viewed" history.

All library data is saved to disk using Java's **binary persistence** (`ObjectOutputStream`) with a custom versioned file header for integrity. The application also provides an **integrated media preview** for common file types (images, documents, audio, and video).

## Build and Run

### Build Prerequisites

- JDK 17 or later
- Maven 3.6 or later
- Internet access for Maven Central on the first build

### Quick Start (Developer Experience)

Use the cross-platform launcher scripts for the smoothest setup. By default the Linux/macOS launcher runs the app via `mvn javafx:run`, skipping the heavyweight runtime image build and providing faster feedback for development. A portable zip is still created when you perform a full package build.

**Linux/macOS**

```sh
./run.sh
```

* `./run.sh` first tries to reuse an existing self-contained runtime under `target/smart-collections-manager/`.
* If no runtime is found it runs `mvn javafx:run` instead, so you can keep working even when `jlink` cannot produce an image.
* Pass `--runtime` to force a runtime rebuild (`./run.sh --runtime`). If packaging fails, the script falls back to Maven automatically.

**Windows (PowerShell or Command Prompt)**

```sh
run.bat
```

* The Windows launcher will attempt to build and use the bundled runtime when missing and otherwise falls back to Maven.

### Manual Build Commands

You can also build and run the project manually using standard Maven commands.

```bash
# Compiles and assembles distribution artifacts (skips jlink by default)
mvn clean package

# Include a self-contained runtime image during the package build
mvn -Dskip.jlink=false clean package

# Creates or updates only the self-contained runtime image
mvn -Dskip.jlink=false javafx:jlink
```

### Development Mode

To run the application against your local JDK/Maven environment without building the full runtime:

```bash
mvn javafx:run
```

> ℹ️  The `skip.jlink` flag is enabled by default in `pom.xml` because several third-party libraries are automatic modules that cannot be linked into a custom runtime. Use the `-Dskip.jlink=false` switch when you explicitly need the bundled runtime and are prepared to resolve any resulting module conflicts.

## Architecture and Design

### Core Modules

The project follows a clear separation of concerns, dividing code into the following main packages:

- `src/main/java/com/smartcollections/`
  - **`SmartCollectionsApp.java`**: JavaFX entry point and scene graph wiring.
  - **`model/`**: Serializable domain records (Item, Task, Category, Memento).
  - **`service/`**: Contains the core logic for the Library, Import, and Persistence services.
  - **`util/`**: Animation helpers and the fallback audio player.
- `src/main/resources/`
  - **`styles.css`**: Light theme.
  - **`styles-dark.css`**: Dark theme.

### Data Structures and Algorithms

	* **`ArrayList`** is used for cache-friendly item storage and efficient index-based access for the `TableView`.
	* **`HashSet`** provides $O(1)$ average time complexity for detecting duplicate file paths during import.
	* **`HashMap`** is used to build the keyword and tag indices, allowing for $O(1)$ average time lookup.
	* **`ArrayDeque`** is used as a stack for the undo system and recently viewed navigation, as it is more performant than the legacy `Stack` class.
	* **`PriorityQueue`** orders the user's "Study Tasks" by urgency and is also used to rank search results by relevance.
	* **Recursion** is implemented using `Files.walkFileTree` with a custom file visitor for the folder import feature.

### Persistence

The `PersistenceService` serialises a versioned wrapper object that stores all items and tasks. This object is saved using `ObjectOutputStream` with buffered wrappers and `try-with-resources` for safety.

Backups are kept under `~/.smartcollections/`. The file header includes a magic number ("SMARTCOL") and a version number to ensure data integrity and prevent loading of corrupt or incompatible files.

## Troubleshooting

	* **MP3 Audio Playback**: The application ships with a built-in pure Java MP3 decoder (JLayer). If the JavaFX `MediaPlayer` fails to start (common on Linux without all codecs), the fallback player will automatically decode and play the audio. No system codecs are required.

	* **MP4 Video Playback**: JavaFX delegates video decoding to the operating system. If MP4 files fail to play, it is likely due to missing OS-level codecs.

			* On Debian/Ubuntu, installing the following packages usually resolves the issue:
				```sh
				sudo apt install gstreamer1.0-libav gstreamer1.0-plugins-good gstreamer1.0-plugins-bad
				```

## Submission Checklist

	* Source compiles with Maven (`mvn clean package`)
	* Shaded jar is present in `target/`
	* README reflects current structure and build process
	* Data structure justifications are present in source code comments

