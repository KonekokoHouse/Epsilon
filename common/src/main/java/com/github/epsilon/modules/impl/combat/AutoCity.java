package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.PlayerUtils;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public class AutoCity extends Module {
    public static final AutoCity INSTANCE = new AutoCity();

    private AutoCity() {
        super("AutoCity", Category.COMBAT);
    }

    private final BoolSetting burrow = boolSetting("burrow", true);
    private final BoolSetting surround = boolSetting("Surround", true);
    private final BoolSetting FeetDown = boolSetting("FeetDown", true);
    public final DoubleSetting targetRange = doubleSetting("TargetRange", 6.0, 0.0, 8.0, 0.1);
    private final IntSetting delay = intSetting("Delay", 500, 0, 5000, 100);
    private final BoolSetting swing = boolSetting("Swing", true);
    private final IntSetting mineDelay = intSetting("MineDelay", 100, 0, 500, 50);

    private final TimerUtils collectTimer = new TimerUtils();
    private final TimerUtils mineTimer = new TimerUtils();
    private final List<BlockPos> mineQueue = new ArrayList<>();
    private BlockPos currentMinePos = null;
    private boolean mining = false;

    @EventHandler
    public void onTick(PlayerTickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;

        if (mining && mineTimer.passedMillise(mineDelay.getValue())) {
            stopCurrentMine();
        }

        if (!mining && !mineQueue.isEmpty()) {
            startMine(mineQueue.remove(0));
        }

        if (!collectTimer.passedMillise(delay.getValue())) return;
        if (mining) return;

        List<LivingEntity> targets = Managers.TARGET.acquireTargets(
                TargetRequest.of(targetRange.getValue(), 360.0f, true, false, false, false, false, 1)
        );
        if (targets.isEmpty()) return;

        collectBlocks((Player) targets.getFirst());

        if (!mineQueue.isEmpty()) {
            startMine(mineQueue.remove(0));
        }

        collectTimer.reset();
    }

    private void collectBlocks(Player targetPlayer) {
        BlockPos feetPos = targetPlayer.blockPosition();
        mineQueue.clear();

        if (burrow.getValue()) {
            collectBurrowBlocks(targetPlayer);
        }

        if (surround.getValue()) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos adjacent = feetPos.relative(dir);
                if (PlayerUtils.isValidBlock(adjacent) && !isInQueueOrMining(adjacent)) {
                    mineQueue.add(adjacent);
                }
            }
        }

        if (FeetDown.getValue()) {
            BlockPos below = feetPos.below();
            if (PlayerUtils.isValidBlock(below) && !isInQueueOrMining(below)) {
                mineQueue.add(below);
            }
        }
    }

    private void startMine(BlockPos pos) {
        Direction dir = RotationUtils.getDirection(pos);

        mc.gameMode.startDestroyBlock(pos, dir);

        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        currentMinePos = pos;
        mining = true;
        mineTimer.reset();
    }

    private void stopCurrentMine() {
        if (currentMinePos == null) return;

        mc.gameMode.stopDestroyBlock();

        if (swing.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        currentMinePos = null;
        mining = false;
    }

    private boolean isInQueueOrMining(BlockPos pos) {
        return pos.equals(currentMinePos) || mineQueue.contains(pos);
    }

    private void collectBurrowBlocks(Player targetPlayer) {
        double[] yOffset = new double[]{-0.8, 0.5, 1.1};
        double[] xzOffset = new double[]{0.3, -0.3, 0.0};

        for (double y : yOffset) {
            for (double x : xzOffset) {
                for (double z : xzOffset) {
                    BlockPos offsetPos = BlockPos.containing(
                            targetPlayer.getX() + x,
                            targetPlayer.getY() + y,
                            targetPlayer.getZ() + z
                    );
                    if (isInQueueOrMining(offsetPos)) {
                        return;
                    }
                }
            }
        }

        yOffset = new double[]{0.5, 1.1};
        for (double y : yOffset) {
            for (double offset : xzOffset) {
                BlockPos offsetPos = BlockPos.containing(
                        targetPlayer.getX() + offset,
                        targetPlayer.getY() + y,
                        targetPlayer.getZ() + offset
                );
                if (PlayerUtils.isValidBlock(offsetPos) && !isInQueueOrMining(offsetPos)) {
                    mineQueue.add(offsetPos);
                }
            }
        }
        for (double y : yOffset) {
            for (double offset : xzOffset) {
                for (double offset2 : xzOffset) {
                    BlockPos offsetPos = BlockPos.containing(
                            targetPlayer.getX() + offset2,
                            targetPlayer.getY() + y,
                            targetPlayer.getZ() + offset
                    );
                    if (PlayerUtils.isValidBlock(offsetPos) && !isInQueueOrMining(offsetPos)) {
                        mineQueue.add(offsetPos);
                    }
                }
            }
        }
    }
}
