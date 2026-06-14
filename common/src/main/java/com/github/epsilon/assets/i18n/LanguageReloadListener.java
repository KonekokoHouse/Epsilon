package com.github.epsilon.assets.i18n;

import com.github.epsilon.assets.holders.TextureCacheHolder;
import com.github.epsilon.assets.holders.TranslateHolder;
import com.github.epsilon.gui.dsl.PanelUiTree;
import net.minecraft.server.packs.resources.PreparableReloadListener;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public class LanguageReloadListener implements PreparableReloadListener {

    @Override
    public CompletableFuture<Void> reload(SharedState sharedState, Executor exectutor, PreparationBarrier barrier, Executor applyExectutor) {
        return CompletableFuture.completedFuture(null)
                .thenCompose(barrier::wait)
                .thenRunAsync(() -> {

                    TranslateHolder.INSTANCE.refresh();
                    PanelUiTree.clearMemoCache();
                    TextureCacheHolder.INSTANCE.clearCache();

                }, applyExectutor);
    }

}
