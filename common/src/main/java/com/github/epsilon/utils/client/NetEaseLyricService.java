package com.github.epsilon.utils.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tech.AirFoundation.yux1n9.MurasameSmtcBootstrap;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class NetEaseLyricService {

    private static final String EAPI_KEY = "e82ckenh8dichen8";
    private static final String SEARCH_URL = "https://interfacepc.music.163.com/eapi/api/cloudsearch/pc";
    private static final String LYRIC_URL = "https://interfacepc.music.163.com/eapi/api/song/lyric/v1";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.0.18.203152";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Map<String, SongCandidate> SEARCH_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, LyricContent> LYRIC_CACHE = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DebugLookupResult resolveLyricsWithDebug(String title, String artist) throws IOException, InterruptedException {
        String normalizedTitle = normalize(title);
        String normalizedArtist = normalize(artist);
        if (normalizedTitle.isEmpty() || MurasameSmtcBootstrap.NO_MEDIA_TEXT.equalsIgnoreCase(normalizedTitle)) {
            return new DebugLookupResult(null, "empty title", List.of(), null, null);
        }

        List<String> attemptedQueries = buildQueries(title, artist);
        List<QueryDebug> queryDebugs = new ArrayList<>();
        SongCandidate candidate = null;
        QueryDebug matchedQuery = null;

        for (String query : attemptedQueries) {
            SearchAttempt attempt = searchBestSong(query, normalizedTitle, normalizedArtist);
            queryDebugs.add(attempt.debug());
            if (attempt.candidate() != null) {
                candidate = attempt.candidate();
                matchedQuery = attempt.debug();
                break;
            }
        }

        if (candidate == null) {
            return new DebugLookupResult(null, "no candidate matched", attemptedQueries, queryDebugs, null);
        }

        LyricContent lyricContent = fetchLyric(candidate.songId());
        if (lyricContent == null || lyricContent.originalLyric() == null || lyricContent.originalLyric().isBlank()) {
            return new DebugLookupResult(
                    null,
                    "lyric empty for songId=" + candidate.songId(),
                    attemptedQueries,
                    queryDebugs,
                    matchedQuery
            );
        }

        return new DebugLookupResult(
                new LyricLookupResult(candidate.songId(), candidate.songName(), candidate.artists(),
                        lyricContent.originalLyric(), lyricContent.translatedLyric()),
                "ok",
                attemptedQueries,
                queryDebugs,
                matchedQuery
        );
    }

    private SearchAttempt searchBestSong(String query, String expectedTitle, String expectedArtist) throws IOException, InterruptedException {
        String cacheKey = normalize(query);
        SongCandidate cachedCandidate = SEARCH_CACHE.get(cacheKey);
        if (cachedCandidate != null) {
            int cachedScore = scoreCandidate(expectedTitle, expectedArtist, cachedCandidate);
            SongCandidate matched = cachedScore >= 30 ? cachedCandidate : null;
            return new SearchAttempt(
                    matched,
                    new QueryDebug(query, "cache bestScore=" + cachedScore, 1,
                            List.of(cachedCandidate.songName() + " [" + cachedCandidate.songId() + "] - "
                                    + String.join("/", cachedCandidate.artists()) + " (score=" + cachedScore + ")"))
            );
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("s", query);
        data.put("type", 1);
        data.put("limit", 100);
        data.put("offset", 0);
        data.put("total", true);

        JsonObject json = postEapi(SEARCH_URL, "/api/cloudsearch/pc", data);
        if (json == null) {
            return new SearchAttempt(null, new QueryDebug(query, "http failure", 0, List.of()));
        }

        int code = json.has("code") ? json.get("code").getAsInt() : -1;
        JsonObject result = json.has("result") && json.get("result").isJsonObject()
                ? json.getAsJsonObject("result")
                : null;
        if (code != 200 || result == null || !result.has("songs")) {
            return new SearchAttempt(null, new QueryDebug(query, "api code=" + code, 0, List.of()));
        }

        JsonArray songs = result.getAsJsonArray("songs");
        List<SongCandidate> candidates = new ArrayList<>();
        for (JsonElement songElement : songs) {
            if (!songElement.isJsonObject()) {
                continue;
            }

            JsonObject song = songElement.getAsJsonObject();
            String songId = song.has("id") ? song.get("id").getAsString() : "";
            String songName = song.has("name") ? song.get("name").getAsString() : "";
            List<String> artists = new ArrayList<>();
            if (song.has("ar") && song.get("ar").isJsonArray()) {
                for (JsonElement artistElement : song.getAsJsonArray("ar")) {
                    if (artistElement.isJsonObject() && artistElement.getAsJsonObject().has("name")) {
                        artists.add(artistElement.getAsJsonObject().get("name").getAsString());
                    }
                }
            }

            candidates.add(new SongCandidate(songId, songName, artists));
        }

        List<String> preview = candidates.stream()
                .limit(5)
                .map(candidate -> candidate.songName() + " [" + candidate.songId() + "] - " + String.join("/", candidate.artists())
                        + " (score=" + scoreCandidate(expectedTitle, expectedArtist, candidate) + ")")
                .toList();

        SongCandidate best = candidates.stream()
                .max(Comparator.comparingInt(candidate -> scoreCandidate(expectedTitle, expectedArtist, candidate)))
                .orElse(null);

        if (best == null) {
            return new SearchAttempt(null, new QueryDebug(query, "empty songs", candidates.size(), preview));
        }

        SEARCH_CACHE.put(cacheKey, best);
        int bestScore = scoreCandidate(expectedTitle, expectedArtist, best);
        SongCandidate matched = bestScore >= 30 ? best : null;
        return new SearchAttempt(
                matched,
                new QueryDebug(query, "bestScore=" + bestScore, candidates.size(), preview)
        );
    }

    private LyricContent fetchLyric(String songId) throws IOException, InterruptedException {
        LyricContent cachedLyric = LYRIC_CACHE.get(songId);
        if (cachedLyric != null) {
            return cachedLyric;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", Long.parseLong(songId));
        data.put("cp", false);
        data.put("tv", 0);
        data.put("lv", 0);
        data.put("rv", 0);
        data.put("kv", 0);
        data.put("yv", 0);
        data.put("ytv", 0);
        data.put("yrv", 0);

        JsonObject json = postEapi(LYRIC_URL, "/api/song/lyric/v1", data);
        if (json == null || !json.has("code") || json.get("code").getAsInt() != 200 || !json.has("lrc")) {
            return null;
        }

        JsonObject lrc = json.getAsJsonObject("lrc");
        if (!lrc.has("lyric")) {
            return null;
        }

        String lyric = lrc.get("lyric").getAsString();
        String translated = "";
        if (json.has("tlyric") && json.get("tlyric").isJsonObject()) {
            JsonObject tlyric = json.getAsJsonObject("tlyric");
            if (tlyric.has("lyric")) {
                translated = tlyric.get("lyric").getAsString();
            }
        }

        if (!lyric.isBlank()) {
            LyricContent content = new LyricContent(lyric, translated);
            LYRIC_CACHE.put(songId, content);
            return content;
        }
        return null;
    }

    private JsonObject postEapi(String fullUrl, String apiPath, Map<String, Object> data) throws IOException, InterruptedException {
        Map<String, String> cookie = createDefaultCookie();
        Map<String, Object> payload = new LinkedHashMap<>(data);
        payload.put("header", createHeader(cookie));
        payload.put("e_r", false);

        String body;
        try {
            body = buildEapiForm(apiPath, payload);
        } catch (GeneralSecurityException e) {
            throw new IOException("Failed to build NetEase request", e);
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(fullUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", USER_AGENT)
                .header("Cookie", buildCookieHeader(cookie))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            return null;
        }

        JsonElement root = JsonParser.parseString(response.body());
        return root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    private static Map<String, String> createDefaultCookie() {
        Map<String, String> cookie = new LinkedHashMap<>();
        String nuid = randomHex(32);
        cookie.put("__remember_me", "true");
        cookie.put("ntes_kaola_ad", "1");
        cookie.put("_ntes_nuid", nuid);
        cookie.put("_ntes_nnid", nuid + "," + System.currentTimeMillis());
        cookie.put("WNMCID", randomLetters(6) + "." + System.currentTimeMillis() + ".01.0");
        cookie.put("WEVNSM", "1.0.0");
        cookie.put("osver", "Microsoft-Windows-11-Professional-build-26100-64bit");
        cookie.put("deviceId", randomHex(26).toUpperCase() + "\r");
        cookie.put("os", "pc");
        cookie.put("channel", "netease");
        cookie.put("appver", "3.1.12.204072");
        cookie.put("NMTID", randomHex(16));
        cookie.put("versioncode", "140");
        cookie.put("mobilename", "");
        cookie.put("buildver", String.valueOf(System.currentTimeMillis()).substring(0, 10));
        cookie.put("resolution", "1920x1080");
        cookie.put("__csrf", "");
        return cookie;
    }

    private static Map<String, String> createHeader(Map<String, String> cookie) {
        Map<String, String> header = new LinkedHashMap<>();
        header.put("osver", cookie.get("osver"));
        header.put("deviceId", cookie.get("deviceId"));
        header.put("os", cookie.get("os"));
        header.put("appver", cookie.get("appver"));
        header.put("versioncode", cookie.get("versioncode"));
        header.put("mobilename", cookie.get("mobilename"));
        header.put("buildver", cookie.get("buildver"));
        header.put("resolution", cookie.get("resolution"));
        header.put("__csrf", cookie.get("__csrf"));
        header.put("channel", cookie.get("channel"));
        header.put("requestId", System.currentTimeMillis() + "_" + String.format("%04d", RANDOM.nextInt(1000)));
        return header;
    }

    private static String buildCookieHeader(Map<String, String> cookie) {
        List<String> pairs = new ArrayList<>();
        for (Map.Entry<String, String> entry : cookie.entrySet()) {
            pairs.add(urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()));
        }
        return String.join("; ", pairs);
    }

    private static String buildEapiForm(String path, Map<String, Object> payload) throws GeneralSecurityException {
        String json = gsonJson(payload);
        String message = "nobody" + path + "use" + json + "md5forencrypt";
        String digest = md5Hex(message);
        String data = path + "-36cd479b6b5-" + json + "-36cd479b6b5-" + digest;
        String params = aesEcbHex(data, EAPI_KEY);
        return "params=" + urlEncode(params);
    }

    private static String gsonJson(Map<String, Object> payload) {
        JsonObject json = new JsonObject();
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            addJsonProperty(json, entry.getKey(), entry.getValue());
        }
        return json.toString();
    }

    private static void addJsonProperty(JsonObject json, String key, Object value) {
        if (value == null) {
            json.add(key, null);
            return;
        }
        if (value instanceof Number number) {
            json.addProperty(key, number);
            return;
        }
        if (value instanceof Boolean bool) {
            json.addProperty(key, bool);
            return;
        }
        if (value instanceof String str) {
            json.addProperty(key, str);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject nested = new JsonObject();
            for (Map.Entry<?, ?> nestedEntry : map.entrySet()) {
                addJsonProperty(nested, String.valueOf(nestedEntry.getKey()), nestedEntry.getValue());
            }
            json.add(key, nested);
            return;
        }
        json.addProperty(key, String.valueOf(value));
    }

    private static String aesEcbHex(String plainText, String key) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
        return toHex(encrypted).toUpperCase();
    }

    private static String md5Hex(String text) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("MD5");
        return toHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private static String randomHex(int bytesLength) {
        byte[] bytes = new byte[bytesLength];
        RANDOM.nextBytes(bytes);
        return toHex(bytes);
    }

    private static String randomLetters(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private static int scoreCandidate(String expectedTitle, String expectedArtist, SongCandidate candidate) {
        int score = 0;
        String songName = normalize(candidate.songName());
        String joinedArtist = normalize(String.join(" ", candidate.artists()));

        if (songName.equals(expectedTitle)) {
            score += 100;
        } else if (songName.contains(expectedTitle) || expectedTitle.contains(songName)) {
            score += 60;
        }

        if (!expectedArtist.isEmpty()) {
            if (joinedArtist.equals(expectedArtist)) {
                score += 80;
            } else if (joinedArtist.contains(expectedArtist) || expectedArtist.contains(joinedArtist)) {
                score += 40;
            }
        }

        score -= Math.abs(songName.length() - expectedTitle.length());
        return score;
    }

    private static List<String> buildQueries(String title, String artist) {
        String safeTitle = title == null ? "" : title.trim();
        String safeArtist = artist == null ? "" : artist.trim();
        Set<String> queries = new LinkedHashSet<>();

        if (!safeTitle.isEmpty() && !safeArtist.isEmpty()) {
            queries.add(safeTitle + " " + safeArtist);
        }
        if (!safeTitle.isEmpty()) {
            queries.add(safeTitle);
        }
        for (String token : splitArtists(safeArtist)) {
            queries.add(safeTitle + " " + token);
        }

        return queries.stream()
                .map(String::trim)
                .filter(query -> !query.isEmpty())
                .toList();
    }

    private static List<String> splitArtists(String artist) {
        if (artist == null || artist.isBlank()) {
            return List.of();
        }

        String[] parts = artist.split("\\s*/\\s*|\\s*&\\s*|\\s*,\\s*|\\s+feat\\.?\\s+|\\s+ft\\.?\\s+");
        List<String> tokens = new ArrayList<>();
        for (String part : parts) {
            String token = part.trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.trim().toLowerCase()
                .replace("（", "(")
                .replace("）", ")")
                .replaceAll("\\s+", " ");
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record LyricLookupResult(String songId, String songName, List<String> artists,
                                    String lyric, String translatedLyric) {
    }

    public record QueryDebug(String query, String status, int candidateCount, List<String> preview) {
    }

    public record DebugLookupResult(
            LyricLookupResult result,
            String status,
            List<String> attemptedQueries,
            List<QueryDebug> queryDebugs,
            QueryDebug matchedQuery
    ) {
    }

    private record SearchAttempt(SongCandidate candidate, QueryDebug debug) {
    }

    private record SongCandidate(String songId, String songName, List<String> artists) {
    }

    private record LyricContent(String originalLyric, String translatedLyric) {
    }

}
