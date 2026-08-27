# 调度器

`com.github.epsilon.schedule` 是服务于 `RotationManager` 的 tick 级协程调度器，用 Kotlin 实现，只依赖
`kotlin-stdlib`。它把「转头到位再放置、放完等几 tick、期间被更高优先级模块抢走就回滚」这类跨 tick 流程
写成串行代码，取代模块里手写的 `timer` / `pending` / `waitingSwapBack` 状态位。

调度器是新增的一层，不改动 `RotationManager.setRotations` 本身：既有模块继续每 tick 直接请求旋转，只有
调度任务参与资源锁与抢占仲裁。旋转优先级仍由 `Priority` 表达，两条路径最终都落在同一个
`Managers.ROTATION` 上。

## 三个借来的概念

| 概念 | 借自 | 形式 |
|---|---|---|
| 资源独占 | `synchronized` | `lock(Resource.ROTATION)`，任务内可重入，任务结束自动释放，没有 `unlock` |
| 挂起等待 | 协程 `await` | `await(delay(3))`，挂起由调度器按 tick 恢复，任务体读起来是串行的 |
| 任务依赖 | Gradle `dependsOn` | `node(a, b) { ... }`，前置节点全部跑完才启动，互不依赖的节点天然并行 |

挂起用 `kotlin.coroutines.intrinsics` 手动驱动（`createCoroutineUnintercepted` /
`suspendCoroutineUninterceptedOrReturn` / `COROUTINE_SUSPENDED`），不引入 `kotlinx-coroutines`，也不另起
线程：任务体始终跑在游戏线程上，因此可以直接读写 Minecraft 状态。

## API

```kotlin
Scheduler.launch("crystal-blocker", Priority.Highest) { block(pos) }
Scheduler.cancel("crystal-blocker")
Scheduler.isRunning("crystal-blocker")
Scheduler.ticks                                   // 调度器自身的 tick 计数，delay 以它为基准
```

`launch` 按名字去重：同名任务仍在运行时直接返回，模块可以每 tick 无条件调用。任务体会同步执行到第一个
挂起点，因此第一次 `setRotations` 仍落在本 tick 的平滑之前。

任务体的接收者是 `TaskScope`：

```kotlin
val priority: Priority                            // 本任务的旋转优先级，aim 一类 awaitable 用它请求旋转
fun node(vararg dependsOn: Node, body: suspend TaskScope.() -> Unit): Node
suspend fun await(awaitable: Awaitable)
suspend fun lock(resource: Resource)
```

`Resource` 当前有 `ROTATION`、`HOTBAR`、`INTERACT` 三项。`Awaitable` 是 `fun interface { fun poll(): Boolean }`，
每 tick 由调度器轮询一次，允许带副作用。内置两个工厂：

| 工厂 | 语义 |
|---|---|
| `delay(ticks)` | 等待 `ticks` 个 tick；`delay(0)` 不消耗 tick |
| `TaskScope.aim(target, speed, tolerance)` | 以本任务优先级持续请求旋转，直到偏差进入 `tolerance` 度 |

`aim` 每次轮询都重新调用 `setRotations`，既让 `RotationManager` 保持 active，也与模块每 tick 自行下发时的
行为一致；管理器实例每次重新从 `Managers.ROTATION` 取，以适应旋转模式切换。

## tick 时序

`PlayerTickEvent.Pre` 的监听器按 priority 降序分发，调度器挂在 `-900`：

```text
模块 @EventHandler (0)  →  Scheduler 推进 (-900)  →  RotationManager.smooth() (-1000)
```

模块已算完本 tick 的目标，`RotationManager` 的平滑尚未执行，因此 awaitable 读到的旋转状态与模块直接调用
`setRotations` 时完全一致。

## 锁与抢占

`lock` 在资源空闲或已被本任务持有时立即返回，否则挂起。**优先级严格更高**的任务立即抢占：原持有者被判死，
其挂起点抛出 `Cancelled`，任务体的 `finally` 得以回滚状态（换回槽位等）。优先级相同或更低时排队等待。

取消一律推迟到当轮推进结束后统一执行，受害者的 `finally` 不会在抢占者的调用栈里重入。`Scheduler.cancel`
与 `Module.onDisable()` 走同一条路径。

## 编写任务时的约束

- `finally` 里的回滚只能是同步操作。任务被取消后挂起点会立刻再次抛出 `Cancelled`，在 `finally` 里
  `await` 会让回滚做不完。
- `delay(n)` 的截止点在构造时算定，只应就地写成 `await(delay(n))`；提前存进变量再 `await` 会从构造那一刻
  起算。
- 任务名是全局唯一键，模块应使用带模块前缀的常量。
- 挂起点之后世界已经变了：落点、目标实体、背包槽位都要重新确认，不能沿用挂起前的判断。
- 不得长期缓存 `Managers.ROTATION`；awaitable 每次轮询重新读取。
- 任务体抛出的非 `Cancelled` 异常会记入 `Constants.LOGGER` 并终结整个任务。

## 示例：CrystalBlocker

`common/src/main/kotlin/com/github/epsilon/modules/impl/combat/CrystalBlocker.kt` 是随调度器一起迁移的
示范模块，一次用到三个概念：

```kotlin
private suspend fun TaskScope.block(pos: BlockPos) {
    val aimed = node { face(pos) }                    // 转头
    val armed = node { lock(Resource.HOTBAR) }        // 只抢锁，与转头并行
    node(aimed, armed) {                              // dependsOn 两者
        lock(Resource.INTERACT)
        val obsidian = InvUtils.findInHotbar(Items.OBSIDIAN)
        if (!obsidian.found() || !BlockUtils.canPlaceAt(pos)) return@node
        place(pos, obsidian)
        await(delay(placeDelay.value))                // 放置后的冷却，即原来的 timer
    }
}
```

模块的 `onTick` 只负责选目标，原先的 `timer` / `waitingSwapBack` / `swapBackTicks` / `savedOldSlot` 四个
跨 tick 状态位全部消失：冷却由任务尾部的 `await(delay(...))` 加上 `launch` 的同名去重表达，换回槽位由
`finally` 保证。Visible 模式的 `Swap Back Delay` 直接写成 `await(delay(n))`，并改用 `InvUtils.swapBack()`
而不是直接写 `setSelectedSlot`，顺带修掉了原实现留下的 `previousSlot` 悬挂。

与迁移前的两处有意差异：

- 目标 `Rot2f` 每个任务算一次，而不是每 tick 重算。落点是固定方块、容差 15 度、默认速度 180 度/tick，
  一个 tick 内即可到位。
- 放置完成后释放旋转锁，不再在冷却期间继续压着旋转。Silent 模式不移动可见视角，没有用户可见差异。

模块 id、名称与全部 Setting 显示名保持不变，配置与 i18n 无需迁移。
