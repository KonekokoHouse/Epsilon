package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

public class FeetTrap extends Module {

    public static final FeetTrap INSTANCE = new FeetTrap();

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST
    };

    private FeetTrap() {
        super("Feet Trap", Category.COMBAT);
    }

    private enum SwitchMode {
        Visible,
        Silent
    }

    private enum RotateMode {
        None,
        Silent
    }

    private final EnumSetting<SwitchMode> switchMode = enumSetting("Switch", SwitchMode.Visible);
    private final EnumSetting<RotateMode> rotate = enumSetting("Rotate", RotateMode.Silent);
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 10, 1, 10, 1, () -> rotate.is(RotateMode.Silent));
    private final BoolSetting sideCheck = boolSetting("Side Check", false, () -> rotate.is(RotateMode.Silent));
    private final IntSetting blocksPerTick = intSetting("Blocks Per Tick", 2, 1, 8, 1);
    private final IntSetting delay = intSetting("Delay", 0, 0, 20, 1);
    private final BoolSetting toggleWhenDone = boolSetting("Toggle When Done", false);

    private int timer;

    @Override
    protected void onEnable() {
        timer = 0;
    }

    @Override
    protected void onDisable() {
        InvUtils.swapBack();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        if (timer > 0) {
            timer--;
            return;
        }

        Set<BlockPos> targets = getTargets();
        if (targets.isEmpty()) {
            if (toggleWhenDone.getValue()) setEnabled(false);
            return;
        }

        FindItemResult obsidian = switchMode.is(SwitchMode.Silent) ? InvUtils.find(Items.OBSIDIAN) : InvUtils.findInHotbar(Items.OBSIDIAN);
        if (!obsidian.found()) return;

        int placed = 0;
        for (BlockPos target : targets) {
            PlaceInfo placeInfo = getPlaceInfo(target);
            if (placeInfo == null) continue;

            if (rotate.is(RotateMode.Silent)) {
                Rot2f rotation = RotationUtils.calculate(placeInfo.neighbor(), placeInfo.side());
                RotationManager.INSTANCE.setRotations(rotation, rotationSpeed.getValue());
                if (!RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), placeInfo.side(), placeInfo.neighbor(), sideCheck.getValue())) {
                    break;
                }
            }

            if (placeBlock(placeInfo, obsidian)) {
                placed++;
                if (placed >= blocksPerTick.getValue()) break;
            }
        }

        if (placed > 0) {
            timer = delay.getValue();
        }
    }

    private Set<BlockPos> getTargets() {
        Set<BlockPos> feetPositions = new LinkedHashSet<>();
        AABB box = mc.player.getBoundingBox().deflate(0.001);
        int y = BlockPos.containing(mc.player.position()).getY();
        int minX = BlockPos.containing(box.minX, mc.player.getY(), box.minZ).getX();
        int maxX = BlockPos.containing(box.maxX, mc.player.getY(), box.maxZ).getX();
        int minZ = BlockPos.containing(box.minX, mc.player.getY(), box.minZ).getZ();
        int maxZ = BlockPos.containing(box.maxX, mc.player.getY(), box.maxZ).getZ();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                feetPositions.add(new BlockPos(x, y, z));
            }
        }

        Set<BlockPos> targets = new LinkedHashSet<>();
        for (BlockPos feetPos : feetPositions) {
            for (Direction direction : HORIZONTAL_DIRECTIONS) {
                BlockPos target = feetPos.relative(direction);
                if (!isInsideFeet(target, feetPositions) && BlockUtils.canPlaceAt(target)) {
                    targets.add(target);
                }
            }
        }
        return targets;
    }

    private boolean isInsideFeet(BlockPos pos, Set<BlockPos> feetPositions) {
        for (BlockPos feetPos : feetPositions) {
            if (pos.getX() == feetPos.getX() && pos.getZ() == feetPos.getZ()) return true;
        }
        return false;
    }

    private PlaceInfo getPlaceInfo(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (!mc.level.getBlockState(neighbor).canBeReplaced()) {
                return new PlaceInfo(neighbor, direction.getOpposite());
            }
        }
        return null;
    }

    private boolean placeBlock(PlaceInfo placeInfo, FindItemResult item) {
        Vec3 hitVec = Vec3.atCenterOf(placeInfo.neighbor()).add(
                placeInfo.side().getStepX() * 0.5,
                placeInfo.side().getStepY() * 0.5,
                placeInfo.side().getStepZ() * 0.5
        );
        BlockHitResult hitResult = new BlockHitResult(hitVec, placeInfo.side(), placeInfo.neighbor(), false);
        int oldSlot = mc.player.getInventory().getSelectedSlot();

        if (switchMode.is(SwitchMode.Visible)) {
            if (oldSlot != item.slot()) {
                InvUtils.swap(item.slot(), true);
            }
        } else {
            InvUtils.invSwap(item.slot());
        }

        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hitResult);
        if (result.consumesAction()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        if (switchMode.is(SwitchMode.Visible)) {
            if (oldSlot != item.slot()) {
                InvUtils.swapBack();
            }
        } else {
            InvUtils.invSwapBack();
        }

        return result.consumesAction();
    }

    private record PlaceInfo(BlockPos neighbor, Direction side) {
    }
}
