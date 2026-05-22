package com.github.epsilon.events.impl;

import net.minecraft.world.entity.player.Input;

public class KeyboardInputEvent {

    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    public KeyboardInputEvent(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        float f = forward == backward ? 0.0F : (forward ? 1.0F : -1.0F);
        float g = left == right ? 0.0F : (left ? 1.0F : -1.0F);
        this.forward = f;
        this.strafe = g;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;
    }

    public Input toNewInput() {
        return new Input(
                this.forward > 0,
                this.forward < 0,
                this.strafe > 0,
                this.strafe < 0,
                this.jump,
                this.sneak,
                this.sprint
        );
    }

    public float getForward() {
        return this.forward;
    }

    public float getStrafe() {
        return this.strafe;
    }

    public boolean isJump() {
        return this.jump;
    }

    public boolean isSneak() {
        return this.sneak;
    }

    public void setForward(float forward) {
        this.forward = forward;
    }

    public void setStrafe(float strafe) {
        this.strafe = strafe;
    }

    public void setJump(boolean jump) {
        this.jump = jump;
    }

    public void setSneak(boolean sneak) {
        this.sneak = sneak;
    }

    public boolean isSprint() {
        return sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }

}
