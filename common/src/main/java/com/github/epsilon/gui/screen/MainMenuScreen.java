package com.github.epsilon.gui.screen;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslateComponent;
import com.github.epsilon.assets.i18n.TranslateComponent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.shaders.GlslSandBox;
import com.github.epsilon.graphics.text.StaticFontLoader;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.scene.GuiLayer;
import com.github.epsilon.gui.scene.GuiScene;
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

    private final GuiScene scene = new GuiScene();

    private final List<MenuEntry> entries = new ArrayList<>();
    private static final TranslateComponent singleplayerComponent = EpsilonTranslateComponent.create("gui", "mainmenu.singleplayer");
    private static final TranslateComponent multiplayerComponent = EpsilonTranslateComponent.create("gui", "mainmenu.multiplayer");
    private static final TranslateComponent optionsComponent = EpsilonTranslateComponent.create("gui", "mainmenu.options");
    private static final TranslateComponent quitComponent = EpsilonTranslateComponent.create("gui", "mainmenu.quit");

    private LuminRenderSystem.LuminRenderTarget backgroundRenderTarget;
    private LuminRenderSystem.LuminRenderTarget uiRenderTarget;

    private long introStartMs;
    private boolean initialized;

    private MainMenuScreen() {
        super(Component.literal("MainMenuScreen"));
        entries.add(new MenuEntry("Singleplayer", () -> minecraft.setScreen(new SelectWorldScreen(this))));
        entries.add(new MenuEntry("Multiplayer", () -> {
            Screen screen = this.minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this);
            this.minecraft.setScreen(screen);
        }));
        entries.add(new MenuEntry("GUI", () -> minecraft.setScreen(switch (ClientSetting.INSTANCE.guiMode.getValue()) {
            case Panel -> PanelScreen.INSTANCE;
            case Dropdown -> DropdownScreen.INSTANCE;
        })));
        entries.add(new MenuEntry("Options", () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options, false))));
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
        Layout layout = Layout.resolve(LuminRenderSystem.getScaledWidthInt(), LuminRenderSystem.getScaledHeightInt(), entries.size());

        Color titleColor = applyAlpha(new Color(230, 224, 233), 0.96f);
        Color subtitleColor = applyAlpha(new Color(202, 196, 208), 0.90f);
        Color accentColor = applyAlpha(new Color(208, 188, 255), 0.95f);

        String title = "EPSILON";
        String subtitle = Constants.VERSION;

        float titleHeight = scene.scheduler().textMetrics().getHeight(layout.titleScale, StaticFontLoader.JURA_LIGHT);
        float subtitleY = layout.titleY + scene.scheduler().textMetrics().getHeight(layout.titleScale, StaticFontLoader.JURA_LIGHT) + layout.titleSubtitleGap;

        PanelUiTree tree = PanelUiTree.build(scope -> {
            scope.layer(0, layer -> layer.rect(layout.titleX, layout.titleY + titleHeight + layout.titleAccentGap,
                    layout.titleAccentWidth, layout.titleAccentHeight, accentColor));
            scope.layer(10, layer -> {
                layer.text(title, layout.titleX, layout.titleY, layout.titleScale, titleColor, StaticFontLoader.JURA_LIGHT);
                layer.text(subtitle, layout.titleX, subtitleY, layout.subtitleScale, subtitleColor);
            });
            for (int i = 0; i < entries.size(); i++) {
                buildEntry(scope, entries.get(i), i, mouseX, mouseY, introProgress, layout);
            }
        });

        scene.submit(GuiLayer.CONTENT, tree);
    }

    private void buildEntry(PanelUiTree.Scope scope, MenuEntry entry, int index, int mouseX, int mouseY, float introProgress, Layout layout) {
        float staged = Mth.clamp((introProgress - index * 0.08f) / 0.52f, 0.0f, 1.0f);
        float appear = easeOutCubic(staged);
        if (appear <= 0.001f) {
            entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f);
            return;
        }

        float drawX = layout.buttonsStartX + index * (layout.buttonWidth + layout.buttonGap);
        float drawY = layout.buttonsY + (1.0f - appear) * layout.buttonRevealDistance;

        boolean hovered = entry.isHovered(mouseX, mouseY);
        entry.hoverProgress = Mth.lerp(hovered ? 0.24f : 0.16f, entry.hoverProgress, hovered ? 1.0f : 0.0f);

        float hover = entry.hoverProgress;
        float hoverLift = hover * 2.5f * layout.scale;
        float buttonY = drawY - hoverLift;

        entry.setBounds(
                drawX - layout.buttonHitPaddingX,
                buttonY - layout.buttonHitPaddingTop,
                layout.buttonWidth + layout.buttonHitPaddingX * 2.0f,
                layout.buttonHitHeight
        );


        Color lineBase = applyAlpha(new Color(147, 143, 153), 0.70f * appear);
        Color lineHover = applyAlpha(new Color(208, 188, 255), 0.98f * appear);

        Color labelColor = MD3Theme.lerp(
                applyAlpha(new Color(230, 224, 233), 0.94f * appear),
                applyAlpha(new Color(234, 221, 255), 0.98f * appear),
                hover * 0.68f
        );

        scope.layer(0, layer -> {
            layer.rect(drawX + layout.scale, buttonY + layout.scale, layout.buttonWidth + layout.scale * 0.5f,
                    layout.buttonLineHeight + layout.scale, applyAlpha(MD3Theme.SURFACE, 0.70f * appear));
            layer.rect(drawX, buttonY, layout.buttonWidth, layout.buttonLineHeight, MD3Theme.lerp(lineBase, lineHover, hover));
        });

        float textY = buttonY + layout.buttonTextOffsetY;
        scope.layer(10, layer -> layer.text(localizedTitle(entry.title), drawX, textY, layout.buttonTextScale, labelColor));
    }

    private static String localizedTitle(String title) {
        return switch (title) {
            case "Singleplayer" -> singleplayerComponent.getTranslatedName();
            case "Multiplayer" -> multiplayerComponent.getTranslatedName();
            case "Options" -> optionsComponent.getTranslatedName();
            case "Quit" -> quitComponent.getTranslatedName();
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

    private record Layout(float scale, float titleX, float titleY, float titleScale, float subtitleScale,
                          float titleSubtitleGap, float titleAccentGap, float titleAccentWidth, float titleAccentHeight,
                          float buttonsStartX, float buttonsY, float buttonWidth, float buttonGap, float buttonRadius,
                          float buttonLineHeight, float buttonHitPaddingX, float buttonHitPaddingTop,
                          float buttonHitHeight,
                          float buttonRevealDistance, float buttonTextScale, float buttonTextOffsetY) {
        private static Layout resolve(int width, int height, int entryCount) {
            float scale = Mth.clamp((width * 2.0f + height) / 900.0f + 0.08f, 0.72f, 1.24f);

            float titleX = Math.max(12.0f * scale, width / 15.0f);
            float titleY = Math.max(8.0f * scale, titleX * 0.5f);
            float titleScale = 2.36f * scale;
            float subtitleScale = 0.62f * scale;
            float titleSubtitleGap = 7.0f * scale;
            float titleAccentGap = 6.0f * scale;
            float titleAccentWidth = 68.0f * scale;
            float titleAccentHeight = Math.max(1.6f, 1.8f * scale);

            float buttonGap = 10.0f * scale;
            float rowInset = Math.max(14.0f * scale, width / 12.0f);
            float maxButtonWidth = Math.max(36.0f * scale, (width - rowInset * 2.0f - buttonGap * Math.max(0, entryCount - 1)) / Math.max(1, entryCount));
            float buttonWidth = Math.max(42.0f * scale, Math.min(112.0f * scale, maxButtonWidth));
            float totalButtonsWidth = entryCount * buttonWidth + Math.max(0, entryCount - 1) * buttonGap;
            float buttonsStartX = (width - totalButtonsWidth) * 0.5f;
            float buttonsY = height - Math.min((width + height * 2.0f) / 25.0f, 54.0f * scale);

            return new Layout(
                    scale,
                    titleX,
                    titleY,
                    titleScale,
                    subtitleScale,
                    titleSubtitleGap,
                    titleAccentGap,
                    titleAccentWidth,
                    titleAccentHeight,
                    buttonsStartX,
                    buttonsY,
                    buttonWidth,
                    buttonGap,
                    10.0f * scale,
                    Math.max(2.0f, 2.0f * scale),
                    8.0f * scale,
                    6.0f * scale,
                    26.0f * scale,
                    18.0f * scale,
                    0.90f * scale,
                    5.5f * scale
            );
        }
    }

    public enum Background {
        SEA_LEVEL,
        PLANET,
        BLACK_HOLE,
        MINECRAFT
    }

}
