# 事件与 Java 调用

Epsilon 保留 LuaJ 原生 `luajava`，因此脚本能调用公开 Java class、构造函数、静态方法、实例方法和字段。
`mc`、事件对象、`module:java_module()`、`java_setting()`、`raw_scope()` 与 `raw_scheduler()` 都是 Java
userdata。

这不是安全沙箱。公开 Java API 可以访问文件系统、网络、线程和整个客户端状态，脚本作者需要自己承担权限、
线程和资源生命周期责任。

## Minecraft 实例

每个 runtime 已注入 `mc`：

```lua
local Minecraft = luajava.bindClass("net.minecraft.client.Minecraft")

assert(mc == Minecraft:getInstance())

if mc.player ~= nil and mc.level ~= nil then
    local position = mc.player:position()
    print(position.x .. ", " .. position.y .. ", " .. position.z)
end
```

在 Minecraft 26.1.2 中，`Minecraft.getInstance()` 是公开静态方法，`player` 与 `level` 是可空公开字段。
进入世界后才可假定它们非空；Module 的世界内 callback 应先检查。

## 绑定和创建 Java 类型

非事件类型使用全限定类名：

```lua
local ArrayList = luajava.bindClass("java.util.ArrayList")
local list = luajava.newInstance("java.util.ArrayList")

list:add("first")
print(list:size())

local Vec3 = luajava.bindClass("net.minecraft.world.phys.Vec3")
local point = luajava.new(Vec3, 1.0, 64.0, 2.0)
```

LuaJ 还提供 `createProxy(...)` 和 `loadLib(className, methodName)`。使用 Java interop 时注意：

- `bindClass` 不接受简单类名，必须写完整包名。
- Lua `number` 传给重载 Java 方法时可能出现重载选择歧义；优先使用 Epsilon wrapper，或确保参数能唯一匹配。
- Java `null` 在 Lua 中表现为 `nil`。
- Java collection 不自动等价于普通 Lua table。
- 只能直接访问 Java 可见性允许的成员；反射或模块系统限制仍由 JVM 决定。
- Minecraft、Epsilon 或依赖升级后，方法签名可能变化，应对照当前源码重新核验。

## Epsilon 与 Utils 类

Epsilon 不维护任意 class 的短名表。若要调用某个公开 Utils 类，绑定它的真实全限定名：

```lua
local SomeUtil = luajava.bindClass("com.github.epsilon.utils.SomeUtil")
-- SomeUtil:publicStaticMethod(...)
```

上面的 `SomeUtil` 只是格式示例，必须替换为当前源码中真实存在的类和方法。实例方法可以通过注入对象、事件
返回值、`luajava.newInstance` 或其他 Java 方法返回的 userdata 调用。平台专有 Fabric/NeoForge 类是否存在
取决于当前运行平台，不应把它们写进跨平台脚本的公共路径。

## 订阅内置事件 ID

常用事件有稳定的字符串 ID：

```lua
module:on("client_tick.post", 0, function(event)
end)

module:on("packet.send", 100, function(event)
    local packet = event:getPacket()
end)
```

当前 ID 列表：

| ID | Java class |
|---|---|
| `client_tick.pre` | `ClientTickEvent.Pre` |
| `client_tick.post` | `ClientTickEvent.Post` |
| `packet.send` | `PacketEvent.Send` |
| `packet.receive` | `PacketEvent.Receive` |
| `player_tick.pre` | `PlayerTickEvent.Pre` |
| `player_tick.post` | `PlayerTickEvent.Post` |
| `render2d.level` | `Render2DEvent.Level` |
| `render2d.hud` | `Render2DEvent.HUD` |
| `render3d` | `Render3DEvent` |

priority 越高越早调用。`render3d` priority 必须高于 `-999`，以便在 scheduler flush 前提交命令。

## bindEventClass 简写

Epsilon 在 LuaJ 上增加了 `luajava.bindEventClass(name)`：

```lua
local MoveEvent = luajava.bindEventClass("MoveEvent")

module:on_class(MoveEvent, 100, function(event)
    event:setX(0.0)
    if not event:isCancelled() then
        event:cancel()
    end
end)
```

它是 Epsilon 扩展，不是 LuaJ 原生 API。它通过显式 registry 解析 Epsilon 事件，不扫描 classpath，也不会在
短名失败后自动尝试任意 Java class。嵌套类型使用完整短名：

```lua
local TickPost = luajava.bindEventClass("ClientTickEvent.Post")
local PacketSend = luajava.bindEventClass("PacketEvent.Send")
local HudRender = luajava.bindEventClass("Render2DEvent.HUD")
```

不要写裸 `Post` 或 `Send`。可用名称以生成的
[`epsilon_lib.lua`](../examples/lua/epsilon_lib.lua) 中 `EpsilonEventClassName` 为准。

## 监听其他公开事件 class

若事件未提供字符串 ID，优先用 `bindEventClass`。registry 外的公开事件类型可以使用全限定名：

```lua
local CustomEvent = luajava.bindClass("example.addon.events.CustomEvent")

module:on_class(CustomEvent, 0, function(event)
end)
```

EventBus 按事件的精确运行时 class 分发。监听父类型不会自动收到子类型事件，例如监听
`ClientTickEvent` 不会收到 `ClientTickEvent.Post`。

## 取消和修改事件

Epsilon 的可取消事件继承 `Cancellable`：

```lua
if not event:isCancelled() then
    event:cancel()
end
```

不存在 `setCancelled(boolean)`。可修改事件应调用它实际公开的 setter，例如 `MoveEvent:setX(...)` 或
`PacketEvent.Send:setPacket(...)`。具体 getter/setter 以
`common/src/main/java/com/github/epsilon/events/impl/` 为准。

## callback 参数

普通事件和 packet 事件 callback 接收一个参数：

```lua
module:on_class(luajava.bindEventClass("MoveEvent"), 0, function(event)
    local x = event:getX()
end)
```

2D 与 3D 渲染事件有 Epsilon wrapper，参数分别为 `(ui, event)` 和 `(render3d, event)`，详见
[2D 与 3D 渲染](rendering.md)。

## 线程规则

callback 在事件原线程同步执行，同一个 Module runtime 的 callback 由可重入锁串行化。特别注意：

- tick、输入和渲染事件通常在客户端/渲染线程执行。
- packet send/receive 为保留取消和修改能力，在发送线程或网络线程同步执行。
- packet callback 中的慢操作会直接阻塞网络，不得 `yield`。
- 只能在渲染 callback 的有效期内使用渲染 context 或其 raw 对象。
- Minecraft 客户端对象通常不是线程安全的；不要在 packet callback 中任意调用只允许客户端线程的 API。

Lua runtime 串行化只保护同一 Module 的 Lua 执行，不会把 callback 自动切换到主线程，也不会让任意 Java
对象变得线程安全。
