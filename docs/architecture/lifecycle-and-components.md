# 生命周期与核心组件

## 启动流程

两个平台都通过平台 Mixin 注入 `Minecraft` 构造函数尾部，再进入各自 Loader：

```text
Minecraft.<init> TAIL
  -> fabric/neoforge Loader
  -> EpsilonFabric/EpsilonNeoForge
  -> 收集并注册 Addon
  -> EpsilonCommon.init()
  -> 注册平台图形生命周期
```

`EpsilonCommon.init()` 当前顺序：

1. 设置 `Constants.mc`，注册 `com.github.epsilon` 包的 EventBus lambda factory。
2. `ModuleHolder.initModules()`。
3. `HudElementHolder.initElements()`。
4. `AddonHolder.setupAddons()`。
5. `ConfigHolder.initConfig()`。
6. 选择当前语言。
7. `Managers.initManagers()`。
8. 初始化 `com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler`，注册 priority `-999` 的统一 flush。
9. 生成空 i18n 模板，并注册退出时保存配置的 shutdown hook。

## LuminGraphics-MC runtime

Fabric 和 NeoForge 的平台入口绑定当前 Minecraft context，并把 Loader 无关的生命周期交给
LuminGraphics-MC。`MinecraftUiRuntime2612` 在渲染线程管理字体、glyph atlas、Minecraft 纹理、render
target、native extraction bridge、资源重载处理和 2D frame resources；关闭时只释放 Epsilon/Lumin
拥有的包装器，不关闭借入的 Minecraft image、view 或 native handle。

Epsilon 业务 UI 直接使用公共 Lumin `UiTree`/`UiScene` 类型。`MinecraftGuiExtractionBridge2612` 把原版
`GuiGraphicsExtractor` 的 native state 提交给 LuminGraphics-MC，原版 overlay 因此可以与声明式 UI
共享同一帧。常规 HUD 在原版 HUD 提取结束、当前 Screen 提取开始前构建并提交独立 `UiTree`，保证
Dropdown/Panel GUI 的命令录制和原版节点都位于 HUD 之后。共享代码不导入 Fabric 或 NeoForge API。

## 保留的 3D 与共享组件

2D 迁移不改变 Epsilon 的 3D/shared 路径。`Render3DScheduler`、`LuminRenderSystem` 以及现有 3D
shaders、buffers 和 immediate renderer 仍在 `common/` 中创建、提交和按原有 priority/sequence 规则
flush。2D runtime 的资源所有权和帧边界不得替代这些 3D 路径。

## Holders

| Holder | 职责 |
|---|---|
| `ModuleHolder` | 注册本体/Addon 模块，处理键盘与鼠标绑定 |
| `HudElementHolder` | 注册 HUD，构建独立 `UiTree` 后通过公共 Lumin `UiScene` 统一提交，并处理原版 overlay |
| `AddonHolder` | Addon 去重、一次性 setup 与查询 |
| `ConfigHolder` | 多配置、导入导出、Setting/custom state、好友与迁移 |
| `TranslateHolder` | 跟踪 `TranslateComponent`，切换语言时刷新缓存 |
| `RenderTargetHolder` | 跟踪仍由 Epsilon 创建的 render target |
| `ShaderHolder` | 手部/箱子 outline 和保留的共享 shader 状态 |

## Managers

运行时管理器通过 `Managers` 的静态字段访问：

`ROTATION`、`EXTRAPOLATION`、`TARGET`、`HEALTH`、`C2SPACKET`、`S2CPACKET`、`FRIEND`、`SOUND`、`NOTIFICATION`、`TIMER`。

这些字段由 `Managers.initManagers()` 初始化。其中 Rotation Manager 可在运行时因模式切换而替换，调用方应通过 `Managers.ROTATION` 获取当前实例。
