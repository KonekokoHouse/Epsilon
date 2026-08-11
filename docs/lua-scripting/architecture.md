# 内部架构

本章面向 Epsilon 维护者，说明 Lua 脚本系统的组件、注册边界、初始化顺序和 reload 模型。公共 Lua API 仍以
前面的教程和当前 Java 注册代码为准。

## 设计目标

- 一个脚本包声明多个原生 Epsilon `Module`。
- 每个 Module 有独立 entrypoint、LuaJ `Globals`、生命周期和事件订阅。
- 同包 Module 共享 `lib/` 源码和 package storage，但不共享隐式 Lua global。
- 包级 Setting 复用 `SettingHost`、`ConfigHolder`、Addon Panel 和 Dropdown。
- 事件复用现有 EventBus 的精确 class 与 priority 语义。
- 2D/3D 内容只提交到共享 `UiTree`/`UiScene` 和 `Render3DScheduler`。
- 包关闭、全局关闭和 reload 必须清理动态注册与 runtime。
- reload 候选准备失败时不破坏当前运行包。

脚本拥有完整 `luajava` 权限，因此系统的隔离用于稳定性和生命周期管理，不构成安全边界。

## 主要 Java 组件

```text
common/src/main/java/com/github/epsilon/scripting/lua/
├── LuaScriptManager.java
├── LuaScriptPackage.java
├── LuaScriptManifest.java
├── LuaRuntime.java
├── LuaUtilRegistry.java
├── LuaModule.java
├── LuaModuleApi.java
├── LuaSettingApi.java
├── LuaStorage.java
├── event/
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

职责：

| 组件 | 职责 |
|---|---|
| `LuaScriptManager` | 扫描 manifest、维护 descriptor/loaded/error 状态、全局启停、包启停和 Reload |
| `LuaScriptManifest` | manifest 数据模型、ID/entrypoint/category 校验 |
| `LuaScriptPackage` | package settings runtime、Module runtime、配置、i18n、注册与包状态 |
| `LuaRuntime` | 创建 LuaJ Globals、注入 `mc`、设置 `lib/` path、安装 class binder、串行 callback |
| `LuaUtilRegistry` | codegen 生成的 `bindUtilClass` 短名到 Epsilon utility class 映射 |
| `LuaModule` | Epsilon Module 生命周期、listener 集合、module storage 和连续错误隔离 |
| `LuaModuleApi` | `module` table 的生命周期、事件、Setting 和 storage 方法 |
| `LuaSettingApi` | `module`/`addon` Setting DSL、handle 和严格类型转换 |
| `LuaStorage` | Lua table 与 JSON 的受限双向转换和同步访问 |
| `LuaEventRegistry` | 事件字符串 ID 与事件 class 短名的显式映射 |
| `LuaEventListener` | 动态 `IListener`、render callback 参数适配 |
| `LuaRender2DService` | HUD tree contributor、共享 Level scene、listener 排序和 scene 清理 |
| `LuaUiContext` | Lua 到 `UiTree.Scope` 的 2D wrapper |
| `LuaRender3DContext` | Lua 到共享 `Render3DScheduler` 的 3D wrapper |

GUI 入口由 `gui/addon/AddonPanelEntry` 与 `AddonPanelEntryRegistry` 统一 Java Addon、Lua package 和 Lua error
entry。Lua package entry key 使用 `lua:<packageId>`，避免与 `java:<addonId>` 发生选择状态冲突。

## 身份与注册边界

包 owner ID 固定为：

```text
lua.<packageId>
```

ModuleHolder 通过 owner ID 与 Module ID 识别动态 Module。`LuaScriptPackage` 不继承 `EpsilonAddon`，也不作为
迟到的 Addon 注册，因此不会绕过 `AddonHolder.setupAddons()` 与 Addon `onSetup()` 约束。

包通过 `ConfigHolder.registerExternalSettingHost(ownerId, host)` 发布包级 Setting，通过
`ModuleHolder.registerExternal(ownerId, module, translation)` 发布 Module。关闭返回的 registration 会撤销
对应动态对象。reload 使用同 owner 的 replace 协议，保持 registry 中身份稳定。

`LuaScriptPackage` 同时实现 `ExternalConfigState`，把 package storage、包级 enabled 和 enabled Module 快照
纳入 profile 的保存/加载、切换和导入导出流程。

## runtime 隔离

每个 settings/Module runtime 都由 `JsePlatform.standardGlobals()` 创建。初始化顺序是：

1. 设置当前包 `lib/?.lua` 与 `lib/?/init.lua` 为唯一 Lua library path，并清空 `package.cpath`。
2. 在 `luajava` table 安装 `bindEventClass` 与 `bindUtilClass`。
3. 注入 `mc`。
4. 由 package 注入 `epsilon`，并按 runtime 类型注入声明态/只读 `addon`。
5. Module runtime 额外注入 `module`。
6. 执行 entrypoint 一次，然后关闭 Setting 声明阶段。

runtime 使用公平的 `ReentrantLock` 串行化同一 Globals 的 execute/invoke，但不迁移线程。不同 Module runtime
仍可能在不同事件线程并发执行，共享 package storage 在 Java 层同步。

## 初始化顺序

`EpsilonCommon.init()` 保持以下顺序：

1. 注册 Epsilon EventBus lambda factory。
2. 初始化本体 Module 与 HUD。
3. `AddonHolder.setupAddons()`。
4. 初始化当前配置并选择语言。
5. 初始化 Managers。
6. 初始化 `Render3DScheduler` pipeline。
7. 初始化 `LuaScriptManager`，扫描 descriptor；仅在全局 Lua 开启时加载已启用包。
8. 生成 Epsilon i18n 空模板。

单个包 prepare 时先读取 `package-state.json`，再执行 `settings.lua`，随后静默 hydrate 包级 Setting，最后按
manifest 顺序执行每个 Module entrypoint并 hydrate Module 配置。全部 prepare 成功后才发布 SettingHost 和
Module，并在最后应用包级/Module enabled 状态。

这样 Module 顶层和 `on_enable` 执行时 Managers 与 3D scheduler 已可用，包级 Setting handle 也已经指向
hydrate 后的值。

## 事件接入

`LuaEventListener` 直接实现 `IListener`，不依赖 Java `@EventHandler` 或 lambda factory。`module:on` 通过
`LuaEventRegistry.resolveId` 取得精确 class；`module:on_class` 接收 Java `Class<?>` userdata。

普通事件交给 EventBus。`Render2DEvent` listener 由 `LuaRender2DService` 单独维护，以便传入 UiTree context；
`Render3DEvent` 仍走 EventBus，但 callback 前额外创建 3D context。Module 启用时订阅、禁用时反订阅。

新增、删除或重命名 Epsilon 事件时，必须同步更新 `LuaEventRegistry`、生成的 `epsilon_lib.lua` 和事件文档。
不能让 `bindEventClass` 通过拼接包名或 classpath 扫描兜底。

`LuaUtilRegistry` 由 Python codegen 使用 Tree-sitter Java AST 生成，不直接手工编辑。发现器只选择
`com.github.epsilon.utils` 下公开的顶层 `*Utils` class，以及 codegen 配置中明确允许的非后缀入口；不会把
整个 utils 树自动视作公共 API。新增、删除或重命名入口后运行生成器，重名短类名会直接失败，不能根据扫描
顺序选择其中一个。AST 同时解析公开字段、构造器、方法重载、参数/返回类型、varargs、nullable 和公开嵌套
enum，并将它们渲染为 LuaLS class。非 Java host API 由结构化
[`epsilon_api.json`](../../scripts/lua_codegen/epsilon_api.json) 驱动，不再保存在 Python 字符串模板中。生成规则
与 `uv` 命令见 [`scripts/README.md`](../../scripts/README.md)。

## 2D/3D 宿主关系

HUD 渲染由 `HudElementHolder` 把 Lua contributor 追加到当帧共享 HUD scope。Level 2D 使用
`LuaRender2DService` 持有的一份共享 `UiScene`；每个 listener 被放入独立子 layer，单个 callback 失败不会
泄漏半构建节点到其他 Module。

3D context 始终引用 `Render3DScheduler.INSTANCE`。Lua listener priority 必须高于 `-999`，scheduler 在
`-999` 统一 flush。Lua wrapper 不公开 flush/clear 生命周期，只允许显式取 raw scheduler。

## 手动 staged reload

没有文件 watcher。包级 Reload 流程为：

1. 保存旧包的 Module、Setting、storage 和 enabled 状态。
2. 重新读取并校验 manifest。
3. 创建未注册的候选 `LuaScriptPackage`。
4. 执行候选 settings entrypoint 和所有 Module entrypoint，hydrate 当前 profile 配置。
5. 任一 prepare 步骤失败：关闭候选，记录错误，旧包继续运行。
6. prepare 成功：替换同 owner 的 external SettingHost 和全部 Module registration。
7. 应用 package/Module enabled 状态，关闭旧包 runtime，并更新 manager map。

当前 reload 粒度是整个包，不存在只重载单个 Module 或只重载语言 catalog 的快路径。这样 `settings.lua` 与
`lib/` 的变化不会留下旧 handle 或跨 runtime 的混合状态。

## 已知边界

- 没有脚本下载、自动更新或包依赖解析器。
- 没有文件 watcher、实时 reload 或 debounce。
- 没有 timer/defer API、可抢占指令预算或 Java 调用超时。
- 没有安全沙箱；library path 限制不能阻止 Java interop 访问外部资源。
- raw Java 调用造成的外部状态和 GPU 资源必须由脚本清理。
- 已注册 Epsilon Util 的公开源码 API 由 `epsilon_lib.lua` 建模；任意 Minecraft、第三方或未注册 Java
  userdata 的完整 IDE 补全仍不保证。

扩展这些能力时必须保持包 ID、独立 Globals、动态 registration、profile 配置、精确事件 class 和共享渲染
调度这些既有契约。
