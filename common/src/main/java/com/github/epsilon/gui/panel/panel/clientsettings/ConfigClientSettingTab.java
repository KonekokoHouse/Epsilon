package com.github.epsilon.gui.panel.panel.clientsettings;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.renderers.TextRenderer;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.PanelState;
import com.github.epsilon.gui.panel.popup.ConfirmActionPopup;
import com.github.epsilon.gui.panel.popup.MessagePopup;
import com.github.epsilon.gui.panel.popup.PanelPopupHost;
import com.github.epsilon.gui.panel.utils.PanelContentBuffer;
import com.github.epsilon.gui.panel.utils.PanelContentInvalidationState;
import com.github.epsilon.gui.panel.utils.ScrollBarDragState;
import com.github.epsilon.gui.panel.utils.ScrollBarUtils;
import com.github.epsilon.holders.ConfigHolder;
import com.github.epsilon.holders.TranslateHolder;
import com.github.epsilon.utils.client.ConfigFolderOpener;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.function.Supplier;

public class ConfigClientSettingTab implements ClientSettingTabView {

    private static final TranslateComponent inputPlaceholderComponent = EpsilonTranslateComponent.create("gui", "config.input.placeholder");
    private static final TranslateComponent currentComponent = EpsilonTranslateComponent.create("gui", "config.current");
    private static final TranslateComponent switchHintComponent = EpsilonTranslateComponent.create("gui", "config.switch_hint");
    private static final TranslateComponent emptyComponent = EpsilonTranslateComponent.create("gui", "config.empty");
    private static final TranslateComponent saveAsComponent = EpsilonTranslateComponent.create("gui", "config.action.saveas");
    private static final TranslateComponent reloadComponent = EpsilonTranslateComponent.create("gui", "config.action.reload");
    private static final TranslateComponent exportComponent = EpsilonTranslateComponent.create("gui", "config.action.export");
    private static final TranslateComponent importComponent = EpsilonTranslateComponent.create("gui", "config.action.import");
    private static final TranslateComponent newComponent = EpsilonTranslateComponent.create("gui", "config.action.new");
    private static final TranslateComponent openFolderComponent = EpsilonTranslateComponent.create("gui", "config.action.open_folder");
    private static final TranslateComponent deleteConfirmTitleComponent = EpsilonTranslateComponent.create("gui", "config.delete.confirm.title");
    private static final TranslateComponent deleteConfirmMessageComponent = EpsilonTranslateComponent.create("gui", "config.delete.confirm.message");
    private static final TranslateComponent deleteConfirmConfirmComponent = EpsilonTranslateComponent.create("gui", "config.delete.confirm.confirm");
    private static final TranslateComponent deleteConfirmCancelComponent = EpsilonTranslateComponent.create("gui", "config.delete.confirm.cancel");
    private static final TranslateComponent errorTitleComponent = EpsilonTranslateComponent.create("gui", "config.error.title");
    private static final TranslateComponent errorOkComponent = EpsilonTranslateComponent.create("gui", "config.error.ok");
    private static final TranslateComponent saveErrorComponent = EpsilonTranslateComponent.create("gui", "config.error.save");
    private static final TranslateComponent reloadErrorComponent = EpsilonTranslateComponent.create("gui", "config.error.reload");
    private static final TranslateComponent exportErrorComponent = EpsilonTranslateComponent.create("gui", "config.error.export");
    private static final TranslateComponent importErrorComponent = EpsilonTranslateComponent.create("gui", "config.error.import");
    private static final TranslateComponent openFolderErrorComponent = EpsilonTranslateComponent.create("gui", "config.error.open_folder");
    private static final TranslateComponent switchErrorComponent = EpsilonTranslateComponent.create("gui", "config.error.switch");
    private static final TranslateComponent deleteErrorComponent = EpsilonTranslateComponent.create("gui", "config.error.delete");
    private static final TranslateComponent deleteLastErrorComponent = EpsilonTranslateComponent.create("gui", "config.error.delete_last");
    private static final TranslateComponent exportSuccessTitleComponent = EpsilonTranslateComponent.create("gui", "config.export.success.title");
    private static final TranslateComponent exportSuccessMessageComponent = EpsilonTranslateComponent.create("gui", "config.export.success.message");
    private static final float ROW_HEIGHT = 36.0f;
    private static final float FIELD_HEIGHT = 28.0f;
    private static final float BUTTON_HEIGHT = 26.0f;
    private static final float SECTION_GAP = 6.0f;
    private static final float FIELD_SCALE = 0.78f;
    private static final int MAX_INPUT_LENGTH = 200;

    private final PanelState state;
    private final PanelPopupHost popupHost;
    private final TextRenderer textRenderer;
    private final PanelContentBuffer contentBuffer = new PanelContentBuffer();
    private final PanelContentInvalidationState contentState = new PanelContentInvalidationState();
    private final ScrollBarDragState scrollBarDrag = new ScrollBarDragState();
    private final ClientSettingTextField inputField = new ClientSettingTextField(MAX_INPUT_LENGTH);
    private final Map<String, Animation> rowHoverAnimations = new HashMap<>();
    private final Map<String, Animation> deleteHoverAnimations = new HashMap<>();
    private final Map<String, Animation> buttonHoverAnimations = new HashMap<>();
    private final List<ConfigRowEntry> rowEntries = new ArrayList<>();

    private PanelLayout.Rect bounds;
    private float lastScroll = Float.NaN;
    private List<String> lastConfigList = List.of();
    private String lastActiveConfig = "";
    private long lastContentSignature = Long.MIN_VALUE;
    private float scrollVelocity = 0;

    public ConfigClientSettingTab(PanelState state, TextRenderer textRenderer, PanelPopupHost popupHost) {
        this.state = state;
        this.popupHost = popupHost;
        this.textRenderer = textRenderer;
    }

    @Override
    public void render(GuiGraphicsExtractor guiGraphics, PanelRenderBatch renderBatch, PanelLayout.Rect bounds, int mouseX, int mouseY, float partialTick) {
        this.bounds = bounds;

        if (Math.abs(scrollVelocity) > 0.01f) {
            state.scrollConfig(scrollVelocity * partialTick);
            scrollVelocity *= 0.86f;
            if (Math.abs(scrollVelocity) < 0.3f) {
                scrollVelocity = 0;
            }
            markDirty();
        }

        List<String> configs = ConfigHolder.INSTANCE.listConfigs();
        String activeConfig = ConfigHolder.INSTANCE.getActiveConfigName();
        PanelLayout.Rect inputSection = getInputSectionBounds(bounds);
        PanelLayout.Rect listViewport = getListViewport(bounds);
        float contentHeight = configs.size() * (ROW_HEIGHT + MD3Theme.ROW_GAP);
        state.setMaxConfigScroll(contentHeight - listViewport.height());
        float maxScroll = Math.max(0.0f, contentHeight - listViewport.height());
        boolean hasScrollBar = maxScroll > 0.0f;
        float rowWidth = hasScrollBar ? listViewport.width() - ScrollBarUtils.TOTAL_WIDTH : listViewport.width();
        long contentSignature = buildContentSignature(configs, activeConfig);
        boolean rebuildContent = shouldRebuild(listViewport, mouseX, mouseY, configs, activeConfig, guiGraphics.guiHeight(), contentSignature);

        if (rebuildContent) {
            contentBuffer.clear();
            contentState.beginRebuild();
            rowEntries.clear();
            rowHoverAnimations.keySet().removeIf(name -> !configs.contains(name));
            deleteHoverAnimations.keySet().removeIf(name -> !configs.contains(name));
        }

        PanelUiTree tree = PanelUiTree.build(scope -> {
            inputField.buildUi(scope, getInputFieldBounds(inputSection), mouseX, mouseY, textRenderer,
                    inputPlaceholderComponent.getTranslatedName(), FIELD_SCALE, null);
            for (ActionButton button : getActionButtons(inputSection)) {
                buildActionButton(scope, button, mouseX, mouseY);
            }
            scope.viewport(contentBuffer, listViewport, guiGraphics.guiHeight(), state.getConfigScroll(), maxScroll, contentHeight, content -> {
                if (!rebuildContent) {
                    return;
                }
                float rowY = listViewport.y() - state.getConfigScroll();
                for (String configName : configs) {
                    PanelLayout.Rect rowBounds = new PanelLayout.Rect(listViewport.x(), rowY, rowWidth, ROW_HEIGHT);
                    PanelLayout.Rect deleteBounds = getDeleteButtonBounds(rowBounds);
                    rowEntries.add(new ConfigRowEntry(configName, rowBounds, deleteBounds));

                    Animation rowHover = rowHoverAnimations.computeIfAbsent(configName, ignored -> createAnimation());
                    Animation deleteHover = deleteHoverAnimations.computeIfAbsent(configName, ignored -> createAnimation());
                    rowHover.run(rowBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f);
                    deleteHover.run(deleteBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f);
                    contentState.noteAnimation(!rowHover.isFinished() || !deleteHover.isFinished());

                    buildConfigRow(content, configName, activeConfig, rowBounds, deleteBounds, rowHover.getValue(), deleteHover.getValue());
                    rowY += ROW_HEIGHT + MD3Theme.ROW_GAP;
                }

                if (configs.isEmpty()) {
                    float hintScale = 0.58f;
                    String hint = emptyComponent.getTranslatedName();
                    float hintWidth = textRenderer.getWidth(hint, hintScale);
                    float hintX = listViewport.x() + (listViewport.width() - hintWidth) / 2.0f;
                    float hintY = listViewport.y() + listViewport.height() / 2.0f - textRenderer.getHeight(hintScale) / 2.0f;
                    content.text(hint, hintX, hintY, hintScale, MD3Theme.TEXT_MUTED);
                }
            });
        });
        renderBatch.render(tree);

        if (rebuildContent) {
            rememberSnapshot(listViewport, mouseX, mouseY, configs, activeConfig, guiGraphics.guiHeight(), contentSignature);
        }
    }

    @Override
    public void flushContent() {
        contentBuffer.flush();
    }

    @Override
    public void markDirty() {
        contentState.markDirty();
    }

    @Override
    public boolean hasActiveAnimations() {
        boolean rowAnimations = rowHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished())
                || deleteHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished());
        boolean buttonAnimations = buttonHoverAnimations.values().stream().anyMatch(animation -> !animation.isFinished());
        return contentState.hasActiveAnimations()
                || rowAnimations
                || buttonAnimations
                || inputField.hasActiveAnimations();
    }

    @Override
    public boolean consumesHover(int mouseX, int mouseY) {
        return popupHost.getActivePopup() != null && popupHost.getActivePopup().getBounds().contains(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (bounds == null || event.button() != 0) {
            return false;
        }

        scrollVelocity = 0;

        PanelLayout.Rect listViewport = getListViewport(bounds);
        float maxScroll = state.getMaxConfigScroll();
        if (scrollBarDrag.mouseClicked(event.x(), event.y(), listViewport, state.getConfigScroll(), maxScroll)) {
            float newScroll = scrollBarDrag.mouseDragged(event.y(), listViewport, maxScroll);
            if (newScroll >= 0.0f) {
                state.setConfigScroll(newScroll);
            }
            markDirty();
            return true;
        }

        PanelLayout.Rect inputSection = getInputSectionBounds(bounds);
        PanelLayout.Rect inputBounds = getInputFieldBounds(inputSection);
        if (inputBounds.contains(event.x(), event.y())) {
            inputField.focusIfContains(inputBounds, event.x(), event.y());
            markDirty();
            return true;
        }

        inputField.blur();

        for (ActionButton button : getActionButtons(inputSection)) {
            if (button.bounds().contains(event.x(), event.y())) {
                handleAction(button.type());
                markDirty();
                return true;
            }
        }

        for (ConfigRowEntry entry : rowEntries) {
            if (entry.deleteBounds().contains(event.x(), event.y())) {
                openDeleteConfirmation(entry.name());
                markDirty();
                return true;
            }
            if (entry.rowBounds().contains(event.x(), event.y())) {
                trySwitchConfig(entry.name());
                markDirty();
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (scrollBarDrag.mouseReleased()) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (!scrollBarDrag.isDragging()) {
            return false;
        }
        PanelLayout.Rect listViewport = getListViewport(bounds);
        float newScroll = scrollBarDrag.mouseDragged(event.y(), listViewport, state.getMaxConfigScroll());
        if (newScroll >= 0.0f) {
            state.setConfigScroll(newScroll);
        }
        markDirty();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bounds == null) {
            return false;
        }
        PanelLayout.Rect listViewport = getListViewport(bounds);
        if (listViewport.contains(mouseX, mouseY)) {
            scrollVelocity -= (float) scrollY * 24.0f;
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE && inputField.isFocused()) {
            inputField.blur();
            markDirty();
            return true;
        }
        if (inputField.keyPressed(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (inputField.charTyped(event)) {
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public void onActivated() {
        if (inputField.getText().isBlank()) {
            inputField.setText(ConfigHolder.INSTANCE.getActiveConfigName());
            inputField.setCursorToEnd();
        }
        markDirty();
    }

    @Override
    public void onDeactivated() {
        scrollBarDrag.reset();
        scrollVelocity = 0;
        inputField.blur();
        markDirty();
    }

    private void buildActionButton(PanelUiTree.Scope scope, ActionButton button, int mouseX, int mouseY) {
        Animation hoverAnimation = buttonHoverAnimations.computeIfAbsent(button.type().name(), ignored -> createAnimation());
        boolean hovered = button.bounds().contains(mouseX, mouseY);
        float hover = scope.animate(hoverAnimation, hovered);

        Color baseColor = switch (button.type()) {
            case SAVE_AS -> MD3Theme.PRIMARY_CONTAINER;
            case RELOAD -> MD3Theme.SECONDARY_CONTAINER;
            case EXPORT, IMPORT, NEW, OPEN_FOLDER -> MD3Theme.SURFACE_CONTAINER_HIGH;
        };
        Color hoverColor = switch (button.type()) {
            case SAVE_AS -> MD3Theme.PRIMARY;
            case RELOAD -> MD3Theme.SECONDARY;
            case EXPORT, IMPORT, NEW, OPEN_FOLDER -> MD3Theme.SURFACE_CONTAINER_HIGHEST;
        };
        Color textColor = switch (button.type()) {
            case SAVE_AS -> MD3Theme.ON_PRIMARY_CONTAINER;
            case RELOAD -> MD3Theme.ON_SECONDARY_CONTAINER;
            case EXPORT, IMPORT, NEW, OPEN_FOLDER -> MD3Theme.TEXT_PRIMARY;
        };

        scope.roundRect(button.bounds().x(), button.bounds().y(), button.bounds().width(), button.bounds().height(),
                button.bounds().height() / 2.0f, MD3Theme.lerp(baseColor, hoverColor, hover * 0.35f));

        float labelScale = 0.56f;
        float labelWidth = textRenderer.getWidth(button.label(), labelScale);
        float labelHeight = textRenderer.getHeight(labelScale);
        scope.text(button.label(),
                button.bounds().x() + (button.bounds().width() - labelWidth) / 2.0f,
                button.bounds().y() + (button.bounds().height() - labelHeight) / 2.0f,
                labelScale,
                textColor);
    }

    private void buildConfigRow(PanelUiTree.Scope scope, String configName, String activeConfig, PanelLayout.Rect rowBounds, PanelLayout.Rect deleteBounds, float hover, float deleteHover) {
        boolean active = Objects.equals(configName, activeConfig);

        Color baseColor = MD3Theme.lerp(MD3Theme.SURFACE_CONTAINER, MD3Theme.SURFACE_CONTAINER_HIGH, hover);
        Color rowColor = active ? MD3Theme.lerp(baseColor, MD3Theme.PRIMARY_CONTAINER, 0.28f) : baseColor;
        scope.roundRect(rowBounds.x(), rowBounds.y(), rowBounds.width(), rowBounds.height(), MD3Theme.CARD_RADIUS, rowColor);

        float nameScale = 0.66f;
        float subScale = 0.52f;
        float textX = rowBounds.x() + MD3Theme.ROW_CONTENT_INSET + 1.0f;
        float nameY = rowBounds.y() + 7.0f;
        scope.text(trimToWidth(configName, nameScale, rowBounds.width() - 72.0f), textX, nameY, nameScale,
                active ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY);

        String subtitle = active ? currentComponent.getTranslatedName() : switchHintComponent.getTranslatedName();
        Color subtitleColor = active ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_MUTED;
        scope.text(subtitle, textX, nameY + 12.0f, subScale, subtitleColor);

        if (active) {
            String chipText = currentComponent.getTranslatedName();
            float chipScale = 0.48f;
            float chipWidth = textRenderer.getWidth(chipText, chipScale) + 10.0f;
            float chipHeight = 14.0f;
            float chipX = deleteBounds.x() - chipWidth - 6.0f;
            float chipY = rowBounds.y() + (rowBounds.height() - chipHeight) / 2.0f;
            scope.roundRect(chipX, chipY, chipWidth, chipHeight, chipHeight / 2.0f, MD3Theme.PRIMARY);
            scope.text(chipText,
                    chipX + (chipWidth - textRenderer.getWidth(chipText, chipScale)) / 2.0f,
                    chipY + (chipHeight - textRenderer.getHeight(chipScale)) / 2.0f,
                    chipScale,
                    MD3Theme.ON_PRIMARY);
        }

        scope.roundRect(deleteBounds.x(), deleteBounds.y(), deleteBounds.width(), deleteBounds.height(),
                deleteBounds.height() / 2.0f,
                MD3Theme.lerp(MD3Theme.withAlpha(MD3Theme.ERROR, 0), MD3Theme.withAlpha(MD3Theme.ERROR, 32), deleteHover));
        float removeScale = 0.50f;
        String removeIcon = "✕";
        scope.text(removeIcon,
                deleteBounds.x() + (deleteBounds.width() - textRenderer.getWidth(removeIcon, removeScale)) / 2.0f,
                deleteBounds.y() + (deleteBounds.height() - textRenderer.getHeight(removeScale)) / 2.0f,
                removeScale,
                MD3Theme.lerp(MD3Theme.TEXT_MUTED, MD3Theme.ERROR, deleteHover));
    }

    private void handleAction(ActionButtonType action) {
        switch (action) {
            case SAVE_AS -> trySaveAs();
            case RELOAD -> tryReload();
            case EXPORT -> tryExport();
            case IMPORT -> tryImport();
            case NEW -> tryNewConfig();
            case OPEN_FOLDER -> tryOpenFolder();
        }
    }

    private void trySaveAs() {
        String targetName = inputField.getText().trim();
        if (targetName.isEmpty()) {
            return;
        }
        try {
            String savedName = ConfigHolder.INSTANCE.saveAsConfig(targetName);
            inputField.setText(savedName);
            inputField.setCursorToEnd();
            state.setConfigScroll(0.0f);
        } catch (Exception exception) {
            Constants.LOGGER.error("保存配置失败", exception);
            openErrorPopup(saveErrorComponent::getTranslatedName, exception);
        }
    }

    private void tryNewConfig() {
        String targetName = inputField.getText().trim();
        if (targetName.isEmpty()) {
            return;
        }
        try {
            String newName = ConfigHolder.INSTANCE.newDefaultConfig(targetName);
            inputField.setText(newName);
            inputField.setCursorToEnd();
            state.setConfigScroll(0.0f);
        } catch (Exception exception) {
            Constants.LOGGER.error("新建配置失败", exception);
            openErrorPopup(saveErrorComponent::getTranslatedName, exception);
        }
    }

    private void tryReload() {
        try {
            ConfigHolder.INSTANCE.reloadOrThrow();
            markDirty();
        } catch (Exception exception) {
            Constants.LOGGER.error("重载配置失败", exception);
            openErrorPopup(reloadErrorComponent::getTranslatedName, exception);
        }
    }

    private void tryExport() {
        try {
            Path exported = ConfigHolder.INSTANCE.exportActiveConfigToZip(inputField.getText());
            openExportSuccessPopup(exported);
        } catch (Exception exception) {
            Constants.LOGGER.error("导出配置失败", exception);
            openErrorPopup(exportErrorComponent::getTranslatedName, exception);
        }
    }

    private void tryImport() {
        String zipPath = inputField.getText().trim();
        if (zipPath.isEmpty()) {
            return;
        }
        try {
            String importedName = ConfigHolder.INSTANCE.importConfigFromZip(zipPath);
            inputField.setText(importedName);
            inputField.setCursorToEnd();
            state.setConfigScroll(0.0f);
        } catch (Exception exception) {
            openErrorPopup(importErrorComponent::getTranslatedName, exception);
        }
    }

    private void tryOpenFolder() {
        try {
            ConfigFolderOpener.openConfigFolder();
        } catch (Exception exception) {
            Constants.LOGGER.error("打开配置文件夹失败", exception);
            openErrorPopup(openFolderErrorComponent::getTranslatedName, exception);
        }
    }

    private void trySwitchConfig(String configName) {
        if (Objects.equals(configName, ConfigHolder.INSTANCE.getActiveConfigName())) {
            return;
        }
        try {
            ConfigHolder.INSTANCE.switchConfig(configName);
            inputField.setText(configName);
            inputField.setCursorToEnd();
        } catch (Exception exception) {
            Constants.LOGGER.error("切换配置失败", exception);
            openErrorPopup(switchErrorComponent::getTranslatedName, exception);
        }
    }

    private void tryDeleteConfig(String configName) {
        try {
            if (!ConfigHolder.INSTANCE.deleteConfig(configName)) {
                openErrorPopup(deleteErrorComponent::getTranslatedName, deleteLastErrorComponent.getTranslatedName());
                return;
            }
            if (Objects.equals(inputField.getText().trim(), configName)) {
                inputField.setText(ConfigHolder.INSTANCE.getActiveConfigName());
                inputField.setCursorToEnd();
            }
        } catch (Exception exception) {
            Constants.LOGGER.error("删除配置失败", exception);
            openErrorPopup(deleteErrorComponent::getTranslatedName, exception);
        }
    }

    private void openDeleteConfirmation(String configName) {
        float popupWidth = 198.0f;
        PanelLayout.Rect popupBounds = popupHost.getCenteredBounds(popupWidth, 82.0f);
        popupHost.open(new ConfirmActionPopup(
                popupBounds,
                deleteConfirmTitleComponent::getTranslatedName,
                deleteConfirmMessageComponent::getTranslatedName,
                trimToWidth(configName, 0.60f, popupWidth - 24.0f),
                deleteConfirmConfirmComponent::getTranslatedName,
                deleteConfirmCancelComponent::getTranslatedName,
                () -> {
                    tryDeleteConfig(configName);
                    markDirty();
                }
        ));
    }

    private void openErrorPopup(Supplier<String> actionMessageSupplier, Exception exception) {
        openErrorPopup(actionMessageSupplier, buildErrorDetail(exception));
    }

    private void openErrorPopup(Supplier<String> actionMessageSupplier, String detail) {
        float popupWidth = 220.0f;
        float popupHeight = 84.0f;
        popupHost.open(new MessagePopup(
                popupHost.getCenteredBounds(popupWidth, popupHeight),
                errorTitleComponent::getTranslatedName,
                actionMessageSupplier,
                trimToWidth(detail, 0.52f, popupWidth - 24.0f),
                errorOkComponent::getTranslatedName
        ));
    }

    private void openExportSuccessPopup(Path exported) {
        float popupWidth = 220.0f;
        float popupHeight = 84.0f;
        String detail = "";
        if (exported != null) {
            Path fileName = exported.getFileName();
            detail = fileName != null ? fileName.toString() : exported.toString();
        }
        popupHost.open(new MessagePopup(
                popupHost.getCenteredBounds(popupWidth, popupHeight),
                exportSuccessTitleComponent::getTranslatedName,
                exportSuccessMessageComponent::getTranslatedName,
                trimToWidth(detail, 0.52f, popupWidth - 24.0f),
                errorOkComponent::getTranslatedName
        ));
    }

    private String buildErrorDetail(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private boolean shouldRebuild(PanelLayout.Rect listViewport, int mouseX, int mouseY, List<String> configs, String activeConfig, int currentGuiHeight, long contentSignature) {
        if (contentState.needsRebuild(listViewport, mouseX, mouseY, currentGuiHeight, contentSignature)) {
            return true;
        }
        if (Float.compare(lastScroll, state.getConfigScroll()) != 0) {
            return true;
        }
        if (!Objects.equals(lastConfigList, configs)) {
            return true;
        }
        if (!Objects.equals(lastActiveConfig, activeConfig)) {
            return true;
        }
        return lastContentSignature != contentSignature;
    }

    private void rememberSnapshot(PanelLayout.Rect listViewport, int mouseX, int mouseY, List<String> configs, String activeConfig, int currentGuiHeight, long contentSignature) {
        contentState.rememberSnapshot(listViewport, mouseX, mouseY, currentGuiHeight, contentSignature);
        lastScroll = state.getConfigScroll();
        lastConfigList = new ArrayList<>(configs);
        lastActiveConfig = activeConfig;
        lastContentSignature = contentSignature;
    }

    private long buildContentSignature(List<String> configs, String activeConfig) {
        long signature = 17L;
        signature = signature * 31L + TranslateHolder.INSTANCE.getRevision();
        signature = signature * 31L + Float.floatToIntBits(state.getConfigScroll());
        signature = signature * 31L + activeConfig.hashCode();
        for (String config : configs) {
            signature = signature * 31L + config.hashCode();
        }
        return signature;
    }

    private PanelLayout.Rect getInputSectionBounds(PanelLayout.Rect bounds) {
        float inputHeight = FIELD_HEIGHT + BUTTON_HEIGHT * 2.0f + SECTION_GAP * 3.0f;
        return new PanelLayout.Rect(bounds.x(), bounds.bottom() - inputHeight, bounds.width(), inputHeight);
    }

    private PanelLayout.Rect getListViewport(PanelLayout.Rect bounds) {
        PanelLayout.Rect inputBounds = getInputSectionBounds(bounds);
        float y = bounds.y();
        float bottom = inputBounds.y() - SECTION_GAP;
        return new PanelLayout.Rect(bounds.x(), y, bounds.width(), Math.max(0.0f, bottom - y));
    }

    private PanelLayout.Rect getInputFieldBounds(PanelLayout.Rect inputBounds) {
        return new PanelLayout.Rect(inputBounds.x(), inputBounds.y(), inputBounds.width(), FIELD_HEIGHT);
    }

    private List<ActionButton> getActionButtons(PanelLayout.Rect inputBounds) {
        float y = getInputFieldBounds(inputBounds).bottom() + SECTION_GAP;
        float gap = 4.0f;
        float width = (inputBounds.width() - gap * 3.0f) / 4.0f;
        float secondRowWidth = (inputBounds.width() - gap) / 2.0f;
        float secondRowY = y + BUTTON_HEIGHT + SECTION_GAP;
        return List.of(
                new ActionButton(ActionButtonType.SAVE_AS, saveAsComponent.getTranslatedName(), new PanelLayout.Rect(inputBounds.x(), y, width, BUTTON_HEIGHT)),
                new ActionButton(ActionButtonType.RELOAD, reloadComponent.getTranslatedName(), new PanelLayout.Rect(inputBounds.x() + width + gap, y, width, BUTTON_HEIGHT)),
                new ActionButton(ActionButtonType.EXPORT, exportComponent.getTranslatedName(), new PanelLayout.Rect(inputBounds.x() + (width + gap) * 2.0f, y, width, BUTTON_HEIGHT)),
                new ActionButton(ActionButtonType.IMPORT, importComponent.getTranslatedName(), new PanelLayout.Rect(inputBounds.x() + (width + gap) * 3.0f, y, width, BUTTON_HEIGHT)),
                new ActionButton(ActionButtonType.NEW, newComponent.getTranslatedName(), new PanelLayout.Rect(inputBounds.x(), secondRowY, secondRowWidth, BUTTON_HEIGHT)),
                new ActionButton(ActionButtonType.OPEN_FOLDER, openFolderComponent.getTranslatedName(), new PanelLayout.Rect(inputBounds.x() + secondRowWidth + gap, secondRowY, secondRowWidth, BUTTON_HEIGHT))
        );
    }

    private PanelLayout.Rect getDeleteButtonBounds(PanelLayout.Rect rowBounds) {
        float buttonSize = 20.0f;
        return new PanelLayout.Rect(
                rowBounds.right() - MD3Theme.ROW_TRAILING_INSET - buttonSize,
                rowBounds.y() + (rowBounds.height() - buttonSize) / 2.0f,
                buttonSize,
                buttonSize
        );
    }

    private Animation createAnimation() {
        Animation animation = new Animation(Easing.EASE_OUT_CUBIC, 120L);
        animation.setStartValue(0.0f);
        return animation;
    }

    private String trimToWidth(String value, float scale, float width) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        if (textRenderer.getWidth(value, scale) <= width) {
            return value;
        }
        String ellipsis = "...";
        float ellipsisWidth = textRenderer.getWidth(ellipsis, scale);
        if (ellipsisWidth >= width) {
            return ellipsis;
        }
        for (int length = value.length() - 1; length >= 0; length--) {
            String candidate = value.substring(0, length) + ellipsis;
            if (textRenderer.getWidth(candidate, scale) <= width) {
                return candidate;
            }
        }
        return ellipsis;
    }

    private record ConfigRowEntry(String name, PanelLayout.Rect rowBounds, PanelLayout.Rect deleteBounds) {
    }

    private record ActionButton(ActionButtonType type, String label, PanelLayout.Rect bounds) {
    }

    private enum ActionButtonType {
        SAVE_AS,
        RELOAD,
        EXPORT,
        IMPORT,
        NEW,
        OPEN_FOLDER
    }

}
