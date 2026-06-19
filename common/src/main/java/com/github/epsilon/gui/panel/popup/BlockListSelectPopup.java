package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.panel.utils.PanelContentBuffer;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.settings.impl.BlockListSetting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.epsilon.utils.world.BlockRegistryUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BlockListSelectPopup implements PanelPopupHost.Popup {

    private static final float PADDING = 8.0f;
    private static final float TITLE_HEIGHT = 18.0f;
    private static final float SEARCH_HEIGHT = 18.0f;
    private static final float HEADER_HEIGHT = 14.0f;
    private static final float ROW_HEIGHT = 18.0f;
    private static final float ROW_GAP = 2.0f;
    private static final float COLUMN_GAP = 6.0f;
    private static final float SCROLLBAR_GUTTER = ScrollBarUtils.TOTAL_WIDTH + 4.0f;
    private static final int MAX_QUERY_LENGTH = 64;

    private final PanelLayout.Rect bounds;
    private final BlockListSetting setting;
    private final List<Block> allBlocks = BlockRegistryUtils.allSelectableBlocks();
    private final PanelContentBuffer contentBuffer = new PanelContentBuffer();
    private final TextRenderer textRenderer = TextRenderer.create();
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);

    private String query = "";
    private float scroll;
    private float maxScroll;
    private Block hoveredAdd;
    private Block hoveredRemove;

    public BlockListSelectPopup(PanelLayout.Rect bounds, BlockListSetting setting) {
        this.bounds = bounds;
        this.setting = setting;
        this.openAnimation.setStartValue(0.0f);
    }

    @Override
    public PanelLayout.Rect getBounds() {
        return bounds;
    }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, PanelRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        contentBuffer.clear();
        List<Block> available = filteredAvailable();
        List<Block> selected = filteredSelected();
        float columnContentHeight = Math.max(available.size(), selected.size()) * (ROW_HEIGHT + ROW_GAP);
        PanelLayout.Rect viewport = getViewport();
        maxScroll = Math.max(0.0f, columnContentHeight - viewport.height());
        scroll = Mth.clamp(scroll, 0.0f, maxScroll);

        PanelUiTree tree = PanelUiTree.build(scope -> {
            float progress = scope.animate(openAnimation, 1.0f);
            float popupY = bounds.y() - (1.0f - progress) * 6.0f;
            PanelLayout.Rect animatedBounds = new PanelLayout.Rect(bounds.x(), popupY, bounds.width(), bounds.height());
            PanelLayout.Rect searchBounds = getSearchBounds(popupY);
            PanelLayout.Rect animatedViewport = getViewport(popupY);

            scope.popupCard(animatedBounds, MD3Theme.CARD_RADIUS, POPUP_SHADOW_RADIUS,
                    MD3Theme.withAlpha(MD3Theme.SHADOW, (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress)),
                    MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255));

            float titleY = centeredTextY(popupY + 6.0f, TITLE_HEIGHT, 0.68f);
            float summaryScale = 0.52f;
            String summary = setting.size() + " selected";
            scope.text(setting.getDisplayName(), bounds.x() + PADDING, titleY, 0.68f, MD3Theme.TEXT_PRIMARY);
            scope.text(summary, bounds.right() - PADDING - textRenderer.getWidth(summary, summaryScale),
                    centeredTextY(popupY + 6.0f, TITLE_HEIGHT, summaryScale), summaryScale, MD3Theme.TEXT_MUTED);
            scope.input(searchBounds, true, 1.0f, 8.0f, query.isEmpty() ? "Search blocks" : query, 0.54f,
                    query.isEmpty() ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY, query.length(), MD3Theme.PRIMARY, null, 0.0f, null);
            IMEFocusHelper.updateCursorPos(searchBounds.x() + 8.0f, searchBounds.y() + 4.0f);

            float contentWidth = animatedViewport.width() - SCROLLBAR_GUTTER;
            float columnWidth = (contentWidth - COLUMN_GAP) / 2.0f;
            float leftX = animatedViewport.x();
            float rightX = leftX + columnWidth + COLUMN_GAP;
            float headerY = animatedViewport.y() - HEADER_HEIGHT - 2.0f;
            float headerTextY = centeredTextY(headerY, HEADER_HEIGHT, 0.50f);
            scope.text("Available", leftX + 4.0f, headerTextY, 0.50f, MD3Theme.TEXT_SECONDARY);
            scope.text("Selected", rightX + 4.0f, headerTextY, 0.50f, MD3Theme.TEXT_SECONDARY);

            hoveredAdd = null;
            hoveredRemove = null;
            scope.viewport(contentBuffer, animatedViewport, guiGraphics.guiHeight(), scroll, maxScroll, columnContentHeight, content -> {
                buildColumn(content, available, leftX, animatedViewport.y() - scroll, columnWidth, mouseX, mouseY, true, animatedViewport);
                buildColumn(content, selected, rightX, animatedViewport.y() - scroll, columnWidth, mouseX, mouseY, false, animatedViewport);
            });
        });
        renderBatch.render(tree);
    }

    @Override
    public void flush(PanelRenderBatch renderBatch) {
        renderBatch.flushAndClear();
        contentBuffer.flushAndClear();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0 || !bounds.contains(event.x(), event.y())) {
            return false;
        }
        if (hoveredAdd != null) {
            setting.add(hoveredAdd);
            return true;
        }
        if (hoveredRemove != null) {
            setting.remove(hoveredRemove);
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!query.isEmpty()) {
                    query = query.substring(0, query.length() - 1);
                    scroll = 0.0f;
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                query = "";
                scroll = 0.0f;
                yield true;
            }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!event.isAllowedChatCharacter() || query.length() >= MAX_QUERY_LENGTH) {
            return false;
        }
        query += event.codepointAsString();
        scroll = 0.0f;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!getViewport(bounds.y()).contains(mouseX, mouseY) || maxScroll <= 0.0f) {
            return false;
        }
        scroll = Mth.clamp(scroll - (float) scrollY * 24.0f, 0.0f, maxScroll);
        return true;
    }

    private void buildColumn(PanelUiTree.Scope scope, List<Block> blocks, float columnX, float startY, float columnWidth,
                             int mouseX, int mouseY, boolean addColumn, PanelLayout.Rect viewport) {
        for (int i = 0; i < blocks.size(); i++) {
            Block block = blocks.get(i);
            float rowY = startY + i * (ROW_HEIGHT + ROW_GAP);
            if (rowY + ROW_HEIGHT < viewport.y() || rowY > viewport.bottom()) {
                continue;
            }
            PanelLayout.Rect rowBounds = new PanelLayout.Rect(columnX, rowY, columnWidth, ROW_HEIGHT);
            boolean hovered = rowBounds.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY);
            if (hovered) {
                if (addColumn) {
                    hoveredAdd = block;
                } else {
                    hoveredRemove = block;
                }
            }

            Color background = addColumn
                    ? MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, hovered ? 1.0f : 0.0f)
                    : MD3Theme.lerp(MD3Theme.SECONDARY_CONTAINER, MD3Theme.PRIMARY_CONTAINER, hovered ? 0.45f : 0.0f);
            Color text = addColumn ? (hovered ? MD3Theme.TEXT_PRIMARY : MD3Theme.TEXT_SECONDARY) : MD3Theme.ON_SECONDARY_CONTAINER;
            scope.roundRect(rowBounds.x(), rowBounds.y(), rowBounds.width(), rowBounds.height(), MD3Theme.CONTROL_RADIUS, background);
            String name = trim(BlockRegistryUtils.displayName(block), 0.50f, rowBounds.width() - 24.0f);
            scope.text(name, rowBounds.x() + 6.0f, centeredTextY(rowBounds.y(), rowBounds.height(), 0.50f), 0.50f, text);
            scope.text(addColumn ? "+" : "-", rowBounds.right() - 12.0f,
                    centeredTextY(rowBounds.y(), rowBounds.height(), 0.54f), 0.54f, text);
        }
    }

    private List<Block> filteredAvailable() {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<Block> result = new ArrayList<>();
        for (Block block : allBlocks) {
            if (setting.contains(block)) {
                continue;
            }
            if (needle.isEmpty() || BlockRegistryUtils.searchText(block).contains(needle)) {
                result.add(block);
            }
        }
        return result;
    }

    private List<Block> filteredSelected() {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<Block> result = new ArrayList<>();
        for (Block block : setting.getValue()) {
            if (needle.isEmpty() || BlockRegistryUtils.searchText(block).contains(needle)) {
                result.add(block);
            }
        }
        return result;
    }

    private PanelLayout.Rect getSearchBounds(float popupY) {
        return new PanelLayout.Rect(bounds.x() + PADDING, popupY + TITLE_HEIGHT + 10.0f,
                bounds.width() - PADDING * 2.0f, SEARCH_HEIGHT);
    }

    private PanelLayout.Rect getViewport() {
        return getViewport(bounds.y());
    }

    private PanelLayout.Rect getViewport(float popupY) {
        float y = popupY + TITLE_HEIGHT + SEARCH_HEIGHT + HEADER_HEIGHT + 18.0f;
        return new PanelLayout.Rect(bounds.x() + PADDING, y, bounds.width() - PADDING * 2.0f, bounds.bottom() - y - PADDING);
    }

    private String trim(String value, float scale, float maxWidth) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int maxChars = Math.max(3, (int) (maxWidth / (5.0f * scale)));
        return value.length() <= maxChars ? value : value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private float centeredTextY(float boxY, float boxHeight, float scale) {
        return boxY + (boxHeight - textRenderer.getLineHeight(scale)) * 0.5f;
    }

}
