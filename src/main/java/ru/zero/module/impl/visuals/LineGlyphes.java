package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventUpdate;
import ru.zero.event.render.WorldRenderEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.render.animation.util.Animation;
import ru.zero.util.render.animation.util.Easings;
import ru.zero.util.render.world.WorldGeometryEmitter;
import ru.zero.util.render.world.WorldRenderLayers;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@IModule(name = "LineGlyphes", description = "Рендерит глифические линии вокруг игрока", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class LineGlyphes extends Module {
    private static final int MAX_LINES = 200;

    private final SliderSetting count = new SliderSetting("Count", 20, 5, 100, 1, false);
    private final SliderSetting speed = new SliderSetting("Speed", 1.0f, 0.1f, 3.0f, 0.1f, false);
    private final SliderSetting thickness = new SliderSetting("Thickness", 1.0f, 0.1f, 3.0f, 0.1f, false);
    private final BooleanSetting glow = new BooleanSetting("Glow", false);

    private final List<Path> paths = new ArrayList<>();
    private final Random random = new Random();

    public LineGlyphes() {
        this.addSettings(new Setting[] { count, speed, glow, thickness });
    }

    @Override
    public void onDisable() {
        paths.clear();
        super.onDisable();
    }

    @EventInit
    public void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.world == null) {
            paths.clear();
            return;
        }

        if (paths.size() > MAX_LINES) {
            paths.removeIf(Path::isDead);
        } else {
            paths.removeIf(p -> p.isDead());
        }

        for (Path path : paths) {
            path.update(speed.get());
        }

        while (paths.size() < (int) count.get() && paths.size() < MAX_LINES) {
            paths.add(new Path(getRandomSpawnPos()));
        }
    }

    @EventInit
    public void onRender3D(WorldRenderEvent event) {
        if (paths.isEmpty() || mc.player == null) return;

        var renderer = event.worldRenderer();
        float lineWidth = thickness.get();
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();

        float yaw = mc.gameRenderer.getCamera().getYaw();
        float pitch = mc.gameRenderer.getCamera().getPitch();
        Vec3d lookVec = new Vec3d(
            -MathHelper.sin(yaw) * MathHelper.cos(pitch),
            -MathHelper.sin(pitch),
            MathHelper.cos(yaw) * MathHelper.cos(pitch)
        );

        var emitter = new WorldGeometryEmitter(
            renderer,
            renderer.matrixStack().peek(),
            renderer.getBuffer(WorldRenderLayers.LINES_NO_DEPTH(lineWidth))
        );
        WorldGeometryEmitter glowEmitter = null;
        if (glow.get()) {
            glowEmitter = new WorldGeometryEmitter(renderer, renderer.matrixStack().peek(),
                renderer.getBuffer(WorldRenderLayers.LINES_NO_DEPTH(lineWidth * 1.5f)));
        }

        int totalLines = 0;
        for (Path path : paths) {
            float alpha = path.getAlpha();
            if (alpha <= 0.01f) continue;

            List<Vec3d> points = path.getPoints(Math.round(renderer.tickDelta() * 1000));
            if (points.size() < 2) continue;

            int color = 0xFFFFFF | (Math.round(alpha * 255) << 24);
            int glowColor = 0xFFFFFF | (Math.round(alpha * 63) << 24);

            for (int i = 0; i < points.size() - 1; i++) {
                Vec3d start = points.get(i);
                Vec3d end = points.get(i + 1);

                Vec3d cameraRel = start.subtract(cameraPos);
                if (cameraRel.dotProduct(lookVec) < 0) continue;

                if (glowEmitter != null) {
                    glowEmitter.emitLineWithoutNormal(start, end, glowColor);
                }
                emitter.emitLineWithoutNormal(start, end, color);

                totalLines++;
                if (totalLines >= MAX_LINES) return;
            }
        }
    }

    private Vec3d getRandomSpawnPos() {
        if (mc.player == null) return Vec3d.ZERO;
        double range = 30.0;
        return new Vec3d(
            mc.player.getX() + (random.nextDouble() - 0.5) * range * 2,
            mc.player.getY() + (random.nextDouble() - 0.5) * 15,
            mc.player.getZ() + (random.nextDouble() - 0.5) * range * 2
        );
    }

    private static class Path {
        private final List<Vec3d> points = new ArrayList<>();
        private final Animation alphaAnim;
        private final int maxPoints;
        private boolean removing = false;
        private Vec3d lastDir = Vec3d.ZERO;
        private float moveProgress = 0;
        private float prevMoveProgress = 0;
        private static final Random RAND = new Random();

        public Path(Vec3d start) {
            points.add(start);
            this.alphaAnim = new Animation();
            this.alphaAnim.run(1.0, 1.2, Easings.QUAD_IN_OUT, false);
            this.maxPoints = 12 + RAND.nextInt(15);
        }

        public void update(float speedMul) {
            if (!removing) {
                prevMoveProgress = moveProgress;
                moveProgress += 0.025f * speedMul;
                if (moveProgress >= 1.0f) {
                    prevMoveProgress = 0;
                    moveProgress = 0;
                    addPoint();
                }

                if (points.size() >= maxPoints) {
                    removing = true;
                    alphaAnim.run(0, 0.5, Easings.QUAD_OUT, false);
                }
            }
            alphaAnim.update();
        }

        private void addPoint() {
            Vec3d last = points.get(points.size() - 1);
            Vec3d nextDir = getRandomDir();
            while (nextDir.dotProduct(lastDir) < -0.5) {
                nextDir = getRandomDir();
            }
            lastDir = nextDir;
            points.add(last.add(nextDir.multiply(2.5)));
        }

        private Vec3d getRandomDir() {
            int axis = RAND.nextInt(3);
            int dir = RAND.nextBoolean() ? 1 : -1;
            return switch (axis) {
                case 0 -> new Vec3d(dir, 0, 0);
                case 1 -> new Vec3d(0, dir, 0);
                default -> new Vec3d(0, 0, dir);
            };
        }

        private List<Vec3d> cachedPoints = new ArrayList<>();
        private int lastTick = -1;

        public List<Vec3d> getPoints(int tick) {
            if (points.size() < 2 || removing) return points;

            if (lastTick == tick && !cachedPoints.isEmpty()) {
                return cachedPoints;
            }

            cachedPoints.clear();
            for (Vec3d p : points) {
                cachedPoints.add(p);
            }

            int lastIdx = cachedPoints.size() - 1;
            Vec3d last = points.get(lastIdx);
            Vec3d prev = points.get(lastIdx - 1);
            float lerpProgress = prevMoveProgress + (moveProgress - prevMoveProgress);
            cachedPoints.set(lastIdx, prev.add((last.subtract(prev)).multiply(lerpProgress)));
            lastTick = tick;
            return cachedPoints;
        }

        public float getAlpha() {
            return MathHelper.clamp((float) alphaAnim.get(), 0, 1);
        }

        public boolean isDead() {
            return removing && alphaAnim.get() <= 0.01f;
        }
    }
}