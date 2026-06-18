package com.github.epsilon.gui.panel.panel;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.renderers.RectRenderer;
import com.github.epsilon.graphics.renderers.RoundRectRenderer;
import com.github.epsilon.graphics.renderers.ShadowRenderer;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelUiCompiler;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.PanelState;
import com.github.epsilon.gui.panel.adapter.ModuleViewModel;
import com.github.epsilon.gui.panel.component.ModuleRow;
import com.github.epsilon.gui.panel.utils.*;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.sound.SoundKey;
import com.github.epsilon.modules.Module;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 模块列表面板。
 * <p>
 * 负责渲染分类下的模块列表、搜索框、滚动视口与模块行缓存，
 * 并维护与列表内容相关的输入状态、滚动状态和重建签名。
 */
public class ModuleListPanel {

    protected final PanelState state;
    private final RoundRectRenderer roundRectRenderer;
    private final RectRenderer rectRenderer;
    private final TextRenderer textRenderer;
    private final PanelContentBuffer contentBuffer = new PanelContentBuffer();
    private final PanelContentInvalidationState contentState = new PanelContentInvalidationState();
    private PanelLayout.Rect bounds;
    private int guiHeight;
    private final List<ModuleRow> rows = new ArrayList<>();
    private final Map<Module, Animation> hoverAnimations = new HashMap<>();
    private final Map<Module, Animation> selectionAnimations = new HashMap<>();
    private final Map<Module, Animation> toggleAnimations = new HashMap<>();
    private final Map<Module, Animation> toggleHoverAnimations = new HashMap<>();
    private float lastModuleScroll = Float.NaN;
    private String lastSearchQuery = "";
    private boolean lastSearchFocused;
    private CategorySnapshot lastCategorySnapshot;
    private String lastSelectedModuleName = "";
    private final Animation searchHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private final Animation searchFocusAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();
    private float scrollVelocity = 0;
    private boolean searchFocused;
    private int searchCursorIndex;
    private long lastContentSignature = Long.MIN_VALUE;

    private static final TranslateComponent searchComponent = EpsilonTranslateComponent.create("gui", "search");
    private static final TranslateComponent modulesComponent = EpsilonTranslateComponent.create("gui", "modules");

    public ModuleListPanel(PanelState state, RoundRectRenderer roundRectRenderer, RectRenderer rectRenderer, ShadowRenderer shadowRenderer, TextRenderer textRenderer) {
        this.state = state;
        this.roundRectRenderer = roundRectRenderer;
        this.rectRenderer = rectRenderer;
        this.textRenderer = textRenderer;
        this.searchHoverAnimation.setStartValue(0.0f);
        this.searchFocusAnimation.setStartValue(0.0f);
    }

    /**
     * 提取并编译模块列表面板当前帧的 UI。
     * <p>
     * 面板标题与搜索框会直接写入主批次；滚动列表内容则写入独立的 viewport 缓冲，
     * 并在之后的统一 flush 阶段输出。
     */
    public void render(GuiGraphicsExtractor GuiGraphicsExtractor, PanelLayout.Rect bounds, int mouseX, int mouseY, float partialTick) {
        this.bounds = bounds;
        this.guiHeight = GuiGraphicsExtractor.guiHeight();

        if (Math.abs(scrollVelocity) > 0.01f) {
            state.scrollModules(scrollVelocity * partialTick);
            scrollVelocity *= 0.86f;
            if (Math.abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0;
            }
            markDirty();
        }

        PanelLayout.Rect viewport = getViewport();
        List<Module> modules = state.getVisibleModules();
        float contentHeight = modules.size() * (ModuleRow.HEIGHT + MD3Theme.ROW_GAP);
        state.setMaxModuleScroll(contentHeight - viewport.height());
        float maxModuleScroll = Math.max(0, contentHeight - viewport.height());
        boolean hasScrollBar = maxModuleScroll > 0;
        float rowWidth = hasScrollBar ? viewport.width() - ScrollBarUtils.TOTAL_WIDTH : viewport.width();
        long contentSignature = buildContentSignature(modules);
        boolean rebuildContent = shouldRebuildContent(bounds, mouseX, mouseY, modules, GuiGraphicsExtractor.guiHeight(), contentSignature);

        if (rebuildContent) {
            rows.clear();
            contentBuffer.clear();
            contentState.beginRebuild();
        }

        PanelUiTree tree = PanelUiTree.build(scope -> {
            scope.text(state.getSelectedCategory().getName(), bounds.x() + MD3Theme.PANEL_TITLE_INSET, bounds.y() + 10.0f, 0.78f, MD3Theme.TEXT_PRIMARY);
            scope.text(modulesComponent.getTranslatedName(), bounds.x() + MD3Theme.PANEL_TITLE_INSET, bounds.y() + 21.0f, 0.56f, MD3Theme.TEXT_SECONDARY);
            buildSearchField(scope, mouseX, mouseY);
            scope.viewport(contentBuffer, viewport, guiHeight, state.getModuleScroll(), maxModuleScroll, contentHeight, content -> {
                if (!rebuildContent) {
                    return;
                }
                float y = viewport.y() - state.getModuleScroll();
                for (Module module : modules) {
                    ModuleRow row = new ModuleRow(ModuleViewModel.from(module), new PanelLayout.Rect(viewport.x(), y, rowWidth, ModuleRow.HEIGHT));
                    rows.add(row);
                    Animation hoverAnimation = hoverAnimations.computeIfAbsent(module, ignored -> new Animation(Easing.EASE_OUT_CUBIC, 120L));
                    Animation selectionAnimation = selectionAnimations.computeIfAbsent(module, ignored -> new Animation(Easing.EASE_OUT_CUBIC, 160L));
                    Animation toggleAnimation = toggleAnimations.computeIfAbsent(module, ignored -> new Animation(Easing.EASE_OUT_ELASTIC, 620L));
                    Animation toggleHoverAnimation = toggleHoverAnimations.computeIfAbsent(module, ignored -> new Animation(Easing.EASE_OUT_CUBIC, 120L));
                    hoverAnimation.run(row.getBounds().contains(mouseX, mouseY) ? 1.0f : 0.0f);
                    selectionAnimation.run(state.getSelectedModule() == module ? 1.0f : 0.0f);
                    toggleAnimation.run(module.isEnabled() ? 1.0f : 0.0f);
                    toggleHoverAnimation.run(row.getToggleBounds().contains(mouseX, mouseY) ? 1.0f : 0.0f);
                    boolean marqueeActive = row.hasOverflowingKeybind(textRenderer);
                    contentState.noteAnimation(!hoverAnimation.isFinished()
                            || !selectionAnimation.isFinished()
                            || !toggleAnimation.isFinished()
                            || !toggleHoverAnimation.isFinished()
                            || marqueeActive);
                    row.buildUi(content, textRenderer, hoverAnimation.getValue(), selectionAnimation.getValue(), toggleAnimation.getValue(), toggleHoverAnimation.getValue());
                    y += ModuleRow.HEIGHT + MD3Theme.ROW_GAP;
                }
            });
        });
        PanelUiCompiler.render(tree, roundRectRenderer, rectRenderer, textRenderer);

        if (rebuildContent) {
            rememberSnapshot(bounds, mouseX, mouseY, modules, GuiGraphicsExtractor.guiHeight(), contentSignature);
        }
    }

    /**
     * 输出并清空列表视口缓冲中的内容。
     */
    public void flushContent() {
        contentBuffer.flush();
    }

    /**
     * 将列表内容标记为脏，以便在下次渲染时触发重建。
     */
    public void markDirty() {
        contentState.markDirty();
    }

    /**
     * 返回列表内容是否仍包含未结束的动画。
     */
    public boolean hasActiveAnimations() {
        return contentState.hasActiveAnimations();
    }

    /**
     * 处理列表区域中的点击事件。
     * <p>
     * 该方法会优先处理滚动条拖拽，其次处理搜索框聚焦，最后处理模块行选择与启用切换。
     */
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (bounds == null || event.button() != 0) {
            return false;
        }
        scrollVelocity = 0;
        // Scrollbar drag
        PanelLayout.Rect viewport = getViewport();
        float maxScroll = state.getMaxModuleScroll();
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), viewport, state.getModuleScroll(), maxScroll)) {
            float newScroll = scrollBarDrag.mouseDragged(event.y(), viewport, maxScroll);
            if (newScroll >= 0) {
                state.setModuleScroll(newScroll);
            }
            markDirty();
            return true;
        }
        PanelLayout.Rect searchBounds = getSearchBounds();
        if (searchBounds.contains(event.x(), event.y())) {
            searchFocused = true;
            searchCursorIndex = state.getSearchQuery().length();
            IMEFocusHelper.activate();
            markDirty();
            return true;
        }
        for (ModuleRow row : rows) {
            if (!row.getBounds().contains(event.x(), event.y())) {
                continue;
            }
            if (row.getToggleBounds().contains(event.x(), event.y())) {
                row.getModule().module().toggle();
                Managers.SOUND.playInUi(row.getModule().module().isEnabled() ? SoundKey.SETTINGS_OPEN : SoundKey.SETTINGS_CLOSE);
            } else {
                state.setSelectedModule(row.getModule().module());
            }
            markDirty();
            return true;
        }
        return false;
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        if (scrollBarDrag.mouseReleased()) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (scrollBarDrag.isDragging()) {
            PanelLayout.Rect viewport = getViewport();
            float newScroll = scrollBarDrag.mouseDragged(event.y(), viewport, state.getMaxModuleScroll());
            if (newScroll >= 0) {
                state.setModuleScroll(newScroll);
            }
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * 当鼠标位于列表视口内时，处理滚轮滚动。
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        PanelLayout.Rect viewport = getViewport();
        if (bounds != null && viewport.contains(mouseX, mouseY)) {
            scrollVelocity -= (float) scrollY * 24f;
            markDirty();
            return true;
        }
        return false;
    }

    /**
     * 处理搜索框处于焦点状态时的键盘事件。
     */
    public boolean keyPressed(KeyEvent event) {
        if (!searchFocused) {
            return false;
        }
        String query = state.getSearchQuery();
        return switch (event.key()) {
            case 257, 335 -> true;
            case 256 -> {
                searchFocused = false;
                IMEFocusHelper.deactivate();
                yield true;
            }
            case 259 -> {
                if (searchCursorIndex > 0 && !query.isEmpty()) {
                    state.setSearchQuery(query.substring(0, searchCursorIndex - 1) + query.substring(searchCursorIndex));
                    searchCursorIndex--;
                    markDirty();
                }
                yield true;
            }
            case 261 -> {
                if (searchCursorIndex < query.length()) {
                    state.setSearchQuery(query.substring(0, searchCursorIndex) + query.substring(searchCursorIndex + 1));
                    markDirty();
                }
                yield true;
            }
            case 263 -> {
                searchCursorIndex = Math.max(0, searchCursorIndex - 1);
                markDirty();
                yield true;
            }
            case 262 -> {
                searchCursorIndex = Math.min(state.getSearchQuery().length(), searchCursorIndex + 1);
                markDirty();
                yield true;
            }
            default -> false;
        };
    }

    /**
     * 处理搜索框的字符输入。
     */
    public boolean charTyped(CharacterEvent event) {
        if (!searchFocused || !event.isAllowedChatCharacter()) {
            return false;
        }
        String query = state.getSearchQuery();
        String typed = event.codepointAsString();
        state.setSearchQuery(query.substring(0, searchCursorIndex) + typed + query.substring(searchCursorIndex));
        searchCursorIndex++;
        markDirty();
        return true;
    }

    /**
     * 处理来自面板外层的全局点击通知。
     * <p>
     * 若点击位置不在搜索框内，则会取消搜索框焦点。
     */
    public void handleGlobalClick(double mouseX, double mouseY) {
        if (bounds == null) {
            return;
        }
        if (!getSearchBounds().contains(mouseX, mouseY)) {
            searchFocused = false;
            IMEFocusHelper.deactivate();
            markDirty();
        }
    }

    private boolean shouldRebuildContent(PanelLayout.Rect bounds, int mouseX, int mouseY, List<Module> modules, int currentGuiHeight, long contentSignature) {
        if (contentState.needsRebuild(bounds, mouseX, mouseY, currentGuiHeight, contentSignature)) {
            return true;
        }
        if (Float.compare(lastModuleScroll, state.getModuleScroll()) != 0) {
            return true;
        }
        if (!Objects.equals(lastSearchQuery, state.getSearchQuery()) || lastSearchFocused != searchFocused) {
            return true;
        }
        String selectedModuleName = state.getSelectedModule() == null ? "" : state.getSelectedModule().getName();
        if (!Objects.equals(lastSelectedModuleName, selectedModuleName)) {
            return true;
        }
        if (!Objects.equals(lastCategorySnapshot, CategorySnapshot.of(state.getSelectedCategory().name(), modules))) {
            return true;
        }
        return lastContentSignature != contentSignature;
    }

    private void rememberSnapshot(PanelLayout.Rect bounds, int mouseX, int mouseY, List<Module> modules, int currentGuiHeight, long contentSignature) {
        contentState.rememberSnapshot(bounds, mouseX, mouseY, currentGuiHeight, contentSignature);
        lastModuleScroll = state.getModuleScroll();
        lastSearchQuery = state.getSearchQuery();
        lastSearchFocused = searchFocused;
        lastCategorySnapshot = CategorySnapshot.of(state.getSelectedCategory().name(), modules);
        lastSelectedModuleName = state.getSelectedModule() == null ? "" : state.getSelectedModule().getName();
        lastContentSignature = contentSignature;
    }

    private long buildContentSignature(List<Module> modules) {
        long signature = 17L;
        signature = signature * 31L + TranslateHolder.INSTANCE.getRevision();
        signature = signature * 31L + state.getSelectedCategory().name().hashCode();
        signature = signature * 31L + state.getSearchQuery().hashCode();
        signature = signature * 31L + (searchFocused ? 1 : 0);
        signature = signature * 31L + (state.getSelectedModule() == null ? 0 : state.getSelectedModule().getName().hashCode());
        signature = signature * 31L + Float.floatToIntBits(state.getModuleScroll());
        for (Module module : modules) {
            signature = signature * 31L + module.getName().hashCode();
            signature = signature * 31L + module.getKeyBind();
            signature = signature * 31L + (module.isEnabled() ? 1 : 0);
        }
        return signature;
    }

    private record CategorySnapshot(String categoryName, List<String> moduleIds) {
        private static CategorySnapshot of(String categoryName, List<Module> modules) {
            List<String> ids = modules.stream().map(Module::getName).toList();
            return new CategorySnapshot(categoryName, ids);
        }
    }

    private PanelLayout.Rect getViewport() {
        return new PanelLayout.Rect(bounds.x() + MD3Theme.PANEL_VIEWPORT_INSET, bounds.y() + 34.0f, bounds.width() - MD3Theme.PANEL_VIEWPORT_INSET * 2.0f, bounds.height() - 40.0f);
    }

    private PanelLayout.Rect getSearchBounds() {
        return new PanelLayout.Rect(bounds.right() - MD3Theme.PANEL_TITLE_INSET - 76.0f, bounds.y() + 8.0f, 76.0f, 18.0f);
    }

    private void buildSearchField(PanelUiTree.Scope scope, int mouseX, int mouseY) {
        PanelLayout.Rect searchBounds = getSearchBounds();
        float hoverProgress = scope.animate(searchHoverAnimation, searchBounds.contains(mouseX, mouseY));
        float focusProgress = scope.animate(searchFocusAnimation, searchFocused);
        float fieldHover = Math.max(hoverProgress, focusProgress * 0.85f);

        String query = state.getSearchQuery();
        boolean showPlaceholder = query.isEmpty() && !searchFocused;
        String display = showPlaceholder ? searchComponent.getTranslatedName() : query;
        float scale = 0.52f;
        Color textColor = showPlaceholder
                ? MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.filledFieldContent(searchFocused), focusProgress)
                : MD3Theme.filledFieldContent(searchFocused);
        scope.input(searchBounds, searchFocused, fieldHover,
                8.0f, display, scale, textColor,
                searchFocused ? searchCursorIndex : null, searchFocused ? MD3Theme.filledFieldCaret(true) : null,
                null, 0.0f, null);

        if (searchFocused) {
            float textY = searchBounds.y() + (searchBounds.height() - textRenderer.getHeight(scale)) / 2.0f - 1.0f;
            float textX = searchBounds.x() + 8.0f;
            float caretX = textX + textRenderer.getWidth(query.substring(0, Math.min(searchCursorIndex, query.length())), scale);
            IMEFocusHelper.updateCursorPos(caretX, textY);
        }
    }
}
