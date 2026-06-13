package com.github.epsilon.modules.impl.render;

import com.github.epsilon.assets.resources.ResourceLocationUtils;
import com.github.epsilon.events.bus.EventHandler;
import com.github.epsilon.events.impl.ClientTickEvent;
import com.github.epsilon.events.impl.Render3DEvent;
import com.github.epsilon.modules.Category;
import com.github.epsilon.modules.Module;
import com.github.epsilon.settings.SettingGroup;
import com.github.epsilon.settings.impl.*;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.LayeringTransform;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class Particles extends Module {

    public static final Particles INSTANCE = new Particles();

    private Particles() {
        super("Particles", Category.RENDER);
    }

    private final SettingGroup sgFireFlies = settingGroup("Fire Flies");

    private final BoolSetting fireFliesEnabled = boolSetting("Fire Flies", true).group(sgFireFlies);
    private final IntSetting ffcount = intSetting("FF Count", 30, 20, 200, 1, fireFliesEnabled::getValue).group(sgFireFlies);
    private final DoubleSetting ffsize = doubleSetting("FF Size", 1.0, 0.1, 2.0, 0.1, fireFliesEnabled::getValue).group(sgFireFlies);
    private final EnumSetting<Mode> mode = enumSetting("Mode", Mode.SnowFlake);
    private final IntSetting count = intSetting("Count", 100, 20, 800, 1, () -> !mode.is(Mode.Off));
    private final DoubleSetting size = doubleSetting("Size", 1.0, 0.1, 6.0, 0.1, () -> !mode.is(Mode.Off));
    private final EnumSetting<ColorMode> colorMode = enumSetting("Color Mode", ColorMode.Sync);
    private final ColorSetting color = colorSetting("Color", new Color(3649978), () -> colorMode.is(ColorMode.Custom));
    private final EnumSetting<Physics> physics = enumSetting("Physics", Physics.Fly, () -> !mode.is(Mode.Off));

    private final ArrayList<ParticleBase> fireFlies = new ArrayList<>();
    private final ArrayList<ParticleBase> particles = new ArrayList<>();

    private static final Identifier FIREFLY_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/firefly.png");
    private static final Identifier SNOWFLAKE_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/snowflake.png");
    private static final Identifier STAR_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/star.png");
    private static final Identifier HEART_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/heart.png");
    private static final Identifier DOLLAR_TEXTURE = ResourceLocationUtils.getIdentifier("textures/particles/dollar.png");

    private static final RenderPipeline PARTICLE_PIPELINE = RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
            .withLocation("pipeline/epsilon_particles")
            .withColorTargetState(new ColorTargetState(BlendFunction.LIGHTNING))
            .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false))
            .withCull(false)
            .build();

    private static final Function<Identifier, RenderType> PARTICLE_LAYER = Util.memoize(texture -> RenderType.create(
            "epsilon_particles",
            RenderSetup.builder(PARTICLE_PIPELINE)
                    .withTexture("Sampler0", texture)
                    .sortOnUpload()
                    .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                    .setOutputTarget(OutputTarget.MAIN_TARGET)
                    .createRenderSetup()
    ));

    @EventHandler
    private void onTick(ClientTickEvent.Post event) {
        if (nullCheck()) return;

        fireFlies.removeIf(ParticleBase::tick);
        particles.removeIf(ParticleBase::tick);

        for (int i = fireFlies.size(); i < ffcount.getValue(); i++) {
            if (fireFliesEnabled.getValue()) {
                fireFlies.add(new FireFly(
                        (float) (mc.player.getX() + random(-25.0f, 25.0f)),
                        (float) (mc.player.getY() + random(2.0f, 15.0f)),
                        (float) (mc.player.getZ() + random(-25.0f, 25.0f)),
                        random(-0.2f, 0.2f),
                        random(-0.1f, 0.1f),
                        random(-0.2f, 0.2f)
                ));
            }
        }

        for (int i = particles.size(); i < count.getValue(); i++) {
            boolean drop = physics.is(Physics.Drop);
            if (!mode.is(Mode.Off)) {
                particles.add(new ParticleBase(
                        (float) (mc.player.getX() + random(-48.0f, 48.0f)),
                        (float) (mc.player.getY() + random(2.0f, 48.0f)),
                        (float) (mc.player.getZ() + random(-48.0f, 48.0f)),
                        drop ? 0.0f : random(-0.4f, 0.4f),
                        drop ? random(-0.2f, -0.05f) : random(-0.1f, 0.1f),
                        drop ? 0.0f : random(-0.4f, 0.4f)
                ));
            }
        }
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (nullCheck()) return;

        if (fireFliesEnabled.getValue() && !fireFlies.isEmpty()) {
            renderParticleList(event.getPoseStack(), fireFlies, FIREFLY_TEXTURE);
        }

        if (!mode.is(Mode.Off) && !particles.isEmpty()) {
            renderParticleList(event.getPoseStack(), particles, textureForMode(mode.getValue()));
        }
    }

    @Override
    protected void onDisable() {
        clearParticles();
    }

    @Override
    protected void resetCustomState() {
        clearParticles();
    }

    private void renderParticleList(PoseStack poseStack, List<ParticleBase> list, Identifier texture) {
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (ParticleBase particle : list) {
            particle.render(poseStack, buffer);
        }
        PARTICLE_LAYER.apply(texture).draw(buffer.buildOrThrow());
    }

    private Identifier textureForMode(Mode mode) {
        return switch (mode) {
            case Bloom -> FIREFLY_TEXTURE;
            case SnowFlake -> SNOWFLAKE_TEXTURE;
            case Stars -> STAR_TEXTURE;
            case Hearts -> HEART_TEXTURE;
            case Dollars -> DOLLAR_TEXTURE;
            case Off -> FIREFLY_TEXTURE;
        };
    }

    private void clearParticles() {
        fireFlies.clear();
        particles.clear();
    }

    private Color resolveColor(int offset) {
        return colorMode.is(ColorMode.Sync) ? syncColor(offset) : color.getValue();
    }

    private static Color syncColor(int offset) {
        float hue = Mth.frac((System.currentTimeMillis() + offset * 20L) / 4500.0f);
        Color rainbow = Color.getHSBColor(hue, 0.65f, 1.0f);
        return new Color(rainbow.getRed(), rainbow.getGreen(), rainbow.getBlue(), 255);
    }

    private static Color withAlpha(Color source, int alpha) {
        return new Color(source.getRed(), source.getGreen(), source.getBlue(), Mth.clamp(alpha, 0, 255));
    }

    private static float random(float min, float max) {
        return (float) ThreadLocalRandom.current().nextDouble(min, max);
    }

    private enum ColorMode {
        Custom,
        Sync
    }

    private enum Mode {
        Off,
        SnowFlake,
        Stars,
        Hearts,
        Dollars,
        Bloom
    }

    private enum Physics {
        Drop,
        Fly
    }

    public class FireFly extends ParticleBase {
        private final List<Trail> trails = new ArrayList<>();

        public FireFly(float posX, float posY, float posZ, float motionX, float motionY, float motionZ) {
            super(posX, posY, posZ, motionX, motionY, motionZ);
        }

        @Override
        public boolean tick() {
            if (mc.player.distanceToSqr(posX, posY, posZ) > 100.0) {
                age -= 4;
            } else if (!mc.level.getBlockState(new BlockPos((int) posX, (int) posY, (int) posZ)).isAir()) {
                age -= 8;
            } else {
                age--;
            }

            if (age < 0) {
                return true;
            }

            trails.removeIf(Trail::update);

            prevposX = posX;
            prevposY = posY;
            prevposZ = posZ;

            posX += motionX;
            posY += motionY;
            posZ += motionZ;

            trails.add(new Trail(
                    new Vec3(prevposX, prevposY, prevposZ),
                    new Vec3(posX, posY, posZ),
                    resolveColor(age * 10)
            ));

            motionX *= 0.99f;
            motionY *= 0.99f;
            motionZ *= 0.99f;

            return false;
        }

        @Override
        public void render(PoseStack poseStack, BufferBuilder buffer) {
            if (trails.isEmpty()) return;

            float tickDelta = getTickDelta();
            float particleSize = ffsize.getValue().floatValue();
            for (Trail trail : trails) {
                Vec3 position = trail.interpolate(tickDelta);
                int alpha = (int) (255.0f * ((float) age / (float) maxAge) * trail.animation(tickDelta));
                drawBillboard(poseStack, buffer, position, particleSize, withAlpha(trail.color(), alpha));
            }
        }
    }

    public class ParticleBase {
        protected float prevposX;
        protected float prevposY;
        protected float prevposZ;
        protected float posX;
        protected float posY;
        protected float posZ;
        protected float motionX;
        protected float motionY;
        protected float motionZ;
        protected int age;
        protected int maxAge;

        public ParticleBase(float posX, float posY, float posZ, float motionX, float motionY, float motionZ) {
            this.posX = posX;
            this.posY = posY;
            this.posZ = posZ;
            this.prevposX = posX;
            this.prevposY = posY;
            this.prevposZ = posZ;
            this.motionX = motionX;
            this.motionY = motionY;
            this.motionZ = motionZ;
            this.age = (int) random(100.0f, 300.0f);
            this.maxAge = age;
        }

        public boolean tick() {
            if (mc.player.distanceToSqr(posX, posY, posZ) > 4096.0) {
                age -= 8;
            } else {
                age--;
            }

            if (age < 0) {
                return true;
            }

            prevposX = posX;
            prevposY = posY;
            prevposZ = posZ;

            posX += motionX;
            posY += motionY;
            posZ += motionZ;

            motionX *= 0.9f;
            if (physics.is(Physics.Fly)) {
                motionY *= 0.9f;
            }
            motionZ *= 0.9f;
            motionY -= 0.001f;

            return false;
        }

        public void render(PoseStack poseStack, BufferBuilder buffer) {
            Color particleColor = withAlpha(resolveColor(age * 2), (int) (255.0f * ((float) age / (float) maxAge)));
            drawBillboard(poseStack, buffer, interpolatePos(), size.getValue().floatValue(), particleColor);
        }

        protected Vec3 interpolatePos() {
            float tickDelta = getTickDelta();
            Vec3 cameraPos = mc.gameRenderer.getMainCamera().position();
            double x = Mth.lerp(tickDelta, prevposX, posX) - cameraPos.x;
            double y = Mth.lerp(tickDelta, prevposY, posY) - cameraPos.y;
            double z = Mth.lerp(tickDelta, prevposZ, posZ) - cameraPos.z;
            return new Vec3(x, y, z);
        }

        protected void drawBillboard(PoseStack poseStack, BufferBuilder buffer, Vec3 position, float particleSize, Color particleColor) {
            Camera camera = mc.gameRenderer.getMainCamera();

            poseStack.pushPose();
            poseStack.translate(position.x, position.y, position.z);
            poseStack.mulPose(Axis.YP.rotationDegrees(-camera.yRot()));
            poseStack.mulPose(Axis.XP.rotationDegrees(camera.xRot()));

            Matrix4f matrix = poseStack.last().pose();
            int argb = particleColor.getRGB();

            buffer.addVertex(matrix, 0.0f, -particleSize, 0.0f).setUv(0.0f, 1.0f).setColor(argb);
            buffer.addVertex(matrix, -particleSize, -particleSize, 0.0f).setUv(1.0f, 1.0f).setColor(argb);
            buffer.addVertex(matrix, -particleSize, 0.0f, 0.0f).setUv(1.0f, 0.0f).setColor(argb);
            buffer.addVertex(matrix, 0.0f, 0.0f, 0.0f).setUv(0.0f, 0.0f).setColor(argb);

            poseStack.popPose();
        }
    }

    public static class Trail {
        private final Vec3 from;
        private final Vec3 to;
        private final Color color;
        private int ticks = 10;
        private int prevTicks = 10;

        public Trail(Vec3 from, Vec3 to, Color color) {
            this.from = from;
            this.to = to;
            this.color = color;
        }

        public Vec3 interpolate(float tickDelta) {
            Camera camera = INSTANCE.mc.gameRenderer.getMainCamera();
            double x = Mth.lerp(tickDelta, from.x, to.x) - camera.position().x;
            double y = Mth.lerp(tickDelta, from.y, to.y) - camera.position().y;
            double z = Mth.lerp(tickDelta, from.z, to.z) - camera.position().z;
            return new Vec3(x, y, z);
        }

        public double animation(float tickDelta) {
            return (prevTicks + (ticks - prevTicks) * tickDelta) / 10.0;
        }

        public boolean update() {
            prevTicks = ticks;
            return ticks-- <= 0;
        }

        public Color color() {
            return color;
        }
    }

    private static float getTickDelta() {
        return INSTANCE.mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }
}
