package ics.util;

import java.io.*;
import java.nio.file.*;

public final class FileHelper {
    public static final String FOLDER_NAME = "ICS-data";
    private static final String[] FILE_NAMES = {"users.txt", "policies.txt", "claims.txt", "payouts.txt"};

    private FileHelper() {
    }

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
                value = java.math.BigDecimal.valueOf((Double) fields[i]).toPlainString();
                if (!value.contains(".")) {
                    value = value + ".0";
                }
            } else {
                value = fields[i].toString();
            }
            line.append(value.replace("\\", "\\\\").replace("|", "\\p").replace("\r", "").replace("\n", "\\n"));
        }
        return line.toString();
    }

    public static String[] split(String line) {
        String[] parts = line.split("\\|", -1);
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
                    out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public static Path dataFolder() {
        String override = System.getProperty("ics.data");
        if (override != null && !override.isBlank()) {
            return Path.of(override);
        }
        return Path.of(System.getProperty("user.home"), FOLDER_NAME);
    }

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
