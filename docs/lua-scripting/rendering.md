# 2D 与 3D 渲染

Lua 渲染 API 只向 Epsilon 已有的共享渲染系统提交内容。脚本不为每个 Module 创建 renderer，也不自行调用
flush。这样可以保持 UiTree layer/scissor 顺序、Render3DScheduler 的统一清理和 GPU 资源生命周期。

## 2D 事件

HUD 和世界 2D overlay 分别使用：

```lua
module:on("render2d.hud", 0, function(ui, event)
end)

module:on("render2d.level", 0, function(ui, event)
end)
```

第一个参数是 `LuaUiContext`，第二个参数是原始 `Render2DEvent.HUD` 或 `Render2DEvent.Level` Java
userdata。原始事件在 Minecraft 26.1.2 中携带 `GuiGraphicsExtractor`，可通过 `event:getGuiGraphics()` 访问；
常规脚本应优先使用 `ui`，以免绕过共享提取/提交流程。

HUD callback 被追加到 Epsilon 当帧共享的 HUD `UiTree`。所有 Lua Level callback 共用一个 `UiScene`，
scene 在 Lumin runtime 变化或 frame failure 时释放并按需重建。

## 基础图元

```lua
module:on("render2d.hud", 10, function(ui, event)
    ui:shadow(8, 8, 120, 28, 4, 8, 0x66000000)
    ui:round_rect(8, 8, 120, 28, 4, 0xDD101418)
    ui:outline(8, 8, 120, 28, 4, 1, 0xFF33AAFF)
    ui:text("Epsilon Lua", 16, 15, 0.65, 0xFFFFFFFF)
    ui:triangle(116, 16, 8, 0, 0xFF33AAFF)
end)
```

颜色是 32-bit ARGB 整数，可以写 `0xAARRGGBB`。支持：

```text
rect(x, y, width, height, color)
round_rect(x, y, width, height, radius, color)
outline(x, y, width, height, radius, thickness, color)
shadow(x, y, width, height, radius, blur, color)
triangle(x, y, size, angle, color)
texture(resource, x, y, width, height, color)
```

`texture` 的 `resource` 是 Lumin/Minecraft 可解析的资源字符串。不要在 render callback 中执行磁盘读取或
动态创建 GPU 资源。

## 文本

```lua
ui:text("Default font", 10, 10, 0.7, 0xFFFFFFFF)
ui:text("Named font", 10, 24, 0.7, 0xFFFFFFFF, "epsilon-default")

local width = ui:text_width("Measured", 0.7)
local height = ui:text_height(0.7)

ui:rotated_text("Rotated", 40, 40, 0.7, 0xFFFFFFFF, nil, 45, 40, 40)
```

绘制与测量必须传相同的 scale 和 font。`rotated_text` 使用默认字体时，font 参数仍需显式传 `nil`，后面
依次是角度和旋转中心。

## layer、scissor 和绝对边界

```lua
ui:layer(2, function(child)
    child:outline(8, 8, 120, 28, 4, 1, 0xFFFFFFFF)
end)

ui:scissor(8, 8, 60, 28, function(clipped)
    clipped:rect(0, 0, 120, 40, 0x8833AAFF)
end)

ui:push_absolute(20, 50, 100, 30, function(child)
    child:rect(0, 0, 100, 30, 0xCC101418)
end)
```

callback 获得一个 child context。需要明确遮挡关系时使用 `layer`，不要依赖同 layer 中不同 pipeline 的偶然
提交顺序。scissor 和 bounds 嵌套由共享 UiTree 管理。

`ui:raw_scope()` 返回当前 `UiTree.Scope` Java userdata，用于尚未封装的公开 UiTree API。scope 只属于当前
树构建 callback；保存后跨帧调用是不受支持的行为。

## 3D 事件

```lua
module:on("render3d", 0, function(render3d, event)
    if mc.player == nil then return end

    local pos = mc.player:position()
    local box = render3d:box(
        pos.x - 0.5, pos.y, pos.z - 0.5,
        pos.x + 0.5, pos.y + 1.0, pos.z + 0.5
    )

    render3d:filled_box(box, 0x4433AAFF)
    render3d:outline_box(box, 0xFF33AAFF, 2.0)
end)
```

`render3d` 是 `LuaRender3DContext`，`event` 是原始 `Render3DEvent` Java userdata。所有 callback 必须使用
高于 `-999` 的 priority；Epsilon 在 `-999` 统一 flush scheduler。

## 3D 命令

```text
blurred_box(box, blurStrength)
filled_box(box, color)
filled_fade_box(box, bottomColor, topColor)
filled_side(box, color, direction)
outline_box(box, color, thickness)
side_outline(box, color, thickness, direction)
line(from, to, color, thickness)
```

`box` 必须是 `net.minecraft.world.phys.AABB`，`from/to` 必须是
`net.minecraft.world.phys.Vec3`。可以使用 helper 创建：

```lua
local box = render3d:box(0, 64, 0, 1, 65, 1)
local from = render3d:vec3(0, 64, 0)
local to = render3d:vec3(1, 65, 1)
render3d:line(from, to, 0xFFFFFFFF, 1.5)
```

也可以直接调用 Java 构造函数：

```lua
local box = luajava.newInstance(
    "net.minecraft.world.phys.AABB",
    0, 64, 0, 1, 65, 1
)
```

`direction` 可传 `"down"`、`"up"`、`"north"`、`"south"`、`"west"`、`"east"`，或 Java
`net.minecraft.core.Direction` userdata。

`render3d:raw_scheduler()` 返回共享 `Render3DScheduler`，只用于调用 wrapper 尚未覆盖的公开方法。不要调用
flush、clear 或关闭 scheduler；context 和 scheduler 提交时机只在当前 render callback 内有效。

## 失败隔离

每个 Lua 渲染 callback 单独捕获异常，失败不会阻止后续 Module 提交。一次成功 callback 会清零该 Module 的
连续失败计数；连续三次失败会关闭该 Module。Level scene 的 frame failure 还会释放 scene，下一帧再创建。

脚本通过 raw Java API 创建的 GPU 资源无法由 Lua runtime 自动推断。若确实创建资源，必须遵循渲染线程约束，
并在 `on_disable`/`on_cleanup` 中调用相应 `close()`。
