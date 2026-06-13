package com.github.epsilon.modules.impl.render;

import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.graphics.shaders.BlurShader;
import com.github.epsilon.managers.RotationManager;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.impl.BoolSetting;
import com.github.epsilon.settings.impl.ColorSetting;
import com.github.epsilon.settings.impl.DoubleSetting;
import com.github.epsilon.settings.impl.EnumSetting;
import com.github.epsilon.utils.render.Render3DUtils;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.awt.*;

public class BlockHighlight extends Module {

    public static final BlockHighlight INSTANCE = new BlockHighlight();

    private BlockHighlight() {
        super("Block Highlight", Category.RENDER);
    }

    private enum Mode {
        Both,
        BothSide,
        Fill,
        FilledSide,
        Outline,
        OutlinedSide
    }

    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.Outline);
    private final ColorSetting sideColor = colorSetting("Color", new Color(255, 255, 255, 100), () -> mode.is(Mode.Both) || mode.is(Mode.BothSide) || mode.is(Mode.Fill) || mode.is(Mode.FilledSide));
    private final ColorSetting lineColor = colorSetting("Line Color", new Color(255, 255, 255, 255), () -> mode.is(Mode.Both) || mode.is(Mode.BothSide) || mode.is(Mode.Outline) || mode.is(Mode.OutlinedSide));
    private final DoubleSetting lineWidth = doubleSetting("Line Width", 1.0, 0.0, 5.0, 0.5);
    private final BoolSetting blur = boolSetting("Blur", true);
    private final DoubleSetting blurStrength = doubleSetting("Blur Strength", 5.0, 0.0, 16.0, 0.5, () -> blur.getValue() && (mode.is(Mode.Both) || mode.is(Mode.BothSide) || mode.is(Mode.Fill)));

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        HitResult hitResult = RotationManager.INSTANCE.getHitResult();
        if (hitResult == null || hitResult.getType() != HitResult.Type.BLOCK || !(hitResult instanceof BlockHitResult bhr)) {
            return;
        }

        float thickness = lineWidth.getValue().floatValue();
        AABB box = new AABB(bhr.getBlockPos());
        Color fillColor = sideColor.getValue();
        Color outlineColor = lineColor.getValue();
        Direction direction = bhr.getDirection();

        switch (mode.getValue()) {
            case Both -> {
                drawBlur(box);
                Render3DUtils.drawFilledBox(box, fillColor);
                Render3DUtils.drawOutlineBox(event.getPoseStack(), box, outlineColor, thickness);
            }
            case BothSide -> {
                drawBlur(box);
                Render3DUtils.drawSideOutline(event.getPoseStack(), box, outlineColor, thickness, direction);
                Render3DUtils.drawFilledSide(box, fillColor, direction);
            }
            case Fill -> {
                drawBlur(box);
                Render3DUtils.drawFilledBox(box, fillColor);
            }
            case FilledSide -> {
                Render3DUtils.drawFilledSide(box, fillColor, direction);
            }
            case Outline -> {
                Render3DUtils.drawOutlineBox(event.getPoseStack(), box, outlineColor, thickness);
            }
            case OutlinedSide -> {
                Render3DUtils.drawSideOutline(event.getPoseStack(), box, outlineColor, thickness, direction);
            }
        }
    }

    private void drawBlur(AABB aabb) {
        if (blur.getValue()) BlurShader.INSTANCE.render3DBox(aabb, blurStrength.getValue().floatValue());
    }

}
