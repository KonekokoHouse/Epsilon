package com.github.epsilon.gui.dropdown.component;

import com.github.epsilon.gui.dropdown.DropdownRenderer;
import com.github.epsilon.gui.dropdown.DropdownTheme;
import com.github.epsilon.managers.ModuleManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.ClientSetting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class CategoryPanel extends AbstractDropdownPanel {

    private final Category category;
    private final List<ModuleButton> moduleButtons = new ArrayList<>();
    private List<ModuleButton> sortedModuleButtons = List.of();
    private String searchQuery = "";

    public CategoryPanel(Category category, int panelIndex) {
        super("category:" + category, category::getName, category.icon, panelIndex);
        this.category = category;
        List<Module> modules = ModuleManager.INSTANCE.getModules().stream()
                .filter(m -> m.getCategory() == category)
                .toList();
        for (Module module : modules) {
            moduleButtons.add(new ModuleButton(module));
        }
        refreshSortedModuleButtons();
    }

    @Override
    protected void drawPanelContent(DropdownRenderer renderer, int mouseX, int mouseY, float visibleHeight) {
        refreshSortedModuleButtons();
        float expand = openAnim.getValue();
        float currentY = y + DropdownTheme.PANEL_HEADER_HEIGHT - scroll;
        for (ModuleButton button : sortedModuleButtons) {
            if (!matchesSearch(button)) continue;
            button.setPosition(x, currentY, width);
            float btnH = button.getHeight();

            float visibleTop = y + DropdownTheme.PANEL_HEADER_HEIGHT;
            float visibleBottom = visibleTop + visibleHeight * expand;
            if (currentY + btnH > visibleTop && currentY < visibleBottom) {
                button.draw(renderer, mouseX, mouseY);
            }

            currentY += btnH;
        }
    }

    @Override
    protected float computeContentHeight() {
        refreshSortedModuleButtons();
        float total = 0.0f;
        for (ModuleButton button : sortedModuleButtons) {
            if (!matchesSearch(button)) continue;
            total += button.getHeight();
        }
        return total;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean mouseClickedContent(double mouseX, double mouseY, int button) {
        refreshSortedModuleButtons();
        for (ModuleButton mb : sortedModuleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean mouseReleasedContent(double mouseX, double mouseY, int button) {
        refreshSortedModuleButtons();
        for (ModuleButton mb : sortedModuleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.mouseReleased(mouseX, mouseY, button)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        refreshSortedModuleButtons();
        for (ModuleButton mb : sortedModuleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean charTyped(String typedText) {
        refreshSortedModuleButtons();
        for (ModuleButton mb : sortedModuleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.charTyped(typedText)) {
                return true;
            }
        }
        return false;
    }

    public Category getCategory() {
        return category;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery == null ? "" : searchQuery.trim().toLowerCase();
        setScrollImmediate(0.0f);
    }

    @Override
    public boolean hasActiveInput() {
        refreshSortedModuleButtons();
        for (ModuleButton mb : sortedModuleButtons) {
            if (!matchesSearch(mb)) continue;
            if (mb.hasListeningKeybind() || mb.hasFocusedInput()) return true;
        }
        return false;
    }

    private void refreshSortedModuleButtons() {
        ClientSetting.ModuleSort sortMode = ClientSetting.INSTANCE.moduleSort.getValue();
        sortedModuleButtons = moduleButtons.stream()
                .sorted(getComparator(sortMode))
                .toList();
    }

    private Comparator<ModuleButton> getComparator(ClientSetting.ModuleSort sortMode) {
        Comparator<ModuleButton> nameComparator = Comparator.comparing(button -> normalizedName(button.getModule()), String.CASE_INSENSITIVE_ORDER);
        return switch (sortMode) {
            case EnabledFirst -> Comparator.comparing((ModuleButton button) -> button.getModule().isEnabled()).reversed()
                    .thenComparing(nameComparator);
            case Addon -> Comparator.comparing((ModuleButton button) -> normalizedAddon(button.getModule()), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(nameComparator);
            case Name -> nameComparator;
        };
    }

    private String normalizedName(Module module) {
        String translated = module.getTranslatedName();
        if (translated != null && !translated.isBlank()) return translated;
        String name = module.getName();
        return name == null ? "" : name;
    }

    private String normalizedAddon(Module module) {
        String addonId = module.getAddonId();
        return addonId == null || addonId.isBlank() ? "unknown" : addonId.toLowerCase(Locale.ROOT);
    }

    private boolean matchesSearch(ModuleButton button) {
        if (searchQuery.isBlank()) return true;
        Module module = button.getModule();
        String translated = module.getTranslatedName() == null ? "" : module.getTranslatedName();
        String name = module.getName() == null ? "" : module.getName();
        String categoryName = module.getCategory() == null ? "" : module.getCategory().getName();
        String addon = module.getAddonId() == null ? "" : module.getAddonId();
        return translated.toLowerCase().contains(searchQuery)
                || name.toLowerCase().contains(searchQuery)
                || categoryName.toLowerCase().contains(searchQuery)
                || addon.toLowerCase().contains(searchQuery);
    }

}
