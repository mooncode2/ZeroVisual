package ru.zero.module.impl.visuals.HUD;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardEntry;
import net.minecraft.scoreboard.ScoreboardObjective;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.util.client.ScoreboardCapture;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.animation.util.Animation;
import ru.zero.util.render.animation.util.Easings;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.glass.LiquidGlassRenderer;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.util.render.text.FormattedText;

@Environment(EnvType.CLIENT)
public class ScoreboardHUD {
   private static final MinecraftClient mc = MinecraftClient.getInstance();
   private static final int MAX_ENTRIES = 15;
   private static final float ROW_HEIGHT = 22.0F;
   private static final float PADDING_X = 14.0F;
   private static final float PADDING_Y = 8.0F;
   private static final float TITLE_GAP = 6.0F;
   private static final float RADIUS = 10.0F;
   private static final float FONT_SIZE = 20.0F;
   private static final float TITLE_SIZE = 22.0F;
   private static final String PREVIEW_TITLE = "Zero";
   private static final String PREVIEW_BODY = "Steve 1\nAlex 2\nEnderman 3";
   private static final Animation openAnimation = new Animation();
   private static boolean lastVisible = false;
   private static final List<ScoreboardHUD.Entry> entryBuffer = new ArrayList<>();

   private ScoreboardHUD() {
   }

   public static void scoreboard(Renderer2D r2) {
      if (mc.player == null || mc.world == null) {
         return;
      }

      ScoreboardObjective objective = ScoreboardCapture.current();
      if (objective == null) {
         try {
            Scoreboard sb = mc.world.getScoreboard();
            if (sb != null) {
               objective = sb.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
            }
         } catch (Throwable ignored) {
         }
      }

      boolean chat = isChatOpen();
      boolean hasReal = objective != null;
      if (!hasReal && !chat) {
         if (lastVisible) {
            lastVisible = false;
         }
         openAnimation.update();
         openAnimation.run(0.0, 0.4F, Easings.SINE_OUT);
         float gone = openAnimation.get();
         if (gone > 0.001F) {
            renderPanel(r2, collectPreviewEntries(), PREVIEW_TITLE, gone);
         }
         return;
      }

      List<ScoreboardHUD.Entry> entries;
      String title;
      if (hasReal) {
         entries = collectEntries(objective);
         title = objective.getDisplayName() != null ? objective.getDisplayName().getString() : objective.getName();
      } else {
         entries = collectPreviewEntries();
         title = PREVIEW_TITLE;
      }
      if (title == null) {
         title = "";
      }

      if (!lastVisible && hasReal) {
         lastVisible = true;
      }
      openAnimation.update();
      openAnimation.run(1.0, 0.6F, Easings.ELASTIC_OUT);
      float alpha = openAnimation.get();
      if (alpha <= 0.001F && !hasReal && !chat) {
         return;
      }
      renderPanel(r2, entries, title, alpha);
   }

   private static void renderPanel(Renderer2D r2, List<ScoreboardHUD.Entry> entries, String title, float alpha) {
      float titleWidth = FormattedText.measure(r2, FontRegistry.INTER_SEMIBOLD, title, TITLE_SIZE);
      float maxNameWidth = 0.0F;
      float maxScoreWidth = 0.0F;

      for (ScoreboardHUD.Entry e : entries) {
         maxNameWidth = Math.max(maxNameWidth, FormattedText.measure(r2, FontRegistry.INTER_MEDIUM, e.name, FONT_SIZE));
         maxScoreWidth = Math.max(maxScoreWidth, r2.measureText(FontRegistry.INTER_SEMIBOLD, e.scoreText, FONT_SIZE).width);
      }

      float contentWidth = Math.max(titleWidth, maxNameWidth + maxScoreWidth + 20.0F);
      float panelWidth = contentWidth + 2.0F * PADDING_X;
      float titleHeight = entries.isEmpty() ? FONT_SIZE : TITLE_SIZE;
      float panelHeight = PADDING_Y + titleHeight + TITLE_GAP + Math.max(1, entries.size()) * ROW_HEIGHT + PADDING_Y;

      float screenWidth = mc.getWindow().getWidth();
      float defaultX = screenWidth - panelWidth - 4.0F;
      float defaultY = 4.0F;
      if (defaultX < 0.0F) {
         defaultX = 0.0F;
      }

      DraggableManager.DragSession dragSession = DraggableManager.getInstance().beginDrag("scoreboard", defaultX, defaultY, panelWidth, panelHeight);
      float x = dragSession.positionX();
      float slideY = dragSession.positionY() + (20.0F - 20.0F * alpha);

      r2.pushAlpha(alpha);
      int mainColor = Renderer2D.ColorUtil.getMainColor(1, 1);
      int textColor = Renderer2D.ColorUtil.getTextColor(1, 1);
      int titleColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextTwoColor(1, 1), 255);
      int scoreColor = ColorUtil.replAlpha(mainColor, 230);
      int bgTint = ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), 180);

      if (LiquidGlassRenderer.isEnabled()) {
         LiquidGlassRenderer.drawGlass(r2, x, slideY, panelWidth, panelHeight, RADIUS, 1.0F);
      } else {
         Hud.drawClientRect(r2, x, slideY, panelWidth, panelHeight, RADIUS, 1.0F, 1.0F);
         r2.rect(x, slideY, panelWidth, panelHeight, RADIUS, bgTint);
      }

      float titleX = x + (panelWidth - titleWidth) * 0.5F;
      FormattedText.draw(r2, FontRegistry.INTER_SEMIBOLD, titleX, slideY + PADDING_Y + TITLE_SIZE - 4.0F, TITLE_SIZE, title, titleColor);

      float rowY = slideY + PADDING_Y + titleHeight + TITLE_GAP;
      for (ScoreboardHUD.Entry e : entries) {
         FormattedText.draw(r2, FontRegistry.INTER_MEDIUM, x + PADDING_X, rowY + FONT_SIZE - 4.0F, FONT_SIZE, e.name, textColor);
         r2.text(FontRegistry.INTER_SEMIBOLD, x + panelWidth - PADDING_X - maxScoreWidth, rowY + FONT_SIZE - 4.0F, FONT_SIZE, e.scoreText, scoreColor);
         rowY += ROW_HEIGHT;
      }

      r2.popAlpha();
      HudEditor.registerRect(x, slideY, panelWidth, panelHeight);
      DraggableManager.getInstance().endDrag(dragSession);
   }

   private static boolean isChatOpen() {
      return mc.currentScreen instanceof ChatScreen;
   }

   private static List<ScoreboardHUD.Entry> collectPreviewEntries() {
      List<ScoreboardHUD.Entry> result = entryBuffer;
      result.clear();
      String[] lines = PREVIEW_BODY.split("\n", -1);
      for (String line : lines) {
         String trimmed = line.trim();
         if (trimmed.isEmpty()) {
            continue;
         }
         int space = trimmed.lastIndexOf(' ');
         String name;
         String scoreText;
         int score;
         if (space > 0 && space < trimmed.length() - 1) {
            name = trimmed.substring(0, space);
            scoreText = trimmed.substring(space + 1);
            int parsed = 0;
            try {
               parsed = Integer.parseInt(scoreText);
            } catch (NumberFormatException ignored) {
            }
            score = parsed;
         } else {
            name = trimmed;
            scoreText = "";
            score = 0;
         }
         result.add(new ScoreboardHUD.Entry(name, scoreText, score));
      }
      return result;
   }

   private static List<ScoreboardHUD.Entry> collectEntries(ScoreboardObjective objective) {
      List<ScoreboardHUD.Entry> result = entryBuffer;
      result.clear();
      try {
         Scoreboard scoreboard = objective.getScoreboard();
         if (scoreboard == null) {
            return result;
         }
         Collection<ScoreboardEntry> rawEntries = scoreboard.getScoreboardEntries(objective);
         if (rawEntries != null) {
            for (ScoreboardEntry se : rawEntries) {
               String name;
               if (se.name() != null) {
                  name = se.name().getString();
               } else {
                  name = se.owner();
               }
               if (name == null) {
                  name = "";
               }
               int value = se.value();
               result.add(new ScoreboardHUD.Entry(name, String.valueOf(value), value));
            }
         }
      } catch (Throwable ignored) {
      }

      if (result.size() > 1) {
         result.sort((a, b) -> Integer.compare(b.score, a.score));
      }
      if (result.size() > MAX_ENTRIES) {
         result.subList(MAX_ENTRIES, result.size()).clear();
      }
      return result;
   }

   @Environment(EnvType.CLIENT)
   private static final class Entry {
      final String name;
      final String scoreText;
      final int score;

      Entry(String name, String scoreText, int score) {
         this.name = name;
         this.scoreText = scoreText;
         this.score = score;
      }
   }
}