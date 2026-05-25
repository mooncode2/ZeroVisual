package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.hud.bar.Bar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.Zero;
import ru.zero.event.EventManager;
import ru.zero.event.impl.EventScreen;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.module.impl.visuals.HUD.TargetHUD;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.util.render.animation.AnimationSystem;
import ru.zero.util.render.backends.gl.GlState;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
@Mixin({InGameHud.class})
public class InGameHudMixin {
   private static boolean isHudModuleEnabled() {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      Hud hud = Zero.get.manager.get(Hud.class);
      return hud != null && hud.enable;
   }

   private static boolean shouldUseCustomHotbar() {
      return isHudModuleEnabled() && Hud.element.get("Хот бар");
   }

   private static boolean shouldUseCustomPotions() {
      return isHudModuleEnabled() && Hud.element.get("Список зелий");
   }

   @Inject(
      method = {"renderStatusEffectOverlay"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderStatusEffects(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (shouldUseCustomPotions()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderHotbar"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderHotbar(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (shouldUseCustomHotbar()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderHealthBar"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderHealthBar(
      DrawContext context,
      PlayerEntity player,
      int x,
      int y,
      int lines,
      int regeneratingHeartIndex,
      float maxHealth,
      int lastHealth,
      int health,
      int absorption,
      boolean blinking,
      CallbackInfo ci
   ) {
      if (shouldUseCustomHotbar()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderFood"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderFood(DrawContext context, PlayerEntity player, int top, int right, CallbackInfo ci) {
      if (shouldUseCustomHotbar()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderArmor"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onRenderArmor(DrawContext context, PlayerEntity player, int i, int j, int k, int x, CallbackInfo ci) {
      if (shouldUseCustomHotbar()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderAirBubbles"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderAir(DrawContext context, PlayerEntity player, int heartCount, int top, int left, CallbackInfo ci) {
      if (shouldUseCustomHotbar()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"renderMountHealth"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onRenderMountHealth(DrawContext context, CallbackInfo ci) {
      if (shouldUseCustomHotbar()) {
         ci.cancel();
      }
   }

   @Redirect(
      method = {"renderMainHud"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/bar/Bar;drawExperienceLevel(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;I)V"
      )
   )
   private void redirectDrawExperienceLevel(DrawContext context, TextRenderer textRenderer, int level) {
      if (!shouldUseCustomHotbar()) {
         Bar.drawExperienceLevel(context, textRenderer, level);
      }
   }

   @Redirect(
      method = {"renderMainHud"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderBar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
         ordinal = 0
      )
   )
   private void redirectRenderBar(Bar bar, DrawContext context, RenderTickCounter tickCounter) {
      if (!shouldUseCustomHotbar()) {
         bar.renderBar(context, tickCounter);
      }
   }

   @Redirect(
      method = {"renderMainHud"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/hud/bar/Bar;renderAddons(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/render/RenderTickCounter;)V",
         ordinal = 0
      )
   )
   private void redirectRenderAddons(Bar bar, DrawContext context, RenderTickCounter tickCounter) {
      if (!shouldUseCustomHotbar()) {
         bar.renderAddons(context, tickCounter);
      }
   }

   @Inject(
      method = {"render"},
      at = {@At("RETURN")}
   )
   private void onRenderHud(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
      if (Zero.isModInitialized()) {
         MinecraftClient client = MinecraftClient.getInstance();
         if (client != null && client.player != null && client.world != null && client.getWindow() != null) {
            Zero.ensureRendererInitialized();
            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
            if (width > 0 && height > 0) {
               GlState.Snapshot snapshot = GlState.push();
               int hudFramebuffer = bindHudDrawFramebuffer(client);

               try {
                  AnimationSystem.getInstance().tick();
                  Renderer2D renderer = Zero.getRenderer();
                  if (renderer != null) {
                     DraggableManager draggableManager = DraggableManager.getInstance();
                     draggableManager.beginFrame(client, renderer, width, height);
                     boolean rendererBegun = false;

                     try {
                        renderer.begin(width, height);
                        rendererBegun = true;
                        EventManager.call(new EventScreen(client, renderer, FontRegistry.INTER_MEDIUM, width, height, context));
                     } finally {
                        if (rendererBegun) {
                           renderer.end();
                        }

                        draggableManager.endFrame();
                     }
                  }
               } finally {
                  GlState.pop(snapshot);
                  if (hudFramebuffer != 0) {
                     GL30.glDeleteFramebuffers(hudFramebuffer);
                  }
               }

               TargetHUD.renderPendingItems(context);
            }
         }
      }
   }

   /**
    * Routes {@link Renderer2D} draws to the main window color attachment (required on 1.21+).
    */
   private static int bindHudDrawFramebuffer(MinecraftClient client) {
      Framebuffer framebuffer = client.getFramebuffer();
      if (framebuffer == null) {
         return 0;
      }

      if (!(framebuffer.getColorAttachment() instanceof GlTexture glColor)) {
         return 0;
      }

      int fbo = GL30.glGenFramebuffers();
      GL30.glBindFramebuffer(36160, fbo);
      GL30.glFramebufferTexture2D(36160, 36064, 3553, glColor.getGlId(), 0);
      if (GL30.glCheckFramebufferStatus(36160) != 36053) {
         GL30.glBindFramebuffer(36160, 0);
         GL30.glDeleteFramebuffers(fbo);
         return 0;
      }

      GL11.glDrawBuffer(36064);
      GL11.glColorMask(true, true, true, true);
      return fbo;
   }
}
