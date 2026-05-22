package com.github.epsilon.utils.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AmllTtmlDbLyricService {

    private static final String NCM_TTML_URL = "https://amll-ttml-db.stevexmh.net/ncm/";
    private static final String BIKONOO_SEARCH_URL = "https://amlldb.bikonoo.com/api/search-lyrics";
    private static final String BIKONOO_RAW_LYRIC_URL = "https://amlldb.bikonoo.com/raw-lyrics/";
    private static final Map<String, List<SmtcLyricTimeline.LyricLine>> LYRIC_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, List<SmtcLyricTimeline.LyricLine>> TITLE_CACHE = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<SmtcLyricTimeline.LyricLine> resolveByNeteaseId(String songId) throws IOException, InterruptedException {
        if (songId == null || songId.isBlank()) {
            return List.of();
        }

        List<SmtcLyricTimeline.LyricLine> cached = LYRIC_CACHE.get(songId);
        if (cached != null) {
            return cached;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(NCM_TTML_URL + songId + "?format=ttml"))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/xml,text/xml,text/plain;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body().isBlank()) {
            return List.of();
        }

        List<SmtcLyricTimeline.LyricLine> parsed = parseTtml(response.body());
        if (!parsed.isEmpty()) {
            LYRIC_CACHE.put(songId, parsed);
        }
        return parsed;
    }

    public List<SmtcLyricTimeline.LyricLine> resolveByRoughTitle(String title) throws IOException, InterruptedException {
        if (title == null || title.isBlank()) {
            return List.of();
        }

        String normalizedTitle = normalizeQuery(title);
        List<SmtcLyricTimeline.LyricLine> cached = TITLE_CACHE.get(normalizedTitle);
        if (cached != null) {
            return cached;
        }

        JsonArray results = searchLyrics(title);
        if (results == null || results.isEmpty()) {
            return List.of();
        }

        JsonObject first = firstResult(results);
        if (first == null) {
            return List.of();
        }

        String file = getString(first, "file");
        if (file.isBlank()) {
            return List.of();
        }

        List<SmtcLyricTimeline.LyricLine> parsed = fetchBikonooLyric(file);
        if (!parsed.isEmpty()) {
            TITLE_CACHE.put(normalizedTitle, parsed);
        }
        return parsed;
    }

    private JsonArray searchLyrics(String title) throws IOException, InterruptedException {
        JsonObject payload = new JsonObject();
        payload.addProperty("query", title);
        payload.addProperty("type", "title");

        HttpRequest request = HttpRequest.newBuilder(URI.create(BIKONOO_SEARCH_URL))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body().isBlank()) {
            return null;
        }

        JsonElement root = JsonParser.parseString(response.body());
        return root.isJsonArray() ? root.getAsJsonArray() : null;
    }

    private JsonObject firstResult(JsonArray results) {
        for (JsonElement element : results) {
            if (element.isJsonObject()) {
                return element.getAsJsonObject();
            }
        }
        return null;
    }

    private List<SmtcLyricTimeline.LyricLine> fetchBikonooLyric(String fileName) throws IOException, InterruptedException {
        List<SmtcLyricTimeline.LyricLine> cached = LYRIC_CACHE.get(fileName);
        if (cached != null) {
            return cached;
        }

        HttpRequest request = HttpRequest.newBuilder(URI.create(BIKONOO_RAW_LYRIC_URL + URLEncoder.encode(fileName, StandardCharsets.UTF_8)))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/xml,text/xml,text/plain;q=0.9,*/*;q=0.8")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200 || response.body().isBlank()) {
            return List.of();
        }

        List<SmtcLyricTimeline.LyricLine> parsed = parseTtml(response.body());
        if (!parsed.isEmpty()) {
            LYRIC_CACHE.put(fileName, parsed);
        }
        return parsed;
    }

    private static List<SmtcLyricTimeline.LyricLine> parseTtml(String xml) throws IOException {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            Document document = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            NodeList pNodes = document.getElementsByTagNameNS("*", "p");
            List<SmtcLyricTimeline.LyricLine> lines = new ArrayList<>(pNodes.getLength());
            for (int i = 0; i < pNodes.getLength(); i++) {
                Node node = pNodes.item(i);
                if (!(node instanceof Element element)) {
                    continue;
                }

                String begin = element.getAttribute("begin");
                if (begin == null || begin.isBlank()) {
                    continue;
                }

                ParsedTtmlLine parsedLine = parseLine(element);
                String original = parsedLine.originalText();
                if (original.isBlank()) {
                    continue;
                }

                lines.add(new SmtcLyricTimeline.LyricLine(
                        parseTimestamp(begin),
                        original,
                        parsedLine.translatedText(),
                        parsedLine.wordChunks()
                ));
            }

            lines.sort(Comparator.comparingDouble(SmtcLyricTimeline.LyricLine::timeSeconds));
            return List.copyOf(lines);
        } catch (Exception e) {
            throw new IOException("Failed to parse AMLL TTML", e);
        }
    }

    private static ParsedTtmlLine parseLine(Element element) {
        StringBuilder originalBuilder = new StringBuilder();
        StringBuilder translatedBuilder = new StringBuilder();
        List<SmtcLyricTimeline.WordChunk> chunks = new ArrayList<>();
        NodeList children = element.getChildNodes();

        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                appendVisibleText(child, originalBuilder);
                continue;
            }

            if (!(child instanceof Element childElement)) {
                continue;
            }

            String role = getAttributeByLocalName(childElement, "role");
            if ("x-translation".equalsIgnoreCase(role)) {
                appendVisibleText(childElement, translatedBuilder);
                continue;
            }

            if ("x-bg".equalsIgnoreCase(role) || shouldSkipNode(childElement)) {
                continue;
            }

            appendVisibleText(childElement, originalBuilder);

            if (!isSpan(childElement)) {
                continue;
            }

            String begin = childElement.getAttribute("begin");
            String end = childElement.getAttribute("end");
            String text = collectVisibleText(childElement);
            if (text.isBlank() || begin == null || begin.isBlank() || end == null || end.isBlank()) {
                continue;
            }

            chunks.add(new SmtcLyricTimeline.WordChunk(text, parseTimestamp(begin), parseTimestamp(end)));
        }

        return new ParsedTtmlLine(
                normalizeText(originalBuilder.toString()),
                normalizeText(translatedBuilder.toString()),
                List.copyOf(chunks)
        );
    }

    private static boolean isSpan(Element element) {
        return "span".equalsIgnoreCase(element.getLocalName()) || "span".equalsIgnoreCase(element.getNodeName());
    }

    private static void appendVisibleText(Node node, StringBuilder builder) {
        if (node == null || shouldSkipNode(node)) {
            return;
        }

        if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            String text = node.getNodeValue();
            if (text != null && !text.isEmpty()) {
                appendNormalizedText(builder, text);
            }
            return;
        }

        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            appendVisibleText(children.item(i), builder);
        }
    }

    private static String collectVisibleText(Node node) {
        StringBuilder builder = new StringBuilder();
        appendVisibleText(node, builder);
        return normalizeText(builder.toString());
    }

    private static boolean shouldSkipNode(Node node) {
        if (!(node instanceof Element element)) {
            return false;
        }

        String localName = safeLower(element.getLocalName());
        String nodeName = safeLower(element.getNodeName());
        if (localName.equals("rt") || nodeName.equals("rt")
                || localName.equals("ruby") || nodeName.equals("ruby")
                || localName.equals("phonetic") || nodeName.equals("phonetic")
                || localName.equals("annotation") || nodeName.equals("annotation")) {
            return true;
        }

        String role = safeLower(getAttributeByLocalName(element, "role"));
        String type = safeLower(getAttributeByLocalName(element, "type"));
        String marker = role + " " + type + " " + localName + " " + nodeName;
        return marker.contains("phonetic")
                || marker.contains("ruby")
                || marker.contains("annotation")
                || marker.contains("translit")
                || marker.contains("roman")
                || marker.contains("romaji")
                || marker.contains("pronun")
                || marker.contains("pinyin")
                || marker.contains("lyric-annotation");
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }

    private static String getAttributeByLocalName(Element element, String localName) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attribute = attributes.item(i);
            String currentLocalName = attribute.getLocalName();
            if (localName.equals(currentLocalName) || localName.equals(attribute.getNodeName())) {
                return attribute.getNodeValue();
            }
        }
        return "";
    }

    private static String normalizeText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace('\uFEFF', ' ')
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static void appendNormalizedText(StringBuilder builder, String text) {
        String normalized = text.replace('\uFEFF', ' ')
                .replace('\u00A0', ' ');
        if (normalized.isBlank()) {
            if (builder.length() > 0 && builder.charAt(builder.length() - 1) != ' ') {
                builder.append(' ');
            }
            return;
        }

        if (builder.length() > 0 && Character.isWhitespace(builder.charAt(builder.length() - 1))
                && Character.isWhitespace(normalized.charAt(0))) {
            builder.append(normalized.stripLeading());
            return;
        }

        builder.append(normalized);
    }

    private static double parseTimestamp(String value) {
        String[] parts = value.split(":");
        if (parts.length == 2) {
            int minutes = Integer.parseInt(parts[0]);
            double seconds = Double.parseDouble(parts[1]);
            return minutes * 60d + seconds;
        }

        if (parts.length == 3) {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            double seconds = Double.parseDouble(parts[2]);
            return hours * 3600d + minutes * 60d + seconds;
        }

        if (parts.length == 1) {
            return Double.parseDouble(parts[0]);
        }

        if (value.contains("ms")) {
            return Double.parseDouble(value.replace("ms", "").trim()) / 1000d;
        }

        if (value.contains("s")) {
            return Double.parseDouble(value.replace("s", "").trim());
        }

        if (value.contains("m")) {
            return Double.parseDouble(value.replace("m", "").trim()) * 60d;
        }

        if (value.contains("h")) {
            return Double.parseDouble(value.replace("h", "").trim()) * 3600d;
        }

        if (value.isBlank()) {
            return 0d;
        }

        return Double.parseDouble(value);
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private static String normalizeQuery(String text) {
        return text == null ? "" : text.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private record ParsedTtmlLine(
            String originalText,
            String translatedText,
            List<SmtcLyricTimeline.WordChunk> wordChunks
    ) {
    }

}
