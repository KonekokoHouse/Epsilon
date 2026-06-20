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

    /**
     * 用于检查水平四个方向相邻方块的偏移。
     */
    public static final Vec3i[] VECTOR_PATTERN = {
            new Vec3i(0, 0, 1),
            new Vec3i(0, 0, -1),
            new Vec3i(1, 0, 0),
            new Vec3i(-1, 0, 0)
    };

    /**
     * 根据实体坐标返回其脚下可能重叠的洞位方块。
     * <p>
     * 当坐标小数部分接近方块边缘时，实体碰撞箱可能横跨到相邻格，因此会额外返回相邻方向的候选位置。
     *
     * @param from 实体或玩家的世界坐标
     * @return 可能被实体脚部占据的方块坐标列表
     */
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

    /**
     * 将世界坐标转换为洞位检测使用的方块坐标。
     * <p>
     * 当 Y 轴小数部分大于 0.8 时视为更接近上一格，避免站在方块边缘或上升过程中误判脚下位置。
     *
     * @param from 世界坐标
     * @return 用于洞位检测的方块坐标
     */
    private static BlockPos getPos(Vec3 from) {
        return BlockPos.containing(from.x, from.y - Math.floor(from.y) > 0.8 ? Math.floor(from.y) + 1.0 : Math.floor(from.y), from.z);
    }

    /**
     * 根据坐标小数部分计算需要检查的相邻格偏移。
     *
     * @param dec 坐标小数部分，通常位于 0.0 到 1.0 之间
     * @return 接近正方向边缘返回 1，接近负方向边缘返回 -1，否则返回 0
     */
    public static int calcOffset(double dec) {
        return dec >= 0.7 ? 1 : (dec <= 0.3 ? -1 : 0);
    }

    /**
     * 根据坐标小数部分计算玩家在指定方向上额外占用的长度。
     *
     * @param decimal  坐标小数部分，通常位于 0.0 到 1.0 之间
     * @param negative 是否检查负方向边缘
     * @return 需要向该方向额外扩展的格数，当前只会返回 0 或 1
     */
    public static int calcLength(double decimal, boolean negative) {
        if (negative) return decimal <= 0.3 ? 1 : 0;
        return decimal >= 0.7 ? 1 : 0;
    }

    /**
     * 基于玩家所在方块和相对偏移计算实际世界方块坐标。
     * <p>
     * 当玩家坐标为负数时，Minecraft 的 floor 坐标会让相对方向看起来反向，因此这里会按坐标符号翻转偏移。
     *
     * @param playerPos 玩家所在方块坐标
     * @param x         X 轴相对偏移
     * @param y         Y 轴相对偏移
     * @param z         Z 轴相对偏移
     * @return 加上符号修正后得到的方块坐标
     */
    public static BlockPos addToPlayer(BlockPos playerPos, double x, double y, double z) {
        if (playerPos.getX() < 0) x = -x;
        if (playerPos.getY() < 0) y = -y;
        if (playerPos.getZ() < 0) z = -z;
        return playerPos.offset(BlockPos.containing(x, y, z));
    }

    /**
     * 判断指定位置是否为任意受保护洞位。
     *
     * @param pos 待检测的脚部方块坐标
     * @return 如果位置是单格、双格或 2x2 洞位则返回 true
     */
    public static boolean isHole(BlockPos pos) {
        return isSingleHole(pos)
                || validTwoBlockIndestructible(pos) || validTwoBlockBedrock(pos)
                || validQuadIndestructible(pos) || validQuadBedrock(pos);
    }

    /**
     * 判断指定位置是否为单格洞。
     *
     * @param pos 待检测的脚部方块坐标
     * @return 如果位置是基岩单格洞或混合不可破坏方块单格洞则返回 true
     */
    public static boolean isSingleHole(BlockPos pos) {
        return validIndestructible(pos) || validBedrock(pos);
    }

    /**
     * 判断指定位置是否为混合不可破坏方块单格洞。
     * <p>
     * 混合洞允许黑曜石、哭泣的黑曜石、下界合金块、重生锚和基岩共同组成底部与水平四周，
     * 但不把纯基岩洞算入此类型。
     *
     * @param pos 待检测的脚部方块坐标
     * @return 如果该位置是混合不可破坏方块单格洞则返回 true
     */
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

    /**
     * 判断指定位置是否为纯基岩单格洞。
     *
     * @param pos 待检测的脚部方块坐标
     * @return 如果底部与水平四周都是基岩，且内部三格可替换则返回 true
     */
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

    /**
     * 判断指定位置是否属于纯基岩双格洞。
     *
     * @param pos 双格洞中任意一个脚部方块坐标
     * @return 如果两个相邻可替换格的底部与外侧围挡全部为基岩则返回 true
     */
    public static boolean validTwoBlockBedrock(BlockPos pos) {
        if (!isReplaceable(pos)) return false;
        Vec3i addVec = getTwoBlocksDirection(pos);

        // 找不到第二个可替换格时，说明不是双格洞。
        if (addVec == null)
            return false;

        BlockPos[] checkPoses = new BlockPos[]{pos, pos.offset(addVec)};
        // 检查两个内部格各自的底部、头顶空间和外侧围挡。
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

    /**
     * 判断指定位置是否属于混合不可破坏方块双格洞。
     * <p>
     * 与纯基岩双格洞不同，该类型要求围挡中至少存在一个非基岩的不可破坏方块。
     *
     * @param pos 双格洞中任意一个脚部方块坐标
     * @return 如果两个相邻可替换格由基岩和不可破坏方块保护则返回 true
     */
    public static boolean validTwoBlockIndestructible(BlockPos pos) {
        if (!isReplaceable(pos)) return false;
        Vec3i addVec = getTwoBlocksDirection(pos);

        // 找不到第二个可替换格时，说明不是双格洞。
        if (addVec == null)
            return false;

        BlockPos[] checkPoses = new BlockPos[]{pos, pos.offset(addVec)};
        // 检查两个内部格各自的底部、头顶空间和外侧围挡。
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

    /**
     * 查找与指定位置相邻的第二个双格洞内部格。
     *
     * @param pos 双格洞起始脚部方块坐标
     * @return 相邻可替换格的方向偏移；找不到时返回 null
     */
    private static Vec3i getTwoBlocksDirection(BlockPos pos) {
        // 尝试在水平四个方向寻找第二个内部格。
        for (Vec3i vec : VECTOR_PATTERN) {
            if (isReplaceable(pos.offset(vec)))
                return vec;
        }

        return null;
    }

    /**
     * 判断指定位置是否属于混合不可破坏方块 2x2 洞。
     * <p>
     * 与纯基岩 2x2 洞不同，该类型要求围挡中至少存在一个非基岩的不可破坏方块。
     *
     * @param pos 2x2 洞中任意一个脚部方块坐标
     * @return 如果四个内部格由基岩和不可破坏方块保护则返回 true
     */
    public static boolean validQuadIndestructible(BlockPos pos) {
        List<BlockPos> checkPoses = getQuadDirection(pos);
        // 找不到完整的 2x2 内部空间时，说明不是 2x2 洞。
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

    /**
     * 判断指定位置是否属于纯基岩 2x2 洞。
     *
     * @param pos 2x2 洞中任意一个脚部方块坐标
     * @return 如果四个内部格的底部与外侧围挡全部为基岩则返回 true
     */
    public static boolean validQuadBedrock(BlockPos pos) {
        List<BlockPos> checkPoses = getQuadDirection(pos);
        // 找不到完整的 2x2 内部空间时，说明不是 2x2 洞。
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

    /**
     * 查找包含指定位置的 2x2 可替换内部空间。
     *
     * @param pos 2x2 洞中任意一个脚部方块坐标
     * @return 四个内部格的坐标列表；找不到唯一完整 2x2 空间时返回 null
     */
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

    /**
     * 判断方块是否属于洞位判定中的非基岩不可破坏围挡。
     *
     * @param bp 方块坐标
     * @return 如果方块是黑曜石、下界合金块、哭泣的黑曜石或重生锚则返回 true
     */
    private static boolean isIndestructible(BlockPos bp) {
        Block block = mc.level.getBlockState(bp).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.NETHERITE_BLOCK || block == Blocks.CRYING_OBSIDIAN || block == Blocks.RESPAWN_ANCHOR;
    }

    /**
     * 判断指定位置是否为基岩。
     *
     * @param bp 方块坐标
     * @return 如果该位置方块是基岩则返回 true
     */
    private static boolean isBedrock(BlockPos bp) {
        return mc.level.getBlockState(bp).getBlock() == Blocks.BEDROCK;
    }

    /**
     * 判断指定位置的方块状态是否可替换。
     *
     * @param bp 方块坐标
     * @return 如果该位置可被放置方块替换则返回 true
     */
    private static boolean isReplaceable(BlockPos bp) {
        return mc.level.getBlockState(bp).canBeReplaced();
    }

}
