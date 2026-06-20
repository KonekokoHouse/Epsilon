# Module 与事件系统

## 核心包结构

所有平台无关代码位于 `common/src/main/java/com/github/epsilon/`：

| 包名 | 用途 |
|------|------|
| `addon/` | Addon 基类、注册事件、Bootstrap 工具 |
| `assets/` | i18n 翻译、配置文件迁移、资源持有者 |
| `events/` | 自定义事件总线、27 种事件类型 |
| `graphics/` | Lumin Graphics 渲染框架 |
| `gui/` | 点击 GUI（Panel、Dropdown、HUD 编辑器） |
| `managers/` | 各种管理器（Addon、Config、Module、Rotation 等） |
| `mixins/` | 37 个 Mixin 注入类 |
| `modules/` | Module 基类、Category 枚举、HudModule |
| `settings/` | Setting 基类与 8 种设置类型 |
| `utils/` | 工具类（client、combat、math、network、player、render、timer、rotation、world） |

## Module 基本模板

所有功能模块继承 `Module`，参考现有模块，如 `common/.../modules/impl/combat/KillAura.java`。

```java
public class MyModule extends Module {

    public static final MyModule INSTANCE = new MyModule();

    private MyModule() {
        super("My Module", Category.COMBAT);
    }

    private final BoolSetting enabled = boolSetting("Enabled", true);
    private final IntSetting range = intSetting("Range", 4, 1, 64, 1);
    private final DoubleSetting reach = doubleSetting("Reach", 3.0, 1.0, 6.0, 0.1);

    @Override
    protected void onEnable() {
    }

    @Override
    protected void onDisable() {
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
    }
}
```

## Module 开发约定

1. 每个 Module 使用 `public static final MyModule INSTANCE = new MyModule()` 单例模式
2. 构造函数必须私有
3. 事件处理方法中先调用 `nullCheck()` 检查世界和玩家
4. Setting 必须声明为类的实例字段，并在构造阶段初始化
5. 事件监听方法必须使用 `@EventHandler`，且必须是实例方法
6. 事件方法必须 `void` 返回，只接收一个事件参数
7. 内置模块在 `ModuleManager.initModules()` 中注册
8. 模块名称和设置名称需要补 i18n 翻译

## Setting 说明

### 可见性依赖

Setting 的依赖是一个 `() -> boolean`，返回 `false` 时该设置在 GUI 中隐藏。

```java
private final BoolSetting enableEsp = boolSetting("Enable ESP", true);
private final ColorSetting espColor = colorSetting("ESP Color", Color.WHITE, () -> enableEsp.getValue());
```

也可以使用 lambda 解决声明顺序问题：

```java
private final BoolSetting advanced = boolSetting("Advanced", false);
private final IntSetting threshold = intSetting("Threshold", 50, 0, 100, 1, () -> advanced.getValue());
```

### 分组

```java
private final SettingGroup sgCombat = settingGroup("Combat");
private final BoolSetting autoAttack = boolSetting("Auto Attack", true).group(sgCombat);
```

### 模块键位

- `getKeyBind()` / `setKeyBind(int)`：键位代码（GLFW key code）
- `BindMode.Toggle`：按下切换开关
- `BindMode.Hold`：按住启用，松开禁用

## 事件系统

### EventBus

Epsilon 使用自定义事件总线，与 Minecraft 原生事件系统独立。

- 订阅：`EventBus.INSTANCE.subscribe(object)` / `EventBus.INSTANCE.subscribe(Class)`
- 取消订阅：`EventBus.INSTANCE.unsubscribe(object)` / `EventBus.INSTANCE.unsubscribe(Class)`
- 发布：`EventBus.INSTANCE.post(event)`
- Lambda 工厂注册：`EventBus.INSTANCE.registerLambdaFactory("com.github.epsilon", factory)`，已由 `EpsilonCommon.init()` 自动注册

Module 启用时自动 `subscribe(this)`，禁用时自动 `unsubscribe(this)`。

### 常用事件类型

| 事件类 | 描述 |
|--------|------|
| `TickEvent.Pre` / `TickEvent.Post` | 每 tick 前后 |
| `Render2DEvent.Level` / `Render2DEvent.HUD` | 2D 渲染 |
| `Render3DEvent` | 3D 渲染 |
| `PacketEvent.Send` / `PacketEvent.Receive` | 网络包收发 |
| `KeyPressEvent` | 按键按下/释放 |
| `MousePressEvent` | 鼠标按键 |
| `AttackEntityEvent` | 攻击实体 |
| `JumpEvent` | 跳跃 |
| `CollisionEvent` | 碰撞检测 |
| `TravelEvent` | 移动 |
| `VelocityEvent` | 击退 |
| `RaytraceEvent` | 射线追踪 |
| `KeyboardInputEvent` | 键盘输入 |
| ... | 等 27 个事件 |

### 事件优先级

`@EventHandler(priority = EventPriority.HIGH)` 对应优先级顺序：`HIGHEST` > `HIGH` > `MEDIUM` > `LOW` > `LOWEST`。

也支持直接传入整数，数值越大越先执行，数值越小越晚执行。

### 可取消事件

实现 `Cancellable` 接口的事件可以通过 `event.setCancelled(true)` 取消，取消后低优先级监听器不再执行。
