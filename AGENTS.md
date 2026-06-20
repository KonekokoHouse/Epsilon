# Epsilon 本体开发

## 概述

Epsilon 是一个面向 Minecraft 的多加载器（Multi-Loader）客户端工具模组，同时支持 Fabric 和 NeoForge，采用三层架构。

## 核心规则

- `common/` 中的代码**不能调用任何 ModLoader 的 API**
- 需要加载器 API 时，在 `fabric/` 或 `neoforge/` 子项目中实现，通过 compat 接口传入 common
- `common/` 中的 Java 源码会被 Gradle 自动共享到 `fabric` 和 `neoforge` 的编译路径
- 编写前必须查阅当前版本 Minecraft 源码，不要基于训练数据猜测类名、方法名或版本差异
- 编译环境使用 `JDK 25`
- 代码使用 Java，遵循项目现有风格
- Module 与 Addon 使用私有构造函数 + 单例 `INSTANCE` 模式
- 所有注释使用中文
- `Logger` 通过 `Constants.LOGGER` 获取
- Minecraft 实例通过 `Constants.mc` 或 Module 中的 `this.mc` 获取

## 源码查阅

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

## 文档索引

- 架构与构建：`docs/AGENTS/project-architecture.md`
- Module 与事件系统：`docs/AGENTS/module-and-events.md`
- Mixin 与管理器：`docs/AGENTS/mixins-and-managers.md`
- 渲染系统：`docs/AGENTS/rendering.md`
- i18n、代码规范与参考资料：`docs/AGENTS/project-conventions.md`
- 提交规范：`docs/AGENTS/commit-rules.md`
- Addon 开发：`docs/addon-development.md`

## 优先阅读

- 新增或修改模块前，先看 `docs/AGENTS/module-and-events.md`
- 修改渲染逻辑前，先看 `docs/AGENTS/rendering.md`
- 修改旋转、目标选择、配置注册前，先看 `docs/AGENTS/mixins-and-managers.md`
- 准备提交 commit 或开 PR 前，先看 `docs/AGENTS/commit-rules.md`
