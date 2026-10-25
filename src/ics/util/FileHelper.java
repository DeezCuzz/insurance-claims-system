package ics.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
   <home folder>/ICS-data, however the program is started
 */
public final class FileHelper {

    public static final String FOLDER_NAME = "ICS-data";
    private static final String[] FILE_NAMES = {"users.txt", "policies.txt", "claims.txt", "payouts.txt"};

    private FileHelper() {
        // only static methods
    }

    // ------------------------------------------------------------------ 1. the line format

    /**
     * Joins the fields of one record into one line, for example  CLM-0001|POL-0002|2026-11-01|...
     * A real | or line break inside a value is written as \p or \n, so it cannot break the line apart.
     */
    public static String join(Object... fields) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) {
                line.append('|');
            }
            String value;
            if (fields[i] == null) {
                value = "";
            } else if (fields[i] instanceof Double) {
                // fix D3: always a plain number like 30000.0. Java would write 12 million as "1.2E7".
                value = java.math.BigDecimal.valueOf((Double) fields[i]).toPlainString();
                if (!value.contains(".")) {
                    value = value + ".0";                                // 12000000 -> 12000000.0, like the others
                }
            } else {
                value = fields[i].toString();
            }
            line.append(value.replace("\\", "\\\\").replace("|", "\\p").replace("\r", "").replace("\n", "\\n"));
        }
        return line.toString();
    }

    /** Splits one line back into its fields and undoes the \p and \n from join(). */
    public static String[] split(String line) {
        String[] parts = line.split("\\|", -1);          // -1 keeps empty fields at the end
        for (int i = 0; i < parts.length; i++) {
            parts[i] = unescape(parts[i]);
        }
        return parts;
    }

    private static String unescape(String s) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                if (next == 'p') {
                    out.append('|');
                } else if (next == 'n') {
                    out.append('\n');
                } else {
                    out.append(next);                     // "\\" becomes "\"
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ 2. where the files live

    /**
     * The data folder: <home folder>/ICS-data (for example C:\Users\Ceejay\ICS-data).
     * WHY a fixed place: if it depended on where the program was started, running it from NetBeans one day
     * and from a .jar the next could open an empty folder and "lose" every account.
     * Start Java with  -Dics.data=C:\some\folder  to use another folder (for example a demo copy).
     */
    public static Path dataFolder() {
        String override = System.getProperty("ics.data");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), FOLDER_NAME);
    }

    /**
     * Older versions kept the files in a folder called "data" next to where the program was started.
     * The first time this version runs, copy them into the new folder so nothing is lost.
     * Nothing is copied if the new folder already has accounts. Returns true if it copied.
     */
    public static boolean moveOldDataIfNeeded(Path newFolder) throws IOException {
        Path oldFolder = Path.of("data").toAbsolutePath();
        if (oldFolder.equals(newFolder.toAbsolutePath())) {
            return false;
        }
        if (!Files.exists(oldFolder.resolve("users.txt")) || Files.exists(newFolder.resolve("users.txt"))) {
            return false;
        }
        Files.createDirectories(newFolder);
        for (String name : FILE_NAMES) {
            Path from = oldFolder.resolve(name);
            if (Files.exists(from)) {
                Files.copy(from, newFolder.resolve(name), StandardCopyOption.COPY_ATTRIBUTES);
            }
        }
        return true;
    }
}
