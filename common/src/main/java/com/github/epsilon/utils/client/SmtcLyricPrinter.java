package com.github.epsilon.utils.client;

import tech.AirFoundation.yux1n9.MurasameSmtcBootstrap;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class SmtcLyricPrinter {

    private final MurasameSmtcBootstrap smtcBootstrap;
    private final AmllTtmlDbLyricService amllLyricService = new AmllTtmlDbLyricService();
    private final NetEaseLyricService netEaseLyricService = new NetEaseLyricService();

    private volatile String lastPrintedTrackKey = "";
    private volatile boolean lookupInFlight;

    public SmtcLyricPrinter(MurasameSmtcBootstrap smtcBootstrap) {
        this.smtcBootstrap = smtcBootstrap;
    }

    public void tick() {
        MurasameSmtcBootstrap.MediaInfo mediaInfo = smtcBootstrap.getCurrentMediaInfo();
        if (mediaInfo == null) {
            return;
        }

        String title = safe(mediaInfo.title());
        if (title.isBlank() || MurasameSmtcBootstrap.NO_MEDIA_TEXT.equalsIgnoreCase(title)) {
            return;
        }

        String trackKey = (title + "|" + safe(mediaInfo.artist())).trim();
        if (lookupInFlight || trackKey.equals(lastPrintedTrackKey)) {
            return;
        }

        SmtcLyricTimeline.clear(trackKey);
        lookupInFlight = true;
        lastPrintedTrackKey = trackKey;

        CompletableFuture.runAsync(() -> lookupAndPrint(title, safe(mediaInfo.artist())));
    }

    private void lookupAndPrint(String title, String artist) {
        String trackKey = (title + "|" + artist).trim();
        try {
            NetEaseLyricService.DebugLookupResult debugResult = netEaseLyricService.resolveLyricsWithDebug(title, artist);
            NetEaseLyricService.LyricLookupResult result = debugResult.result();
            if (result == null) {
                SmtcLyricTimeline.clear(trackKey);
                return;
            }

            java.util.List<SmtcLyricTimeline.LyricLine> amllLines = amllLyricService.resolveByNeteaseId(result.songId());
            if (!amllLines.isEmpty()) {
                SmtcLyricTimeline.updateLines(trackKey, result.songId(), amllLines);
                return;
            }

            amllLines = amllLyricService.resolveByRoughTitle(title);
            if (!amllLines.isEmpty()) {
                SmtcLyricTimeline.updateLines(trackKey, result.songId(), amllLines);
                return;
            }

            SmtcLyricTimeline.update(trackKey, result.songId(), result.lyric(), result.translatedLyric());
        } catch (IOException | InterruptedException e) {
            SmtcLyricTimeline.clear(trackKey);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        } finally {
            lookupInFlight = false;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

}
