# 测试与排错

## 常见错误

### Addon Panel 中没有脚本包

确认目录层级为 `~/.epsilon/scripts/<package>/script.json`，而不是多套了一层目录。manifest 必须是合法 UTF-8
JSON，`schema` 和 `api` 当前都为 `1`，且至少声明一个 Module。

全局 Lua 关闭时仍应显示能够成功解析的 manifest descriptor，但包按钮不可用。完全不显示通常意味着目录
不正确；Lua error entry 则表示 manifest 解析或校验失败。

### 开启包后 Module 没出现

查看包条目的最近错误，并检查：

- 包 ID/Module ID 是否满足 `[a-z0-9][a-z0-9._-]{0,63}`。
- entrypoint 是否存在、是相对 `.lua` 路径，并且不在 `lib/` 或 `lang/`。
- 同一 entrypoint 是否被多个 Module 或 `settingsEntry` 重复引用。
- category 是否为当前 Epsilon `Category`。
- `settings.lua` 或任一 Module entrypoint 是否抛错。

修复后点击该包 Reload；Epsilon不会自动监听文件。

### require 找不到文件

`require("colors")` 只查找 `lib/colors.lua` 和 `lib/colors/init.lua`；`require("ui.theme")` 查找
`lib/ui/theme.lua` 和 `lib/ui/theme/init.lua`。不要把 library 放在 `modules/`，也不要依赖当前工作目录。

### Java class 找不到

普通 class 必须使用全限定名：

```lua
luajava.bindClass("net.minecraft.client.Minecraft")
```

Epsilon 事件短名才使用：

```lua
luajava.bindEventClass("MoveEvent")
```

Epsilon 工具类短名使用：

```lua
luajava.bindUtilClass("PlayerUtils")
```

若升级后找不到 Minecraft 方法或字段，直接查当前版本的 vanilla sources，不要根据旧教程猜测签名。

### callback 不触发

依次确认全局 Lua、包和 Module 三层状态都开启。EventBus 按精确运行时 class 分发；父事件 class 不会收到
子事件。`module:on(...)` 只能使用已登记的稳定 ID，其他事件使用 `on_class(...)`。

同一 callback 连续失败三次会自动关闭 Module。检查日志后修复，再在 GUI 中重新开启 Module。

### 包关闭再开启后旧对象报错

这是预期行为。关闭包会卸载 runtime、Module 和 SettingHost；重新开启创建的是新对象。不要跨卸载保存
Setting handle、`java_module()` 结果、UiTree scope、render context 或事件对象。

### 2D/3D 没有内容

- 确认使用 `render2d.hud`、`render2d.level` 或 `render3d` 的正确 callback 签名。
- 确认世界内使用的 `mc.player`/`mc.level` 不为 nil。
- Render3D priority 必须高于 `-999`。
- ARGB alpha 不能为 0；例如 `0x0033AAFF` 是完全透明。
- 不要保存 context 到下一帧，也不要自行 flush raw scheduler。

## 补全库检查

修改 Java Lua API 或事件 registry 后执行：

```powershell
uv sync --frozen
uv run scripts/dev.py lua update
```

`--check` 失败表示 `LuaUtilRegistry.java` 或 `docs/examples/lua/epsilon_lib.lua` 不是当前生成结果，或生成器
发现 Java AST、`scripts/lua_codegen/epsilon_api.json`、导出键与元数据约定不一致。单元测试覆盖公开成员、
重载、构造器、varargs、nullable、嵌套 enum 和保守类型映射。

## 维护验证矩阵

修改 Lua 系统时至少覆盖与变更相关的项目：

- manifest/path 校验；重复 package、Module、Setting ID。
- 一个包的多个 entrypoint 创建独立 Globals、`package.loaded`、Setting、启停状态和订阅。
- 两个 Module `require` 同一 `lib/` 源码，但可变 Lua state 不共享。
- 包级 `intSetting`/`doubleSetting` 创建正确 Java 类型，并在 Module 顶层读取到 hydrate 后的值。
- int 拒绝小数、溢出、NaN/infinity；double 拒绝非有限值。
- choice、color、keybind、string list 的配置、Panel、Dropdown 与 i18n。
- module storage 与 package storage 的 JSON 类型、循环引用和深度限制。
- 包级 Setting 和包状态随 profile 保存、reset、切换及 Zip 导入导出。
- Addon Panel/Dropdown 使用同一包级 Setting 实例，不产生重复状态。
- 每个包都有独立开关和 Reload；全局设置不在 Addon Panel 重复出现。
- 包关闭后 Module 从 GUI/ModuleHolder 移除，listener、render callback、SettingHost 和 runtime 失效。
- 包重新开启后重新注册 Module，并恢复关闭前的 Module enabled 快照。
- 全局关闭后全部包卸载；重新开启只加载当前 profile 中开启的包。
- reload 候选失败保留旧包；成功后同 ID Module 恢复配置和 enabled 状态。
- 连续启停/Reload 不产生重复 Module、listener 或 stale Dropdown Module。
- 当前语言、`en_us` 和 fallback 的优先级；非法 i18n 叶节点被拒绝。
- 精确事件 class、priority、取消、修改值与 packet 原线程行为。
- `bindEventClass` 的顶层/嵌套名称、`bindUtilClass` 注册短名与成员级补全、未知名称和 `bindClass` fallback
  用法。
- HUD callback 共用宿主 tree，Level callback 共用 scene，layer/scissor 隔离。
- 3D 命令在 priority `-999` flush 前提交并由 scheduler 当帧清理。
- raw Minecraft、Java class、UiTree scope 和 scheduler 的 smoke test。

## 仓库检查

纯文档或补全库修改至少执行：

```powershell
uv run scripts/dev.py verify
git diff --check
git diff -- AGENTS.md
git status --short
```

修改 Java 共享行为时至少编译两个平台：

```powershell
.\gradlew.bat :common:compileJava
.\gradlew.bat :fabric:compileJava :neoforge:compileJava
```

涉及依赖嵌入、Mixin、资源或启动流程时按仓库约束运行完整 `buildRelease` 和相关验证任务。2D、3D 与 packet
callback 仍需在 Fabric 和 NeoForge 客户端人工验证，重点检查字体测量、GUI scale、scissor、scene 重建、
scheduler flush 顺序和网络线程阻塞。
