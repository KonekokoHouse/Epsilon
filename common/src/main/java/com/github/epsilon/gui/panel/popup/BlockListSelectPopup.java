package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.utils.IMEFocusHelper;
import com.github.epsilon.gui.panel.utils.PanelContentBuffer;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.github.epsilon.Constants.mc;

public class BlockListSelectPopup implements PanelPopupHost.Popup {

    private static final float PADDING = 8.0f;
    private static final float TITLE_HEIGHT = 18.0f;
    private static final float SEARCH_HEIGHT = 18.0f;
    private static final float HEADER_HEIGHT = 14.0f;
    private static final float ROW_HEIGHT = 18.0f;
    private static final float ROW_GAP = 2.0f;
    private static final float COLUMN_GAP = 6.0f;
    private static final float SCROLLBAR_GUTTER = ScrollBarUtils.TOTAL_WIDTH + 1.0f;
    private static final float ITEM_PREVIEW_SIZE = 12.0f;
    private static final float ITEM_PREVIEW_GAP = 5.0f;
    private static final float SCROLL_STEP = 24.0f;
    private static final float SCROLL_DECAY = 0.86f;
    private static final float MIN_SCROLL_VELOCITY = 0.3f;
    private static final int MAX_QUERY_LENGTH = 64;

    private final PanelLayout.Rect bounds;
    private final BlockListSetting setting;
    private final List<Block> allBlocks = BlockRegistryUtils.allSelectableBlocks();
    private final PanelContentBuffer contentBuffer = new PanelContentBuffer();
    private final TextRenderer textRenderer = TextRenderer.create();
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final List<ItemPreview> itemPreviews = new ArrayList<>();
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();

    private String query = "";
    private float scroll;
    private float scrollVelocity;
    private float maxScroll;
    private Block hoveredAdd;
    private Block hoveredRemove;
    private PanelLayout.Rect lastViewport;

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
        itemPreviews.clear();
        List<Block> available = filteredAvailable();
        List<Block> selected = filteredSelected();
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
        contentBuffer.flushAndClear();
    }

    @Override
    public void extractOverlay(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (itemPreviews.isEmpty()) {
            return;
        }
        guiGraphics.nextStratum();
        if (lastViewport != null) {
            guiGraphics.enableScissor(
                    toMinecraftGuiXInt(lastViewport.x()),
                    toMinecraftGuiYInt(lastViewport.y()),
                    toMinecraftGuiXInt(lastViewport.right()),
                    toMinecraftGuiYInt(lastViewport.bottom())
            );
        }
        for (ItemPreview preview : itemPreviews) {
            drawItemPreview(guiGraphics, preview);
        }
        if (lastViewport != null) {
            guiGraphics.disableScissor();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0 || !bounds.contains(event.x(), event.y())) {
            return false;
        }
        PanelLayout.Rect viewport = lastViewport != null ? lastViewport : getViewport();
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, scroll, maxScroll)) {
            applyDraggedScroll(event.y(), viewport);
            return true;
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
    public boolean mouseReleased(MouseButtonEvent event) {
        return scrollBarDrag.mouseReleased();
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (!scrollBarDrag.isDragging()) {
            return false;
        }
        PanelLayout.Rect viewport = lastViewport != null ? lastViewport : getViewport();
        applyDraggedScroll(event.y(), viewport);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!query.isEmpty()) {
                    query = query.substring(0, query.length() - 1);
                    resetScroll();
                }
                yield true;
            }
            case GLFW.GLFW_KEY_DELETE -> {
                query = "";
                resetScroll();
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
        resetScroll();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!getViewport(bounds.y()).contains(mouseX, mouseY) || maxScroll <= 0.0f) {
            return false;
        }
        scrollVelocity -= (float) scrollY * SCROLL_STEP;
        return true;
    }

    private void updateSmoothScroll(float partialTick) {
        if (maxScroll <= 0.0f) {
            resetScroll();
            return;
        }
        if (Math.abs(scrollVelocity) <= 0.01f) {
            return;
        }
        if (partialTick <= 0.0f) {
            return;
        }
        float nextScroll = Mth.clamp(scroll + scrollVelocity * partialTick, 0.0f, maxScroll);
        if (Float.compare(nextScroll, scroll) == 0) {
            scrollVelocity = 0.0f;
            return;
        }
        scroll = nextScroll;
        scrollVelocity *= SCROLL_DECAY;
        if (Math.abs(scrollVelocity) < MIN_SCROLL_VELOCITY) {
            scrollVelocity = 0.0f;
        }
    }

    private void resetScroll() {
        scroll = 0.0f;
        scrollVelocity = 0.0f;
    }

    private void applyDraggedScroll(double mouseY, PanelLayout.Rect viewport) {
        float newScroll = scrollBarDrag.mouseDragged(mouseY, viewport, maxScroll);
        if (newScroll >= 0.0f) {
            scroll = Mth.clamp(newScroll, 0.0f, maxScroll);
            scrollVelocity = 0.0f;
        }
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
            float actionX = rowBounds.right() - 12.0f;
            float previewX = actionX - ITEM_PREVIEW_GAP - ITEM_PREVIEW_SIZE;
            float previewY = rowBounds.y() + (rowBounds.height() - ITEM_PREVIEW_SIZE) * 0.5f;
            ItemStack previewStack = createPreviewStack(block);
            if (!previewStack.isEmpty()) {
                itemPreviews.add(new ItemPreview(previewStack, previewX, previewY, ITEM_PREVIEW_SIZE));
            }

            String name = trim(BlockRegistryUtils.displayName(block), 0.50f, previewX - rowBounds.x() - 12.0f);
            scope.text(name, rowBounds.x() + 6.0f, centeredTextY(rowBounds.y(), rowBounds.height(), 0.50f), 0.50f, text);
            scope.text(addColumn ? "+" : "-", actionX,
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
        return boxY + (boxHeight - textRenderer.getHeight(scale)) * 0.5f;
    }

    private void drawItemPreview(GuiGraphicsExtractor guiGraphics, ItemPreview preview) {
        if (preview.stack().isEmpty()) {
            return;
        }
        float scale = preview.size() / 16.0f;
        float guiScale = (float) (scale * LuminRenderSystem.getGuiScale() / mc.getWindow().getGuiScale());
        float guiX = toMinecraftGuiX(preview.x());
        float guiY = toMinecraftGuiY(preview.y());
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(guiX + guiScale, guiY + guiScale);
        guiGraphics.pose().scale(guiScale, guiScale);
        guiGraphics.item(preview.stack(), 0, 0);
        guiGraphics.pose().popMatrix();
    }

    private float toMinecraftGuiX(float epsilonX) {
        return (float) LuminRenderSystem.toMinecraftGuiX(epsilonX);
    }

    private float toMinecraftGuiY(float epsilonY) {
        return (float) LuminRenderSystem.toMinecraftGuiY(epsilonY);
    }

    private int toMinecraftGuiXInt(float epsilonX) {
        return (int) Math.round(toMinecraftGuiX(epsilonX));
    }

    private int toMinecraftGuiYInt(float epsilonY) {
        return (int) Math.round(toMinecraftGuiY(epsilonY));
    }

    private ItemStack createPreviewStack(Block block) {
        Item item = block.asItem();
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        if (!item.builtInRegistryHolder().areComponentsBound()) {
            return ItemStack.EMPTY;
        }
        return item.getDefaultInstance();
    }

    private record ItemPreview(ItemStack stack, float x, float y, float size) {
    }

}
