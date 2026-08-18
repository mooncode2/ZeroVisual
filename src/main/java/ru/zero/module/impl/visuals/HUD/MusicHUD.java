package ru.zero.module.impl.visuals.HUD;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.util.render.core.Renderer2DInterface;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.util.client.MusicPlayer;

@Environment(EnvType.CLIENT)
public class MusicHUD {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   
   public static void musicWidget(Renderer2DInterface r2) {
      // Check if music is playing
      if (!MusicPlayer.isPlaying()) {
         return; // Hide widget if no music is playing
      }
      
      // Get music info
      String trackName = MusicPlayer.getCurrentTrackName();
      String artistName = MusicPlayer.getCurrentArtistName();
      String albumArtUrl = MusicPlayer.getCurrentAlbumArt();
      int currentTime = MusicPlayer.getCurrentTime();
      int totalTime = MusicPlayer.getTotalTime();
      boolean isPlaying = MusicPlayer.isPlaying();
      
      // Widget dimensions and positioning
      float widgetWidth = 250.0F;
      float widgetHeight = 80.0F;
      float x = 20.0F;
      float y = 20.0F;
      
      // Draw background
      r2.rect(x, y, widgetWidth, widgetHeight, 8.0F, 0x881A1A1A);
      r2.rectOutline(x, y, widgetWidth, widgetHeight, 8.0F, 2.0F, 0xAA00A2FF);
      
      // Draw album art placeholder
      float albumArtSize = 60.0F;
      r2.rect(x + 10, y + 10, albumArtSize, albumArtSize, 4.0F, 0xFF2A2A2A);
      
      // Draw track info
      float textX = x + 80.0F;
      float textY = y + 15.0F;
      
      r2.text(FontRegistry.INTER_SEMIBOLD, textX, textY, 16.0F, trackName, 0xFFFFFFFF);
      textY += 12.0F;
      r2.text(FontRegistry.INTER_MEDIUM, textX, textY, 14.0F, artistName, 0xFFA0A0A0);
      
      // Draw progress bar
      float progressBarWidth = 150.0F;
      float progressBarHeight = 3.0F;
      float progressBarX = x + 80.0F;
      float progressBarY = y + 50.0F;
      
      // Background
      r2.rect(progressBarX, progressBarY, progressBarWidth, progressBarHeight, 1.5F, 0x663A3A3A);
      
      // Progress
      float progress = (float)currentTime / (float)totalTime;
      r2.rect(progressBarX, progressBarY, progressBarWidth * progress, progressBarHeight, 1.5F, 0xFF00A2FF);
      
      // Draw time text
      String timeText = formatTime(currentTime) + " / " + formatTime(totalTime);
      float timeTextWidth = r2.measureText(FontRegistry.INTER_MEDIUM, timeText, 12.0F).width;
      float timeTextX = progressBarX + progressBarWidth / 2.0F - timeTextWidth / 2.0F;
      r2.text(FontRegistry.INTER_MEDIUM, timeTextX, progressBarY - 8.0F, 12.0F, timeText, 0xFFA0A0A0);
      
      // Draw control buttons
      float buttonSize = 20.0F;
      float buttonY = y + 60.0F;
      
      // Previous button
      float prevButtonX = x + 80.0F;
      drawControlButton(r2, prevButtonX, buttonY, buttonSize, "⏮", 0xFFA0A0A0);
      
      // Play/Pause button
      float playButtonX = prevButtonX + 30.0F;
      String playIcon = isPlaying ? "⏸" : "▶";
      drawControlButton(r2, playButtonX, buttonY, buttonSize, playIcon, 0xFFFFFFFF);
      
      // Next button
      float nextButtonX = playButtonX + 30.0F;
      drawControlButton(r2, nextButtonX, buttonY, buttonSize, "⏭", 0xFFA0A0A0);
      
      // Register widget bounds for HUD editor
      HudEditor.registerRect(x, y, widgetWidth, widgetHeight);
   }
   
   private static void drawControlButton(Renderer2DInterface r2, float x, float y, float size, String icon, int color) {
      // Button background
      r2.rect(x, y, size, size, 4.0F, 0x662A2A2A);
      
      // Button icon
      float iconX = x + size / 2.0F - r2.measureText(FontRegistry.ICONS, icon, 16.0F).width / 2.0F;
      float iconY = y + size / 2.0F - 8.0F;
      r2.text(FontRegistry.ICONS, iconX, iconY, 16.0F, icon, color);
   }
   
   private static String formatTime(int seconds) {
      int minutes = seconds / 60;
      int secs = seconds % 60;
      return String.format("%d:%02d", minutes, secs);
   }
}