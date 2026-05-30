package com.github.epsilon.gui.dropdown;

import com.github.epsilon.graphics.LuminRenderSystem;
import com.github.epsilon.graphics.renderers.*;

public class DropdownRenderer {

    private static final int MAX_SLOTS = 32;

    private final Slot[] slots = new Slot[MAX_SLOTS];
    private int slotIndex = -1;
    private int slotCount;

    private Slot current() {
        return slots[slotIndex];
    }

    public ShadowRenderer shadow() {
        Slot slot = current();
        if (slot.shadow == null) slot.shadow = ShadowRenderer.create();
        return slot.shadow;
    }

    public RoundRectRenderer roundRect() {
        Slot slot = current();
        if (slot.roundRect == null) slot.roundRect = RoundRectRenderer.create();
        return slot.roundRect;
    }

    public RoundRectOutlineRenderer outline() {
        Slot slot = current();
        if (slot.outline == null) slot.outline = RoundRectOutlineRenderer.create();
        return slot.outline;
    }

    public RectRenderer rect() {
        Slot slot = current();
        if (slot.rect == null) slot.rect = RectRenderer.create();
        return slot.rect;
    }

    public TriangleRenderer triangle() {
        Slot slot = current();
        if (slot.triangle == null) slot.triangle = TriangleRenderer.create();
        return slot.triangle;
    }

    public TextRenderer text() {
        Slot slot = current();
        if (slot.text == null) slot.text = TextRenderer.create();
        return slot.text;
    }

    public void setScissor(float guiX, float guiY, float guiW, float guiH, int guiHeight) {
        LuminRenderSystem.ScissorRect scissor = LuminRenderSystem.toFramebufferScissor(guiX, guiY, guiW, guiH);
        int x = scissor.x();
        int y = scissor.y();
        int w = scissor.width();
        int h = scissor.height();
        Slot slot = current();
        setScissorOn(slot.shadow, x, y, w, h);
        setScissorOn(slot.roundRect, x, y, w, h);
        setScissorOn(slot.outline, x, y, w, h);
        setScissorOn(slot.rect, x, y, w, h);
        setScissorOn(slot.triangle, x, y, w, h);
        setScissorOn(slot.text, x, y, w, h);
    }

    private static void setScissorOn(Object renderer, int x, int y, int w, int h) {
        if (renderer instanceof ShadowRenderer r) r.setScissor(x, y, w, h);
        else if (renderer instanceof RoundRectRenderer r) r.setScissor(x, y, w, h);
        else if (renderer instanceof RoundRectOutlineRenderer r) r.setScissor(x, y, w, h);
        else if (renderer instanceof RectRenderer r) r.setScissor(x, y, w, h);
        else if (renderer instanceof TriangleRenderer r) r.setScissor(x, y, w, h);
        else if (renderer instanceof TextRenderer r) r.setScissor(x, y, w, h);
    }

    public void clearScissor() {
        Slot slot = current();
        clearScissorOn(slot.shadow);
        clearScissorOn(slot.roundRect);
        clearScissorOn(slot.outline);
        clearScissorOn(slot.rect);
        clearScissorOn(slot.triangle);
        clearScissorOn(slot.text);
    }

    private static void clearScissorOn(Object renderer) {
        if (renderer instanceof ShadowRenderer r) r.clearScissor();
        else if (renderer instanceof RoundRectRenderer r) r.clearScissor();
        else if (renderer instanceof RoundRectOutlineRenderer r) r.clearScissor();
        else if (renderer instanceof RectRenderer r) r.clearScissor();
        else if (renderer instanceof TriangleRenderer r) r.clearScissor();
        else if (renderer instanceof TextRenderer r) r.clearScissor();
    }

    public void beginFrame() {
        slotIndex = -1;
    }

    public void beginPass() {
        slotIndex++;
        if (slotIndex >= slotCount) {
            if (slotCount >= MAX_SLOTS) {
                throw new IllegalStateException("exceeded max renderer slots: " + MAX_SLOTS);
            }
            slots[slotCount] = new Slot();
            slotCount++;
        }
        slots[slotIndex].flushed = false;
    }

    public void flush() {
        Slot slot = current();
        if (slot.flushed) return;
        slot.flushed = true;
        if (slot.shadow != null) slot.shadow.drawAndClear();
        if (slot.roundRect != null) slot.roundRect.drawAndClear();
        if (slot.outline != null) slot.outline.drawAndClear();
        if (slot.rect != null) slot.rect.drawAndClear();
        if (slot.triangle != null) slot.triangle.drawAndClear();
        if (slot.text != null) slot.text.drawAndClear();
    }

    public void close() {
        for (int i = 0; i < slotCount; i++) {
            Slot slot = slots[i];
            if (slot.shadow != null) slot.shadow.close();
            if (slot.roundRect != null) slot.roundRect.close();
            if (slot.outline != null) slot.outline.close();
            if (slot.rect != null) slot.rect.close();
            if (slot.triangle != null) slot.triangle.close();
            if (slot.text != null) slot.text.close();
        }
    }

    private static final class Slot {
        boolean flushed;
        ShadowRenderer shadow;
        RoundRectRenderer roundRect;
        RoundRectOutlineRenderer outline;
        RectRenderer rect;
        TriangleRenderer triangle;
        TextRenderer text;
    }

}
