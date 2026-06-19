package com.github.epsilon.gui.dsl;

import com.github.epsilon.graphics.text.ttf.TtfFontLoader;
import com.github.epsilon.gui.panel.PanelLayout;
import com.github.epsilon.gui.panel.utils.PanelContentBuffer;
import com.github.epsilon.utils.render.animation.Animation;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 面板 UI 的声明式节点树。
 * <p>
 * 调用方通过 {@link #build(Consumer)} 在一个 {@link Scope} 中描述本帧需要绘制的内容，
 * 然后再由 {@link PanelUiCompiler} 将树结构编译进具体 renderer 或视口缓冲。
 */
public class PanelUiTree {

    private final List<UiNode> nodes;
    private final boolean hasActiveAnimations;

    private PanelUiTree(List<UiNode> nodes, boolean hasActiveAnimations) {
        this.nodes = nodes;
        this.hasActiveAnimations = hasActiveAnimations;
    }

    /**
     * 构建一棵新的 UI 树。
     *
     * @param content 用于向 {@link Scope} 写入节点的 DSL 构建函数
     * @return 当前帧的 UI 树快照
     */
    public static PanelUiTree build(Consumer<Scope> content) {
        Scope scope = new Scope();
        content.accept(scope);
        return new PanelUiTree(List.copyOf(scope.nodes), scope.hasActiveAnimations);
    }

    public static void clearMemoCache() {
        // memo 缓存已经移除；保留该方法作为兼容空实现。
    }

    List<UiNode> nodes() {
        return nodes;
    }

    /**
     * 返回该树是否仍包含未结束的动画。
     * <p>
     * 该标记通常被面板层用于决定是否需要继续触发重绘。
     *
     * @return 若仍有活动动画则为 {@code true}
     */
    public boolean hasActiveAnimations() {
        return hasActiveAnimations;
    }

    /**
     * UI DSL 的构建作用域。
     * <p>
     * 作用域负责收集节点、传播动画活动状态，并在需要时把子树包装成 group、memo 或 viewport。
     */
    public static final class Scope {

        private List<UiNode> nodes = new ArrayList<>();
        private boolean hasActiveAnimations;

        /**
         * 向当前作用域追加单个节点，并在需要时绑定到相对 layer。
         * <p>
         * 高频渲染门面会大量调用单节点重载；这里直接包装 {@link LayeredNode}，
         * 避免为了一个图元额外创建临时 {@link Scope} 和子列表。
         */
        private void addNode(int layer, UiNode node) {
            if (layer == 0) {
                nodes.add(node);
            } else {
                nodes.add(new LayeredNode(layer, node));
            }
        }

        /**
         * 生成当前作用域内容的不可变树快照。
         * <p>
         * 长生命周期渲染门面会复用同一个 {@link Scope} 收集整帧内容，
         * 快照用于把当帧节点稳定地交给批次编译。
         */
        public PanelUiTree snapshot() {
            return new PanelUiTree(List.copyOf(nodes), hasActiveAnimations);
        }

        /**
         * 清空当前作用域，便于长生命周期的渲染门面复用。
         */
        public void clear() {
            nodes.clear();
            hasActiveAnimations = false;
        }

        /**
         * 构建一个普通子分组。
         * <p>
         * 分组本身不引入新的绘制语义，只用于组织子节点并合并动画状态。
         *
         * @param content 子分组内容
         */
        public void group(Consumer<Scope> content) {
            CaptureResult capture = capture(content);
            hasActiveAnimations = hasActiveAnimations || capture.hasActiveAnimations();
            nodes.add(new GroupNode(capture.nodes()));
        }

        /**
         * 构建一个语义上的 memo 子树。
         * <p>
         * 当前实现不再保留全局缓存；该方法仅作为兼容入口，行为等同于 {@link #group(Consumer)}。
         *
         * @param key       缓存的逻辑键
         * @param signature 当前子树的状态签名
         * @param content   子树内容
         */
        public void memo(Object key, long signature, Consumer<Scope> content) {
            CaptureResult capture = capture(content);
            hasActiveAnimations = hasActiveAnimations || capture.hasActiveAnimations();
            nodes.add(new GroupNode(capture.nodes()));
        }

        /**
         * 在一个相对层级中构建子树。
         * <p>
         * 层级只影响支持 layer 的批次输出；旧的直接编译入口会把它当作普通分组处理。
         *
         * @param layer   相对层级偏移，数值越大越靠上
         * @param content 子层级内容
         */
        public void layer(int layer, Consumer<Scope> content) {
            CaptureResult capture = capture(content);
            hasActiveAnimations = hasActiveAnimations || capture.hasActiveAnimations();
            nodes.add(new LayerNode(layer, capture.nodes()));
        }

        /**
         * 推进一个布尔目标动画，并返回当前值。
         *
         * @param animation 动画实例
         * @param target    目标布尔状态
         * @return 动画当前值，通常在 {@code 0..1} 之间
         */
        public float animate(Animation animation, boolean target) {
            return animate(animation, target ? 1.0f : 0.0f);
        }

        /**
         * 推进一个浮点目标动画，并返回当前值。
         *
         * @param animation 动画实例
         * @param target    目标值
         * @return 动画当前值
         */
        public float animate(Animation animation, float target) {
            animation.run(target);
            hasActiveAnimations = hasActiveAnimations || !animation.isFinished();
            return animation.getValue();
        }

        public void shadow(float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            nodes.add(new ShadowNode(x, y, width, height, radius, radius, radius, radius, blurRadius, color));
        }

        public void shadow(int layer, float x, float y, float width, float height, float radius, float blurRadius, Color color) {
            addNode(layer, new ShadowNode(x, y, width, height, radius, radius, radius, radius, blurRadius, color));
        }

        public void shadow(float x, float y, float width, float height,
                           float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                           float blurRadius, Color color) {
            nodes.add(new ShadowNode(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, blurRadius, color));
        }

        public void shadow(int layer, float x, float y, float width, float height,
                           float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                           float blurRadius, Color color) {
            addNode(layer, new ShadowNode(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, blurRadius, color));
        }

        public void roundRect(float x, float y, float width, float height, float radius, Color color) {
            nodes.add(new RoundRectNode(x, y, width, height, radius, radius, radius, radius, color));
        }

        public void roundRect(int layer, float x, float y, float width, float height, float radius, Color color) {
            addNode(layer, new RoundRectNode(x, y, width, height, radius, radius, radius, radius, color));
        }

        public void roundRect(float x, float y, float width, float height,
                              float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                              Color color) {
            nodes.add(new RoundRectNode(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color));
        }

        public void roundRect(int layer, float x, float y, float width, float height,
                              float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                              Color color) {
            addNode(layer, new RoundRectNode(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft, color));
        }

        public void roundRectGradient(float x, float y, float width, float height, float radius,
                                      Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            roundRectGradient(x, y, width, height, radius, radius, radius, radius, topLeft, bottomLeft, bottomRight, topRight);
        }

        public void roundRectGradient(float x, float y, float width, float height,
                                      float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                                      Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            nodes.add(new RoundRectGradientNode(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    topLeft, bottomLeft, bottomRight, topRight));
        }

        public void roundRectGradient(int layer, float x, float y, float width, float height,
                                      float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                                      Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            addNode(layer, new RoundRectGradientNode(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    topLeft, bottomLeft, bottomRight, topRight));
        }

        public void roundRectVerticalGradient(float x, float y, float width, float height, float radius, Color top, Color bottom) {
            roundRectGradient(x, y, width, height, radius, top, bottom, bottom, top);
        }

        public void roundRectHorizontalGradient(float x, float y, float width, float height, float radius, Color left, Color right) {
            roundRectGradient(x, y, width, height, radius, left, left, right, right);
        }

        public void roundRectHorizontalGradient(int layer, float x, float y, float width, float height, float radius, Color left, Color right) {
            roundRectGradient(layer, x, y, width, height, radius, radius, radius, radius, left, left, right, right);
        }

        public void rect(float x, float y, float width, float height, Color color) {
            nodes.add(new RectNode(x, y, width, height, color));
        }

        public void rect(int layer, float x, float y, float width, float height, Color color) {
            addNode(layer, new RectNode(x, y, width, height, color));
        }

        public void rectOutline(float x, float y, float width, float height, float outlineWidth, Color color) {
            nodes.add(new RectOutlineNode(x, y, width, height, outlineWidth, color));
        }

        public void rectOutline(int layer, float x, float y, float width, float height, float outlineWidth, Color color) {
            addNode(layer, new RectOutlineNode(x, y, width, height, outlineWidth, color));
        }

        public void rectGradient(float x, float y, float width, float height,
                                 Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            nodes.add(new RectGradientNode(x, y, width, height, topLeft, bottomLeft, bottomRight, topRight));
        }

        public void rectGradient(int layer, float x, float y, float width, float height,
                                 Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) {
            addNode(layer, new RectGradientNode(x, y, width, height, topLeft, bottomLeft, bottomRight, topRight));
        }

        public void rectVerticalGradient(float x, float y, float width, float height, Color top, Color bottom) {
            rectGradient(x, y, width, height, top, bottom, bottom, top);
        }

        public void rectHorizontalGradient(float x, float y, float width, float height, Color left, Color right) {
            rectGradient(x, y, width, height, left, left, right, right);
        }

        public void outline(float x, float y, float width, float height, float radius, float outlineWidth, Color color) {
            outline(x, y, width, height, radius, radius, radius, radius, outlineWidth, color);
        }

        public void outline(float x, float y, float width, float height,
                            float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                            float outlineWidth, Color color) {
            nodes.add(new OutlineNode(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    outlineWidth, color));
        }

        public void outline(int layer, float x, float y, float width, float height,
                            float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                            float outlineWidth, Color color) {
            addNode(layer, new OutlineNode(x, y, width, height, radiusTopLeft, radiusTopRight, radiusBottomRight, radiusBottomLeft,
                    outlineWidth, color));
        }

        public void text(String text, float x, float y, float scale, Color color) {
            nodes.add(new TextNode(text, x, y, scale, color, null));
        }

        public void text(int layer, String text, float x, float y, float scale, Color color) {
            addNode(layer, new TextNode(text, x, y, scale, color, null));
        }

        public void text(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
            nodes.add(new TextNode(text, x, y, scale, color, fontLoader));
        }

        public void text(int layer, String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader) {
            addNode(layer, new TextNode(text, x, y, scale, color, fontLoader));
        }

        /**
         * 添加一个带独立裁剪区域的文本节点（跑马灯）。
         * <p>
         * 仅在 viewport 上下文中生效；装入主批次后，会与视口区域取交集后单独以 scissor 输出。
         */
        public void marqueeText(String text, float x, float y, float scale, Color color, PanelLayout.Rect clip) {
            marqueeText(text, x, y, scale, color, null, clip);
        }

        public void marqueeText(String text, float x, float y, float scale, Color color, TtfFontLoader fontLoader, PanelLayout.Rect clip) {
            nodes.add(new MarqueeTextNode(text, x, y, scale, color, fontLoader, clip));
        }

        public void button(float x, float y, float width, float height, float radius, Color background,
                           String label, float labelScale, Color labelColor) {
            button(new ButtonElement(new PanelLayout.Rect(x, y, width, height), radius, background, label, labelScale, labelColor));
        }

        /**
         * 添加一个标准按钮节点。
         *
         * @param bounds     按钮区域
         * @param radius     圆角半径
         * @param background 背景色
         * @param label      文本标签
         * @param labelScale 文本缩放
         * @param labelColor 文本颜色
         */
        public void button(PanelLayout.Rect bounds, float radius, Color background,
                           String label, float labelScale, Color labelColor) {
            button(new ButtonElement(bounds, radius, background, label, labelScale, labelColor));
        }

        public void button(ButtonElement element) {
            PanelLayout.Rect bounds = element.bounds();
            nodes.add(new ButtonNode(bounds.x(), bounds.y(), bounds.width(), bounds.height(), element.radius(),
                    element.background(), element.label(), element.labelScale(), element.labelColor()));
        }

        public void switchControl(PanelLayout.Rect bounds, float toggleProgress, float hoverProgress) {
            toggleSwitch(new SwitchElement(bounds, toggleProgress, hoverProgress));
        }

        /**
         * 添加一个开关控件节点。
         *
         * @param bounds         开关区域
         * @param toggleProgress 开关开启进度
         * @param hoverProgress  悬停高亮进度
         */
        public void toggle(PanelLayout.Rect bounds, float toggleProgress, float hoverProgress) {
            toggleSwitch(new SwitchElement(bounds, toggleProgress, hoverProgress));
        }

        public void toggleSwitch(SwitchElement element) {
            nodes.add(new SwitchNode(element.bounds(), element.toggleProgress(), element.hoverProgress()));
        }

        public void toggle(SwitchElement element) {
            toggleSwitch(element);
        }

        public void filledField(PanelLayout.Rect bounds, boolean focused, float hoverProgress) {
            input(new InputElement(bounds, focused, hoverProgress,
                    0.0f, new Color(0, 0, 0, 0), 0.0f,
                    6.0f, null, 0.0f, new Color(0, 0, 0, 0),
                    null, null,
                    null, null,
                    null, 0.0f, null));
        }

        /**
         * 添加一个完整输入框节点。
         * <p>
         * 该节点可以携带光标、选区、聚焦环和尾部提示等信息。
         *
         * @param element 输入框描述
         */
        public void input(InputElement element) {
            nodes.add(new InputNode(element));
        }

        /**
         * 以简化参数形式添加输入框节点。
         *
         * @param bounds            输入框区域
         * @param focused           是否处于焦点状态
         * @param hoverProgress     悬停进度
         * @param textInset         文本左侧内边距
         * @param text              显示文本
         * @param textScale         文本缩放
         * @param textColor         文本颜色
         * @param caretIndex        光标索引，可为空
         * @param caretColor        光标颜色，可为空
         * @param trailingHint      右侧提示文字，可为空
         * @param trailingHintScale 提示文字缩放
         * @param trailingHintColor 提示文字颜色，可为空
         */
        public void input(PanelLayout.Rect bounds, boolean focused, float hoverProgress,
                          float textInset, String text, float textScale, Color textColor,
                          Integer caretIndex, Color caretColor,
                          String trailingHint, float trailingHintScale, Color trailingHintColor) {
            input(new InputElement(bounds, focused, hoverProgress,
                    0.0f, new Color(0, 0, 0, 0), 0.0f,
                    textInset, text, textScale, textColor,
                    null, null,
                    caretIndex, caretColor,
                    trailingHint, trailingHintScale, trailingHintColor));
        }

        /**
         * 以完整参数形式添加输入框节点。
         *
         * @param bounds            输入框区域
         * @param focused           是否处于焦点状态
         * @param hoverProgress     悬停进度
         * @param focusRingProgress 聚焦环进度
         * @param focusRingColor    聚焦环颜色
         * @param focusRingInset    聚焦环向外扩张的距离
         * @param textInset         文本左侧内边距
         * @param text              显示文本
         * @param textScale         文本缩放
         * @param textColor         文本颜色
         * @param selection         文本选区，可为空
         * @param selectionColor    选区颜色，可为空
         * @param caretIndex        光标索引，可为空
         * @param caretColor        光标颜色，可为空
         * @param trailingHint      右侧提示文字，可为空
         * @param trailingHintScale 提示文字缩放
         * @param trailingHintColor 提示文字颜色，可为空
         */
        public void input(PanelLayout.Rect bounds, boolean focused, float hoverProgress,
                          float focusRingProgress, Color focusRingColor, float focusRingInset,
                          float textInset, String text, float textScale, Color textColor,
                          SelectionRange selection, Color selectionColor,
                          Integer caretIndex, Color caretColor,
                          String trailingHint, float trailingHintScale, Color trailingHintColor) {
            input(new InputElement(bounds, focused, hoverProgress,
                    focusRingProgress, focusRingColor, focusRingInset,
                    textInset, text, textScale, textColor,
                    selection, selectionColor,
                    caretIndex, caretColor,
                    trailingHint, trailingHintScale, trailingHintColor));
        }

        public void assistChip(PanelLayout.Rect bounds, String label, float textScale, Color background, Color foreground,
                               String trailingIcon, float trailingIconScale, TtfFontLoader trailingIconFont) {
            nodes.add(new AssistChipNode(bounds, label, textScale, background, foreground, trailingIcon, trailingIconScale, trailingIconFont));
        }

        public void chip(PanelLayout.Rect bounds, String label, float textScale, Color background, Color foreground,
                         String trailingIcon, float trailingIconScale, TtfFontLoader trailingIconFont) {
            assistChip(bounds, label, textScale, background, foreground, trailingIcon, trailingIconScale, trailingIconFont);
        }

        public void segmentedControl(PanelLayout.Rect bounds, String leadingLabel, String trailingLabel,
                                     float progress, float hoverProgress) {
            nodes.add(new SegmentedControlNode(bounds, leadingLabel, trailingLabel, progress, hoverProgress));
        }

        public void segmented(PanelLayout.Rect bounds, String leadingLabel, String trailingLabel,
                              float progress, float hoverProgress) {
            segmentedControl(bounds, leadingLabel, trailingLabel, progress, hoverProgress);
        }

        public void iconButton(PanelLayout.Rect bounds, String label, float scale, Color tone, float hoverProgress) {
            nodes.add(new IconButtonNode(bounds, label, scale, tone, hoverProgress));
        }

        /**
         * 添加一个带阴影的弹窗卡片外壳节点。
         *
         * @param bounds       卡片区域
         * @param radius       圆角半径
         * @param blurRadius   阴影模糊半径
         * @param shadowColor  阴影颜色
         * @param surfaceColor 面颜色
         */
        public void popupCard(PanelLayout.Rect bounds, float radius, float blurRadius, Color shadowColor, Color surfaceColor) {
            nodes.add(new PopupCardNode(bounds, radius, blurRadius, shadowColor, surfaceColor));
        }

        /**
         * 添加一个滑条节点。
         * <p>
         * 该节点同时描述底轨、激活轨和拖柄，可用于数值设置、颜色通道等场景。
         *
         * @param bounds         轨道区域
         * @param progress       当前进度，通常在 {@code 0..1} 之间
         * @param trackRadius    轨道圆角
         * @param trackColor     底轨颜色
         * @param activeEndInset 激活轨终点内缩
         * @param activeMinWidth 激活轨最小宽度
         * @param activeColor    激活轨颜色
         * @param handleWidth    拖柄宽度
         * @param handleHeight   拖柄高度
         * @param handleRadius   拖柄圆角
         * @param handleColor    拖柄颜色
         */
        public void slider(PanelLayout.Rect bounds, float progress, float trackRadius,
                           Color trackColor, float activeEndInset, float activeMinWidth, Color activeColor,
                           float handleWidth, float handleHeight, float handleRadius, Color handleColor) {
            nodes.add(new SliderNode(bounds, progress, trackRadius, trackColor, activeEndInset, activeMinWidth, activeColor,
                    handleWidth, handleHeight, handleRadius, handleColor));
        }

        /**
         * 添加一个chevron三角箭头节点。
         *
         * @param centerX  三角形中心 X
         * @param centerY  三角形中心 Y
         * @param size     三角形半尺寸
         * @param progress 旋转进度：0 = 指向右，1 = 指向下
         * @param color    三角形颜色
         */
        public void triangle(float centerX, float centerY, float size, float progress, Color color) {
            nodes.add(new TriangleNode(centerX, centerY, size, progress, color));
        }

        public void triangle(int layer, float centerX, float centerY, float size, float progress, Color color) {
            addNode(layer, new TriangleNode(centerX, centerY, size, progress, color));
        }

        /**
         * 在独立视口缓冲中构建一个可裁剪子树。
         * <p>
         * 子树会先被编译进 {@link PanelContentBuffer}，稍后在统一 flush 阶段按视口裁剪后输出。
         *
         * @param buffer        目标内容缓冲
         * @param viewport      视口区域
         * @param guiHeight     当前 GUI 高度，用于换算 scissor 坐标
         * @param scroll        当前滚动偏移
         * @param maxScroll     最大滚动偏移
         * @param contentHeight 内容总高度
         * @param content       视口内部的子树内容
         */
        public void viewport(PanelContentBuffer buffer, PanelLayout.Rect viewport, int guiHeight,
                             float scroll, float maxScroll, float contentHeight,
                             Consumer<Scope> content) {
            CaptureResult capture = capture(content);
            hasActiveAnimations = hasActiveAnimations || capture.hasActiveAnimations();
            nodes.add(new ViewportNode(buffer, viewport, guiHeight, scroll, maxScroll, contentHeight, capture.nodes()));
        }

        private CaptureResult capture(Consumer<Scope> content) {
            List<UiNode> parent = nodes;
            boolean parentAnimations = hasActiveAnimations;
            // 子树构建期间临时切换收集列表，finally 中恢复父作用域，避免子节点泄露到父层。
            nodes = new ArrayList<>();
            hasActiveAnimations = false;
            try {
                content.accept(this);
                return new CaptureResult(List.copyOf(nodes), hasActiveAnimations);
            } finally {
                nodes = parent;
                hasActiveAnimations = parentAnimations;
            }
        }
    }

    private record CaptureResult(List<UiNode> nodes, boolean hasActiveAnimations) {
    }


    /**
     * DSL 节点只描述本帧的绘制意图，不持有 renderer 状态。
     * <p>
     * 编译阶段会按节点类型把它们分发到具体 renderer 或视口缓冲。
     */
    sealed interface UiNode permits GroupNode, LayerNode, LayeredNode, ShadowNode, RoundRectNode, RoundRectGradientNode, RectNode, RectGradientNode, RectOutlineNode, OutlineNode, TextNode, MarqueeTextNode, ButtonNode, SwitchNode, FilledFieldNode, InputNode, AssistChipNode, SegmentedControlNode, IconButtonNode, PopupCardNode, SliderNode, TriangleNode, ViewportNode {
    }

    /**
     * 按钮节点的语义描述。
     */
    public record ButtonElement(PanelLayout.Rect bounds, float radius, Color background,
                                String label, float labelScale, Color labelColor) {
    }

    /**
     * 开关节点的语义描述。
     */
    public record SwitchElement(PanelLayout.Rect bounds, float toggleProgress, float hoverProgress) {
    }

    /**
     * 输入框中文字选区的半开区间描述。
     */
    public record SelectionRange(int start, int end) {
    }

    /**
     * 输入框节点的完整语义描述。
     * <p>
     * 用于承载文本、焦点、选区、光标与尾部提示等输入态信息。
     */
    public record InputElement(PanelLayout.Rect bounds, boolean focused, float hoverProgress,
                               float focusRingProgress, Color focusRingColor, float focusRingInset,
                               float textInset, String text, float textScale, Color textColor,
                               SelectionRange selection, Color selectionColor,
                               Integer caretIndex, Color caretColor,
                               String trailingHint, float trailingHintScale,
                               Color trailingHintColor) {
    }

    record GroupNode(List<UiNode> children) implements UiNode {
    }

    /**
     * 子树级 layer 偏移；适合一组节点整体提升或下沉。
     */
    record LayerNode(int layer, List<UiNode> children) implements UiNode {
    }

    /**
     * 单节点 layer 包装；供高频图元入口使用，减少小列表分配。
     */
    record LayeredNode(int layer, UiNode child) implements UiNode {
    }

    record ShadowNode(float x, float y, float width, float height,
                      float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                      float blurRadius, Color color) implements UiNode {
    }

    record RoundRectNode(float x, float y, float width, float height,
                         float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                         Color color) implements UiNode {
    }

    record RoundRectGradientNode(float x, float y, float width, float height,
                                 float radiusTopLeft, float radiusTopRight, float radiusBottomRight,
                                 float radiusBottomLeft,
                                 Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) implements UiNode {
    }

    record RectNode(float x, float y, float width, float height, Color color) implements UiNode {
    }

    record RectGradientNode(float x, float y, float width, float height,
                            Color topLeft, Color bottomLeft, Color bottomRight, Color topRight) implements UiNode {
    }

    record RectOutlineNode(float x, float y, float width, float height, float outlineWidth,
                           Color color) implements UiNode {
    }

    record OutlineNode(float x, float y, float width, float height,
                       float radiusTopLeft, float radiusTopRight, float radiusBottomRight, float radiusBottomLeft,
                       float outlineWidth, Color color) implements UiNode {
    }

    record TextNode(String text, float x, float y, float scale, Color color,
                    TtfFontLoader fontLoader) implements UiNode {
    }

    record MarqueeTextNode(String text, float x, float y, float scale, Color color,
                           TtfFontLoader fontLoader, PanelLayout.Rect clip) implements UiNode {
    }

    record ButtonNode(float x, float y, float width, float height, float radius, Color background,
                      String label, float labelScale, Color labelColor) implements UiNode {
    }

    record SwitchNode(PanelLayout.Rect bounds, float toggleProgress, float hoverProgress) implements UiNode {
    }

    record FilledFieldNode(PanelLayout.Rect bounds, boolean focused, float hoverProgress) implements UiNode {
    }

    record InputNode(InputElement element) implements UiNode {
    }

    record AssistChipNode(PanelLayout.Rect bounds, String label, float textScale, Color background, Color foreground,
                          String trailingIcon, float trailingIconScale,
                          TtfFontLoader trailingIconFont) implements UiNode {
    }

    record SegmentedControlNode(PanelLayout.Rect bounds, String leadingLabel, String trailingLabel,
                                float progress, float hoverProgress) implements UiNode {
    }

    record IconButtonNode(PanelLayout.Rect bounds, String label, float scale, Color tone,
                          float hoverProgress) implements UiNode {
    }

    record PopupCardNode(PanelLayout.Rect bounds, float radius, float blurRadius, Color shadowColor,
                         Color surfaceColor) implements UiNode {
    }

    record SliderNode(PanelLayout.Rect bounds, float progress, float trackRadius, Color trackColor,
                      float activeEndInset, float activeMinWidth, Color activeColor,
                      float handleWidth, float handleHeight, float handleRadius, Color handleColor) implements UiNode {
    }

    record TriangleNode(float centerX, float centerY, float size, float progress, Color color) implements UiNode {
    }

    record ViewportNode(PanelContentBuffer buffer, PanelLayout.Rect viewport, int guiHeight,
                        float scroll, float maxScroll, float contentHeight,
                        List<UiNode> children) implements UiNode {
    }

}
