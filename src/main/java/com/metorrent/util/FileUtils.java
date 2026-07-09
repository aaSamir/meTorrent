package com.metorrent.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/**
 * File-related helpers: human-readable sizes, extension/category detection
 * and safe destination-path handling for incoming downloads.
 */
public final class FileUtils {

    private static final String[] SIZE_UNITS = {"B", "KB", "MB", "GB", "TB"};

    private static final Set<String> DOCUMENT_EXT = Set.of(
            "pdf", "doc", "docx", "txt", "odt", "rtf", "xls", "xlsx", "ppt", "pptx", "md");
    private static final Set<String> IMAGE_EXT = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "svg", "webp");
    private static final Set<String> AUDIO_EXT = Set.of(
            "mp3", "wav", "flac", "aac", "ogg", "m4a");
    private static final Set<String> VIDEO_EXT = Set.of(
            "mp4", "mkv", "avi", "mov", "wmv", "webm");
    private static final Set<String> ARCHIVE_EXT = Set.of(
            "zip", "rar", "7z", "tar", "gz", "iso");

    private FileUtils() {
    }

    public static String humanReadableSize(long bytes) {
        if (bytes < 0) {
            return "0 B";
        }
        if (bytes < 1024) {
            return bytes + " B";
        }
        double size = bytes;
        int unitIndex = 0;
        while (size >= 1024 && unitIndex < SIZE_UNITS.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        return String.format(Locale.US, "%.1f %s", size, SIZE_UNITS[unitIndex]);
    }

    public static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** Coarse file-type category, used for the "Type" column in the shared-files view. */
    public static String categoryOf(String filename) {
        String ext = extensionOf(filename);
        if (DOCUMENT_EXT.contains(ext)) return "Document";
        if (IMAGE_EXT.contains(ext)) return "Image";
        if (AUDIO_EXT.contains(ext)) return "Audio";
        if (VIDEO_EXT.contains(ext)) return "Video";
        if (ARCHIVE_EXT.contains(ext)) return "Archive";
        return ext.isEmpty() ? "File" : ext.toUpperCase(Locale.ROOT);
    }

    /**
     * Strips path separators and traversal sequences from a filename received
     * from a remote peer, so it can never be used to escape the download
     * directory (e.g. "../../etc/passwd").
     */
    public static String sanitizeFileName(String filename) {
        // Do not route untrusted input through Path.of(): a peer-supplied name
        // containing characters like ':' throws InvalidPathException on Windows.
        String normalized = filename.replace('\\', '/');
        int lastSlash = normalized.lastIndexOf('/');
        String base = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
        String cleaned = base.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        if (cleaned.isEmpty() || cleaned.equals(".") || cleaned.equals("..")) {
            return "unnamed_file";
        }
        return cleaned;
    }

    /** Ensures the directory exists, creating parent directories as needed. */
    public static void ensureDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        } else if (!Files.isDirectory(dir)) {
            throw new IOException("Not a directory: " + dir);
        }
    }

    /**
     * Returns a destination path guaranteed not to collide with an existing
     * file, appending " (1)", " (2)", etc. before the extension as needed.
     */
    public static Path uniqueDestination(Path dir, String filename) {
        String safeName = sanitizeFileName(filename);
        Path candidate = dir.resolve(safeName);
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String ext = extensionOf(safeName);
        String base = ext.isEmpty() ? safeName : safeName.substring(0, safeName.length() - ext.length() - 1);

        int counter = 1;
        Path deduped;
        do {
            String newName = ext.isEmpty()
                    ? String.format("%s (%d)", base, counter)
                    : String.format("%s (%d).%s", base, counter, ext);
            deduped = dir.resolve(newName);
            counter++;
        } while (Files.exists(deduped));
        return deduped;
    }
}
