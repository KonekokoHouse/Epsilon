# 构建与版本

## 版本来源

- `gradle.properties`：Epsilon 自身的 `version`、`group`、`mod_id`、`mod_name`、`mod_author`、许可证和描述。
- `gradle/libs.versions.toml`：JDK、Minecraft、NeoForm、Fabric API、Fabric Loader、NeoForge、Mixin、Sodium、LuminGraphics 和 PrismRHI 版本。
- 根 `build.gradle.kts`：将版本目录中的值映射为各子项目使用的 Gradle 属性。
- `common/build.gradle.kts`：生成 `com.github.epsilon.BuildConfig`，当前暴露 `MOD_ID` 和有效构建版本。

普通开发构建会在项目版本后追加当前 Git 短提交号；`buildRelease` 使用 `gradle.properties` 中的纯发布版本。

## 约定插件

- `multiloader-common.gradle.kts`：Java 25 工具链、仓库、资源展开、Jar 元数据、源码 Jar、发布和 `buildRelease`。
- `multiloader-loader.gradle.kts`：将 `:common` 的 Java、资源和生成源码加入 Fabric/NeoForge 编译与打包流程。

Sodium 兼容代码只在对应平台编译，不会把 Sodium 打入 Epsilon 成品。

## Lumin 发布版本

Epsilon 优先从本机 `mavenLocal()` 解析 `com.github.slmpc` 依赖，未找到时从
`https://slmpc.github.io/maven-repository` 获取，不依赖仓库绝对路径。版本目录当前消费 PrismRHI
`0.2.2`、LuminGraphics `1.2.5` 和 LuminGraphics-MC `1.2.5`。LuminGraphics-MC
会将 LuminGraphics class 直接打入 loader JAR，因此两者必须共用同一版本键。三个上游项目发布到远端 Maven
仓库后，CI 可以直接构建；本地开发也可以用 `publishToMavenLocal` 覆盖同版本依赖。

```powershell
cd D:\Dev\OpenEpsilon\Open-Epsilon
.\gradlew.bat buildRelease --no-daemon --stacktrace
```

### 内嵌 Lumin 运行时的依赖声明

两个成品 Jar 各自内嵌与自身加载器匹配的 LuminGraphics-MC：Fabric 侧走 Loom `include`（`META-INF/jars/`），
NeoForge 侧走 JarJar（`META-INF/jarjar/`）。上游 Fabric 版 `fabric.mod.json` 把 `fabricloader` 与
`fabric-api` 写成 `=` 精确依赖，而 Fabric Loader 对依赖不满足的内嵌 mod 只是静默排除、不报错，
玩家一旦更新 Loader 或 Fabric API，整个 Lumin 运行时就会从 classpath 消失，Epsilon 直到首次触碰
Lumin class 才抛 `NoClassDefFoundError`。因此：

- `fabric/build.gradle.kts` 在 `processIncludeJars` 的 `doLast` 中重写暂存的内嵌 Jar，把这两条精确
  依赖降级为下限（`=0.19.2` → `>=0.19.2`）；`minecraft` 与 `java` 保持原样，Lumin 的 MC 绑定确实
  只对应单一游戏版本。重打包逐条保留原始压缩方式与时间戳，STORED 条目补齐 `size`/`crc`。
- `fabric/src/main/resources/fabric.mod.json` 硬依赖 `lumin_graphics_mc >=${lumin_graphics_version}`，
  把「运行时消失」从运行期崩溃变成加载期报错。该占位符由版本目录经根 `build.gradle.kts` 的
  `extra["lumin_graphics_version"]` 传入 `multiloader-common` 的资源展开，子项目脚本不写死版本号。
- `verifyLuminJarInJar` 除了原有的「只内嵌匹配加载器的 Lumin、且只有一个 LuaJ」断言，还会解开
  Fabric 成品的内嵌 Jar，确认其 `fabric.mod.json` 不再残留 `=` 精确依赖，并确认外层
  `fabric.mod.json` 的 `depends` 中声明了 `lumin_graphics_mc`。

NeoForge 版内嵌元数据同样把 `neoforge` 与 `minecraft` 钉在单点区间，但 NeoForge 缺依赖时会直接报错
而非静默丢弃，且其 Maven `ComparableVersion` 不能正确处理 `1.2.5+mc26.1.2` 这类构建元数据，所以这一侧
不做重写。

重打包只是过渡手段，根因在上游加载器元数据本身。已向上游提交
[slmpc/LuminGraphics-MC#1](https://github.com/slmpc/LuminGraphics-MC/pull/1)：三个 Minecraft 版本树的
`fabricloader`/`fabric-api` 由 `=` 改为 `>=`，NeoForge 的 `versionRange` 由 `[X]` 改为 `[X,)`，同时放宽
上游 `verifyFabricWiring`/`verifyNeoForgeContract` 两个门禁（`minecraft` 仍是精确单版本——加载器区间放开
后，它才是把 mod 约束在对应游戏版本上的那一条）。上游发布带该修复的版本后，可以删掉
`processIncludeJars` 的重写，`verifyLuminJarInJar` 的断言留下来当回归门禁。

## 常用命令

Windows PowerShell：

```powershell
.\gradlew.bat buildRelease --stacktrace
.\gradlew.bat verifyLuminJarInJar
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```

CI 使用 Java 25 执行：

```powershell
.\gradlew.bat build
```

构建产物包括 Fabric 与 NeoForge Jar，并由 CI 上传。

## 验证

仓库当前不维护测试源码或测试专用依赖。修改后使用与范围匹配的编译、`buildRelease` 和客户端运行检查；
改动打包流程、内嵌依赖或 `fabric.mod.json` / `neoforge.mods.toml` 元数据时另跑
`.\gradlew.bat verifyLuminJarInJar`。具体验证范围遵循 [`AGENTS.md`](../../AGENTS.md) 的提交前检查。

## 外部资料

- [NeoForge 文档](https://docs.neoforged.net/)
- [NeoForge Primer](https://docs.neoforged.net/primer/docs/)
- [Fabric 文档](https://docs.fabricmc.net/develop/)
- [Mixin 介绍](https://wiki.fabricmc.net/tutorial:mixin_introduction)
- [Mixin 示例](https://wiki.fabricmc.net/tutorial:mixin_examples)
- [Porting Primers](https://gu-zt.github.io/Porting-Primers/)

外部资料用于理解加载器与 Mixin 机制。项目当前 Minecraft 版本的类和签名仍以 `common/build/moddev/` 中由当前 NeoForm 生成的源码为准；获取流程见根目录 [`AGENTS.md`](../../AGENTS.md)。
