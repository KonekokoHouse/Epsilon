package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.KeyboardInputEvent;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.github.epsilon.Constants.mc;

@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        Input input = (Input) (Object) this;
        boolean sprinting = mc.player != null && mc.player.isSprinting();
        KeyboardInputEvent event = EventBus.INSTANCE.post(new KeyboardInputEvent(
                input.forwardImpulse, input.leftImpulse, input.jumping, input.shiftKeyDown, sprinting));
        event.applyTo(input);
        if (mc.player != null) mc.player.setSprinting(event.isSprint());
    }

}
