package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.SubmitNodeCollectionAccessor;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(SubmitNodeStorage.class)
public abstract class MixinSubmitNodeStorage implements SubmitNodeCollectionAccessor {

    @Shadow
    public abstract SubmitNodeCollection order(int order);

    @Override
    public SubmitNodeCollection epsilon$getSubmitNodeCollection() {
        return order(0);
    }

}
