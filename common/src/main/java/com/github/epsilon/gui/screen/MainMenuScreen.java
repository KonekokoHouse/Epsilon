package com.github.epsilon.gui.screen;

import com.github.epsilon.Constants;
import com.github.epsilon.assets.i18n.EpsilonTranslations;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.shaders.GlslSandBox;
import com.github.epsilon.gui.dropdown.DropdownScreen;
import com.github.epsilon.gui.panel.PanelScreen;
import com.github.epsilon.gui.theme.EpsilonUiTheme;
import com.github.epsilon.gui.theme.MD3Theme;
import com.github.epsilon.gui.utils.UiCoordinateMapper;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.ui.geometry.UiRect;
import com.github.slmpc.lumingraphics.ui.scene.UiLayer;
import com.github.slmpc.lumingraphics.ui.scene.UiScene;
import com.github.slmpc.lumingraphics.ui.text.UiTextMetrics;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
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
    private static final String DEFAULT_FONT_ID = "epsilon-default";
    private static final String JURA_LIGHT_FONT_ID = "epsilon-jura-light";
    private static final float MENU_TEXT_SCALE = 0.62f;

    private final List<MenuEntry> entries = new ArrayList<>();

    private LuminRenderSystem.LuminRenderTarget backgroundRenderTarget;
    private UiScene scene;
    private MinecraftUiRuntime2612 sceneRuntime;
    private UiTextMetrics textMetrics;
    private int pendingMouseX;
    private int pendingMouseY;
    private boolean overlayPending;

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
        pendingMouseX = UiCoordinateMapper.toProjectionX(mouseX);
        pendingMouseY = UiCoordinateMapper.toProjectionY(mouseY);
        overlayPending = true;
    }

    public void renderPendingOverlay() {
        if (!overlayPending || minecraft.screen != this) return;
        overlayPending = false;
        MinecraftUiRuntime2612 runtime = MinecraftUiRuntime2612.current();
        ClientSetting.INSTANCE.configureMinecraftFonts(runtime);
        prepareScene(runtime);
        runtime.render(scene, activeScene -> drawMenu(activeScene, pendingMouseX, pendingMouseY));
    }

    private void prepareScene(MinecraftUiRuntime2612 runtime) {
        if (scene != null && sceneRuntime == runtime) return;
        releaseScene();
        runtime.useDefaultFont(DEFAULT_FONT_ID);
        scene = runtime.createScene(EpsilonUiTheme.lumin());
        sceneRuntime = runtime;
        textMetrics = runtime.textMetrics();
    }

    private void releaseScene() {
        UiScene previous = scene;
        scene = null;
        sceneRuntime = null;
        textMetrics = null;
        if (previous != null) previous.close();
    }

    private void drawMenu(UiScene activeScene, int mouseX, int mouseY) {
        float introProgress = easeOutCubic(Mth.clamp((Util.getMillis() - introStartMs) / 650.0f, 0.0f, 1.0f));
        int width = UiCoordinateMapper.getProjectionWidthInt();
        int height = UiCoordinateMapper.getProjectionHeightInt();
        int buttonCount = entries.size();
        int gapCount = Math.max(0, buttonCount - 1);
        float scale = Mth.clamp((width * 2.0f + height) / 900.0f + 0.08f, 0.72f, 1.24f);

        float titleX = Math.max(12.0f * scale, width / 15.0f);
        float titleY = Math.max(8.0f * scale, titleX * 0.5f);
        float titleSubtitleGap = 12.0f * scale;
        float titleAccentGap = 6.0f * scale;
        float titleAccentWidth = 68.0f * scale;
        float titleAccentHeight = Math.max(1.6f, 1.8f * scale);

        float rowInset = Math.min(14.0f * scale, width * 0.5f);
        float availableRowWidth = Math.max(0.0f, width - rowInset * 2.0f);
        float minButtonWidth = 42.0f * scale;
        String[] labels = new String[buttonCount];
        float[] buttonWidths = new float[buttonCount];
        float labelsWidth = 0.0f;
        for (int index = 0; index < buttonCount; index++) {
            labels[index] = localizedTitle(entries.get(index).title);
            buttonWidths[index] = Math.max(minButtonWidth,
                    textMetrics.textWidth(labels[index], MENU_TEXT_SCALE, null));
            labelsWidth += buttonWidths[index];
        }
        float buttonGap = gapCount == 0 ? 0.0f
                : Math.clamp((availableRowWidth - labelsWidth) / gapCount, 0.0f, 10.0f * scale);
        float totalButtonsWidth = labelsWidth + gapCount * buttonGap;
        float buttonsStartX = (width - totalButtonsWidth) * 0.5f;
        float buttonLineHeight = Math.max(2.0f, 2.0f * scale);
        float buttonHitPaddingX = 8.0f * scale;
        float buttonHitPaddingTop = 6.0f * scale;
        float buttonHitHeight = 26.0f * scale;
        float buttonRevealDistance = 18.0f * scale;
        float buttonTextOffsetY = 5.5f * scale;
        float targetButtonsY = height - Math.min((width + height * 2.0f) / 25.0f, 54.0f * scale);
        float buttonsY = Math.min(targetButtonsY, height - buttonHitHeight + buttonHitPaddingTop);

        Color titleColor = applyAlpha(new Color(230, 224, 233), 0.96f);
        Color subtitleColor = applyAlpha(new Color(202, 196, 208), 0.90f);
        Color accentColor = applyAlpha(new Color(208, 188, 255), 0.95f);

        String title = "EPSILON";
        String subtitle = Constants.VERSION;

        float titleHeight = textMetrics.textHeight(MENU_TEXT_SCALE, null);
        float subtitleY = titleY + titleHeight + titleSubtitleGap;

        UiTree tree = UiTree.build(scope -> {
            scope.layer(0, layer -> layer.rect(titleX, titleY + titleHeight + titleAccentGap,
                    titleAccentWidth, titleAccentHeight, accentColor));
            scope.layer(10, layer -> {
                layer.text(title, titleX, titleY, MENU_TEXT_SCALE, titleColor);
                layer.text(subtitle, titleX, subtitleY, MENU_TEXT_SCALE, subtitleColor);
            });
            for (int index = 0; index < entries.size(); index++) {
                MenuEntry entry = entries.get(index);
                float staged = Mth.clamp((introProgress - index * 0.08f) / 0.52f, 0.0f, 1.0f);
                float appear = easeOutCubic(staged);
                if (appear <= 0.001f) {
                    entry.setBounds(0.0f, 0.0f, 0.0f, 0.0f);
                    continue;
                }

                float buttonX = buttonsStartX;
                for (int previous = 0; previous < index; previous++) {
                    buttonX += buttonWidths[previous] + buttonGap;
                }
                float drawX = buttonX;
                float buttonWidth = buttonWidths[index];
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

                String label = labels[index];
                float textY = buttonY + buttonTextOffsetY;
                scope.layer(10, layer -> layer.text(label, drawX, textY, MENU_TEXT_SCALE, labelColor));
            }
        });

        activeScene.submit(UiLayer.CONTENT, tree);
    }

    private static float easeOutCubic(float value) {
        float t = Mth.clamp(value, 0.0f, 1.0f);
        float inv = 1.0f - t;
        return 1.0f - inv * inv * inv;
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

    private static Color applyAlpha(Color color, float alphaFactor) {
        float factor = Mth.clamp(alphaFactor, 0.0f, 1.0f);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(color.getAlpha() * factor));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent epsilonEvent = UiCoordinateMapper.toProjectionEvent(event);
        if (epsilonEvent.button() == 0) {
            for (MenuEntry entry : entries) {
                if (entry.isHovered(epsilonEvent.x(), epsilonEvent.y())) {
                    entry.action.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(epsilonEvent, doubleClick);
    }

    @Override
    public void removed() {
        super.removed();
        releaseScene();
        initialized = false;
        overlayPending = false;
        if (backgroundRenderTarget != null) {
            backgroundRenderTarget.close();
            backgroundRenderTarget = null;
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

    public enum Background {
        SEA_LEVEL,
        PLANET,
        BLACK_HOLE,
        MINECRAFT
    }

}
