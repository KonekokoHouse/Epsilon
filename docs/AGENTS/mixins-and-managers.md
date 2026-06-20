# Mixin 与管理器

## Mixin 开发

Mixin 类位于 `common/src/main/java/com/github/epsilon/mixins/`。

### 配置文件

- `common/src/main/resources/epsilon.mixins.json`：common 层 mixins
- `fabric/src/main/resources/epsilon.fabric.mixins.json`：Fabric 专有 mixins
- `neoforge/src/main/resources/epsilon.neoforge.mixins.json`：NeoForge 专有 mixins

### Access 配置

- `common/src/main/resources/epsilon.accesswidener`：Fabric 访问拓宽
- `common/src/main/resources/META-INF/accesstransformer.cfg`：NeoForge 访问转换

### 编写规则

1. 不要凭空猜测 API，先查官方文档和当前源码
2. Mixin 类命名使用 `Mixin<目标类名>`
3. 优先使用 `@Inject` 与 `@WrapOperation`，少用 `@Overwrite`
4. 事件发布通常在 `@Inject` 回调中通过 `EventBus.INSTANCE.post(...)` 完成
5. 遇到不熟悉的签名必须先确认文档和源码

## 管理器概览

### ModuleManager

- `initModules()`：注册所有内置模块
- `registerAddonModule(addonId, module, translateComponent)`：注册 Addon 模块
- `getModules()`：获取所有已注册模块

### AddonManager

- `registerAddon(addon)`：注册单个 addon
- `registerAddons(Iterable)`：批量注册 addon
- `setupAddons()`：初始化 addon i18n 并调用 `onSetup()`
- `getAddons()`：获取已注册 addon 列表

### 其他管理器

| 管理器 | 职责 |
|--------|------|
| `ConfigManager` | 配置序列化、反序列化、自动保存 |
| `RotationManager` | 旋转角度管理 |
| `HealthManager` | 实体生命值缓存 |
| `TargetManager` | 目标选择 |
| `FriendManager` | 好友管理 |
| `SoundManager` | 音效播放 |
| `ServerboundPacketManager` / `ClientboundPacketManager` | 网络包管理 |

## RotationManager

RotationManager 管理玩家视角旋转，支持优先级队列、平滑插值和射线追踪偏移。

### 核心概念

- `setRotations()` 设置目标旋转后，RotationManager 每 tick 自动平滑旋转
- 旋转角度会在 `SendPositionEvent` 中写入发包
- 旋转完成后会自动 `active = false`
- 多个模块同时设置时，高优先级覆盖低优先级
- 旋转过程中每 tick 会发布 `AfterRotationEvent`

### 常用 API

```java
float yaw = RotationManager.INSTANCE.getYRot();
float pitch = RotationManager.INSTANCE.getXRot();
Vector2f current = RotationManager.INSTANCE.getRotation();

RotationManager.INSTANCE.setRotations(new Vector2f(yaw, pitch), rotationSpeed);
RotationManager.INSTANCE.setRotations(new Vector2f(yaw, pitch), rotationSpeed, Priority.High);

RotationManager.INSTANCE.setRotations(rotations, rotationSpeed, null, Priority.High, () -> {
    if (RaytraceUtils.overBlock(
            new Vector2f(RotationManager.INSTANCE.getYRot(), RotationManager.INSTANCE.getXRot()),
            side, blockPos, true)) {
        mc.gameMode.useItemOn(mc.player, hand, hitResult);
    }
});

boolean active = RotationManager.INSTANCE.isActive();
boolean done = RotationManager.INSTANCE.isDone();
```

### 优先级

| 优先级 | 值 | 用途 |
|--------|----|------|
| `Lowest` | -200 | 预旋转 |
| `Low` | -100 | 低优先级旋转 |
| `Medium` | 0 | 默认优先级 |
| `High` | 100 | 攻击、放置等即时操作 |
| `Highest` | 200 | 最高优先级 |

### 回调使用模式

推荐通过 `setRotations(..., callback)` 实现“旋转 -> 瞄准 -> 操作”的解耦流程，无需手动订阅 `AfterRotationEvent`。

### 设计要点

- callback 在 `RotationManager.onTick()` 中每 tick 执行一次
- callback 内应使用 `getYRot()` / `getXRot()` 获取当前平滑后的旋转角度
- callback 需要自行防止重复执行
- 模块禁用时应清理 pending 状态，并在需要时 `InvUtils.swapBack()`
