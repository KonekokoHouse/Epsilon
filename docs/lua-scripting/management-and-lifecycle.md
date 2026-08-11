# 管理与生命周期

Lua 系统有一层全局开关和一层包级开关。它们保存位置、用途和卸载范围不同。

## 全局开关

`Enable Lua Scripts` 位于 Client Settings 的 `Lua Scripts` 分组，默认关闭。它是 root setting，写入客户端
全局设置，不随 profile 切换。

- 开启：重新扫描 `~/.epsilon/scripts/*/script.json`，只加载当前 profile 中包级开关为开启的包。没有保存状态
  的新包默认开启。
- 关闭：关闭并注销全部 Lua 包、Module、SettingHost、事件监听、渲染 callback 和 runtime。
- 关闭后 Addon Panel 仍可根据 manifest 展示包元数据，但包操作不可用。

Addon Panel 中没有第二个重复的全局 Lua 开关。

## 每个包的开关和 Reload

Addon Panel 和 Addon Dropdown 都通过同一 entry registry 展示 Java Addon 与 Lua 包。Lua 包具有独立的代码
标识，并在自己的详情区提供：

- 包级启用/禁用开关。
- Reload 按钮。
- manifest 元数据、Module 数量、包级 Setting 和最近错误。

每个包开关互不影响。它不是 Module toggle：包关闭时，该包的所有 Module 会从 ModuleHolder 和 GUI 中移除；
包重新开启时才从磁盘加载并重新注册。

## 包关闭

关闭包时依次发生：

1. 保存当前配置和各 Module 的 enabled 快照。
2. 将包级 `enabled` 写入当前 profile 的 `package-state.json`。
3. 禁用全部 Module，取消事件和渲染订阅，调用 `on_disable`。
4. 从 `ModuleHolder` 注销 Module，因此 GUI 不再显示它们。
5. 调用 `on_cleanup` 并关闭各 Module runtime。
6. 从 `ConfigHolder` 注销动态 SettingHost，再关闭 settings runtime 与翻译组件。

脚本直接通过 Java API 修改的外部状态无法自动恢复，仍必须在 `on_disable` 中处理。

## 包重新开启

重新开启会重新读取 `script.json`、`settings.lua`、全部 Module entrypoint、`lib/` 和语言文件，然后：

1. 创建新的 package settings runtime 和各 Module runtime。
2. 从当前 profile hydrate 包级 Setting、Module Setting、storage 和之前保存的 Module enabled 状态。
3. 注册 SettingHost 和 Module。
4. 恢复关闭包前开启的 Module；新 Module 使用 manifest/config 的默认状态。
5. 更新 `package-state.json` 中的包级 enabled 状态。

因此不能继续使用包关闭前保存的 Setting handle、Java Module userdata、UiTree scope 或其他 runtime 对象。

## 手动 Reload

Epsilon 没有 `WatchService`、文件轮询、debounce 或实时重载。所有修改都通过包详情中的 Reload 显式生效。

Reload 是整个包的 staged replacement：先保存当前状态并在未发布的候选对象中执行所有可信脚本；候选准备
失败时关闭候选，当前包继续运行。候选准备成功后替换同 owner 的 SettingHost 和 Module registration，应用
已有配置与 enabled 状态，再关闭旧 runtime。

这意味着修改任意一个 Module entrypoint 也会重建同包其他 Module。`settings.lua` 和 `lib/` 的消费者自然
获得同一版本的代码，不存在新旧共享 Setting handle 混用。

Reload 只在全局系统和该包都开启时可用。包处于关闭状态时，先开启包；开启动作本身会重新读取磁盘。

## manifest 错误

无法解析的 `script.json` 会作为 Lua error entry 出现在 Addon UI 中，显示目录名和错误。此时不能启用一个
没有有效 descriptor 的包，但可在全局 Lua 系统开启时用 Reload 重新扫描全部 manifest。

成功解析、但 entrypoint 执行失败的包会在它自己的条目中显示最近错误。修复后再次 Reload。

## profile 与配置布局

包状态属于当前配置 profile：

```text
~/.epsilon/configs/<profile>/
└── lua.example-suite/
    ├── addon-settings.json
    ├── hud-sample.json
    ├── world-box.json
    └── package-state.json
```

- `addon-settings.json`：包级 Setting。
- `<moduleId>.json`：Module Setting、Module enabled/hidden 和 `module.storage`。
- `package-state.json`：包级 enabled、关闭时的 Module enabled 快照和 `epsilon.packageStorage`。

切换 profile 后，Lua manager 会重新协调 descriptor 的包级启用状态和已注册 runtime。新 profile 中关闭的包
会卸载，开启的包会从该 profile 的配置重新加载。删除包目录不会自动删除旧配置，重新安装相同稳定 ID 的包可
继续使用原状态。

## 状态表

| 全局 Lua | 包开关 | 包 entry | Module 注册 | 事件/runtime |
|---|---|---|---|---|
| 关 | 任意已保存值 | 仅 manifest 元数据 | 无 | 无 |
| 开 | 关 | 显示，可开启 | 无 | 无 |
| 开 | 开且加载成功 | 显示，可关闭/Reload | 有 | 按 Module toggle 订阅 |
| 开 | 开但加载失败 | 显示错误 | 无或保留旧包 | 候选失败时保留旧包 |
