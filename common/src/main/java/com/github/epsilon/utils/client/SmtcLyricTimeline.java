package com.github.epsilon.utils.client;

import tech.AirFoundation.yux1n9.MurasameSmtcBootstrap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmtcLyricTimeline {

    private static final Pattern TIMESTAMP_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]");
    private static final double LEAD_SECONDS = 1.3d;
    private static final double SAME_TIMESTAMP_TOLERANCE = 0.08d;

    private static volatile String trackKey = "";
    private static volatile String songId = "";
    private static volatile List<LyricLine> lines = List.of();
    private static volatile String lastSmoothTrackKey = "";
    private static volatile double lastSmoothLineTime = -1d;
    private static volatile int lastSmoothWordIndex = -1;
    private static volatile double lastSmoothedWordSeconds = 0d;
    private static volatile long lastSmoothUpdateMillis = 0L;
    private static volatile float lastSmoothedWordProgress = 0f;

    private SmtcLyricTimeline() {
    }

    public static synchronized void update(String newTrackKey, String newSongId, String rawLyric, String rawTranslatedLyric) {
        trackKey = newTrackKey == null ? "" : newTrackKey;
        songId = newSongId == null ? "" : newSongId;
        lines = merge(parse(rawLyric), parse(rawTranslatedLyric));
    }

    public static synchronized void clear(String newTrackKey) {
        trackKey = newTrackKey == null ? "" : newTrackKey;
        songId = "";
        lines = List.of();
        resetWordProgressSmoothing();
    }

    public static synchronized void updateLines(String newTrackKey, String newSongId, List<LyricLine> newLines) {
        trackKey = newTrackKey == null ? "" : newTrackKey;
        songId = newSongId == null ? "" : newSongId;
        lines = newLines == null ? List.of() : List.copyOf(newLines);
        resetWordProgressSmoothing();
    }

    public static LyricSnapshot snapshot(double seconds) {
        List<LyricLine> currentLines = lines;
        if (currentLines.isEmpty()) {
            return LyricSnapshot.EMPTY;
        }

        double rawSeconds = Math.max(0d, seconds);
        boolean hasWordTiming = currentLines.stream().anyMatch(line -> !line.wordChunks().isEmpty());
        double lineLeadSeconds = hasWordTiming ? 0d : LEAD_SECONDS;
        double adjustedSeconds = Math.max(0d, rawSeconds + lineLeadSeconds);
        int index = -1;
        for (int i = 0; i < currentLines.size(); i++) {
            if (adjustedSeconds >= currentLines.get(i).timeSeconds()) {
                index = i;
            } else {
                break;
            }
        }

        if (index < 0) {
            LyricLine first = currentLines.get(0);
            return new LyricSnapshot(
                    "",
                    sanitize(first.originalText()),
                    sanitize(first.translatedText()),
                    0f,
                    trackKey,
                    songId,
                    first,
                    List.of(),
                    0,
                    0f
            );
        }

        LyricLine current = currentLines.get(index);
        String next = index + 1 < currentLines.size() ? sanitize(currentLines.get(index + 1).originalText()) : "";

        float progress = 0f;
        if (index + 1 < currentLines.size()) {
            double start = current.timeSeconds();
            double end = currentLines.get(index + 1).timeSeconds();
            if (end > start) {
                progress = (float) Math.max(0d, Math.min(1d, (adjustedSeconds - start) / (end - start)));
            }
        }

        int activeWordIndex = -1;
        float activeWordProgress = 0f;
        double activeWordDuration = 0d;
        double activeWordElapsedSeconds = 0d;
        List<WordChunk> wordChunks = current.wordChunks();
        if (!wordChunks.isEmpty()) {
            for (int i = 0; i < wordChunks.size(); i++) {
                WordChunk chunk = wordChunks.get(i);
                if (rawSeconds >= chunk.beginSeconds()) {
                    activeWordIndex = i;
                    double chunkDuration = Math.max(0.001d, chunk.endSeconds() - chunk.beginSeconds());
                    activeWordDuration = chunkDuration;
                    activeWordElapsedSeconds = Math.max(0d, Math.min(chunkDuration, rawSeconds - chunk.beginSeconds()));
                    activeWordProgress = (float) Math.max(0d, Math.min(1d, activeWordElapsedSeconds / chunkDuration));
                    if (rawSeconds <= chunk.endSeconds()) {
                        break;
                    }
                    activeWordElapsedSeconds = chunkDuration;
                    activeWordProgress = 1f;
                } else {
                    break;
                }
            }
        }

        activeWordProgress = smoothActiveWordProgress(
                current.timeSeconds(),
                activeWordIndex,
                activeWordProgress,
                activeWordDuration,
                activeWordElapsedSeconds
        );

        return new LyricSnapshot(
                sanitize(current.originalText()),
                sanitize(current.translatedText()),
                next,
                progress,
                trackKey,
                songId,
                current,
                wordChunks,
                activeWordIndex,
                activeWordProgress
        );
    }

    private static List<ParsedLine> parse(String rawLyric) {
        if (rawLyric == null || rawLyric.isBlank()) {
            return List.of();
        }

        List<ParsedLine> parsed = new ArrayList<>();
        for (String rawLine : rawLyric.split("\\R")) {
            Matcher matcher = TIMESTAMP_PATTERN.matcher(rawLine);
            List<Double> stamps = new ArrayList<>();
            int lastEnd = 0;
            while (matcher.find()) {
                stamps.add(toSeconds(matcher.group(1), matcher.group(2), matcher.group(3)));
                lastEnd = matcher.end();
            }

            if (stamps.isEmpty()) {
                continue;
            }

            String text = sanitize(rawLine.substring(Math.min(lastEnd, rawLine.length())));
            if (text.isEmpty()) {
                continue;
            }

            for (double stamp : stamps) {
                parsed.add(new ParsedLine(stamp, text));
            }
        }

        parsed.sort(Comparator.comparingDouble(ParsedLine::timeSeconds));
        return List.copyOf(parsed);
    }

    private static List<LyricLine> merge(List<ParsedLine> original, List<ParsedLine> translated) {
        if (original.isEmpty()) {
            return List.of();
        }

        List<LyricLine> merged = new ArrayList<>(original.size());
        int translatedIndex = 0;
        for (ParsedLine source : original) {
            while (translatedIndex < translated.size() - 1
                    && translated.get(translatedIndex).timeSeconds() + SAME_TIMESTAMP_TOLERANCE < source.timeSeconds()) {
                translatedIndex++;
            }

            String translatedText = "";
            if (translatedIndex < translated.size()) {
                ParsedLine translatedLine = translated.get(translatedIndex);
                if (Math.abs(translatedLine.timeSeconds() - source.timeSeconds()) <= SAME_TIMESTAMP_TOLERANCE
                        && !sanitize(translatedLine.text()).equals(sanitize(source.text()))) {
                    translatedText = translatedLine.text();
                }
            }

            merged.add(new LyricLine(source.timeSeconds(), source.text(), translatedText, List.of()));
        }

        return List.copyOf(merged);
    }

    private static double toSeconds(String minutes, String seconds, String fraction) {
        int minute = Integer.parseInt(minutes);
        int second = Integer.parseInt(seconds);
        int millis = 0;
        if (fraction != null && !fraction.isBlank()) {
            if (fraction.length() == 1) {
                millis = Integer.parseInt(fraction) * 100;
            } else if (fraction.length() == 2) {
                millis = Integer.parseInt(fraction) * 10;
            } else {
                millis = Integer.parseInt(fraction.substring(0, 3));
            }
        }
        return minute * 60d + second + millis / 1000d;
    }

    private static String sanitize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\uFEFF", "").trim();
    }

    private static float smoothActiveWordProgress(double lineTimeSeconds,
                                                  int activeWordIndex,
                                                  float rawProgress,
                                                  double chunkDurationSeconds,
                                                  double rawWordElapsedSeconds) {
        long now = System.currentTimeMillis();
        boolean sameTrack = trackKey.equals(lastSmoothTrackKey);
        boolean sameLine = Math.abs(lineTimeSeconds - lastSmoothLineTime) <= 0.0001d;
        boolean sameWord = activeWordIndex == lastSmoothWordIndex;
        boolean canAdvance = activeWordIndex >= 0
                && chunkDurationSeconds > 0d
                && sameTrack
                && sameLine
                && sameWord
                && MurasameSmtcBootstrap.stateText != null
                && MurasameSmtcBootstrap.stateText.equalsIgnoreCase("Playing");

        double smoothedWordSeconds = rawWordElapsedSeconds;
        if (canAdvance) {
            long elapsedMillis = Math.max(0L, now - lastSmoothUpdateMillis);
            double elapsedSeconds = elapsedMillis / 1000d;
            double predictedWordSeconds = Math.min(chunkDurationSeconds, lastSmoothedWordSeconds + elapsedSeconds);
            smoothedWordSeconds = Math.max(rawWordElapsedSeconds, predictedWordSeconds);
        }

        float smoothed = chunkDurationSeconds <= 0d
                ? rawProgress
                : (float) Math.max(0d, Math.min(1d, smoothedWordSeconds / chunkDurationSeconds));

        lastSmoothTrackKey = trackKey;
        lastSmoothLineTime = lineTimeSeconds;
        lastSmoothWordIndex = activeWordIndex;
        lastSmoothedWordSeconds = Math.max(0d, smoothedWordSeconds);
        lastSmoothUpdateMillis = now;
        lastSmoothedWordProgress = Math.max(0f, Math.min(1f, smoothed));
        return lastSmoothedWordProgress;
    }

    private static void resetWordProgressSmoothing() {
        lastSmoothTrackKey = "";
        lastSmoothLineTime = -1d;
        lastSmoothWordIndex = -1;
        lastSmoothedWordSeconds = 0d;
        lastSmoothUpdateMillis = 0L;
        lastSmoothedWordProgress = 0f;
    }

    public record ParsedLine(double timeSeconds, String text) {
    }

    public record LyricLine(double timeSeconds, String originalText, String translatedText, List<WordChunk> wordChunks) {
        public LyricLine {
            wordChunks = wordChunks == null ? List.of() : List.copyOf(wordChunks);
        }
    }

    public record WordChunk(String text, double beginSeconds, double endSeconds) {
    }

    public record LyricSnapshot(
            String currentLine,
            String currentTranslatedLine,
            String nextLine,
            float progress,
            String trackKey,
            String songId,
            LyricLine currentLyricLine,
            List<WordChunk> currentWordChunks,
            int activeWordIndex,
            float activeWordProgress
    ) {
        public LyricSnapshot {
            currentWordChunks = currentWordChunks == null ? List.of() : List.copyOf(currentWordChunks);
        }

        public boolean hasWordTiming() {
            return !currentWordChunks.isEmpty();
        }

        public static final LyricSnapshot EMPTY = new LyricSnapshot("", "", "", 0f, "", "", null, List.of(), -1, 0f);
    }

}
