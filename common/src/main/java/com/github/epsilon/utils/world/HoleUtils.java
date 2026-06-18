package com.github.epsilon.utils.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.github.epsilon.Constants.mc;

public class HoleUtils {

    public static final Vec3i[] VECTOR_PATTERN = {
            new Vec3i(0, 0, 1),
            new Vec3i(0, 0, -1),
            new Vec3i(1, 0, 0),
            new Vec3i(-1, 0, 0)
    };

    public static List<BlockPos> getHolePoses(Vec3 from) {
        List<BlockPos> positions = new ArrayList<>();

        double decimalX = from.x - Math.floor(from.x);
        double decimalZ = from.z - Math.floor(from.z);
        int offX = calcOffset(decimalX);
        int offZ = calcOffset(decimalZ);
        positions.add(getPos(from));
        for (int x = 0; x <= Math.abs(offX); ++x) {
            for (int z = 0; z <= Math.abs(offZ); ++z) {
                int properX = x * offX;
                int properZ = z * offZ;
                positions.add(Objects.requireNonNull(getPos(from)).offset(properX, 0, properZ));
            }
        }

        return positions;
    }

    public static List<BlockPos> getSurroundPoses(Vec3 from) {
        final BlockPos fromPos = BlockPos.containing(from);
        final ArrayList<BlockPos> tempOffsets = new ArrayList<>();

        final double decimalX = Math.abs(from.x) - Math.floor(Math.abs(from.x));
        final double decimalZ = Math.abs(from.z) - Math.floor(Math.abs(from.z));
        final int lengthXPos = calcLength(decimalX, false);
        final int lengthXNeg = calcLength(decimalX, true);
        final int lengthZPos = calcLength(decimalZ, false);
        final int lengthZNeg = calcLength(decimalZ, true);

        for (int x = 1; x < lengthXPos + 1; ++x) {
            tempOffsets.add(addToPlayer(fromPos, x, 0.0, 1 + lengthZPos));
            tempOffsets.add(addToPlayer(fromPos, x, 0.0, -(1 + lengthZNeg)));
        }
        for (int x = 0; x <= lengthXNeg; ++x) {
            tempOffsets.add(addToPlayer(fromPos, -x, 0.0, 1 + lengthZPos));
            tempOffsets.add(addToPlayer(fromPos, -x, 0.0, -(1 + lengthZNeg)));
        }
        for (int z = 1; z < lengthZPos + 1; ++z) {
            tempOffsets.add(addToPlayer(fromPos, 1 + lengthXPos, 0.0, z));
            tempOffsets.add(addToPlayer(fromPos, -(1 + lengthXNeg), 0.0, z));
        }
        for (int z = 0; z <= lengthZNeg; ++z) {
            tempOffsets.add(addToPlayer(fromPos, 1 + lengthXPos, 0.0, -z));
            tempOffsets.add(addToPlayer(fromPos, -(1 + lengthXNeg), 0.0, -z));
        }

        return tempOffsets;
    }

    private static BlockPos getPos(Vec3 from) {
        return BlockPos.containing(from.x, from.y - Math.floor(from.y) > 0.8 ? Math.floor(from.y) + 1.0 : Math.floor(from.y), from.z);
    }

    public static int calcOffset(double dec) {
        return dec >= 0.7 ? 1 : (dec <= 0.3 ? -1 : 0);
    }

    public static int calcLength(double decimal, boolean negative) {
        if (negative) return decimal <= 0.3 ? 1 : 0;
        return decimal >= 0.7 ? 1 : 0;
    }

    public static BlockPos addToPlayer(BlockPos playerPos, double x, double y, double z) {
        if (playerPos.getX() < 0) x = -x;
        if (playerPos.getY() < 0) y = -y;
        if (playerPos.getZ() < 0) z = -z;
        return playerPos.offset(BlockPos.containing(x, y, z));
    }

    public static boolean isHole(BlockPos pos) {
        return isSingleHole(pos)
                || validTwoBlockIndestructible(pos) || validTwoBlockBedrock(pos)
                || validQuadIndestructible(pos) || validQuadBedrock(pos);
    }

    public static boolean isSingleHole(BlockPos pos) {
        return validIndestructible(pos) || validBedrock(pos);
    }

    public static boolean validIndestructible(BlockPos pos) {
        return !validBedrock(pos)
                && (isIndestructible(pos.offset(0, -1, 0)) || isBedrock(pos.offset(0, -1, 0)))
                && (isIndestructible(pos.offset(1, 0, 0)) || isBedrock(pos.offset(1, 0, 0)))
                && (isIndestructible(pos.offset(-1, 0, 0)) || isBedrock(pos.offset(-1, 0, 0)))
                && (isIndestructible(pos.offset(0, 0, 1)) || isBedrock(pos.offset(0, 0, 1)))
                && (isIndestructible(pos.offset(0, 0, -1)) || isBedrock(pos.offset(0, 0, -1)))
                && isReplaceable(pos)
                && isReplaceable(pos.offset(0, 1, 0))
                && isReplaceable(pos.offset(0, 2, 0));
    }

    public static boolean validBedrock(BlockPos pos) {
        return isBedrock(pos.offset(0, -1, 0))
                && isBedrock(pos.offset(1, 0, 0))
                && isBedrock(pos.offset(-1, 0, 0))
                && isBedrock(pos.offset(0, 0, 1))
                && isBedrock(pos.offset(0, 0, -1))
                && isReplaceable(pos)
                && isReplaceable(pos.offset(0, 1, 0))
                && isReplaceable(pos.offset(0, 2, 0));
    }

    public static boolean validTwoBlockBedrock(BlockPos pos) {
        if (!isReplaceable(pos)) return false;
        Vec3i addVec = getTwoBlocksDirection(pos);

        // If addVec not found -> hole incorrect
        if (addVec == null)
            return false;

        BlockPos[] checkPoses = new BlockPos[]{pos, pos.offset(addVec)};
        // Check surround poses of checkPoses
        for (BlockPos checkPos : checkPoses) {

            if (!isReplaceable(checkPos.offset(0, 1, 0)) || !isReplaceable(checkPos.offset(0, 2, 0)))
                return false;

            BlockPos downPos = checkPos.below();
            if (!isBedrock(downPos))
                return false;

            for (Vec3i vec : VECTOR_PATTERN) {
                BlockPos reducedPos = checkPos.offset(vec);
                if (!isBedrock(reducedPos) && !reducedPos.equals(pos) && !reducedPos.equals(pos.offset(addVec)))
                    return false;
            }
        }

        return true;
    }

    public static boolean validTwoBlockIndestructible(BlockPos pos) {
        if (!isReplaceable(pos)) return false;
        Vec3i addVec = getTwoBlocksDirection(pos);

        // If addVec not found -> hole incorrect
        if (addVec == null)
            return false;

        BlockPos[] checkPoses = new BlockPos[]{pos, pos.offset(addVec)};
        // Check surround poses of checkPoses
        boolean wasIndestrictible = false;
        for (BlockPos checkPos : checkPoses) {
            BlockPos downPos = checkPos.below();

            if (isIndestructible(downPos))
                wasIndestrictible = true;
            else if (!isBedrock(downPos))
                return false;

            if (!isReplaceable(checkPos.offset(0, 1, 0)) || !isReplaceable(checkPos.offset(0, 2, 0)))
                return false;

            for (Vec3i vec : VECTOR_PATTERN) {
                BlockPos reducedPos = checkPos.offset(vec);

                if (isIndestructible(reducedPos)) {
                    wasIndestrictible = true;
                    continue;
                }
                if (!isBedrock(reducedPos) && !reducedPos.equals(pos) && !reducedPos.equals(pos.offset(addVec)))
                    return false;
            }
        }

        return wasIndestrictible;
    }

    private static Vec3i getTwoBlocksDirection(BlockPos pos) {
        // Try to get direction
        for (Vec3i vec : VECTOR_PATTERN) {
            if (isReplaceable(pos.offset(vec)))
                return vec;
        }

        return null;
    }

    public static boolean validQuadIndestructible(BlockPos pos) {
        List<BlockPos> checkPoses = getQuadDirection(pos);
        // If checkPoses not found -> hole incorrect
        if (checkPoses == null)
            return false;

        boolean wasIndestrictible = false;
        for (BlockPos checkPos : checkPoses) {
            BlockPos downPos = checkPos.below();
            if (isIndestructible(downPos)) {
                wasIndestrictible = true;
            } else if (!isBedrock(downPos)) {
                return false;
            }

            if (!isReplaceable(checkPos.offset(0, 1, 0)) || !isReplaceable(checkPos.offset(0, 2, 0)))
                return false;

            for (Vec3i vec : VECTOR_PATTERN) {
                BlockPos reducedPos = checkPos.offset(vec);

                if (isIndestructible(reducedPos)) {
                    wasIndestrictible = true;
                    continue;
                }
                if (!isBedrock(reducedPos) && !checkPoses.contains(reducedPos)) {
                    return false;
                }
            }
        }

        return wasIndestrictible;
    }

    public static boolean validQuadBedrock(BlockPos pos) {
        List<BlockPos> checkPoses = getQuadDirection(pos);
        // If checkPoses not found -> hole incorrect
        if (checkPoses == null)
            return false;

        for (BlockPos checkPos : checkPoses) {
            BlockPos downPos = checkPos.below();
            if (!isBedrock(downPos))
                return false;

            if (!isReplaceable(checkPos.offset(0, 1, 0)) || !isReplaceable(checkPos.offset(0, 2, 0)))
                return false;

            for (Vec3i vec : VECTOR_PATTERN) {
                BlockPos reducedPos = checkPos.offset(vec);
                if (!isBedrock(reducedPos) && !checkPoses.contains(reducedPos)) {
                    return false;
                }
            }
        }

        return true;
    }

    private static List<BlockPos> getQuadDirection(BlockPos pos) {
        List<BlockPos> dirList = new ArrayList<>();
        dirList.add(pos);

        if (!isReplaceable(pos))
            return null;

        if (isReplaceable(pos.offset(1, 0, 0)) && isReplaceable(pos.offset(0, 0, 1)) && isReplaceable(pos.offset(1, 0, 1))) {
            dirList.add(pos.offset(1, 0, 0));
            dirList.add(pos.offset(0, 0, 1));
            dirList.add(pos.offset(1, 0, 1));
        }
        if (isReplaceable(pos.offset(-1, 0, 0)) && isReplaceable(pos.offset(0, 0, -1)) && isReplaceable(pos.offset(-1, 0, -1))) {
            dirList.add(pos.offset(-1, 0, 0));
            dirList.add(pos.offset(0, 0, -1));
            dirList.add(pos.offset(-1, 0, -1));
        }
        if (isReplaceable(pos.offset(1, 0, 0)) && isReplaceable(pos.offset(0, 0, -1)) && isReplaceable(pos.offset(1, 0, -1))) {
            dirList.add(pos.offset(1, 0, 0));
            dirList.add(pos.offset(0, 0, -1));
            dirList.add(pos.offset(1, 0, -1));
        }
        if (isReplaceable(pos.offset(-1, 0, 0)) && isReplaceable(pos.offset(0, 0, 1)) && isReplaceable(pos.offset(-1, 0, 1))) {
            dirList.add(pos.offset(-1, 0, 0));
            dirList.add(pos.offset(0, 0, 1));
            dirList.add(pos.offset(-1, 0, 1));
        }

        if (dirList.size() != 4) {
            return null;
        }

        return dirList;
    }

    private static boolean isIndestructible(BlockPos bp) {
        Block block = mc.level.getBlockState(bp).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.NETHERITE_BLOCK || block == Blocks.CRYING_OBSIDIAN || block == Blocks.RESPAWN_ANCHOR;
    }

    private static boolean isBedrock(BlockPos bp) {
        return mc.level.getBlockState(bp).getBlock() == Blocks.BEDROCK;
    }

    private static boolean isReplaceable(BlockPos bp) {
        return mc.level.getBlockState(bp).canBeReplaced();
    }

}
