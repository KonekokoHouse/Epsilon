package com.github.epsilon.mixins;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.impl.KeyPressEvent;
import net.minecraft.client.KeyboardHandler;
import com.github.epsilon.gui.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class MixinKeyboardHandler {

    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void keyPress(long handle, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        KeyEvent keyEvent = new KeyEvent(key, scancode, modifiers);
        KeyPressEvent event = EventBus.INSTANCE.post(new KeyPressEvent(keyEvent, action));
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

}
