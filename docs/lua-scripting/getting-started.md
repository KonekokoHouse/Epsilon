# 快速开始

## 安装脚本包

脚本根目录固定为当前用户目录下的：

```text
~/.epsilon/scripts/
```

其中每个包含 `script.json` 的一级子目录都是一个独立包。例如：

```text
~/.epsilon/scripts/example-suite/script.json
```

可以先将仓库中的 [`docs/examples/lua/example-suite`](../examples/lua/example-suite/) 整个目录复制到该位置。
不要只复制 `modules/`，loader 只通过包根目录的 `script.json` 发现包。

## 启用顺序

1. 打开 Epsilon Client Settings 的 `Lua Scripts` 分组。
2. 开启默认关闭的 `Enable Lua Scripts`。
3. 打开 Addon Panel，找到带 Lua/code 标识的 `example-suite`。
4. 确认该包自己的开关已开启；首次发现且没有 profile 状态的包默认开启。
5. 在 `Render` 分类中分别启用 `HUD Sample` 或 `World Box` Module。

全局开关不在 Addon Panel 中重复出现。全局开关决定 Lua 系统是否运行；每个脚本包的开关决定该包是否
加载。两层开关的详细行为见 [管理与生命周期](management-and-lifecycle.md)。

## 修改脚本

Epsilon 不监听文件变化。修改 `script.json`、`settings.lua`、`modules/*.lua`、`lib/*.lua` 或语言文件后，
在该脚本包详情中点击 Reload。Reload 总是以整个包为单位重新读取文件。

若新版本加载失败，当前已经运行的包会继续保留，错误会显示在脚本条目中。修复文件后再次点击 Reload。

## VS Code 与 LuaLS

安装 VS Code 扩展 `sumneko.lua`，然后将 `.epsilon/scripts/` 作为 workspace 打开。在 workspace 根目录创建
`.luarc.json`：

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

将路径替换为本机仓库中 `docs/examples/lua` 的绝对路径。Windows 路径建议使用 `/`。如果只打开一个包，
也可以把 `.luarc.json` 放在该包根目录。

配置后从命令面板执行 `Lua: Restart Language Server`。`epsilon_lib.lua` 是 `---@meta` 类型库，只供 IDE
读取，脚本运行时不得写 `require("epsilon_lib")`。

## 重新生成补全库

Java 层的 Lua API 或 `com.github.epsilon.utils` 工具类变化后，在仓库根目录执行：

```powershell
uv sync --frozen
uv run scripts/dev.py lua update
```

生成器使用 Tree-sitter Java AST 发现公开 Util class，并解析其公开字段、构造器、方法重载和类型，同时生成
Java `LuaUtilRegistry.java` 与 LuaLS `epsilon_lib.lua`。静态 host API 来自结构化 JSON；事件 ID、
`bindEventClass` 事件名和各 API table 的导出键继续从 Java 注册点提取或校验。详细规则见
[`scripts/README.md`](../../scripts/README.md)。已注册 Util 会获得成员级补全；任意 Minecraft userdata 自身的
方法，例如 `LocalPlayer` 方法，不由该文件完整建模，仍需查阅当前 Minecraft 参考源码。

静态方法、字段、构造器、重载、varargs、nullable、嵌套 enum 和 JSON 数据模型的完整示例见
[Util 简写与代码补全](util-code-completion.md)。

## 下一步

先阅读 [脚本包与 Module](packages-and-modules.md)，理解多 Module 包的 entrypoint 隔离，再按需阅读
[Setting 与存储](settings-and-storage.md)、[事件与 Java 调用](events-and-java.md) 和
[Util 简写与代码补全](util-code-completion.md)，再阅读 [2D 与 3D 渲染](rendering.md)。
