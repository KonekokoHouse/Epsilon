package com.github.epsilon.modules.impl.combat

import com.github.epsilon.events.bus.EventHandler
import com.github.epsilon.events.impl.PlayerTickEvent
import com.github.epsilon.modules.Category
import com.github.epsilon.modules.Module
import com.github.epsilon.schedule.Resource
import com.github.epsilon.schedule.Scheduler
import com.github.epsilon.schedule.TaskScope
import com.github.epsilon.schedule.aim
import com.github.epsilon.schedule.delay
import com.github.epsilon.settings.Setting
import com.github.epsilon.utils.player.FindItemResult
import com.github.epsilon.utils.player.InvUtils
import com.github.epsilon.utils.rotation.Priority
import com.github.epsilon.utils.rotation.RotationUtils
import com.github.epsilon.utils.world.BlockUtils
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs

/**
 * 在玩家与末影水晶之间垫一块黑曜石。
 *
 * 也是 [Scheduler] 的示范模块：原先的 `timer` / `waitingSwapBack` / `swapBackTicks` /
 * `savedOldSlot` 四个跨 tick 状态位全部由任务体的串行写法取代，[onTick] 只负责选目标。
 */
object CrystalBlocker : Module("Crystal Blocker", Category.COMBAT) {

    private const val TASK = "crystal-blocker"

    private val range = doubleSetting("Range", 4.0, 1.0, 6.0, 0.1)
    private val rotate = enumSetting("Rotate", RotateMode.Silent)
    private val switchMode = enumSetting("Switch", SwitchMode.Visible)
    private val placeDelay = intSetting("Delay", 2, 0, 20, 1)
    private val visibleSwapBackDelay = intSetting(
        "Swap Back Delay", 0, 0, 20, 1,
        Setting.Dependency { switchMode.value == SwitchMode.Visible },
    )
    private val rotationSpeed = intSetting(
        "Rotation Speed", 180, 10, 180, 10,
        Setting.Dependency { rotate.value == RotateMode.Silent },
    )

    enum class RotateMode { None, Normal, Silent }

    enum class SwitchMode { Visible, Silent }

    override fun rotationPriority(): Priority = Priority.Highest

    /** 关模块时取消任务：挂起点抛出取消信号，任务体的 `finally` 负责把手上的槽位换回来。 */
    override fun onDisable() = Scheduler.cancel(TASK)

    @Suppress("UNUSED_PARAMETER")
    @EventHandler
    private fun onTick(event: PlayerTickEvent.Pre) {
        val player = mc.player ?: return
        val level = mc.level ?: return
        if (!player.onGround()) return
        // 任务存活期间（含放置后的冷却）无须再选目标，launch 本身也会按名字去重。
        if (Scheduler.isRunning(TASK)) return

        val crystal = level.getEntitiesOfClass(
            EndCrystal::class.java,
            player.boundingBox.inflate(range.value),
        ) { crystal -> abs(crystal.y - player.y) < 1.0 }
            .minByOrNull { player.distanceTo(it) } ?: return
        if (blocked(player, level, crystal)) return

        val pos = placement(player, level, crystal) ?: return
        if (!InvUtils.findInHotbar(Items.OBSIDIAN).found()) return

        Scheduler.launch(TASK, Priority.Highest) { block(pos) }
    }

    /**
     * 转头与抢热键栏互不依赖，两个节点天然并行；放置节点 `dependsOn` 二者。放置后任务再多活
     * Delay 个 tick 充当冷却——这就是原来的 `timer`。
     */
    private suspend fun TaskScope.block(pos: BlockPos) {
        val aimed = node { face(pos) }
        // 只抢锁不做事：锁跟着任务活，转头节点还在转的时候热键栏就已经归本任务了。
        val armed = node { lock(Resource.HOTBAR) }
        node(aimed, armed) {
            lock(Resource.INTERACT)
            // 等待期间世界会变，落点与背包都要重新确认。
            val obsidian = InvUtils.findInHotbar(Items.OBSIDIAN)
            if (!obsidian.found() || !BlockUtils.canPlaceAt(pos)) return@node
            place(pos, obsidian)
            await(delay(placeDelay.value))
        }
    }

    /** Silent 模式下持续续压旋转直到进入 15 度，与迁移前逐 tick 调用 `setRotations` 等价。 */
    private suspend fun TaskScope.face(pos: BlockPos) {
        val player = mc.player ?: return
        val target = RotationUtils.calculate(player.eyePosition, Vec3.atCenterOf(pos))
        when (rotate.value) {
            RotateMode.Normal -> {
                player.yRot = target.yaw
                player.xRot = Mth.clamp(target.pitch, -90f, 90f)
            }

            RotateMode.Silent -> {
                lock(Resource.ROTATION)
                await(aim(target, rotationSpeed.value.toDouble(), 15f))
            }

            else -> {}
        }
    }

    /**
     * 换手、放置、换回。Visible 模式的 `Swap Back Delay` 直接写成 `await(delay(n))`。
     *
     * `finally` 里的回滚只能是同步操作：任务被抢占时挂起点会立刻抛出取消信号，在 `finally` 里再
     * `await` 会二次抛出，回滚就做不完了。
     */
    private suspend fun TaskScope.place(pos: BlockPos, obsidian: FindItemResult) {
        val hit = BlockHitResult(Vec3.atCenterOf(pos), RotationUtils.getDirection(pos), pos, false)
        if (switchMode.value == SwitchMode.Silent) {
            InvUtils.invSwap(obsidian.slot())
            try {
                use(hit)
            } finally {
                InvUtils.invSwapBack()
            }
            return
        }
        if (mc.player?.inventory?.selectedSlot == obsidian.slot()) {
            use(hit)
            return
        }
        InvUtils.swap(obsidian.slot(), true)
        var restore = true
        try {
            use(hit)
            await(delay(visibleSwapBackDelay.value))
            restore = false
            InvUtils.swapBack()
        } finally {
            if (restore) InvUtils.swapBack()
        }
    }

    private fun use(hit: BlockHitResult) {
        val player = mc.player ?: return
        val gameMode = mc.gameMode ?: return
        gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)
        player.swing(InteractionHand.MAIN_HAND)
    }

    /** 视线已被黑曜石挡住就不用再垫。 */
    private fun blocked(player: LocalPlayer, level: ClientLevel, crystal: EndCrystal): Boolean {
        val hit = level.clip(
            ClipContext(
                player.eyePosition,
                crystal.position().add(0.0, 0.5, 0.0),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player,
            )
        )
        return hit.type == HitResult.Type.BLOCK &&
            level.getBlockState(hit.blockPos).`is`(Blocks.OBSIDIAN)
    }

    /** 取玩家与水晶连线的中点；中点落在两者自己的方块上时退一格到玩家紧邻处。 */
    private fun placement(player: LocalPlayer, level: ClientLevel, crystal: EndCrystal): BlockPos? {
        val eyes = player.position()
        val crystalPos = crystal.position()
        val middle = eyes.lerp(crystalPos, 0.5)
        var pos = BlockPos.containing(middle.x, player.y, middle.z)
        if (pos == player.blockPosition() || pos == crystal.blockPosition()) {
            val direction = crystalPos.subtract(eyes).normalize()
            pos = BlockPos.containing(eyes.x + direction.x, player.y, eyes.z + direction.z)
        }
        if (!BlockUtils.canPlaceAt(pos)) return null
        // isSolid 已废弃但保留：换成 isFaceSturdy 会改判部分能站的方块，与迁移前行为不一致。
        @Suppress("DEPRECATION")
        val supported = level.getBlockState(pos.below()).isSolid
        return if (supported) pos else null
    }
}
