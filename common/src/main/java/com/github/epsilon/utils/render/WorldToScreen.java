package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;

public final class WorldToScreen {

    private static final float REFERENCE_PIXELS_PER_WORLD_UNIT = 20.0f;
    private static final Matrix4f viewMatrix = new Matrix4f();
    private static final Matrix4f projectionMatrix = new Matrix4f();
    private static Vec3 cameraPosition = Vec3.ZERO;

    private WorldToScreen() {
    }

    /** 每个世界渲染帧保存 1.21.1 传入 LevelRenderer 的相机矩阵。 */
    public static void update(Camera camera, Matrix4f modelView, Matrix4f projection) {
        cameraPosition = camera.getPosition();
        viewMatrix.set(modelView);
        projectionMatrix.set(projection);
    }

    public static Vector3f calcWorld2ScreenRaw(Vec3 pos) {
        Vector3f viewPos = pos.subtract(cameraPosition).toVector3f();
        viewMatrix.transformPosition(viewPos);
        float depth = -viewPos.z;
        Vector3f projected = projectionMatrix.transformProject(viewPos, new Vector3f());

        float width = LuminRenderSystem.getScaledWidth();
        float height = LuminRenderSystem.getScaledHeight();
        return projected.set(
                (projected.x + 1.0f) * 0.5f * width,
                (1.0f - projected.y) * 0.5f * height,
                depth
        );
    }

    @Nullable
    public static Vector3f calcWorld2Screen(Vec3 pos) {
        Vector3f projected = calcWorld2ScreenRaw(pos);
        return projected.z < GameRenderer.PROJECTION_Z_NEAR ? null : projected;
    }

    public static float calcScale(Vec3 pos) {
        Vector3f viewPos = pos.subtract(cameraPosition).toVector3f();
        float depth = -viewMatrix.transformPosition(viewPos).z;
        if (depth < GameRenderer.PROJECTION_Z_NEAR) return 0.0f;

        return LuminRenderSystem.getScaledHeight() * projectionMatrix.m11()
                / (2.0f * depth * REFERENCE_PIXELS_PER_WORLD_UNIT);
    }
}
