package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.gui.dropdown.widget.DropdownTextField;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.managers.ConfigManager;
import com.github.epsilon.utils.client.ConfigFolderOpener;

import java.util.List;
import java.util.Objects;

public class ConfigDropdownPanel extends AbstractDropdownPanel {

    private static final TranslateComponent titleComponent = EpsilonTranslateComponent.create("gui", "tab.config");
    private static final TranslateComponent emptyComponent = EpsilonTranslateComponent.create("gui", "config.empty");
    private static final TranslateComponent saveAsComponent = EpsilonTranslateComponent.create("gui", "config.action.saveas");
    private static final TranslateComponent reloadComponent = EpsilonTranslateComponent.create("gui", "config.action.reload");
    private static final TranslateComponent exportComponent = EpsilonTranslateComponent.create("gui", "config.action.export");
    private static final TranslateComponent importComponent = EpsilonTranslateComponent.create("gui", "config.action.import");
    private static final TranslateComponent newComponent = EpsilonTranslateComponent.create("gui", "config.action.new");
    private static final TranslateComponent openFolderComponent = EpsilonTranslateComponent.create("gui", "config.action.open_folder");
    private static final TranslateComponent savedComponent = EpsilonTranslateComponent.create("gui", "dropdown.status.saved");
    private static final TranslateComponent reloadedComponent = EpsilonTranslateComponent.create("gui", "dropdown.status.reloaded");
    private static final TranslateComponent exportedComponent = EpsilonTranslateComponent.create("gui", "dropdown.status.exported");
    private static final TranslateComponent importedComponent = EpsilonTranslateComponent.create("gui", "dropdown.status.imported");
    private static final TranslateComponent deletedComponent = EpsilonTranslateComponent.create("gui", "dropdown.status.deleted");
    private static final TranslateComponent switchedComponent = EpsilonTranslateComponent.create("gui", "dropdown.status.switched");
    private static final TranslateComponent createdComponent = EpsilonTranslateComponent.create("gui", "dropdown.status.created");
    private static final TranslateComponent errorComponent = EpsilonTranslateComponent.create("gui", "config.error.title");

    private static final float FIELD_HEIGHT = 18.0f;
    private static final float BUTTON_HEIGHT = 17.0f;
    private static final float ROW_HEIGHT = 24.0f;
    private static final float GAP = 4.0f;
    private static final float PADDING = 6.0f;

    private final DropdownTextField inputField = new DropdownTextField(160);
    private String status = "";

    public ConfigDropdownPanel(int panelIndex) {
        super("config", titleComponent, "", panelIndex);
    }

    @Override
    protected float computeContentHeight() {
        int configCount = ConfigManager.INSTANCE.listConfigs().size();
        return PADDING * 2.0f + FIELD_HEIGHT + GAP + BUTTON_HEIGHT * 3.0f + GAP * 3.0f
                + Math.max(ROW_HEIGHT, configCount * (ROW_HEIGHT + GAP))
                + (status.isEmpty() ? 0.0f : ROW_HEIGHT);
    }

    @Override
    protected void drawPanelContent(DropdownRenderer renderer, int mouseX, int mouseY, float visibleHeight) {
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float contentX = x + PADDING;
        float contentW = width - PADDING * 2.0f;

        String placeholder = ConfigManager.INSTANCE.getActiveConfigName();
        if (!inputField.isFocused() && inputField.getText().isEmpty()) {
            inputField.setText(placeholder);
        }
        inputField.draw(renderer, contentX, currentY, contentW, FIELD_HEIGHT, mouseX, mouseY, placeholder, DropdownTheme.SETTING_TEXT_SCALE);
        currentY += FIELD_HEIGHT + GAP;

        String[] actions = {
                saveAsComponent.getTranslatedName(),
                reloadComponent.getTranslatedName(),
                exportComponent.getTranslatedName(),
                importComponent.getTranslatedName(),
                newComponent.getTranslatedName(),
                openFolderComponent.getTranslatedName()
        };
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                if (index >= actions.length) continue;
                float btnW = (contentW - GAP) * 0.5f;
                float btnX = contentX + col * (btnW + GAP);
                float btnY = currentY + row * (BUTTON_HEIGHT + GAP);
                boolean hovered = isHovered(mouseX, mouseY, btnX, btnY, btnW, BUTTON_HEIGHT);
                renderer.roundRect().addRoundRect(btnX, btnY, btnW, BUTTON_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                        hovered ? MD3Theme.PRIMARY_CONTAINER : MD3Theme.SURFACE_CONTAINER_HIGH);
                float labelScale = 0.48f;
                float labelW = renderer.text().getWidth(actions[index], labelScale);
                renderer.text().addText(actions[index], btnX + (btnW - labelW) * 0.5f, getCenteredTextY(renderer, btnY, BUTTON_HEIGHT, labelScale), labelScale, MD3Theme.TEXT_PRIMARY);
            }
        }
        currentY += BUTTON_HEIGHT * 3.0f + GAP * 3.0f;

        if (!status.isEmpty()) {
            float statusScale = 0.50f;
            renderer.text().addText(trimToWidth(status, statusScale, contentW, renderer), contentX, getCenteredTextY(renderer, currentY, ROW_HEIGHT, statusScale), statusScale, MD3Theme.TEXT_MUTED);
            currentY += ROW_HEIGHT;
        }

        String active = ConfigManager.INSTANCE.getActiveConfigName();
        List<String> configs = ConfigManager.INSTANCE.listConfigs();
        if (configs.isEmpty()) {
            float emptyScale = 0.55f;
            renderer.text().addText(emptyComponent.getTranslatedName(), contentX, getCenteredTextY(renderer, currentY, ROW_HEIGHT, emptyScale), emptyScale, MD3Theme.TEXT_MUTED);
            return;
        }
        for (String name : configs) {
            boolean activeRow = Objects.equals(name, active);
            boolean hovered = isHovered(mouseX, mouseY, contentX, currentY, contentW, ROW_HEIGHT);
            renderer.roundRect().addRoundRect(contentX, currentY, contentW, ROW_HEIGHT, DropdownTheme.BUTTON_RADIUS,
                    activeRow ? MD3Theme.PRIMARY_CONTAINER : (hovered ? MD3Theme.SURFACE_CONTAINER_HIGH : MD3Theme.SURFACE_CONTAINER_LOW));
            float nameScale = 0.56f;
            renderer.text().addText(trimToWidth(name, nameScale, contentW - 28.0f, renderer), contentX + 6.0f, getCenteredTextY(renderer, currentY, ROW_HEIGHT, nameScale), nameScale,
                    activeRow ? MD3Theme.ON_PRIMARY_CONTAINER : MD3Theme.TEXT_PRIMARY);
            float deleteX = contentX + contentW - 18.0f;
            float deleteScale = 0.52f;
            renderer.text().addText("x", deleteX + 5.0f, getCenteredTextY(renderer, currentY + 3.0f, 16.0f, deleteScale), deleteScale,
                    isHovered(mouseX, mouseY, deleteX, currentY + 3.0f, 16.0f, 16.0f) ? MD3Theme.ERROR : MD3Theme.TEXT_MUTED);
            currentY += ROW_HEIGHT + GAP;
        }
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        if (button != 0) return false;
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT + PADDING - scroll;
        float contentX = x + PADDING;
        float contentW = width - PADDING * 2.0f;
        if (inputField.focusIfContains(mouseX, mouseY, contentX, currentY, contentW, FIELD_HEIGHT)) {
            return true;
        }
        inputField.blur();
        currentY += FIELD_HEIGHT + GAP;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 2; col++) {
                int index = row * 2 + col;
                if (index >= 6) continue;
                float btnW = (contentW - GAP) * 0.5f;
                float btnX = contentX + col * (btnW + GAP);
                float btnY = currentY + row * (BUTTON_HEIGHT + GAP);
                if (isHovered(mouseX, mouseY, btnX, btnY, btnW, BUTTON_HEIGHT)) {
                    runAction(index);
                    return true;
                }
            }
        }
        currentY += BUTTON_HEIGHT * 3.0f + GAP * 3.0f;
        if (!status.isEmpty()) currentY += ROW_HEIGHT;

        for (String name : ConfigManager.INSTANCE.listConfigs()) {
            float deleteX = contentX + contentW - 18.0f;
            if (isHovered(mouseX, mouseY, deleteX, currentY + 3.0f, 16.0f, 16.0f)) {
                try {
                    ConfigManager.INSTANCE.deleteConfig(name);
                    status = deletedComponent.getTranslatedName() + " " + name;
                } catch (Exception e) {
                    status = errorText(e);
                }
                return true;
            }
            if (isHovered(mouseX, mouseY, contentX, currentY, contentW, ROW_HEIGHT)) {
                try {
                    ConfigManager.INSTANCE.switchConfig(name);
                    inputField.setText(name);
                    inputField.setCursorToEnd();
                    status = switchedComponent.getTranslatedName() + " " + name;
                } catch (Exception e) {
                    status = errorText(e);
                }
                return true;
            }
            currentY += ROW_HEIGHT + GAP;
        }
        return false;
    }

    private void runAction(int index) {
        String value = inputField.getText().trim();
        try {
            switch (index) {
                case 0 -> {
                    if (!value.isEmpty()) {
                        String saved = ConfigManager.INSTANCE.saveAsConfig(value);
                        inputField.setText(saved);
                        inputField.setCursorToEnd();
                        status = savedComponent.getTranslatedName() + " " + saved;
                    }
                }
                case 1 -> {
                    ConfigManager.INSTANCE.reloadOrThrow();
                    status = reloadedComponent.getTranslatedName();
                }
                case 2 -> {
                    status = exportedComponent.getTranslatedName() + " " + ConfigManager.INSTANCE.exportActiveConfigToZip(value).getFileName();
                }
                case 3 -> {
                    if (!value.isEmpty()) {
                        String imported = ConfigManager.INSTANCE.importConfigFromZip(value);
                        inputField.setText(imported);
                        inputField.setCursorToEnd();
                        status = importedComponent.getTranslatedName() + " " + imported;
                    }
                }
                case 4 -> {
                    if (!value.isEmpty()) {
                        String created = ConfigManager.INSTANCE.newDefaultConfig(value);
                        inputField.setText(created);
                        inputField.setCursorToEnd();
                        status = createdComponent.getTranslatedName() + " " + created;
                    }
                }
                case 5 ->
                        status = openFolderComponent.getTranslatedName() + " " + ConfigFolderOpener.openConfigFolder();
                default -> {
                }
            }
        } catch (Exception e) {
            status = errorText(e);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!inputField.isFocused()) return false;
        if (keyCode == 256) {
            inputField.blur();
            return true;
        }
        return inputField.keyPressed(keyCode);
    }

    @Override
    public boolean charTyped(String typedText) {
        return inputField.charTyped(typedText);
    }

    @Override
    public boolean hasActiveInput() {
        return inputField.isFocused();
    }

    private String errorText(Exception e) {
        String message = e.getMessage();
        return errorComponent.getTranslatedName() + ": " + (message == null || message.isBlank() ? e.getClass().getSimpleName() : message);
    }

    private float getCenteredTextY(DropdownRenderer renderer, float boxY, float boxH, float scale) {
        return boxY + (boxH - renderer.text().getHeight(scale)) / 2.0f;
    }

}
