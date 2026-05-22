package tech.AirFoundation.yux1n9;

import com.github.epsilon.utils.client.ClientUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.NotNull;

public class MurasameSmtcBootstrap {

    private static final String DEFAULT_ARTIST_TEXT = "Waiting Information...";
    public static final String NO_MEDIA_TEXT = "No playing";
    private static final String[] BLOCKED_KEYWORDS = {"抖音", "快手", "bilibili", "哔哩哔哩"};

    public static String titleText = "";
    public static String lastTitle = null;
    public static String artistText = "";
    public static String lastArtist = null;
    public static String totalTimeText = "";
    public static String lastTotalTime = null;
    public static String passTimeText = "";
    public static String lastBase64 = "";
    public static String base64 = "";
    public static String stateText = "";
    public static String sourceApp = "";
    public static float progress = 0;
    public static boolean changed = false;

    public static double currentPositionSeconds;

    public Thread musicInfoThread;
    public MediaInfo currentMediaInfo = new MediaInfo("", "", "", "", 0, "", State.Unknown, false, "");

    public native String getMediaInfo();

    static {
        ClientUtils.loadNativeLibrary("/assets/epsilon/native/smtc_native.dll", "smtc_native");
    }

    public MurasameSmtcBootstrap() {
        startMusicInfoThread();
    }

    private void startMusicInfoThread() {
        System.out.println("SMTC Init.");
        musicInfoThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                updateMediaInfo();
                try {
                    Thread.sleep(1000 / 30);
                } catch (InterruptedException e) {
                    System.err.println("SMTC thread interrupted: " + e.getMessage());
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "SMTC Music Catcher");
        musicInfoThread.start();
    }

    public void updateMediaInfo() {
        String info = getMediaInfo();
        if (shouldClearMediaInfo(info)) {
            clearMediaInfo();
            return;
        }
        try {
            JsonObject json = JsonParser.parseString(info).getAsJsonObject();
            processMediaJson(json);
        } catch (JsonSyntaxException e) {
            System.err.println("SMTC JSON Error: " + e.getMessage());
            clearMediaInfo();
        } catch (IllegalStateException e) {
            System.err.println("SMTC JSON Error - Not a JSON Object: " + e.getMessage());
            clearMediaInfo();
        }
    }

    private boolean shouldClearMediaInfo(String info) {
        if (info == null || info.equals("{}")) {
            return true;
        }
        for (String keyword : BLOCKED_KEYWORDS) {
            if (info.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void processMediaJson(JsonObject json) {
        double totalTimeSeconds = getDoubleValue(json, "totalTime");
        currentPositionSeconds = getDoubleValue(json, "currentPosition");

        String base64Data = getStringValue(json, "thumbnail");
        String state = getStringValue(json, "playbackStatus");
        sourceApp = getStringValue(json, "sourceApp");
        stateText = state.replace("Unknown", "Closed");
        changed = getBooleanValue(json, "changed");

        if (isPlaybackStopped(stateText)) {
            clearMediaInfo();
            return;
        }

        updateTitle(json);
        updateArtist(json);
        updateTotalTime(totalTimeSeconds);
        updateThumbnail(base64Data);
        updateProgressAndPassTime(totalTimeSeconds);

        currentMediaInfo = new MediaInfo(
                titleText, artistText, totalTimeText, passTimeText,
                progress, base64, mapState(stateText), changed, sourceApp
        );
    }

    private boolean isPlaybackStopped(String state) {
        return state.equalsIgnoreCase("Closed")
                || state.equalsIgnoreCase("Opened")
                || state.equalsIgnoreCase("Stopped");
    }

    private void updateTitle(JsonObject json) {
        String newTitle = getStringValue(json, "title");
        if (newTitle.isEmpty()) {
            newTitle = NO_MEDIA_TEXT;
        }
        titleText = newTitle;
        lastTitle = newTitle;
    }

    private void updateArtist(JsonObject json) {
        String newArtist = getStringValue(json, "artist");
        if (newArtist != null) {
            artistText = newArtist;
            lastArtist = newArtist;
        } else {
            artistText = DEFAULT_ARTIST_TEXT;
        }
    }

    private void updateTotalTime(double totalTimeSeconds) {
        String newTotalTime = formatTime(totalTimeSeconds);
        if (!newTotalTime.equals(lastTotalTime)) {
            totalTimeText = newTotalTime;
            lastTotalTime = newTotalTime;
        }
    }

    private void updateThumbnail(String base64Data) {
        if (base64Data != null && !base64Data.equals(lastBase64)) {
            base64 = base64Data;
            lastBase64 = base64Data;
        }
    }

    private void updateProgressAndPassTime(double totalTimeSeconds) {
        passTimeText = formatTime(currentPositionSeconds);
        if (totalTimeSeconds > 0 && currentPositionSeconds >= 0) {
            progress = (float) (currentPositionSeconds / totalTimeSeconds) * 100.0f;
        } else {
            progress = 0.0f;
        }
    }

    private static State mapState(String state) {
        return switch (state) {
            case "Playing" -> State.Playing;
            case "Paused" -> State.Paused;
            case "Stopped" -> State.Stopped;
            default -> State.Unknown;
        };
    }

    public static String formatTime(double seconds) {
        int minutes = (int) (seconds / 60);
        int remainingSeconds = (int) (seconds % 60);
        return String.format("%02d:%02d", minutes, remainingSeconds);
    }

    public void clearMediaInfo() {
        titleText = NO_MEDIA_TEXT;
        artistText = "";
        totalTimeText = "";
        passTimeText = "";
        base64 = "";
        progress = 0;
        currentPositionSeconds = 0d;
        lastTitle = null;
        lastArtist = null;
        lastTotalTime = null;
        lastBase64 = "";
        changed = false;
        sourceApp = "";

        currentMediaInfo = new MediaInfo(
                titleText, artistText, totalTimeText, passTimeText,
                progress, base64, mapState(stateText), changed, sourceApp
        );
    }

    private String getStringValue(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : "";
    }

    private double getDoubleValue(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsDouble() : 0.0;
    }

    private boolean getBooleanValue(JsonObject json, String key) {
        return json.has(key) && json.get(key).getAsBoolean();
    }

    public static MurasameSmtcBootstrap startSmtc() {
        return new MurasameSmtcBootstrap();
    }

    public record MediaInfo(
            String title,
            String artist,
            String totalTime,
            String passTime,
            float progress,
            String base64,
            MurasameSmtcBootstrap.State state,
            boolean changed,
            String sourceApp
    ) {
        @NotNull
        @Override
        public String toString() {
            return "MediaInfo{"
                    + "title='" + title + '\''
                    + ", artist='" + artist + '\''
                    + ", totalTime='" + totalTime + '\''
                    + ", passTime='" + passTime + '\''
                    + ", progress=" + progress
                    + ", base64 length='" + base64.length() + '\''
                    + ", state='" + state.getDisplayName() + '\''
                    + ", changed='" + changed + '\''
                    + ", sourceApp='" + sourceApp + '\''
                    + '}';
        }
    }

    public enum State {
        Playing("Playing"),
        Paused("Paused"),
        Stopped("Stopped"),
        Unknown("Unknown");

        private final String displayName;

        State(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
