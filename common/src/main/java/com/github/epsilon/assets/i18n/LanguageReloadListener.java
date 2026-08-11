package com.github.epsilon.assets.i18n;

import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class LanguageReloadListener implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(PreparationBarrier barrier, ResourceManager resourceManager,
                                          ProfilerFiller preparationProfiler, ProfilerFiller applyProfiler,
                                          Executor preparationExecutor, Executor applyExecutor) {
        return CompletableFuture.completedFuture(null)
                .thenCompose(barrier::wait)
                .thenRunAsync(() -> {
                    EpsilonLanguageManager.INSTANCE.reload(resourceManager);
                }, applyExecutor);
    }

}
