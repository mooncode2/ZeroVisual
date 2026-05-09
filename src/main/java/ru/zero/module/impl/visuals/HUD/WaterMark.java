package ru.zero.module.impl.visuals.HUD;

import java.awt.Color;
import java.net.SocketAddress;
import java.util.Calendar;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.module.impl.visuals.HUD.HudEditor;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.ScaledResolution;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public class WaterMark {
   public static MinecraftClient mc = MinecraftClient.getInstance();

   public static void waterMark(Renderer2D r2) {
      new ScaledResolution(mc);
      float x = 20.0F;
      float y = 20.0F;
      Color mainColorGlow = Renderer2D.ColorUtil.getColor(Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 50));
      Hud.drawClientRect(r2, x, y, 94.54F, 40.64F, 13.0F, 1.0F, 1.0F);
      r2.shadow(x + 23.27F, y + 20.0F, 0.1F, 0.1F, 8.0F, 10.0F, 0.1F, mainColorGlow.getRGB());
      // Лого: круг вместо текста/иконки Zero.
      r2.circle(x + 23.27F, y + 20.0F, 10.0F, 0.0F, 1.0F, Renderer2D.ColorUtil.getMainColor(1, 1));
      r2.circle(x + 23.27F, y + 20.0F, 6.5F, 0.0F, 1.0F, Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), 180));
      r2.text(FontRegistry.INTER_SEMIBOLD, x + 42.0F, y + 25.5F, 30.0F, "Zero", Renderer2D.ColorUtil.getTextColor(1, 1));
      Calendar calendar = Calendar.getInstance();
      int hours = calendar.get(11);
      int minutes = calendar.get(12);
      String timeStr = String.format("%d:%02d", hours, minutes);
      float width = r2.measureText(FontRegistry.INTER_SEMIBOLD, mc.player.getName().getString(), 28.0F).width + 148.0F;
      float fps = r2.measureText(FontRegistry.INTER_SEMIBOLD, String.valueOf(mc.getCurrentFps()), 28.0F).width
         + r2.measureText(FontRegistry.INTER_SEMIBOLD, "fps", 28.0F).width
         + 40.0F;
      float time = r2.measureText(FontRegistry.INTER_MEDIUM, timeStr, 28.0F).width;
      Hud.drawClientRect(r2, x + 103.5F, y, width + fps + time - 55.0F, 40.64F, 13.0F, 1.0F, 1.0F);
      r2.text(FontRegistry.ICONS, x + 117.56F, y + 28.0F, 32.0F, "q", Renderer2D.ColorUtil.getMainColor(1, 1));
      r2.text(FontRegistry.INTER_SEMIBOLD, x + 140.75F, y + 25.5F, 28.0F, mc.player.getName().getString(), Renderer2D.ColorUtil.getTextColor(1, 1));
      r2.rect(x + width, y + 15.0F, 2.34F, 11.21F, 4.0F, Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 80));
      r2.text(FontRegistry.ICONS, x + width + 10.0F, y + 28.0F, 32.0F, "r", Renderer2D.ColorUtil.getMainColor(1, 1));
      float fpsTextX = x + width + 33.0F;
      r2.text(FontRegistry.INTER_SEMIBOLD, fpsTextX, y + 25.5F, 28.0F, String.valueOf(mc.getCurrentFps()), Renderer2D.ColorUtil.getTextColor(1, 1));
      fpsTextX += r2.measureText(FontRegistry.INTER_SEMIBOLD, String.valueOf(mc.getCurrentFps()), 28.0F).width;
      r2.text(FontRegistry.INTER_SEMIBOLD, fpsTextX, y + 25.5F, 28.0F, "fps", Renderer2D.ColorUtil.getMainColor(1, 1));
      r2.rect(x + width + fps, y + 15.0F, 2.34F, 11.21F, 4.0F, Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 80));
      r2.text(FontRegistry.ICONS, x + width + fps + 10.0F, y + 28.0F, 32.0F, "s", Renderer2D.ColorUtil.getMainColor(1, 1));
      r2.text(FontRegistry.INTER_SEMIBOLD, x + width + fps + 33.0F, y + 25.5F, 28.0F, timeStr, Renderer2D.ColorUtil.getTextColor(1, 1));
      String ip = "N/A";
      if (mc.isConnectedToLocalServer()) {
         ip = "localhost";
      } else {
         String domainName = null;
         if (mc.getCurrentServerEntry() != null) {
            domainName = mc.getCurrentServerEntry().address;
         }

         if (domainName != null && !domainName.isEmpty() && domainName.matches(".*[a-zA-Z].*")) {
            int colonIndex = domainName.lastIndexOf(58);
            if (colonIndex > 0) {
               ip = domainName.substring(0, colonIndex);
            } else {
               ip = domainName;
            }
         } else if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
            SocketAddress address = mc.getNetworkHandler().getConnection().getAddress();
            if (address != null) {
               String addr = address.toString();
               if (addr.startsWith("/")) {
                  addr = addr.substring(1);
               }

               int colonIndex = addr.lastIndexOf(58);
               if (colonIndex > 0) {
                  ip = addr.substring(0, colonIndex);
               } else {
                  ip = addr;
               }
            }
         }
      }

      String ping = "0ms";
      if (mc.getNetworkHandler() != null && mc.player != null) {
         PlayerListEntry playerListEntry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
         if (playerListEntry != null) {
            ping = playerListEntry.getLatency() + "ms";
         }
      }

      String ipMain = ip;
      String ipSuffix = "";
      int lastDotIndex = ip.lastIndexOf(46);
      if (lastDotIndex > 0 && lastDotIndex < ip.length() - 1) {
         ipMain = ip.substring(0, lastDotIndex);
         ipSuffix = ip.substring(lastDotIndex);
      }

      float ipMainWidth = r2.measureText(FontRegistry.INTER_SEMIBOLD, ipMain, 28.0F).width;
      float ipSuffixWidth = r2.measureText(FontRegistry.INTER_SEMIBOLD, ipSuffix, 28.0F).width;
      float ipServer = ipMainWidth + ipSuffixWidth + 44.0F;
      String pingValue = ping.replace("ms", "");
      float pingValueWidth = r2.measureText(FontRegistry.INTER_SEMIBOLD, pingValue, 28.0F).width;
      float pingMsWidth = r2.measureText(FontRegistry.INTER_SEMIBOLD, "ms", 28.0F).width;
      float pingW = pingValueWidth + pingMsWidth + 42.0F;
      float row1Width = 103.5F + (width + fps + time - 55.0F);
      float row2Width = ipServer + pingW;
      float totalWidth = Math.max(row1Width, row2Width);
      float totalHeight = 90.24F;
      HudEditor.registerRect(x, y, totalWidth, totalHeight);
      Hud.drawClientRect(r2, x, y + 49.6F, ipServer + pingW, 40.64F, 13.0F, 1.0F, 1.0F);
      r2.text(FontRegistry.ICONS, x + 14.06F, y + 78.0F, 36.0F, "t", Renderer2D.ColorUtil.getMainColor(1, 1));
      float ipTextX = x + 38.0F;
      r2.text(FontRegistry.INTER_SEMIBOLD, ipTextX, y + 74.5F, 28.0F, ipMain, Renderer2D.ColorUtil.getTextColor(1, 1));
      ipTextX += ipMainWidth;
      r2.text(FontRegistry.INTER_SEMIBOLD, ipTextX, y + 74.5F, 28.0F, ipSuffix, Renderer2D.ColorUtil.getMainColor(1, 1));
      r2.rect(x + ipServer, y + 64.5F, 2.34F, 11.21F, 4.0F, Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 80));
      r2.text(FontRegistry.ICONS, x + ipServer + 8.0F, y + 76.0F, 32.0F, "u", Renderer2D.ColorUtil.getMainColor(1, 1));
      float pingTextX = x + 28.0F + ipServer;
      r2.text(FontRegistry.INTER_SEMIBOLD, pingTextX, y + 74.5F, 28.0F, pingValue, Renderer2D.ColorUtil.getTextColor(1, 1));
      pingTextX += pingValueWidth;
      r2.text(FontRegistry.INTER_SEMIBOLD, pingTextX, y + 74.5F, 28.0F, "ms", Renderer2D.ColorUtil.getMainColor(1, 1));
   }
}
