package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.gui.hud.bar.Bar;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
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
               Framebuffer mainFramebuffer = client.getFramebuffer();
               int tempFbo = 0;
               int savedDrawFbo = GL11.glGetInteger(36006);
               int savedReadFbo = GL11.glGetInteger(36010);
               int savedFbo = GL11.glGetInteger(36006);
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

               GL11.glColorMask(true, true, true, true);
               GL11.glDisable(2929);
               GL11.glEnable(3042);
               GlState.Snapshot snapshot = GlState.push();

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
                  if (snapshot != null) {
                     GL20.glUseProgram(snapshot.program);
                     GL30.glBindVertexArray(snapshot.vao);
                     GL15.glBindBuffer(34962, snapshot.arrayBuffer);
                     GL15.glBindBuffer(34963, snapshot.elementArrayBuffer);
                     GL13.glActiveTexture(snapshot.activeTexture);
                     GL11.glBindTexture(3553, snapshot.texture2D);
                     GL11.glPixelStorei(3317, snapshot.unpackAlignment);
                     setEnabled(3089, snapshot.scissorEnabled);
                     setEnabled(2929, snapshot.depthTestEnabled);
                     setEnabled(2884, snapshot.cullFaceEnabled);
                     setEnabled(3042, snapshot.blendEnabled);
                     setEnabled(36281, snapshot.framebufferSrgbEnabled);
                     GL14.glBlendFuncSeparate(snapshot.blendSrcRGB, snapshot.blendDstRGB, snapshot.blendSrcAlpha, snapshot.blendDstAlpha);
                     GL11.glColorMask(snapshot.colorMaskR, snapshot.colorMaskG, snapshot.colorMaskB, snapshot.colorMaskA);
                     GL11.glDepthMask(snapshot.depthMask);
                     GL11.glViewport(snapshot.viewport[0], snapshot.viewport[1], snapshot.viewport[2], snapshot.viewport[3]);
                     GL11.glScissor(snapshot.scissorBox[0], snapshot.scissorBox[1], snapshot.scissorBox[2], snapshot.scissorBox[3]);
                  }

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

               TargetHUD.renderPendingItems(context);
            }
         }
      }
   }

   private static void setEnabled(int cap, boolean enabled) {
      if (enabled) {
         GL11.glEnable(cap);
      } else {
         GL11.glDisable(cap);
      }
   }
}
