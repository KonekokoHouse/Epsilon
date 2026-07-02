package com.github.epsilon.utils.render;

import com.github.epsilon.graphics.LuminRenderSystem;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4d;
import org.joml.Vector4f;

import static com.github.epsilon.Constants.mc;

public class WorldToScreen {

    public static Vector3f getWorldPositionToScreen(Vec3 pos) {
        final var camera = mc.gameRenderer.getMainCamera();
        final Vector3f position = new Vec3(
                pos.x - camera.position().x,
                pos.y - camera.position().y,
                pos.z - camera.position().z
        ).toVector3f();

        CameraRenderState cameraState = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Matrix4f viewProjectionMatrix = new Matrix4f(cameraState.projectionMatrix).mul(cameraState.viewRotationMatrix);

        final int[] viewport = new int[]{0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()};
        final Vector4f out = new Vector4f();

        viewProjectionMatrix.project(position, viewport, out);
        out.y = viewport[3] - out.y;

        return new Vector3f(out.x, out.y, out.z);
    }

    public static Vector4d getEntityPositionsOn2D(Entity entity, float tickDelta) {
        final Vec3 position = interpolate(entity, tickDelta);
        final float width = entity.getBbWidth() / 2f;
        final float height = entity.getBbHeight() + (entity.isCrouching() ? 0.1f : 0.2f);

        final AABB boundingBox = new AABB(
                position.x - width, position.y, position.z - width,
                position.x + width, position.y + height, position.z + width
        );

        return projectAbsoluteAABBOn2D(boundingBox);
    }

    public static Vector4d projectAbsoluteAABBOn2D(AABB absoluteBoundingBox) {
        final int[] viewport = new int[]{0, 0, mc.getWindow().getWidth(), mc.getWindow().getHeight()};
        CameraRenderState cameraState = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState;
        Matrix4f viewProjectionMatrix = new Matrix4f(cameraState.projectionMatrix).mul(cameraState.viewRotationMatrix);
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();

        final Vector4d projection = projectEntity(viewport, viewProjectionMatrix, absoluteBoundingBox, cameraPos);
        if (projection == null) return null;

        double guiScale = LuminRenderSystem.getGuiScale();
        projection.x /= guiScale;
        projection.y /= guiScale;
        projection.z /= guiScale;
        projection.w /= guiScale;

        return projection;
    }

    public static Vector4d projectEntity(final int[] viewport, final Matrix4f matrix, final AABB absoluteBoundingBox, final Vec3 cameraPos) {
        final Vector4f out = new Vector4f();
        Vector4d result = null;
        boolean hasProjectedPoint = false;

        for (int i = 0; i < 8; i++) {
            Vector3f point = new Vector3f(
                    ((i & 1) == 0 ? (float) absoluteBoundingBox.minX : (float) absoluteBoundingBox.maxX) - (float) cameraPos.x,
                    ((i & 2) == 0 ? (float) absoluteBoundingBox.minY : (float) absoluteBoundingBox.maxY) - (float) cameraPos.y,
                    ((i & 4) == 0 ? (float) absoluteBoundingBox.minZ : (float) absoluteBoundingBox.maxZ) - (float) cameraPos.z
            );

            matrix.project(point, viewport, out);
            out.y = viewport[3] - out.y;

            if (!Float.isFinite(out.x) || !Float.isFinite(out.y) || !Float.isFinite(out.z)) {
                continue;
            }
            if (out.z < 0.0f || out.z > 1.0f) {
                continue;
            }

            hasProjectedPoint = true;

            if (result == null) {
                result = new Vector4d(out.x, out.y, out.x, out.y);
            } else {
                result.x = Math.min(result.x, out.x);
                result.y = Math.min(result.y, out.y);
                result.z = Math.max(result.z, out.x);
                result.w = Math.max(result.w, out.y);
            }
        }
        return hasProjectedPoint ? result : null;
    }

    public static Vector4d projectEntity(final int[] viewport, final Matrix4f matrix, final AABB absoluteBoundingBox) {
        Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
        return projectEntity(viewport, matrix, absoluteBoundingBox, cameraPos);
    }

    public static Vec3 interpolate(Entity entity, float tickDelta) {
        double x = Mth.lerp(tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp(tickDelta, entity.yOld, entity.getY());
        double z = Mth.lerp(tickDelta, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }

}