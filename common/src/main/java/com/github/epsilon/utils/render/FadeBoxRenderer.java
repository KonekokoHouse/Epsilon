package com.github.epsilon.utils.render;

import com.github.epsilon.events.bus.EventBus;
import com.github.epsilon.events.bus.listeners.ConsumerListener;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.schedulers.render3d.Render3DScheduler;
import com.github.epsilon.utils.render.animation.Easing;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

public class FadeBoxRenderer {

    private final BooleanSupplier enabledSupplier;
    private final LongSupplier fadeTimeSupplier;
    private final Supplier<Color> sideColorSupplier;
    private final Supplier<Color> lineColorSupplier;
    private final BooleanSupplier fadeEnabledSupplier;
    private final BooleanSupplier shrinkSupplier;

    private final List<RenderEntry> entries = new ArrayList<>();

    public FadeBoxRenderer(BooleanSupplier enabledSupplier,
                           LongSupplier fadeTimeSupplier,
                           Supplier<Color> sideColorSupplier,
                           Supplier<Color> lineColorSupplier) {
        this(enabledSupplier, fadeTimeSupplier, sideColorSupplier, lineColorSupplier, () -> true, () -> false);
    }

    public FadeBoxRenderer(BooleanSupplier enabledSupplier,
                           LongSupplier fadeTimeSupplier,
                           Supplier<Color> sideColorSupplier,
                           Supplier<Color> lineColorSupplier,
                           BooleanSupplier fadeEnabledSupplier,
                           BooleanSupplier shrinkSupplier) {
        this.enabledSupplier = enabledSupplier;
        this.fadeTimeSupplier = fadeTimeSupplier;
        this.sideColorSupplier = sideColorSupplier;
        this.lineColorSupplier = lineColorSupplier;
        this.fadeEnabledSupplier = fadeEnabledSupplier;
        this.shrinkSupplier = shrinkSupplier;

        EventBus.INSTANCE.subscribe(new ConsumerListener<>(Render3DEvent.class, this::onRender3D));
    }

    public void addBox(AABB aabb) {
        entries.add(new RenderEntry(aabb, lineColorSupplier.get(), sideColorSupplier.get(),
                System.currentTimeMillis(), fadeEnabledSupplier.getAsBoolean(), shrinkSupplier.getAsBoolean()));
    }

    public void addBox(BlockPos pos) {
        addBox(new AABB(pos));
    }

    public void clear() {
        entries.clear();
    }

    private void onRender3D(Render3DEvent event) {
        if (!enabledSupplier.getAsBoolean() || entries.isEmpty()) return;

        long time = System.currentTimeMillis();
        long fadeTime = this.fadeTimeSupplier.getAsLong();

        entries.removeIf(box -> time - box.startTime() > fadeTime);

        for (RenderEntry box : entries) {
            float progress = Mth.clamp((float) (time - box.startTime()) / fadeTime, 0.0f, 1.0f);

            double scale = 1.0;
            if (box.shrink()) {
                scale = 1.0 - Easing.EASE_IN_OUT_EXPO.getFunction().apply(progress);
                if (scale < 0) scale = 0;
            }

            float alphaFactor = box.fade() ? Mth.clamp(1.0f - progress, 0.0f, 1.0f) : 1.0f;

            Color sideColor = box.sideColor();
            Color lineColor = box.lineColor();

            Color side = new Color(sideColor.getRed(), sideColor.getGreen(), sideColor.getBlue(), (int) (sideColor.getAlpha() * alphaFactor));
            Color line = new Color(lineColor.getRed(), lineColor.getGreen(), lineColor.getBlue(), (int) (lineColor.getAlpha() * alphaFactor));

            AABB renderBox = box.aabb;
            if (box.shrink()) {
                renderBox = AABB.ofSize(renderBox.getCenter(), renderBox.getXsize() * scale, renderBox.getYsize() * scale, renderBox.getZsize() * scale);
            }

            Render3DScheduler.INSTANCE.addFilledBox(renderBox, side);
            Render3DScheduler.INSTANCE.addOutlineBox(renderBox, line);
        }
    }

    private record RenderEntry(AABB aabb, Color lineColor, Color sideColor, long startTime, boolean fade, boolean shrink) {
    }

}
