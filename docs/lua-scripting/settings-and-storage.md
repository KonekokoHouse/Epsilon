# Setting 与存储

Lua Module 使用与 Java Module 相同的 Setting 类型、GUI 控件、配置保存和 i18n 流程。脚本包还可以像
`EpsilonAddon` 一样持有一组包级 Setting；这里的 “Addon Setting” 指 `SettingHost` 上的 Setting，不是一个
名为 `AddonSetting` 的 Java 类型。

## Module Setting

在 Module entrypoint 顶层声明：

```lua
local general = module:group("general")

local enabled_check = module:boolSetting({
    id = "enabled check",
    default = true,
    group = general,
    changed = function(value)
        print("enabled check = " .. tostring(value))
    end
})

local attempts = module:intSetting({
    id = "attempts",
    default = 3,
    min = 1,
    max = 10,
    step = 1,
    group = general
})

local range = module:doubleSetting({
    id = "range",
    default = 4.25,
    min = 1.0,
    max = 8.0,
    step = 0.05,
    group = general,
    available = function()
        return enabled_check:get() and attempts:get() > 1
    end
})
```

`available` 是延迟执行的可见性/可用性依赖，不要在声明阶段预先计算。支持 `changed` 的类型会在值变化时把
新值传给 callback。

## 包级 Setting

在 manifest 设置 `"settingsEntry": "settings.lua"`，并在 `settings.lua` 中声明：

```lua
local general = addon:group("general")

addon:doubleSetting({
    id = "shared range",
    default = 4.25,
    min = 1.0,
    max = 8.0,
    step = 0.05,
    group = general
})
```

Module entrypoint 中的 `addon` 是只读代理，只能按稳定 ID 取得已经声明的 Setting：

```lua
local shared_range = addon:setting("shared range")

module:on("client_tick.post", 0, function(event)
    local value = shared_range:get()
end)
```

不能在 Module entrypoint 中调用 `addon:doubleSetting(...)`，也不能在任意运行时 callback 中声明新的
Setting。包级 Setting 会显示在该脚本包的 Addon Panel/Dropdown 详情中。

## Setting 类型

| Lua API | Java 类型 | 必需字段 | Lua 值 |
|---|---|---|---|
| `boolSetting` | `BoolSetting` | `id`, `default` | `boolean` |
| `intSetting` | `IntSetting` | `id`, `default`, `min`, `max`, `step` | 整数 `number` |
| `doubleSetting` | `DoubleSetting` | `id`, `default`, `min`, `max`, `step` | `number` |
| `stringSetting` | `StringSetting` | `id`, `default` | `string` |
| `choiceSetting` | `ChoiceSetting` | `id`, `default`, `choices` | `string` |
| `colorSetting` | `ColorSetting` | `id`, `default` | 32-bit ARGB `number` |
| `keybindSetting` | `KeybindSetting` | `id`, `default` | GLFW key code `number` |
| `stringListSetting` | `StringListSetting` | `id`, `default` | string array table |

所有 spec 都可以包含 `group` 和 `available`。`boolSetting`、`intSetting`、`doubleSetting`、`stringSetting`
与 `choiceSetting` 还支持 `changed`。`colorSetting` 支持可选的 `allowAlpha`，默认 `true`。

```lua
local mode = module:choiceSetting({
    id = "mode",
    default = "normal",
    choices = {"normal", "strict"},
    group = general
})

local color = module:colorSetting({
    id = "color",
    default = 0xCC33AAFF,
    allowAlpha = true,
    group = general
})

local names = module:stringListSetting({
    id = "names",
    default = {"Alice", "Bob"},
    group = general
})
```

Setting 与 SettingGroup ID 必须为小写，并匹配 `[a-z0-9][a-z0-9._ -]{0,63}`。不允许 `..`，也不允许
以空格结尾。ID 中允许空格，这是为了与 Epsilon 的 i18n key 规则一致。

## number 语义

LuaJ 中 `intSetting` 和 `doubleSetting` 的值都属于 Lua `number`：

```lua
assert(type(attempts:get()) == "number")
assert(type(range:get()) == "number")
```

Java 层仍分别创建 `IntSetting` 和 `DoubleSetting`，因此 GUI、步长、范围和 JSON 类型不会混淆：

- `intSetting` 的 `default/min/max/step` 与 `set(value)` 必须是 32-bit 有限整数；`4.5`、NaN、infinity
  和溢出值会直接报错，不会截断。
- `doubleSetting` 接受有限 number，拒绝 NaN 和 infinity。
- `min <= default <= max`，且 `step > 0`。
- `colorSetting` 接受有符号 Java int 范围或 `0x00000000` 到 `0xFFFFFFFF` 的整数。
- `keybindSetting` 接受 32-bit 整数。

## Setting handle

每个声明方法和 `setting(id)` 都返回 handle：

```lua
local normalized = range:set(5.0)
local current = range:get()
local java_setting = range:java_setting()

print(range.id)
print(range.owner)
```

`java_setting()` 返回真实 Java `Setting` userdata，适合调用 Lua API 尚未封装的公开方法。包被关闭、重载或
全局 Lua 系统关闭后，旧 handle 随 runtime 失效，不要把它保存到 Java static 对象中继续使用。

## Module storage

`module.storage` 随该 Module 配置保存：

```lua
local count = module.storage:get("count") or 0
module.storage:set("count", count + 1)
module.storage:remove("old-key")
-- module.storage:clear()
```

它适合只属于当前 Module 的持久状态。Module 配置文件位于当前 profile 的
`lua.<packageId>/<moduleId>.json`，storage 写在其自定义状态中。

## package storage

`epsilon.packageStorage` 由同包所有 runtime 访问，并写入当前 profile 的 `package-state.json`：

```lua
local cache = epsilon.packageStorage:get("targets") or {}
cache[#cache + 1] = "example"
epsilon.packageStorage:set("targets", cache)
```

它适合跨 Module、包关闭/开启和 reload 保存共享数据。Java 实现对访问进行了同步，但复合的“读取、修改、
写回”不是跨多个 API 调用的原子事务；多个线程都可能修改同一 key 时，脚本应自行设计冲突规则。

## 可存储值

两种 storage 都只接受 JSON-compatible 值：`boolean`、有限 `number`、`string`、连续数组 table 和
string-key object table。`nil` 不作为值保存，删除 key 请调用 `remove`。限制包括：

- 不接受 Java userdata、function、thread 或含非 string key 的 object table。
- 不接受 NaN 和 infinity。
- 不接受循环引用。
- table 最大嵌套深度为 64。

包级 Setting 存在 `addon-settings.json`；包开关、Module 启用快照和 package storage 存在
`package-state.json`。完整配置布局见 [管理与生命周期](management-and-lifecycle.md)。
