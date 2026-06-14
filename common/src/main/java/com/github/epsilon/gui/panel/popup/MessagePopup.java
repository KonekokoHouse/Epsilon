package com.github.epsilon.gui.panel.popup;

import com.github.epsilon.gui.panel.MD3Theme;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.dsl.PanelRenderBatch;
import com.github.epsilon.gui.dsl.PanelUiTree;
import com.github.epsilon.utils.render.animation.Animation;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.Supplier;

public class MessagePopup implements PanelPopupHost.Popup {

    private final PanelLayout.Rect bounds;
    private final Supplier<String> titleSupplier;
    private final Supplier<String> messageSupplier;
    private final String detail;
    private final Supplier<String> buttonLabelSupplier;

    private final Animation openAnimation = new Animation(Easing.EASE_OUT_CUBIC, 160L);
    private final Animation buttonHoverAnimation = new Animation(Easing.EASE_OUT_CUBIC, 120L);

    private boolean closeAfterClick;
    private PanelLayout.Rect buttonBounds;

    public MessagePopup(PanelLayout.Rect bounds, String title, String message, String detail, String buttonLabel) {
        this(bounds, () -> title, () -> message, detail, () -> buttonLabel);
    }

    public MessagePopup(PanelLayout.Rect bounds, Supplier<String> titleSupplier, Supplier<String> messageSupplier, String detail, Supplier<String> buttonLabelSupplier) {
        this.bounds = bounds;
        this.titleSupplier = titleSupplier;
        this.messageSupplier = messageSupplier;
        this.detail = detail;
        this.buttonLabelSupplier = buttonLabelSupplier;
        this.openAnimation.setStartValue(0.0f);
        this.buttonHoverAnimation.setStartValue(0.0f);
        updateLayout(bounds.y());
    }

    @Override
    public PanelLayout.Rect getBounds() {
        return bounds;
    }

    @Override
    public void extractGui(GuiGraphicsExtractor guiGraphics, PanelRenderBatch renderBatch, int mouseX, int mouseY, float partialTick) {
        openAnimation.run(1.0f);
        float progress = openAnimation.getValue();
        float popupY = bounds.y() - (1.0f - progress) * 6.0f;
        updateLayout(popupY);
        buttonHoverAnimation.run(buttonBounds.contains(mouseX, mouseY) ? 1.0f : 0.0f);
        PanelUiTree tree = PanelUiTree.build(scope -> {
            scope.popupCard(new PanelLayout.Rect(bounds.x(), popupY, bounds.width(), bounds.height()),
                    MD3Theme.CARD_RADIUS,
                    POPUP_SHADOW_RADIUS,
                    MD3Theme.withAlpha(MD3Theme.SHADOW, (int) (MD3Theme.POPUP_SHADOW_ALPHA * progress)),
                    MD3Theme.withAlpha(MD3Theme.SURFACE_CONTAINER_LOW, 255));

            float titleScale = 0.66f;
            float messageScale = 0.56f;
            float detailScale = 0.52f;
            float textX = bounds.x() + 12.0f;
            String title = titleSupplier.get();
            String message = messageSupplier.get();
            String buttonLabel = buttonLabelSupplier.get();
            scope.text(title, textX, popupY + 10.0f, titleScale, MD3Theme.TEXT_PRIMARY);
            scope.text(message, textX, popupY + 25.0f, messageScale, MD3Theme.TEXT_SECONDARY);
            if (detail != null && !detail.isBlank()) {
                scope.text(detail, textX, popupY + 38.0f, detailScale, MD3Theme.TEXT_MUTED);
            }

            float hover = buttonHoverAnimation.getValue();
            scope.button(buttonBounds, buttonBounds.height() / 2.0f,
                    MD3Theme.lerp(MD3Theme.PRIMARY_CONTAINER, MD3Theme.PRIMARY, hover * 0.35f),
                    buttonLabel, 0.56f, MD3Theme.ON_PRIMARY_CONTAINER);
        });
        renderBatch.render(tree);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean isDoubleClick) {
        if (event.button() != 0 || !bounds.contains(event.x(), event.y())) {
            return false;
        }
        closeAfterClick = buttonBounds.contains(event.x(), event.y());
        return true;
    }

    @Override
    public boolean shouldCloseAfterClick() {
        return closeAfterClick;
    }

    private void updateLayout(float popupY) {
        float buttonWidth = 68.0f;
        float buttonHeight = 24.0f;
        buttonBounds = new PanelLayout.Rect(
                bounds.x() + bounds.width() - buttonWidth - 12.0f,
                popupY + bounds.height() - buttonHeight - 10.0f,
                buttonWidth,
                buttonHeight
        );
    }
}

