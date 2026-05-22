package com.github.epsilon.mixins;

import com.github.epsilon.Constants;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.ClickEvent;
import com.github.epsilon.events.impl.LevelUpdateEvent;
import com.github.epsilon.events.impl.StartUseItemEvent;
import com.github.epsilon.events.impl.TickEvent;
import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.modules.impl.ClientSetting;
import com.github.epsilon.modules.impl.player.MultiTask;
import com.github.epsilon.modules.impl.player.UseCooldown;
import com.github.epsilon.modules.impl.render.HandsView;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MixinMinecraft {

    @Shadow
    private int rightClickDelay;

    @Inject(method = "tick", at = @At("HEAD"))
    private void onPreTick(CallbackInfo info) {
        EventBus.INSTANCE.post(new TickEvent.Pre());
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onPostTick(CallbackInfo info) {
        EventBus.INSTANCE.post(new TickEvent.Post());
    }

    @ModifyArg(method = "updateTitle", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;setTitle(Ljava/lang/String;)V"))
    private String onUpdateTitle(String title) {
        return ClientSetting.INSTANCE.customTitle.getValue() ? Constants.NAME + " " + Constants.VERSION + " for " + title : title;
    }

    @Inject(method = "handleKeybinds", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z", ordinal = 0, shift = At.Shift.BEFORE), cancellable = true)
    private void onHandleKeybinds(CallbackInfo ci) {
        ClickEvent event = EventBus.INSTANCE.post(new ClickEvent());
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/InteractionHand;values()[Lnet/minecraft/world/InteractionHand;"), cancellable = true)
    private void onStartUseItemBeforeHands(CallbackInfo ci) {
        if (EventBus.INSTANCE.post(new StartUseItemEvent()).isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;isItemEnabled(Lnet/minecraft/world/flag/FeatureFlagSet;)Z"))
    private void onStartUseItem(CallbackInfo ci) {
        UseCooldown useCooldown = UseCooldown.INSTANCE;
        if (useCooldown.isEnabled()) {
            rightClickDelay = useCooldown.cooldown.getValue();
        }
    }

    @WrapOperation(method = "continueAttack", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"))
    private boolean attackMultiTask(LocalPlayer instance, Operation<Boolean> original) {
        return original.call(instance) && !MultiTask.INSTANCE.isEnabled();
    }

    @WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;isDestroying()Z"))
    private boolean useMultiTask(MultiPlayerGameMode instance, Operation<Boolean> original) {
        return original.call(instance) && !MultiTask.INSTANCE.isEnabled();
    }

    @Inject(method = "handleKeybinds", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;keyUse:Lnet/minecraft/client/KeyMapping;", ordinal = 0, opcode = Opcodes.GETFIELD))
    private void onItemUseMouseHandle(CallbackInfo ci) {
        HandsView handsView = HandsView.INSTANCE;
        Minecraft mc = (Minecraft) (Object) this;
        if (handsView.isEnabled() && handsView.swingWhileUsing.getValue()
                && mc.options.keyAttack.isDown()
                && mc.options.keyAttack.consumeClick()
                && (!handsView.onlyOnBlock.getValue() || mc.hitResult.getType() == HitResult.Type.BLOCK)
        ) {
            mc.player.swing(InteractionHand.MAIN_HAND, false); // Use this method can swing client side.
        }
    }

    @Inject(method = "updateLevelInEngines(Lnet/minecraft/client/multiplayer/ClientLevel;Z)V", at = @At("HEAD"))
    private void onUpdateLevelInEngines(ClientLevel level, boolean stopSound, CallbackInfo ci) {
        EventBus.INSTANCE.post(new LevelUpdateEvent());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void onClose(CallbackInfo ci) {
        LuminRenderSystem.destroyAll();
    }

}
