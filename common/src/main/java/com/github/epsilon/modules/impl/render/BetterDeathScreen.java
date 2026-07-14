package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.bus.EventPriority;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.KeyPressEvent;
import com.github.epsilon.events.impl.OpenScreenEvent;
import com.github.epsilon.events.impl.Render2DEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.KeybindSetting;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import org.lwjgl.glfw.GLFW;

public class BetterDeathScreen extends Module {

    public static final BetterDeathScreen INSTANCE = new BetterDeathScreen();
    public static boolean freecamActive = false;

    private DeathScreen savedDeathScreen = null;

    private double lastMouseX, lastMouseY;
    private boolean mouseInitialized = false;

    private boolean pendingChat = false;
    private String chatPrefix = "";

    private final KeybindSetting freecamKey = keybindSetting("Freecam Key", GLFW.GLFW_KEY_E);

    private BetterDeathScreen() {
        super("Better Death Screen", Category.PLAYER);
    }

    @Override
    protected void onEnable() {
        freecamActive = false;
        savedDeathScreen = null;
    }

    @Override
    protected void onDisable() {
        if (freecamActive) exitFreecam();
    }

    @EventHandler(priority = EventPriority.HIGH)
    private void onKey(KeyPressEvent event) {
        if (!isEnabled()) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        // 可配置键位切换 freecam
        if (event.getKey() == freecamKey.getValue()) {
            if ((mc.screen instanceof DeathScreen || freecamActive) && !(mc.screen instanceof ChatScreen)) {
                toggleFreecam();
                event.cancel();
            }
            return;
        }

        // 按聊天或命令键打开聊天栏
        if ((mc.screen instanceof DeathScreen || freecamActive) && !(mc.screen instanceof ChatScreen)) {
            if (mc.options.keyChat.matches(event.getKeyEvent())) {
                pendingChat = true;
                chatPrefix = "";
                event.cancel();
            } else if (mc.options.keyCommand.matches(event.getKeyEvent())) {
                mc.setScreen(new ChatScreen("", false));
                event.cancel();
            }
        }
    }

    // 阻止死亡界面在 freecam 期间被自动重开
    @EventHandler
    private void onOpenScreen(OpenScreenEvent event) {
        if (freecamActive && event.getScreen() instanceof DeathScreen) {
            event.cancel();
        }
    }

    public void toggleFreecam() {
        if (mc.player == null || !mc.player.isDeadOrDying()) return;
        if (freecamActive) exitFreecam();
        else enterFreecam();
    }

    private void enterFreecam() {
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        if (freeCamera.isEnabled()) return;

        // 保存死亡界面，然后清空屏幕
        if (mc.screen instanceof DeathScreen) {
            savedDeathScreen = (DeathScreen) mc.screen;
        }
        mc.setScreen(null);

        // 强制锁定鼠标并隐藏指针
        hideCursor();

        // 初始化鼠标位置
        long window = mc.getWindow().handle();
        double[] xpos = new double[1], ypos = new double[1];
        GLFW.glfwGetCursorPos(window, xpos, ypos);
        lastMouseX = xpos[0];
        lastMouseY = ypos[0];
        mouseInitialized = true;

        freeCamera.toggle();
        freecamActive = true;
    }

    private void exitFreecam() {
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        if (!freeCamera.isEnabled()) {
            freecamActive = false;
            return;
        }

        // 恢复鼠标可见
        showCursor();
        mouseInitialized = false;

        freeCamera.toggle();

        // 恢复死亡界面
        if (savedDeathScreen != null && mc.player != null && mc.player.isDeadOrDying()) {
            mc.setScreen(savedDeathScreen);
            savedDeathScreen = null;
        }

        freecamActive = false;
    }

    @EventHandler
    private void onRender(Render2DEvent.HUD event) {
        if (!freecamActive || !mouseInitialized) return;
        // 动态切换鼠标显示状态
        if (mc.screen instanceof ChatScreen) {
            showCursor();
        } else {
            hideCursor();
        }
        // 打开聊天栏时不转动视角
        if (mc.screen instanceof ChatScreen) return;
        FreeCamera freeCamera = FreeCamera.INSTANCE;
        if (!freeCamera.isEnabled()) return;

        long window = mc.getWindow().handle();
        double[] xpos = new double[1], ypos = new double[1];
        GLFW.glfwGetCursorPos(window, xpos, ypos);

        double dx = xpos[0] - lastMouseX;
        double dy = ypos[0] - lastMouseY;
        if (dx != 0 || dy != 0) {
            double sensitivity = mc.options.sensitivity().get();
            freeCamera.changeLookDirection(dx * sensitivity, dy * sensitivity);
        }
        lastMouseX = xpos[0];
        lastMouseY = ypos[0];
    }

    // 复活自动退出
    @EventHandler
    private void onTick(ClientTickEvent.Post event) {
        if (nullCheck()) return;

        if (pendingChat) {
            pendingChat = false;
            if (mc.player.isDeadOrDying()) {
                mc.setScreen(new ChatScreen(chatPrefix, false));
            }
        }
        if (!freecamActive) return;
        if (!mc.player.isDeadOrDying()) {
            exitFreecam();
            if (mc.screen instanceof DeathScreen) mc.setScreen(null);
        }
    }

    private void showCursor() {
        long window = mc.getWindow().handle();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    }

    private void hideCursor() {
        long window = mc.getWindow().handle();
        GLFW.glfwSetInputMode(window, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    }
}
