# 渲染

## Lumin 2D 资源与帧

LuminGraphics-MC 的 `MinecraftUiRuntime2612` 在渲染线程拥有 2D 帧资源：字体、glyph atlas、Minecraft
纹理与 render target、native extraction bridge、资源重载失效处理，以及帧内 UI 资源的创建和释放。
这些资源只在 runtime 的活动帧中使用；借入的 Minecraft image、view 和 native handle 不由 Epsilon 关闭。

Epsilon 业务代码直接构造公共 Lumin `UiTree` 与 `UiScene`（`com.github.slmpc.lumingraphics.ui.*`），
不再提供 Epsilon 自有的 2D renderer、text renderer、scheduler 或 post-process wrapper。一个 Screen 或
HUD 帧共享一个 `UiScene`，在 `beginFrame()` 与 `endFrame()` 之间提交 UI layer、控件、scissor 和 popup
层级；主题通过 Epsilon 的业务适配层转换为公共 Lumin 类型。

LuminGraphics `1.2.4` 的 `LuminRingBuffer` 会在当前帧耗尽可复用 slot 时按需追加 GPU buffer，
并支持为超过初始 slot 大小的单次写入创建足够大的 slot。扩容后的资源保留到 Ring 关闭，已经提交的
draw command 不会引用被替换或提前释放的 buffer。修改 Ring 生命周期时必须继续实测完整 Dropdown 帧。

字体缺少 code point 时，Lumin atlas 使用内置的 hollow-box glyph 继续完成测量和绘制，不得让
`MissingGlyphException` 穿透 Minecraft GUI。`Font Glyphs Per Frame` 限制同一帧内所有 Lumin 字体合计
写入的真实 glyph 数量；STB 栅格化在 runtime 专用后台线程串行执行，atlas 修改和 GPU 上传仍只在
Render Thread 按预算提交。超过预算和尚未加载的 glyph 临时使用同一占位符，后续帧继续加载。

`Custom Font` 的相对值不依赖 Minecraft 工作目录：先在用户目录的 `.epsilon/fonts/` 下按相对路径
查找，再按文件名递归查找当前用户和操作系统的标准字体目录。显式绝对路径仍可直接使用。Windows
查找 `%LOCALAPPDATA%/Microsoft/Windows/Fonts` 与 `%WINDIR%/Fonts`；macOS 和 Linux 查找各自的
用户字体目录与系统字体目录。切换自定义字体时必须立即创建 Lumin font loader，以便路径不可读或
字体内容无效时在配置边界记录错误并恢复 `epsilon-default`，不能让延迟解析异常逃逸到 GUI 渲染。

`Font Scale` 在 LuminGraphics-MC 的 `MinecraftUiRuntime2612.UI_TEXT_SCALE` 基准上追加业务倍率，
同时更新现有 scene 的文字 renderer 和缓存的文字测量器。默认字体与自定义字体必须共享该倍率；
不得只缩放绘制坐标而遗漏布局测量。

字体 loader 将 `48px` 原样作为 STB 栅格化高度，`4px` SDF padding 额外扩展 glyph bitmap，不能从
栅格化高度中扣除。LuminGraphics-MC 使用与高分辨率栅格匹配的 UI 基准倍率保持默认逻辑字号不变。

`HudElementHolder` 每帧单独构建一棵 HUD `UiTree`：所有启用的 `HudModule` 只向该树追加节点，完成后
整棵树一次提交到 HUD scene。Dropdown、Panel 和 HUD Editor chrome 维护各自的 GUI 树，不接收 HUD
节点；HUD Editor 预览只在独立相对层提交 HUD 树。单个 HUD 元素使用子 layer 隔离构建失败，不能把
未完成节点泄漏到同帧其他元素。

常规 HUD 在 `Gui.extractRenderState` 完成原版 HUD 提取后提交，此时 `GameRenderer.extractGui` 尚未提取
当前 Screen。Dropdown 与 Panel 的 GUI 树因此晚于 HUD 树录制并覆盖 HUD；不得把 HUD 提交移回
`GuiRenderer.draw`，否则 Lumin command buffer 中的 HUD 会晚于 Screen GUI，重新出现在 GUI 上方。

Lumin 先在每个整数 layer 内按 pipeline、scissor 和采样纹理建立批次组，再只对批次组建立遮挡依赖。
同一 layer 不保证不同 pipeline 之间维持图元提交顺序：背景、内容、浮层等存在明确遮挡关系的 pass 必须
使用 `UiRenderBatch.render(tree, relativeLayer)`、带相对层级的 `scene.batch(...)` 或
`UiTree.Scope.layer(...)` 表达顺序。批次组按 bounds 建立 painter-order 依赖，并在可安全重排时优先
选择同 pipeline 的 ready group；layer 仍按递增顺序 flush，跨 layer 不重排。scissor 与采样纹理属于
精确批次键，字体跨 Atlas 页面不会误合并，分段阴影保持独立批次。

Dropdown 沿用 `26.1.2` 的递增局部 layer：scrim、每个 Panel 的 Background/Content 和搜索区
分别创建 scope 并调用 `UiRenderBatch.render(tree, relativeLayer)`，避免后提交的白色底覆盖已开启 Module
的内容。popup 使用独立的 `POPUP` batch/layer；Panel Screen 的 CHROME、CONTENT 相对层和 POPUP 语义层，
以及 HUD Editor 的元素、编辑框与提示层，仍必须维持各自的显式层级。

`MinecraftGuiExtractionBridge2612` 负责提交世界 2D overlay 使用的独立 `GuiGraphicsExtractor` native
state。HUD 的原版物品等不能进入 UI batch 的内容继续在 `renderOverlay(GuiGraphicsExtractor,
DeltaTracker)` 中提交，但直接复用 `GameRenderer.extractGui` 的主 extractor，从而保持 HUD 与 Screen
之间的原版 painter order。

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

2D AABB 边界通过投影全部 8 个顶点并取有效屏幕坐标的最小/最大值计算；没有有效投影或边界完全位于屏幕外时拒绝该边界。

## 字体与原版桥接

业务文本使用 `MinecraftUiRuntime2612.current()` 提供的字体与 text metrics；字体选择、glyph atlas 和
Minecraft texture bridge 随 runtime 的资源重载 generation 更新。原版 `Font` 的 Epsilon 设置 Mixin
仍保留，但 glyph、宽度和 atlas 资源由 LuminGraphics-MC 的公开 Minecraft API 适配。

帧内 UI、3D scheduler 和共享 GPU 生命周期的强制约束见 [`AGENTS.md`](../../AGENTS.md)。
