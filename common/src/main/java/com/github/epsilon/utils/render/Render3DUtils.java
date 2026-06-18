package com.github.epsilon.utils.render;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;

import static com.github.epsilon.Constants.mc;

public class Render3DUtils {

    private static final RenderPipeline FILLED_BOX_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/filled_box"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    private static final RenderPipeline LINES_PIPELINE = RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
            .withLocation(ResourceLocationUtils.getIdentifier("pipeline/lines"))
            .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, false))
            .withCull(false)
            .build();

    public static void drawFilledBox(BlockPos blockPos, Color color) {
        drawFilledBox(new AABB(blockPos), color.getRGB());
    }

    public static void drawFilledBox(AABB box, Color color) {
        drawFilledBox(box, color.getRGB());
    }

    public static void drawFilledBox(AABB box, int color) {
        drawFilledFadeBox(box, color, color);
    }

    public static void drawFilledSide(BlockPos blockPos, Color color, Direction direction) {
        drawFilledSide(new AABB(blockPos), color, direction);
    }

    public static void drawFilledSide(AABB box, Color color, Direction direction) {
        LuminImmediateRenderer.PosColorQuads builder = LuminImmediateRenderer.beginPosColorQuads(FILLED_BOX_PIPELINE);
        BoxVertices vertices = BoxVertices.of(box);
        Matrix4f matrix = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;
        int c = color.getRGB();

        switch (direction) {
            case DOWN -> quad(builder, matrix,
                    vertices.minX, vertices.minY, vertices.minZ, c,
                    vertices.maxX, vertices.minY, vertices.minZ, c,
                    vertices.maxX, vertices.minY, vertices.maxZ, c,
                    vertices.minX, vertices.minY, vertices.maxZ, c);
            case NORTH -> quad(builder, matrix,
                    vertices.minX, vertices.minY, vertices.minZ, c,
                    vertices.minX, vertices.maxY, vertices.minZ, c,
                    vertices.maxX, vertices.maxY, vertices.minZ, c,
                    vertices.maxX, vertices.minY, vertices.minZ, c);
            case EAST -> quad(builder, matrix,
                    vertices.maxX, vertices.minY, vertices.minZ, c,
                    vertices.maxX, vertices.maxY, vertices.minZ, c,
                    vertices.maxX, vertices.maxY, vertices.maxZ, c,
                    vertices.maxX, vertices.minY, vertices.maxZ, c);
            case SOUTH -> quad(builder, matrix,
                    vertices.minX, vertices.minY, vertices.maxZ, c,
                    vertices.maxX, vertices.minY, vertices.maxZ, c,
                    vertices.maxX, vertices.maxY, vertices.maxZ, c,
                    vertices.minX, vertices.maxY, vertices.maxZ, c);
            case WEST -> quad(builder, matrix,
                    vertices.minX, vertices.minY, vertices.minZ, c,
                    vertices.minX, vertices.minY, vertices.maxZ, c,
                    vertices.minX, vertices.maxY, vertices.maxZ, c,
                    vertices.minX, vertices.maxY, vertices.minZ, c);
            case UP -> quad(builder, matrix,
                    vertices.minX, vertices.maxY, vertices.minZ, c,
                    vertices.minX, vertices.maxY, vertices.maxZ, c,
                    vertices.maxX, vertices.maxY, vertices.maxZ, c,
                    vertices.maxX, vertices.maxY, vertices.minZ, c);
        }

        builder.end();
    }

    public static void drawFilledFadeBox(AABB box, int bottomColor, int topColor) {
        LuminImmediateRenderer.PosColorQuads builder = LuminImmediateRenderer.beginPosColorQuads(FILLED_BOX_PIPELINE);
        BoxVertices vertices = BoxVertices.of(box);
        Matrix4f matrix = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;

        quad(builder, matrix,
                vertices.minX, vertices.minY, vertices.minZ, bottomColor,
                vertices.minX, vertices.minY, vertices.maxZ, bottomColor,
                vertices.maxX, vertices.minY, vertices.maxZ, bottomColor,
                vertices.maxX, vertices.minY, vertices.minZ, bottomColor);

        quad(builder, matrix,
                vertices.minX, vertices.maxY, vertices.minZ, topColor,
                vertices.maxX, vertices.maxY, vertices.minZ, topColor,
                vertices.maxX, vertices.maxY, vertices.maxZ, topColor,
                vertices.minX, vertices.maxY, vertices.maxZ, topColor);

        quad(builder, matrix,
                vertices.minX, vertices.minY, vertices.minZ, bottomColor,
                vertices.maxX, vertices.minY, vertices.minZ, bottomColor,
                vertices.maxX, vertices.maxY, vertices.minZ, topColor,
                vertices.minX, vertices.maxY, vertices.minZ, topColor);

        quad(builder, matrix,
                vertices.maxX, vertices.minY, vertices.minZ, bottomColor,
                vertices.maxX, vertices.minY, vertices.maxZ, bottomColor,
                vertices.maxX, vertices.maxY, vertices.maxZ, topColor,
                vertices.maxX, vertices.maxY, vertices.minZ, topColor);

        quad(builder, matrix,
                vertices.minX, vertices.minY, vertices.maxZ, bottomColor,
                vertices.minX, vertices.maxY, vertices.maxZ, topColor,
                vertices.maxX, vertices.maxY, vertices.maxZ, topColor,
                vertices.maxX, vertices.minY, vertices.maxZ, bottomColor);

        quad(builder, matrix,
                vertices.minX, vertices.minY, vertices.minZ, bottomColor,
                vertices.minX, vertices.maxY, vertices.minZ, topColor,
                vertices.minX, vertices.maxY, vertices.maxZ, topColor,
                vertices.minX, vertices.minY, vertices.maxZ, bottomColor);

        builder.end();
    }

    public static void drawOutlineBox(PoseStack stack, BlockPos blockPos, Color color) {
        drawOutlineBox(stack, new AABB(blockPos), color);
    }

    public static void drawOutlineBox(PoseStack stack, BlockPos blockPos, Color color, float thickness) {
        drawOutlineBox(stack, new AABB(blockPos), color.getRGB(), thickness);
    }

    public static void drawOutlineBox(PoseStack stack, AABB box, Color color) {
        drawOutlineBox(stack, box, color.getRGB(), 2f);
    }

    public static void drawOutlineBox(PoseStack stack, AABB box, Color color, float thickness) {
        drawOutlineBox(stack, box, color.getRGB(), thickness);
    }

    public static void drawSideOutline(PoseStack stack, BlockPos blockPos, Color color, float thickness, Direction direction) {
        drawSideOutline(stack, new AABB(blockPos), color.getRGB(), thickness, direction);
    }

    public static void drawSideOutline(PoseStack stack, AABB box, Color color, float thickness, Direction direction) {
        drawSideOutline(stack, box, color.getRGB(), thickness, direction);
    }

    public static void drawSideOutline(PoseStack stack, AABB box, int color, float thickness, Direction direction) {
        LuminImmediateRenderer.Lines builder = LuminImmediateRenderer.beginLines(LINES_PIPELINE);
        BoxVertices vertices = BoxVertices.of(box);
        PoseStack.Pose entry = stack.last();
        Matrix4f matrix = entry.pose();

        switch (direction) {
            case UP -> {
                vertexLine(builder, matrix, entry, vertices.minX, vertices.maxY, vertices.minZ, vertices.maxX, vertices.maxY, vertices.minZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.maxY, vertices.minZ, vertices.maxX, vertices.maxY, vertices.maxZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.maxY, vertices.maxZ, vertices.minX, vertices.maxY, vertices.maxZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.minX, vertices.maxY, vertices.maxZ, vertices.minX, vertices.maxY, vertices.minZ, color, thickness);
            }
            case DOWN -> {
                vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.minZ, vertices.maxX, vertices.minY, vertices.minZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.minZ, vertices.maxX, vertices.minY, vertices.maxZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.maxZ, vertices.minX, vertices.minY, vertices.maxZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.maxZ, vertices.minX, vertices.minY, vertices.minZ, color, thickness);
            }
            case EAST -> {
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.minZ, vertices.maxX, vertices.maxY, vertices.minZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.maxZ, vertices.maxX, vertices.maxY, vertices.maxZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.maxY, vertices.maxZ, vertices.maxX, vertices.maxY, vertices.minZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.maxZ, vertices.maxX, vertices.minY, vertices.minZ, color, thickness);
            }
            case WEST -> {
                vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.minZ, vertices.minX, vertices.maxY, vertices.minZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.maxZ, vertices.minX, vertices.maxY, vertices.maxZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.minX, vertices.maxY, vertices.maxZ, vertices.minX, vertices.maxY, vertices.minZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.maxZ, vertices.minX, vertices.minY, vertices.minZ, color, thickness);
            }
            case NORTH -> {
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.minZ, vertices.maxX, vertices.maxY, vertices.minZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.minZ, vertices.minX, vertices.maxY, vertices.minZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.minZ, vertices.minX, vertices.minY, vertices.minZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.maxY, vertices.minZ, vertices.minX, vertices.maxY, vertices.minZ, color, thickness);
            }
            case SOUTH -> {
                vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.maxZ, vertices.minX, vertices.maxY, vertices.maxZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.maxZ, vertices.maxX, vertices.maxY, vertices.maxZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.maxZ, vertices.maxX, vertices.minY, vertices.maxZ, color, thickness);
                vertexLine(builder, matrix, entry, vertices.minX, vertices.maxY, vertices.maxZ, vertices.maxX, vertices.maxY, vertices.maxZ, color, thickness);
            }
        }

        builder.end();
    }

    public static void drawOutlineBox(PoseStack stack, AABB box, int color, float thickness) {
        LuminImmediateRenderer.Lines builder = LuminImmediateRenderer.beginLines(LINES_PIPELINE);
        BoxVertices vertices = BoxVertices.of(box);
        Matrix4f matrix = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;
        PoseStack.Pose entry = stack.last();

        vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.minZ, vertices.maxX, vertices.minY, vertices.minZ, color, thickness);
        vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.minZ, vertices.maxX, vertices.minY, vertices.maxZ, color, thickness);
        vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.maxZ, vertices.minX, vertices.minY, vertices.maxZ, color, thickness);
        vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.maxZ, vertices.minX, vertices.minY, vertices.minZ, color, thickness);

        vertexLine(builder, matrix, entry, vertices.minX, vertices.maxY, vertices.minZ, vertices.maxX, vertices.maxY, vertices.minZ, color, thickness);
        vertexLine(builder, matrix, entry, vertices.maxX, vertices.maxY, vertices.minZ, vertices.maxX, vertices.maxY, vertices.maxZ, color, thickness);
        vertexLine(builder, matrix, entry, vertices.maxX, vertices.maxY, vertices.maxZ, vertices.minX, vertices.maxY, vertices.maxZ, color, thickness);
        vertexLine(builder, matrix, entry, vertices.minX, vertices.maxY, vertices.maxZ, vertices.minX, vertices.maxY, vertices.minZ, color, thickness);

        vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.minZ, vertices.minX, vertices.maxY, vertices.minZ, color, thickness);
        vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.minZ, vertices.maxX, vertices.maxY, vertices.minZ, color, thickness);
        vertexLine(builder, matrix, entry, vertices.maxX, vertices.minY, vertices.maxZ, vertices.maxX, vertices.maxY, vertices.maxZ, color, thickness);
        vertexLine(builder, matrix, entry, vertices.minX, vertices.minY, vertices.maxZ, vertices.minX, vertices.maxY, vertices.maxZ, color, thickness);

        builder.end();
    }

    private static void vertex(LuminImmediateRenderer.PosColorQuads builder, Matrix4f matrix, float x, float y, float z, int color) {
        builder.vertex(matrix, x, y, z, color);
    }

    private static void quad(LuminImmediateRenderer.PosColorQuads builder, Matrix4f matrix,
                             float x1, float y1, float z1, int c1,
                             float x2, float y2, float z2, int c2,
                             float x3, float y3, float z3, int c3,
                             float x4, float y4, float z4, int c4) {
        vertex(builder, matrix, x1, y1, z1, c1);
        vertex(builder, matrix, x2, y2, z2, c2);
        vertex(builder, matrix, x3, y3, z3, c3);
        vertex(builder, matrix, x4, y4, z4, c4);
    }

    private static void vertexLine(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose entry, float x1, float y1, float z1, float x2, float y2, float z2, int color, float thickness) {
        Vector3f normal = getNormal(x1, y1, z1, x2, y2, z2);
        builder.vertex(matrix, entry, x1, y1, z1, color, normal.x, normal.y, normal.z, thickness);
        builder.vertex(matrix, entry, x2, y2, z2, color, normal.x, normal.y, normal.z, thickness);
    }

    private static Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);
        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }

    private record BoxVertices(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {

        private static BoxVertices of(AABB box) {
            Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();
            return new BoxVertices(
                    (float) (box.minX - camPos.x),
                    (float) (box.minY - camPos.y),
                    (float) (box.minZ - camPos.z),
                    (float) (box.maxX - camPos.x),
                    (float) (box.maxY - camPos.y),
                    (float) (box.maxZ - camPos.z)
            );
        }

    }

}
