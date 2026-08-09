# Lua 脚本系统实现计划

本文描述 Epsilon Lua 脚本系统的目标架构、公共接口、生命周期、渲染接入和分阶段实施方案。
实现对应的用户 API 见 [`lua-scripting.md`](lua-scripting.md)。本文继续作为架构决策、后续强化项和验证矩阵；
实现状态仍以当前源码、`gradle/libs.versions.toml` 和 Minecraft 26.1.2 参考源码为准。

## 当前实现状态

首版已实现 LuaJ 双平台嵌入、多 Module 独立 entrypoint/Globals、包内 `lib/`、包级 Setting、脚本 i18n、
动态事件、`bindEventClass`、JSON storage、UiTree 2D context、Render3DScheduler context、WatchService 热重载、
profile 持久化以及 Addon Panel/Dropdown 统一条目。发布时会先完整构建候选 runtime，再原子替换同 owner 的
SettingHost 和 Module registry；staging 失败保留旧 runtime。连续三次回调错误只禁用对应 Module。

仍应在后续强化：首次启用的专用确认弹窗、timer/defer 指令预算、仅重载语言 catalog 的 watcher 快路径、
更完整的自动化 runtime/GUI 测试，以及 Addon action tooltip/focus 导航。它们不改变本文已经确定的包格式和
公共 Lua API。

## 目标与信任模型

Lua 脚本用于声明一个或多个原生 `Module` 和包级 Addon-style Setting，并复用 Epsilon 的配置、i18n、
EventBus、Addon Panel、LuminGraphics `UiTree` 和 `Render3DScheduler`。

脚本按可信本地代码处理：

- 使用 LuaJ，并显式开放 `luajava`。
- 允许获取 `Minecraft`、Epsilon 对象和事件对象，并调用公开 Java 方法。
- 不承诺安全沙箱；脚本权限等同于同进程 Java 代码。
- 首次启用脚本系统时必须显示明确提示，`Enable Lua Scripts` 使用 root setting，默认关闭。
- 运行时仍负责线程串行化、错误隔离、资源清理和事务式热重载。这些是稳定性边界，不是安全边界。

首期不包括：

- 从网络下载或自动更新脚本。
- 在脚本之间建立隐式依赖解析器。
- 把脚本目录动态挂载为 Minecraft resource pack。
- 对任意 Java 调用提供可抢占的超时保证。

## 脚本包结构

一个脚本包可以声明多个 Module，每个 Module 必须有单独的 Lua entrypoint 和独立 `Globals`。包内 Module
共享 `lib/` 下的 Lua 源码和只读包元数据，但不隐式共享全局变量或 `package.loaded`；启用状态、Setting、
事件订阅、配置和运行时状态彼此独立。

```text
~/.epsilon/scripts/example-suite/
├── script.json
├── settings.lua
├── modules/
│   ├── combat-assist.lua
│   └── world-overlay.lua
├── lib/
│   ├── targeting.lua
│   └── ui/
│       └── theme.lua
└── lang/
    ├── en_us.json
    └── zh_cn.json
```

`script.json` 保存包级元数据和 Module 清单。Module 的稳定 ID、entrypoint、分类及默认状态只在 manifest
声明一次；loader 不执行 Lua 来发现 Module，也不执行 entrypoint 两次提取元数据。

```json
{
  "schema": 1,
  "api": 1,
  "id": "example-suite",
  "version": "1.0.0",
  "authors": ["Example"],
  "settingsEntry": "settings.lua",
  "modules": [
    {
      "id": "combat-assist",
      "entry": "modules/combat-assist.lua",
      "category": "COMBAT",
      "defaultEnabled": false,
      "defaultHidden": false
    },
    {
      "id": "world-overlay",
      "entry": "modules/world-overlay.lua",
      "category": "RENDER",
      "defaultEnabled": false,
      "defaultHidden": false
    }
  ]
}
```

包 ID 和 Module ID 必须匹配 `[a-z0-9][a-z0-9._-]{0,63}`。Setting、SettingGroup 和 choice ID
必须匹配 `[a-z0-9][a-z0-9._ -]{0,63}`，允许保留空格以遵循现有 i18n key 约束，但不允许 `/`、`\\`
或 `..` 路径分量。所有用于文件系统的路径分量在进入 `Path.resolve` 前单独校验，不能使用显示名称构造路径。

每个脚本包的 owner ID 为 `lua.<packageId>`。Module 的稳定身份为：

```java
public record ModuleKey(String ownerId, String moduleId) {
}
```

对应配置结构：

```text
~/.epsilon/configs/<profile>/
└── lua.example-suite/
    ├── addon-settings.json
    ├── combat-assist.json
    ├── world-overlay.json
    └── package-state.json
```

`addon-settings.json` 保存脚本包的全局 Setting，格式与 `EpsilonAddon` 的 Addon Setting 文件一致。Module
显示名称变化不得改变配置路径。删除 Module 或脚本包时保留旧配置文件，以便恢复同 ID 对象。

entrypoint 必须是包目录内规范化后的相对 `.lua` 路径，不能指向 `lib/`、`lang/` 或包目录之外。多个 Module
不能引用同一个 entrypoint，避免一个文件的生命周期归属不明确。`lib/` 只能由 `require` 加载，不能作为
Module entrypoint。可选 `settingsEntry` 遵循相同的路径约束，且不能和任一 Module entrypoint 指向同一文件。

## 多 Module entrypoint 接口

loader 为每个 manifest 条目创建独立 runtime，并在执行对应 entrypoint 前注入只属于该 Module 的
`module` 对象。`modules/combat-assist.lua` 不再声明 ID 或分类，只声明 Setting 与生命周期：

```lua
local targeting = require("targeting")

local attempts = module:intSetting({
    id = "attempts",
    default = 3,
    min = 1,
    max = 10,
    step = 1,
    group = "general"
})

module:on_enable(function()
    targeting.reset()
end)

module:on_disable(function()
end)

module:on("client_tick.post", 0, function(event)
end)

module:on_class(
    luajava.bindEventClass("MoveEvent"),
    100,
    function(event)
    end
)

local java_module = module:java_module()
```

entrypoint 正常返回后冻结 Module 与 Setting 结构。运行时回调不能新增或删除 Setting，防止 GUI、配置和
i18n 结构在一帧内变化。entrypoint 返回值被忽略，Module 不能从 entrypoint 动态新增另一个 Module。

事件回调只在所属 Module 启用时订阅。禁用一个 Module 不影响同包其他 Module；卸载包时按 manifest 顺序
禁用全部已启用 Module，执行各 Module 的 cleanup hook，然后分别释放 runtime。

`require("targeting")` 在每个 Module 的独立 `Globals` 中加载同一份 `lib/targeting.lua`，因此共享代码但
不共享 library 的可变 Lua table 或 upvalue。需要在 Module 间共享且跨重载/配置切换持久化的数据，使用
线程安全的 `epsilon.packageStorage`；只属于某个 Module 的数据使用 `module.storage`。两者只接受
JSON-compatible 值：nil、boolean、有限 number、string、数组和 string-key table。共享对象不得通过
Java static 或 Lua userdata 绕过 storage 生命周期。

## 脚本包全局 Setting

当前源码没有名为 `AddonSetting` 的独立类型；`EpsilonAddon` 通过实现 `SettingHost` 持有全局 Setting。脚本包
采用相同模型：`LuaScriptPackage implements SettingHost`，可选 `settings.lua` 在独立的 package settings
runtime 中声明供包内所有 Module 使用的 Setting 与 SettingGroup。

```lua
local general = addon:group("general")

local sharedRange = addon:doubleSetting({
    id = "shared range",
    default = 4.25,
    min = 1.0,
    max = 8.0,
    step = 0.05,
    group = general
})

local debugLog = addon:boolSetting({
    id = "debug log",
    default = false,
    group = general,
    changed = function(value)
    end
})
```

settings entrypoint 中注入的 `addon` 是声明态 API；文件正常返回后冻结 Setting 结构。每个 Module runtime
也注入同一包的只读 `addon` 代理，通过稳定 ID 读取 Setting：

```lua
local sharedRange = addon:setting("shared range")

module:on("client_tick.post", 0, function(event)
    local range = sharedRange:get()
end)
```

包级 `boolSetting`、`intSetting`、`doubleSetting`、`stringSetting`、`choiceSetting`、`colorSetting`、
`keybindSetting` 和 `stringListSetting` 与 Module API 使用相同 Lua 值语义，并创建相同 Java Setting 类型。
Module runtime 中调用声明方法必须报错，不能在运行时修改包级 Setting 结构。

`LuaScriptPackage` 不继承或注册成 `EpsilonAddon`，避免绕过 `AddonHolder.setupAddons()` 和 Addon Module 只能
在 `onSetup()` 注册的约束。`ConfigHolder` 新增按稳定 owner ID 管理动态 `SettingHost` 的注册协议，脚本包使用
`lua.<packageId>` 注册；读取、保存、reset、配置切换和 Zip 导入导出复用现有 Addon Setting 序列化逻辑。
注册后立即应用当前配置，卸载前主动保存并反注册；全局保存必须同时枚举原生 Addon 和动态 Setting host。
为支持事务 staging，`ConfigHolder` 还需提供“只写入 Setting 值但暂不触发 changed callback”的 hydrate
路径；提交成功后在客户端线程按声明顺序发布一次 changed callback。

## Java 组件

建议新增：

```text
common/src/main/java/com/github/epsilon/scripting/lua/
├── LuaScriptManager.java
├── LuaScriptPackage.java
├── LuaScriptManifest.java
├── LuaPackageLoader.java
├── LuaPackageSettingsRuntime.java
├── LuaModuleRuntime.java
├── LuaSharedLibraryResolver.java
├── LuaModule.java
├── LuaModuleApi.java
├── LuaAddonApi.java
├── LuaSettingHandle.java
├── LuaStorage.java
├── event/
│   ├── LuaEventDescriptor.java
│   ├── LuaEventRegistry.java
│   └── LuaEventListener.java
├── i18n/
│   ├── LuaTranslationCatalog.java
│   └── LuaTranslateComponent.java
└── render/
    ├── LuaRender2DService.java
    ├── LuaUiContext.java
    └── LuaRender3DContext.java
```

核心 Java 契约：

```java
public interface ModuleRegistration extends AutoCloseable {
    ModuleKey key();
    Module module();
    @Override void close();
}

public interface ExternalSettingHostRegistration extends AutoCloseable {
    String ownerId();
    SettingHost settingHost();
    @Override void close();
}

public interface LuaCallbackOwner {
    LuaScriptPackage scriptPackage();
    LuaModule module();
}

public interface LuaEventDescriptor<E> {
    String id();
    Class<E> eventClass();
    LuaEventThread thread();
}
```

`ModuleHolder` 增加 `registerExternal`、`replaceExternal`、`unregister` 和 `find(ModuleKey)`，并校验全局
唯一性。旧 `Module(String name, Category)` 保持兼容，默认 `moduleId = name`；新构造函数显式接收 ID。

## LuaJ 与 Java interop

版本统一加入 `gradle/libs.versions.toml`。`common` 编译依赖 LuaJ，Fabric 使用 `implementation + include`，
NeoForge 使用 `implementation + jarJar`，最终产物必须验证只嵌入一份 LuaJ。

每个 Module 创建独立 `Globals`，并注入当前 Module 与所属脚本包的只读 Addon-style Setting 绑定：

```java
Globals globals = JsePlatform.standardGlobals();
globals.load(new LuajavaLib());
globals.set("mc", CoerceJavaToLua.coerce(Constants.mc));
globals.set("epsilon", packageApi);
globals.set("module", moduleApi);
globals.set("addon", readOnlyAddonApi);
```

package settings runtime 使用相同 LuaJ 初始化方式，但不注入 `module`，并将 `addon` 替换为声明态
`LuaAddonApi`。settings runtime 与各 Module runtime 都可以从包内 `lib/` 加载共享源码，但分别维护
`Globals` 和 `package.loaded`。

Epsilon 在每个 runtime 的 `luajava` table 上额外注册 `bindEventClass(name)`。这是 Epsilon 提供的 LuaJ
扩展，不是 LuaJ 原生函数；任意非事件 Java 类型仍使用原生 `luajava.bindClass(fullyQualifiedName)`。

`LuaSharedLibraryResolver` 将 `require("targeting")` 和 `require("ui.theme")` 分别解析为包内
`lib/targeting.lua` 和 `lib/ui/theme.lua`，并在规范化后校验路径仍位于 `lib/`。不把 Minecraft 工作目录、
其他脚本包或 Module 目录加入 `package.path`；初始化时清空 LuaJ 默认文件路径与 `package.cpath`，用该 resolver
替换默认 filesystem searcher。由于开放 Java interop，这不是安全沙箱，而是稳定的模块解析契约。每个 Module
维护自己的 `package.loaded`，所以共享 library 文件的加载结果和可变状态不会跨 Module。

所有进入同一 Module runtime 的回调使用 Module 级可重入锁串行化。`packageStorage` 的 Java 实现另行做
线程安全保护，使不同 Module runtime 可以并发访问。普通 tick、输入和渲染事件在其原线程同步执行；Packet
事件为保留取消和修改能力，也在 Netty/发送线程同步执行，禁止 yield。脚本文档必须明确：慢 Packet 回调会
直接阻塞网络线程。

纯 Lua 回调可增加可配置的指令预算和耗时统计，但不得声称它能中断任意 Java 方法。连续回调错误达到
阈值后只禁用出错 Module；单个 entrypoint 初始化失败时保留其旧 runtime。manifest、共享 library、
共享存储或包级注册事务失败时整包回滚。

## Setting 接口与 number 语义

Lua 侧的 `intSetting` 和 `doubleSetting` 都读取、返回 Lua `number`。Java 侧必须分别创建原生
`IntSetting` 和 `DoubleSetting`，从而保留 GUI 控件、步长、范围、JSON 类型和 Java callback 类型。

```lua
local attempts = module:intSetting({
    id = "attempts",
    default = 3,
    min = 1,
    max = 10,
    step = 1,
    group = "general"
})

local range = module:doubleSetting({
    id = "range",
    default = 4.25,
    min = 1.0,
    max = 6.0,
    step = 0.05,
    group = "general",
    available = function()
        return attempts:get() > 1
    end,
    changed = function(value)
    end
})

assert(type(attempts:get()) == "number")
assert(type(range:get()) == "number")
```

转换规则：

- `IntSetting` 的 default/min/max/step 和 `set(value)` 必须是有限整数；传入 `4.5` 直接抛 Lua 参数错误，
  不执行静默截断。
- `DoubleSetting` 接受有限 Lua number，拒绝 NaN 和 infinity。
- Java `Integer` 回调转换为 Lua number，Lua number 写回时通过 int 精确校验后调用 `IntSetting.setValue()`。
- Java `Double` 回调转换为 Lua number，写回时调用 `DoubleSetting.setValue()`。
- 范围、step 和 default 在声明阶段校验；运行时 set 继续遵循原生 Setting 的约束。
- ConfigHolder 继续把 `IntSetting` 写成 JSON integer、`DoubleSetting` 写成 JSON number。
- Setting handle 在包卸载后失效，后续调用抛出包含 package/module/setting ID 的错误。

首期 Setting API：

| Lua API | Java 类型 | Lua 值 |
|---|---|---|
| `boolSetting` | `BoolSetting` | boolean |
| `intSetting` | `IntSetting` | number，必须为整数 |
| `doubleSetting` | `DoubleSetting` | number |
| `stringSetting` | `StringSetting` | string |
| `choiceSetting` | 新增 `ChoiceSetting` | string |
| `colorSetting` | `ColorSetting` | ARGB number / `java.awt.Color` |
| `keybindSetting` | `KeybindSetting` | number，必须为整数 |
| `stringListSetting` | `StringListSetting` | string table |

`choiceSetting` 不能伪装成 `EnumSetting`。实现原生 `ChoiceSetting<String>`，并同步修改 Setting DSL、
ConfigHolder、Panel、Dropdown 和 i18n 模板生成。SettingGroup 通过 `module:group(id)` 或 `addon:group(id)`
声明并在各自 Setting host 内复用。

## i18n

每个包在 `lang/` 中提供语言 JSON。叶节点只能是字符串，不能出现数组、数字、布尔或 null。

```json
{
  "_value": "示例脚本包",
  "groups": {
    "general": {
      "_value": "全局设置"
    }
  },
  "settings": {
    "shared range": {
      "_value": "共享距离"
    },
    "debug log": {
      "_value": "调试日志"
    }
  },
  "modules": {
    "combat-assist": {
      "_value": "战斗辅助",
      "description": "示例战斗模块",
      "groups": {
        "general": {
          "_value": "常规"
        }
      },
      "settings": {
        "attempts": {
          "_value": "尝试次数"
        },
        "range": {
          "_value": "距离"
        },
        "mode": {
          "_value": "模式",
          "normal": "普通",
          "strict": "严格"
        }
      }
    },
    "world-overlay": {
      "_value": "世界覆盖层",
      "description": "显示世界标记"
    }
  }
}
```

完整 key：

```text
lua.<packageId>._value
lua.<packageId>.description
lua.<packageId>.groups.<groupId>._value
lua.<packageId>.settings.<settingId>._value
lua.<packageId>.settings.<settingId>.<choiceId>
lua.<packageId>.modules.<moduleId>._value
lua.<packageId>.modules.<moduleId>.description
lua.<packageId>.modules.<moduleId>.groups.<groupId>._value
lua.<packageId>.modules.<moduleId>.settings.<settingId>._value
lua.<packageId>.modules.<moduleId>.settings.<settingId>.<choiceId>
```

新增 `LuaTranslationCatalog` 读取包语言文件，并新增 `LuaTranslateComponent implements TranslateComponent`。
它注册到现有 `TranslateHolder`，因此语言切换会和原生组件一起 refresh。`EpsilonLanguageManager` 需要公开
当前实际语言 code，供 custom language 选择正确文件。

回退顺序为：当前语言 -> `en_us` -> manifest/Module/Setting 的 fallback name -> ID 格式化结果。新增、
删除或热重载 Module/Setting 时重新校验翻译 key，并刷新该包组件。语言文件变化只重载 catalog，不重建
Module；单个 Module entrypoint 变化只重载对应 Module，manifest、`settings.lua` 或 `lib/` 变化执行包级
事务重载。

脚本管理 UI、错误提示和全局开关仍使用 Epsilon 自身 i18n 资源。脚本提供的静态 Module、Setting、
SettingGroup 和 choice 文案不得直接散落在 Lua render callback 中。

## EventBus 接入

`LuaEventListener implements IListener` 直接注册目标事件 class 和 priority，不依赖 Java 注解或 lambda
factory。内置事件字符串映射到当前 Epsilon 事件 class，同时提供 `on_class` 访问任意公开事件类型。

`luajava.bindEventClass(name)` 通过 `LuaEventRegistry` 的显式索引解析 Epsilon 事件，不做 classpath 扫描或
包名拼接。顶层事件使用简单类名，例如 `MoveEvent`；嵌套事件使用 `ClientTickEvent.Pre`、
`PacketEvent.Send`、`Render2DEvent.HUD` 形式。裸 `Pre`、`Post` 等名称不注册，未知名称直接抛出包含候选项的
Lua 参数错误。该函数返回对应 Java `Class<?>` userdata，可直接传给 `module:on_class(...)`。

registry 在编译期显式登记 `common/events/impl` 中允许暴露的精确运行时 class；新增、删除或重命名事件时，
必须同步更新 registry 和脚本 API 文档。若确实需要监听 registry 外的公开事件，仍可显式使用
`luajava.bindClass("完整类名")`，不会让简写解析器静默回退到任意 Java class。

EventBus 按精确运行时 class 分发，脚本监听父 class 不会收到子事件。实现前必须统一当前源码和文档中的
取消语义；Lua 层不得自行引入与 Java EventBus 不同的短路规则。

包重载或 Module 禁用时，所有由该 Module 创建的 listener、timer、deferred callback 和 cleanup hook
必须被移除。脚本通过直接 Java API 修改的任意外部状态无法自动推断，仍由 `on_disable` 负责恢复。

## LuminGraphics UiTree 2D

Lua 2D API 不创建每 Module renderer。它只在活动帧中向宿主提供的 `UiTree.Scope` 追加节点。

```lua
module:on("render2d.hud", 10, function(ui, event)
    ui:round_rect(20, 20, 160, 42, 6, 0xDD15171A)
    ui:text("Lua HUD", 32, 46, 0.8, 0xFFFFFFFF, "epsilon-default")
    ui:layer(2, function(child)
        child:outline(20, 20, 160, 42, 6, 1, 0xFF55CCFF)
    end)
end)
```

`LuaUiContext` 提供：

- rect、round rect、outline、gradient、shadow、triangle。
- text、rotated text、marquee text 和对应 text metrics。
- texture，首期只接受 Minecraft ResourceManager 已存在的 `Identifier`。
- `layer(relative, callback)`、`scissor(rect, callback)` 和 bound push。
- 屏幕逻辑尺寸、鼠标逻辑坐标。
- `world_to_screen(Vec3)` 和 `world_scale(Vec3)`，直接复用 `WorldToScreen`。
- `raw_scope()`，返回当前 `UiTree.Scope` Java userdata。

HUD 阶段扩展 `HudElementHolder`：在同一棵 HUD tree 中调用 `LuaRender2DService.appendHud(scope)`，每个
脚本 Module 使用隔离子 layer。不得为每个脚本或 Module 创建 scene。

Level 阶段由 `LuaRender2DService` 持有一个共享 `UiScene`，在 `Render2DEvent.Level` 中构建并提交所有
启用 Module 的树。scene 必须绑定创建它的 `MinecraftUiRuntime2612`；runtime 变化、资源重载、关闭或
frame failure 时关闭旧 scene。每个脚本 callback 的树构建独立捕获，失败不能把未完成节点泄漏给其他脚本。

Lua callback 不直接调用 `beginFrame/endFrame/drawAndClear`。`LuaUiContext` 在 callback 结束后失效；
保留 raw scope 属于不受支持行为。存在遮挡关系的内容必须显式使用 layer，不能依赖同 layer pipeline 顺序。

## Render3DScheduler

所有 `render3d` callback 在 `Render3DEvent` priority 大于 `-999` 时执行，保证命令在 scheduler 统一 flush
前完成提交。

```lua
module:on("render3d", 0, function(render3d, event)
    local box = luajava.newInstance(
        "net.minecraft.world.phys.AABB",
        0, 64, 0, 1, 65, 1
    )
    render3d:filled_box(box, 0x5533AAFF)
    render3d:outline_box(box, 0xFF33AAFF, 2.0)
end)
```

`LuaRender3DContext` 映射现有 scheduler：

- `blurred_box(AABB, strength)`
- `filled_box(AABB, color)`
- `filled_fade_box(AABB, bottomColor, topColor)`
- `filled_side(AABB, color, Direction)`
- `outline_box(AABB, color, thickness)`
- `side_outline(AABB, color, thickness, Direction)`
- `line(Vec3, Vec3, color, thickness)`
- `raw_scheduler()`

Context 只在 render callback 中有效。所有命令仍由共享 scheduler 持有并在同帧清空，脚本不得自行 flush。

## 初始化顺序

保持现有 Addon 和配置前置关系，脚本在 Managers 与 3D scheduler 可用后加载：

1. 注册 EventBus lambda factory。
2. 初始化本体 Module 和 HUD。
3. setup Addon。
4. 初始化当前配置和语言。
5. 初始化 Managers。
6. 初始化 `Render3DScheduler`。
7. 初始化 `LuaScriptManager`，扫描 manifest 并 stage package settings runtime。
8. 从当前配置静默 hydrate 包级 Setting，再 stage 各 Module runtime，使 Module 顶层读到最终全局值。
9. 原子注册动态 `SettingHost` 和脚本 Module，再发布包级 Setting changed callback。
10. 调用 `ConfigHolder.applyToModules()` 恢复各 Module 配置。
11. 加载脚本 package state，enabled 最后应用。
12. 注册 Addon Panel entry，生成 i18n 模板并启动 watcher。

这样每个 Module entrypoint 顶层和 `on_enable` 可以安全访问 Managers。ConfigHolder 的全量保存会自然包含
已注册的脚本 Module 和动态 Setting host。保持现有 `AddonHolder.setupAddons()` 顺序不变，脚本包不会作为
迟到的 `EpsilonAddon` 注册。

## 包级事务热重载

Watcher 只收集路径事件。合并同包事件并防抖至少 300ms，随后通过 Minecraft client executor 在主线程执行
reload；文件线程不得执行 Lua、修改 ModuleHolder 或使用渲染 API。

manifest、`settings.lua`、`lib/` 或手动 Reload Package 会重载整个多 Module 包：

1. 读取并校验 manifest、settings entrypoint、全部 Module entrypoint、共享 library 和语言文件。
2. 将旧包 Addon-style Setting、全部 Module 配置、storage 和 enabled 状态保存到磁盘并保留内存快照。
3. 创建未注册的 package settings runtime，执行一次 `settings.lua`，再从快照静默 hydrate 全局 Setting。
4. 按 manifest 顺序为每个 Module 创建独立 runtime，每个 entrypoint 只执行一次。
5. 校验 package/Module Setting ID、entrypoint 唯一性、全局 `ModuleKey` 和 i18n key。
6. 任一 staging 步骤失败时关闭全部 staged runtime，整个旧包和旧全局 Setting 继续运行。
7. 调用旧 Module 的 `on_disable`，再原子替换动态 `SettingHost`、Module registrations 和 Panel entry。
8. 对每个新 Module 应用配置并恢复 package/module storage；新增对象使用默认值，删除对象的配置保留。
9. 提交成功后发布包级 Setting changed callback，并恢复原有 enabled 状态。
10. 最后分别关闭旧 runtime。

若提交阶段发生异常，ConfigHolder、ModuleHolder 和 Addon Panel entry registry 必须共同回滚；不能留下新旧
Setting host 或 Module 混合状态。语言文件单独变化时只刷新 catalog，不执行 Lua entry，也不切换 Setting
或 Module 实例。

单个 `modules/<name>.lua` 变化且 manifest 映射未改变时执行更小的 Module 事务：stage 新 runtime、保存该
Module 配置、替换同一 `ModuleKey`、恢复 storage 和 enabled 状态，失败则保留旧 runtime。同包其他 Module
不禁用也不重建。`settings.lua` 变化必须连同所有 Module 重载，避免 Module 持有已失效的全局 Setting handle；
`lib/` 变化也必须重载整包，因为 package settings 和任意 Module 都可能通过动态 `require` 使用它。首期不
维护静态依赖图。

## Addon Panel 脚本管理

不新增 Scripts tab。将 `AddonClientSettingTab` 当前直接依赖 `List<EpsilonAddon>` 的部分抽成内部
`AddonPanelEntry` 协议，并由统一 registry 提供条目：

```java
public interface AddonPanelEntry {
    String entryKey();
    AddonPanelEntryKind kind();
    String typeIcon();
    String id();
    String displayName();
    String description();
    String version();
    List<String> authors();
    List<Setting<?>> settings();
    AddonPanelStatus status();
    List<AddonPanelAction> actions();
}
```

- `JavaAddonPanelEntry` 适配现有 `EpsilonAddon`，保持原有展示、Setting 编辑和配置行为。
- `LuaPackagePanelEntry` 适配 `LuaScriptPackage`，在同一 Addon 列表展示 Lua 标识、Module 数量、加载状态和
  最后错误，并直接显示包级 Addon-style Setting。
- `entryKey` 使用 `addon:<addonId>` 或 `lua:<packageId>`，避免原生 Addon 与脚本包同 ID 时选择状态冲突。
- `AddonPanelEntryKind` 至少包含 `JAVA_ADDON`、`LUA_SCRIPT` 和 `LUA_SYSTEM`。Lua package 返回
  `LUA_SCRIPT + IconChars.CODE`；Addon 列表行在名称前绘制 `epsilon-icons` code icon，详情标题旁再显示翻译为
  `Lua Script` 的类型 chip。原生 Addon 不显示 Lua 标识，不能只依靠颜色区分类型。
- 脚本详情信息下方增加固定高度 action row。最前面是 package enable/disable 图标按钮：启用状态显示
  `IconChars.POWER_OFF` 和“禁用脚本”tooltip，禁用状态显示 `IconChars.POWER` 和“启用脚本”tooltip；第二个是
  `IconChars.REFRESH` Reload 按钮。打开目录与查看错误继续放在后续 icon action/menu 中。
- action 按钮使用稳定的正方形 hit box、`epsilon-icons` 字体、hover/focus 状态和 `PanelPopupHost` tooltip；
  `AddonClientSettingTab` 必须把 action row 高度计入 info/settings viewport 布局，不能覆盖 Module 数量 chip、
  描述或第一行 Setting。
- 点击禁用先主动保存 package/Module 配置和 enabled snapshot，再禁用该包全部 Module；点击启用会在需要时
  创建 runtime、恢复全局 Setting/Module 配置，并恢复禁用前各 Module 的 enabled 状态。package enabled
  标志写入 `package-state.json`，不与系统 root 总开关混用。
- Reload 必须调用前述 staged transaction。`LOADING`/`RELOADING` 时两个按钮禁用并显示 busy 状态，防止重入；
  reload 失败时旧 runtime 与 enabled 状态保持不变，详情区显示最后错误，Reload 仍可用于重试。
- 系统总开关关闭时 package 启停和 Reload 按钮不可用，tooltip 指向先启用 Lua Scripts；manifest 解析失败的
  条目禁用启停按钮，但允许 Reload 重试。操作完成后使 Panel 的 `UiInvalidationState` 失效并刷新当前条目。
- 固定的 `Lua Scripts` 系统条目承载 root settings：总开关（默认关闭）、watcher 开关和 debounce；actions
  提供打开 `.epsilon/scripts/` 与 Reload All。root settings 继续写入 `client-settings.json`，不随配置切换。
- `AddonDropdownPanel` 使用同一 entry registry 展示原生 Addon 与脚本包 Setting；不创建第二套脚本管理页。

package disable 只禁用该包所有 Module 及其事件/渲染 callback，保留 package settings runtime、Setting host
和 Panel entry，使用户仍可修改全局 Setting 或重新启用。关闭系统总开关则关闭全部 Lua runtime；Addon
Panel 仅保留系统条目和从 manifest 读取的 package 元数据，启用后再执行受信任的 `settings.lua` 与 Module
entrypoint。

包级 Setting 由当前 profile 的 `addon-settings.json` 持久化，系统 root settings 与包级 Setting 必须在 UI
中明确分组，不能混为一种生命周期。可信代码提示、状态、action tooltip 和错误提示进入 Epsilon 自身
`en_us.json` 与 `zh_cn.json`；脚本包名称、描述和 Setting 文案来自包内 i18n。

## 分阶段实施

### 第一阶段：核心身份和 Setting

- 为 Module 增加稳定 ID 和 `ModuleKey`。
- 实现外部 Module 注册、替换、反注册和唯一性校验。
- 为 `ConfigHolder` 增加动态 `SettingHost` 注册、应用、保存和反注册协议。
- 实现 `ChoiceSetting` 及两套 GUI、配置和 i18n 支持。
- 增加单 Module 主动保存接口，复用现有 `applyToModules()`。

### 第二阶段：独立 Module runtime 与 entrypoint

- 添加 LuaJ 双平台依赖与最终 Jar 验证。
- 实现 manifest Module 清单、entrypoint 路径校验和完整 Java interop。
- 实现每 Module 一个 `Globals`、entrypoint 单次执行、声明冻结和独立启停。
- 实现 package settings runtime、声明态/只读 `addon` API 和全局 Setting handle。
- 实现包级 `lib/` resolver、Module 独立 `package.loaded` 和共享 `packageStorage`。
- 实现 int/double 等 Setting bridge 和严格 number 转换测试。

### 第三阶段：包级 Setting、配置与 i18n

- 实现 module/package storage。
- 将包级 Setting 保存到 `lua.<packageId>/addon-settings.json`，覆盖配置切换、reset、导入和导出。
- 实现 `LuaTranslationCatalog`、`LuaTranslateComponent` 和语言回退。
- 扩展当前语言 code 查询与翻译刷新。
- 添加 `en_us`、`zh_cn` 示例脚本翻译。

### 第四阶段：事件与调度

- 实现动态 `IListener`、内置事件表和 `on_class`。
- 为 LuaJ 注入 `bindEventClass`，覆盖顶层与嵌套事件名称、未知名称和重名校验。
- 实现 Module runtime 串行锁、timer、defer、cleanup 和错误隔离。
- 核验 Packet 线程、取消与修改后的值。

### 第五阶段：2D 与 3D 渲染

- 将脚本 HUD contributor 接入共享 HUD tree。
- 实现共享 Level scene、runtime 变化和 frame failure 清理。
- 实现 UiTree primitives、layer、scissor、text metrics 和 raw scope。
- 实现全部 Render3DScheduler wrapper 和 raw scheduler。

### 第六阶段：热重载与管理 UI

- 实现 WatchService、防抖和主线程提交。
- 实现包级 staged load、Setting host/Module registration diff、回滚和配置恢复。
- 抽取 `AddonPanelEntry` registry，将系统设置和脚本包管理接入现有 Addon Panel。
- 让 `AddonClientSettingTab` 与 `AddonDropdownPanel` 展示脚本包全局 Setting。
- 添加多 entrypoint Module 示例包、共享 library 与脚本 API 文档。

## 验证矩阵

自动测试至少覆盖：

- manifest/path 校验、重复 package/module/setting ID。
- 一个包声明多个 entrypoint，分别创建独立 `Globals`、启停、配置和订阅事件。
- 两个 Module 可以 `require` 同一 `lib/` 文件，但 `package.loaded` 和 library 可变状态互不共享。
- 单 Module entrypoint 热重载不重建同包其他 Module；共享 library 变化原子重载整包。
- `settings.lua` 声明的 int/double 等全局 Setting 创建正确 Java 类型，并能被全部 Module 读取。
- Module entrypoint 执行前已静默 hydrate 包级 Setting，staging 阶段不触发 changed callback。
- 包级 Setting 随 profile 保存、reset、切换、Zip 导入导出，系统 root settings 不随 profile 切换。
- settings runtime 重载成功后替换全局 Setting handle，失败时旧 Setting host、Module 和 Panel entry 全部保留。
- Addon Panel 同时显示原生 Addon、Lua Scripts 系统条目和脚本包，重名 ID 不影响选择状态。
- Addon Panel 和 Dropdown 编辑同一包级 Setting 实例，不产生两份状态。
- package disable 保留包级 Setting；系统总开关关闭时不执行 settings 或 Module entrypoint。
- Lua package 在列表和详情中都有 `IconChars.CODE`/`Lua Script` 标识，原生 Addon 不被误标。
- package 启用、禁用和 Reload 按钮覆盖正常、busy、系统关闭、manifest error 和 reload failure 状态。
- 连续点击或启停与 Reload 交错不能造成重复 runtime、重复 listener 或 enabled snapshot 丢失。
- `intSetting` 与 `doubleSetting` 均返回 Lua number，但创建正确 Java Setting 类型。
- int 拒绝小数、溢出、NaN 和 infinity；double 拒绝非有限值。
- choice 配置、Panel、Dropdown 和 i18n 选项。
- 当前语言、`en_us` 和 fallback 的优先级。
- 语言热重载不重建 Module。
- 包级 reload 失败保留全部旧 Module；成功后保留同 ID Module 状态。
- 删除/新增 Module 的 registration diff 与旧配置保留。
- Module 禁用清理 listener、timer 和 pending callback，不影响同包其他 Module。
- 精确事件 class、priority、取消和 Packet 原线程行为。
- HUD 所有脚本共用一棵树，Level 所有脚本共用一个 scene。
- UiTree layer/scissor 隔离和 callback 失败清理。
- 3D 命令在 priority `-999` flush 前提交且每帧清空。
- `luajava.bindEventClass`、`luajava.bindClass`、raw Minecraft、raw UiTree scope 和 raw scheduler smoke test。

提交前运行：

```powershell
.\gradlew.bat :common:compileJava
.\gradlew.bat :fabric:compileJava :neoforge:compileJava
.\gradlew.bat buildRelease
.\gradlew.bat verifyLuminJarInJar
git diff --check
git diff -- AGENTS.md
git status --short
```

2D、3D、热重载和 Packet callback 还需要分别在 Fabric 与 NeoForge 客户端进行人工验证。渲染验证重点
包括字体测量、GUI scale、HUD/Screen painter order、scissor、scene 重建和 scheduler flush 顺序。
