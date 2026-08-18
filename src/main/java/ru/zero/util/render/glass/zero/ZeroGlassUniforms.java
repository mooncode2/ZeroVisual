package ru.zero.util.render.glass.zero;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.util.math.ColorHelper;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

public final class ZeroGlassUniforms {
   public static final int MAX_WIDGETS = 64;
   public static final int MAX_BLUR_LEVELS = 5;
   public static final int SHARED_BLUR_RADIUS = 20;
   public static final int MAX_BLUR_RADIUS_CAP = 48;
   private static final ZeroGlassUniforms INSTANCE = new ZeroGlassUniforms();
   private final GpuBuffer samplerInfo = RenderSystem.getDevice().createBuffer(() -> "zero SamplerInfo", 130, 16L);
   private final GpuBuffer customUniforms;
   private final GpuBuffer widgetInfo;
   private final GpuBuffer bgConfig;
   private final List<ZeroGlassElementRenderState> widgets = new ArrayList();
   private final HashMap<Integer, Integer> blurRadiusToIndex = new HashMap();
   private final List<Integer> usedBlurRadiiOrdered = new ArrayList();
   private final HashMap<Integer, int[]> bboxByRadius = new HashMap();
   private final HashSet<Integer> requestedRadii = new HashSet();
   private int[] compositeScissor = null;
   private boolean screenWantsBlur = false;
   private double dtSeconds = (double)0.0F;
   // Кэш размеров: samplerInfo/bgConfig перезаливаем только при смене размера fb/guiScale,
   // а не каждый кадр — убирает 2 mapBuffer/close (CPU-GPU sync) на кадр.
   private int lastSamplerW = -1;
   private int lastSamplerH = -1;
   private float lastBgScale = -1.0F;
   private int lastWidgetSignature = Integer.MIN_VALUE;
   private final double[] mouseBufX = new double[1];
   private final double[] mouseBufY = new double[1];
   private final org.joml.Vector4f reusableMouseVec = new org.joml.Vector4f();
   private final org.joml.Vector3f reusableBgVec = new org.joml.Vector3f(0.55F, -0.45F, 0.0F);

   private ZeroGlassUniforms() {
      Std140SizeCalculator calc = new Std140SizeCalculator();
      calc.putFloat();
      calc.align(16);
      calc.putVec4();
      calc.putFloat();
      calc.align(16);
      calc.putVec3();
      calc.align(16);
      calc.putVec4();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      calc.putFloat();
      this.customUniforms = RenderSystem.getDevice().createBuffer(() -> "zero CustomUniforms", 130, (long)calc.get());
      this.widgetInfo = RenderSystem.getDevice().createBuffer(() -> "zero WidgetInfo", 130, 12304L);
      Std140SizeCalculator bgCalc = new Std140SizeCalculator();
      bgCalc.putFloat();
      bgCalc.putFloat();
      bgCalc.putVec2();
      this.bgConfig = RenderSystem.getDevice().createBuffer(() -> "zero BgConfig", 130, (long)bgCalc.get());
   }

   public static ZeroGlassUniforms get() {
      return INSTANCE;
   }

   public void beginFrame(double dtSeconds) {
      this.dtSeconds = Math.max((double)0.0F, dtSeconds);
   }

   public void clearWidgets() {
      this.widgets.clear();
      this.screenWantsBlur = false;
      this.usedBlurRadiiOrdered.clear();
      this.blurRadiusToIndex.clear();
      this.bboxByRadius.clear();
      this.requestedRadii.clear();
      this.compositeScissor = null;
   }

   public HashMap<Integer, int[]> getBboxByRadius() {
      return this.bboxByRadius;
   }

   public int[] getCompositeScissor() {
      return this.compositeScissor;
   }

   private static int snapBlurRadius(int radius) {
      int r = Math.min(Math.max(radius, 0), MAX_BLUR_RADIUS_CAP);
      return r > 0 ? SHARED_BLUR_RADIUS : 0;
   }

   public void tryApplyBlur(DrawContext context) {
      // No-op: glass composite выполняется в GameRendererMixin.zero$drawGlassUnderHud.
      // Раньше здесь вызывался state.applyBlur() для запуска ванильного blur-конвейера,
      // но это конфликтует с Screen.renderBackground, который тоже вызывает applyBlur() —
      // "Can only blur once per frame". Поскольку composite мы делаем сами, ванильный
      // blur нам не нужен.
   }

   public void addWidget(ZeroGlassElementRenderState element) {
      if (this.widgets.size() < 64) {
         this.widgets.add(element);
      }

   }

   public void uploadSharedUniforms() {
      MinecraftClient client = MinecraftClient.getInstance();
      int outWidth = client.getFramebuffer().textureWidth;
      int outHeight = client.getFramebuffer().textureHeight;
      if (outWidth != this.lastSamplerW || outHeight != this.lastSamplerH) {
         GpuBuffer.MappedView map = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.samplerInfo, false, true);

         try {
            Std140Builder builder = Std140Builder.intoBuffer(map.data());
            builder.putVec2((float)outWidth, (float)outHeight);
            builder.putVec2((float)outWidth, (float)outHeight);
         } catch (Throwable var17) {
            if (map != null) {
               try {
                  map.close();
               } catch (Throwable var14) {
                  var17.addSuppressed(var14);
               }
            }

            throw var17;
         }

         if (map != null) {
            map.close();
         }

         this.lastSamplerW = outWidth;
         this.lastSamplerH = outHeight;
      }

      GLFW.glfwGetCursorPos(client.getWindow().getHandle(), this.mouseBufX, this.mouseBufY);
      float scale = (float)client.getWindow().getScaleFactor();
      int framebufferHeight = client.getFramebuffer().textureHeight;
      GpuBuffer.MappedView map = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.customUniforms, false, true);

      try {
         Std140Builder builder = Std140Builder.intoBuffer(map.data());
         builder.putFloat((float)GLFW.glfwGetTime());
         builder.align(16);
         float scaledMouseX = (float)(this.mouseBufX[0] * (double)scale);
         float scaledMouseY = (float)framebufferHeight - (float)(this.mouseBufY[0] * (double)scale);
         this.reusableMouseVec.set(scaledMouseX, scaledMouseY, 0.0F, 0.0F);
         builder.putVec4(this.reusableMouseVec);
         builder.putFloat(this.screenWantsBlur ? 1.0F : 0.0F);
         builder.align(16);
         builder.putVec3(this.reusableBgVec);
         builder.align(16);
         builder.putVec4(1.0F, 1.0F, 1.0F, 0.08F);
         builder.putFloat(0.75F);
         builder.putFloat(2.0F);
         builder.putFloat(0.0F);
         builder.putFloat(8.0F);
         builder.putFloat(0.0F);
         builder.putFloat(0.0F);
         builder.putFloat(0.0F);
         builder.putFloat(0.0F);
         builder.putFloat((float)this.dtSeconds);
      } catch (Throwable var16) {
         if (map != null) {
            try {
               map.close();
            } catch (Throwable var13) {
               var16.addSuppressed(var13);
            }
         }

         throw var16;
      }

      if (map != null) {
         map.close();
      }

      if (scale != this.lastBgScale) {
         map = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.bgConfig, false, true);

         try {
            Std140Builder builder = Std140Builder.intoBuffer(map.data());
            builder.putFloat(20.0F);
            builder.putFloat(0.85F);
            builder.putVec2(0.0F, 2.0F * scale);
         } catch (Throwable var15) {
            if (map != null) {
               try {
                  map.close();
               } catch (Throwable var12) {
                  var15.addSuppressed(var12);
               }
            }

            throw var15;
         }

         if (map != null) {
            map.close();
         }

         this.lastBgScale = scale;
      }

   }

   public void uploadWidgetInfo() {
      MinecraftClient client = MinecraftClient.getInstance();
      int framebufferHeight = client.getFramebuffer().textureHeight;
      int framebufferWidth = client.getFramebuffer().textureWidth;
      float scale = (float)client.getWindow().getScaleFactor();
      this.requestedRadii.clear();

      for(ZeroGlassElementRenderState widget : this.widgets) {
         this.requestedRadii.add(snapBlurRadius(widget.style().getBlurRadius()));
      }

      this.bboxByRadius.clear();
      int uMinX = Integer.MAX_VALUE;
      int uMinY = Integer.MAX_VALUE;
      int uMaxX = Integer.MIN_VALUE;
      int uMaxY = Integer.MIN_VALUE;
      int maxPad = 0;

      for(ZeroGlassElementRenderState widget : this.widgets) {
         int radius = snapBlurRadius(widget.style().getBlurRadius());
         ZeroGlassStyle st = widget.style();
         float w = (float)(widget.x2() - widget.x1());
         float h = (float)(widget.y2() - widget.y1());
         float px = (float)widget.x1() * scale;
         float pyTop = (float)widget.y1() * scale;
         float scaledWidth = w * scale;
         float scaledHeight = h * scale;
         int x0 = (int)Math.floor((double)px);
         int y0 = (int)Math.floor((double)((float)framebufferHeight - (pyTop + scaledHeight)));
         int x1 = (int)Math.ceil((double)(px + scaledWidth));
         int y1 = (int)Math.ceil((double)((float)framebufferHeight - pyTop));
         uMinX = Math.min(uMinX, x0);
         uMinY = Math.min(uMinY, y0);
         uMaxX = Math.max(uMaxX, x1);
         uMaxY = Math.max(uMaxY, y1);
         int wpad = (int)Math.ceil((double)st.getShadowExpand()) + (int)Math.ceil((double)Math.abs(st.getShadowOffsetX())) + (int)Math.ceil((double)Math.abs(st.getShadowOffsetY())) + (int)Math.ceil((double)st.getRefThickness());
         maxPad = Math.max(maxPad, wpad);
         if (radius > 0) {
            int[] existing = (int[])this.bboxByRadius.get(radius);
            if (existing == null) {
               this.bboxByRadius.put(radius, new int[]{x0, y0, x1 - x0, y1 - y0});
            } else {
               int ex0 = existing[0];
               int ey0 = existing[1];
               int ex1 = ex0 + existing[2];
               int ey1 = ey0 + existing[3];
               int ux0 = Math.min(ex0, x0);
               int uy0 = Math.min(ey0, y0);
               int ux1 = Math.max(ex1, x1);
               int uy1 = Math.max(ey1, y1);
               existing[0] = ux0;
               existing[1] = uy0;
               existing[2] = ux1 - ux0;
               existing[3] = uy1 - uy0;
            }
         }
      }

      if (uMinX != Integer.MAX_VALUE) {
         int pad = maxPad + 8;
         int sx0 = Math.max(0, uMinX - pad);
         int sy0 = Math.max(0, uMinY - pad);
         int sx1 = Math.min(framebufferWidth, uMaxX + pad);
         int sy1 = Math.min(framebufferHeight, uMaxY + pad);
         if (sx1 > sx0 && sy1 > sy0) {
            this.compositeScissor = new int[]{sx0, sy0, sx1 - sx0, sy1 - sy0};
         } else {
            this.compositeScissor = null;
         }
      } else {
         this.compositeScissor = null;
      }

      this.usedBlurRadiiOrdered.clear();
      this.usedBlurRadiiOrdered.addAll(this.requestedRadii);
      this.usedBlurRadiiOrdered.sort(null);
      while (this.usedBlurRadiiOrdered.size() > 5) {
         this.usedBlurRadiiOrdered.remove(this.usedBlurRadiiOrdered.size() - 1);
      }
      if (this.usedBlurRadiiOrdered.isEmpty()) {
         this.usedBlurRadiiOrdered.add(0);
      }

      this.blurRadiusToIndex.clear();

      for(int i = 0; i < this.usedBlurRadiiOrdered.size(); ++i) {
         this.blurRadiusToIndex.put((Integer)this.usedBlurRadiiOrdered.get(i), i);
      }

      // WidgetInfo (~12KB) перезаливаем только когда реально изменилась геометрия/стиль
      // виджетов или размер fb. Пока GUI статичен (типичный кадр) — mapBuffer и весь
      // std140-билд из 704 vec4 пропускаются, что снимает CPU-GPU sync каждый кадр.
      int widgetSignature = this.computeWidgetSignature(framebufferWidth, framebufferHeight, scale);
      if (widgetSignature == this.lastWidgetSignature) {
         return;
      }
      this.lastWidgetSignature = widgetSignature;

      GpuBuffer.MappedView map = RenderSystem.getDevice().createCommandEncoder().mapBuffer(this.widgetInfo, false, true);

      try {
         Std140Builder builder = Std140Builder.intoBuffer(map.data());
         builder.putFloat((float)this.widgets.size());
         builder.align(16);

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               ZeroGlassElementRenderState widget = (ZeroGlassElementRenderState)this.widgets.get(i);
               float width = (float)(widget.x2() - widget.x1());
               float height = (float)(widget.y2() - widget.y1());
               float px = (float)widget.x1() * scale;
               float pyTop = (float)widget.y1() * scale;
               float scaledWidth = width * scale;
               float scaledHeight = height * scale;
               float centerX = px + 0.5F * scaledWidth;
               float centerY = pyTop + 0.5F * scaledHeight;
               float centerFramebufferY = (float)framebufferHeight - centerY;
               builder.putVec4(centerX - 0.5F * scaledWidth, centerFramebufferY - 0.5F * scaledHeight, scaledWidth, scaledHeight);
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               float radius = ((ZeroGlassElementRenderState)this.widgets.get(i)).cornerRadius() * scale;
               builder.putVec4(radius, radius, radius, radius);
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               ZeroGlassStyle style = ((ZeroGlassElementRenderState)this.widgets.get(i)).style();
               int color = style.getTintColor();
               builder.putVec4((float)ColorHelper.getRed(color) / 255.0F, (float)ColorHelper.getGreen(color) / 255.0F, (float)ColorHelper.getBlue(color) / 255.0F, style.getTintAlpha());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               ZeroGlassStyle style = ((ZeroGlassElementRenderState)this.widgets.get(i)).style();
               builder.putVec4(style.getRefThickness(), style.getRefFactor(), style.getRefDispersion(), style.getRefFresnelRange());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               ZeroGlassStyle style = ((ZeroGlassElementRenderState)this.widgets.get(i)).style();
               builder.putVec4(style.getRefFresnelHardness(), style.getRefFresnelFactor(), style.getGlareRange(), style.getGlareHardness());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               ZeroGlassStyle style = ((ZeroGlassElementRenderState)this.widgets.get(i)).style();
               builder.putVec4(style.getGlareConvergence(), style.getGlareOppositeFactor(), style.getGlareFactor(), style.getGlareAngleRad());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               builder.putVec4(((ZeroGlassElementRenderState)this.widgets.get(i)).style().getSmoothing(), 0.0F, 0.0F, 0.0F);
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               ScreenRect scissor = ((ZeroGlassElementRenderState)this.widgets.get(i)).scissorArea();
               if (scissor != null) {
                  float left = (float)scissor.getLeft() * scale;
                  float right = (float)scissor.getRight() * scale;
                  float top = (float)scissor.getTop() * scale;
                  float bottom = (float)scissor.getBottom() * scale;
                  builder.putVec4(left, (float)framebufferHeight - bottom, right, (float)framebufferHeight - top);
               } else {
                  builder.putVec4(0.0F, 0.0F, (float)framebufferWidth, (float)framebufferHeight);
               }
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               ZeroGlassStyle style = ((ZeroGlassElementRenderState)this.widgets.get(i)).style();
               builder.putVec4(style.getShadowExpand(), style.getShadowFactor(), style.getShadowOffsetX() * scale, style.getShadowOffsetY() * scale);
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

         for(int i = 0; i < 64; ++i) {
            if (i < this.widgets.size()) {
               ZeroGlassStyle style = ((ZeroGlassElementRenderState)this.widgets.get(i)).style();
               int color = style.getShadowColor();
               builder.putVec4((float)ColorHelper.getRed(color) / 255.0F, (float)ColorHelper.getGreen(color) / 255.0F, (float)ColorHelper.getBlue(color) / 255.0F, style.getShadowColorAlpha());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }

          for(int i = 0; i < 64; ++i) {
             if (i < this.widgets.size()) {
                int radius = snapBlurRadius(((ZeroGlassElementRenderState)this.widgets.get(i)).style().getBlurRadius());
                int index = (Integer)this.blurRadiusToIndex.getOrDefault(radius, 0);
                builder.putVec4((float)index, ((ZeroGlassElementRenderState)this.widgets.get(i)).hover(), ((ZeroGlassElementRenderState)this.widgets.get(i)).focus(), ((ZeroGlassElementRenderState)this.widgets.get(i)).style().getOpacity());
            } else {
               builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
            }
         }
      } catch (Throwable var29) {
         if (map != null) {
            try {
               map.close();
            } catch (Throwable var28) {
               var29.addSuppressed(var28);
            }
         }

         throw var29;
      }

      if (map != null) {
         map.close();
      }

   }

   public int getCount() {
      return this.widgets.size();
   }

   /**
    * Дешёвый отпечаток всего, что влияет на содержимое WidgetInfo UBO. Меняется —
    * значит нужно перезалить буфер; совпал — заливку можно пропустить.
    */
   private int computeWidgetSignature(int fbWidth, int fbHeight, float scale) {
      int h = 17;
      h = 31 * h + fbWidth;
      h = 31 * h + fbHeight;
      h = 31 * h + Float.floatToIntBits(scale);
      h = 31 * h + this.widgets.size();

      for (ZeroGlassElementRenderState widget : this.widgets) {
         h = 31 * h + widget.x1();
         h = 31 * h + widget.y1();
         h = 31 * h + widget.x2();
         h = 31 * h + widget.y2();
         h = 31 * h + Float.floatToIntBits(widget.cornerRadius());
         h = 31 * h + Float.floatToIntBits(widget.hover());
         h = 31 * h + Float.floatToIntBits(widget.focus());

         ZeroGlassStyle style = widget.style();
         h = 31 * h + style.getTintColor();
         h = 31 * h + Float.floatToIntBits(style.getTintAlpha());
         h = 31 * h + Float.floatToIntBits(style.getOpacity());
         h = 31 * h + Float.floatToIntBits(style.getRefThickness());
         h = 31 * h + Float.floatToIntBits(style.getSmoothing());
         h = 31 * h + style.getBlurRadius();

         ScreenRect scissor = widget.scissorArea();
         if (scissor != null) {
            h = 31 * h + scissor.getLeft();
            h = 31 * h + scissor.getTop();
            h = 31 * h + scissor.getRight();
            h = 31 * h + scissor.getBottom();
         }
      }

      return h;
   }

   public GpuBuffer getSamplerInfoBuffer() {
      return this.samplerInfo;
   }

   public GpuBuffer getCustomUniformsBuffer() {
      return this.customUniforms;
   }

   public GpuBuffer getWidgetInfoBuffer() {
      return this.widgetInfo;
   }

   public GpuBuffer getBgConfigBuffer() {
      return this.bgConfig;
   }

   public List<Integer> getUsedBlurRadiiOrdered() {
      return this.usedBlurRadiiOrdered;
   }
}
