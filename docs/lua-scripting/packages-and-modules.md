# 脚本包与 Module

## 推荐目录结构

```text
~/.epsilon/scripts/example-suite/
├── script.json
├── settings.lua
├── modules/
│   ├── hud-sample.lua
│   └── world-box.lua
├── lib/
│   ├── colors.lua
│   └── ui/
│       └── theme.lua
└── lang/
    ├── en_us.json
    └── zh_cn.json
```

- `script.json` 是唯一的包发现入口和 Module 清单。
- `settings.lua` 可选，只声明包级 Setting。
- `modules/` 中每个 Module 使用独立 `.lua` entrypoint。
- `lib/` 保存各 entrypoint 可 `require` 的共享源码。
- `lang/` 保存包自己的翻译。

## manifest

```json
{
  "schema": 1,
  "api": 1,
  "id": "example-suite",
  "name": "Example Suite",
  "description": "Epsilon multi-module Lua example",
  "version": "1.0.0",
  "authors": ["Epsilon"],
  "settingsEntry": "settings.lua",
  "modules": [
    {
      "id": "hud-sample",
      "name": "HUD Sample",
      "entry": "modules/hud-sample.lua",
      "category": "RENDER",
      "defaultEnabled": false,
      "defaultHidden": false
    },
    {
      "id": "world-box",
      "name": "World Box",
      "entry": "modules/world-box.lua",
      "category": "RENDER",
      "defaultEnabled": false,
      "defaultHidden": false
    }
  ]
}
```

字段规则：

| 字段 | 必需 | 含义 |
|---|---:|---|
| `schema` | 是 | 当前只能为 `1` |
| `api` | 是 | 当前只能为 `1` |
| `id` | 是 | 稳定包 ID，也是配置 owner 的一部分 |
| `name` | 否 | 无翻译时的显示名，省略则使用 ID |
| `description` | 否 | 无翻译时的说明 |
| `version` | 否 | 在 Addon Panel 中显示的包版本 |
| `authors` | 否 | 作者字符串数组 |
| `settingsEntry` | 否 | 包级 Setting entrypoint |
| `modules` | 是 | 至少一个 Module 条目 |

Module 的 `category` 使用当前 Epsilon `Category` 枚举名：`COMBAT`、`PLAYER`、`MOVEMENT` 或 `RENDER`。
大小写不敏感，但建议统一写大写。`defaultEnabled` 和 `defaultHidden` 只作为首次没有 Module 配置时的默认值。

包 ID 和 Module ID 必须匹配：

```text
[a-z0-9][a-z0-9._-]{0,63}
```

同一包内 Module ID 和 entrypoint 都必须唯一。显示名称可以改变，但不要随意修改 ID；稳定 ID 决定 Module
身份、配置路径和 i18n key。包的 owner ID 为 `lua.<packageId>`，Module 的实际身份为 owner ID 与 Module ID
的组合，因此 Java Addon 与 Lua 包同名时也不会冲突。

## entrypoint 路径约束

entrypoint 必须是包目录内的相对 `.lua` 路径：

- 不允许绝对路径、盘符、空路径分量、`.` 或 `..`。
- 不允许越过包根目录。
- 不允许位于 `lib/` 或 `lang/`。
- `settingsEntry` 不能和 Module entrypoint 重复。
- 两个 Module 不能指向同一 entrypoint。

loader 只执行 manifest 中声明的文件，不通过执行 Lua 来发现 Module，也不会为了读取元数据重复执行
entrypoint。

## 每个 Module 一个 runtime

每个 Module entrypoint 都有独立的 LuaJ `Globals`、全局变量和 `package.loaded`。entrypoint 执行前会注入：

| 全局名 | 内容 |
|---|---|
| `mc` | 当前 `net.minecraft.client.Minecraft` Java 对象 |
| `epsilon` | 当前包的只读元数据与 `packageStorage` |
| `addon` | 当前包的只读 Setting API，只能 `setting(id)` |
| `module` | 当前 Module 的 Setting、事件、生命周期和 storage API |
| `luajava` | LuaJ Java interop，加上 Epsilon 的 `bindEventClass` 与 `bindUtilClass` |

`settings.lua` 使用单独的 runtime；它没有 `module`，其中的 `addon` 允许声明包级 Setting。entrypoint 正常
返回后声明阶段关闭，回调中不能再增加 Setting 或生命周期声明。

`epsilon` 提供当前包的只读信息：

```lua
print(epsilon.id)         -- package ID
print(epsilon.version)    -- manifest version，省略时为空字符串
print(epsilon.directory)  -- 规范化后的包目录

epsilon.packageStorage:set("shared-key", "shared value")
```

不要使用 `epsilon.directory` 与字符串拼接绕过 `require` 规则；脚本确需自行访问文件时，应使用 Java 的结构化
Path API，并自行处理权限、编码和异常。

## 共享 lib，不共享状态

```lua
local colors = require("colors")       -- lib/colors.lua
local theme = require("ui.theme")      -- lib/ui/theme.lua 或 lib/ui/theme/init.lua
```

LuaJ 的 `package.path` 只包含当前包的 `lib/?.lua` 和 `lib/?/init.lua`，`package.cpath` 为空。不会自动搜索
Minecraft 工作目录、其他脚本包或 `modules/`。

不同 Module 可以加载同一个 library 文件，但每个 runtime 会各自执行并缓存它。因此共享的是源码，不是返回
table、upvalue 或可变 global。跨 Module 共享持久数据应使用 `epsilon.packageStorage`；只属于当前 Module 的
数据使用 `module.storage`。

由于开放了 `luajava`，这些路径限制是稳定的模块解析规则，不是安全边界。脚本仍能通过公开 Java API 访问
文件系统或其他进程资源。

## 生命周期

```lua
module:on_enable(function()
    -- Module 从关闭变为开启后调用
end)

module:on_disable(function()
    -- Module 关闭、包关闭、系统关闭或 reload 替换旧包时调用
    -- 在这里恢复脚本直接修改的按键、计时器、旋转或其他外部状态
end)

module:on_cleanup(function()
    -- runtime 最终卸载时调用一次
end)
```

事件监听只在所属 Module 开启时订阅。关闭一个 Module 不影响同包其他 Module。连续三次事件或渲染 callback
失败会自动关闭出错 Module；生命周期 callback 的异常会记录日志。

当前 API 没有 timer、defer、协程调度器或指令预算。不要在 callback 中 `yield`，也不要把长时间工作放在
tick、渲染或 packet callback 中。

## entrypoint 的建议写法

顶层只做声明和轻量初始化：

```lua
local shared_range = addon:setting("shared range")
local attempts = module:intSetting({
    id = "attempts",
    default = 3,
    min = 1,
    max = 10,
    step = 1,
    group = module:group("general")
})

module:on("client_tick.post", 0, function(event)
    if mc.player == nil or mc.level == nil then return end
    local range = shared_range:get()
    local count = attempts:get()
end)
```

entrypoint 返回值会被忽略。一个 entrypoint 不能动态创建另一个 Epsilon Module；新增 Module 必须增加新的
manifest 条目和新的 entrypoint 文件。
