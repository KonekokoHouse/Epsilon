package com.github.epsilon.utils.client;

import net.minecraft.client.gui.screens.LoadingOverlay;

import static com.github.epsilon.Constants.mc;

public class ClientUtils {

    /**
     * 判断客户端是否仍处于资源加载或重载阶段。
     *
     * @return 仍在加载时返回 true
     */
    public static boolean isLoading() {
        return !mc.isGameLoadFinished() || mc.getOverlay() instanceof LoadingOverlay;
    }

}
