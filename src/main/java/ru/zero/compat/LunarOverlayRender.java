package ru.zero.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.GlTexture;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import ru.zero.Zero;
import ru.zero.ui.gui.GuiClient;
import ru.zero.event.EventManager;
import ru.zero.event.impl.EventScreen;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.ui.gui.component.render.GuiRender;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.animation.AnimationSystem;
import ru.zero.util.render.backends.gl.GlState;
import ru.zero.util.render.text.FontRegistry;

/**
 * In-game / menu 2D overlay path for Lunar when {@link ru.zero.mixin.InGameHudMixin} is disabled.
 * Mirrors the FBO bind logic from InGameHud so HUD and GUI draw into the visible framebuffer.
 */
@Environment(EnvType.CLIENT)
public final class LunarOverlayRender {

   private static boolean menuRenderedThisFrame;

   private LunarOverlayRender() {
   }

   /** Call once per game frame before HUD / screens (not from {@code flipFrame}). */
   public static void beginFrame() {
      menuRenderedThisFrame = false;
   }

   public static boolean wasMenuRenderedThisFrame() {
      return menuRenderedThisFrame;
   }

   public static boolean isZeroMenuOpen(MinecraftClient client) {
      return client != null && client.currentScreen instanceof GuiClient;
   }

   /** In-game HUD overlay only — skip while inventory / other screens are open (avoids GL corruption). */
   public static boolean canRenderInGameHudOverlay(MinecraftClient client) {
      if (client == null || client.player == null || client.world == null || client.getWindow() == null) {
         return false;
      }

      Screen screen = client.currentScreen;
      return screen == null || screen instanceof ChatScreen;
   }

   public static void render(MinecraftClient client, DrawContext drawContext) {
      if (!LunarCompat.isLunarClient() || client == null || client.getWindow() == null) {
         return;
      }

      if (!canRenderInGameHudOverlay(client)) {
         return;
      }

      if (!EventManager.hasListeners(EventScreen.class)) {
         return;
      }

      runOverlay(client, (renderer, width, height) -> EventManager.call(
            new EventScreen(client, renderer, FontRegistry.INTER_MEDIUM, width, height, drawContext)));
   }

   /** Draws on the currently bound framebuffer (safe on Lunar's multi-FBO pipeline). */
   public static void renderImmediate(MinecraftClient client, OverlayDraw draw) {
      if (!LunarCompat.isLunarClient() || client == null || client.getWindow() == null) {
         return;
      }

      runOverlay(client, draw);
   }

   public static void renderMenuIfNeeded(DrawContext drawContext, int mouseX, int mouseY, float delta) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (!LunarCompat.isLunarClient() || client == null || client.getWindow() == null) {
         return;
      }

      if (!isZeroMenuOpen(client)) {
         return;
      }

      if (menuRenderedThisFrame) {
         return;
      }

      menuRenderedThisFrame = true;
      runOverlay(client, (renderer, width, height) -> GuiRender.render(renderer, drawContext, mouseX, mouseY, delta));
   }

   public static void renderMenu(MinecraftClient client, DrawContext drawContext, int mouseX, int mouseY, float delta) {
      renderMenuIfNeeded(drawContext, mouseX, mouseY, delta);
   }

   /**
    * Draws the menu with an already active {@link Renderer2D} frame (used from {@link ru.zero.Zero#onRender}).
    * Does not call {@link Renderer2D#begin}/{@link Renderer2D#end}.
    */
   public static void renderMenuInActiveFrame(Renderer2D renderer, MinecraftClient client) {
      if (!LunarCompat.isLunarClient() || renderer == null || client == null || client.getWindow() == null) {
         return;
      }

      if (!isZeroMenuOpen(client)) {
         return;
      }

      if (menuRenderedThisFrame) {
         return;
      }

      double[] mouseX = new double[1];
      double[] mouseY = new double[1];
      GLFW.glfwGetCursorPos(client.getWindow().getHandle(), mouseX, mouseY);
      if (client.mouse != null) {
         client.mouse.unlockCursor();
      }

      menuRenderedThisFrame = true;
      float delta = client.getRenderTickCounter().getDynamicDeltaTicks();
      GuiRender.render(renderer, null, (int) mouseX[0], (int) mouseY[0], delta);
   }

   @FunctionalInterface
   public interface OverlayDraw {
      void draw(Renderer2D renderer, int width, int height);
   }

   private static void runOverlay(MinecraftClient client, OverlayDraw draw) {
      if (LunarCompat.isLunarClient()) {
         runDirect(client, draw);
         return;
      }

      runWithFramebuffer(client, draw);
   }

   private static void runDirect(MinecraftClient client, OverlayDraw draw) {
      if (!Zero.isModInitialized()) {
         return;
      }

      Zero.ensureRendererInitialized();

      int width = client.getWindow().getFramebufferWidth();
      int height = client.getWindow().getFramebufferHeight();
      if (width <= 0 || height <= 0) {
         return;
      }

      GlState.Snapshot snapshot = GlState.push();
      GL11.glColorMask(true, true, true, true);
      GL11.glDisable(2929);
      GL11.glEnable(3042);

      try {
         AnimationSystem.getInstance().tick();
         Renderer2D renderer = Zero.getRenderer();
         if (renderer == null) {
            return;
         }

         DraggableManager draggableManager = DraggableManager.getInstance();
         draggableManager.beginFrame(client, renderer, width, height);
         boolean rendererBegun = false;

         try {
            renderer.begin(width, height);
            rendererBegun = true;
            draw.draw(renderer, width, height);
         } finally {
            if (rendererBegun) {
               renderer.end();
            }
            draggableManager.endFrame();
         }
      } finally {
         GlState.pop(snapshot);
      }
   }

   private static void runWithFramebuffer(MinecraftClient client, OverlayDraw draw) {
      if (!Zero.isModInitialized()) {
         return;
      }

      Zero.ensureRendererInitialized();

      int width = client.getWindow().getFramebufferWidth();
      int height = client.getWindow().getFramebufferHeight();
      if (width <= 0 || height <= 0) {
         return;
      }

      Framebuffer mainFramebuffer = client.getFramebuffer();
      int tempFbo = 0;
      int savedDrawFbo = GL11.glGetInteger(36006);
      int savedReadFbo = GL11.glGetInteger(36010);
      int savedFbo = GL11.glGetInteger(36160);
      if (mainFramebuffer != null) {
         if (mainFramebuffer.getColorAttachment() instanceof GlTexture glColor) {
            int mainFramebufferTextureId = glColor.getGlId();
            tempFbo = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(36160, tempFbo);
            GL30.glFramebufferTexture2D(36160, 36064, 3553, mainFramebufferTextureId, 0);
            GL11.glDrawBuffer(36064);
            int status = GL30.glCheckFramebufferStatus(36160);
            if (status != 36053) {
               GL30.glDeleteFramebuffers(tempFbo);
               tempFbo = 0;
               GL30.glBindFramebuffer(36160, savedFbo);
            }
         } else {
            GL30.glBindFramebuffer(36160, 0);
         }
      } else {
         GL30.glBindFramebuffer(36160, 0);
      }

      GlState.Snapshot snapshot = GlState.push();
      GL11.glColorMask(true, true, true, true);
      GL11.glDisable(2929);
      GL11.glEnable(3042);

      try {
         AnimationSystem.getInstance().tick();
         Renderer2D renderer = Zero.getRenderer();
         if (renderer == null) {
            return;
         }

         DraggableManager draggableManager = DraggableManager.getInstance();
         draggableManager.beginFrame(client, renderer, width, height);
         boolean rendererBegun = false;

         try {
            renderer.begin(width, height);
            rendererBegun = true;
            draw.draw(renderer, width, height);
         } finally {
            if (rendererBegun) {
               renderer.end();
            }
            draggableManager.endFrame();
         }
      } finally {
         GlState.pop(snapshot);

         if (tempFbo != 0) {
            GL30.glBindFramebuffer(36160, tempFbo);
            GL30.glFramebufferTexture2D(36160, 36064, 3553, 0, 0);
         }

         GL30.glBindFramebuffer(36009, savedDrawFbo);
         GL30.glBindFramebuffer(36008, savedReadFbo);
         GL30.glBindFramebuffer(36160, savedFbo);
         if (tempFbo != 0) {
            GL30.glDeleteFramebuffers(tempFbo);
         }
      }
   }
}
