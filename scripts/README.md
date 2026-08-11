# Epsilon 开发脚本

仓库的 Python 工具使用 [uv](https://docs.astral.sh/uv/) 管理固定依赖。首次使用或 `uv.lock` 更新后执行：

```powershell
uv sync --frozen
```

## 统一入口

日常使用统一入口 `scripts/dev.py`，不需要记住各脚本文件名和完整 Python 参数：

```powershell
uv run scripts/dev.py --help
```

| 命令 | 行为 | 是否修改文件 |
|---|---|---|
| `uv run scripts/dev.py verify` | 运行全部非修改性检查；当前包含 Lua 测试和生成物漂移检查 | 否 |
| `uv run scripts/dev.py lua generate` | 重新生成 Lua registry 和 LuaLS 类型库 | 是 |
| `uv run scripts/dev.py lua check` | 检查 Lua 生成物是否过期 | 否 |
| `uv run scripts/dev.py lua test` | 只运行 Lua codegen 测试 | 否 |
| `uv run scripts/dev.py lua verify` | 依次运行 Lua 测试和漂移检查 | 否 |
| `uv run scripts/dev.py lua update` | 生成后运行测试和漂移检查 | 是 |
| `uv run scripts/dev.py i18n [参数]` | 运行 i18n 补全器，参数原样转发 | 取决于是否传入 `--dry-run` |

子命令在任一步失败后立即停止并保留原退出码，适合本地开发和 CI。需要查看 i18n 的全部选项时执行：

```powershell
uv run scripts/dev.py i18n --help
```

## Lua API codegen

Lua codegen 使用 Tree-sitter Java AST 扫描 `common/src/main/java/com/github/epsilon/utils/`，使用
`scripts/lua_codegen/epsilon_api.json` 描述非 Java 的 Lua API，并生成：

- `common/src/main/java/com/github/epsilon/scripting/lua/LuaUtilRegistry.java`
- `docs/examples/lua/epsilon_lib.lua`

生成命令：

```powershell
uv run scripts/dev.py lua generate
```

提交前检查生成产物没有漂移：

```powershell
uv run scripts/dev.py lua check
```

运行 Java AST 与 LuaLS renderer 的单元测试：

```powershell
uv run scripts/dev.py lua test
```

Util 发现规则为：

- 必须是 `com.github.epsilon.utils` 目录树中的公开顶层 `class`。
- 类名以 `Utils` 结尾；稳定但不使用该后缀的入口在
  `scripts/lua_codegen/java_utils.py` 的 `EXTRA_UTIL_CLASS_NAMES` 中声明。
- 当前额外入口为 `WorldToScreen`。
- 内部类、record、enum、非 public class、GUI 私有工具和 ESP renderer 不会自动暴露。
- 简单类名必须全局唯一；重名会让 codegen 失败，不会根据扫描顺序选择。

每个已暴露 Util 的以下源码声明会进入 `epsilon_lib.lua`：

- 公开 static/instance 字段，并保留 `final` 只读信息。
- 公开显式构造器，以及 public class 没有声明构造器时的隐式无参构造器。
- 公开 static/instance 方法、全部重载、参数名、返回类型、varargs 和 `@Nullable`。
- 公开嵌套 enum 及常量；LuaJ 绑定使用 JVM binary name（`Outer$Inner`）。

Java 类型映射坚持保守原则：布尔、整数、浮点和字符串映射到对应 LuaLS 基础类型；已注册 Util 和已发现
嵌套 enum 映射到生成 class；其他 Java object、collection、generic 和数组保持 `userdata`。生成注释保留
Java 源码类型，不能把 Java collection 或数组当成 Lua table。

`epsilon_api.json` 是静态 Lua host API 的唯一模板。alias、class、field、method、overload 和 global 都使用
结构化数据描述；事件名、事件 ID、Util 名称、Util overload 和构造器 overload 在生成时从 Java 源码注入。
新增 Lua host API 时修改 JSON 与对应 Java 导出，生成器会校验两边的 key 是否一致。

不要直接编辑两个生成产物。修改 Util class、Java Lua API 或 `epsilon_api.json` 后重新运行生成器，并将
源码、模型与生成结果放在同一次提交中。

## i18n

现有 i18n 补全脚本通过统一入口运行：

```powershell
uv run scripts/dev.py i18n
```
