# Lua 脚本系统

Epsilon 使用 LuaJ 3.0.1 加载本地可信脚本。脚本与 Java Addon 具有相同进程权限，可以通过 `luajava`、`mc`
和注入对象调用 Minecraft、Epsilon 及其他公开 Java API；它不是安全沙箱。脚本系统默认关闭，在 Addon Panel
的 `Lua Scripts` 条目中启用。

## 包结构

脚本根目录为 `~/.epsilon/scripts/`。每个一级目录是一个包。仓库中的完整可运行示例见
[`docs/examples/lua/example-suite`](../examples/lua/example-suite/)：

```text
example-suite/
├── script.json
├── settings.lua
├── modules/
│   ├── hud-sample.lua
│   └── world-box.lua
├── lib/
│   └── colors.lua
└── lang/
    ├── en_us.json
    └── zh_cn.json
```

一个 manifest 可以声明多个 Module，但每个 Module 必须使用不同的 `.lua` entrypoint。每个 entrypoint 有独立
`Globals` 和 `package.loaded`；`lib/` 共享源码文件，不共享加载后的 Lua table 或 mutable global。

## 代码补全

[`epsilon_lib.lua`](../examples/lua/epsilon_lib.lua) 是从 Java 层公开给脚本的 API 整理出的 LuaLS/EmmyLua
`---@meta` 类型库。将文件放入脚本 workspace，或在 Lua Language Server 的 `workspace.library` 中
引用它，即可补全 `module`、`addon`、`epsilon`、`luajava`、Setting、storage 和 2D/3D 渲染接口。
该文件只用于 IDE 元数据，不得在运行时 `require("epsilon_lib")`。Java userdata 的具体公开方法补全仍取决于
IDE 是否安装并配置 Java-Lua 类型插件。

该文件由 Java Lua API 注册点自动生成，不直接手工修改：

```shell
python scripts/generate_epsilon_lib.py
python scripts/generate_epsilon_lib.py --check
```

生成器会从 `LuaEventRegistry` 提取事件 ID 与 `bindEventClass` 名称，并校验 Storage、Setting、Module、
UiTree、Render3D 和 package table 的 Java 导出键。Java 接口与 LuaLS 元数据不一致时生成和检查都会失败。

### VS Code 配置

安装 VS Code 扩展 `sumneko.lua`，然后将 `.epsilon/scripts/` 作为工作区打开。在该目录创建
`.luarc.json`，通过 `workspace.library` 引用生成文件所在目录：

```json
{
  "$schema": "https://raw.githubusercontent.com/LuaLS/vscode-lua/master/setting/schema.json",
  "runtime.version": "Lua 5.2",
  "workspace.library": [
    "D:/Dev/OpenEpsilon/Open-Epsilon/docs/examples/lua"
  ],
  "workspace.checkThirdParty": false
}
```

Windows 路径建议使用 `/`，并把示例中的仓库路径替换为本机实际路径。如果只打开单个脚本包，
也可以把同一份 `.luarc.json` 放在该包根目录。配置完成后从命令面板执行
`Lua: Restart Language Server`；补全库通过 workspace 加载，脚本中不得调用 `require("epsilon_lib")`。

```json
{
  "schema": 1,
  "api": 1,
  "id": "example-suite",
  "name": "Example Suite",
  "version": "1.0.0",
  "authors": ["Example"],
  "settingsEntry": "settings.lua",
  "modules": [
    {
      "id": "hud-sample",
      "name": "HUD Sample",
      "entry": "modules/hud-sample.lua",
      "category": "RENDER",
      "defaultEnabled": false,
      "defaultHidden": false
    }
  ]
}
```

## 注入对象与 Java interop

所有 runtime 都有 `mc`、`epsilon` 和原生 `luajava`。Module entrypoint 另有 `module` 和只读声明模式的
`addon`；`settings.lua` 的 `addon` 允许声明包级 Setting。

```lua
local Minecraft = luajava.bindClass("net.minecraft.client.Minecraft")
local MoveEvent = luajava.bindEventClass("MoveEvent")

assert(mc == Minecraft:getInstance())
module:on_class(MoveEvent, 100, function(event)
    -- 同步运行，可调用 event 的公开 Java 方法
end)
```

`luajava.bindEventClass` 接受 Epsilon 事件短名，包括 `MoveEvent`、`ClientTickEvent.Post`、
`PacketEvent.Send`、`Render2DEvent.HUD` 和 `Render3DEvent`。其他 Java class 继续传全限定名给
`luajava.bindClass`。Packet callback 在原网络/发送线程同步执行，不得 yield，耗时调用会阻塞网络线程。

## Setting

`module` 与声明态 `addon` 提供 `group`、`boolSetting`、`intSetting`、`doubleSetting`、`stringSetting`、
`choiceSetting`、`colorSetting`、`keybindSetting` 和 `stringListSetting`。entrypoint 返回后声明结构冻结。

```lua
local general = module:group("general")
local count = module:intSetting({
    id = "count", default = 3, min = 1, max = 10, step = 1, group = general
})
local range = module:doubleSetting({
    id = "range", default = 4.25, min = 1.0, max = 8.0, step = 0.05,
    group = general, available = function() return count:get() > 1 end
})
local mode = module:choiceSetting({
    id = "mode", default = "normal", choices = {"normal", "strict"}, group = general
})
```

`intSetting` 与 `doubleSetting` 在 Lua 中都返回 `number`，Java 中分别是 `IntSetting` 和 `DoubleSetting`。
整数参数拒绝小数、溢出、NaN 和 infinity；double 参数拒绝非有限值。handle 提供 `get()`、`set(value)` 和
`java_setting()`，包卸载后全部失效。Module 中通过 `addon:setting("id")` 读取包级 Setting。

## 生命周期、事件和存储

```lua
module:on_enable(function() end)
module:on_disable(function() end)
module:on_cleanup(function() end)
module:on("client_tick.post", 0, function(event) end)
```

Module 禁用时取消全部事件订阅；包卸载时先禁用，再执行 `on_cleanup`。`module.storage` 和
`epsilon.packageStorage` 提供 `get`、`set`、`remove`、`clear`，只接受 JSON-compatible 值。前者随 Module
配置保存，后者写入当前 profile 的 `package-state.json`。

## 2D 与 3D 渲染

2D callback 的第一个参数是 UiTree context，第二个参数是原始事件。HUD callback 追加到 Epsilon 的共享 HUD
树；Level callback 由所有 Lua Module 共用一个 `UiScene`。

```lua
module:on("render2d.hud", 0, function(ui, event)
    ui:round_rect(8, 8, 90, 24, 4, 0xCC101418)
    ui:text("Lua", 16, 14, 0.7, 0xFFFFFFFF)
    ui:scissor(8, 8, 90, 24, function(clipped)
        clipped:rect(10, 10, 20, 20, 0xFF4CAF50)
    end)
end)
```

context 提供 `rect`、`round_rect`、`outline`、`shadow`、`text`、`rotated_text`、`texture`、`triangle`、
`layer`、`scissor`、`push_absolute`、`text_width`、`text_height` 和 `raw_scope()`。颜色是 32-bit ARGB。

3D callback 的第一个参数封装共享 `Render3DScheduler`，priority 必须高于 `-999`：

```lua
module:on("render3d", 0, function(render3d, event)
    local box = render3d:box(0, 64, 0, 1, 65, 1)
    render3d:filled_box(box, 0x5533AAFF)
    render3d:outline_box(box, 0xFF33AAFF, 2.0)
end)
```

可用命令包括 `blurred_box`、`filled_box`、`filled_fade_box`、`filled_side`、`outline_box`、
`side_outline`、`line`、`box`、`vec3` 和 `raw_scheduler()`。

## i18n 与管理

`lang/<code>.json` 的叶节点必须全部是 string。解析顺序是当前语言、`en_us`、manifest/ID fallback。完整
key 和示例见 [`lua-scripting-plan.md`](lua-scripting-plan.md#i18n)。

Addon Panel 同时显示 Java Addon 和已发现的脚本包，不再显示重复的 Lua 系统条目。Lua 包有代码标识，
Panel 与 Dropdown 两种 GUI 中每个包的详情区都有独立开关和 Reload 按钮。关闭包时保存 Module 状态快照，
然后注销 Module、SettingHost、事件监听并关闭全部 Lua runtime；重新开启时从磁盘重新加载并恢复注册。系统不监听
脚本文件变化，只在启动、全局开关重新开启或手动 Reload 时读取脚本。Reload 使用候选 runtime；
候选失败时保留当前包，错误显示在脚本条目中。
