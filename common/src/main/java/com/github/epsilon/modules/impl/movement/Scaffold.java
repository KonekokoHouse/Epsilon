package dev.maru.modules;

import com.github.epsilon.Constants;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.*;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.modules.impl.movement.SafeWalk;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.math.MathUtils;
import com.github.epsilon.utils.player.FallingPlayer;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.render.Render3DUtils;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.StandingAndWallBlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Scaffold extends Module {

    public static final Scaffold INSTANCE = new Scaffold();

    private Scaffold() {
        super("Scaffold", Category.MOVEMENT);
        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class,
                event -> {
                    if (!render.getValue() || renderBoxes.isEmpty()) return;

                    long time = System.currentTimeMillis();
                    long fadeTime = this.fadeTime.getValue().longValue();

                    renderBoxes.removeIf(box -> time - box.startTime() > fadeTime);

                    for (RenderBox box : renderBoxes) {
                        float progress = Mth.clamp((float) (time - box.startTime()) / fadeTime, 0.0f, 1.0f);

                        double scale = 1.0;
                        if (box.shrink()) {
                            scale = 1.0 - Easing.EASE_OUT_QUAD.getFunction().apply(progress);
                            if (scale < 0) scale = 0;
                        }

                        float alphaFactor = box.fade() ? Mth.clamp(1.0f - progress, 0.0f, 1.0f) : 1.0f;

                        Color sideColor = box.sideColor();
                        Color lineColor = box.lineColor();

                        Color side = new Color(sideColor.getRed(), sideColor.getGreen(), sideColor.getBlue(), (int) (sideColor.getAlpha() * alphaFactor));
                        Color line = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), (int) (lineColor.getAlpha() * alphaFactor));

                        AABB renderBox = getRenderBox(box, scale);

                        Render3DUtils.drawFilledBox(renderBox, side);
                        Render3DUtils.drawOutlineBox(event.getPoseStack(), renderBox, line);
                    }
                }
        ));
    }

    private static final List<Block> BLACKLISTED_BLOCKS = List.of(
            Blocks.AIR,
            Blocks.WATER,
            Blocks.LAVA,
            Blocks.ENCHANTING_TABLE,
            Blocks.GLASS_PANE,
            Blocks.IRON_BARS,
            Blocks.SNOW,
            Blocks.COAL_ORE,
            Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE,
            Blocks.CHEST,
            Blocks.TRAPPED_CHEST,
            Blocks.TORCH,
            Blocks.ANVIL,
            Blocks.NOTE_BLOCK,
            Blocks.JUKEBOX,
            Blocks.TNT,
            Blocks.GOLD_ORE,
            Blocks.IRON_ORE,
            Blocks.LAPIS_ORE,
            Blocks.STONE_PRESSURE_PLATE,
            Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE,
            Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE,
            Blocks.STONE_BUTTON,
            Blocks.LEVER,
            Blocks.TALL_GRASS,
            Blocks.TRIPWIRE,
            Blocks.TRIPWIRE_HOOK,
            Blocks.RAIL,
            Blocks.CORNFLOWER,
            Blocks.RED_MUSHROOM,
            Blocks.BROWN_MUSHROOM,
            Blocks.VINE,
            Blocks.SUNFLOWER,
            Blocks.LADDER,
            Blocks.FURNACE,
            Blocks.SAND,
            Blocks.CACTUS,
            Blocks.DISPENSER,
            Blocks.DROPPER,
            Blocks.CRAFTING_TABLE,
            Blocks.COBWEB,
            Blocks.PUMPKIN,
            Blocks.COBBLESTONE_WALL,
            Blocks.OAK_FENCE,
            Blocks.REDSTONE_TORCH,
            Blocks.FLOWER_POT
    );

    private enum RaytraceMode {
        Hypixel,
        Normal,
        Strict
    }

    private final BoolSetting telly = boolSetting("Telly", false);
    //private final BoolSetting spoof = boolSetting("Spoof", false);
    private final BoolSetting snap = boolSetting("Snap", false, () -> !telly.getValue());
    private final EnumSetting<RaytraceMode> raytrace = enumSetting("Raytrace", RaytraceMode.Hypixel);
    private final IntSetting rotateSpeed = intSetting("Rot Speed", 10, 1, 10, 1, () -> !raytrace.is(RaytraceMode.Hypixel));
    private final IntSetting rotateBackSpeed = intSetting("Back Speed", 10, 1, 10, 1, telly::getValue);
    private final IntSetting tellyTicks = intSetting("Telly Ticks", 1, 0, 6, 1, telly::getValue);
    private final BoolSetting safeWalk = boolSetting("Safe Walk", false, () -> !telly.getValue());

    private final BoolSetting swingHand = boolSetting("Swing Hand", true);
    private final BoolSetting render = boolSetting("Render", true);
    private final BoolSetting fade = boolSetting("Fade", false, render::getValue);
    private final IntSetting fadeTime = intSetting("Fade Time", 500, 0, 3000, 50, () -> render.getValue() && fade.getValue());
    private final BoolSetting shrink = boolSetting("Shrink", true, render::getValue);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 183, 197, 100), render::getValue);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 105, 180), render::getValue);

    private int airTick;
    private int yLevel;
    private BlockPos blockPos;
    private Direction enumFacing;
    private int oldSlot = -1;
    private Rot2f rotation;
    private int rotateCount = 0;

    private final List<RenderBox> renderBoxes = new ArrayList<>();

    @Override
    protected void onEnable() {
        if (mc.player != null) {
            oldSlot = mc.player.getInventory().getSelectedSlot();
        }

        airTick = 0;
        blockPos = null;
        enumFacing = null;
        rotation = null;
        rotateCount = 0;
    }

    @Override
    protected void onDisable() {
        if (mc.player == null) return;

        boolean isHoldingShift = InputConstants.isKeyDown(mc.getWindow(), mc.options.keyShift.getDefaultKey().getValue());
        mc.options.keyShift.setDown(isHoldingShift);

        if (oldSlot != -1) {
            mc.player.getInventory().setSelectedSlot(oldSlot);
        }
        yLevel = 0;
    }

    @EventHandler
    private void onMotion(SendPositionEvent event) {
        if (telly.getValue() || !safeWalk.getValue()) return;
        mc.options.keyShift.setDown(mc.player.onGround() && SafeWalk.INSTANCE.isOnBlockEdge(0.3F));
    }

    @EventHandler(priority = EventPriority.LOWEST)
    private void onTick(TickEvent.Pre event) {
        if (nullCheck()) return;

        int slotId = findBlockSlot();
        if (slotId != -1 && mc.player.getInventory().getSelectedSlot() != slotId) {
            mc.player.getInventory().setSelectedSlot(slotId);
        }

        if (mc.player.onGround()) {
            airTick = 0;
            yLevel = Mth.floor(mc.player.getY()) - 1;
        } else {
            airTick++;
        }

        getBlockInfo();

        if (blockPos != null) {
            boolean reachable = true;
            if (mc.player.getDeltaMovement().y < -0.1) {
                FallingPlayer fallingPlayer = new FallingPlayer(mc.player);
                fallingPlayer.calculate(2);
                if (blockPos.getY() > fallingPlayer.getY()) {
                    reachable = false;
                }
            }
            double strength = mc.player.getDeltaMovement().horizontal().length();
            if ((!reachable || strength >= 1.5D) && rotateCount <= 8 && getBlockCount() >= 1 && isValidStack(mc.player.getInventory().getSelectedItem())) {
                Rot2f rotation = getRotation(blockPos, enumFacing);
                Constants.skipTicks++;
                rotateCount++;
                RotationManager.INSTANCE.rotations = rotation;
                RotationManager.INSTANCE.setActive(true);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(rotation.getYaw(), rotation.getPitch(), mc.player.onGround(), mc.player.horizontalCollision));
                InteractionResult result = mc.gameMode.useItemOn(
                        mc.player,
                        InteractionHand.MAIN_HAND,
                        new BlockHitResult(getVec3(blockPos, enumFacing), enumFacing, blockPos, false)
                );
                if (result.consumesAction()) {
                    if (swingHand.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
                    else mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

                    if (render.getValue()) {
                        renderBoxes.add(new RenderBox(new AABB(blockPos.relative(enumFacing)), new java.awt.Color(255, 105, 180), new java.awt.Color(255, 183, 197, 100), System.currentTimeMillis(), fade.getValue(), shrink.getValue()));
                    }
                }
                return;
            } else {
                rotateCount = 0;
            }
        }

        if (telly.getValue()) {
            handleTelly();
        } else {
            handleNormal();
        }
    }

    @EventHandler
    private void onMoveInput(KeyboardInputEvent event) {
        if (mc.player.onGround() && !mc.options.keyJump.isDown() && MoveUtils.isMoving() && telly.getValue()) {
            event.setJump(true);
        }
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValidStack(stack)) {
                return i;
            }
        }
        return -1;
    }

    private int getBlockCount() {
        int total = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (isValidStack(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private void handleTelly() {
        if (mc.player.onGround()) {
            RotationManager.INSTANCE.setRotations(new Rot2f(mc.player.getYRot(), rotation == null ? mc.player.getXRot() : rotation.getPitch()), rotateBackSpeed.getValue());
            return;
        }

        rotation = getRotation(blockPos, enumFacing);
        int speed = rotateSpeed.getValue();

        if (raytrace.is(RaytraceMode.Hypixel)) {
            speed = airTick <= 1 ? 127 : 35;
        }

        RotationManager.INSTANCE.setRotations(rotation, speed);

        if (airTick > tellyTicks.getValue()) {
            place();
        }
    }

    private void handleNormal() {
        if (onAir() || !snap.getValue()) {
            rotation = getRotation(blockPos, enumFacing);
            RotationManager.INSTANCE.setRotations(rotation, rotateSpeed.getValue());
        }
        place();
    }

    private void place() {
        if (!onAir() || blockPos == null || enumFacing == null || !isValidStack(mc.player.getInventory().getSelectedItem())) {
            return;
        }

        if (switch (raytrace.getValue()) {
            case Hypixel -> !RotationManager.INSTANCE.isDone();
            case Normal -> !RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), blockPos);
            case Strict -> !RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), blockPos, enumFacing);
        }) {
            return;
        }

        InteractionResult result = mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new BlockHitResult(getVec3(blockPos, enumFacing), enumFacing, blockPos, false));
        if (result.consumesAction()) {
            if (swingHand.getValue()) mc.player.swing(InteractionHand.MAIN_HAND);
            else mc.getConnection().send(new ServerboundSwingPacket(InteractionHand.MAIN_HAND));

            if (render.getValue()) {
                renderBoxes.add(new RenderBox(new AABB(blockPos.offset(enumFacing.getUnitVec3i())), lineColor.getValue(), sideColor.getValue(), System.currentTimeMillis(), fade.getValue(), shrink.getValue()));
            }
        }
    }

    private int getYLevel() {
        if (!mc.options.keyJump.isDown() && MoveUtils.isMoving() && mc.player.fallDistance <= 0.25F && telly.getValue()) {
            return yLevel;
        }
        return Mth.floor(mc.player.getY()) - 1;
    }

    private void getBlockInfo() {
        blockPos = null;
        enumFacing = null;

        Vec3 baseVec = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(baseVec.x, getYLevel(), baseVec.z);
        int baseX = base.getX();
        int baseZ = base.getZ();

        if (!onAir()) {
            return;
        }

        if (checkBlock(baseVec, base)) {
            return;
        }

        for (int d = 1; d <= 6; d++) {
            if (checkBlock(baseVec, new BlockPos(baseX, getYLevel() - d, baseZ))) {
                return;
            }

            for (int x = 0; x <= d; x++) {
                for (int z = 0; z <= d - x; z++) {
                    int y = d - x - z;
                    for (int rev1 = 0; rev1 <= 1; rev1++) {
                        for (int rev2 = 0; rev2 <= 1; rev2++) {
                            BlockPos pos = new BlockPos(baseX + (rev1 == 0 ? x : -x), getYLevel() - y, baseZ + (rev2 == 0 ? z : -z));
                            if (checkBlock(baseVec, pos)) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isSolidAndNonInteractive(BlockState state, Level level, BlockPos pos) {
        return !state.getCollisionShape(level, pos).isEmpty() && state.getMenuProvider(level, pos) == null;
    }

    private boolean checkBlock(Vec3 baseVec, BlockPos pos) {
        if (!onAir()) {
            return false;
        }

        if (pos.getY() > getYLevel()) {
            return false;
        }

        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        for (Direction direction : Direction.values()) {
            Vec3 normal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            Vec3 hit = center.add(normal.scale(0.5));
            BlockPos baseBlockPos = pos.offset(direction.getStepX(), direction.getStepY(), direction.getStepZ());

            if (!isSolidAndNonInteractive(mc.level.getBlockState(baseBlockPos), mc.level, baseBlockPos)) {
                continue;
            }

            Vec3 relevant = hit.subtract(baseVec);
            if (relevant.lengthSqr() <= 4.5D * 4.5D && relevant.dot(normal) >= 0.0D) {
                if (direction.getOpposite() == Direction.UP && MoveUtils.isMoving() && !mc.options.keyJump.isDown()) {
                    continue;
                }

                blockPos = baseBlockPos;
                enumFacing = direction.getOpposite();
                return true;
            }
        }

        return false;
    }

    private Rot2f getRotation(BlockPos pos, Direction direction) {
        if (rotation == null) {
            return new Rot2f(Mth.wrapDegrees(mc.player.getYRot() - 135.0F), 82.0F);
        }

        if (!onAir() || pos == null || direction == null) {
            return rotation;
        }

        Rot2f calculated = RotationUtils.calculate(pos, direction);
        Float[] yawArray = {
                -135F,
                -90F,
                -45F,
                0F,
                45F,
                90F,
                135F,
                180F,
                calculated.getYaw()
        };
        Arrays.sort(yawArray, (a, b) ->
                Float.compare(
                        Math.abs(Mth.wrapDegrees(mc.player.getYRot() - 180 - a)),
                        Math.abs(Mth.wrapDegrees(mc.player.getYRot() - 180 - b))
                )
        );

        if (raytrace.is(RaytraceMode.Hypixel)) {
            return new Rot2f(yawArray[0], 82.0F);
        }

        float[] pitchArray = {75.0F, 82.0F, 87.0F};

        for (float yaw : yawArray) {
            for (float pitch : pitchArray) {
                Rot2f candidate = new Rot2f(yaw + MathUtils.getRandom(-0.3F, 0.3F), pitch + MathUtils.getRandom(-0.3F, 0.3F));
                boolean matches = raytrace.is(RaytraceMode.Normal) ? RaytraceUtils.overBlock(candidate, pos) : RaytraceUtils.overBlock(candidate, pos, direction);
                if (matches) {
                    return candidate;
                }
            }

            for (int pitch = -90; pitch < 90; pitch++) {
                Rot2f candidate = new Rot2f(yaw, pitch);
                boolean matches = raytrace.is(RaytraceMode.Normal) ? RaytraceUtils.overBlock(candidate, pos) : RaytraceUtils.overBlock(candidate, pos, direction);
                if (matches) {
                    return candidate;
                }
            }
        }

        return calculated;
    }

    private boolean onAir() {
        Vec3 baseVec = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(baseVec.x, getYLevel(), baseVec.z);
        return mc.level.getBlockState(base).canBeReplaced();
    }

    private Vec3 getVec3(BlockPos pos, Direction face) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        if (face != Direction.UP && face != Direction.DOWN) {
            y += 0.08;
        } else {
            x += MathUtils.getRandom(-0.3, 0.3);
            z += MathUtils.getRandom(-0.3, 0.3);
        }

        if (face == Direction.WEST || face == Direction.EAST) {
            z += MathUtils.getRandom(-0.3, 0.3);
        }

        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += MathUtils.getRandom(-0.3, 0.3);
        }

        return new Vec3(x, y, z);
    }

    private boolean isValidStack(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }

        String name = stack.getDisplayName().getString();
        if (name.contains("Click") || name.contains("点击")) {
            return false;
        }

        if (stack.getItem() instanceof StandingAndWallBlockItem) {
            return false;
        }

        Block block = ((BlockItem) stack.getItem()).getBlock();
        if (block instanceof FlowerBlock || block instanceof BushBlock || block instanceof NetherFungusBlock || block instanceof CropBlock) {
            return false;
        }

        return !(block instanceof SlabBlock) && !BLACKLISTED_BLOCKS.contains(block);
    }

    private static AABB getRenderBox(RenderBox boxes, double scale) {
        AABB renderBox = boxes.aabb;
        if (boxes.shrink()) {
            return AABB.ofSize(renderBox.getCenter(), renderBox.getXsize() * scale, renderBox.getYsize() * scale, renderBox.getZsize() * scale);
        }
        return renderBox;
    }


    private record RenderBox(AABB aabb, Color lineColor, Color sideColor, long startTime, boolean fade,
                             boolean shrink) {
    }

}
