package com.github.epsilon.utils.player;

import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.epsilon.Constants.mc;

public class PlayerUtils {

    public static final Map<BlockPos, Long> awaiting = new HashMap<>();

    public static boolean isEating() {
        return (mc.player.getMainHandItem().getComponents().has(DataComponents.FOOD) || mc.player.getOffhandItem().getComponents().has(DataComponents.FOOD)) && mc.player.isUsingItem();
    }

    public static boolean isInWeb() {
        AABB box = mc.player.getBoundingBox().deflate(1.0E-6);

        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    if (mc.level.getBlockState(mutablePos).getBlock() instanceof WebBlock) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static boolean isInBlock() {
        AABB box = mc.player.getBoundingBox().deflate(1.0E-6);

        int minX = Mth.floor(box.minX);
        int minY = Mth.floor(box.minY);
        int minZ = Mth.floor(box.minZ);
        int maxX = Mth.floor(box.maxX);
        int maxY = Mth.floor(box.maxY);
        int maxZ = Mth.floor(box.maxZ);

        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutablePos.set(x, y, z);
                    if (BlockUtils.isSolidBlock(mutablePos)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static BlockHitResult getPlaceResult(BlockPos pos, HelperMode interact, boolean ignoreEntities) {
        if (!ignoreEntities) {
            for (Entity entity : mc.level.getEntitiesOfClass(Entity.class, new AABB(pos))) {
                if (!(entity instanceof ItemEntity) && !(entity instanceof ExperienceOrb)) {
                    return null;
                }
            }
        }

        if (!mc.level.getBlockState(pos).canBeReplaced()) {
            return null;
        }

        List<BlockPosWithFacing> supports = getSupportBlocks(pos);
        for (BlockPosWithFacing support : supports) {
            if (interact != HelperMode.NCP) {
                List<Direction> dirs = getStrictDirections(pos);
                if (dirs.isEmpty()) {
                    return null;
                }

                if (!dirs.contains(support.facing())) {
                    continue;
                }
            }

            if (interact == HelperMode.Legit) {
                Vec3 point = getVisibleDirectionPoint(support.facing(), support.position(), 0.0f, 6.0f);
                if (point != null) {
                    return new BlockHitResult(point, support.facing(), support.position(), false);
                }
            } else {
                Vec3 directionVec = new Vec3(
                        support.position().getX() + 0.5 + support.facing().getStepX() * 0.5,
                        support.position().getY() + 0.5 + support.facing().getStepY() * 0.5,
                        support.position().getZ() + 0.5 + support.facing().getStepZ() * 0.5
                );
                return new BlockHitResult(directionVec, support.facing(), support.position(), false);
            }
        }

        return null;
    }

    /**
     * 获取可以支持该方块被放置的放置点方块
     * @param pos 被放置方块的位置
     * @return 支持方块
     */
    private static List<BlockPosWithFacing> getSupportBlocks(BlockPos pos) {
        List<BlockPosWithFacing> list = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            if (isValidBlock(pos.relative(direction))) {
                list.add(new BlockPosWithFacing(pos.relative(direction), direction.getOpposite()));
            }
        }

        return list;
    }

    private static List<Direction> getStrictDirections(BlockPos bp) {
        List<Direction> visibleSides = new ArrayList<>();
        Vec3 positionVector = bp.getCenter();
        Vec3 eyesPos = mc.player.getEyePosition();

        double westDelta = eyesPos.x - positionVector.add(0.5, 0.0, 0.0).x;
        double eastDelta = eyesPos.x - positionVector.add(-0.5, 0.0, 0.0).x;
        double northDelta = eyesPos.z - positionVector.add(0.0, 0.0, 0.5).z;
        double southDelta = eyesPos.z - positionVector.add(0.0, 0.0, -0.5).z;
        double upDelta = eyesPos.y - positionVector.add(0.0, 0.5, 0.0).y;
        double downDelta = eyesPos.y - positionVector.add(0.0, -0.5, 0.0).y;

        if (westDelta > 0 && isValidBlock(bp.west())) {
            visibleSides.add(Direction.EAST);
        }
        if (westDelta < 0 && isValidBlock(bp.east())) {
            visibleSides.add(Direction.WEST);
        }

        if (eastDelta < 0 && isValidBlock(bp.east())) {
            visibleSides.add(Direction.WEST);
        }
        if (eastDelta > 0 && isValidBlock(bp.west())) {
            visibleSides.add(Direction.EAST);
        }

        if (northDelta > 0 && isValidBlock(bp.north())) {
            visibleSides.add(Direction.SOUTH);
        }
        if (northDelta < 0 && isValidBlock(bp.south())) {
            visibleSides.add(Direction.NORTH);
        }

        if (southDelta < 0 && isValidBlock(bp.south())) {
            visibleSides.add(Direction.NORTH);
        }
        if (southDelta > 0 && isValidBlock(bp.north())) {
            visibleSides.add(Direction.SOUTH);
        }

        if (upDelta > 0 && isValidBlock(bp.below())) {
            visibleSides.add(Direction.UP);
        }
        if (upDelta < 0 && isValidBlock(bp.above())) {
            visibleSides.add(Direction.DOWN);
        }

        if (downDelta < 0 && isValidBlock(bp.above())) {
            visibleSides.add(Direction.DOWN);
        }
        if (downDelta > 0 && isValidBlock(bp.below())) {
            visibleSides.add(Direction.UP);
        }

        return visibleSides;
    }

    /**
     * 判断该方块是否为空气、流体或者可替换方块
     * @param bp 方块位置
     * @return 是否有效
     */
    public static boolean isValidBlock(BlockPos bp) {
        BlockState state = mc.level.getBlockState(bp);
        return !state.isAir() && !state.canBeReplaced() && state.getFluidState().isEmpty();
    }

    private static Vec3 getVisibleDirectionPoint(Direction dir, BlockPos bp, float wallRange, float range) {
        AABB directionBox = getDirectionBox(dir);

        if (directionBox.maxX - directionBox.minX == 0.0) {
            for (double y = directionBox.minY; y < directionBox.maxY; y += 0.1f) {
                for (double z = directionBox.minZ; z < directionBox.maxZ; z += 0.1f) {
                    Vec3 point = new Vec3(bp.getX() + directionBox.minX, bp.getY() + y, bp.getZ() + z);
                    if (!shouldSkipPoint(point, bp, wallRange, range)) {
                        return point;
                    }
                }
            }
        }

        if (directionBox.maxY - directionBox.minY == 0.0) {
            for (double x = directionBox.minX; x < directionBox.maxX; x += 0.1f) {
                for (double z = directionBox.minZ; z < directionBox.maxZ; z += 0.1f) {
                    Vec3 point = new Vec3(bp.getX() + x, bp.getY() + directionBox.minY, bp.getZ() + z);
                    if (!shouldSkipPoint(point, bp, wallRange, range)) {
                        return point;
                    }
                }
            }
        }

        if (directionBox.maxZ - directionBox.minZ == 0.0) {
            for (double x = directionBox.minX; x < directionBox.maxX; x += 0.1f) {
                for (double y = directionBox.minY; y < directionBox.maxY; y += 0.1f) {
                    Vec3 point = new Vec3(bp.getX() + x, bp.getY() + y, bp.getZ() + directionBox.minZ);
                    if (!shouldSkipPoint(point, bp, wallRange, range)) {
                        return point;
                    }
                }
            }
        }

        return null;
    }

    private static AABB getDirectionBox(Direction dir) {
        return switch (dir) {
            case UP -> new AABB(.15f, 1f, .15f, .85f, 1f, .85f);
            case DOWN -> new AABB(.15f, 0f, .15f, .85f, 0f, .85f);
            case EAST -> new AABB(1f, .15f, .15f, 1f, .85f, .85f);
            case WEST -> new AABB(0f, .15f, .15f, 0f, .85f, .85f);
            case NORTH -> new AABB(.15f, .15f, 0f, .85f, .85f, 0f);
            case SOUTH -> new AABB(.15f, .15f, 1f, .85f, .85f, 1f);
        };
    }

    private static boolean shouldSkipPoint(Vec3 point, BlockPos bp, float wallRange, float range) {
        BlockHitResult result = mc.level.clip(new ClipContext(
                mc.player.getEyePosition(),
                point,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                mc.player
        ));

        double distance = mc.player.getEyePosition().distanceToSqr(point);

        if (result.getType() == HitResult.Type.BLOCK && !result.getBlockPos().equals(bp) && distance > wallRange * wallRange) {
            return true;
        }

        return distance > range * range;
    }

    public enum HelperMode {
        NCP,
        Grim,
        Legit
    }

    private record BlockPosWithFacing(BlockPos position, Direction facing) {
    }

}
