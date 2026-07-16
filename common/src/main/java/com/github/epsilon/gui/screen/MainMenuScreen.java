package com.github.epsilon.gui.screen;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.shaders.GlslSandBox;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.lib.UiTree;
import com.github.epsilon.gui.lib.scene.UiLayer;
import com.github.epsilon.gui.lib.scene.UiScene;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.modules.impl.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MainMenuScreen extends Screen {

    public static final MainMenuScreen INSTANCE = new MainMenuScreen();

    private final UiScene scene = new UiScene(EpsilonUiTheme.INSTANCE);

    private final List<MenuEntry> entries = new ArrayList<>();

    private LuminRenderSystem.LuminRenderTarget backgroundRenderTarget;
    private LuminRenderSystem.LuminRenderTarget uiRenderTarget;

    private long introStartMs;
    private boolean initialized;

    private MainMenuScreen() {
        super(Component.literal("MainMenuScreen"));
        entries.add(new MenuEntry("Singleplayer", () -> minecraft.gui.setScreen(new SelectWorldScreen(this))));
        entries.add(new MenuEntry("Multiplayer", () -> {
            Screen screen = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
            this.minecraft.gui.setScreen(screen);
        }));
        entries.add(new MenuEntry("GUI", () -> minecraft.gui.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
            case Panel -> PanelScreen.INSTANCE;
            case Dropdown -> DropdownScreen.INSTANCE;
        })));
        entries.add(new MenuEntry("Options", () -> minecraft.gui.setScreen(new OptionsScreen(this, minecraft.options, false))));
        entries.add(new MenuEntry("Quit", minecraft::stop));
    }

    @Override
    protected void init() {
        super.init();
        if (!initialized) {
            initialized = true;
            introStartMs = Util.getMillis();
            GlslSandBox.INSTANCE.resetTime();
            for (MenuEntry entry : entries) {
                entry.hoverProgress = 0.0f;
                entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f);
            }
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        final var window = minecraft.getWindow();
        if (backgroundRenderTarget == null) {
            backgroundRenderTarget = LuminRenderSystem.LuminRenderTarget.create("main-menu-background", window.getWidth(), window.getHeight());
        }

        backgroundRenderTarget.clear();
        backgroundRenderTarget.resize(window.getWidth(), window.getHeight());
        LuminRenderSystem.setActiveTarget(backgroundRenderTarget);

        final var background = switch (ClientSetting.INSTANCE.mainMenuBackground.getValue()) {
            case SEA_LEVEL -> GlslSandBox.SEA_LEVEL;
            case PLANET -> GlslSandBox.PLANET;
            case BLACK_HOLE -> GlslSandBox.BLACK_HOLE;
            case MINECRAFT -> GlslSandBox.MINECRAFT;
        };

        GlslSandBox.INSTANCE.render(background, LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY));

        LuminRenderSystem.setActiveTarget(null);
        graphics.blit(backgroundRenderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        final var window = minecraft.getWindow();
        if (uiRenderTarget == null) {
            uiRenderTarget = LuminRenderSystem.LuminRenderTarget.create("main-menu-ui", window.getWidth(), window.getHeight());
        }

        uiRenderTarget.clear();
        uiRenderTarget.resize(window.getWidth(), window.getHeight());
        LuminRenderSystem.setActiveTarget(uiRenderTarget);

        scene.beginFrame();
        drawMenu(LuminRenderSystem.toEpsilonMouseX(mouseX), LuminRenderSystem.toEpsilonMouseY(mouseY));
        scene.endFrame();

        LuminRenderSystem.setActiveTarget(null);
        graphics.blit(uiRenderTarget.getIdentifier(), 0, 0, window.getGuiScaledWidth(), window.getGuiScaledHeight(), 0, 1, 1, 0);
    }

    private void drawMenu(int mouseX, int mouseY) {
        float introProgress = easeOutCubic(Mth.clamp((Util.getMillis() - introStartMs) / 650.0f, 0.0f, 1.0f));
        int width = LuminRenderSystem.getScaledWidthInt();
        int height = LuminRenderSystem.getScaledHeightInt();
        int buttonCount = entries.size();
        int gapCount = Math.max(0, buttonCount - 1);
        float scale = Mth.clamp((width * 2.0f + height) / 900.0f + 0.08f, 0.72f, 1.24f);

        float titleX = Math.max(12.0f * scale, width / 15.0f);
        float titleY = Math.max(8.0f * scale, titleX * 0.5f);
        float titleScale = 2.36f * scale;
        float subtitleScale = 0.62f * scale;
        float titleSubtitleGap = 12.0f * scale;
        float titleAccentGap = 6.0f * scale;
        float titleAccentWidth = 68.0f * scale;
        float titleAccentHeight = Math.max(1.6f, 1.8f * scale);

        float rowInset = Math.clamp(14.0f * scale, width / 12.0f, width * 0.5f);
        float availableRowWidth = Math.max(0.0f, width - rowInset * 2.0f);
        float minButtonWidth = 42.0f * scale;
        float buttonGap = gapCount == 0 ? 0.0f : Math.clamp((availableRowWidth - buttonCount * minButtonWidth) / gapCount, 0.0f, 10.0f * scale);
        float maxButtonWidth = Math.max(0.0f, (availableRowWidth - gapCount * buttonGap) / Math.max(1, buttonCount));
        float buttonWidth = Math.min(112.0f * scale, maxButtonWidth);
        float totalButtonsWidth = buttonCount * buttonWidth + gapCount * buttonGap;
        float buttonsStartX = (width - totalButtonsWidth) * 0.5f;
        float buttonLineHeight = Math.max(2.0f, 2.0f * scale);
        float buttonHitPaddingX = 8.0f * scale;
        float buttonHitPaddingTop = 6.0f * scale;
        float buttonHitHeight = 26.0f * scale;
        float buttonRevealDistance = 18.0f * scale;
        float preferredButtonTextScale = 0.90f * scale;
        float buttonTextOffsetY = 5.5f * scale;
        float targetButtonsY = height - Math.min((width + height * 2.0f) / 25.0f, 54.0f * scale);
        float buttonsY = Math.min(targetButtonsY, height - buttonHitHeight + buttonHitPaddingTop);

        Color titleColor = applyAlpha(new Color(230, 224, 233), 0.96f);
        Color subtitleColor = applyAlpha(new Color(202, 196, 208), 0.90f);
        Color accentColor = applyAlpha(new Color(208, 188, 255), 0.95f);

        String title = "EPSILON";
        String subtitle = Constants.VERSION;

        float titleHeight = scene.scheduler().textMetrics().getHeight(titleScale, StaticFontLoader.JURA_LIGHT);
        float subtitleY = titleY + titleHeight + titleSubtitleGap;

        UiTree tree = UiTree.build(scope -> {
            scope.layer(0, layer -> layer.rect(titleX, titleY + titleHeight + titleAccentGap,
                    titleAccentWidth, titleAccentHeight, accentColor));
            scope.layer(10, layer -> {
                layer.text(title, titleX, titleY, titleScale, titleColor, StaticFontLoader.JURA_LIGHT);
                layer.text(subtitle, titleX, subtitleY, subtitleScale, subtitleColor);
            });
            for (int index = 0; index < entries.size(); index++) {
                MenuEntry entry = entries.get(index);
                float staged = Mth.clamp((introProgress - index * 0.08f) / 0.52f, 0.0f, 1.0f);
                float appear = easeOutCubic(staged);
                if (appear <= 0.001f) {
                    entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f);
                    continue;
                }

                float drawX = buttonsStartX + index * (buttonWidth + buttonGap);
                float drawY = buttonsY + (1.0f - appear) * buttonRevealDistance;
                boolean hovered = entry.isHovered(mouseX, mouseY);
                entry.hoverProgress = Mth.lerp(hovered ? 0.24f : 0.16f, entry.hoverProgress, hovered ? 1.0f : 0.0f);

                float hover = entry.hoverProgress;
                float buttonY = drawY - hover * 2.5f * scale;
                entry.setBounds(
                        drawX - buttonHitPaddingX,
                        buttonY - buttonHitPaddingTop,
                        buttonWidth + buttonHitPaddingX * 2.0f,
                        buttonHitHeight
                );

                Color lineBase = applyAlpha(new Color(147, 143, 153), 0.70f * appear);
                Color lineHover = applyAlpha(new Color(208, 188, 255), 0.98f * appear);
                Color labelColor = MD3Theme.lerp(
                        applyAlpha(new Color(230, 224, 233), 0.94f * appear),
                        applyAlpha(new Color(234, 221, 255), 0.98f * appear),
                        hover * 0.68f
                );

                scope.layer(0, layer -> {
                    layer.rect(drawX + scale, buttonY + scale, buttonWidth + scale * 0.5f,
                            buttonLineHeight + scale, applyAlpha(MD3Theme.SURFACE, 0.70f * appear));
                    layer.rect(drawX, buttonY, buttonWidth, buttonLineHeight, MD3Theme.lerp(lineBase, lineHover, hover));
                });

                String label = localizedTitle(entry.title);
                float labelWidth = scene.scheduler().textMetrics().getWidth(label, preferredButtonTextScale);
                float buttonTextScale = labelWidth > buttonWidth && labelWidth > 0.0f
                        ? preferredButtonTextScale * buttonWidth / labelWidth
                        : preferredButtonTextScale;
                float textY = buttonY + buttonTextOffsetY;
                scope.layer(10, layer -> layer.text(label, drawX, textY, buttonTextScale, labelColor));
            }
        });

        scene.submit(UiLayer.CONTENT, tree);
    }

    private static String localizedTitle(String title) {
        return switch (title) {
            case "Singleplayer" -> EpsilonTranslations.Gui.MAINMENU_SINGLEPLAYER.getTranslatedName();
            case "Multiplayer" -> EpsilonTranslations.Gui.MAINMENU_MULTIPLAYER.getTranslatedName();
            case "Options" -> EpsilonTranslations.Gui.MAINMENU_OPTIONS.getTranslatedName();
            case "Quit" -> EpsilonTranslations.Gui.MAINMENU_QUIT.getTranslatedName();
            default -> title;
        };
    }

    private static float easeOutCubic(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
    }

    private static Color applyAlpha(Color color, float alphaFactor) {
        float factor = Mth.clamp(alphaFactor, 0.0f, 1.0f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * factor));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (MenuEntry entry : entries) {
                MouseButtonEvent epsilonEvent = LuminRenderSystem.toEpsilonMouseEvent(event);
                if (entry.isHovered(epsilonEvent.x(), epsilonEvent.y())) {
                    entry.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(LuminRenderSystem.toEpsilonMouseEvent(event), doubleClick);
    }

    @Override
    public void removed() {
        super.removed();
        initialized = false;
        if (backgroundRenderTarget != null) {
            backgroundRenderTarget.close();
            backgroundRenderTarget = null;
        }
        if (uiRenderTarget != null) {
            uiRenderTarget.close();
            uiRenderTarget = null;
        }
        scene.close();
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class MenuEntry {
        private final String title;
        private final Runnable action;

        private float x;
        private float y;
        private float width;
        private float height;
        private float hoverProgress;

        private MenuEntry(String title, Runnable action) {
            this.title = title;
            this.action = action;
        }

        private void setBounds(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        private boolean isHovered(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        }
    }

    public enum Background {
        SEA_LEVEL,
        PLANET,
        BLACK_HOLE,
        MINECRAFT
    }

}
