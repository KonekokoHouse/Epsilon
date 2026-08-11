# Epsilon Lua 脚本教程

Epsilon 使用 LuaJ 3.0.1 加载本地 Lua 脚本包。一个包可以提供多个 Epsilon Module、包级 Setting、共享
Lua library、语言文件、2D HUD 和 3D 世界渲染。脚本可以通过 `luajava` 调用 Minecraft、Epsilon 和 Java
运行时的公开接口。

脚本按可信本地代码处理，不是安全沙箱。安装脚本包等同于安装能在 Minecraft 进程内执行的 Java Addon：
只使用来源可信、且已经阅读过的脚本。

## 阅读顺序

1. [快速开始](getting-started.md)：安装示例包、启用系统并配置 VS Code。
2. [脚本包与 Module](packages-and-modules.md)：`script.json`、多 entrypoint、`lib/` 和生命周期。
3. [Setting 与存储](settings-and-storage.md)：Module/包级 Setting、number 规则和持久化。
4. [事件与 Java 调用](events-and-java.md)：事件订阅、`bindEventClass`、`bindUtilClass`、Minecraft 和 Java API。
5. [Util 简写与代码补全](util-code-completion.md)：静态/实例成员、构造器、重载、类型映射和 codegen。
6. [2D 与 3D 渲染](rendering.md)：LuminGraphics `UiTree` 和 `Render3DScheduler`。
7. [脚本 i18n](internationalization.md)：语言文件结构、完整 key 和回退规则。
8. [管理与生命周期](management-and-lifecycle.md)：全局开关、包开关、Reload、配置切换和卸载语义。

维护者还应阅读 [内部架构](architecture.md) 和 [测试与排错](testing-and-troubleshooting.md)。

## 最小脚本包

```text
hello-epsilon/
├── script.json
├── modules/
│   └── hello.lua
└── lib/
```

`script.json`：

```json
{
  "schema": 1,
  "api": 1,
  "id": "hello-epsilon",
  "name": "Hello Epsilon",
  "version": "1.0.0",
  "authors": ["Your Name"],
  "modules": [
    {
      "id": "hello",
      "name": "Hello",
      "entry": "modules/hello.lua",
      "category": "PLAYER",
      "defaultEnabled": false,
      "defaultHidden": false
    }
  ]
}
```

`modules/hello.lua`：

```lua
module:on_enable(function()
    print("Hello from Epsilon Lua")
end)

module:on("client_tick.post", 0, function(event)
    if mc.player == nil then return end
end)
```

将目录复制到 `~/.epsilon/scripts/hello-epsilon/`，在 Client Settings 中启用 `Enable Lua Scripts`。首次发现
且没有 profile 状态的包默认开启；若此前关闭过它，则在 Addon Panel 中使用 `hello-epsilon` 自己的开关重新
开启。Module 会像原生 Module 一样出现在其分类中。

## API 来源

- 完整可运行示例：[example-suite](../examples/lua/example-suite/)
- Lua Language Server 类型库：[epsilon_lib.lua](../examples/lua/epsilon_lib.lua)
- Util 补全与生成器教程：[util-code-completion.md](util-code-completion.md)
- 类型库生成器：[`scripts/generate_epsilon_lib.py`](../../scripts/generate_epsilon_lib.py)
- Python/uv codegen 维护说明：[`scripts/README.md`](../../scripts/README.md)
- 当前游戏版本：Minecraft 26.1.2，具体 Java 方法签名以本仓库当前源码和
  `common/build/moddev/artifacts/vanilla-26.1.2-sources.jar` 为准。
