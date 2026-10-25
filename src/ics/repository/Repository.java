package ics.repository;

import ics.exception.DataStorageException;
import ics.exception.InvalidInputException;
import ics.model.Entity;
import ics.util.FileHelper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One kind of record kept in an ArrayList in memory and in one txt file on disk (rubric V.1: no database).
 *
 *   load():  file -> one line at a time -> one record each -> the ArrayList
 *   save():  the ArrayList -> each record's toFileLine() -> the file
 *
 * WHY "Repository<T>": the T means "any kind of record". The SAME code stores users, policies, claims and
 * payouts; the only thing that differs is how to read one line back, which each kind of record supplies
 * (see the LineReader in DataStore).
 */
public class Repository<T extends Entity> {

    /** How to turn the fields of one line back into a record. */
    public interface LineReader<T> {
        T read(String[] fields) throws InvalidInputException;
    }

    private final ArrayList<T> items = new ArrayList<>();
    private final Path file;
    private final String prefix;           // for the IDs, for example "CLM" gives CLM-0001
    private final LineReader<T> reader;
    private final List<String> warnings = new ArrayList<>();   // lines skipped by the last load() (fix D4)

    public Repository(Path file, String prefix, LineReader<T> reader) {
        this.file = file;
        this.prefix = prefix;
        this.reader = reader;
    }

    // ------------------------------------------------------------------ reading and writing the file

    /**
     * Reads the whole file into memory.
     *   fix D1: a missing file is created (empty), so all four files always exist
     *   fix D4: a broken line is SKIPPED with a warning instead of stopping the program. Before anything is
     *           saved over it, the original file is copied to  <name>.bak  so the broken line is not lost.
     */
    public void load() throws DataStorageException {
        warnings.clear();
        if (!Files.exists(file)) {
            items.clear();
            save();                                                     // creates the folder and an empty file
            return;
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DataStorageException("Cannot read " + file, e);   // the whole file cannot be read: stop
        }
        ArrayList<T> loaded = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                continue;                                               // blank lines and # comments are skipped
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
                // RuntimeException covers a missing column or a word where a number should be
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
        items.clear();
        items.addAll(loaded);
    }

    /** What the last load() skipped, one sentence per line (empty if nothing was wrong). */
    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    /**
     * Writes every record to the file.
     * WHY a temporary file first: if the computer dies while writing, the old file is still whole.
     */
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

    // ------------------------------------------------------------------ add, update, delete
    // Each one changes the list, then saves. If saving fails, the list is put back the way it was.

    /** Adds a record. A new record (no ID yet) gets the next auto-number, for example CLM-0003. */
    public void add(T item) throws DataStorageException {
        ArrayList<T> before = new ArrayList<>(items);
        try {
            if (item.getId() == null || item.getId().isBlank()) {
                item.setId(nextId());
            } else if (findById(item.getId()) != null) {
                throw new DataStorageException("ID already exists: " + item.getId());
            }
            items.add(item);
            save();
        } catch (DataStorageException e) {
            putBack(before);
            throw e;
        }
    }

    /** Replaces the record that has the same ID, then saves. */
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

    /** Removes the record with this ID, then saves. */
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

    // ------------------------------------------------------------------ finding

    /** The record with this ID, or null if there is none. */
    public T findById(String id) {
        int index = indexOf(id);
        return index < 0 ? null : items.get(index);
    }

    /** A COPY of the list, so a screen cannot change the stored list by accident. */
    public List<T> getAll() {
        return new ArrayList<>(items);
    }

    /**
     * The next auto-number: the highest number so far plus one (rubric II.A.6).
     * Known limit: if the newest record is deleted, its number is used again.
     */
    public String nextId() {
        String start = prefix + "-";
        int highest = 0;
        for (T item : items) {
            String id = item.getId();
            if (id != null && id.startsWith(start)) {
                try {
                    highest = Math.max(highest, Integer.parseInt(id.substring(start.length())));
                } catch (NumberFormatException ignored) {
                    // an ID that does not end in a number is skipped
                }
            }
        }
        return String.format(Locale.ROOT, "%s-%04d", prefix, highest + 1);   // %04d = 4 digits, for example 0007
    }

    private int indexOf(String id) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).getId().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private void putBack(ArrayList<T> before) {
        items.clear();
        items.addAll(before);
    }
}
