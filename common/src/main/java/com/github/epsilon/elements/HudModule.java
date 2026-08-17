package com.github.epsilon.elements;

import com.github.epsilon.gui.hudeditor.HudLayoutHelper;
import com.github.epsilon.gui.utils.UiCoordinateMapper;
import com.github.slmpc.lumingraphics.core.geometry.LuminColor;
import com.github.slmpc.lumingraphics.mc.v2612.runtime.MinecraftUiRuntime2612;
import com.github.slmpc.lumingraphics.render.scheduler.Render2DScheduler;
import com.github.slmpc.lumingraphics.text.render.TextRenderer;
import com.github.slmpc.lumingraphics.ui.render.UiRenderBatch;
import com.github.slmpc.lumingraphics.ui.tree.UiTree;
import com.github.epsilon.modules.Module;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;

import java.awt.Color;
import java.util.Objects;

public abstract class HudModule extends Module {

    protected static final double DEFAULT_SHADOW_BLUR = 10.0;
    protected static final double MIN_SHADOW_BLUR = 2.0;
    protected static final double MAX_SHADOW_BLUR = 32.0;
    protected static final double SHADOW_BLUR_STEP = 1.0;
    protected static final Color DEFAULT_SHADOW_COLOR = new Color(0, 0, 0, 110);

    public enum HorizontalAnchor {
        Left,
        Center,
        Right
    }

    public enum VerticalAnchor {
        Top,
        Center,
        Bottom
    }

    public float x, y, width, height;
    private float anchorX, anchorY;

    private final float defaultX, defaultY;
    private final float defaultAnchorX, defaultAnchorY;

    private HorizontalAnchor horizontalAnchor = HorizontalAnchor.Left;
    private VerticalAnchor verticalAnchor = VerticalAnchor.Top;
    private UiTree.Scope currentRenderScope;
    private Render2DScheduler.LayerHandle currentRenderLayer;

    public HudModule(String name, float width, float height) {
        this(name, 0f, 0f, width, height);
    }

    public HudModule(String name, float x, float y, float width, float height) {
        super(name, null);

        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.anchorX = x;
        this.anchorY = y;
        this.defaultX = x;
        this.defaultY = y;
        this.defaultAnchorX = x;
        this.defaultAnchorY = y;
    }

    @Override
    public void reset() {
        super.reset();
        resetLayout();
    }

    private void resetLayout() {
        horizontalAnchor = HorizontalAnchor.Left;
        verticalAnchor = VerticalAnchor.Top;
        anchorX = defaultAnchorX;
        anchorY = defaultAnchorY;
        applyRenderPosition(defaultX, defaultY, false);
    }

    public final void updateLayout() {
        applyRenderPosition(getAnchoredRenderX(), getAnchoredRenderY(), false);
    }

    protected final void setBounds(float width, float height) {
        boolean changed = this.width != width || this.height != height;
        this.width = width;
        this.height = height;
        if (changed) {
            applyRenderPosition(getAnchoredRenderX(), getAnchoredRenderY(), false);
        }
    }

    public final boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    public final void moveTo(float x, float y) {
        applyRenderPosition(x, y, true);
    }

    public final void moveBy(float deltaX, float deltaY) {
        moveTo(x + deltaX, y + deltaY);
    }

    public final void loadLegacyPosition(float renderX, float renderY) {
        horizontalAnchor = HorizontalAnchor.Left;
        verticalAnchor = VerticalAnchor.Top;
        anchorX = renderX;
        anchorY = renderY;
        applyRenderPosition(renderX, renderY, false);
    }

    public final void setAnchorState(HorizontalAnchor horizontalAnchor, VerticalAnchor verticalAnchor, float anchorX, float anchorY) {
        this.horizontalAnchor = horizontalAnchor == null ? HorizontalAnchor.Left : horizontalAnchor;
        this.verticalAnchor = verticalAnchor == null ? VerticalAnchor.Top : verticalAnchor;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        applyRenderPosition(getAnchoredRenderX(), getAnchoredRenderY(), false);
    }

    public final HorizontalAnchor getHorizontalAnchor() {
        return horizontalAnchor;
    }

    public final VerticalAnchor getVerticalAnchor() {
        return verticalAnchor;
    }

    public final float getAnchorX() {
        return anchorX;
    }

    public final float getAnchorY() {
        return anchorY;
    }

    private void applyRenderPosition(float renderX, float renderY, boolean updateAnchors) {
        int screenWidth = getScreenWidth();
        int screenHeight = getScreenHeight();
        float clampedX = Mth.clamp(renderX, 0.0f, Math.max(0.0f, screenWidth - width));
        float clampedY = Mth.clamp(renderY, 0.0f, Math.max(0.0f, screenHeight - height));

        if (updateAnchors) {
            horizontalAnchor = HudLayoutHelper.resolveHorizontalAnchor(clampedX, width, screenWidth);
            verticalAnchor = HudLayoutHelper.resolveVerticalAnchor(clampedY, height, screenHeight);
        }

        this.x = clampedX;
        this.y = clampedY;
        if (updateAnchors) {
            this.anchorX = HudLayoutHelper.toAnchorX(horizontalAnchor, clampedX, width, screenWidth);
            this.anchorY = HudLayoutHelper.toAnchorY(verticalAnchor, clampedY, height, screenHeight);
        }
    }

    private float getAnchoredRenderX() {
        int screenWidth = getScreenWidth();
        return HudLayoutHelper.getRenderX(horizontalAnchor, anchorX, width, screenWidth);
    }

    private float getAnchoredRenderY() {
        return HudLayoutHelper.getRenderY(verticalAnchor, anchorY, height, getScreenHeight());
    }

    private int getScreenWidth() {
        if (mc.getWindow() == null) {
            return 0;
        }
        return UiCoordinateMapper.getProjectionWidthInt();
    }

    private int getScreenHeight() {
        if (mc.getWindow() == null) {
            return 0;
        }
        return UiCoordinateMapper.getProjectionHeightInt();
    }

    public final void renderWithBatch(DeltaTracker deltaTracker, UiRenderBatch renderBatch) {
        UiTree.Scope scope = new UiTree.Scope();
        appendToTree(deltaTracker, scope, renderBatch.layerHandle(0));
        renderBatch.render(UiTree.from(scope));
    }

    /**
     * 将当前 HUD 元素追加到宿主持有的 HUD 树，不在元素内部提交渲染批次。
     */
    public final void appendToTree(DeltaTracker deltaTracker, UiTree.Scope scope) {
        appendToTree(deltaTracker, scope, null);
    }

    /**
     * 将当前 HUD 元素追加到宿主持有的 HUD 树，不在元素内部提交渲染批次。
     *
     * @param layer 宿主批次的直接绘制层，供需要绘制运行时纹理对象（例如字形图集）的元素使用；
     *              允许为 {@code null}，此时元素只能向 UI 树追加节点。
     */
    public final void appendToTree(DeltaTracker deltaTracker, UiTree.Scope scope,
                                   Render2DScheduler.LayerHandle layer) {
        Objects.requireNonNull(deltaTracker, "deltaTracker");
        Objects.requireNonNull(scope, "scope");
        UiTree.Scope previousScope = currentRenderScope;
        Render2DScheduler.LayerHandle previousLayer = currentRenderLayer;
        currentRenderScope = scope;
        currentRenderLayer = layer;
        try {
            render(deltaTracker);
        } finally {
            currentRenderScope = previousScope;
            currentRenderLayer = previousLayer;
        }
    }

    protected final UiTree.Scope renderScope() {
        if (currentRenderScope == null) {
            throw new IllegalStateException("HUD elements must render through renderWithBatch.");
        }
        return currentRenderScope;
    }

    /**
     * 返回宿主批次的直接绘制层。UI 树的纹理节点只接受资源 ID，因此运行时生成的纹理
     * （例如字形/emoji 图集）必须通过该层直接绘制。
     */
    protected final Render2DScheduler.LayerHandle renderLayer() {
        if (currentRenderLayer == null) {
            throw new IllegalStateException("HUD elements must render through a host batch to draw runtime textures.");
        }
        return currentRenderLayer;
    }

    protected static LuminColor lumin(Color color) {
        return new LuminColor(color.getRed() / 255.0f, color.getGreen() / 255.0f,
                color.getBlue() / 255.0f, color.getAlpha() / 255.0f);
    }

    protected static float textWidth(TextRenderer renderer, String text, float scale) {
        return textWidth(text, scale, "epsilon-default");
    }

    protected static float textWidth(TextRenderer renderer, String text, float scale, String fontId) {
        return textWidth(text, scale, fontId);
    }

    protected static float textWidth(String text, float scale, String fontId) {
        return MinecraftUiRuntime2612.current().textMetrics().textWidth(text, scale, fontId);
    }

    protected static float textHeight(TextRenderer renderer, float scale) {
        return textHeight(scale, "epsilon-default");
    }

    protected static float textHeight(TextRenderer renderer, float scale, String fontId) {
        return textHeight(scale, fontId);
    }

    protected static float textHeight(float scale, String fontId) {
        return MinecraftUiRuntime2612.current().textMetrics().textHeight(scale, fontId);
    }

    public abstract void render(DeltaTracker deltaTracker);

    public void renderOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
    }

}
