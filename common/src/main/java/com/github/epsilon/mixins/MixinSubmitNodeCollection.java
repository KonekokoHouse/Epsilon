package com.github.epsilon.mixins;

import com.github.epsilon.interfaces.SubmitNodeCollectionAccessor;
import net.minecraft.client.renderer.SubmitNodeCollection;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SubmitNodeCollection.class)
public class MixinSubmitNodeCollection implements SubmitNodeCollectionAccessor {

    @Override
    public SubmitNodeCollection epsilon$getSubmitNodeCollection() {
        return (SubmitNodeCollection) (Object) this;
    }

}
