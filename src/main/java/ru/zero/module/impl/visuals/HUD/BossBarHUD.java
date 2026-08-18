package ru.zero.module.impl.visuals.HUD;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.util.Formatting;
import ru.zero.mixin.BossBarHudAccessor;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.glass.LiquidGlassRenderer;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.util.render.text.FormattedText;

@Environment(EnvType.CLIENT)
public class BossBarHUD {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final float BAR_WIDTH = 340.0F;
   private static final float FLASK_HEIGHT = 20.0F;
   private static final float LIQUID_INSET = 2.5F;
   private static final float NAME_HEIGHT = 28.0F;
   private static final float ROW_SPACING = 14.0F;
   private static final float GLASS_RADIUS = 10.0F;
   private static final float NORMAL_RADIUS = 5.0F;
   private static final int DIVISIONS = 10;
   private static final int MAX_BARS = 6;
   private static final String PREVIEW_NAME = "Zero";
   private static final float PREVIEW_PERCENT = 1.0F;
   private static final List<BossBar> barBuffer = new ArrayList<>();

   private BossBarHUD() {
   }

   public static void bossbar(Renderer2D r2) {
      if (mc.inGameHud == null) {
         return;
      }
      List<BossBar> bars = collectBars();
      boolean preview = bars.isEmpty() && isChatOpen();
      if (bars.isEmpty() && !preview) {
         return;
      }

      boolean glass = LiquidGlassRenderer.isEnabled();
      int count = preview ? 1 : Math.min(bars.size(), MAX_BARS);
      float rowHeight = NAME_HEIGHT + FLASK_HEIGHT + 6.0F;
      float panelWidth = BAR_WIDTH + 28.0F;
      float panelHeight = count * rowHeight + (count - 1) * ROW_SPACING + 12.0F;

      float screenWidth = mc.getWindow().getWidth();
      float defaultX = (screenWidth - panelWidth) / 2.0F;
      float defaultY = 20.0F;

      DraggableManager.DragSession dragSession = DraggableManager.getInstance().beginDrag("bossbar", defaultX, defaultY, panelWidth, panelHeight);
      float x = dragSession.positionX();
      float y = dragSession.positionY();

      int mainColor = Renderer2D.ColorUtil.getMainColor(1, 1);
      int textColor = Renderer2D.ColorUtil.getTextColor(1, 1);

      float cursorY = y + 6.0F;
      for (int i = 0; i < count; i++) {
         String name;
         float percent;
         int barColor;
         if (preview) {
            name = PREVIEW_NAME;
            percent = PREVIEW_PERCENT;
            barColor = ColorUtil.replAlpha(mainColor, 240);
         } else {
            BossBar bar = bars.get(i);
            name = nameOf(bar);
            percent = bar.getPercent();
            barColor = colorOf(bar);
         }

         float nameWidth = FormattedText.measure(r2, FontRegistry.INTER_SEMIBOLD, name, 24.0F);
         float nameX = x + (panelWidth - nameWidth) * 0.5F;
         FormattedText.draw(r2, FontRegistry.INTER_SEMIBOLD, nameX, cursorY + 20.0F, 24.0F, name, textColor);

         float flaskX = x + 14.0F;
         float flaskY = cursorY + NAME_HEIGHT;
         float radius = glass ? GLASS_RADIUS : NORMAL_RADIUS;

         drawFlask(r2, flaskX, flaskY, BAR_WIDTH, FLASK_HEIGHT, radius, glass, barColor, percent, mainColor);

         cursorY += rowHeight + ROW_SPACING;
      }

      HudEditor.registerRect(x, y, panelWidth, panelHeight);
      DraggableManager.getInstance().endDrag(dragSession);
   }

   private static void drawFlask(Renderer2D r2, float x, float y, float w, float h, float radius,
         boolean glass, int liquidColor, float percent, int mainColor) {
      int trackColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), glass ? 80 : 160);
      int outlineColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), 60);

      if (glass) {
         LiquidGlassRenderer.drawGlass(r2, x, y, w, h, radius, 1.0F);
      } else {
         Hud.drawClientRect(r2, x, y, w, h, radius, 1.0F, 1.0F);
      }

      r2.rect(x, y, w, h, radius, trackColor);

      float fillW = w * Math.max(0.0F, Math.min(1.0F, percent));
      if (fillW > 0.0F) {
         float liquidX = x + LIQUID_INSET;
         float liquidY = y + LIQUID_INSET;
         float liquidW = Math.max(0.0F, fillW - 2.0F * LIQUID_INSET);
         float liquidH = h - 2.0F * LIQUID_INSET;
         float liquidR = Math.max(0.0F, radius - LIQUID_INSET);
         if (liquidW > liquidR) {
            r2.rect(liquidX, liquidY, liquidW, liquidH, liquidR, liquidColor);
         } else if (liquidW > 0.0F) {
            r2.rect(liquidX, liquidY, liquidW, liquidH, 0.0F, liquidColor);
         }
      }

      float step = w / (float) DIVISIONS;
      int tickColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), glass ? 90 : 130);
      for (int d = 1; d < DIVISIONS; d++) {
         float tx = x + step * d;
         float tickH = h * 0.35F;
         r2.rect(tx - 0.5F, y + (h - tickH) * 0.5F, 1.0F, tickH, 0.0F, tickColor);
      }

      if (!glass) {
         r2.rectOutline(x, y, w, h, radius, outlineColor, 1.0F);
      }
   }

   private static boolean isChatOpen() {
      return mc.currentScreen instanceof ChatScreen;
   }

   private static List<BossBar> collectBars() {
      List<BossBar> result = barBuffer;
      result.clear();
      try {
         if (mc.inGameHud == null || mc.inGameHud.getBossBarHud() == null) {
            return result;
         }
         Object hud = mc.inGameHud.getBossBarHud();
         if (!(hud instanceof BossBarHudAccessor accessor)) {
            return result;
         }
         Map<UUID, BossBar> bars = accessor.zero$getBossBars();
         if (bars != null) {
            result.addAll(bars.values());
         }
      } catch (Throwable ignored) {
      }
      return result;
   }

   private static String nameOf(BossBar bar) {
      try {
         if (bar.getName() != null) {
            return bar.getName().getString();
         }
      } catch (Throwable ignored) {
      }
      return "";
   }

   private static int colorOf(BossBar bar) {
      try {
         Formatting fmt = bar.getColor().getTextFormat();
         Integer rgb = fmt != null ? fmt.getColorValue() : null;
         if (rgb != null) {
            return (0xFF << 24) | rgb;
         }
      } catch (Throwable ignored) {
      }
      return ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 240);
   }
}