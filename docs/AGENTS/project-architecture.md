# 项目架构与构建

## 三层架构

```text
Epsilon/
├── common/          ← 平台无关核心代码（模块、事件、图形、工具类）
├── fabric/          ← Fabric 加载器适配层
├── neoforge/        ← NeoForge 加载器适配层
├── buildSrc/        ← Gradle 自定义插件（multiloader-common、multiloader-loader）
└── docs/            ← 文档
```

## 目录职责

- `common/`：平台无关核心逻辑
- `fabric/`：Fabric 入口与加载器适配
- `neoforge/`：NeoForge 入口与加载器适配
- `buildSrc/`：Gradle 约定插件
- `docs/`：开发文档

## 关键原则

- `common/` 中的代码不能调用任何 ModLoader API
- 需要加载器 API 时，在 `fabric/` 或 `neoforge/` 中实现，再通过 compat 接口传入 common
- `common/` 中的 Java 源码会被 Gradle 自动共享到 `fabric` 和 `neoforge` 的编译路径
- 编写前必须查阅当前版本 Minecraft 源码

## 构建系统

### 版本信息

当前 Minecraft 版本、Fabric API、NeoForge 版本等信息均在 `gradle.properties` 中定义，编写代码时优先参考该文件。

### buildSrc 约定插件

- `multiloader-common.gradle.kts`：common 子项目共享配置，包括 JDK 工具链、Maven 仓库、资源处理、发布配置
- `multiloader-loader.gradle.kts`：fabric 与 neoforge 共享配置，自动关联 `:common` 源码与资源

## Minecraft 源码查阅

源代码位于：`common/build/moddev/artifacts/vanilla-<游戏版本>-sources.jar`

如果不存在，执行：

```bash
./gradlew :common:downloadAssets
./gradlew :common:createMinecraftArtifacts
```

查阅后解压：

```bash
mkdir -p reference && unzip common/build/moddev/artifacts/vanilla-*-sources.jar -d reference/vanilla/
```
