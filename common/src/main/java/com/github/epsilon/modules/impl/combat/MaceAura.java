package com.github.epsilon.modules.impl.combat;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.PlayerTickEvent;
import com.github.epsilon.managers.Managers;
import com.github.epsilon.managers.impl.target.TargetRequest;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.settings.impl.IntSetting;
import com.github.epsilon.utils.player.FindItemResult;
import com.github.epsilon.utils.player.InvUtils;
import com.github.epsilon.utils.rotation.Priority;
import com.github.epsilon.utils.rotation.RotationUtils;
import com.github.epsilon.utils.timer.TimerUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Moli
 * 用于无反香草服，如LBLT
 */

public class MaceAura extends Module {

    public static final MaceAura INSTANCE = new MaceAura();

    private MaceAura() {
        super("Mace Aura", Category.COMBAT);
    }

    private enum AttackMode {
        Normal,
        Mace
    }

    private enum TargetPriority {
        Distance,
        Angle,
        Health
    }

    private final EnumSetting<AttackMode> mode = enumSetting("Mode", AttackMode.Mace);
    private final EnumSetting<TargetPriority> priority = enumSetting("Priority", TargetPriority.Distance);
    private final DoubleSetting range = doubleSetting("Range", 3.0, 1.0, 6.0, 0.1);
    private final DoubleSetting moveDistance = doubleSetting("Move Distance", 8.0, 1.0, 20, 0.1);
    private final BoolSetting paperServer = boolSetting("Paper Server", true);
    private final DoubleSetting vclip = doubleSetting("VClip", 10, 1.0, 512, 1.0);
    private final BoolSetting damageOverride = boolSetting("Damage VClip", true);
    private final DoubleSetting overrideVClip = doubleSetting("Override VClip", 30, 1.0, 512, 1.0);
    private final BoolSetting swingHand = boolSetting("Swing Hand", false);
    private final BoolSetting cooldown = boolSetting("Cooldown", true);
    private final DoubleSetting cooldownBase = doubleSetting("Cooldown Base", 0.75, 0.1, 1.0, 0.05, cooldown::getValue);
    private final IntSetting attackDelay = intSetting("Attack Delay", 50, 1, 2000, 1, () -> !cooldown.getValue());
    private final BoolSetting players = boolSetting("Players", true);
    private final BoolSetting animals = boolSetting("Animals", false);
    private final BoolSetting mobs = boolSetting("Mobs", false);
    private final BoolSetting villagers = boolSetting("Villagers", false);
    private final BoolSetting slimes = boolSetting("Slimes", false);

    public LivingEntity target;
    private final TimerUtils attackTimer = new TimerUtils();
    private boolean rotationReady;

    @Override
    protected void onEnable() {
        target = null;
        attackTimer.reset();
    }

    @Override
    protected void onDisable() {
        target = null;
        rotationReady = false;
    }

    @EventHandler
    public void onTick(PlayerTickEvent e) {
        updateTarget();
        if (target == null) {
            return;
        }

        Managers.ROTATION.setRotations(RotationUtils.getRotationsToEntity(target), 180, Priority.Medium);

        if (!isReadyToAttack()) return;

        doAura();
    }

    private void updateTarget() {
        List<LivingEntity> candidates = new ArrayList<>();
        double rangeValue = range.getValue();

        for (LivingEntity entity : Managers.TARGET.acquireTargets(
                TargetRequest.of(
                        rangeValue,
                        360.0f,
                        players.getValue(),
                        mobs.getValue(),
                        animals.getValue(),
                        villagers.getValue(),
                        true,
                        64
                ))) {
            if (!isValidTarget(entity)) continue;
            candidates.add(entity);
        }

        if (priority.is(TargetPriority.Distance)) {
            candidates.sort((a, b) -> Double.compare(RotationUtils.getEyeDistanceToEntity(a), RotationUtils.getEyeDistanceToEntity(b)));
        } else if (priority.is(TargetPriority.Angle)) {
            candidates.sort((a, b) -> Double.compare(getAngleScore(a), getAngleScore(b)));
        } else if (priority.is(TargetPriority.Health)) {
            candidates.sort((a, b) -> Float.compare(a.getHealth(), b.getHealth()));
        }

        target = candidates.isEmpty() ? null : candidates.getFirst();
    }

    private double getAngleScore(LivingEntity entity) {
        Vec3 hitVec = getClosestPointOnBB(entity.getBoundingBox(), mc.player.getEyePosition());
        var angle = RotationUtils.calculate(hitVec);
        float yawDiff = Math.abs(Mth.wrapDegrees(angle.getYaw() - mc.player.getYRot()));
        float pitchDiff = Math.abs(Mth.wrapDegrees(angle.getPitch() - mc.player.getXRot()));
        return yawDiff * yawDiff + pitchDiff * pitchDiff;
    }

    private Vec3 getClosestPointOnBB(AABB box, Vec3 point) {
        double x = Mth.clamp(point.x(), box.minX, box.maxX);
        double y = Mth.clamp(point.y(), box.minY, box.maxY);
        double z = Mth.clamp(point.z(), box.minZ, box.maxZ);
        return new Vec3(x, y, z);
    }

    private boolean isValidTarget(LivingEntity entity) {
        if (slimes.getValue()) {
            return entity instanceof Slime;
        }
        return true;
    }

    private boolean isReadyToAttack() {
        if (cooldown.getValue()) {
            return mc.player.getAttackStrengthScale(0.5f) >= cooldownBase.getValue();
        }
        return attackTimer.passedMillise(attackDelay.getValue());
    }

    private void doAura() {
        if (target == null) return;
        if (RotationUtils.getEyeDistanceToEntity(target) > range.getValue()) return;

        if (mode.is(AttackMode.Normal)) {
            doNormalAttack();
        } else {
            doMaceAttack(vclip.getValue());
            if (damageOverride.getValue()) {
                doMaceAttack(overrideVClip.getValue());
            }
        }

        attackTimer.reset();
    }

    private void doMaceAttack(double vclip) {
        if (nullCheck()) return;

        int currentSlot = mc.player.getInventory().getSelectedSlot();
        boolean swappedInventory = false;

        FindItemResult hotbar = InvUtils.findInHotbar(Items.MACE);
        if (hotbar.found()) {
            if (hotbar.slot() != currentSlot) {
                InvUtils.swap(hotbar.slot(), true);
            }
        } else {
            FindItemResult inv = InvUtils.find(Items.MACE);
            if (!inv.found()) return;
            InvUtils.invSwap(inv.slot());
            swappedInventory = true;
            InvUtils.swap(mc.player.getInventory().getSelectedSlot(), true);
        }

        Vec3 startPos = mc.player.position();
        Vec3 targetPos = startPos.add(0.0, vclip, 0.0);
        // 何意味，，，
        if (paperServer.getValue()) {
            for (int i = 0; i < 4; i++) {
                mc.getConnection().send(new ServerboundMovePlayerPacket.StatusOnly(false, false));
            }
        }

        doTp(startPos, targetPos, moveDistance.getValue(), false, 20);
        sendMovePacket(startPos, false);
        attack();
        sendMovePacket(mc.player.getX(), mc.player.getY() + 1.0E-4, mc.player.getZ(), false);

        if (swappedInventory) {
            InvUtils.invSwapBack();
        }
        if (hotbar.found() && hotbar.slot() != currentSlot) {
            InvUtils.swapBack();
        }
    }

    private void doTp(Vec3 from, Vec3 to, double maxDistance, boolean onGround, int maxPackets) {
        double dist = from.distanceTo(to);
        if (dist <= 0.0 || maxDistance <= 0.0) {
            sendMovePacket(to, onGround);
            return;
        }
        int steps = (int) Math.ceil(dist / maxDistance);
        if (maxPackets > 0 && steps > maxPackets) steps = maxPackets;
        Vec3 delta = to.subtract(from);
        for (int i = 1; i <= steps; ++i) {
            double t = (double) i / (double) steps;
            Vec3 stepPos = from.add(delta.x() * t, delta.y() * t, delta.z() * t);
            sendMovePacket(stepPos, onGround);
        }
    }

    private void sendMovePacket(Vec3 pos, boolean onGround) {
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(pos.x(), pos.y(), pos.z(), onGround, false));
    }

    private void sendMovePacket(double x, double y, double z, boolean onGround) {
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(x, y, z, onGround, false));
    }

    private void doNormalAttack() {
        if (target == null) return;
        attack();
    }

    private void attack() {
        if (target == null) return;
        mc.gameMode.attack(mc.player, target);
        if (swingHand.getValue()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }
    }
}
