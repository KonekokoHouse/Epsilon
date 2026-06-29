package com.github.epsilon.graphics.text.ttf;

import com.github.epsilon.graphics.text.GlyphDescriptor;
import com.github.epsilon.graphics.text.IFontLoader;
import net.minecraft.resources.Identifier;
import org.lwjgl.stb.STBTruetype;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

public class TtfFontLoader implements IFontLoader {

    private static final int ASCII_LIMIT = 128;
    private static final int ADVANCE_UNSET = Integer.MIN_VALUE;
    private static final int DEFAULT_MAX_GLYPH_UPLOADS_PER_FRAME = 8;
    private static final AtomicInteger WORKER_ID = new AtomicInteger();
    private static final ExecutorService GLYPH_WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Epsilon TTF Glyph Worker " + WORKER_ID.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    public final TtfFontFile fontFile;

    // ASCII 是 GUI/HUD 文本的主路径，用数组避免 Character 装箱和 HashMap 查找。
    private final GlyphDescriptor[] asciiGlyphMap = new GlyphDescriptor[ASCII_LIMIT];
    private final int[] asciiAdvanceMap = new int[ASCII_LIMIT];
    private final boolean[] asciiPendingGlyphs = new boolean[ASCII_LIMIT];
    private final HashMap<Character, GlyphDescriptor> glyphMap = new HashMap<>();
    private final HashMap<Character, Integer> advanceMap = new HashMap<>();
    private final HashMap<Character, CompletableFuture<TtfGlyph>> pendingGlyphs = new HashMap<>();
    private final Set<Character> deferredReadyGlyphs = new LinkedHashSet<>();
    private final List<TtfGlyphAtlas> atlases = new ArrayList<>();

    private TtfGlyphAtlas currentAtlas;
    private int atlasId = 0;
    // glyphRevision 驱动未完成布局重建，atlasRevision 驱动已释放 atlas 的布局失效。
    private long glyphRevision;
    private long atlasRevision;
    private static int maxGlyphUploadsPerFrame = DEFAULT_MAX_GLYPH_UPLOADS_PER_FRAME;
    private static long GLOBAL_RENDER_FRAME_ID;
    private static long uploadFrameId = Long.MIN_VALUE;
    private static int glyphUploadsThisFrame;

    public TtfFontLoader(Identifier ttfFile) {
        this.fontFile = new TtfFontFile(ttfFile, 48, 4);
        Arrays.fill(asciiAdvanceMap, ADVANCE_UNSET);
    }

    @Override
    public void checkAndLoadChar(char ch) {
        loadCharImmediately(ch);
    }

    public void loadCharImmediately(char ch) {
        if (hasGlyph(ch)) return;

        CompletableFuture<TtfGlyph> pending = removePendingGlyph(ch);
        deferredReadyGlyphs.remove(ch);
        TtfGlyph glyph;
        try {
            glyph = pending != null ? pending.join() : fontFile.generateGlyph(ch);
        } catch (RuntimeException ignored) {
            glyph = fontFile.generateGlyph(ch);
        }
        appendGlyph(ch, glyph);
    }

    public int getAdvance(char ch) {
        GlyphDescriptor glyph = getGlyph(ch);
        if (glyph != null) {
            return glyph.advance();
        }

        if (isAscii(ch)) {
            int advance = asciiAdvanceMap[ch];
            if (advance != ADVANCE_UNSET) {
                return advance;
            }

            advance = fontFile.getAdvance(ch);
            asciiAdvanceMap[ch] = advance;
            return advance;
        }

        return advanceMap.computeIfAbsent(ch, fontFile::getAdvance);
    }

    public void requestChars(String chars) {
        drainReadyGlyphs();
        requestMissingChars(chars);
    }

    // addText 热路径先请求缺失字形，再统一上传已完成字形，避免一次调用里重复 drain。
    public void prepareChars(String chars) {
        requestMissingChars(chars);
        drainReadyGlyphs();
    }

    private void requestMissingChars(String chars) {

        for (int i = 0; i < chars.length(); i++) {
            char ch = chars.charAt(i);
            if (ch == ' ' || ch == '\n' || hasGlyph(ch) || hasPendingGlyph(ch)) {
                continue;
            }

            putPendingGlyph(ch, CompletableFuture.supplyAsync(() -> fontFile.generateGlyph(ch), GLYPH_WORKER));
        }
    }

    public void drainReadyGlyphs() {
        beginUploadFrameIfNeeded();
        if (pendingGlyphs.isEmpty() && deferredReadyGlyphs.isEmpty()) {
            return;
        }

        List<Character> newlyReadyChars = null;
        for (Map.Entry<Character, CompletableFuture<TtfGlyph>> entry : pendingGlyphs.entrySet()) {
            if (entry.getValue().isDone()) {
                if (newlyReadyChars == null) {
                    newlyReadyChars = new ArrayList<>();
                }
                newlyReadyChars.add(entry.getKey());
            }
        }

        if (newlyReadyChars != null) {
            deferredReadyGlyphs.addAll(newlyReadyChars);
        }

        if (deferredReadyGlyphs.isEmpty()) {
            return;
        }

        Iterator<Character> iterator = deferredReadyGlyphs.iterator();
        while (iterator.hasNext() && canUploadMoreGlyphsThisFrame()) {
            char ch = iterator.next();
            iterator.remove();
            CompletableFuture<TtfGlyph> future = removePendingGlyph(ch);
            if (future != null && !future.isCompletedExceptionally() && !future.isCancelled()) {
                appendGlyph(ch, future.join());
                glyphUploadsThisFrame++;
            }
        }
    }

    public static int getMaxGlyphUploadsPerFrame() {
        return maxGlyphUploadsPerFrame;
    }

    public static void setMaxGlyphUploadsPerFrame(int maxGlyphUploadsPerFrame) {
        TtfFontLoader.maxGlyphUploadsPerFrame = Math.max(1, maxGlyphUploadsPerFrame);
    }

    public static void beginRenderFrame() {
        GLOBAL_RENDER_FRAME_ID++;
    }

    public long getGlyphRevision() {
        return glyphRevision;
    }

    public long getAtlasRevision() {
        return atlasRevision;
    }

    private void beginUploadFrameIfNeeded() {
        long frameId = GLOBAL_RENDER_FRAME_ID;
        if (frameId == uploadFrameId) {
            return;
        }
        uploadFrameId = frameId;
        glyphUploadsThisFrame = 0;
    }

    private boolean canUploadMoreGlyphsThisFrame() {
        return glyphUploadsThisFrame < maxGlyphUploadsPerFrame;
    }

    private void appendGlyph(char ch, TtfGlyph glyph) {
        if (glyph == null || glyph.glyphData() == null) return;

        if (currentAtlas == null) {
            createNewAtlas();
        }

        TtfGlyphAtlas.GlyphUV uv = currentAtlas.appendGlyph(glyph);

        if (uv == null) {
            createNewAtlas();
            uv = currentAtlas.appendGlyph(glyph);
        }

        if (uv != null) {
            GlyphDescriptor descriptor = new GlyphDescriptor(
                    currentAtlas, uv,
                    glyph.width(), glyph.height(),
                    glyph.xOffset(), glyph.yOffset(),
                    glyph.advance()
            );
            putGlyph(ch, descriptor);
            glyphRevision++;
        }

        freeGlyph(glyph);
    }

    private void createNewAtlas() {
        currentAtlas = new TtfGlyphAtlas(atlasId);
        atlases.add(currentAtlas);
        atlasId++;
    }

    @Override
    public void checkAndLoadChars(String chars) {
        for (final var ch : chars.toCharArray()) {
            checkAndLoadChar(ch);
        }
    }


    @Override
    public void destroy() {
        fontFile.destroy();
        for (TtfGlyphAtlas atlas : atlases) {
            atlas.destroy();
        }
        atlases.clear();
        glyphMap.clear();
        advanceMap.clear();
        Arrays.fill(asciiGlyphMap, null);
        Arrays.fill(asciiAdvanceMap, ADVANCE_UNSET);
        Arrays.fill(asciiPendingGlyphs, false);
        for (CompletableFuture<TtfGlyph> future : pendingGlyphs.values()) {
            if (future.isDone() && !future.isCompletedExceptionally() && !future.isCancelled()) {
                freeGlyph(future.join());
            } else {
                future.cancel(false);
            }
        }
        pendingGlyphs.clear();
        deferredReadyGlyphs.clear();
        glyphRevision++;
        // atlas 已被释放，任何缓存布局里保存的 atlas 引用都必须失效。
        atlasRevision++;
    }

    private void freeGlyph(TtfGlyph glyph) {
        if (glyph != null && glyph.glyphData() != null) {
            STBTruetype.stbtt_FreeSDF(glyph.glyphData());
        }
    }

    @Override
    public GlyphDescriptor getGlyph(char ch) {
        if (isAscii(ch)) {
            return asciiGlyphMap[ch];
        }
        return glyphMap.get(ch);
    }

    private void putGlyph(char ch, GlyphDescriptor glyph) {
        if (isAscii(ch)) {
            asciiGlyphMap[ch] = glyph;
        } else {
            glyphMap.put(ch, glyph);
        }
    }

    private boolean hasGlyph(char ch) {
        return getGlyph(ch) != null;
    }

    private void putPendingGlyph(char ch, CompletableFuture<TtfGlyph> future) {
        pendingGlyphs.put(ch, future);
        if (isAscii(ch)) {
            asciiPendingGlyphs[ch] = true;
        }
    }

    private CompletableFuture<TtfGlyph> removePendingGlyph(char ch) {
        CompletableFuture<TtfGlyph> future = pendingGlyphs.remove(ch);
        if (isAscii(ch)) {
            asciiPendingGlyphs[ch] = false;
        }
        return future;
    }

    private boolean hasPendingGlyph(char ch) {
        if (isAscii(ch)) {
            return asciiPendingGlyphs[ch];
        }
        return pendingGlyphs.containsKey(ch);
    }

    private static boolean isAscii(char ch) {
        return ch < ASCII_LIMIT;
    }
}
