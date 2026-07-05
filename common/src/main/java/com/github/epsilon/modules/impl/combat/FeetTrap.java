package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.MoveEvent;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.*;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class FeetTrap extends Module {

    public static final FeetTrap INSTANCE = new FeetTrap();

    private FeetTrap() {
        super("Feet Trap", Category.COMBAT);
    }

    private final SettingGroup sgGeneral = settingGroup("General");
    private final SettingGroup sgRotate = settingGroup("Rotate");
    private final SettingGroup sgCheck = settingGroup("Check");

    public final IntSetting placeDelay = intSetting("Place Delay", 50, 0, 500, 1).group(sgGeneral);
    public final BoolSetting extend = boolSetting("Extend", true).group(sgGeneral);
    public final BoolSetting onlySelf = boolSetting("Only Self", false, extend::getValue).group(sgGeneral);
    private final IntSetting blocksPer = intSetting("Blocks Per Tick", 1, 1, 8, 1).group(sgGeneral);
    private final BoolSetting breakCrystal = boolSetting("Break", true).group(sgGeneral);
    private final BoolSetting pauseOnEat = boolSetting("Pause On Eat", true, breakCrystal::getValue).group(sgGeneral);
    private final BoolSetting center = boolSetting("Center", true).group(sgGeneral);
    private final BoolSetting inventory = boolSetting("Inventory Swap", true).group(sgGeneral);
    private final BoolSetting enderChest = boolSetting("Ender Chest", true).group(sgGeneral);

    private final BoolSetting rotate = boolSetting("Rotate", true).group(sgRotate);

    public final BoolSetting inAir = boolSetting("In Air", true).group(sgCheck);
    private final BoolSetting moveDisable = boolSetting("Move Disable", true).group(sgCheck);
    private final BoolSetting jumpDisable = boolSetting("Jump Disable", true).group(sgCheck);

    public Vec3 directionVec = null;
    private double startX = 0;
    private double startY = 0;
    private double startZ = 0;
    private int progress = 0;
    private boolean shouldCenter = true;

    private final TimerUtils timer = new TimerUtils();

    public boolean selfIntersectPos(BlockPos pos) {
        return mc.player.getBoundingBox().intersects(new AABB(pos));
    }

    public boolean otherIntersectPos(BlockPos pos) {
        for (AbstractClientPlayer player : mc.level.players()) {
            if (player.getBoundingBox().intersects(new AABB(pos))) {
                return true;
            }
        }
        return false;
    }

    public Rot2f getRotationTo(Vec3 posFrom, Vec3 posTo) {
        Vec3 vec3d = posTo.subtract(posFrom);
        return getRotationFromVec(vec3d);
    }

    private Rot2f getRotationFromVec(Vec3 vec) {
        double d = vec.x;
        double d2 = vec.z;
        double xz = Math.hypot(d, d2);
        d2 = vec.z;
        double d3 = vec.x;
        double yaw = normalizeAngle(Math.toDegrees(Math.atan2(d2, d3)) - 90.0);
        double pitch = normalizeAngle(Math.toDegrees(-Math.atan2(vec.y, xz)));
        return new Rot2f((float) yaw, (float) pitch);
    }

    private static double normalizeAngle(double angleIn) {
        double angle = angleIn;
        if ((angle %= 360.0) >= 180.0) {
            angle -= 360.0;
        }
        if (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }

    @EventHandler
    private void onPlayerTick(PlayerTickEvent.Pre event) {
        if (directionVec != null && rotate.getValue()) {
            Managers.ROTATION.setRotations(RotationUtils.calculate(directionVec), 180f);
        }
    }

    @EventHandler
    private void onClientTick(ClientTickEvent.Pre event) {
        if (nullCheck()) return;
        if (inventory.getValue() && mc.screen != null) return;
        if (!timer.passedMillise(placeDelay.getValue().longValue())) return;

        progress = 0;

        if (!MoveUtils.isMoving() && !mc.options.keyJump.isDown()) {
            startX = mc.player.getX();
            startY = mc.player.getY();
            startZ = mc.player.getZ();
        }

        FindItemResult result = findBlocks();
        if (!result.found()) {
            ChatUtils.addChatMessage("[FeetTrap] No block found");
            toggle();
            return;
        }

        double distanceToStart = Mth.sqrt((float) mc.player.distanceToSqr(startX, startY, startZ));
        if ((moveDisable.getValue() && distanceToStart > 1.0 || jumpDisable.getValue() && mc.player.input.keyPresses.jump())) {
            toggle();
            return;
        }

        if (pauseOnEat.getValue() && PlayerUtils.isEating()) {
            return;
        }

        if (!inAir.getValue() && !mc.player.onGround()) return;
        doSurround(BlockPos.containing(mc.player.getX(), mc.player.getY(), mc.player.getZ()), result.slot());
        doSurround(BlockPos.containing(mc.player.getX(), mc.player.getY() + 0.8, mc.player.getZ()), result.slot());
    }

    public void doSurround(BlockPos pos, int slot) {
        for (Direction i : Direction.values()) {
            if (i == Direction.UP) continue;
            BlockPos offsetPos = pos.relative(i);
            if (getPlaceSide(offsetPos) != null) {
                tryPlaceBlock(offsetPos, slot);
            } else if (mc.level.getBlockState(offsetPos).canBeReplaced()) {
                tryPlaceBlock(getHelperPos(offsetPos), slot);
            }
            if ((selfIntersectPos(offsetPos) || !onlySelf.getValue() && otherIntersectPos(offsetPos)) && extend.getValue()) {
                for (Direction i2 : Direction.values()) {
                    if (i2 == Direction.UP) continue;
                    BlockPos offsetPos2 = offsetPos.relative(i2);
                    if (selfIntersectPos(offsetPos2) || !onlySelf.getValue() && otherIntersectPos(offsetPos2)) {
                        for (Direction i3 : Direction.values()) {
                            if (i3 == Direction.UP) continue;
                            tryPlaceBlock(offsetPos2, slot);
                            BlockPos offsetPos3 = offsetPos2.relative(i3);
                            tryPlaceBlock(getPlaceSide(offsetPos3) != null || !mc.level.getBlockState(offsetPos3).canBeReplaced() ? offsetPos3 : getHelperPos(offsetPos3), slot);
                        }
                    }
                    tryPlaceBlock(getPlaceSide(offsetPos2) != null || !mc.level.getBlockState(offsetPos2).canBeReplaced() ? offsetPos2 : getHelperPos(offsetPos2), slot);
                }
            }
        }
    }

    public Direction getPlaceSide(BlockPos pos) {
        double minDistance = Double.MAX_VALUE;
        Direction side = null;
        for (Direction i : Direction.values()) {
            if (!canClick(pos.relative(i))) continue;
            if (mc.level.getBlockState(pos.relative(i)).canBeReplaced()) continue;
            if (!RotationUtils.canSee(pos.relative(i), i.getOpposite())) continue;
            double vecDis = mc.player.getEyePosition().distanceToSqr(pos.getCenter().add(i.getUnitVec3i().getX() * 0.5, i.getUnitVec3i().getY() * 0.5, i.getUnitVec3i().getZ() * 0.5));
            if (vecDis > minDistance) {
                continue;
            }
            side = i;
            minDistance = vecDis;
        }
        return side;
    }

    public boolean canClick(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        Block block = state.getBlock();
        return mc.player.isCrouching() || !isClickable(block);
    }

    public boolean isClickable(Block block) {
        return block instanceof CraftingTableBlock
                || block instanceof AnvilBlock
                || block instanceof LoomBlock
                || block instanceof CartographyTableBlock
                || block instanceof GrindstoneBlock
                || block instanceof StonecutterBlock
                || block instanceof ButtonBlock
                || block instanceof BasePressurePlateBlock
                || block instanceof BedBlock
                || block instanceof FenceGateBlock
                || block instanceof DoorBlock
                || block instanceof NoteBlock
                || block instanceof TrapDoorBlock;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            if (moveDisable.getValue() || jumpDisable.getValue()) {
                toggle();
            }
            return;
        }
        startX = mc.player.getX();
        startY = mc.player.getY();
        startZ = mc.player.getZ();
        shouldCenter = true;
    }

    @EventHandler
    public void onMove(MoveEvent event) {
        if (!center.getValue() || mc.player.isFallFlying()) {
            return;
        }

        BlockPos blockPos = BlockPos.containing(mc.player.position());
        if (mc.player.getX() - blockPos.getX() - 0.5 <= 0.2 && mc.player.getX() - blockPos.getX() - 0.5 >= -0.2 && mc.player.getZ() - blockPos.getZ() - 0.5 <= 0.2 && mc.player.getZ() - 0.5 - blockPos.getZ() >= -0.2) {
            if (shouldCenter && (mc.player.onGround() || MoveUtils.isMoving())) {
                event.setX(0);
                event.setZ(0);
                shouldCenter = false;
            }
        } else {
            if (shouldCenter) {
                Vec3 centerPos = blockPos.getCenter();
                float rotation = getRotationTo(mc.player.position(), centerPos).getYaw();
                float yawRad = rotation / 180.0f * 3.1415927f;
                double dist = mc.player.position().distanceTo(new Vec3(centerPos.x, mc.player.getY(), centerPos.z));
                double cappedSpeed = Math.min(0.2873, dist);
                double x = -(float) Math.sin(yawRad) * cappedSpeed;
                double z = (float) Math.cos(yawRad) * cappedSpeed;
                event.setX(x);
                event.setZ(z);
            }
        }
    }

    private void tryPlaceBlock(BlockPos pos, int slot) {
        if (pos == null) return;
        if (!(progress < blocksPer.getValue())) return;
        Direction side = getPlaceSide(pos);
        if (side == null) return;
        Vec3 directionVec = new Vec3(pos.getX() + 0.5 + side.getUnitVec3i().getX() * 0.5, pos.getY() + 0.5 + side.getUnitVec3i().getY() * 0.5, pos.getZ() + 0.5 + side.getUnitVec3i().getZ() * 0.5);
        if (!BlockUtils.canPlaceAt(pos, false)) return;
        if (rotate.getValue()) {
            this.directionVec = directionVec;
            if (RaytraceUtils.overBlock(Managers.ROTATION.getRotation(), pos.relative(side))) {
                // 在这个 tick 转头已经到达，无需继续设置转头
                this.directionVec = null;
            } else {
                return;
            }
        }
        if (breakCrystal.getValue()) {
            attackCrystal(pos, rotate.getValue(), pauseOnEat.getValue());
        } else if (BlockUtils.noEntity(pos, false)) {
            return;
        }

        if (inventory.getValue()) InvUtils.invSwap(slot);
        else InvUtils.swap(slot, true);

        clickBlock(pos.relative(side), side.getOpposite(), InteractionHand.MAIN_HAND);

        timer.reset();

        if (inventory.getValue()) InvUtils.invSwapBack();
        else InvUtils.swapBack();

        progress++;
    }

    public void clickBlock(BlockPos pos, Direction side, InteractionHand hand) {
        Vec3 directionVec = new Vec3(pos.getX() + 0.5 + side.getUnitVec3i().getX() * 0.5, pos.getY() + 0.5 + side.getUnitVec3i().getY() * 0.5, pos.getZ() + 0.5 + side.getUnitVec3i().getZ() * 0.5);
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        mc.gameMode.useItemOn(mc.player, hand, result);
    }

    public void attackCrystal(BlockPos blockPos, boolean rotate, boolean eatingPause) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof EndCrystal crystal) {
                if (crystal.getBoundingBox().intersects(new AABB(blockPos))) {
                    if (eatingPause && PlayerUtils.isEating()) return;
                    Managers.ROTATION.setRotations(RotationUtils.calculate(crystal), 360f, Priority.Highest);
                    mc.gameMode.attack(mc.player, crystal);
                    mc.player.swing(InteractionHand.MAIN_HAND);
                }
            }
        }
    }

    private FindItemResult findBlocks() {
        if (inventory.getValue()) {
            FindItemResult result = InvUtils.find(Items.OBSIDIAN);
            if (result.found() || !enderChest.getValue()) {
                return result;
            }
            return InvUtils.find(Items.ENDER_CHEST);
        } else {
            FindItemResult result = InvUtils.findInHotbar(Items.OBSIDIAN);
            if (result.found() || !enderChest.getValue()) {
                return result;
            }
            return InvUtils.findInHotbar(Items.ENDER_CHEST);
        }
    }

    public BlockPos getHelperPos(BlockPos pos) {
        for (Direction i : Direction.values()) {
            if (!RotationUtils.canSee(pos.relative(i), i.getOpposite())) continue;
            if (BlockUtils.canPlaceAt(pos.relative(i), false)) return pos.relative(i);
        }
        return null;
    }

}
