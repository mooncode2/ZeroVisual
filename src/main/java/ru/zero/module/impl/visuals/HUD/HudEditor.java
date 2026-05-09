package ru.zero.module.impl.visuals.HUD;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import org.lwjgl.glfw.GLFW;
import ru.zero.Zero;
import ru.zero.event.input.MouseButtonEvent;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public final class HudEditor {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final float MIN_SCALE = 0.5F;
   private static final float MAX_SCALE = 2.0F;
   private static final float POPUP_WIDTH = 170.0F;
   private static final float POPUP_HEIGHT = 58.0F;
   private static final float SLIDER_X = 12.0F;
   private static final float SLIDER_Y = 36.0F;
   private static final float SLIDER_W = 146.0F;
   private static final float SLIDER_H = 4.0F;
   private static final Map<String, Float> elementScales = new LinkedHashMap<>();
   private static final Map<String, HudEditor.Bounds> elementBounds = new LinkedHashMap<>();
   private static final Map<String, HudEditor.Bounds> frameBounds = new HashMap<>();
   private static HudEditor.RenderContext currentContext;
   private static boolean popupOpen = false;
   private static String selectedElementId;
   private static float popupX;
   private static float popupY;
   private static boolean sliderDragging = false;

   private HudEditor() {
   }

   public static float getOriginX(String id) {
      HudEditor.Bounds b = elementBounds.get(id);
      return b != null ? b.x : 0.0F;
   }

   public static float getOriginY(String id) {
      HudEditor.Bounds b = elementBounds.get(id);
      return b != null ? b.y : 0.0F;
   }

   public static void renderElement(String id, Renderer2D renderer, Runnable renderCall) {
      if (renderer != null && renderCall != null && id != null && !id.isBlank()) {
         float scale = getScale(id);
         HudEditor.Bounds previous = elementBounds.get(id);
         float originX = previous != null ? previous.x : 0.0F;
         float originY = previous != null ? previous.y : 0.0F;
         frameBounds.remove(id);
         currentContext = new HudEditor.RenderContext(id, scale, originX, originY);
         renderer.pushScale(scale, scale, originX, originY);

         try {
            renderCall.run();
         } catch (Throwable ignored) {
            // Fail-safe: one broken HUD element must not disable entire HUD rendering.
            currentContext = null;

            try {
               renderCall.run();
            } catch (Throwable ignoredFallback) {
            }

            return;
         } finally {
            try {
               renderer.popScale();
            } catch (Throwable ignored) {
            }

            currentContext = null;
         }

         HudEditor.Bounds frame = frameBounds.get(id);
         if (frame != null) {
            elementBounds.put(id, frame);
         }
      }
   }

   public static void registerRect(float x, float y, float w, float h) {
      if (currentContext != null && !(w <= 0.0F) && !(h <= 0.0F)) {
         float scale = currentContext.scale;
         float transformedX = currentContext.originX + (x - currentContext.originX) * scale;
         float transformedY = currentContext.originY + (y - currentContext.originY) * scale;
         float transformedW = w * scale;
         float transformedH = h * scale;
         HudEditor.Bounds next = new HudEditor.Bounds(transformedX, transformedY, transformedW, transformedH);
         frameBounds.merge(currentContext.id, next, HudEditor::union);
      }
   }

   public static void renderPopup(Renderer2D renderer) {
      if (renderer != null && popupOpen && selectedElementId != null) {
         if (sliderDragging) {
            tickSliderDrag();
         }

         int bg = ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), 235);
         int outline = ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), 60);
         renderer.rect(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, 7.0F, bg);
         renderer.rectOutline(popupX, popupY, POPUP_WIDTH, POPUP_HEIGHT, 7.0F, outline, 1.0F);
         renderer.text(FontRegistry.INTER_MEDIUM, popupX + 10.0F, popupY + 14.0F, 18.0F, "Размер: " + selectedElementId, Renderer2D.ColorUtil.getTextColor(1, 1));
         float scale = getScale(selectedElementId);
         float progress = (scale - MIN_SCALE) / (MAX_SCALE - MIN_SCALE);
         float sx = popupX + SLIDER_X;
         float sy = popupY + SLIDER_Y;
         renderer.rect(sx, sy, SLIDER_W, SLIDER_H, 2.0F, ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 35));
         renderer.rect(sx, sy, SLIDER_W * progress, SLIDER_H, 2.0F, ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 140));
         float knobX = sx + SLIDER_W * progress;
         renderer.rect(knobX - 2.0F, sy - 3.0F, 4.0F, 10.0F, 2.0F, Renderer2D.ColorUtil.getMainColor(1, 1));
         renderer.text(FontRegistry.INTER_MEDIUM, popupX + 10.0F, popupY + 52.0F, 16.0F, String.format("%.2fx", scale), Renderer2D.ColorUtil.getTextColor(1, 1));
      }
   }

   public static void onMouseButton(MouseButtonEvent event) {
      if (event != null && mc.currentScreen instanceof ChatScreen) {
         float mx = (float)event.cursorX();
         float my = (float)event.cursorY();
         if (event.isPress() && event.button() == 1) {
            String hitId = findHitElement(mx, my);
            if (hitId != null) {
               selectedElementId = hitId;
               popupOpen = true;
               sliderDragging = false;
               popupX = mx + 6.0F;
               popupY = my + 6.0F;
               event.cancel();
            }
         }

         if (popupOpen && event.button() == 0) {
            if (event.isPress()) {
               if (isInsideSlider(mx, my)) {
                  sliderDragging = true;
                  setScaleFromMouse(mx);
                  event.cancel();
               } else if (!isInsidePopup(mx, my)) {
                  popupOpen = false;
                  sliderDragging = false;
               }
            } else if (event.isRelease()) {
               sliderDragging = false;
            }
         }
      }
   }

   public static void closeAllPopups() {
      popupOpen = false;
      sliderDragging = false;
      selectedElementId = null;
   }

   public static Map<String, Float> snapshotScales() {
      return new LinkedHashMap<>(elementScales);
   }

   public static void loadScales(Map<String, Float> scales) {
      elementScales.clear();
      if (scales != null) {
         scales.forEach((id, scale) -> {
            if (id != null && !id.isBlank()) {
               elementScales.put(id, clampScale(scale));
            }
         });
      }
   }

   public static float getScale(String id) {
      return clampScale(elementScales.getOrDefault(id, 1.0F));
   }

   private static void setScale(String id, float scale) {
      float clamped = clampScale(scale);
      float previous = getScale(id);
      if (Math.abs(previous - clamped) > 0.0005F) {
         elementScales.put(id, clamped);
         if (Zero.get != null && Zero.get.configManager != null) {
            Zero.get.configManager.autoSave();
         }
      }
   }

   private static void tickSliderDrag() {
      if (mc.getWindow() == null) {
         sliderDragging = false;
      } else {
         long handle = mc.getWindow().getHandle();
         if (handle == 0L || GLFW.glfwGetMouseButton(handle, 0) != 1) {
            sliderDragging = false;
         } else {
            double[] x = new double[1];
            double[] y = new double[1];
            GLFW.glfwGetCursorPos(handle, x, y);
            setScaleFromMouse((float)x[0]);
         }
      }
   }

   private static void setScaleFromMouse(float mouseX) {
      if (selectedElementId != null) {
         float sx = popupX + SLIDER_X;
         float progress = clamp01((mouseX - sx) / SLIDER_W);
         float scale = MIN_SCALE + (MAX_SCALE - MIN_SCALE) * progress;
         setScale(selectedElementId, scale);
      }
   }

   private static boolean isInsideSlider(float mx, float my) {
      float sx = popupX + SLIDER_X;
      float sy = popupY + SLIDER_Y - 6.0F;
      return mx >= sx && mx <= sx + SLIDER_W && my >= sy && my <= sy + 16.0F;
   }

   private static boolean isInsidePopup(float mx, float my) {
      return mx >= popupX && mx <= popupX + POPUP_WIDTH && my >= popupY && my <= popupY + POPUP_HEIGHT;
   }

   private static String findHitElement(float mx, float my) {
      for (Map.Entry<String, HudEditor.Bounds> entry : elementBounds.entrySet()) {
         HudEditor.Bounds bounds = entry.getValue();
         if (bounds != null && mx >= bounds.x && mx <= bounds.x + bounds.w && my >= bounds.y && my <= bounds.y + bounds.h) {
            return entry.getKey();
         }
      }

      return DraggableManager.getInstance().findHitElement(mx, my);
   }

   private static HudEditor.Bounds union(HudEditor.Bounds a, HudEditor.Bounds b) {
      float minX = Math.min(a.x, b.x);
      float minY = Math.min(a.y, b.y);
      float maxX = Math.max(a.x + a.w, b.x + b.w);
      float maxY = Math.max(a.y + a.h, b.y + b.h);
      return new HudEditor.Bounds(minX, minY, maxX - minX, maxY - minY);
   }

   private static float clampScale(float scale) {
      if (!Float.isFinite(scale)) {
         return 1.0F;
      } else if (scale < MIN_SCALE) {
         return MIN_SCALE;
      } else {
         return scale > MAX_SCALE ? MAX_SCALE : scale;
      }
   }

   private static float clamp01(float value) {
      if (value < 0.0F) {
         return 0.0F;
      } else {
         return value > 1.0F ? 1.0F : value;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class RenderContext {
      private final String id;
      private final float scale;
      private final float originX;
      private final float originY;

      private RenderContext(String id, float scale, float originX, float originY) {
         this.id = id;
         this.scale = scale;
         this.originX = originX;
         this.originY = originY;
      }
   }

   @Environment(EnvType.CLIENT)
   private static final class Bounds {
      private final float x;
      private final float y;
      private final float w;
      private final float h;

      private Bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
      }
   }
}
