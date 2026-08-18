package ru.zero.mixin.lunar;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.compat.LunarCompat;
import ru.zero.compat.LunarOverlayRender;
import ru.zero.ui.gui.GuiClient;

/**
 * Renders Zero menu GUI into the framebuffer on Lunar (flipFrame hook alone is not enough).
 */
@Environment(EnvType.CLIENT)
@Mixin(Screen.class)
public class ScreenLunarMixin {

   @Shadow
   @Final
   protected MinecraftClient client;

   @Inject(method = { "renderWithTooltip" }, at = { @At("RETURN") }, require = 0)
   private void zero$renderGuiOverlay(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
      if (!LunarCompat.isLunarClient()) {
         return;
      }

      if (!((Object) this instanceof GuiClient)) {
         return;
      }

      LunarOverlayRender.renderMenuIfNeeded(context, mouseX, mouseY, delta);
   }
}
