package com.github.epsilon.graphics.schedulers.render3d;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import static com.github.epsilon.Constants.mc;

public final class Render3DScheduler {
    public static final Render3DScheduler INSTANCE = new Render3DScheduler();

    private final List<FilledBoxCommand> filledBoxes = new ArrayList<>();
    private final List<FilledSideCommand> filledSides = new ArrayList<>();
    private final List<OutlineBoxCommand> outlineBoxes = new ArrayList<>();
    private final List<SideOutlineCommand> sideOutlines = new ArrayList<>();
    private final List<LineCommand> lines = new ArrayList<>();

    private Render3DScheduler() {
        EventBus.INSTANCE.subscribe(this);
    }

    public static void init() {
    }

    public boolean isEmpty() {
        return filledBoxes.isEmpty() && filledSides.isEmpty() && outlineBoxes.isEmpty()
                && sideOutlines.isEmpty() && lines.isEmpty();
    }

    public void clear() {
        filledBoxes.clear();
        filledSides.clear();
        outlineBoxes.clear();
        sideOutlines.clear();
        lines.clear();
    }

    public void addBlurredBox(AABB box, double blurStrength) {
    }

    public void addFilledBox(AABB box, Color color) {
        addFilledBox(box, color.getRGB());
    }

    public void addFilledBox(AABB box, int color) {
        addFilledFadeBox(box, color, color);
    }

    public void addFilledFadeBox(AABB box, int bottomColor, int topColor) {
        filledBoxes.add(new FilledBoxCommand(box, bottomColor, topColor));
    }

    public void addFilledSide(AABB box, int color, Direction direction) {
        filledSides.add(new FilledSideCommand(box, color, direction));
    }

    public void addOutlineBox(PoseStack stack, AABB box, Color color) {
        addOutlineBox(box, color.getRGB());
    }

    public void addOutlineBox(AABB box, Color color) {
        addOutlineBox(box, color.getRGB());
    }

    public void addOutlineBox(PoseStack stack, AABB box, int color) {
        addOutlineBox(box, color);
    }

    public void addOutlineBox(AABB box, int color) {
        addOutlineBox(box, color, 2.0f);
    }

    public void addOutlineBox(PoseStack stack, AABB box, int color, float thickness) {
        addOutlineBox(box, color, thickness);
    }

    public void addOutlineBox(AABB box, int color, float thickness) {
        outlineBoxes.add(new OutlineBoxCommand(box, color));
    }

    public void addSideOutline(PoseStack stack, AABB box, int color, float thickness, Direction direction) {
        addSideOutline(box, color, thickness, direction);
    }

    public void addSideOutline(AABB box, int color, float thickness, Direction direction) {
        sideOutlines.add(new SideOutlineCommand(box, color, direction));
    }

    public void addLine(Vec3 from, Vec3 to, Color color, float thickness) {
        addLine(from, to, color.getRGB(), thickness);
    }

    public void addLine(Vec3 from, Vec3 to, int color, float thickness) {
        if (from.distanceToSqr(to) >= 1.0E-6) lines.add(new LineCommand(from, to, color));
    }

    public void flush(PoseStack stack) {
        if (isEmpty()) return;
        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.disableDepthTest();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            flushFilled(stack.last().pose());
            flushLines(stack.last().pose());
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            clear();
        }
    }

    @EventHandler(priority = -999)
    private void onRender3D(Render3DEvent event) {
        flush(event.getPoseStack());
    }

    private void flushFilled(Matrix4f matrix) {
        if (filledBoxes.isEmpty() && filledSides.isEmpty()) return;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (FilledBoxCommand command : filledBoxes) emitBox(builder, matrix, camera, command);
        for (FilledSideCommand command : filledSides) emitSide(builder, matrix, camera, command.box(), command.color(), command.direction());
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private void flushLines(Matrix4f matrix) {
        if (outlineBoxes.isEmpty() && sideOutlines.isEmpty() && lines.isEmpty()) return;
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR);
        Vec3 camera = mc.gameRenderer.getMainCamera().getPosition();
        for (OutlineBoxCommand command : outlineBoxes) emitOutline(builder, matrix, camera, command.box(), command.color(), null);
        for (SideOutlineCommand command : sideOutlines) emitOutline(builder, matrix, camera, command.box(), command.color(), command.direction());
        for (LineCommand command : lines) line(builder, matrix, camera, command.from(), command.to(), command.color());
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static void emitBox(BufferBuilder builder, Matrix4f matrix, Vec3 camera, FilledBoxCommand command) {
        for (Direction direction : Direction.values()) {
            emitSide(builder, matrix, camera, command.box(),
                    direction == Direction.UP ? command.topColor() : command.bottomColor(), direction);
        }
    }

    private static void emitSide(BufferBuilder builder, Matrix4f matrix, Vec3 camera, AABB box, int color, Direction side) {
        float x0 = (float) (box.minX - camera.x);
        float y0 = (float) (box.minY - camera.y);
        float z0 = (float) (box.minZ - camera.z);
        float x1 = (float) (box.maxX - camera.x);
        float y1 = (float) (box.maxY - camera.y);
        float z1 = (float) (box.maxZ - camera.z);
        switch (side) {
            case DOWN -> quad(builder, matrix, color, x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
            case UP -> quad(builder, matrix, color, x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
            case NORTH -> quad(builder, matrix, color, x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
            case SOUTH -> quad(builder, matrix, color, x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
            case WEST -> quad(builder, matrix, color, x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
            case EAST -> quad(builder, matrix, color, x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
        }
    }

    private static void emitOutline(BufferBuilder builder, Matrix4f matrix, Vec3 camera, AABB box, int color, Direction side) {
        Vec3[] p = {
                new Vec3(box.minX, box.minY, box.minZ), new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.maxZ), new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.minZ), new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.maxZ), new Vec3(box.minX, box.maxY, box.maxZ)
        };
        int[][] edges = side == null ? new int[][]{
                {0,1},{1,2},{2,3},{3,0},{4,5},{5,6},{6,7},{7,4},{0,4},{1,5},{2,6},{3,7}
        } : switch (side) {
            case DOWN -> new int[][]{{0,1},{1,2},{2,3},{3,0}};
            case UP -> new int[][]{{4,5},{5,6},{6,7},{7,4}};
            case NORTH -> new int[][]{{0,1},{1,5},{5,4},{4,0}};
            case SOUTH -> new int[][]{{3,2},{2,6},{6,7},{7,3}};
            case WEST -> new int[][]{{0,3},{3,7},{7,4},{4,0}};
            case EAST -> new int[][]{{1,2},{2,6},{6,5},{5,1}};
        };
        for (int[] edge : edges) line(builder, matrix, camera, p[edge[0]], p[edge[1]], color);
    }

    private static void line(BufferBuilder builder, Matrix4f matrix, Vec3 camera, Vec3 from, Vec3 to, int color) {
        builder.addVertex(matrix, (float)(from.x-camera.x), (float)(from.y-camera.y), (float)(from.z-camera.z)).setColor(color);
        builder.addVertex(matrix, (float)(to.x-camera.x), (float)(to.y-camera.y), (float)(to.z-camera.z)).setColor(color);
    }

    private static void quad(BufferBuilder builder, Matrix4f matrix, int color, float... xyz) {
        for (int i = 0; i < xyz.length; i += 3) builder.addVertex(matrix, xyz[i], xyz[i + 1], xyz[i + 2]).setColor(color);
    }

    private record FilledBoxCommand(AABB box, int bottomColor, int topColor) {}
    private record FilledSideCommand(AABB box, int color, Direction direction) {}
    private record OutlineBoxCommand(AABB box, int color) {}
    private record SideOutlineCommand(AABB box, int color, Direction direction) {}
    private record LineCommand(Vec3 from, Vec3 to, int color) {}
}
