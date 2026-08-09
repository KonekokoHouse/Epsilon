package com.github.epsilon.utils.client;

import com.github.epsilon.Constants;
import com.github.epsilon.holders.ConfigHolder;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** 按 Epsilon 字体目录和当前操作系统字体目录解析自定义字体。 */
public final class FontPathResolver {

    private FontPathResolver() {
    }

    public static Path resolve(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Custom font is blank");
        }

        Path configured;
        try {
            configured = Path.of(value.trim());
        } catch (InvalidPathException invalidPath) {
            throw new IllegalArgumentException("Invalid custom font path: " + value, invalidPath);
        }

        if (configured.isAbsolute()) {
            if (Files.isRegularFile(configured)) return configured.normalize();
            throw new IllegalArgumentException("Custom font file does not exist: " + configured);
        }

        List<Path> roots = searchRoots();
        for (Path root : roots) {
            Path candidate = root.resolve(configured).normalize();
            if (candidate.startsWith(root) && Files.isRegularFile(candidate)) return candidate;
        }

        List<IOException> searchFailures = new ArrayList<>();
        if (configured.getNameCount() == 1) {
            String fileName = configured.getFileName().toString();
            for (Path root : roots) {
                Path match = findByFileName(root, fileName, searchFailures);
                if (match != null) return match;
            }
        }

        IllegalArgumentException missing = new IllegalArgumentException(
                "Custom font '" + value + "' was not found in " + roots);
        searchFailures.forEach(missing::addSuppressed);
        throw missing;
    }

    private static Path findByFileName(Path root, String fileName, List<IOException> failures) {
        if (!Files.isDirectory(root)) return null;
        try (Stream<Path> paths = Files.find(root, Integer.MAX_VALUE,
                (path, attributes) -> attributes.isRegularFile()
                        && path.getFileName().toString().equalsIgnoreCase(fileName))) {
            return paths.min(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER)).orElse(null);
        } catch (IOException failure) {
            recordSearchFailure(root, failure, failures);
        } catch (UncheckedIOException failure) {
            recordSearchFailure(root, failure.getCause(), failures);
        }
        return null;
    }

    private static void recordSearchFailure(Path root, IOException failure, List<IOException> failures) {
        IOException contextual = new IOException("Failed to search font directory: " + root, failure);
        failures.add(contextual);
        Constants.LOGGER.warn("Failed to search font directory '{}'", root, failure);
    }

    private static List<Path> searchRoots() {
        List<Path> roots = new ArrayList<>();
        Path userHome = Path.of(System.getProperty("user.home"));
        addRoot(roots, ConfigHolder.INSTANCE.getConfigDir().resolve("fonts"));

        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (osName.contains("win")) {
            Path localAppData = environmentPath("LOCALAPPDATA");
            if (localAppData != null) addRoot(roots, localAppData.resolve("Microsoft/Windows/Fonts"));
            Path windows = environmentPath("WINDIR");
            if (windows == null) windows = environmentPath("SystemRoot");
            if (windows != null) addRoot(roots, windows.resolve("Fonts"));
        } else if (osName.contains("mac")) {
            addRoot(roots, userHome.resolve("Library/Fonts"));
            addRoot(roots, Path.of("/Library/Fonts"));
            addRoot(roots, Path.of("/System/Library/Fonts"));
            addRoot(roots, Path.of("/Network/Library/Fonts"));
        } else {
            Path xdgDataHome = environmentPath("XDG_DATA_HOME");
            if (xdgDataHome != null) addRoot(roots, xdgDataHome.resolve("fonts"));
            addRoot(roots, userHome.resolve(".local/share/fonts"));
            addRoot(roots, userHome.resolve(".fonts"));
            addRoot(roots, Path.of("/usr/local/share/fonts"));
            addRoot(roots, Path.of("/usr/share/fonts"));
            addXdgDataRoots(roots);
        }
        return List.copyOf(roots);
    }

    private static void addXdgDataRoots(List<Path> roots) {
        String value = System.getenv("XDG_DATA_DIRS");
        if (value == null || value.isBlank()) return;
        for (String entry : value.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            if (!entry.isBlank()) addRoot(roots, Path.of(entry).resolve("fonts"));
        }
    }

    private static Path environmentPath(String name) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? null : Path.of(value);
    }

    private static void addRoot(List<Path> roots, Path root) {
        Path normalized = root.toAbsolutePath().normalize();
        if (!roots.contains(normalized)) roots.add(normalized);
    }
}
