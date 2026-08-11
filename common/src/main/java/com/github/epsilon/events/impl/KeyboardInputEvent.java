package com.github.epsilon.events.impl;

import net.minecraft.client.player.Input;

public class KeyboardInputEvent {

    private float forward;
    private float strafe;
    private boolean jump;
    private boolean sneak;
    private boolean sprint;

    public KeyboardInputEvent(float forward, float strafe, boolean jump, boolean sneak, boolean sprint) {
        this.forward = forward;
        this.strafe = strafe;
        this.jump = jump;
        this.sneak = sneak;
        this.sprint = sprint;
    }

    public void applyTo(Input input) {
        input.forwardImpulse = this.forward;
        input.leftImpulse = this.strafe;
        input.up = this.forward > 0.0f;
        input.down = this.forward < 0.0f;
        input.left = this.strafe > 0.0f;
        input.right = this.strafe < 0.0f;
        input.jumping = this.jump;
        input.shiftKeyDown = this.sneak;
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
