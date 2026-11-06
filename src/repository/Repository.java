package ics.repository;

import ics.exception.*;
import ics.model.*;
import ics.util.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;

public class Repository<T extends Entity> {
    public interface LineReader<T> {
        T read(String[] fields) throws InvalidInputException;
    }

    private final ArrayList<T> items = new ArrayList<>();
    private final Path file;
    private final String prefix;
    private final LineReader<T> reader;
    private final List<String> warnings = new ArrayList<>();
    private int lastSearchSteps = 0;

    public Repository(Path file, String prefix, LineReader<T> reader) {
        this.file = file;
        this.prefix = prefix;
        this.reader = reader;
    }

    public void load() throws DataStorageException {
        warnings.clear();
        if (!Files.exists(file)) {
            items.clear();
            save();
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataStorageException("Cannot read " + file, e);
        }
        ArrayList<T> loaded = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            try {
                T item = reader.read(FileHelper.split(line));
                boolean duplicate = false;
                for (T earlier : loaded) {
                    if (earlier.getId().equals(item.getId())) {
                        duplicate = true;
                    }
                }
                if (duplicate) {
                    warnings.add(file.getFileName() + " line " + (i + 1) + ": the ID " + item.getId() + " appears twice");
                } else {
                    loaded.add(item);
                }
            } catch (InvalidInputException | RuntimeException e) {
                warnings.add(file.getFileName() + " line " + (i + 1) + ": " + e.getMessage());
            }
        }
        if (!warnings.isEmpty()) {
            try {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new DataStorageException("Cannot make a backup of " + file.getFileName(), e);
            }
        }

        Sorting.insertionSort(loaded, (a, b) -> compareIds(a.getId(), b.getId()));
        items.clear();
        items.addAll(loaded);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public void save() throws DataStorageException {
        try {
            Path folder = file.toAbsolutePath().getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            List<String> lines = new ArrayList<>();
            for (T item : items) {
                lines.add(item.toFileLine());
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.write(temp, lines, StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new DataStorageException("Cannot save " + file.getFileName(), e);
        }
    }

    public void add(T item) throws DataStorageException {
        ArrayList<T> before = new ArrayList<>(items);
        try {
            if (item.getId() == null || item.getId().isBlank()) {
                item.setId(nextId());
            } else if (findById(item.getId()) != null) {
                throw new DataStorageException("ID already exists: " + item.getId());
            }
            insertInOrder(item);
            save();
        } catch (DataStorageException e) {
            putBack(before);
            throw e;
        }
    }

    private void insertInOrder(T item) {
        int i = items.size();
        while (i > 0 && compareIds(items.get(i - 1).getId(), item.getId()) > 0) {
            i--;
        }
        items.add(i, item);
    }

    public void update(T item) throws DataStorageException {
        ArrayList<T> before = new ArrayList<>(items);
        try {
            int index = indexOf(item.getId());
            if (index < 0) {
                throw new DataStorageException("Record not found: " + item.getId());
            }
            items.set(index, item);
            save();
        } catch (DataStorageException e) {
            putBack(before);
            throw e;
        }
    }

    public void delete(String id) throws DataStorageException {
        ArrayList<T> before = new ArrayList<>(items);
        try {
            int index = indexOf(id);
            if (index < 0) {
                throw new DataStorageException("Record not found: " + id);
            }
            items.remove(index);
            save();
        } catch (DataStorageException e) {
            putBack(before);
            throw e;
        }
    }

    public T findById(String id) {
        int index = indexOf(id);
        return index < 0 ? null : items.get(index);
    }

    public List<T> getAll() {
        return new ArrayList<>(items);
    }

    public String nextId() {
        int highest = 0;
        for (int i = items.size() - 1; i >= 0; i--) {
            int number = numberOf(items.get(i).getId());
            if (number >= 0) {
                highest = number;
                break;
            }
        }
        return String.format(Locale.ROOT, "%s-%04d", prefix, highest + 1);
    }

    public int getLastSearchSteps() {
        return lastSearchSteps;
    }

    private int indexOf(String id) {
        lastSearchSteps = 0;
        if (id == null) {
            return -1;
        }
        int low = 0;
        int high = items.size() - 1;
        while (low <= high) {
            int mid = (low + high) / 2;
            int result = compareIds(items.get(mid).getId(), id);
            lastSearchSteps++;
            if (result == 0) {
                return mid;
            } else if (result < 0) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    private int numberOf(String id) {
        String start = prefix + "-";
        if (id == null || !id.startsWith(start)) {
            return -1;
        }
        try {
            return Integer.parseInt(id.substring(start.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private int compareIds(String a, String b) {
        int numberA = numberOf(a);
        int numberB = numberOf(b);
        if (numberA >= 0 && numberB >= 0) {
            return Integer.compare(numberA, numberB);
        }
        if (numberA >= 0) {
            return -1;
        }
        if (numberB >= 0) {
            return 1;
        }
        return a.compareTo(b);
    }

    private void putBack(ArrayList<T> before) {
        items.clear();
        items.addAll(before);
    }
}
