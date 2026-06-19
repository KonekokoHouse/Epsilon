package com.github.epsilon.managers.impl;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.immediate.LuminImmediateRenderer;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public class RenderManager {

    public RenderManager() {
        EventBus.INSTANCE.subscribe(this);
    }

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

    private final List<BlurredBoxRequest> blurredBoxes = new ArrayList<>();
    private final List<FilledBoxRequest> filledBoxes = new ArrayList<>();
    private final List<FilledSideRequest> filledSides = new ArrayList<>();
    private final List<OutlineBoxRequest> outlineBoxes = new ArrayList<>();
    private final List<SideOutlineRequest> sideOutlines = new ArrayList<>();

    public void addBlurredBox(AABB box, double blurStrength) {
        blurredBoxes.add(new BlurredBoxRequest(box, blurStrength));
    }

    public void addFilledBox(AABB box, Color color) {
        addFilledBox(box, color.getRGB());
    }

    public void addFilledBox(AABB box, int color) {
        addFilledFadeBox(box, color, color);
    }

    public void addFilledFadeBox(AABB box, int bottomColor, int topColor) {
        filledBoxes.add(new FilledBoxRequest(box, bottomColor, topColor));
    }

    public void addFilledSide(AABB box, int color, Direction direction) {
        filledSides.add(new FilledSideRequest(box, color, direction));
    }

    public void addOutlineBox(PoseStack stack, AABB box, Color color) {
        addOutlineBox(stack, box, color.getRGB());
    }

    public void addOutlineBox(AABB box, Color color) {
        addOutlineBox(box, color.getRGB());
    }

    public void addOutlineBox(PoseStack stack, AABB box, int color) {
        addOutlineBox(stack, box, color, 2.0f);
    }

    public void addOutlineBox(AABB box, int color) {
        addOutlineBox(box, color, 2.0f);
    }

    public void addOutlineBox(PoseStack stack, AABB box, int color, float thickness) {
        outlineBoxes.add(new OutlineBoxRequest(box, color, thickness));
    }

    public void addOutlineBox(AABB box, int color, float thickness) {
        outlineBoxes.add(new OutlineBoxRequest(box, color, thickness));
    }

    public void addSideOutline(PoseStack stack, AABB box, int color, float thickness, Direction direction) {
        sideOutlines.add(new SideOutlineRequest(box, color, thickness, direction));
    }

    public void addSideOutline(AABB box, int color, float thickness, Direction direction) {
        sideOutlines.add(new SideOutlineRequest(box, color, thickness, direction));
    }

    @EventHandler(priority = -999)
    private void onRender3D(Render3DEvent event) {
        flushBlur();
        flushFilled();
        flushLines(event.getPoseStack());
        clearAll();
    }

    private void flushBlur() {
        if (blurredBoxes.isEmpty()) {
            return;
        }

        for (BlurredBoxRequest request : blurredBoxes) {
            BlurShader.INSTANCE.render3DBox(request.box, request.blurStrength);
        }
    }

    private void flushFilled() {
        if (filledBoxes.isEmpty() && filledSides.isEmpty()) {
            return;
        }

        LuminImmediateRenderer.PosColorQuads builder = LuminImmediateRenderer.beginPosColorQuads(FILLED_BOX_PIPELINE);
        Matrix4f matrix = mc.gameRenderer.getGameRenderState().levelRenderState.cameraRenderState.viewRotationMatrix;
        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();

        for (FilledBoxRequest request : filledBoxes) {
            AABB box = request.box();
            float minX = (float) (box.minX - camPos.x);
            float minY = (float) (box.minY - camPos.y);
            float minZ = (float) (box.minZ - camPos.z);
            float maxX = (float) (box.maxX - camPos.x);
            float maxY = (float) (box.maxY - camPos.y);
            float maxZ = (float) (box.maxZ - camPos.z);

            quad(builder, matrix,
                    minX, minY, minZ, request.bottomColor(),
                    minX, minY, maxZ, request.bottomColor(),
                    maxX, minY, maxZ, request.bottomColor(),
                    maxX, minY, minZ, request.bottomColor());

            quad(builder, matrix,
                    minX, maxY, minZ, request.topColor(),
                    maxX, maxY, minZ, request.topColor(),
                    maxX, maxY, maxZ, request.topColor(),
                    minX, maxY, maxZ, request.topColor());

            quad(builder, matrix,
                    minX, minY, minZ, request.bottomColor(),
                    maxX, minY, minZ, request.bottomColor(),
                    maxX, maxY, minZ, request.topColor(),
                    minX, maxY, minZ, request.topColor());

            quad(builder, matrix,
                    maxX, minY, minZ, request.bottomColor(),
                    maxX, minY, maxZ, request.bottomColor(),
                    maxX, maxY, maxZ, request.topColor(),
                    maxX, maxY, minZ, request.topColor());

            quad(builder, matrix,
                    minX, minY, maxZ, request.bottomColor(),
                    minX, maxY, maxZ, request.topColor(),
                    maxX, maxY, maxZ, request.topColor(),
                    maxX, minY, maxZ, request.bottomColor());

            quad(builder, matrix,
                    minX, minY, minZ, request.bottomColor(),
                    minX, maxY, minZ, request.topColor(),
                    minX, maxY, maxZ, request.topColor(),
                    minX, minY, maxZ, request.bottomColor());
        }

        for (FilledSideRequest request : filledSides) {
            AABB box = request.box();
            float minX = (float) (box.minX - camPos.x);
            float minY = (float) (box.minY - camPos.y);
            float minZ = (float) (box.minZ - camPos.z);
            float maxX = (float) (box.maxX - camPos.x);
            float maxY = (float) (box.maxY - camPos.y);
            float maxZ = (float) (box.maxZ - camPos.z);

            switch (request.direction()) {
                case DOWN -> quad(builder, matrix,
                        minX, minY, minZ, request.color(),
                        maxX, minY, minZ, request.color(),
                        maxX, minY, maxZ, request.color(),
                        minX, minY, maxZ, request.color());
                case NORTH -> quad(builder, matrix,
                        minX, minY, minZ, request.color(),
                        minX, maxY, minZ, request.color(),
                        maxX, maxY, minZ, request.color(),
                        maxX, minY, minZ, request.color());
                case EAST -> quad(builder, matrix,
                        maxX, minY, minZ, request.color(),
                        maxX, maxY, minZ, request.color(),
                        maxX, maxY, maxZ, request.color(),
                        maxX, minY, maxZ, request.color());
                case SOUTH -> quad(builder, matrix,
                        minX, minY, maxZ, request.color(),
                        maxX, minY, maxZ, request.color(),
                        maxX, maxY, maxZ, request.color(),
                        minX, maxY, maxZ, request.color());
                case WEST -> quad(builder, matrix,
                        minX, minY, minZ, request.color(),
                        minX, minY, maxZ, request.color(),
                        minX, maxY, maxZ, request.color(),
                        minX, maxY, minZ, request.color());
                case UP -> quad(builder, matrix,
                        minX, maxY, minZ, request.color(),
                        minX, maxY, maxZ, request.color(),
                        maxX, maxY, maxZ, request.color(),
                        maxX, maxY, minZ, request.color());
            }
        }

        builder.end();
    }

    private void flushLines(PoseStack stack) {
        if (outlineBoxes.isEmpty() && sideOutlines.isEmpty()) {
            return;
        }

        LuminImmediateRenderer.Lines builder = LuminImmediateRenderer.beginLines(LINES_PIPELINE);
        Vec3 camPos = mc.getEntityRenderDispatcher().camera.position();

        for (OutlineBoxRequest request : outlineBoxes) {
            AABB box = request.box();
            float minX = (float) (box.minX - camPos.x);
            float minY = (float) (box.minY - camPos.y);
            float minZ = (float) (box.minZ - camPos.z);
            float maxX = (float) (box.maxX - camPos.x);
            float maxY = (float) (box.maxY - camPos.y);
            float maxZ = (float) (box.maxZ - camPos.z);

            PoseStack.Pose entry = stack.last();
            Matrix4f matrix = entry.pose();

            vertexLine(builder, matrix, entry, minX, minY, minZ, maxX, minY, minZ, request.color(), request.thickness());
            vertexLine(builder, matrix, entry, maxX, minY, minZ, maxX, minY, maxZ, request.color(), request.thickness());
            vertexLine(builder, matrix, entry, maxX, minY, maxZ, minX, minY, maxZ, request.color(), request.thickness());
            vertexLine(builder, matrix, entry, minX, minY, maxZ, minX, minY, minZ, request.color(), request.thickness());

            vertexLine(builder, matrix, entry, minX, maxY, minZ, maxX, maxY, minZ, request.color(), request.thickness());
            vertexLine(builder, matrix, entry, maxX, maxY, minZ, maxX, maxY, maxZ, request.color(), request.thickness());
            vertexLine(builder, matrix, entry, maxX, maxY, maxZ, minX, maxY, maxZ, request.color(), request.thickness());
            vertexLine(builder, matrix, entry, minX, maxY, maxZ, minX, maxY, minZ, request.color(), request.thickness());

            vertexLine(builder, matrix, entry, minX, minY, minZ, minX, maxY, minZ, request.color(), request.thickness());
            vertexLine(builder, matrix, entry, maxX, minY, minZ, maxX, maxY, minZ, request.color(), request.thickness());
            vertexLine(builder, matrix, entry, maxX, minY, maxZ, maxX, maxY, maxZ, request.color(), request.thickness());
            vertexLine(builder, matrix, entry, minX, minY, maxZ, minX, maxY, maxZ, request.color(), request.thickness());
        }

        for (SideOutlineRequest request : sideOutlines) {
            AABB box = request.box();
            float minX = (float) (box.minX - camPos.x);
            float minY = (float) (box.minY - camPos.y);
            float minZ = (float) (box.minZ - camPos.z);
            float maxX = (float) (box.maxX - camPos.x);
            float maxY = (float) (box.maxY - camPos.y);
            float maxZ = (float) (box.maxZ - camPos.z);

            PoseStack.Pose entry = stack.last();
            Matrix4f matrix = entry.pose();

            switch (request.direction()) {
                case UP -> {
                    vertexLine(builder, matrix, entry, minX, maxY, minZ, maxX, maxY, minZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, maxY, minZ, maxX, maxY, maxZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, maxY, maxZ, minX, maxY, maxZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, minX, maxY, maxZ, minX, maxY, minZ, request.color(), request.thickness());
                }
                case DOWN -> {
                    vertexLine(builder, matrix, entry, minX, minY, minZ, maxX, minY, minZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, minY, minZ, maxX, minY, maxZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, minY, maxZ, minX, minY, maxZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, minX, minY, maxZ, minX, minY, minZ, request.color(), request.thickness());
                }
                case EAST -> {
                    vertexLine(builder, matrix, entry, maxX, minY, minZ, maxX, maxY, minZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, minY, maxZ, maxX, maxY, maxZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, maxY, maxZ, maxX, maxY, minZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, minY, maxZ, maxX, minY, minZ, request.color(), request.thickness());
                }
                case WEST -> {
                    vertexLine(builder, matrix, entry, minX, minY, minZ, minX, maxY, minZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, minX, minY, maxZ, minX, maxY, maxZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, minX, maxY, maxZ, minX, maxY, minZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, minX, minY, maxZ, minX, minY, minZ, request.color(), request.thickness());
                }
                case NORTH -> {
                    vertexLine(builder, matrix, entry, maxX, minY, minZ, maxX, maxY, minZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, minX, minY, minZ, minX, maxY, minZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, minY, minZ, minX, minY, minZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, maxY, minZ, minX, maxY, minZ, request.color(), request.thickness());
                }
                case SOUTH -> {
                    vertexLine(builder, matrix, entry, minX, minY, maxZ, minX, maxY, maxZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, maxX, minY, maxZ, maxX, maxY, maxZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, minX, minY, maxZ, maxX, minY, maxZ, request.color(), request.thickness());
                    vertexLine(builder, matrix, entry, minX, maxY, maxZ, maxX, maxY, maxZ, request.color(), request.thickness());
                }
            }
        }

        builder.end();
    }

    private void clearAll() {
        blurredBoxes.clear();
        filledBoxes.clear();
        filledSides.clear();
        outlineBoxes.clear();
        sideOutlines.clear();
    }

    private void quad(
            LuminImmediateRenderer.PosColorQuads builder, Matrix4f matrix,
            float x1, float y1, float z1, int c1,
            float x2, float y2, float z2, int c2,
            float x3, float y3, float z3, int c3,
            float x4, float y4, float z4, int c4
    ) {
        builder.vertex(matrix, x1, y1, z1, c1);
        builder.vertex(matrix, x2, y2, z2, c2);
        builder.vertex(matrix, x3, y3, z3, c3);
        builder.vertex(matrix, x4, y4, z4, c4);
    }

    private void vertexLine(LuminImmediateRenderer.Lines builder, Matrix4f matrix, PoseStack.Pose pose, float x1, float y1, float z1, float x2, float y2, float z2, int color, float thickness) {
        Vector3f normal = getNormal(x1, y1, z1, x2, y2, z2);
        builder.vertex(matrix, pose, x1, y1, z1, color, normal.x, normal.y, normal.z, thickness);
        builder.vertex(matrix, pose, x2, y2, z2, color, normal.x, normal.y, normal.z, thickness);
    }

    private Vector3f getNormal(float x1, float y1, float z1, float x2, float y2, float z2) {
        float xNormal = x2 - x1;
        float yNormal = y2 - y1;
        float zNormal = z2 - z1;
        float normalSqrt = Mth.sqrt(xNormal * xNormal + yNormal * yNormal + zNormal * zNormal);
        return new Vector3f(xNormal / normalSqrt, yNormal / normalSqrt, zNormal / normalSqrt);
    }

    private record BlurredBoxRequest(AABB box, double blurStrength) {
    }

    private record FilledBoxRequest(AABB box, int bottomColor, int topColor) {
    }

    private record FilledSideRequest(AABB box, int color, Direction direction) {
    }

    private record OutlineBoxRequest(AABB box, int color, float thickness) {
    }

    private record SideOutlineRequest(AABB box, int color, float thickness, Direction direction) {
    }

}
