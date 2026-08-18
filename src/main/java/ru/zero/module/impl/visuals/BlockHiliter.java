package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.World;
import ru.zero.event.EventInit;
import ru.zero.event.render.WorldRenderEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.world.WorldRenderer;

@Environment(EnvType.CLIENT)
@IModule(
      name = "BlockHiliter",
      description = "Кастомная подсветка блока (вместо ванильной)",
      category = Category.Visuals,
      bind = -1
)
public class BlockHiliter extends Module {
   private static final int BASE_COLOR = Renderer2D.ColorUtil.getMainColor(1, 1);
   private static final int MAX_SHAPE_BOXES_DETAILED = 12;
   private static final double MAX_RENDER_DISTANCE_SQ = 12.0 * 12.0;

   public static BooleanSetting replaceVanilla = new BooleanSetting("Заменить ванилу", true);
   public static BooleanSetting fill = new BooleanSetting("Заливка", true);
   public static BooleanSetting outline = new BooleanSetting("Обводка", true);
   public static BooleanSetting ignoreDepth = new BooleanSetting("Поверх мира", true);

   public static SliderSetting lineWidth = new SliderSetting("Толщина", 2.0F, 0.5F, 6.0F, 0.1F, false);
   public static SliderSetting fillAlpha = new SliderSetting("Прозрачность (заливка)", 70.0F, 0.0F, 220.0F, 1.0F, false);
   public static SliderSetting outlineAlpha = new SliderSetting("Прозрачность (обводка)", 210.0F, 0.0F, 255.0F, 1.0F, false);

   public BlockHiliter() {
      this.addSettings(new Setting[] {
            replaceVanilla,
            fill,
            outline,
            ignoreDepth,
            lineWidth,
            fillAlpha,
            outlineAlpha
      });
   }

   @EventInit
   public void onWorldRender(WorldRenderEvent event) {
      if (mc.world == null || mc.player == null) {
         return;
      }

      if (mc.crosshairTarget == null || mc.crosshairTarget.getType() != HitResult.Type.BLOCK) {
         return;
      }

      BlockHitResult bhr = (BlockHitResult) mc.crosshairTarget;
      BlockPos pos = bhr.getBlockPos();
      renderHighlightForBlock(event.worldRenderer(), mc.world, pos, mc.player, BASE_COLOR);
   }

   public static void renderHighlightForBlock(WorldRenderer renderer, World world, BlockPos pos, Entity viewer, int baseColor) {
      if (renderer == null || world == null || pos == null || viewer == null) {
         return;
      }
      Vec3d center = Vec3d.ofCenter(pos);
      if (viewer.squaredDistanceTo(center) > MAX_RENDER_DISTANCE_SQ) {
         return;
      }
      double width = Math.max(0.1, lineWidth.get());
      boolean depthTest = !ignoreDepth.get();
      BlockState state = world.getBlockState(pos);
      VoxelShape shape = state.getOutlineShape(world, pos, ShapeContext.of(viewer));
      if (shape.isEmpty()) {
         return;
      }
      java.util.List<Box> shapeBoxes = shape.getBoundingBoxes();
      boolean simplified = shapeBoxes.size() > MAX_SHAPE_BOXES_DETAILED;
      Box fallbackBox = shape.getBoundingBox();

      if (fill.get()) {
         int fillColor = ColorUtil.replAlpha(baseColor, (int) Math.max(0, Math.min(255, fillAlpha.get())));
         java.util.List<Box> fillBoxes = simplified ? java.util.List.of(fallbackBox) : shapeBoxes;
         for (Box box : fillBoxes) {
            Vec3d min = new Vec3d(pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ);
            Vec3d max = new Vec3d(pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ);
            renderer.drawCube(min, max, fillColor, depthTest);
         }
      }

      if (outline.get()) {
         int outlineColor = ColorUtil.replAlpha(baseColor, (int) Math.max(0, Math.min(255, outlineAlpha.get())));
         double edgeSize = Math.max(0.004, width * 0.01);
         java.util.List<Box> outlineBoxes = simplified ? java.util.List.of(fallbackBox) : shapeBoxes;
         for (Box box : outlineBoxes) {
            Vec3d min = new Vec3d(pos.getX() + box.minX, pos.getY() + box.minY, pos.getZ() + box.minZ);
            Vec3d max = new Vec3d(pos.getX() + box.maxX, pos.getY() + box.maxY, pos.getZ() + box.maxZ);
            Vec3d b0 = new Vec3d(min.x, min.y, min.z);
            Vec3d b1 = new Vec3d(max.x, min.y, min.z);
            Vec3d b2 = new Vec3d(max.x, min.y, max.z);
            Vec3d b3 = new Vec3d(min.x, min.y, max.z);
            Vec3d t0 = new Vec3d(min.x, max.y, min.z);
            Vec3d t1 = new Vec3d(max.x, max.y, min.z);
            Vec3d t2 = new Vec3d(max.x, max.y, max.z);
            Vec3d t3 = new Vec3d(min.x, max.y, max.z);
            drawEdge(renderer, b0, b1, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, b1, b2, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, b2, b3, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, b3, b0, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, t0, t1, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, t1, t2, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, t2, t3, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, t3, t0, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, b0, t0, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, b1, t1, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, b2, t2, edgeSize, outlineColor, depthTest);
            drawEdge(renderer, b3, t3, edgeSize, outlineColor, depthTest);
         }
      }
   }

   private static void drawEdge(WorldRenderer renderer, Vec3d start, Vec3d end, double halfThickness, int color, boolean depthTest) {
      double minX = Math.min(start.x, end.x) - halfThickness;
      double minY = Math.min(start.y, end.y) - halfThickness;
      double minZ = Math.min(start.z, end.z) - halfThickness;
      double maxX = Math.max(start.x, end.x) + halfThickness;
      double maxY = Math.max(start.y, end.y) + halfThickness;
      double maxZ = Math.max(start.z, end.z) + halfThickness;
      renderer.drawCube(new Vec3d(minX, minY, minZ), new Vec3d(maxX, maxY, maxZ), color, depthTest);
   }
}

