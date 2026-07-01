package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.panel.utils.PanelContentBuffer;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.settings.impl.SoundEventListSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class SoundEventListSelectPopup implements PanelPopupHost.Popup {

    private static final float PADDING = 8.0f;
    private static final float TITLE_HEIGHT = 18.0f;
    private static final float SEARCH_HEIGHT = 18.0f;
    private static final float HEADER_HEIGHT = 14.0f;
    private static final float ROW_HEIGHT = 18.0f;
    private static final float ROW_GAP = 2.0f;
    private static final float COLUMN_GAP = 6.0f;
    private static final float SCROLLBAR_GUTTER = ScrollBarUtils.TOTAL_WIDTH + 1.0f;
    private static final float SCROLL_STEP = 24.0f;
    private static final float SCROLL_DECAY = 0.86f;
    private static final float MIN_SCROLL_VELOCITY = 0.3f;
    private static final int MAX_QUERY_LENGTH = 64;

    private final PanelLayout.Rect bounds;
    private final SoundEventListSetting setting;
    private final List<SoundEvent> allSounds;
    private final PanelContentBuffer contentBuffer = new PanelContentBuffer();
    private final TextRenderer textRenderer = TextRenderer.create();
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();

    private String query = "";
    private float scroll;
    private float scrollVelocity;
    private float maxScroll;
    private SoundEvent hoveredAdd;
    private SoundEvent hoveredRemove;
    private PanelLayout.Rect lastViewport;

    public SoundEventListSelectPopup(PanelLayout.Rect bounds, SoundEventListSetting setting) {
        this.bounds = bounds;
        this.setting = setting;
        this.openAnimation.setStartValue(0.0f);
        this.allSounds = collectAllSounds();
    }

    private static List<SoundEvent> collectAllSounds() {
        List<SoundEvent> sounds = new ArrayList<>();
        for (SoundEvent sound : BuiltInRegistries.SOUND_EVENT) sounds.add(sound);
        sounds.sort(Comparator.comparing(s -> {
            Identifier key = BuiltInRegistries.SOUND_EVENT.getKey(s);
            return key != null ? key.toString() : "";
        }));
        return sounds;
    }

    @Override
    public PanelLayout.Rect getBounds() { return bounds; }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, PanelRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        contentBuffer.clear();
        List<SoundEvent> available = filteredAvailable();
        List<SoundEvent> selected = filteredSelected();
        float columnContentHeight = Math.max(available.size(), selected.size()) * (ROW_HEIGHT + ROW_GAP);
        PanelLayout.Rect viewport = getViewport();
        maxScroll = Math.max(0.0f, columnContentHeight - viewport.height());
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);
        updateSmoothScroll(partialTick);

        PanelUiTree tree = PanelUiTree.build(scope -> {
            float progress = scope.animate(openAnimation, 1.0f);
            float popupY = bounds.y() - (1.0f - progress) * 6.0f;
            PanelLayout.Rect animatedBounds = new PanelLayout.Rect(bounds.x(), popupY, bounds.width(), bounds.height());
            PanelLayout.Rect searchBounds = getSearchBounds(popupY);
            PanelLayout.Rect animatedViewport = getViewport(popupY);
            lastViewport = animatedViewport;
            scope.pushAbsolute(animatedBounds, popup -> {
                popup.popupCard(animatedBounds.atOrigin(), MD3Theme.CARD_RADIUS, POPUP_SHADOW_RADIUS,
                        MD3Theme.withAlpha(MD3Theme.SHADOW, (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress)),
                        MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255));

                float titleY = centeredTextY(6.0f, TITLE_HEIGHT, 0.68f);
                float summaryScale = 0.52f;
                String summary = setting.size() + EpsilonTranslations.Gui.LIST_SELECTED.getTranslatedName();
                popup.text(setting.getDisplayName(), PADDING, titleY, 0.68f, MD3Theme.TEXT_PRIMARY);
                popup.text(summary, animatedBounds.width() - PADDING - textRenderer.getWidth(summary, summaryScale),
                        centeredTextY(6.0f, TITLE_HEIGHT, summaryScale), summaryScale, MD3Theme.TEXT_MUTED);
                popup.input(searchBounds.relativeTo(animatedBounds), true, 1.0f, 8.0f,
                        query.isEmpty() ? EpsilonTranslations.Gui.LIST_SEARCH.getTranslatedName() : query, 0.54f,
                        query.isEmpty() ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY,
                        query.length(), MD3Theme.PRIMARY, null, 0.0f, null);
                IMEFocusHelper.updateCursorPos(searchBounds.x() + 8.0f, searchBounds.y() + 4.0f);

                float contentWidth = animatedViewport.width() - SCROLLBAR_GUTTER;
                float columnWidth = (contentWidth - COLUMN_GAP) / 2.0f;
                float leftX = animatedViewport.x();
                float rightX = leftX + columnWidth + COLUMN_GAP;
                float headerY = animatedViewport.y() - HEADER_HEIGHT - 2.0f;
                float headerTextY = centeredTextY(headerY, HEADER_HEIGHT, 0.50f);
                popup.text(EpsilonTranslations.Gui.LIST_AVAILABLE.getTranslatedName(), leftX - animatedBounds.x() + 4.0f, headerTextY - animatedBounds.y(), 0.50f, MD3Theme.TEXT_SECONDARY);
                popup.text(EpsilonTranslations.Gui.LIST_SELECTED_HEADER.getTranslatedName(), rightX - animatedBounds.x() + 4.0f, headerTextY - animatedBounds.y(), 0.50f, MD3Theme.TEXT_SECONDARY);

                hoveredAdd = null;
                hoveredRemove = null;
                PanelLayout.Rect localViewport = animatedViewport.relativeTo(animatedBounds);
                popup.viewport(contentBuffer, localViewport, guiGraphics.guiHeight(), scroll, maxScroll, columnContentHeight, content -> {
                    buildColumn(content, available, leftX, animatedViewport.y() - scroll, columnWidth, mouseX, mouseY, true, animatedViewport);
                    buildColumn(content, selected, rightX, animatedViewport.y() - scroll, columnWidth, mouseX, mouseY, false, animatedViewport);
                });
            });
        });
        renderBatch.render(tree);
    }

    @Override
    public void flush(PanelRenderBatch renderBatch) { contentBuffer.flushAndClear(); }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0 || !bounds.contains(event.x(), event.y())) return false;
        PanelLayout.Rect viewport = lastViewport != null ? lastViewport : getViewport();
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, scroll, maxScroll)) {
            applyDraggedScroll(event.y(), viewport);
            return true;
        }
        if (hoveredAdd != null) { setting.add(hoveredAdd); return true; }
        if (hoveredRemove != null) { setting.remove(hoveredRemove); return true; }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) { return scrollBarDrag.mouseReleased(); }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (!scrollBarDrag.isDragging()) return false;
        PanelLayout.Rect viewport = lastViewport != null ? lastViewport : getViewport();
        applyDraggedScroll(event.y(), viewport);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> { if (!query.isEmpty()) { query = query.substring(0, query.length() - 1); resetScroll(); } yield true; }
            case GLFW.GLFW_KEY_DELETE -> { query = ""; resetScroll(); yield true; }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter() || query.length() >= MAX_QUERY_LENGTH) return false;
        query += event.codepointAsString();
        resetScroll();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!getViewport(bounds.y()).contains(mouseX, mouseY) || maxScroll <= 0.0f) return false;
        scrollVelocity -= (float) scrollY * SCROLL_STEP;
        return true;
    }

    private List<SoundEvent> filteredAvailable() {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<SoundEvent> result = new ArrayList<>();
        for (SoundEvent sound : allSounds) {
            if (setting.contains(sound)) continue;
            Identifier key = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            String text = key != null ? key.toString() : "";
            if (needle.isEmpty() || text.contains(needle)) result.add(sound);
        }
        return result;
    }

    private List<SoundEvent> filteredSelected() {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<SoundEvent> result = new ArrayList<>();
        for (SoundEvent sound : setting.getValue()) {
            Identifier key = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            String text = key != null ? key.toString() : "";
            if (needle.isEmpty() || text.contains(needle)) result.add(sound);
        }
        return result;
    }

    private void buildColumn(PanelUiTree.Scope scope, List<SoundEvent> sounds, float columnX, float startY,
                              float columnWidth, int mouseX, int mouseY, boolean addColumn, PanelLayout.Rect viewport) {
        PanelLayout.Rect origin = scope.bound();
        for (int i = 0; i < sounds.size(); i++) {
            SoundEvent sound = sounds.get(i);
            float rowY = startY + i * (ROW_HEIGHT + ROW_GAP);
            if (rowY + ROW_HEIGHT < viewport.y() || rowY > viewport.bottom()) continue;

            PanelLayout.Rect rowBounds = new PanelLayout.Rect(columnX, rowY, columnWidth, ROW_HEIGHT);
            boolean hovered = rowBounds.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY);
            if (hovered) { if (addColumn) hoveredAdd = sound; else hoveredRemove = sound; }

            float bgHover = addColumn ? (hovered ? 1.0f : 0.0f) : (hovered ? 0.45f : 0.0f);
            Identifier key = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            String displayName = key != null ? key.getPath() : sound.toString();
            String display = trim(displayName, 0.50f, rowBounds.width() - 22.0f);
            float textY = centeredTextY(0.0f, rowBounds.height(), 0.50f);

            PanelLayout.Rect localRowBounds = rowBounds.relativeTo(origin);
            scope.pushRelative(localRowBounds, row -> {
                if (addColumn) {
                    row.roundRect(0.0f, 0.0f, rowBounds.width(), rowBounds.height(), MD3Theme.CONTROL_RADIUS,
                            MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, bgHover));
                    row.text(display, 6.0f, textY, 0.50f, hovered ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_SECONDARY);
                    row.text("+", rowBounds.width() - 12.0f, textY, 0.54f, hovered ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_SECONDARY);
                } else {
                    row.roundRect(0.0f, 0.0f, rowBounds.width(), rowBounds.height(), MD3Theme.CONTROL_RADIUS,
                            MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, bgHover));
                    row.text(display, 6.0f, textY, 0.50f, MD3Theme.ON_SECONDARY_CONTAINER);
                    row.text("-", rowBounds.width() - 12.0f, textY, 0.54f, MD3Theme.ON_SECONDARY_CONTAINER);
                }
            });
        }
    }

    private PanelLayout.Rect getSearchBounds(float popupY) {
        return new PanelLayout.Rect(bounds.x() + PADDING, popupY + TITLE_HEIGHT + 10.0f, bounds.width() - PADDING * 2.0f, SEARCH_HEIGHT);
    }

    private PanelLayout.Rect getViewport() { return getViewport(bounds.y()); }

    private PanelLayout.Rect getViewport(float popupY) {
        float y = popupY + TITLE_HEIGHT + SEARCH_HEIGHT + HEADER_HEIGHT + 18.0f;
        return new PanelLayout.Rect(bounds.x() + PADDING, y, bounds.width() - PADDING * 2.0f, bounds.bottom() - y - PADDING);
    }

    private void updateSmoothScroll(float partialTick) {
        if (maxScroll <= 0.0f) { resetScroll(); return; }
        if (Math.abs(scrollVelocity) <= 0.01f || partialTick <= 0.0f) return;
        float nextScroll = Mth.clamp(scroll + scrollVelocity * partialTick, 0.0f, maxScroll);
        if (Float.compare(nextScroll, scroll) == 0) { scrollVelocity = 0.0f; return; }
        scroll = nextScroll;
        scrollVelocity *= SCROLL_DECAY;
        if (Math.abs(scrollVelocity) < MIN_SCROLL_VELOCITY) scrollVelocity = 0.0f;
    }

    private void resetScroll() { scroll = 0.0f; scrollVelocity = 0.0f; }

    private void applyDraggedScroll(double mouseY, PanelLayout.Rect viewport) {
        float newScroll = scrollBarDrag.mouseDragged(mouseY, viewport, maxScroll);
        if (newScroll >= 0.0f) { scroll = Mth.clamp(newScroll, 0.0f, maxScroll); scrollVelocity = 0.0f; }
    }

    private String trim(String value, float scale, float maxWidth) {
        if (value == null || value.isEmpty()) return "";
        int maxChars = Math.max(3, (int) (maxWidth / (5.0f * scale)));
        return value.length() <= maxChars ? value : value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private float centeredTextY(float boxY, float boxHeight, float scale) {
        return boxY + (boxHeight - textRenderer.getHeight(scale)) * 0.5f;
    }

}
