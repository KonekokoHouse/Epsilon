package com.github.epsilon.schedule

import com.github.epsilon.managers.Managers
import com.github.epsilon.utils.rotation.Rot2f
import net.minecraft.util.Mth
import kotlin.math.abs

/**
 * [TaskScope.await] 的条件。每 tick 由调度器轮询一次，允许带副作用——[aim] 就靠这点在等待期间
 * 持续续压旋转。
 */
fun interface Awaitable {

    fun poll(): Boolean
}

/**
 * 等待 [ticks] 个 tick。截止点在构造时算定，故只应就地写成 `await(delay(3))`；提前存进变量再
 * await 会从构造那一刻起算。`delay(0)` 不消耗 tick。
 */
fun delay(ticks: Int): Awaitable {
    val deadline = Scheduler.ticks + ticks
    return Awaitable { Scheduler.ticks >= deadline }
}

/**
 * 以本任务的优先级持续朝 [target] 转头，直到偏差进入 [tolerance] 度。每次轮询都重新调用
 * `setRotations`：既让 `RotationManager` 保持 active，也和模块每 tick 自行下发时的行为一致。
 * 切换旋转模式会整体替换管理器实例，所以每次都要重新取 `Managers.ROTATION`。
 */
fun TaskScope.aim(target: Rot2f, speed: Double, tolerance: Float = 1f): Awaitable {
    val priority = priority
    return Awaitable {
        val rotation = Managers.ROTATION
        rotation.setRotations(target, speed, priority)
        rotation.rotations == null ||
            (abs(Mth.wrapDegrees(rotation.yaw - target.yaw)) <= tolerance &&
                abs(rotation.pitch - target.pitch) <= tolerance)
    }
}
