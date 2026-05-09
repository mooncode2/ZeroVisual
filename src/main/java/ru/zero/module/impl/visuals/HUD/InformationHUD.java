package ru.zero.module.impl.visuals.HUD;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.module.impl.visuals.ClickGUI;
import ru.zero.module.impl.visuals.HUD.HudEditor;
import ru.zero.util.render.animation.util.Easings;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.ScaledResolution;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public class InformationHUD {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   private static double prevX = 0.0;
   private static double prevY = 0.0;
   private static double prevZ = 0.0;
   private static long prevTime = 0L;
   private static float bps = 0.0F;

   public static void information(Renderer2D r2) {
      Hud.animC.update();
      Color mainColorGlow = Renderer2D.ColorUtil.getColor(Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 50));
      ScaledResolution sr = new ScaledResolution(mc);
      boolean chat = mc.currentScreen instanceof ChatScreen;
      Hud.animC.run(chat ? 1.0 : 0.0, 0.8F, Easings.CIRC_OUT, false);
      float x = 20.0F;
      float y = sr.getHeight() * 2 - 75.0F + (20.0F + -20.0F * Hud.animC.get());
      long currentTime = System.currentTimeMillis();
      if (prevTime != 0L && currentTime - prevTime >= 50L) {
         double dx = mc.player.getX() - prevX;
         double dy = mc.player.getY() - prevY;
         double dz = mc.player.getZ() - prevZ;
         double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
         double timeDelta = (currentTime - prevTime) / 1000.0;
         if (timeDelta > 0.0) {
            bps = (float)(distance / timeDelta);
         }

         prevX = mc.player.getX();
         prevY = mc.player.getY();
         prevZ = mc.player.getZ();
         prevTime = currentTime;
      } else if (prevTime == 0L) {
         prevX = mc.player.getX();
         prevY = mc.player.getY();
         prevZ = mc.player.getZ();
         prevTime = currentTime;
      }

      Hud.drawClientRect(r2, x, y, 40.64F, 40.64F, 13.0F, 1.0F, 1.0F);
      r2.shadow(x + 15.0F, y + 22.0F, 0.1F, 0.1F, 8.0F, 10.0F, 0.1F, mainColorGlow.getRGB());
      boolean vanillaStyle = ClickGUI.isVanillaStyle();
      r2.text(vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS, x + 11.0F, y + 30.0F, 36.0F, vanillaStyle ? "i" : "0", Renderer2D.ColorUtil.getMainColor(1, 1));
      int playerX = (int)mc.player.getX();
      int playerY = (int)mc.player.getY();
      int playerZ = (int)mc.player.getZ();
      String xStr = String.valueOf(playerX);
      String yStr = String.valueOf(playerY);
      String zStr = String.valueOf(playerZ);
      float fontSize = 28.0F;
      float textX = x + 92.0F;
      float textY = y + 25.0F;
      float coordsWidth = r2.measureText(FontRegistry.INTER_MEDIUM, xStr, fontSize).width
         + r2.measureText(FontRegistry.INTER_MEDIUM, "x ", fontSize).width
         + r2.measureText(FontRegistry.INTER_MEDIUM, yStr, fontSize).width
         + r2.measureText(FontRegistry.INTER_MEDIUM, "y ", fontSize).width
         + r2.measureText(FontRegistry.INTER_MEDIUM, zStr, fontSize).width
         + r2.measureText(FontRegistry.INTER_MEDIUM, "z", fontSize).width;
      String bpsValue = String.format("%.1f", bps);
      float bpsWidth = r2.measureText(FontRegistry.INTER_MEDIUM, bpsValue, 28.0F).width + r2.measureText(FontRegistry.INTER_MEDIUM, "b/s", 28.0F).width;
      HudEditor.registerRect(x, y, coordsWidth + bpsWidth + 149.6F, 40.64F);
      Hud.drawClientRect(r2, x + 49.6F, y, coordsWidth + bpsWidth + 100.0F, 40.64F, 13.0F, 1.0F, 1.0F);
      r2.text(vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS, x + 66.56F, y + 28.0F, 32.0F, vanillaStyle ? "XYZ" : "1", Renderer2D.ColorUtil.getMainColor(1, 1));
      r2.text(FontRegistry.INTER_MEDIUM, textX, textY, fontSize, xStr, Renderer2D.ColorUtil.getTextColor(1, 1));
      textX += r2.measureText(FontRegistry.INTER_MEDIUM, xStr, fontSize).width;
      r2.text(FontRegistry.INTER_MEDIUM, textX, textY, fontSize, "x ", Renderer2D.ColorUtil.getMainColor(1, 1));
      textX += r2.measureText(FontRegistry.INTER_MEDIUM, "x ", fontSize).width;
      r2.text(FontRegistry.INTER_MEDIUM, textX, textY, fontSize, yStr, Renderer2D.ColorUtil.getTextColor(1, 1));
      textX += r2.measureText(FontRegistry.INTER_MEDIUM, yStr, fontSize).width;
      r2.text(FontRegistry.INTER_MEDIUM, textX, textY, fontSize, "y ", Renderer2D.ColorUtil.getMainColor(1, 1));
      textX += r2.measureText(FontRegistry.INTER_MEDIUM, "y ", fontSize).width;
      r2.text(FontRegistry.INTER_MEDIUM, textX, textY, fontSize, zStr, Renderer2D.ColorUtil.getTextColor(1, 1));
      textX += r2.measureText(FontRegistry.INTER_MEDIUM, zStr, fontSize).width;
      r2.text(FontRegistry.INTER_MEDIUM, textX, textY, fontSize, "z", Renderer2D.ColorUtil.getMainColor(1, 1));
      textX += r2.measureText(FontRegistry.INTER_MEDIUM, "z", fontSize).width;
      r2.rect(x + coordsWidth + 98.0F, y + 15.0F, 2.34F, 11.21F, 4.0F, Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 80));
      r2.text(vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS, x + coordsWidth + 108.0F, y + 28.0F, 32.0F, vanillaStyle ? "S" : "2", Renderer2D.ColorUtil.getMainColor(1, 1));
      float bpsTextX = x + coordsWidth + 132.0F;
      r2.text(FontRegistry.INTER_MEDIUM, bpsTextX, y + 25.0F, fontSize, bpsValue, Renderer2D.ColorUtil.getTextColor(1, 1));
      bpsTextX += r2.measureText(FontRegistry.INTER_MEDIUM, bpsValue, fontSize).width;
      r2.text(FontRegistry.INTER_MEDIUM, bpsTextX, y + 25.0F, fontSize, "b/s", Renderer2D.ColorUtil.getMainColor(1, 1));
   }
}
