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

## 常用命令

Windows PowerShell：

```powershell
.\gradlew.bat buildRelease --stacktrace
.\gradlew.bat :fabric:runClient
.\gradlew.bat :neoforge:runClient
```

CI 使用 Java 25 执行：

```powershell
.\gradlew.bat buildRelease --no-daemon --stacktrace
```

构建产物包括 Fabric 与 NeoForge Jar，并由 CI 上传。

## 验证

仓库当前不维护测试源码或测试专用依赖。修改后使用与范围匹配的编译、`buildRelease` 和客户端运行检查；
具体验证范围遵循 [`AGENTS.md`](../../AGENTS.md) 的提交前检查。

## 外部资料

- [NeoForge 文档](https://docs.neoforged.net/)
- [NeoForge Primer](https://docs.neoforged.net/primer/docs/)
- [Fabric 文档](https://docs.fabricmc.net/develop/)
- [Mixin 介绍](https://wiki.fabricmc.net/tutorial:mixin_introduction)
- [Mixin 示例](https://wiki.fabricmc.net/tutorial:mixin_examples)
- [Porting Primers](https://gu-zt.github.io/Porting-Primers/)

外部资料用于理解加载器与 Mixin 机制。项目当前 Minecraft 版本的类和签名仍以 `common/build/moddev/` 中由当前 NeoForm 生成的源码为准；获取流程见根目录 [`AGENTS.md`](../../AGENTS.md)。
