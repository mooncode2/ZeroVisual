package ru.zero.util.render;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.math.ColorHelper;
import ru.zero.mixin.OverlayTextureAccessor;
import ru.zero.module.impl.visuals.HitColor;

@Environment(EnvType.CLIENT)
public final class HitColorOverlay {

   /** Approximates default vanilla hurt tint (red). */
   public static final Color VANILLA_HIT_COLOR = new Color(255, 0, 0);

   private static NativeImage vanillaSnapshot;

   private HitColorOverlay() {
   }

   public static void captureVanilla(OverlayTexture overlayTexture) {
      NativeImageBackedTexture texture = ((OverlayTextureAccessor) overlayTexture).zero$getTexture();
      NativeImage image = texture.getImage();
      if (image == null) {
         return;
      }

      vanillaSnapshot = copyImage(image);
   }

   public static void applyFromModule() {
      HitColor module = HitColor.getModule();
      if (module == null || !module.enable) {
         restoreVanilla();
         return;
      }

      applyColor(HitColor.resolveColor());
   }

   public static void restoreVanilla() {
      NativeImageBackedTexture texture = getClientTexture();
      if (texture == null || vanillaSnapshot == null) {
         return;
      }

      NativeImage image = texture.getImage();
      if (image == null) {
         return;
      }

      image.copyFrom(vanillaSnapshot);
      texture.upload();
   }

   private static void applyColor(Color color) {
      NativeImageBackedTexture texture = getClientTexture();
      if (texture == null || vanillaSnapshot == null) {
         return;
      }

      NativeImage image = texture.getImage();
      if (image == null) {
         return;
      }

      image.copyFrom(vanillaSnapshot);

      int targetR = color.getRed();
      int targetG = color.getGreen();
      int targetB = color.getBlue();

      for (int x = 0; x < image.getWidth(); x++) {
         for (int y = 0; y < image.getHeight(); y++) {
            int vanilla = vanillaSnapshot.getColorArgb(x, y);
            int alpha = ColorHelper.getAlpha(vanilla);
            if (alpha <= 0) {
               continue;
            }

            int vr = ColorHelper.getRed(vanilla);
            int vg = ColorHelper.getGreen(vanilla);
            int vb = ColorHelper.getBlue(vanilla);
            if (vr > vg && vr > vb) {
               image.setColorArgb(x, y, ColorHelper.getArgb(alpha, targetR, targetG, targetB));
            }
         }
      }

      texture.upload();
   }

   private static NativeImageBackedTexture getClientTexture() {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.gameRenderer == null) {
         return null;
      }

      OverlayTexture overlayTexture = client.gameRenderer.getOverlayTexture();
      return ((OverlayTextureAccessor) overlayTexture).zero$getTexture();
   }

   private static NativeImage copyImage(NativeImage source) {
      NativeImage copy = new NativeImage(source.getFormat(), source.getWidth(), source.getHeight(), false);
      copy.copyFrom(source);
      return copy;
   }
}
