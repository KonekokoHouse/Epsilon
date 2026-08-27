package com.github.epsilon.schedule

import com.github.epsilon.Constants
import com.github.epsilon.events.bus.EventBus
import com.github.epsilon.events.bus.EventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.utils.rotation.Priority
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 可被任务独占的资源。[TaskScope.lock] 照抄 `synchronized`：同一任务内可重入，锁在任务存活期间
 * 一直持有，任务跑完或被取消时自动释放，因此没有 `unlock`。
 */
enum class Resource { ROTATION, HOTBAR, INTERACT }

/** 任务取消时在挂起点抛出的信号。无栈单例，只为让任务体的 `finally` 完成状态回滚。 */
object Cancelled : RuntimeException("epsilon scheduler task cancelled", null, false, false)

/** [TaskScope.node] 的返回句柄，照抄 Gradle 的 `dependsOn`。 */
interface Node {

    /** 节点是否已跑完。失败与取消会直接终结整个任务，故依赖方无需区分成功与否。 */
    val done: Boolean
}

/** 任务体的接收者。挂起全部由调度器在游戏线程上按 tick 恢复，所以任务体可以写成串行代码。 */
interface TaskScope {

    /** 所属任务的旋转优先级；[aim] 一类 awaitable 用它调用 `RotationManager.setRotations`。 */
    val priority: Priority

    /** 派生子节点：[dependsOn] 全部跑完后才启动，互不依赖的节点天然并行。 */
    fun node(vararg dependsOn: Node, body: suspend TaskScope.() -> Unit): Node

    /** 挂起直到 [awaitable] 成立；首次轮询即成立时不消耗 tick。 */
    suspend fun await(awaitable: Awaitable)

    /** 挂起直到取得 [resource]；优先级严格更高的任务立即抢占并取消原持有者。 */
    suspend fun lock(resource: Resource)
}

/**
 * 服务于 `RotationManager` 的 tick 级协程调度器。
 *
 * 在 [PlayerTickEvent.Pre] 上以 -900 推进：模块（0）已算完本 tick 的目标，`RotationManager`
 * 的平滑（-1000）尚未执行，因此 awaitable 读到的旋转与模块直接调用 `setRotations` 时完全一致。
 */
object Scheduler {

    /** 调度器自身的 tick 计数，[delay] 以它为基准。 */
    val ticks: Long get() = clock

    /** 由 `EpsilonCommon.init` 调用一次。 */
    @JvmStatic
    fun init() = EventBus.INSTANCE.subscribe(this)

    /**
     * 启动任务 [name]。同名任务仍在运行时直接返回，模块可以每 tick 无条件调用。任务体会同步
     * 执行到第一个挂起点，`setRotations` 因此仍落在本 tick 的平滑之前。
     */
    fun launch(name: String, priority: Priority = Priority.Medium, body: suspend TaskScope.() -> Unit) {
        if (name in tasks) return
        val task = Task(name, priority)
        tasks[name] = task
        task.fork(NO_DEPS, body)
        drive(task)
        if (!busy) flush()
    }

    /** 取消任务 [name]：其挂起点抛出 [Cancelled]，任务体的 `finally` 得以回滚状态。 */
    @JvmStatic
    fun cancel(name: String) {
        doom(tasks[name] ?: return)
        if (!busy) flush()
    }

    @JvmStatic
    fun isRunning(name: String) = name in tasks

    @Suppress("UNUSED_PARAMETER")
    @EventHandler(priority = -900)
    private fun onPlayerTick(event: PlayerTickEvent.Pre) {
        clock++
        if (tasks.isEmpty()) return
        busy = true
        try {
            // 快照遍历：任务体可以在本轮里 launch/cancel，不能让它改到正在遍历的容器。
            for (task in tasks.values.toTypedArray()) if (!task.cancelled) drive(task)
            flush()
        } finally {
            busy = false
        }
    }
}

private val tasks = LinkedHashMap<String, Task>()
private val owners = arrayOfNulls<Task>(Resource.entries.size)
private val doomed = ArrayList<Task>(2)
private val NO_DEPS = emptyArray<Node>()
private var clock = 0L
private var busy = false

/** 推进一个任务；按索引遍历，[TaskScope.node] 在本轮追加的子节点会立即参与调度。 */
private fun drive(task: Task) {
    val fibers = task.fibers
    var index = 0
    var settled = true
    while (index < fibers.size) {
        val fiber = fibers[index++]
        fiber.step()
        if (task.cancelled) return
        settled = settled && fiber.done
    }
    if (settled) retire(task)
}

/** 取消一律推迟到这里执行，受害者的 `finally` 便不会在抢占者的调用栈里重入。 */
private fun flush() {
    var index = 0
    // unwind 里的 finally 可能再判死别的任务，索引遍历顺带把它们也排干。
    while (index < doomed.size) {
        val task = doomed[index++]
        task.unwind()
        retire(task)
    }
    doomed.clear()
}

private fun doom(task: Task) {
    if (task.cancelled) return
    task.cancelled = true
    doomed += task
}

private fun retire(task: Task) {
    for (slot in owners.indices) if (owners[slot] === task) owners[slot] = null
    // finally 里重新 launch 的同名任务不能被这次退场顺手删掉。
    tasks.remove(task.name, task)
}

/** 抢占后槽位已易主，旧持有者 [retire] 时的 `===` 判断自然不会把锁抢回去。 */
private fun acquire(task: Task, resource: Resource): Boolean {
    val slot = resource.ordinal
    val owner = owners[slot]
    if (owner === task) return true
    if (owner != null) {
        if (task.priority.priority <= owner.priority.priority) return false
        doom(owner)
    }
    owners[slot] = task
    return true
}

/** 一个"过程"：持有名字、优先级与全部锁，内部由若干 fiber 组成 DAG。 */
private class Task(val name: String, val priority: Priority) {

    val fibers = ArrayList<Fiber>(4)
    var cancelled = false

    fun fork(dependsOn: Array<out Node>, body: suspend TaskScope.() -> Unit): Node =
        Fiber(this, dependsOn, body).also { fibers += it }

    fun unwind() {
        var index = 0
        while (index < fibers.size) fibers[index++].abort()
    }
}

/**
 * 手动驱动的协程：`createCoroutineUnintercepted` 建体、`COROUTINE_SUSPENDED` 挂起、调度器按
 * tick 恢复。只依赖 kotlin-stdlib，任务体始终跑在游戏线程上，无需看护线程。
 */
private class Fiber(
    private val task: Task,
    private val dependsOn: Array<out Node>,
    body: suspend TaskScope.() -> Unit,
) : Node, TaskScope, Continuation<Unit> {

    override val context: CoroutineContext get() = EmptyCoroutineContext
    override val priority: Priority get() = task.priority
    override var done = false
        private set

    private var start: Continuation<Unit>? = body.createCoroutineUnintercepted(this, this)
    private var resume: Continuation<Unit>? = null
    private var awaiting: Awaitable? = null

    fun step() {
        val begin = start
        if (begin != null) {
            for (node in dependsOn) if (!node.done) return
            start = null
            begin.resume(Unit)
            return
        }
        val awaitable = awaiting ?: return
        if (!awaitable.poll()) return
        val next = resume ?: return
        // 必须先清空再恢复：任务体紧接着可能在同一栈里重新挂起。
        awaiting = null
        resume = null
        next.resume(Unit)
    }

    fun abort() {
        start = null
        awaiting = null
        val pending = resume
        resume = null
        if (pending == null) done = true else pending.resumeWithException(Cancelled)
    }

    override fun node(vararg dependsOn: Node, body: suspend TaskScope.() -> Unit): Node =
        task.fork(dependsOn, body)

    override suspend fun await(awaitable: Awaitable): Unit =
        suspendCoroutineUninterceptedOrReturn { continuation ->
            if (task.cancelled) throw Cancelled
            if (awaitable.poll()) Unit
            else {
                awaiting = awaitable
                resume = continuation
                COROUTINE_SUSPENDED
            }
        }

    override suspend fun lock(resource: Resource) = await { acquire(task, resource) }

    override fun resumeWith(result: Result<Unit>) {
        done = true
        awaiting = null
        resume = null
        val error = result.exceptionOrNull() ?: return
        if (error !== Cancelled) Constants.LOGGER.error("Epsilon 调度任务 {} 异常终止", task.name, error)
        doom(task)
    }
}
