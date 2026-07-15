package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HoleESP extends Module {

    public static final HoleESP INSTANCE = new HoleESP();

    private HoleESP() {
        super("Hole ESP", Category.RENDER);
    }

    public enum UpdateHoles {
        Ignore,
        Render,
        Tick
    }

    private enum ShapeMode {
        Both,
        Sides,
        Lines
    }

    private enum HoleType {
        Bedrock,
        Mixed,
        Obsidian
    }

    private final SettingGroup sgRender = settingGroup("Render");

    private final DoubleSetting horizontalDistance = doubleSetting("Horizontal Distance", 15.0, 0.0, 64.0, 1.0);
    private final DoubleSetting verticalDistance = doubleSetting("Vertical Distance", 10.0, 0.0, 64.0, 1.0);
    private final EnumSetting<UpdateHoles> updateHoles = enumSetting("Update Holes", UpdateHoles.Render);
    private final BoolSetting allowHalf = boolSetting("Allow Half", false);
    private final BoolSetting webs = boolSetting("Webs", false);
    private final BoolSetting ignoreOwn = boolSetting("Ignore Own", false);
    private final BoolSetting ignoreBurrow = boolSetting("Ignore Burrow", true);

    private final BoolSetting fadeIn = boolSetting("Fade In", true).group(sgRender);
    private final BoolSetting fadeOut = boolSetting("Fade Out", true).group(sgRender);
    private final IntSetting renderTicks = intSetting("Ticks", 10, 1, 15, 1).group(sgRender);
    private final EnumSetting<ShapeMode> shapeMode = enumSetting("Shape Mode", ShapeMode.Both).group(sgRender);
    private final DoubleSetting lineWidth = doubleSetting("Line Width", 1.0, 0.0, 5.0, 0.1, () -> shapeMode.is(ShapeMode.Both) || shapeMode.is(ShapeMode.Lines)).group(sgRender);
    private final DoubleSetting renderHeight = doubleSetting("Height", 0.75, 0.0, 1.0, 0.05).group(sgRender);
    private final DoubleSetting shrinkSpeed = doubleSetting("Shrink Speed", 0.1, 0.0, 0.25, 0.01).group(sgRender);
    private final BoolSetting topQuad = boolSetting("Top Quad", false).group(sgRender);
    private final BoolSetting bottomQuad = boolSetting("Bottom Quad", true).group(sgRender);

    private final ColorSetting bedrockSidesTop = colorSetting("Bedrock Sides Top", new Color(100, 255, 0, 0)).group(sgRender);
    private final ColorSetting bedrockSidesBottom = colorSetting("Bedrock Sides Bottom", new Color(100, 255, 0, 25)).group(sgRender);
    private final ColorSetting bedrockLinesTop = colorSetting("Bedrock Lines Top", new Color(100, 255, 0, 0)).group(sgRender);
    private final ColorSetting bedrockLinesBottom = colorSetting("Bedrock Lines Bottom", new Color(100, 255, 0, 200)).group(sgRender);
    private final ColorSetting obsidianSidesTop = colorSetting("Obsidian Sides Top", new Color(255, 0, 0, 0)).group(sgRender);
    private final ColorSetting obsidianSidesBottom = colorSetting("Obsidian Sides Bottom", new Color(255, 0, 0, 25)).group(sgRender);
    private final ColorSetting obsidianLinesTop = colorSetting("Obsidian Lines Top", new Color(255, 0, 0, 0)).group(sgRender);
    private final ColorSetting obsidianLinesBottom = colorSetting("Obsidian Lines Bottom", new Color(255, 0, 0, 200)).group(sgRender);
    private final ColorSetting mixedSidesTop = colorSetting("Mixed Sides Top", new Color(255, 127, 0, 0)).group(sgRender);
    private final ColorSetting mixedSidesBottom = colorSetting("Mixed Sides Bottom", new Color(255, 127, 0, 25)).group(sgRender);
    private final ColorSetting mixedLinesTop = colorSetting("Mixed Lines Top", new Color(255, 127, 0, 0)).group(sgRender);
    private final ColorSetting mixedLinesBottom = colorSetting("Mixed Lines Bottom", new Color(255, 127, 0, 200)).group(sgRender);

    private final List<RenderBlock> renderBlocks = new ArrayList<>();

    private static final float BLAST_RESISTANCE = 600.0f;

    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    @Override
    protected void onEnable() {
        renderBlocks.clear();
    }

    @Override
    protected void onDisable() {
        renderBlocks.clear();
    }

    @EventHandler
    private void onPreTick(PlayerTickEvent.Pre event) {
        List<Hole> holes = scanHoles();
        holes.sort(Comparator.comparingDouble(hole -> mc.player.position().distanceToSqr(hole.pos1().getCenter())));

        BlockPos playerPos = mc.player.blockPosition();
        Set<BlockPos> processed = new HashSet<>();
        for (Hole hole : holes) {
            updateHolePart(hole.type(), hole.pos1(), hole.direction(), playerPos, processed);
            if (hole.isDouble()) {
                updateHolePart(hole.type(), hole.pos2(), hole.direction().getOpposite(), playerPos, processed);
            }
        }
    }

    @EventHandler
    private void onPostTick(PlayerTickEvent.Post event) {
        renderBlocks.forEach(RenderBlock::tick);
        renderBlocks.removeIf(block -> block.ticks <= 0);
        if (updateHoles.is(UpdateHoles.Tick)) {
            renderBlocks.removeIf(RenderBlock::isInvalid);
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (renderBlocks.isEmpty()) return;

        if (updateHoles.is(UpdateHoles.Render)) {
            renderBlocks.removeIf(RenderBlock::isInvalid);
        }
        renderBlocks.sort(Comparator.comparingInt(block -> -block.ticks));
        renderBlocks.forEach(RenderBlock::render);
    }

    private List<Hole> scanHoles() {
        List<Hole> holes = new ArrayList<>();
        Set<BlockPos> claimed = new HashSet<>();
        BlockPos playerPos = mc.player.blockPosition();
        int horizontal = (int) Math.floor(horizontalDistance.getValue());
        int vertical = (int) Math.floor(verticalDistance.getValue());
        int minY = Math.max(playerPos.getY() - vertical, mc.level.getMinY());
        int maxY = Math.min(playerPos.getY() + vertical, mc.level.getMaxY());

        for (int x = playerPos.getX() - horizontal; x <= playerPos.getX() + horizontal; x++) {
            for (int z = playerPos.getZ() - horizontal; z <= playerPos.getZ() + horizontal; z++) {
                if (!isChunkLoaded(x, z)) continue;

                for (int y = minY; y <= maxY; y++) {
                    if (
                            Math.abs(x - playerPos.getX()) > horizontalDistance.getValue()
                            || Math.abs(y - playerPos.getY()) > verticalDistance.getValue()
                            || Math.abs(z - playerPos.getZ()) > horizontalDistance.getValue()
                    ) {
                        continue;
                    }

                    BlockPos pos = new BlockPos(x, y, z);
                    if (claimed.contains(pos)) continue;

                    Hole hole = findHole(pos);
                    if (hole != null) {
                        holes.add(hole);
                        claimed.add(hole.pos1());
                        if (hole.pos2() != null) claimed.add(hole.pos2());
                    }
                }
            }
        }
        return holes;
    }

    private Hole findHole(BlockPos pos) {
        if (!isValidHole(pos, true) || !isValidHole(pos.above(), false)) return null;

        int bedrock = isBedrock(pos.below()) ? 1 : 0;
        int obsidian = bedrock == 0 ? 1 : 0;
        int air = 0;
        int surrounded = 0;
        BlockPos second = null;
        Direction direction = null;

        for (Direction side : HORIZONTAL_DIRECTIONS) {
            BlockPos adjacent = pos.relative(side);
            if (isValidHole(adjacent, true) && isValidHole(adjacent.above(), false)) {
                int secondSurrounded = 0;
                if (isBedrock(adjacent.below())) bedrock++;
                else obsidian++;

                for (Direction secondSide : HORIZONTAL_DIRECTIONS) {
                    BlockPos wallPos = adjacent.relative(secondSide);
                    if (isBlastResistant(wallPos)) {
                        secondSurrounded++;
                        if (isBedrock(wallPos)) bedrock++;
                        else obsidian++;
                    }
                }

                if (secondSurrounded == 3) {
                    second = adjacent;
                    direction = side;
                    air++;
                } else {
                    air = 0;
                }
            } else if (isBlastResistant(adjacent)) {
                surrounded++;
                if (isBedrock(adjacent)) bedrock++;
                else obsidian++;
            }
        }

        boolean doubleHole = air == 1 && surrounded >= 3 && (!allowHalf.getValue() || isValidHole(pos.above(2), false) || isValidHole(second.above(2), false));
        boolean singleHole = air == 0 && surrounded >= 4 && isValidHole(pos.above(2), false);
        if (!doubleHole && !singleHole) return null;

        HoleType type = bedrock == 0 ? HoleType.Obsidian
                : obsidian == 0 ? HoleType.Bedrock : HoleType.Mixed;
        return doubleHole ? new Hole(type, pos, second, direction) : new Hole(type, pos, null, null);
    }

    private void updateHolePart(HoleType type, BlockPos pos, Direction exclude, BlockPos playerPos, Set<BlockPos> processed) {
        if (!processed.add(pos)) return;

        boolean ignored = ignoreOwn.getValue() && playerPos.equals(pos)
                || ignoreBurrow.getValue() && playerPos.above().equals(pos);
        RenderBlock existing = findRenderBlock(pos);
        if (ignored) {
            if (existing != null) existing.invalidate();
            return;
        }

        if (existing == null && isInRange(pos)) {
            renderBlocks.add(new RenderBlock(type, pos, exclude));
        } else if (existing != null) {
            existing.update(type, exclude);
        }
    }

    private RenderBlock findRenderBlock(BlockPos pos) {
        for (RenderBlock block : renderBlocks) {
            if (block.pos.equals(pos)) return block;
        }
        return null;
    }

    private boolean isValidHole(BlockPos pos, boolean checkDown) {
        if (!mc.level.isInsideBuildHeight(pos.getY()) || !isChunkLoaded(pos.getX(), pos.getZ())) return false;

        BlockState state = mc.level.getBlockState(pos);
        if (!state.canBeReplaced() || state.is(Blocks.COBWEB) && !webs.getValue()) return false;
        if (!state.getCollisionShape(mc.level, pos).isEmpty()) return false;
        if (!checkDown) return true;

        BlockPos below = pos.below();
        if (!mc.level.isInsideBuildHeight(below.getY())) return false;
        BlockState floor = mc.level.getBlockState(below);
        return floor.getBlock().getExplosionResistance() >= BLAST_RESISTANCE
                && !floor.getCollisionShape(mc.level, below).isEmpty();
    }

    private boolean isHole(BlockPos pos) {
        return findHole(pos) != null;
    }

    private boolean isBlastResistant(BlockPos pos) {
        return mc.level.isInsideBuildHeight(pos.getY())
                && isChunkLoaded(pos.getX(), pos.getZ())
                && mc.level.getBlockState(pos).getBlock().getExplosionResistance() >= BLAST_RESISTANCE;
    }

    private boolean isChunkLoaded(int blockX, int blockZ) {
        return mc.level.getChunkSource().hasChunk(
                SectionPos.blockToSectionCoord(blockX), SectionPos.blockToSectionCoord(blockZ));
    }

    private boolean isBedrock(BlockPos pos) {
        return mc.level.getBlockState(pos).is(Blocks.BEDROCK);
    }

    private boolean isInRange(BlockPos pos) {
        Vec3 center = pos.getCenter();
        return horizontalDistance(mc.player.position(), center) <= horizontalDistance.getValue()
                && Math.abs(mc.player.getY() - center.y) <= verticalDistance.getValue();
    }

    private double horizontalDistance(Vec3 first, Vec3 second) {
        double x = first.x - second.x;
        double z = first.z - second.z;
        return Math.sqrt(x * x + z * z);
    }

    private Color sidesTop(HoleType type) {
        return switch (type) {
            case Bedrock -> bedrockSidesTop.getValue();
            case Obsidian -> obsidianSidesTop.getValue();
            case Mixed -> mixedSidesTop.getValue();
        };
    }

    private Color sidesBottom(HoleType type) {
        return switch (type) {
            case Bedrock -> bedrockSidesBottom.getValue();
            case Obsidian -> obsidianSidesBottom.getValue();
            case Mixed -> mixedSidesBottom.getValue();
        };
    }

    private Color linesTop(HoleType type) {
        return switch (type) {
            case Bedrock -> bedrockLinesTop.getValue();
            case Obsidian -> obsidianLinesTop.getValue();
            case Mixed -> mixedLinesTop.getValue();
        };
    }

    private Color linesBottom(HoleType type) {
        return switch (type) {
            case Bedrock -> bedrockLinesBottom.getValue();
            case Obsidian -> obsidianLinesBottom.getValue();
            case Mixed -> mixedLinesBottom.getValue();
        };
    }

    private int faded(Color color, int ticks) {
        int alpha = Math.min(color.getAlpha(), (int) (color.getAlpha() * ticks / 8.0));
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha).getRGB();
    }

    private record Hole(HoleType type, BlockPos pos1, BlockPos pos2, Direction direction) {
        private boolean isDouble() {
            return pos2 != null && direction != null;
        }
    }

    private final class RenderBlock {
        private final BlockPos pos;
        private HoleType type;
        private Direction exclude;
        private int ticks;
        private double height;
        private boolean valid;

        private RenderBlock(HoleType type, BlockPos pos, Direction exclude) {
            this.pos = pos.immutable();
            this.ticks = fadeIn.getValue() ? 1 : renderTicks.getValue();
            this.height = renderHeight.getValue();
            this.valid = true;
            update(type, exclude);
        }

        private void tick() {
            if (!isInRange(pos) || !isHole(pos) || !valid) {
                if (fadeOut.getValue()) {
                    ticks--;
                    height = Math.max(0.0, height - shrinkSpeed.getValue());
                } else {
                    ticks = 0;
                    height = 0.0;
                }
            } else if (fadeIn.getValue() && ticks < renderTicks.getValue()) {
                ticks++;
                height = Math.min(renderHeight.getValue(), height + shrinkSpeed.getValue());
            }
        }

        private void update(HoleType type, Direction exclude) {
            this.type = type;
            this.exclude = exclude;
        }

        private boolean isInvalid() {
            return !fadeOut.getValue() && (!isInRange(pos) || !isHole(pos) || !valid);
        }

        private void invalidate() {
            valid = false;
        }

        private void render() {
            int sideTop = faded(sidesTop(type), ticks);
            int sideBottom = faded(sidesBottom(type), ticks);
            int lineTop = faded(linesTop(type), ticks);
            int lineBottom = faded(linesBottom(type), ticks);
            double x = pos.getX();
            double y = pos.getY();
            double z = pos.getZ();
            AABB box = new AABB(x, y, z, x + 1.0, y + height, z + 1.0);

            if (shapeMode.is(ShapeMode.Sides) || shapeMode.is(ShapeMode.Both)) {
                if (bottomQuad.getValue()) {
                    Render3DScheduler.INSTANCE.addFilledFadeSide(box, sideBottom, sideTop, Direction.DOWN);
                }
                if (topQuad.getValue()) {
                    Render3DScheduler.INSTANCE.addFilledFadeSide(box, sideBottom, sideTop, Direction.UP);
                }
                for (Direction side : HORIZONTAL_DIRECTIONS) {
                    if (side != exclude) {
                        Render3DScheduler.INSTANCE.addFilledFadeSide(box, sideBottom, sideTop, side);
                    }
                }
            }

            if (shapeMode.is(ShapeMode.Lines) || shapeMode.is(ShapeMode.Both)) {
                renderLines(x, y, z, lineBottom, lineTop);
            }
        }

        private void renderLines(double x, double y, double z, int bottom, int top) {
            Render3DScheduler scheduler = Render3DScheduler.INSTANCE;
            double topY = y + height;
            float width = lineWidth.getValue().floatValue();

            if (exclude != Direction.WEST && exclude != Direction.NORTH)
                scheduler.addGradientLine(new Vec3(x, y, z), new Vec3(x, topY, z), bottom, top, width);
            if (exclude != Direction.WEST && exclude != Direction.SOUTH)
                scheduler.addGradientLine(new Vec3(x, y, z + 1), new Vec3(x, topY, z + 1), bottom, top, width);
            if (exclude != Direction.EAST && exclude != Direction.NORTH)
                scheduler.addGradientLine(new Vec3(x + 1, y, z), new Vec3(x + 1, topY, z), bottom, top, width);
            if (exclude != Direction.EAST && exclude != Direction.SOUTH)
                scheduler.addGradientLine(new Vec3(x + 1, y, z + 1), new Vec3(x + 1, topY, z + 1), bottom, top, width);

            if (exclude != Direction.NORTH) {
                scheduler.addLine(new Vec3(x, y, z), new Vec3(x + 1, y, z), bottom, width);
                scheduler.addLine(new Vec3(x, topY, z), new Vec3(x + 1, topY, z), top, width);
            }
            if (exclude != Direction.SOUTH) {
                scheduler.addLine(new Vec3(x, y, z + 1), new Vec3(x + 1, y, z + 1), bottom, width);
                scheduler.addLine(new Vec3(x, topY, z + 1), new Vec3(x + 1, topY, z + 1), top, width);
            }
            if (exclude != Direction.WEST) {
                scheduler.addLine(new Vec3(x, y, z), new Vec3(x, y, z + 1), bottom, width);
                scheduler.addLine(new Vec3(x, topY, z), new Vec3(x, topY, z + 1), top, width);
            }
            if (exclude != Direction.EAST) {
                scheduler.addLine(new Vec3(x + 1, y, z), new Vec3(x + 1, y, z + 1), bottom, width);
                scheduler.addLine(new Vec3(x + 1, topY, z), new Vec3(x + 1, topY, z + 1), top, width);
            }
        }
    }

}
