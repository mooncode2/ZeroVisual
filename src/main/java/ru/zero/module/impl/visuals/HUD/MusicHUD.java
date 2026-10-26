package ru.zero.module.impl.visuals.HUD;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.event.input.MouseButtonEvent;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.module.impl.visuals.HUD.island.Easing;
import ru.zero.module.impl.visuals.HUD.island.IslandSpring;
import ru.zero.module.impl.visuals.HUD.island.lyrics.LyricLine;
import ru.zero.module.impl.visuals.HUD.island.lyrics.LyricTrack;
import ru.zero.module.impl.visuals.HUD.island.lyrics.LyricsProvider;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.util.client.MediaNowPlaying;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontObject;
import ru.zero.util.render.text.FontRegistry;

/**
 * Music-only HUD element, ported from EvoVis' {@code qy}/{@code qu}.
 *
 * <p>The element shows nothing but the currently playing media: a compact pill while collapsing
 * and the expanded player panel (artwork, title/artist, progress, lyrics and transport controls)
 * while a track is playing. With no media it hides completely.
 *
 * <p>Everything is drawn with Zero's own primitives, so no bespoke textures/icon glyphs are needed.
 * Transport controls are interactive while a screen is open, matching Zero's HUD input model.
 */
@Environment(EnvType.CLIENT)
public final class MusicHUD {
   private static final MinecraftClient mc = MinecraftClient.getInstance();

   // ---- row layout -------------------------------------------------------
   private static final float TOP_Y = 4.0F;
   private static final float BAR_H = 20.0F;
   private static final float PAD = 8.0F;
   private static final float GAP = 5.0F;
   private static final float ICON = 12.0F;
   private static final float EQ_W = 11.4F;
   private static final float MIN_PILL_W = 54.0F;
   private static final float RIGHT_PAD_EXTRA = 3.0F;
   private static final float MEDIA_TITLE_MAX = 132.0F;

   // ---- fonts ------------------------------------------------------------
   private static final float LABEL_SIZE = 17.0F;
   private static final float TITLE_SIZE = 19.0F;
   private static final float ARTIST_SIZE = 14.0F;
   private static final float TIME_SIZE = 12.0F;

   private static final FontObject F_STRONG = FontRegistry.INTER_SEMIBOLD;
   private static final FontObject F_LABEL = FontRegistry.INTER_MEDIUM;

   // ---- panel ------------------------------------------------------------
   private static final float PANEL_RADIUS = 11.0F;
   private static final float PANEL_PAD = 9.0F;
   private static final float PANEL_ART = 30.0F;
   private static final float PANEL_ART_RADIUS = 5.0F;
   private static final float PANEL_MIN_W = 152.0F;
   private static final float PANEL_MAX_W = 214.0F;
   private static final float PANEL_BAR_H = 3.0F;
   private static final float MAIN_BTN = 19.5F;
   private static final float BTN = 15.0F;
   private static final float BTN_GAP = 11.0F;
   private static final float EQ_MIN_H = 1.6F;
   private static final float EQ_MAX_H = 8.0F;
   private static final float PANEL_CONTENT_MIN_SCALE = 0.94F;
   private static final float EXPAND_INTERACTIVE = 0.6F;

   // ---- lyrics -----------------------------------------------------------
   private static final float LYRIC_MAX_WIDTH = 150.0F;
   private static final long LYRIC_LEAD_MS = 150L;
   private static final float LYRIC_SWEEP_MS_PER_CHAR = 90.0F;
   private static final float LYRIC_SWEEP_MIN_MS = 400.0F;
   private static final float LYRIC_FOCUS_RATIO = 0.6F;
   private static final float LYRIC_SWAP_MS = 720.0F;
   private static final float LYRIC_ENTER_DELAY = 0.14F;
   private static final float LYRIC_LAST_LINE_MS = 4000.0F;

   private static final float[] EQ_ENVELOPE = {0.42F, 0.74F, 1.0F, 0.78F, 0.5F};

   // ---- springs ----------------------------------------------------------
   private static final Spring PILL = new Spring(0.7156F, 5.467F, 250.0F, 0.0F);
   private static final Spring EXPAND = new Spring(0.7156F, 5.467F, 250.0F, 0.0F);
   private static final Spring EQ_PLAY = new Spring(1.0F, 5.834F, 200.0F, 0.0F);
   private static final float[] EQ_LEVELS = new float[5];

   // ---- media state ------------------------------------------------------
   private static boolean mediaPresent;
   private static String mediaLabel = "";
   private static boolean primed;

   private static boolean lyricsEnabled;
   private static String lyricTrackKey = "";
   private static LyricLine lyricLine = LyricLine.EMPTY;
   private static LyricLine lyricPrevLine = LyricLine.EMPTY;
   private static int lyricIndex = -1;
   private static boolean lyricPrevActive;
   private static float lyricPrevScroll;
   private static float lyricSweep = 1.0F;
   private static long lyricSwapAt;
   private static float lyricPrevViewport;
   private static float lyricViewWidth;
   private static final IslandSpring LYRIC_SCROLL = IslandSpring.transform(IslandSpring.Motion.MEDIUM, 0.0F);

   // ---- geometry (for hit testing) --------------------------------------
   private static float panelX;
   private static float panelY;
   private static float panelW;
   private static float panelH;
   private static float panelArtX;
   private static float panelArtY;
   private static float panelTextX;
   private static float panelTextW;
   private static float panelBarY;
   private static float panelTimeY;
   private static float controlsCenterY;
   private static boolean panelShown;

   private static long lastFrameNanos;

   private MusicHUD() {
   }

   public static void setLyricsEnabled(boolean enabled) {
      lyricsEnabled = enabled;
      if (!enabled) {
         resetLyrics();
      }
   }

   public static void render(Renderer2D r2) {
      try {
         renderIsland(r2);
      } catch (Throwable ignored) {
         // Fail-safe: one broken HUD element must not disable entire HUD rendering.
      }
   }

   private static void renderIsland(Renderer2D r2) {
      long now = System.currentTimeMillis();
      updateMedia();
      updateLyrics(r2, now);

      float totalW = restingRowWidth(r2);
      float screenW = mc.getWindow().getFramebufferWidth();
      DraggableManager.DragSession session = DraggableManager.getInstance()
         .beginDrag("island", screenW / 2.0F - totalW / 2.0F, TOP_Y, totalW, BAR_H);
      try {
         drawIsland(r2, delta(), now, session.positionX(), session.positionY());
         HudEditor.registerRect(session.positionX(), session.positionY(), totalW, BAR_H);
      } finally {
         DraggableManager.getInstance().endDrag(session);
      }
   }

   private static float restingRowWidth(Renderer2D r2) {
      return mediaPresent ? pillWidth(r2) : MIN_PILL_W;
   }

   private static void drawIsland(Renderer2D r2, float dt, long now, float x, float y) {
      if (!hasVisibleContent()) {
         panelShown = false;
         return;
      }

      float baseW = finite(pillWidth(r2), MIN_PILL_W);
      float pillTarget = baseW;
      if (!primed) {
         PILL.jumpTo(pillTarget);
         primed = true;
      }

      float pw = PILL.update(dt, pillTarget);
      if (!Float.isFinite(pw) || pw <= 0.0F) {
         pw = MIN_PILL_W;
      }
      float centerX = x + baseW / 2.0F;
      if (!Float.isFinite(centerX)) {
         centerX = mc.getWindow().getFramebufferWidth() / 2.0F;
      }
      float pillLeft = centerX - pw / 2.0F;

      updateEqualizer(now, dt);

      float rawExpand = Math.max(EXPAND.update(dt, 1.0F), 0.0F);
      float e = Math.min(rawExpand, 1.0F);
      panelShown = true;

      layoutPanel(r2, centerX, y);

      float viewportW = Math.max(1.0F, mc.getWindow().getFramebufferWidth());
      float viewportH = Math.max(1.0F, mc.getWindow().getFramebufferHeight());
      float morphW = Math.max(MIN_PILL_W, Math.max(pw, finite(panelW, pw)));
      float morphH = Math.max(BAR_H, finite(panelH, BAR_H));
      float shapeW = lerp(e, pw, finite(panelW, pw));
      float shapeH = lerp(e, BAR_H, finite(panelH, BAR_H));
      if (!Float.isFinite(shapeW) || shapeW <= 0.0F) {
         shapeW = pw;
      }
      if (!Float.isFinite(shapeH) || shapeH <= 0.0F) {
         shapeH = BAR_H;
      }
      shapeW = clamp(shapeW, MIN_PILL_W, Math.min(viewportW, morphW));
      shapeH = clamp(shapeH, BAR_H, Math.min(viewportH, morphH));
      float shapeLeft = centerX - shapeW / 2.0F;
      if (!Float.isFinite(shapeLeft)) {
         shapeLeft = (viewportW - shapeW) / 2.0F;
      }
      shapeLeft = clamp(shapeLeft, 0.0F, Math.max(0.0F, viewportW - shapeW));
      float radius = finite(lerp(e, BAR_H / 2.0F, PANEL_RADIUS), BAR_H / 2.0F);
      radius = clamp(radius, 0.0F, Math.min(shapeW, shapeH) / 2.0F);

      float pillA = clamp01((0.34F - e) / 0.34F);
      float panelA = clamp01((e - 0.44F) / 0.56F);

      Hud.drawClientRect(r2, shapeLeft, y, shapeW, shapeH, radius, 1.0F, 1.0F);
      r2.pushRoundedClipRect(shapeLeft, y, shapeW, shapeH, radius, radius, radius, radius);
      try {
         drawAccentWash(r2, shapeLeft, y, shapeW, shapeH, radius, accent(), pillA);

         if (pillA > 0.01F) {
            drawPill(r2, pillLeft, y, pw, BAR_H, pillA, now);
         }

         if (panelA > 0.01F) {
            drawPlayer(r2, panelA, now);
         }
      } finally {
         r2.popClipRect();
      }

      int borderColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), 0.14F);
      r2.rectOutline(shapeLeft, y, shapeW, shapeH, radius, borderColor, 1.0F);
   }

   // -----------------------------------------------------------------------
   // Media state
   // -----------------------------------------------------------------------

   private static boolean hasVisibleContent() {
      return mediaPresent;
   }

   private static void updateMedia() {
      if (!MediaNowPlaying.hasTrack()) {
         mediaPresent = false;
         mediaLabel = "";
         return;
      }

      mediaPresent = true;
      String title = MediaNowPlaying.title();
      mediaLabel = title == null || title.isEmpty() ? "Nothing playing" : title;
   }

   private static int accent() {
      return Renderer2D.ColorUtil.getMainColor(1, 1);
   }

   private static String playerTitle() {
      return mediaLabel == null || mediaLabel.isEmpty() ? "Nothing playing" : mediaLabel;
   }

   private static String playerArtist() {
      String artist = MediaNowPlaying.artist();
      return artist == null || artist.isEmpty() ? "Unknown artist" : artist;
   }

   // -----------------------------------------------------------------------
   // Pill
   // -----------------------------------------------------------------------

   private static void drawPill(Renderer2D r2, float x, float y, float w, float h, float alpha, long now) {
      float iconLeft = x + PAD;
      float iconCx = iconLeft + ICON / 2.0F;
      float cy = y + h / 2.0F;

      drawPillIcon(r2, iconCx, cy, alpha);
      float textX = iconLeft + ICON + GAP;
      float textY = baseline(F_LABEL, cy, LABEL_SIZE);
      int color = ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), alpha);

      float eqX = x + w - PAD - EQ_W;
      float avail = eqX - GAP - textX;
      if (avail > 8.0F) {
         float viewW = Math.min(avail, LYRIC_MAX_WIDTH);
         lyricViewWidth = viewW;
         if (lyricsShowing()) {
            float lineTop = cy - LABEL_SIZE * 0.7F / 2.0F;
            float enter = Easing.clamp01((lyricTransition(now) - LYRIC_ENTER_DELAY) / (1.0F - LYRIC_ENTER_DELAY));
            lyricLine.draw(r2, textX, lineTop, viewW, lyricScrollValue(), lyricSweep, enter, false, true,
                  Renderer2D.ColorUtil.getTextColor(1, 1), accent(), alpha, now);
         } else {
            r2.text(F_LABEL, textX, textY, LABEL_SIZE, ellipsize(r2, F_LABEL, mediaLabel, LABEL_SIZE, viewW), color);
         }
      }

      drawEqualizer(r2, eqX, cy, ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), alpha), now);
   }

   private static void drawPillIcon(Renderer2D r2, float cx, float cy, float alpha) {
      float half = ICON / 2.0F;
      int art = MediaNowPlaying.artworkTexture();
      if (art > 0) {
         r2.drawRgbaTextureWithUVRounded(art, cx - half, cy - half, ICON, ICON, 0.0F, 0.0F, 1.0F, 1.0F, 3.0F);
      } else {
         int accent = ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), alpha);
         r2.rect(cx - half, cy - half, ICON, ICON, 3.0F, ColorUtil.replAlpha(accent, 0.22F));
         drawNote(r2, cx, cy, accent);
      }
   }

   private static float pillWidth(Renderer2D r2) {
      String label = mediaLabel == null ? "" : mediaLabel;
      float w = finite(r2.measureText(F_LABEL, label, LABEL_SIZE).width, 0.0F);
      boolean lyrics = lyricsShowing();
      float max = lyrics ? LYRIC_MAX_WIDTH : MEDIA_TITLE_MAX;
      float body = lyrics ? lyricLine.width() : w;
      w = finite(Math.min(body, max), 0.0F) + GAP + EQ_W;
      return Math.max(MIN_PILL_W, PAD + ICON + GAP + w + PAD + RIGHT_PAD_EXTRA);
   }

   private static void drawAccentWash(Renderer2D r2, float x, float y, float w, float h, float radius, int accent, float alpha) {
      if (alpha <= 0.01F) {
         return;
      }

      float washW = Math.min(w, Math.min(w * 0.62F, h * 3.2F));
      r2.horizontalGradient(x, y, washW, h, ColorUtil.replAlpha(accent, 0.36F * alpha), 0x00000000);
   }

   // -----------------------------------------------------------------------
   // Media panel
   // -----------------------------------------------------------------------

   private static void layoutPanel(Renderer2D r2, float centerX, float y) {
      String title = playerTitle();
      String artist = playerArtist();
      float titleW = r2.measureText(F_STRONG, title, TITLE_SIZE).width;
      float artistW = r2.measureText(F_LABEL, artist, ARTIST_SIZE).width;
      float microH = r2.measureText(F_LABEL, "0", TIME_SIZE).height;

      float w = PANEL_PAD + PANEL_ART + 8.0F + Math.max(titleW, artistW) + 6.0F + EQ_W + PANEL_PAD;
      panelW = clamp(w, PANEL_MIN_W, PANEL_MAX_W);
      panelH = PANEL_PAD + PANEL_ART + 8.0F + PANEL_BAR_H + 2.5F + microH + 6.0F + MAIN_BTN + PANEL_PAD;
      panelX = centerX - panelW / 2.0F;
      panelY = y;
      panelArtX = panelX + PANEL_PAD;
      panelArtY = panelY + PANEL_PAD;
      panelTextX = panelArtX + PANEL_ART + 8.0F;
      panelTextW = panelX + panelW - PANEL_PAD - EQ_W - 6.0F - panelTextX;
      panelBarY = panelArtY + PANEL_ART + 8.0F;
      panelTimeY = panelBarY + PANEL_BAR_H + 2.5F;
      controlsCenterY = panelTimeY + microH + 6.0F + MAIN_BTN / 2.0F;
   }

   private static void drawPlayer(Renderer2D r2, float alpha, long now) {
      float scale = PANEL_CONTENT_MIN_SCALE + (1.0F - PANEL_CONTENT_MIN_SCALE) * alpha;
      float cx = panelX + panelW / 2.0F;
      float cy = panelY + panelH / 2.0F;
      r2.pushScale(scale, scale, cx, cy);
      try {
         drawPlayerArtwork(r2, panelArtX, panelArtY, alpha);

         float titleBlockH = r2.measureText(F_STRONG, "0", TITLE_SIZE).height + 1.5F + r2.measureText(F_LABEL, "0", ARTIST_SIZE).height;
         float textY = panelArtY + (PANEL_ART - titleBlockH) / 2.0F;
         marquee(r2, F_STRONG, TITLE_SIZE, playerTitle(), panelTextX, textY, panelTextW, ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), alpha));
         marquee(
            r2,
            F_LABEL,
            ARTIST_SIZE,
            playerArtist(),
            panelTextX,
            textY + r2.measureText(F_STRONG, "0", TITLE_SIZE).height + 1.5F,
            panelTextW,
            ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), alpha * 0.58F));

         drawEqualizer(r2, panelX + panelW - PANEL_PAD - EQ_W, panelArtY + PANEL_ART / 2.0F, ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), alpha), now);
         drawProgress(r2, alpha);
         drawControls(r2, alpha, now);
      } finally {
         r2.popScale();
      }
   }

   private static void drawPlayerArtwork(Renderer2D r2, float x, float y, float alpha) {
      int art = MediaNowPlaying.artworkTexture();
      if (art > 0) {
         r2.drawRgbaTextureWithUVRounded(art, x, y, PANEL_ART, PANEL_ART, 0.0F, 0.0F, 1.0F, 1.0F, PANEL_ART_RADIUS);
         return;
      }

      r2.rect(x, y, PANEL_ART, PANEL_ART, PANEL_ART_RADIUS, ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), alpha * 0.14F));
      drawNote(r2, x + PANEL_ART / 2.0F, y + PANEL_ART / 2.0F, ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), alpha));
   }

   private static void drawProgress(Renderer2D r2, float alpha) {
      float trackX = panelX + PANEL_PAD;
      float trackW = panelW - PANEL_PAD * 2.0F;
      r2.rect(trackX, panelBarY, trackW, PANEL_BAR_H, 1.5F, ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), alpha * 0.22F));

      long duration = MediaNowPlaying.durationMs();
      long position = MediaNowPlaying.positionMs();
      if (duration > 0L) {
         float frac = clamp01((float) position / (float) duration);
         r2.rect(trackX, panelBarY, Math.max(3.0F, trackW * frac), PANEL_BAR_H, 1.5F, ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), alpha));
      }

      int timeColor = ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), alpha * 0.52F);
      String left = duration > 0L ? fmtTime(position) : "0:00";
      String right = duration > 0L ? "-" + fmtTime(Math.max(0L, duration - position)) : "0:00";
      // r2.text() takes a baseline, and panelTimeY marks the top of the row reserved below the
      // bar. Center the labels inside that row so they sit under the progress bar instead of on it.
      float timeH = r2.measureText(F_LABEL, "0", TIME_SIZE).height;
      float timeY = baseline(F_LABEL, panelTimeY + timeH / 2.0F, TIME_SIZE);
      r2.text(F_LABEL, trackX, timeY, TIME_SIZE, left, timeColor);
      r2.text(F_LABEL, trackX + trackW, timeY, TIME_SIZE, right, timeColor, "r");
   }

   private static void drawControls(Renderer2D r2, float alpha, long now) {
      int hover = mc.currentScreen != null ? buttonIndexAt(mouseX(), mouseY()) : -1;
      float width = controlsWidth();
      float startX = panelX + (panelW - width) / 2.0F;
      float cursor = startX;
      for (int i = 0; i < 3; i++) {
         float size = buttonSize(i);
         float top = controlsCenterY - size / 2.0F;
         if (hover == i) {
            r2.rect(cursor, top, size, size, 2.0F, ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), alpha * 0.2F));
         }

         float iconSize = size * 0.52F;
         float iconOff = (size - iconSize) / 2.0F;
         int color = ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), alpha);
         float ix = cursor + iconOff;
         float iy = top + iconOff;
         if (i == 0) {
            drawPreviousIcon(r2, ix, iy, iconSize, iconSize, color);
         } else if (i == 1) {
            if (MediaNowPlaying.playing()) {
               drawPauseIcon(r2, ix, iy, iconSize, iconSize, color);
            } else {
               drawPlayIcon(r2, ix, iy, iconSize, iconSize, color);
            }
         } else {
            drawNextIcon(r2, ix, iy, iconSize, iconSize, color);
         }

         cursor += size + BTN_GAP;
      }
   }

   private static float controlsWidth() {
      return 2.0F * BTN_GAP + BTN + MAIN_BTN + BTN;
   }

   private static float buttonSize(int index) {
      return index == 1 ? MAIN_BTN : BTN;
   }

   private static float buttonLeft(int index) {
      float startX = panelX + (panelW - controlsWidth()) / 2.0F;
      float cursor = startX;
      for (int i = 0; i < index; i++) {
         cursor += buttonSize(i) + BTN_GAP;
      }

      return cursor;
   }

   private static int buttonIndexAt(float mx, float my) {
      for (int i = 0; i < 3; i++) {
         float size = buttonSize(i);
         float left = buttonLeft(i);
         float top = controlsCenterY - size / 2.0F;
         if (isHovered(mx, my, left, top, size, size)) {
            return i;
         }
      }

      return -1;
   }

   // -----------------------------------------------------------------------
   // Input
   // -----------------------------------------------------------------------

   public static void onMouseButton(MouseButtonEvent event) {
      if (event == null || !Hud.element.get("Музыка")) {
         return;
      }
      if (mc.currentScreen == null || !event.isPress() || event.button() != 0) {
         return;
      }
      if (!panelShown || EXPAND.value() < EXPAND_INTERACTIVE) {
         return;
      }

      float mx = localX(event.cursorX());
      float my = localY(event.cursorY());
      int index = buttonIndexAt(mx, my);
      if (index >= 0) {
         if (index == 0) {
            MediaNowPlaying.previous();
         } else if (index == 1) {
            MediaNowPlaying.playPause();
         } else {
            MediaNowPlaying.next();
         }

         event.cancel();
      }
   }

   // -----------------------------------------------------------------------
   // Update helpers
   // -----------------------------------------------------------------------

   private static void updateEqualizer(long now, float dt) {
      boolean playing = mediaPresent && MediaNowPlaying.playing();
      float progress = clamp01(EQ_PLAY.update(dt, playing ? 1.0F : 0.0F));
      for (int i = 0; i < EQ_LEVELS.length; i++) {
         float raw = playing ? (float) Math.abs(Math.sin(now / 260.0 + i * 1.05F)) * EQ_ENVELOPE[i] : 0.0F;
         float target = raw * progress;
         float delta = target - EQ_LEVELS[i];
         EQ_LEVELS[i] = EQ_LEVELS[i] + delta * (delta > 0.0F ? 0.42F : 0.16F);
      }
   }

   private static boolean lyricsShowing() {
      return lyricsEnabled && lyricIndex != -1;
   }

   private static float lyricTransition(long now) {
      return Easing.clamp01((now - lyricSwapAt) / LYRIC_SWAP_MS);
   }

   private static float lyricScrollValue() {
      return lyricIndex == -1 ? 0.0F : LYRIC_SCROLL.value();
   }

   private static void resetLyrics() {
      lyricLine = LyricLine.EMPTY;
      lyricPrevLine = LyricLine.EMPTY;
      lyricTrackKey = "";
      lyricIndex = -1;
      lyricSweep = 1.0F;
      lyricSwapAt = 0L;
      LYRIC_SCROLL.jumpTo(0.0F);
   }

   private static void updateLyrics(Renderer2D r2, long now) {
      if (!lyricsEnabled) {
         return;
      }
      String title = MediaNowPlaying.title();
      if (!MediaNowPlaying.hasTrack() || title == null || title.isEmpty()) {
         resetLyrics();
         return;
      }

      int index = -1;
      float sweep = 1.0F;
      LyricTrack track = null;
      if (MediaNowPlaying.durationMs() > 0L) {
         track = LyricsProvider.getInstance().lookup(MediaNowPlaying.artist(), title, MediaNowPlaying.durationMs());
      }
      if (track != null && track.size() > 0) {
         long position = MediaNowPlaying.positionMs() + LYRIC_LEAD_MS;
         int i = track.indexAt(position);
         if (i >= 0 && !track.line(i).isEmpty()) {
            String lineText = track.line(i);
            long start = track.start(i);
            float sweepMs = Math.max(LYRIC_SWEEP_MIN_MS, lineText.length() * LYRIC_SWEEP_MS_PER_CHAR);
            long end = track.end(i, (long) LYRIC_LAST_LINE_MS);
            float span = Math.max(1.0F, Math.min((float) (end - start), sweepMs));
            sweep = Easing.clamp01((position - start) / span);
            title = lineText;
            index = i;
         }
      }

      String key = MediaNowPlaying.trackKey();
      if (!key.equals(lyricTrackKey)) {
         lyricTrackKey = key;
         lyricLine = LyricLine.layout(r2, F_LABEL, LABEL_SIZE, title);
         lyricIndex = index;
         lyricPrevLine = LyricLine.EMPTY;
         lyricSwapAt = 0L;
         LYRIC_SCROLL.jumpTo(0.0F);
      } else if (index != lyricIndex || !lyricLine.matches(F_LABEL, LABEL_SIZE, title)) {
         if (index != lyricIndex || !lyricLine.text().equals(title)) {
            lyricPrevLine = lyricLine;
            lyricPrevActive = lyricIndex != -1;
            lyricPrevScroll = LYRIC_SCROLL.value();
            lyricPrevViewport = lyricViewWidth;
            lyricSwapAt = now;
            LYRIC_SCROLL.jumpTo(0.0F);
         }
         lyricLine = LyricLine.layout(r2, F_LABEL, LABEL_SIZE, title);
         lyricIndex = index;
      }
      lyricSweep = sweep;

      float span = Math.max(0.0F, lyricLine.width() - lyricViewWidth);
      float focus = lyricLine.sweepFocus(sweep) - lyricViewWidth * LYRIC_FOCUS_RATIO;
      LYRIC_SCROLL.update(lyricIndex == -1 ? 0.0F : Easing.clamp(focus, 0.0F, span));
   }

   private static void drawEqualizer(Renderer2D r2, float x, float centerY, int color, long now) {
      float alphaScale = lerp(clamp01(EQ_PLAY.value()), 0.45F, 1.0F);
      int c = ColorUtil.replAlpha(color, alphaScale);
      float cursor = x;
      for (int i = 0; i < EQ_LEVELS.length; i++) {
         float barH = EQ_MIN_H + (EQ_MAX_H - EQ_MIN_H) * EQ_LEVELS[i];
         r2.rect(cursor, centerY - barH / 2.0F, 1.4F, barH, 0.7F, c);
         cursor += 2.5F;
      }
   }

   private static void drawNote(Renderer2D r2, float cx, float cy, int color) {
      int c = ColorUtil.replAlpha(color, 0.9F);
      r2.rect(cx - 1.0F, cy - 8.0F, 2.0F, 12.0F, 1.0F, c);
      r2.rect(cx - 1.0F, cy - 8.0F, 9.0F, 2.0F, 1.0F, c);
      r2.circle(cx - 2.5F, cy + 4.0F, 3.2F, 0.0F, 1.0F, c);
   }

   // Transport glyphs mirroring the provided play/pause/previous/next SVG icons: a right-pointing
   // triangle for play, two rounded bars for pause, and a triangle paired with an end bar for the
   // skip buttons (bar on the left for previous, on the right for next).

   private static void drawPlayIcon(Renderer2D r2, float x, float y, float w, float h, int color) {
      float inset = w * 0.1F;
      triangleRight(r2, x + inset, y, w - inset * 2.0F, h, color);
   }

   private static void drawPauseIcon(Renderer2D r2, float x, float y, float w, float h, int color) {
      float barW = w * 0.3F;
      float gap = w * 0.2F;
      float left = x + (w - (barW * 2.0F + gap)) / 2.0F;
      r2.rect(left, y, barW, h, barW * 0.22F, color);
      r2.rect(left + barW + gap, y, barW, h, barW * 0.22F, color);
   }

   private static void drawPreviousIcon(Renderer2D r2, float x, float y, float w, float h, int color) {
      float barW = w * 0.16F;
      float gap = w * 0.1F;
      r2.rect(x, y, barW, h, barW * 0.35F, color);
      triangleLeft(r2, x + barW + gap, y, w - barW - gap, h, color);
   }

   private static void drawNextIcon(Renderer2D r2, float x, float y, float w, float h, int color) {
      float barW = w * 0.16F;
      float gap = w * 0.1F;
      triangleRight(r2, x, y, w - barW - gap, h, color);
      r2.rect(x + w - barW, y, barW, h, barW * 0.35F, color);
   }

   private static void triangleRight(Renderer2D r2, float x, float y, float w, float h, int color) {
      int bands = 7;
      float bandH = h / bands;
      for (int i = 0; i < bands; i++) {
         float rowY = y + i * bandH;
         float d = Math.abs(rowY + bandH / 2.0F - (y + h / 2.0F)) / (h / 2.0F);
         float bandW = w * (1.0F - d);
         if (bandW > 0.2F) {
            r2.rect(x, rowY, bandW, bandH + 0.6F, 0.0F, color);
         }
      }
   }

   private static void triangleLeft(Renderer2D r2, float x, float y, float w, float h, int color) {
      int bands = 7;
      float bandH = h / bands;
      for (int i = 0; i < bands; i++) {
         float rowY = y + i * bandH;
         float d = Math.abs(rowY + bandH / 2.0F - (y + h / 2.0F)) / (h / 2.0F);
         float bandW = w * (1.0F - d);
         if (bandW > 0.2F) {
            r2.rect(x + w - bandW, rowY, bandW, bandH + 0.6F, 0.0F, color);
         }
      }
   }

   // -----------------------------------------------------------------------
   // Layout / math / text helpers
   // -----------------------------------------------------------------------

   private static String fmtTime(long ms) {
      long totalSeconds = Math.max(0L, ms) / 1000L;
      return String.format("%d:%02d", totalSeconds / 60L, totalSeconds % 60L);
   }

   private static String ellipsize(Renderer2D r2, FontObject font, String text, float size, float maxWidth) {
      String value = text == null ? "" : text;
      if (value.isEmpty() || maxWidth <= 0.0F || r2.measureText(font, value, size).width <= maxWidth) {
         return value;
      }

      String ellipsis = "\u2026";
      String cut = value;
      while (cut.length() > 1 && r2.measureText(font, cut + ellipsis, size).width > maxWidth) {
         cut = cut.substring(0, cut.length() - 1);
      }

      return cut + ellipsis;
   }

   private static void marquee(Renderer2D r2, FontObject font, float size, String text, float x, float y, float maxWidth, int color) {
      String value = text == null ? "" : text;
      float textWidth = r2.measureText(font, value, size).width;
      if (maxWidth <= 0.0F) {
         return;
      }
      if (textWidth <= maxWidth) {
         r2.text(font, x, y, size, value, color);
         return;
      }

      float overflow = textWidth - maxWidth;
      float phase = (float) ((Math.sin(System.currentTimeMillis() / 1600.0) + 1.0) / 2.0);
      float offset = overflow * phase;
      r2.pushClipRect((int) Math.floor(x), (int) Math.floor(y - size * 0.5F), (int) Math.ceil(maxWidth), (int) Math.ceil(size * 1.8F));
      try {
         r2.text(font, x - offset, y, size, value, color);
      } finally {
         r2.popClipRect();
      }
   }

   private static float baseline(FontObject font, float centerY, float size) {
      return centerY + FontRegistry.centeredBaselineOffset(font, '0', size);
   }

   private static float lerp(float t, float a, float b) {
      return a + (b - a) * t;
   }

   private static float finite(float value, float fallback) {
      return Float.isFinite(value) ? value : fallback;
   }

   private static float clamp(float value, float min, float max) {
      if (value < min) {
         return min;
      }
      return value > max ? max : value;
   }

   private static float clamp01(float value) {
      return clamp(value, 0.0F, 1.0F);
   }

   private static boolean isHovered(float x, float y, float px, float py, float pw, float ph) {
      return x >= px && x <= px + pw && y >= py && y <= py + ph;
   }

   private static float mouseX() {
      return localX(mc.mouse.getX());
   }

   private static float mouseY() {
      return localY(mc.mouse.getY());
   }

   private static float localX(double screenX) {
      float scale = HudEditor.getScale("island");
      float originX = HudEditor.getOriginX("island");
      return (float) (originX + (screenX - originX) / Math.max(scale, 0.001F));
   }

   private static float localY(double screenY) {
      float scale = HudEditor.getScale("island");
      float originY = HudEditor.getOriginY("island");
      return (float) (originY + (screenY - originY) / Math.max(scale, 0.001F));
   }

   private static float delta() {
      long now = System.nanoTime();
      if (lastFrameNanos == 0L) {
         lastFrameNanos = now;
         return 0.016F;
      }

      float seconds = (now - lastFrameNanos) / 1.0E9F;
      lastFrameNanos = now;
      return Math.max(0.0F, Math.min(seconds, 0.05F));
   }

   /** Implicit damped spring, mirroring EvoVis' {@code ct}. */
   private static final class Spring {
      private final float damping;
      private final float settle;
      private final float motionMs;
      private float value;
      private float velocity;
      private float target;

      Spring(float damping, float settle, float motionMs, float initial) {
         this.damping = damping;
         this.settle = settle;
         this.motionMs = motionMs;
         this.value = initial;
         this.target = initial;
      }

      float value() {
         return this.value;
      }

      void jumpTo(float v) {
         this.value = v;
         this.target = v;
         this.velocity = 0.0F;
      }

      float update(float dt, float newTarget) {
         this.target = newTarget;
         float step = Math.min(dt, 0.05F);
         float omega = this.settle * 1000.0F / Math.max(this.motionMs, 1.0F);
         float f = 1.0F + 2.0F * step * omega * this.damping;
         float oo = omega * omega;
         float hoo = step * oo;
         float hhoo = step * hoo;
         float detInv = 1.0F / (f + hhoo);
         float detX = f * this.value + step * this.velocity + hhoo * this.target;
         float detV = this.velocity + hoo * (this.target - this.value);
         this.value = detX * detInv;
         this.velocity = detV * detInv;
         if (Math.abs(this.value - this.target) < 0.001F && Math.abs(this.velocity) < 0.001F) {
            this.value = this.target;
            this.velocity = 0.0F;
         }

         return this.value;
      }
   }
}
