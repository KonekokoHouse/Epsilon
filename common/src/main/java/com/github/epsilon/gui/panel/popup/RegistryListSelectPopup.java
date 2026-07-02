package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.assets.i18n.EpsilonTranslations;
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
import com.github.epsilon.settings.Setting;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.github.epsilon.Constants.mc;

public class RegistryListSelectPopup<T> implements PanelPopupHost.Popup {

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
    private static final float ITEM_PREVIEW_SIZE = 14.0f;
    private static final float ITEM_PREVIEW_GAP = 4.0f;
    private static final float CATEGORY_TAB_HEIGHT = 16.0f;
    private static final float CATEGORY_TAB_GAP = 4.0f;

    private final PanelLayout.Rect bounds;
    private final Setting<List<T>> setting;
    private final Registry<T> registry;
    private final Function<T, String> displayNameFn;
    private final Function<T, ItemStack> iconProvider;
    private final Consumer<T> addFn;
    private final Consumer<T> removeFn;
    private final List<T> allEntries;
    private final List<Category<T>> categories;
    private final PanelContentBuffer contentBuffer = new PanelContentBuffer();
    private final TextRenderer textRenderer = TextRenderer.create();
    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();
    private final List<ItemPreview> itemPreviews = new ArrayList<>();

    private String query = "";
    private int selectedCategory = -1; // -1 = all
    private float scroll;
    private float scrollVelocity;
    private float maxScroll;
    private T hoveredAdd;
    private T hoveredRemove;
    private PanelLayout.Rect lastViewport;

    public RegistryListSelectPopup(PanelLayout.Rect bounds, Setting<List<T>> setting, Registry<T> registry,
                                   Function<T, String> displayNameFn, Consumer<T> addFn, Consumer<T> removeFn) {
        this(bounds, setting, registry, displayNameFn, null, List.of(), addFn, removeFn);
    }

    public RegistryListSelectPopup(PanelLayout.Rect bounds, Setting<List<T>> setting, Registry<T> registry,
                                   Function<T, String> displayNameFn, Function<T, ItemStack> iconProvider,
                                   Consumer<T> addFn, Consumer<T> removeFn) {
        this(bounds, setting, registry, displayNameFn, iconProvider, List.of(), addFn, removeFn);
    }

    public RegistryListSelectPopup(PanelLayout.Rect bounds, Setting<List<T>> setting, Registry<T> registry,
                                   Function<T, String> displayNameFn, Function<T, ItemStack> iconProvider,
                                   List<Category<T>> categories,
                                   Consumer<T> addFn, Consumer<T> removeFn) {
        this.bounds = bounds;
        this.setting = setting;
        this.registry = registry;
        this.displayNameFn = displayNameFn;
        this.iconProvider = iconProvider;
        this.categories = categories;
        this.addFn = addFn;
        this.removeFn = removeFn;
        this.openAnimation.setStartValue(0.0f);
        this.allEntries = collectAllEntries();
    }

    private List<T> collectAllEntries() {
        List<T> entries = new ArrayList<>();
        for (T entry : registry) entries.add(entry);
        entries.sort(Comparator.comparing(e -> {
            Identifier key = registry.getKey(e);
            return key != null ? key.toString() : "";
        }));
        return entries;
    }

    @Override public PanelLayout.Rect getBounds() { return bounds; }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, PanelRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        contentBuffer.clear();
        itemPreviews.clear();
        List<T> available = filteredAvailable();
        List<T> selected = filteredSelected();
        float columnContentHeight = Math.max(available.size(), selected.size()) * (ROW_HEIGHT + ROW_GAP);

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
                String summary = setting.getValue().size() + EpsilonTranslations.Gui.LIST_SELECTED.getTranslatedName();
                popup.text(setting.getDisplayName(), PADDING, titleY, 0.68f, MD3Theme.TEXT_PRIMARY);
                popup.text(summary, animatedBounds.width() - PADDING - textRenderer.getWidth(summary, summaryScale),
                        centeredTextY(6.0f, TITLE_HEIGHT, summaryScale), summaryScale, MD3Theme.TEXT_MUTED);
                popup.input(searchBounds.relativeTo(animatedBounds), true, 1.0f, 8.0f,
                        query.isEmpty() ? EpsilonTranslations.Gui.LIST_SEARCH.getTranslatedName() : query, 0.54f,
                        query.isEmpty() ? MD3Theme.TEXT_MUTED : MD3Theme.TEXT_PRIMARY,
                        query.length(), MD3Theme.PRIMARY, null, 0.0f, null);
                IMEFocusHelper.updateCursorPos(searchBounds.x() + 8.0f, searchBounds.y() + 4.0f);

                // Category tabs
                float contentWidth = animatedViewport.width() - SCROLLBAR_GUTTER;
                float columnWidth = (contentWidth - COLUMN_GAP) / 2.0f;
                float leftX = animatedViewport.x();
                float rightX = leftX + columnWidth + COLUMN_GAP;
                float viewportY = animatedViewport.y();
                if (!categories.isEmpty()) {
                    float catY = searchBounds.bottom() + 4.0f;
                    float catTabX = leftX;
                    for (int ci = -1; ci < categories.size(); ci++) {
                        String catName = ci < 0 ? EpsilonTranslations.Gui.LIST_ALL.getTranslatedName() : categories.get(ci).name();
                        float catTextW = textRenderer.getWidth(catName, 0.44f) + 10.0f;
                        boolean catSelected = selectedCategory == ci;
                        PanelLayout.Rect catBounds = new PanelLayout.Rect(catTabX, catY, catTextW, CATEGORY_TAB_HEIGHT);
                        popup.roundRect(catBounds.x() - animatedBounds.x(), catBounds.y() - animatedBounds.y(),
                                catBounds.width(), catBounds.height(), CATEGORY_TAB_HEIGHT / 2.0f,
                                catSelected ? MD3Theme.PRIMARY : MD3Theme.SURFACE_CONTAINER_HIGH);
                        popup.text(catName,
                                catBounds.x() - animatedBounds.x() + 5.0f,
                                catBounds.y() - animatedBounds.y() + (catBounds.height() - textRenderer.getHeight(0.44f)) * 0.5f,
                                0.44f, catSelected ? MD3Theme.ON_PRIMARY : MD3Theme.TEXT_SECONDARY);
                        catTabX += catTextW + CATEGORY_TAB_GAP;
                    }
                    viewportY = catY + CATEGORY_TAB_HEIGHT + HEADER_HEIGHT + 4.0f;
                }
                final float effectiveViewportY = viewportY;

                float headerY = effectiveViewportY - HEADER_HEIGHT;
                float headerTextY = centeredTextY(headerY, HEADER_HEIGHT, 0.50f);
                popup.text(EpsilonTranslations.Gui.LIST_AVAILABLE.getTranslatedName(), leftX - animatedBounds.x() + 4.0f, headerTextY - animatedBounds.y(), 0.50f, MD3Theme.TEXT_SECONDARY);
                popup.text(EpsilonTranslations.Gui.LIST_SELECTED_HEADER.getTranslatedName(), rightX - animatedBounds.x() + 4.0f, headerTextY - animatedBounds.y(), 0.50f, MD3Theme.TEXT_SECONDARY);

                hoveredAdd = null;
                hoveredRemove = null;
                PanelLayout.Rect effectiveViewport = new PanelLayout.Rect(animatedViewport.x(), effectiveViewportY, animatedViewport.width(), animatedViewport.bottom() - effectiveViewportY);
                lastViewport = effectiveViewport;
                maxScroll = Math.max(0.0f, columnContentHeight - effectiveViewport.height());
                scroll = Mth.clamp(scroll, 0.0f, maxScroll);
                updateSmoothScroll(partialTick);
                PanelLayout.Rect localViewport = effectiveViewport.relativeTo(animatedBounds);
                popup.viewport(contentBuffer, localViewport, guiGraphics.guiHeight(), scroll, maxScroll, columnContentHeight, content -> {
                    buildColumn(content, available, leftX, effectiveViewportY - scroll, columnWidth, mouseX, mouseY, true, effectiveViewport);
                    buildColumn(content, selected, rightX, effectiveViewportY - scroll, columnWidth, mouseX, mouseY, false, effectiveViewport);
                });
            });
        });
        renderBatch.render(tree);
    }

    @Override public void flush(PanelRenderBatch renderBatch) { contentBuffer.flushAndClear(); }

    @Override
    public void extractOverlay(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (iconProvider == null || itemPreviews.isEmpty()) return;
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
        if (event.button() != 0 || !bounds.contains(event.x(), event.y())) return false;
        // Check category tab clicks
        if (!categories.isEmpty()) {
            PanelLayout.Rect searchBounds = getSearchBounds(bounds.y());
            float catY = searchBounds.bottom() + 4.0f;
            float catTabX = bounds.x() + PADDING;
            for (int ci = -1; ci < categories.size(); ci++) {
                String catName = ci < 0 ? EpsilonTranslations.Gui.LIST_ALL.getTranslatedName() : categories.get(ci).name();
                float catTextW = textRenderer.getWidth(catName, 0.44f) + 10.0f;
                if (event.x() >= catTabX && event.x() <= catTabX + catTextW
                        && event.y() >= catY && event.y() <= catY + CATEGORY_TAB_HEIGHT) {
                    selectedCategory = ci;
                    resetScroll();
                    return true;
                }
                catTabX += catTextW + CATEGORY_TAB_GAP;
            }
        }
        PanelLayout.Rect viewport = lastViewport != null ? lastViewport : getViewport();
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, scroll, maxScroll)) {
            applyDraggedScroll(event.y(), viewport);
            return true;
        }
        if (hoveredAdd != null) { addFn.accept(hoveredAdd); return true; }
        if (hoveredRemove != null) { removeFn.accept(hoveredRemove); return true; }
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) { return scrollBarDrag.mouseReleased(); }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (!scrollBarDrag.isDragging()) return false;
        applyDraggedScroll(event.y(), lastViewport != null ? lastViewport : getViewport());
        return true;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        return switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> { if (!query.isEmpty()) { query = query.substring(0, query.length() - 1); resetScroll(); } yield true; }
            case GLFW.GLFW_KEY_DELETE -> { query = ""; resetScroll(); yield true; }
            default -> false;
        };
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (query.length() >= MAX_QUERY_LENGTH) return false;
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

    private List<T> filteredAvailable() {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<T> result = new ArrayList<>();
        for (T entry : allEntries) {
            if (setting.getValue().contains(entry)) continue;
            if (selectedCategory >= 0 && selectedCategory < categories.size()) {
                if (!categories.get(selectedCategory).predicate().test(entry)) continue;
            }
            String text = displayNameFn.apply(entry).toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || text.contains(needle)) result.add(entry);
        }
        return result;
    }

    private List<T> filteredSelected() {
        String needle = query.toLowerCase(Locale.ROOT).trim();
        List<T> result = new ArrayList<>();
        for (T entry : setting.getValue()) {
            String text = displayNameFn.apply(entry).toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || text.contains(needle)) result.add(entry);
        }
        return result;
    }

    private void buildColumn(PanelUiTree.Scope scope, List<T> entries, float columnX, float startY,
                              float columnWidth, int mouseX, int mouseY, boolean addColumn, PanelLayout.Rect viewport) {
        PanelLayout.Rect origin = scope.bound();
        final boolean hasIcons = iconProvider != null;
        for (int i = 0; i < entries.size(); i++) {
            T entry = entries.get(i);
            float rowY = startY + i * (ROW_HEIGHT + ROW_GAP);
            if (rowY + ROW_HEIGHT < viewport.y() || rowY > viewport.bottom()) continue;

            PanelLayout.Rect rowBounds = new PanelLayout.Rect(columnX, rowY, columnWidth, ROW_HEIGHT);
            boolean hovered = rowBounds.contains(mouseX, mouseY) && viewport.contains(mouseX, mouseY);
            if (hovered) { if (addColumn) hoveredAdd = entry; else hoveredRemove = entry; }

            float bgHover = addColumn ? (hovered ? 1.0f : 0.0f) : (hovered ? 0.45f : 0.0f);
            String rawName = displayNameFn.apply(entry);
            float actionX = rowBounds.right() - 12.0f;
            float textMaxWidth = rowBounds.width() - 22.0f;
            if (hasIcons) {
                ItemStack previewStack = iconProvider.apply(entry);
                if (previewStack != null && !previewStack.isEmpty()) {
                    float previewX = actionX - ITEM_PREVIEW_GAP - ITEM_PREVIEW_SIZE;
                    float previewY = rowBounds.y() + (rowBounds.height() - ITEM_PREVIEW_SIZE) * 0.5f;
                    itemPreviews.add(new ItemPreview(previewStack, previewX, previewY, ITEM_PREVIEW_SIZE));
                    textMaxWidth = previewX - rowBounds.x() - 12.0f;
                }
            }
            final String display = trim(rawName, 0.50f, textMaxWidth);
            final float textY = centeredTextY(0.0f, rowBounds.height(), 0.50f);

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

    private void drawItemPreview(GuiGraphicsExtractor guiGraphics, ItemPreview preview) {
        if (preview.stack().isEmpty()) return;
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

    private record ItemPreview(ItemStack stack, float x, float y, float size) {}
    public record Category<T>(String name, Predicate<T> predicate) {}
}
