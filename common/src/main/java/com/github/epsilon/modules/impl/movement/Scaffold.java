package com.github.epsilon.modules.impl.movement;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.events.impl.SendPositionEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.math.MathUtils;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.player.MoveUtils;
import com.github.epsilon.utils.render.Render3DUtils;
import com.github.epsilon.utils.render.animation.Easing;
import com.github.epsilon.utils.rotation.RaytraceUtils;
import com.github.epsilon.utils.rotation.Rot2f;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.world.BlockUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
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

    private enum Mode {
        GodBridge,
        TellyBridge,
    }

    private enum SwapMode {
        None,
        Normal,
        InvSwitch,
        Silent,
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.TellyBridge);
    private final EnumSetting<SwapMode> swapMode = enumSetting("Swap Mode", SwapMode.Normal);
    private final BoolSetting swapBack = boolSetting("Swap Back", true, () -> swapMode.is(SwapMode.Normal));
    private final BoolSetting swingHand = boolSetting("Swing Hand", true);
    private final IntSetting tellyTick = intSetting("Telly Tick", 0, 0, 8, 1, () -> mode.is(Mode.TellyBridge));
    private final BoolSetting keepY = boolSetting("Keep Y", true, () -> mode.is(Mode.TellyBridge));
    private final IntSetting rotationSpeed = intSetting("Rotation Speed", 10, 1, 10, 1);
    private final IntSetting rotationBackSpeed = intSetting("Rotation Back Speed", 10, 0, 10, 1, () -> mode.is(Mode.TellyBridge));
    private final BoolSetting sideCheck = boolSetting("Side Check", false);
    private final BoolSetting safeWalk = boolSetting("Safe Walk", true);

    private final BoolSetting render = boolSetting("Render", true);
    private final BoolSetting fade = boolSetting("Fade", false, render::getValue);
    private final IntSetting fadeTime = intSetting("Fade Time", 500, 0, 3000, 50, () -> render.getValue() && fade.getValue());
    private final BoolSetting shrink = boolSetting("Shrink", true, render::getValue);
    private final ColorSetting sideColor = colorSetting("Side Color", new Color(255, 183, 197, 100), render::getValue);
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 105, 180), render::getValue);

    private int yLevel;
    private int airTicks;

    private boolean shouldSwapBack;

    private BlockInfo blockInfo;

    private final List<RenderBox> renderBoxes = new ArrayList<>();

    @Override
    protected void onEnable() {
        blockInfo = null;
        shouldSwapBack = false;
    }

    @Override
    protected void onDisable() {
        blockInfo = null;
        if (shouldSwapBack) {
            InvUtils.swapBack();
        }
    }

    @EventHandler
    private void onTickPre(TickEvent.Pre event) {
        if (nullCheck()) return;

        updateBlockInfo();

        if (mode.getValue() == Mode.TellyBridge) {
            if (mc.player.onGround()) {
                yLevel = Mth.floor(mc.player.getY()) - 1;
                airTicks = 0;
                blockInfo = null;
                Rot2f rotation = new Rot2f(mc.player.getYRot(), mc.player.getXRot());
                RotationManager.INSTANCE.setRotations(rotation, rotationBackSpeed.getValue());
            } else {
                if (airTicks >= tellyTick.getValue() && blockInfo != null) {
                    FindItemResult item = findItem();
                    if (item.found()) {
                        RotationManager.INSTANCE.setRotations(getRotation(blockInfo), rotationSpeed.getValue());
                        place(item);
                    }
                }
                airTicks++;
            }
        } else if (blockInfo != null) {
            FindItemResult item = findItem();
            if (item.found()) {
                RotationManager.INSTANCE.setRotations(getRotation(blockInfo), rotationSpeed.getValue());
                place(item);
            }
        }
    }

    @EventHandler
    private void onMotion(SendPositionEvent event) {
        if (safeWalk.getValue() && mode.is(Mode.GodBridge)) {
            mc.options.keyShift.setDown(mc.player.onGround() && SafeWalk.INSTANCE.isOnBlockEdge(0.3F));
        }
    }

    @EventHandler
    private void onMoveInput(KeyboardInputEvent event) {
        if (mc.player.onGround() && MoveUtils.isMoving() && mode.is(Mode.TellyBridge) && !mc.options.keyJump.isDown()) {
            event.setJump(true);
        }
    }

    private int getYLevel() {
        if (keepY.getValue() && !mc.options.keyJump.isDown() && MoveUtils.isMoving() && mode.is("TellyBridge") && mc.player.fallDistance <= 0.25) {
            return yLevel;
        } else {
            return Mth.floor(mc.player.getY()) - 1;
        }
    }

    private static AABB getRenderBox(RenderBox boxes, double scale) {
        AABB renderBox = boxes.aabb;
        if (boxes.shrink()) {
            double centerX = renderBox.minX + (renderBox.maxX - renderBox.minX) / 2.0;
            double centerY = renderBox.minY + (renderBox.maxY - renderBox.minY) / 2.0;
            double centerZ = renderBox.minZ + (renderBox.maxZ - renderBox.minZ) / 2.0;

            double dx = (renderBox.maxX - renderBox.minX) / 2.0 * scale;
            double dy = (renderBox.maxY - renderBox.minY) / 2.0 * scale;
            double dz = (renderBox.maxZ - renderBox.minZ) / 2.0 * scale;

            renderBox = new AABB(centerX - dx, centerY - dy, centerZ - dz, centerX + dx, centerY + dy, centerZ + dz);
        }
        return renderBox;
    }

    public static Vec3 getVec3(BlockPos pos, Direction face) {
        double x = (double) pos.getX() + 0.5;
        double y = (double) pos.getY() + 0.5;
        double z = (double) pos.getZ() + 0.5;
        if (face == Direction.UP || face == Direction.DOWN) {
            x += MathUtils.getRandom(0.3, -0.3);
            z += MathUtils.getRandom(0.3, -0.3);
        } else {
            y += MathUtils.getRandom(0.3, -0.3);
        }
        if (face == Direction.WEST || face == Direction.EAST) {
            z += MathUtils.getRandom(0.3, -0.3);
        }
        if (face == Direction.SOUTH || face == Direction.NORTH) {
            x += MathUtils.getRandom(0.3, -0.3);
        }
        return new Vec3(x, y, z);
    }

    private boolean validItem(ItemStack itemStack, BlockPos pos) {
        if (!(itemStack.getItem() instanceof BlockItem blockItem)) return false;

        Block block = blockItem.getBlock();

        if (block instanceof TntBlock) return false;

        if (!Block.isShapeFullBlock(block.defaultBlockState().getCollisionShape(mc.level, pos))) return false;
        return !(block instanceof FallingBlock) || !FallingBlock.isFree(mc.level.getBlockState(pos));
    }

    private FindItemResult findItem() {
        switch (swapMode.getValue()) {
            case SwapMode.None -> {
                if (InvUtils.testInOffHand(itemStack -> validItem(itemStack, blockInfo.position))) {
                    return new FindItemResult(40, mc.player.getOffhandItem().getCount(), mc.player.getOffhandItem().getMaxStackSize());
                }
                if (InvUtils.testInMainHand(itemStack -> validItem(itemStack, blockInfo.position))) {
                    return new FindItemResult(mc.player.getInventory().getSelectedSlot(), mc.player.getMainHandItem().getCount(), mc.player.getMainHandItem().getMaxStackSize());
                }
                return new FindItemResult(-1, 0, 0);
            }
            case SwapMode.InvSwitch -> {
                return InvUtils.find(itemStack -> validItem(itemStack, blockInfo.position));
            }
            default -> {
                return InvUtils.findInHotbar(itemStack -> validItem(itemStack, blockInfo.position));
            }
        }
    }

    private void place(FindItemResult item) {
        if (!onAir()) return;
        if (!BlockUtils.canPlaceAt(blockInfo.blockPos)) return;

        boolean hasRotated = RaytraceUtils.overBlock(RotationManager.INSTANCE.getRotation(), blockInfo.dir, blockInfo.position, sideCheck.getValue());
        if (hasRotated) {
            switch (swapMode.getValue()) {
                case Normal -> {
                    boolean should = swapBack.getValue();
                    InvUtils.swap(item.slot(), should);
                    shouldSwapBack = should;
                }
                case Silent -> InvUtils.swap(item.slot(), true);
                case InvSwitch -> InvUtils.invSwap(item.slot());
            }

            InteractionResult result = mc.gameMode.useItemOn(mc.player, item.getHand(), new BlockHitResult(getVec3(blockInfo.position, blockInfo.dir), blockInfo.dir, blockInfo.position, false));

            if (result.consumesAction()) {
                if (swingHand.getValue()) {
                    mc.player.swing(item.getHand());
                } else {
                    mc.getConnection().send(new ServerboundSwingPacket(item.getHand()));
                }

                if (render.getValue()) {
                    renderBoxes.add(new RenderBox(new AABB(blockInfo.blockPos), lineColor.getValue(), sideColor.getValue(), System.currentTimeMillis(), fade.getValue(), shrink.getValue()));
                }
            }

            switch (swapMode.getValue()) {
                case Silent -> InvUtils.swapBack();
                case InvSwitch -> InvUtils.invSwapBack();
            }
        }
    }

    private void updateBlockInfo() {
        Vec3 baseVec = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(baseVec.x, getYLevel(), baseVec.z);
        int baseX = base.getX();
        int baseZ = base.getZ();
        if (mc.level.getBlockState(base).entityCanStandOn(mc.level, base, mc.player)) return;
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
                            if (checkBlock(baseVec, new BlockPos(baseX + (rev1 == 0 ? x : -x), getYLevel() - y, baseZ + (rev2 == 0 ? z : -z)))) {
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean checkBlock(Vec3 baseVec, BlockPos pos) {
        if (!(mc.level.getBlockState(pos).getBlock() instanceof AirBlock) /*&& !(mc.level.getBlockState(pos).getBlock() instanceof FluidBlock)*/) {
            return false;
        }

        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        for (Direction dir : Direction.values()) {
            Vec3 hit = center.add(new Vec3(dir.getUnitVec3i()).scale(0.5));
            Vec3i baseBlock = pos.offset(dir.getUnitVec3i());
            BlockPos baseBlockPos = new BlockPos(baseBlock.getX(), baseBlock.getY(), baseBlock.getZ());

            if (!mc.level.getBlockState(baseBlockPos).entityCanStandOn(mc.level, baseBlockPos, mc.player)) continue;

            Vec3 relevant = hit.subtract(baseVec);
            if (relevant.lengthSqr() <= 4.5 * 4.5 && relevant.dot(new Vec3(dir.getUnitVec3i())) >= 0) {
                if (dir.getOpposite() == Direction.UP && mode.is(Mode.GodBridge) && MoveUtils.isMoving() && !mc.options.keyJump.isDown()) {
                    continue;
                }
                blockInfo = new BlockInfo(pos, new BlockPos(baseBlock), dir.getOpposite());
                return true;
            }
        }
        return false;
    }

    private Rot2f getRotation(BlockInfo blockCache) {
        Rot2f calculate = onAir() ? RotationUtils.calculate(blockCache.position, blockCache.dir) : RotationUtils.calculate(blockCache.position.getCenter());
        Rot2f reverseYaw = new Rot2f(Mth.wrapDegrees(mc.player.getYRot() - 180), calculate.getPitch());
        boolean hasRotated = RaytraceUtils.overBlock(reverseYaw, blockCache.position, blockCache.dir);
        if (hasRotated) return reverseYaw;
        else return calculate;
    }

    private boolean onAir() {
        Vec3 baseVec = mc.player.getEyePosition();
        BlockPos base = BlockPos.containing(baseVec.x, getYLevel(), baseVec.z);
        return mc.level.getBlockState(base).getBlock() instanceof AirBlock || mc.level.getBlockState(base).getBlock() instanceof WaterloggedTransparentBlock;
    }

    private record BlockInfo(BlockPos blockPos, BlockPos position, Direction dir) {
    }

    private record RenderBox(AABB aabb, Color lineColor, Color sideColor, long startTime, boolean fade,
                             boolean shrink) {
    }

}
