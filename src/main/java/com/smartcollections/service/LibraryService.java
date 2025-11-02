package com.smartcollections.service;

import java.io.Serializable;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Consumer;

import com.smartcollections.model.Item;
import com.smartcollections.model.Memento;
import com.smartcollections.model.Task;

public class LibraryService {
    private static final int RECENTLY_VIEWED_LIMIT = 20;

    // ArrayList keeps cache-friendly sequential reads for the table view and avoids pointer chasing vs LinkedList.
    private final List<Item> items = new ArrayList<>();
    private final Map<String, Item> itemsById = new HashMap<>();
    private final Map<String, Set<String>> keywordIndex = new HashMap<>();
    private final Map<String, Set<String>> itemKeywords = new HashMap<>();
    private final Map<String, Set<String>> itemTags = new HashMap<>();
    private final Map<String, Integer> tagFrequency = new HashMap<>();
    private final Set<String> uniquePaths = new HashSet<>();
    // ArrayDeque behaves as a stack without the synchronization overhead of java.util.Stack.
    private final Deque<Memento> undoStack = new ArrayDeque<>();
    private final Deque<Item> recentlyViewedStack = new ArrayDeque<>();
    private final PriorityQueue<Task> taskQueue = new PriorityQueue<>();
    private final Map<String, Task> tasksById = new HashMap<>();

    public boolean addItem(Item item) {
        return addItemInternal(item, true);
    }

    public boolean addItemSilently(Item item) {
        return addItemInternal(item, false);
    }

    private boolean addItemInternal(Item item, boolean recordUndo) {
        if (item == null) {
            return false;
        }
        String normalisedPath = normalisePath(item.getFilePath());
        if (normalisedPath != null && uniquePaths.contains(normalisedPath)) {
            return false;
        }

        items.add(item);
        itemsById.put(item.getId(), item);
        if (normalisedPath != null) {
            uniquePaths.add(normalisedPath);
        }
        indexItem(item);
        if (recordUndo) {
            undoStack.push(new Memento(item.copy(), Memento.OperationType.ADD, null));
        }
        return true;
    }

    public void editItem(Item item, Consumer<Item> editor) {
        if (item == null || editor == null) {
            return;
        }

        Memento beforeEdit = new Memento(item.copy());
        undoStack.push(beforeEdit);

        String previousPath = normalisePath(item.getFilePath());
        if (previousPath != null) {
            uniquePaths.remove(previousPath);
        }
        removeFromIndex(item);

        editor.accept(item);

        String updatedPath = normalisePath(item.getFilePath());
        if (updatedPath != null && !Objects.equals(updatedPath, previousPath) && uniquePaths.contains(updatedPath)) {
            item.restoreFromMemento(beforeEdit);
            indexItem(item);
            if (previousPath != null) {
                uniquePaths.add(previousPath);
            }
            undoStack.pop();
            throw new IllegalArgumentException("An item with this file path already exists.");
        }

        if (updatedPath != null) {
            uniquePaths.add(updatedPath);
        }
        indexItem(item);
    }

    public void deleteItem(Item item) {
        if (item == null) {
            return;
        }

        DeletedItemSnapshot snapshot = removeItemInternal(item, true);
        undoStack.push(new Memento(snapshot.item(), Memento.OperationType.DELETE, snapshot));
    }

    public List<Item> search(String query) {
        if (query == null || query.isBlank()) {
            return sortedItemsByTitle();
        }

        String trimmed = query.trim().toLowerCase(Locale.ROOT);
        String[] tokens = trimmed.split("\\s+");

        Map<String, ScoreAccumulator> accumulators = new HashMap<>();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            for (Map.Entry<String, Set<String>> entry : keywordIndex.entrySet()) {
                String keyword = entry.getKey();
                if (!keyword.contains(token)) {
                    continue;
                }
                boolean exact = keyword.equals(token);
                double frequencyWeight = tagFrequency.getOrDefault(keyword, entry.getValue().size());
                if (exact) {
                    frequencyWeight *= 1.5;
                }
                for (String itemId : entry.getValue()) {
                    Item item = itemsById.get(itemId);
                    if (item == null) {
                        continue;
                    }
                    accumulators
                        .computeIfAbsent(itemId, k -> new ScoreAccumulator())
                        .boostFrequency(frequencyWeight, exact);
                }
            }
        }

        for (Item item : items) {
            String lowerTitle = item.getTitle() != null ? item.getTitle().toLowerCase(Locale.ROOT) : "";
            if (lowerTitle.contains(trimmed)) {
                accumulators
                    .computeIfAbsent(item.getId(), k -> new ScoreAccumulator())
                    .boostTitle();
            }
        }

        Comparator<ItemScore> rankingComparator = (left, right) -> {
            int comparison = Double.compare(right.score(), left.score());
            if (comparison != 0) return comparison;
            comparison = Integer.compare(right.exactMatches(), left.exactMatches());
            if (comparison != 0) return comparison;
            comparison = Integer.compare(right.item().getRating(), left.item().getRating());
            if (comparison != 0) return comparison;
            return right.item().getCreatedAt().compareTo(left.item().getCreatedAt());
        };

        PriorityQueue<ItemScore> ranked = new PriorityQueue<>(rankingComparator);

        accumulators.forEach((itemId, accumulator) -> {
            Item item = itemsById.get(itemId);
            if (item != null) {
                ranked.offer(accumulator.toScore(item));
            }
        });

        List<Item> results = new ArrayList<>(ranked.size());
        while (!ranked.isEmpty()) {
            results.add(ranked.poll().item());
        }

        return results;
    }

    public void addTask(Task task) {
        if (task == null) {
            return;
        }
        taskQueue.offer(task);
        tasksById.put(task.getId(), task);
    }

    public void deleteTask(Task task) {
        if (task == null) {
            return;
        }
        undoStack.push(new Memento(task.copy(), Memento.OperationType.TASK_DELETE));
        taskQueue.remove(task);
        tasksById.remove(task.getId());
    }

    public Optional<Task> peekNextTask() {
        return Optional.ofNullable(taskQueue.peek());
    }

    public void markAsViewed(Item item) {
        if (item == null) {
            return;
        }
        recentlyViewedStack.remove(item);
        recentlyViewedStack.push(item);
        while (recentlyViewedStack.size() > RECENTLY_VIEWED_LIMIT) {
            recentlyViewedStack.removeLast();
        }
    }

    public List<Item> getRecentlyViewed() {
        List<Item> recent = new ArrayList<>(recentlyViewedStack);
        Collections.reverse(recent);
        return recent;
    }

    public Optional<Item> popRecentlyViewed() {
        return Optional.ofNullable(recentlyViewedStack.poll());
    }

    public Optional<Item> navigateBack(Item current) {
        if (current != null && !recentlyViewedStack.isEmpty() && recentlyViewedStack.peek().equals(current)) {
            recentlyViewedStack.pop();
        }
        Item previous = recentlyViewedStack.poll();
        return Optional.ofNullable(previous);
    }

    public boolean undo() {
        Memento memento = undoStack.poll();
        if (memento == null) {
            return false;
        }

        return switch (memento.getOperationType()) {
            case ADD -> {
                Item item = itemsById.get(memento.getItemId());
                if (item == null) {
                    yield false;
                }
                removeItemInternal(item, false);
                yield true;
            }
            case EDIT -> {
                Item item = itemsById.get(memento.getItemId());
                if (item == null) {
                    yield false;
                }
                removeFromIndex(item);
                String currentPath = normalisePath(item.getFilePath());
                if (currentPath != null) {
                    uniquePaths.remove(currentPath);
                }
                item.restoreFromMemento(memento);
                String restoredPath = normalisePath(item.getFilePath());
                if (restoredPath != null) {
                    uniquePaths.add(restoredPath);
                }
                indexItem(item);
                yield true;
            }
            case DELETE -> {
                Object payload = memento.getOperationData();
                if (payload instanceof DeletedItemSnapshot snapshot) {
                    Item restoredItem = snapshot.item().copy();
                    boolean added = addItemInternal(restoredItem, false);
                    if (added) {
                        for (Task task : snapshot.tasks()) {
                            addTask(task.copy());
                        }
                    }
                    yield added;
                }
                yield false;
            }
            case TASK_DELETE, TASK_EDIT -> {
                Object data = memento.getOperationData();
                if (data instanceof Task task) {
                    addTask(task.copy());
                    yield true;
                }
                if (data instanceof List<?> taskList) {
                    for (Object obj : taskList) {
                        if (obj instanceof Task t) {
                            addTask(t.copy());
                        }
                    }
                    yield true;
                }
                yield false;
            }
        };
    }

    public List<Item> getAllItems() {
        return new ArrayList<>(items);
    }

    public List<Task> getAllTasks() {
        List<Task> ordered = new ArrayList<>(taskQueue);
        ordered.sort(null);
        return ordered;
    }

    public Map<String, Integer> getTagFrequency() {
        return Collections.unmodifiableMap(tagFrequency);
    }

    public boolean hasUndo() {
        return !undoStack.isEmpty();
    }

    public int getItemCount() {
        return items.size();
    }

    public void clear() {
        items.clear();
        itemsById.clear();
        keywordIndex.clear();
        itemKeywords.clear();
        itemTags.clear();
        tagFrequency.clear();
        uniquePaths.clear();
        undoStack.clear();
        recentlyViewedStack.clear();
        taskQueue.clear();
        tasksById.clear();
    }

    public LibraryState createSnapshot() {
        List<String> recentOrder = new ArrayList<>(recentlyViewedStack.size());
        for (Item item : recentlyViewedStack) {
            recentOrder.add(item.getId());
        }

        return new LibraryState(
            copyItems(items),
            copyMapOfSets(keywordIndex),
            copyMapOfSets(itemKeywords),
            copyMapOfSets(itemTags),
            new HashMap<>(tagFrequency),
            new HashSet<>(uniquePaths),
            recentOrder,
            new ArrayList<>(undoStack),
            copyTasks(tasksById.values())
        );
    }

    public void restoreSnapshot(LibraryState state) {
        clear();
        if (state == null) {
            return;
        }

        state.items().forEach(item -> {
            Item copy = item.copy();
            items.add(copy);
            itemsById.put(copy.getId(), copy);
        });
        keywordIndex.putAll(copyMapOfSets(state.keywordIndex()));
        itemKeywords.putAll(copyMapOfSets(state.itemKeywords()));
        itemTags.putAll(copyMapOfSets(state.itemTags()));
        tagFrequency.putAll(state.tagFrequency());
        uniquePaths.addAll(state.uniquePaths());

        state.tasks().forEach(task -> {
            Task copy = task.copy();
            taskQueue.offer(copy);
            tasksById.put(copy.getId(), copy);
        });

        Deque<Memento> restoredUndo = new ArrayDeque<>(state.undoHistory());
        undoStack.addAll(restoredUndo);

    List<String> recentIds = new ArrayList<>(state.recentlyViewedOrder());
    Collections.reverse(recentIds);
    for (String id : recentIds) {
            Item item = itemsById.get(id);
            if (item != null) {
                recentlyViewedStack.push(item);
            }
        }
    }

    private void indexItem(Item item) {
        Set<String> keywordTokens = new HashSet<>(item.keywordTokensForSearch());
        itemKeywords.put(item.getId(), keywordTokens);
        keywordTokens.forEach(token ->
            keywordIndex.computeIfAbsent(token, t -> new HashSet<>()).add(item.getId())
        );

        Set<String> tags = new HashSet<>(item.getTags());
        itemTags.put(item.getId(), tags);
        tags.forEach(tag -> tagFrequency.merge(tag, 1, Integer::sum));
    }

    private DeletedItemSnapshot removeItemInternal(Item item, boolean captureTasks) {
        if (item == null) {
            return new DeletedItemSnapshot(null, List.of());
        }
        Item itemCopy = item.copy();
        List<Task> orphanedTasks = new ArrayList<>();
        taskQueue.removeIf(task -> {
            if (item.getId().equals(task.getItemId())) {
                tasksById.remove(task.getId());
                if (captureTasks) {
                    orphanedTasks.add(task.copy());
                }
                return true;
            }
            return false;
        });

        recentlyViewedStack.remove(item);
        items.remove(item);
        itemsById.remove(item.getId());
        String normalisedPath = normalisePath(item.getFilePath());
        if (normalisedPath != null) {
            uniquePaths.remove(normalisedPath);
        }
        removeFromIndex(item);
        return new DeletedItemSnapshot(itemCopy, orphanedTasks);
    }

    private void removeFromIndex(Item item) {
        Set<String> keywords = itemKeywords.remove(item.getId());
        if (keywords != null) {
            for (String keyword : keywords) {
                Set<String> ids = keywordIndex.get(keyword);
                if (ids != null) {
                    ids.remove(item.getId());
                    if (ids.isEmpty()) {
                        keywordIndex.remove(keyword);
                    }
                }
            }
        }

        Set<String> tags = itemTags.remove(item.getId());
        if (tags != null) {
            for (String tag : tags) {
                tagFrequency.computeIfPresent(tag, (key, count) -> count > 1 ? count - 1 : null);
            }
        }
    }

    private String normalisePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Path normalised = Paths.get(path).toAbsolutePath().normalize();
            return normalised.toString().toLowerCase(Locale.ROOT);
        } catch (InvalidPathException ex) {
            return path.trim().toLowerCase(Locale.ROOT);
        }
    }

    private List<Item> sortedItemsByTitle() {
        List<Item> copy = new ArrayList<>(items);
        copy.sort(Comparator.comparing(Item::getTitle, String.CASE_INSENSITIVE_ORDER));
        return copy;
    }

    private static List<Item> copyItems(List<Item> source) {
        List<Item> copies = new ArrayList<>(source.size());
        for (Item item : source) {
            copies.add(item.copy());
        }
        return copies;
    }

    private static List<Task> copyTasks(Iterable<Task> source) {
        List<Task> copies = new ArrayList<>();
        for (Task task : source) {
            copies.add(task.copy());
        }
        return copies;
    }

    private static Map<String, Set<String>> copyMapOfSets(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new HashMap<>();
        source.forEach((key, value) -> copy.put(key, new HashSet<>(value)));
        return copy;
    }

    private static final class ScoreAccumulator {
        private double frequencyScore;
        private int exactMatches;
        private boolean titleMatch;

        void boostFrequency(double weight, boolean exactMatch) {
            frequencyScore += weight;
            if (exactMatch) {
                exactMatches++;
            }
        }

        void boostTitle() {
            titleMatch = true;
            frequencyScore += 3;
        }

        ItemScore toScore(Item item) {
            double ratingBonus = item.getRating() * 0.5;
            double titleBonus = titleMatch ? 5 : 0;
            double score = frequencyScore + ratingBonus + titleBonus;
            return new ItemScore(item, score, exactMatches);
        }
    }

    private record ItemScore(Item item, double score, int exactMatches) {
    }

    private record DeletedItemSnapshot(Item item, List<Task> tasks) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public static class LibraryState implements Serializable {
        private static final long serialVersionUID = 2L;
        private final List<Item> items;
        private final Map<String, Set<String>> keywordIndex;
        private final Map<String, Set<String>> itemKeywords;
        private final Map<String, Set<String>> itemTags;
        private final Map<String, Integer> tagFrequency;
        private final Set<String> uniquePaths;
        private final List<String> recentlyViewedOrder;
        private final List<Memento> undoHistory;
        private final List<Task> tasks;

        public LibraryState(List<Item> items,
                            Map<String, Set<String>> keywordIndex,
                            Map<String, Set<String>> itemKeywords,
                            Map<String, Set<String>> itemTags,
                            Map<String, Integer> tagFrequency,
                            Set<String> uniquePaths,
                            List<String> recentlyViewedOrder,
                            List<Memento> undoHistory,
                            List<Task> tasks) {
            this.items = items;
            this.keywordIndex = keywordIndex;
            this.itemKeywords = itemKeywords;
            this.itemTags = itemTags;
            this.tagFrequency = tagFrequency;
            this.uniquePaths = uniquePaths;
            this.recentlyViewedOrder = recentlyViewedOrder;
            this.undoHistory = undoHistory;
            this.tasks = tasks;
        }

        public List<Item> items() {
            return items;
        }

        public Map<String, Set<String>> keywordIndex() {
            return keywordIndex;
        }

        public Map<String, Set<String>> itemKeywords() {
            return itemKeywords;
        }

        public Map<String, Set<String>> itemTags() {
            return itemTags;
        }

        public Map<String, Integer> tagFrequency() {
            return tagFrequency;
        }

        public Set<String> uniquePaths() {
            return uniquePaths;
        }

        public List<String> recentlyViewedOrder() {
            return recentlyViewedOrder;
        }

        public List<Memento> undoHistory() {
            return undoHistory;
        }

        public List<Task> tasks() {
            return tasks;
        }
    }
}
