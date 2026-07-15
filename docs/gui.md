# Epsilon GUI 渲染架构

> 公共库 API、依赖边界与使用示例见 [GUI Library 文档](gui-library.md)。

## 目标

新的 GUI 管线统一为 `GUI -> GUI Layers -> Render2DScheduler`。屏幕、Dropdown、Popup、MainMenu 与 HUD Editor 不再直接持有一组 renderer 做零散的 `drawAndClear`，而是提交声明式 UI 节点或 scheduler 命令，由统一场景按层调度、合批和 flush。

`UiTree` 已移除旧的 `group/memo` 模式。布局由 `UiTree.layout(...)`、`UiContentBuffer` 和显式 Setting 分组系统协作完成；滚动列表不再把缓存语义混入 DSL 树。

## 总体架构

```mermaid
flowchart TD
    A["GUI Host: PanelScreen / DropdownScreen / MainMenu / HudEditor"] --> B["UiScene"]
    B --> C["UiLayerStack"]
    C --> C1["BACKGROUND"]
    C --> C2["CHROME"]
    C --> C3["CONTENT"]
    C --> C4["FLOATING"]
    C --> C5["POPUP"]
    C --> C6["OVERLAY"]

    C1 --> D["UiRenderBatch"]
    C2 --> D
    C3 --> D
    C4 --> D
    C5 --> D

    D --> E["LuminUiRenderer"]
    E --> F["Render2DCommand"]
    F --> G["Render2DScheduler"]
    G --> H["LayerBucket"]
    H --> H1["Flat ArrayList"]
    H --> H2["Adaptive QuadTree"]
    H1 --> I["BatchPlanner"]
    H2 --> I
    I --> J["RendererPool"]
    J --> K["Resizable LuminRingBuffer"]
```

## Layer 设计

`UiLayer` 是语义层，`UiLayerStack` 把语义层映射到整数 layer。每个语义层预留相对偏移空间，调用方只在自己的语义层里表达局部顺序。

```mermaid
flowchart LR
    A["BACKGROUND 0"] --> B["CHROME 100"]
    B --> C["CONTENT 200"]
    C --> D["FLOATING 300"]
    D --> E["POPUP 400"]
    E --> F["OVERLAY 500"]
```

PanelScreen 当前分层：

- `CHROME -20`：主面板、rail、modules/detail 背景。
- `CONTENT -20`：Category rail。
- `CONTENT 0`：模块列表。
- `CONTENT 10`：客户端设置页。
- `CONTENT 20`：模块详情。
- `POPUP`：弹窗外壳和弹窗普通图元。

DropdownRenderer 将旧的多次 `flush()` 迁移为 pass layer；每个 pass 映射为 `passIndex * 10`，用于保留旧 Dropdown 的视觉顺序。

## UiTree 与自动布局

```mermaid
flowchart TD
    A["UiTree.build"] --> B["Scope: draw nodes"]
    A2["UiTree.layout(bounds)"] --> C["LayoutScope"]
    C --> C1["box(insets)"]
    C --> C2["row(gap)"]
    C --> C3["column(gap)"]
    C2 --> D["LinearScope.item(size)"]
    C2 --> E["LinearScope.fill()"]
    C3 --> D
    C3 --> E
    B --> F["UiNode list"]
    C --> F
    F --> G["LuminUiRenderer"]
```

自动布局原则：

- `LayoutScope` 只负责计算子区域，实际绘制仍写入同一个 `Scope`。
- `row/column` 使用游标推进，`item(size)` 固定尺寸，`fill()` 吃掉剩余空间。
- 旧手写坐标节点保留，用于现有 Panel 和 Dropdown 中需要精细定位的区域。
- 旧 `group/memo` 已移除，缓存不再作为 DSL 树节点存在。

## 显式设置分组布局

设置列表继续通过 `SettingGroup` 和 `.group(...)` 手动声明分组。模块和 Addon 仍按字段声明顺序注册 setting，`SettingLayoutPlanner` 只负责把显式 group 聚合成可折叠 section，并把未分组或 `rootSetting()` 的设置保持为根级内联项。

```mermaid
flowchart TD
    A["Module / Addon settings"] --> B["SettingLayoutPlanner.plan(ownerKey, settings)"]
    B --> C["Section key"]
    B --> D["Section title"]
    B --> E["Section settings"]
    B --> F["Collapsed state"]
    C --> G["SettingListController"]
    C --> H["SettingsContent"]
    C --> I["ModuleButton"]
    G --> J["Panel adaptive rows"]
    H --> K["Dropdown settings panel"]
    I --> L["Dropdown module expansion"]
```

分组规则：

- 使用 `private final SettingGroup sgRender = settingGroup("Render");` 声明分组。
- 使用 `colorSetting(...).group(sgRender)` 把设置放入指定分组。
- 未调用 `.group(...)` 的设置直接内联显示，不生成可折叠 header。
- `rootSetting()` 会作为根级设置直接显示，即使设置本身也带有 group。
- 同名 group 会在 `SettingLayoutPlanner` 中合并成同一个 section，避免同一设置页重复出现相同分组。
- 折叠状态保存在 `SettingGroup` 自身，Panel、Dropdown 和 Addon 使用同一个显式分组状态。

## UiContentBuffer

滚动列表、设置列表和部分 Popup 列表使用 `UiContentBuffer`。

```mermaid
flowchart TD
    A["ViewportNode"] --> B["UiContentBuffer.beginViewport"]
    B --> C["Compile children into local batch"]
    C --> D["queueViewport"]
    D --> E["Main scene flush"]
    E --> F["UiContentBuffer.flush"]
    F --> G["content scissor layer"]
    F --> H["scrollbar layer"]
    F --> I["marquee text layer"]
```

视口内容缓存是局部 scheduler-backed 批次。主 scene 先 flush 背景和普通内容，随后 flush 私有 viewport 缓冲，避免滚动内容被面板背景覆盖。

## Render2DScheduler

```mermaid
flowchart TD
    A["LayerHandle.add*"] --> B["Render2DCommand"]
    B --> C["CommandStore"]
    C --> D["LayerBucket"]
    D --> E{"count >= threshold?"}
    E -->|"否"| F["Flat ArrayList"]
    E -->|"是"| G["QuadTreeBucket"]
    F --> H["stable sequence sort"]
    G --> H
    H --> I["group by kind + scissor"]
    I --> J["RendererPool.acquire"]
    J --> K["emit commands"]
    K --> L["draw batch"]
```

实现要点：

- 小批量 layer 使用 `ArrayList`，避免四叉树固定分配成本。
- 达到阈值后升级到四叉树，跨象限图元保留在父节点，避免重复提交。
- flush 前恢复提交序，再按 `kind + scissor` 规划批次。
- 同一 layer 内会为了合批按 renderer 类型重排；需要严格遮挡顺序时使用更细 layer 表达。
- renderer pool 按 `(kind, scissor)` 复用 renderer，减少 renderer 和 GPU buffer 创建。

## Buffer 策略

`LuminRingBuffer` 支持 `ensureCapacity(requiredBytes)`。2D renderer 使用较小初始 buffer，并在写入前按需求扩容。

当前策略：

- `RectRenderer`：4 KiB 起步。
- `RoundRectRenderer`：4 KiB 起步。
- `RoundRectOutlineRenderer`：4 KiB 起步。
- `ShadowRenderer`：4 KiB 起步。
- `TriangleRenderer`：6 KiB 起步。
- `TextureRenderer`：每纹理桶 16 KiB 起步。
- `TtfTextRenderer`：默认 256 KiB 起步，按 glyph quad 自动扩容。

## Popup 架构

```mermaid
flowchart TD
    A["PanelPopupHost"] --> B["active Popup"]
    B --> C["extractGui(batch POPUP)"]
    C --> D["普通图元进入 UiScene"]
    C --> E["可滚动内容进入 UiContentBuffer"]
    D --> F["scene.flush"]
    F --> G["popup.flush"]
    G --> H["flush private buffers"]
    A --> I["extractOverlay"]
    I --> J["Minecraft item / IME overlay"]
```

Popup 不再默认提前 `flushAndClear` 主 scene batch。普通弹窗图元由主 scene 帧尾统一输出；只有 viewport 和物品预览等私有资源在 `Popup.flush` 或 `extractOverlay` 中处理。

## 入口状态

- `PanelScreen`：已迁移到 `UiScene + UiRenderBatch`。
- `DropdownScreen`：已迁移到 `UiScene`，`DropdownRenderer` pass 映射为 layer。
- `MainMenuScreen`：UI 层已使用 `UiScene`，背景 shader 保持独立目标。
- `HudEditorScreen`：编辑器 chrome 使用 `UiScene`，HUD 模块通过 `renderWithBatch` 进入统一 batch。
- `UiTree.group/memo`：已移除。
- `SettingGroup/.group(...)`：继续保留，设置分段由调用方手动声明，`SettingLayoutPlanner` 只做布局聚合。
