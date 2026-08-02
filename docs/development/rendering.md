# 渲染

## Lumin 2D 资源与帧

LuminGraphics-MC 的 `MinecraftUiRuntime2612` 在渲染线程拥有 2D 帧资源：字体、glyph atlas、Minecraft
纹理与 render target、native extraction bridge、资源重载失效处理，以及帧内 UI 资源的创建和释放。
这些资源只在 runtime 的活动帧中使用；借入的 Minecraft image、view 和 native handle 不由 Epsilon 关闭。

Epsilon 业务代码直接构造公共 Lumin `UiTree` 与 `UiScene`（`com.github.slmpc.lumingraphics.ui.*`），
不再提供 Epsilon 自有的 2D renderer、text renderer、scheduler 或 post-process wrapper。一个 Screen 或
HUD 帧共享一个 `UiScene`，在 `beginFrame()` 与 `endFrame()` 之间提交 UI layer、控件、scissor 和 popup
层级；主题通过 Epsilon 的业务适配层转换为公共 Lumin 类型。

`MinecraftGuiExtractionBridge2612` 负责把原版 `GuiGraphicsExtractor` 的 native state 提交给
LuminGraphics-MC。原版物品等不能进入 UI batch 的内容继续在 `renderOverlay(GuiGraphicsExtractor,
DeltaTracker)` 中提交。

资源重载在安全帧边界处理，避免活动提交引用已失效的 target、纹理或 atlas。GPU 资源仍只由创建它们的
渲染线程释放；调用方不得在无活动帧时保留 command buffer 或 render-target lease。

## 保留的 3D 与共享路径

本次迁移只覆盖 2D UI。`com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler.INSTANCE`
仍是 Epsilon 的 3D 命令收集入口，在 `Render3DEvent` priority `-999` 统一 flush 并清空，生产者
priority 必须大于 `-999`。

`com.github.epsilon.graphics.LuminRenderSystem` 以及现有 3D shaders、buffers 和 immediate paths
继续由 Epsilon 维护和使用；这些 3D/shared 行为没有迁移到 2D runtime，也不得因 2D 改动而改变。

## World To Screen

`WorldToScreen` 提供三个公共函数：

- `calcWorld2ScreenRaw(Vec3)`：按当前 Lumin runtime `SurfaceMetrics.logicalSize()` 返回逻辑屏幕
  `x/y`；`z` 是以世界单位表示的视图空间前向深度，不再额外除以 GUI scale。
- `calcWorld2Screen(Vec3)`：默认入口；深度小于 `Camera.PROJECTION_Z_NEAR` 时返回 `null`。
- `calcScale(Vec3)`：根据当前投影矩阵和前向深度返回透视 UI 缩放；每世界单位投影为 20 个 Lumin 像素时取 `1.0`。

2D AABB 边界通过投影全部 8 个顶点并取屏幕空间最小/最大坐标计算；任一顶点位于摄像机后方或近裁面内时拒绝该边界。

## 字体与原版桥接

业务文本使用 `MinecraftUiRuntime2612.current()` 提供的字体与 text metrics；字体选择、glyph atlas 和
Minecraft texture bridge 随 runtime 的资源重载 generation 更新。原版 `Font` 的 Epsilon 设置 Mixin
仍保留，但 glyph、宽度和 atlas 资源由 LuminGraphics-MC 的公开 Minecraft API 适配。

帧内 UI、3D scheduler 和共享 GPU 生命周期的强制约束见 [`AGENTS.md`](../../AGENTS.md)。
