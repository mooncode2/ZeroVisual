package ru.zero.module.impl.visuals.HUD;

import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.module.api.Module;
import ru.zero.module.impl.visuals.HUD.HudEditor;
import ru.zero.util.other.ScaleUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.animation.Translate;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public class ArrayListHUD {
   public static ArrayList<Module> modules = new ArrayList<>();

   public static float deltaTime() {
      return MinecraftClient.getInstance().getCurrentFps() > 0.0F ? 1.0F / MinecraftClient.getInstance().getCurrentFps() : 1.0F;
   }

   public static void arraylist(Renderer2D r2) {
      modules.clear();
      if (Zero.get != null && Zero.get.manager != null) {
         for (Module m : Zero.get.manager.module) {
            if (m.category != Category.Visuals && m.enable) {
               modules.add(m);
            }
         }
      }

      if (modules.isEmpty()) {
         ScaleUtil.scale_post(r2);
      } else {
         ScaleUtil.scale_pre(r2);
         modules.sort(
            (f1, f2) -> r2.measureText(FontRegistry.INTER_MEDIUM, f1.getDisplayName(), 32.0F).width
                  > r2.measureText(FontRegistry.INTER_MEDIUM, f2.getDisplayName(), 32.0F).width
               ? -1
               : 1
         );
         int count = 0;
         int x = 10;
         int y = 120;

         for (Module mx : modules) {
            Translate translate = mx.a;
            float offset = count * 24;
            translate.interpolate(10.0F, offset, 15.0F * deltaTime());
            float posX = translate.getX() + x;
            mx.isRender = posX > -10.0F;
            if (translate.getX() <= 0.1F && translate.getX() >= -0.1F) {
               mx.isRender = true;
            }

            count++;
         }

         count = 0;
         float maxModuleWidth = 0.0F;

         for (Module mx : modules) {
            Translate translate = mx.a;
            if (mx.isRender) {
               count++;
               maxModuleWidth = Math.max(maxModuleWidth, r2.measureText(FontRegistry.INTER_MEDIUM, mx.name, 32.0F).width + 12.0F);
            }
         }

         if (count > 0) {
            HudEditor.registerRect(x + 10.0F, y + 5.0F, maxModuleWidth, Math.max(34.0F, count * 24.0F));
         }

         count = 0;

         for (Module mxx : modules) {
            Translate translate = mxx.a;
            if (mxx.isRender) {
               r2.rect(
                  translate.getX() + x,
                  translate.getY() + y + 5.0F,
                  r2.measureText(FontRegistry.INTER_MEDIUM, mxx.name, 32.0F).width + 12.0F,
                  34.0F,
                  8.0F,
                  Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), 200)
               );
               count++;
            }
         }

         count = 0;

         for (Module mxxx : modules) {
            Translate translate = mxxx.a;
            if (mxxx.isRender) {
               r2.rect(
                  translate.getX() + x + 1.0F,
                  translate.getY() + y + 5.0F + 1.0F,
                  r2.measureText(FontRegistry.INTER_MEDIUM, mxxx.name, 32.0F).width + 12.0F - 2.0F,
                  32.0F,
                  7.0F,
                  Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), 200)
               );
               count++;
            }
         }

         count = 0;

         for (Module mxxxx : modules) {
            Translate translate = mxxxx.a;
            if (mxxxx.isRender) {
               float widthName = 0.0F;
               float rectY = translate.getY() + y + 5.0F;
               float rectHeight = 34.0F;
               float fontSize = 32.0F;
               float textHeight = r2.measureText(FontRegistry.INTER_MEDIUM, mxxxx.name, fontSize).height;
               float rectCenterY = rectY + rectHeight / 2.0F;
               float textY = rectCenterY - textHeight * 0.05F + 5.0F;
               int baseColor = Renderer2D.ColorUtil.getMainColor(16, 0);
               int color1 = Renderer2D.ColorUtil.multDark(baseColor, 0.7F);
               int r = Math.min(255, (int)((baseColor >> 16 & 0xFF) * 1.3F));
               int g = Math.min(255, (int)((baseColor >> 8 & 0xFF) * 1.3F));
               int b = Math.min(255, (int)((baseColor & 0xFF) * 1.3F));
               int a = baseColor >> 24 & 0xFF;
               int color2 = a << 24 | r << 16 | g << 8 | b;
               double timeSeconds = System.currentTimeMillis() / 1000.0;
               double animationSpeed = 0.5;
               double animationPhase = timeSeconds * animationSpeed % 1.0;
               int nameLength = mxxxx.name.length();

               for (int charIndex = 0; charIndex < nameLength; charIndex++) {
                  char c = mxxxx.name.charAt(charIndex);
                  double charPosition = nameLength > 1 ? (double)charIndex / (nameLength - 1) : 0.5;
                  double gradientPosition = (charPosition - animationPhase + 1.0) % 1.0;
                  double smoothFactor = Math.sin(gradientPosition * Math.PI * 2.0) * 0.5 + 0.5;
                  int r1 = color1 >> 16 & 0xFF;
                  int g1 = color1 >> 8 & 0xFF;
                  int b1 = color1 & 0xFF;
                  int red2 = color2 >> 16 & 0xFF;
                  int g2 = color2 >> 8 & 0xFF;
                  int b2 = color2 & 0xFF;
                  int finalR = (int)(r1 + (red2 - r1) * smoothFactor);
                  int finalG = (int)(g1 + (g2 - g1) * smoothFactor);
                  int finalB = (int)(b1 + (b2 - b1) * smoothFactor);
                  int colorModuleText = 0xFF000000 | finalR << 16 | finalG << 8 | finalB;
                  r2.text(FontRegistry.INTER_MEDIUM, translate.getX() + x + 6.0F + widthName, textY, fontSize, String.valueOf(c), colorModuleText);
                  widthName += r2.measureText(FontRegistry.INTER_MEDIUM, String.valueOf(c), fontSize).width;
               }

               count++;
            }
         }

         ScaleUtil.scale_post(r2);
         r2.popScale();
      }
   }
}
