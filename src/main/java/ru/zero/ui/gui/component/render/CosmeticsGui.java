package ru.zero.ui.gui.component.render;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.cosmetic.Cosmetic;
import ru.zero.cosmetic.CosmeticRegistry;
import ru.zero.cosmetic.CosmeticSlot;
import ru.zero.cosmetic.CosmeticState;
import ru.zero.cosmetic.render.CosmeticRenderer;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.theme.MinecraftTheme;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.util.render.texture.TextureLoader;

/** Cosmetics tab drawn inside the Zero GUI's main content area. */
@Environment(EnvType.CLIENT)
public final class CosmeticsGui extends GuiScreen {
   private static final float STRIP_GAP = 4.0F;
   private static final float CHIP_HEIGHT = 20.0F;
   private static final float CARD_GAP = 6.0F;
   private static final int COLUMNS = 3;
   private static final float CARD_HEIGHT = 62.0F;

   private static CosmeticSlot currentSlot = CosmeticSlot.WINGS;
   private static float scroll;
   private static float maxScroll;

   private static final List<float[]> SLOT_HITS = new ArrayList<>();
   private static final List<Object[]> CARD_HITS = new ArrayList<>();

   private CosmeticsGui() {
   }

   private static List<Cosmetic> entries() {
      List<Cosmetic> all = CosmeticRegistry.bySlot(currentSlot);
      if (GuiScreen.activeSearch && !GuiScreen.searchText.isEmpty()) {
         String query = GuiScreen.searchText.toLowerCase().trim();
         List<Cosmetic> filtered = new ArrayList<>();
         for (Cosmetic c : all) {
            if (c.name.toLowerCase().contains(query) || c.key.toLowerCase().contains(query)) {
               filtered.add(c);
            }
         }
         return filtered;
      }
      return all;
   }

   public static void render(Renderer2D renderer, int mouseX, int mouseY, float alpha) {
      if (alpha <= 0.001F) {
         return;
      }

      boolean vanillaStyle = GuiScreen.isVanillaStyle();
      int outlineColor = vanillaStyle
            ? Renderer2D.ColorUtil.replAlpha(MinecraftTheme.OUTLINE, 255)
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int) (20.4F * alpha));
      int cardColor = vanillaStyle
            ? Renderer2D.ColorUtil.replAlpha(MinecraftTheme.PANEL_LIGHT, (int) (235.0F * alpha))
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (10.2F * alpha));
      int cardHover = vanillaStyle
            ? Renderer2D.ColorUtil.replAlpha(MinecraftTheme.PANEL, (int) (235.0F * alpha))
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (22.0F * alpha));
      int mainColor = vanillaStyle
            ? Renderer2D.ColorUtil.replAlpha(MinecraftTheme.GOLD, (int) (255.0F * alpha))
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int) (255.0F * alpha));
      int textColor = vanillaStyle
            ? Renderer2D.ColorUtil.replAlpha(MinecraftTheme.TEXT, (int) (255.0F * alpha))
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int) (255.0F * alpha));
      int textDim = vanillaStyle
            ? Renderer2D.ColorUtil.replAlpha(MinecraftTheme.DISABLED, (int) (200.0F * alpha))
            : Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextTwoColor(1, 1), (int) (140.0F * alpha));
      float radius = vanillaStyle ? 0.0F : 5.0F;

      float contentX = GuiScreen.x + 104.735F;
      float contentY = GuiScreen.y + 34.025F;
      float contentW = 261.5F;
      float contentH = 209.5F;

      float stripX = contentX + 5.0F;
      float stripY = contentY + 5.0F;
      float stripW = 84.0F;
      float stripH = contentH - 10.0F;

      float gridX = stripX + stripW + 6.0F;
      float gridY = contentY + 5.0F;
      float gridW = contentX + contentW - 5.0F - gridX;
      float gridH = contentH - 10.0F;

      SLOT_HITS.clear();
      renderer.pushRoundedClipRect(stripX, stripY, stripW, stripH, 0.0F, 0.0F, 0.0F, 0.0F);
      float chipY = stripY;
      for (CosmeticSlot slot : CosmeticRegistry.slots()) {
         boolean selected = slot == currentSlot;
         boolean hovered = isHovered(mouseX, mouseY, stripX, chipY, stripW, CHIP_HEIGHT);
         int bg = selected ? ColorUtil.multAlpha(mainColor, 0.22F) : (hovered ? cardHover : cardColor);
         renderer.rect(stripX, chipY, stripW, CHIP_HEIGHT, radius, bg);
         if (selected) {
            renderer.rect(stripX, chipY, 1.5F, CHIP_HEIGHT, radius, mainColor);
         }
         int label = selected ? mainColor : (hovered ? textColor : textDim);
         renderer.text(FontRegistry.INTER_MEDIUM, stripX + 8.0F, chipY + CHIP_HEIGHT * 0.5F + 4.6F, 13.0F,
               slot.getDisplayName(), label);
         SLOT_HITS.add(new float[]{stripX, chipY, stripW, CHIP_HEIGHT});
         chipY += CHIP_HEIGHT + STRIP_GAP;
      }
      renderer.popClipRect();

      List<Cosmetic> entries = entries();
      int rows = (entries.size() + COLUMNS - 1) / COLUMNS;
      float contentHeight = rows * (CARD_HEIGHT + CARD_GAP);
      maxScroll = Math.max(0.0F, contentHeight - gridH);
      if (scroll > maxScroll) {
         scroll = maxScroll;
      }
      if (scroll < 0.0F) {
         scroll = 0.0F;
      }

      CARD_HITS.clear();
      renderer.pushRoundedClipRect(gridX, gridY, gridW, gridH, 0.0F, 0.0F, 0.0F, 0.0F);
      if (entries.isEmpty()) {
         renderer.text(FontRegistry.INTER_MEDIUM, gridX + gridW * 0.5F - renderer.measureText(FontRegistry.INTER_MEDIUM, "New cosmetics land here", 13.0F).width * 0.5F,
               gridY + gridH * 0.5F, 13.0F, "New cosmetics land here", textDim);
      }

      float cardW = (gridW - CARD_GAP * (COLUMNS - 1)) / COLUMNS;
      float time = CosmeticRenderer.time();
      for (int i = 0; i < entries.size(); i++) {
         Cosmetic c = entries.get(i);
         int col = i % COLUMNS;
         int row = i / COLUMNS;
         float cardX = gridX + col * (cardW + CARD_GAP);
         float cardY = gridY + row * (CARD_HEIGHT + CARD_GAP) - scroll;
         if (cardY + CARD_HEIGHT < gridY - 1.0F || cardY > gridY + gridH + 1.0F) {
            continue;
         }

         boolean hovered = isHovered(mouseX, mouseY, cardX, cardY, cardW, CARD_HEIGHT);
         boolean equipped = CosmeticState.isEquipped(c);
         renderer.rect(cardX, cardY, cardW, CARD_HEIGHT, radius, hovered ? cardHover : cardColor);
         if (equipped) {
            renderer.rectOutline(cardX, cardY, cardW, CARD_HEIGHT, radius, mainColor, 0.8F);
         } else {
            renderer.rectOutline(cardX, cardY, cardW, CARD_HEIGHT, radius, outlineColor, 0.5F);
         }

         float previewCx = cardX + cardW * 0.5F;
         float previewCy = cardY + 24.0F;
         float previewSize = Math.min(cardW - 10.0F, 42.0F);
         drawPreview(renderer, c, previewCx, previewCy, previewSize, time);

         float nameY = cardY + CARD_HEIGHT - 15.0F;
         String name = c.name;
         float textW = renderer.measureText(FontRegistry.INTER_MEDIUM, name, 11.0F).width;
         if (textW > cardW - 4.0F) {
            while (name.length() > 1 && renderer.measureText(FontRegistry.INTER_MEDIUM, name + "…", 11.0F).width > cardW - 4.0F) {
               name = name.substring(0, name.length() - 1);
            }
            name = name + "…";
            textW = renderer.measureText(FontRegistry.INTER_MEDIUM, name, 11.0F).width;
         }
         renderer.text(FontRegistry.INTER_MEDIUM, previewCx - textW * 0.5F, nameY + 5.2F, 11.0F, name,
               equipped ? mainColor : textColor);
         CARD_HITS.add(new Object[]{c, cardX, cardY, cardW, CARD_HEIGHT});
      }
      renderer.popClipRect();
   }

   private static void drawPreview(Renderer2D renderer, Cosmetic c, float cx, float cy, float size, float time) {
      if (c.kind == Cosmetic.Kind.MELEE && c.icon != null) {
         int icon = TextureLoader.load(c.icon);
         if (icon > 0) {
            renderer.drawRgbaTexture(icon, cx - size * 0.5F, cy - size * 0.5F, size, size);
            return;
         }
      }
      switch (c.kind) {
         case MODEL:
         case MELEE:
         case PLAYERSKIN: {
            float yaw = -28.0F + (float) Math.sin(time * 0.9F + c.key.length()) * 16.0F;
            CosmeticRenderer.renderPreview(renderer, c, cx, cy, size, yaw, 12.0F, time);
            break;
         }
         case FLAT:
         case CAPE: {
            int texture = TextureLoader.load(c.texture);
            float drawW = size;
            float drawH = size * 0.6F;
            if (texture > 0) {
               renderer.drawRgbaTexture(texture, cx - drawW * 0.5F, cy - drawH * 0.5F, drawW, drawH);
            }
            break;
         }
         case NAMETAG: {
            int color = c.hueCycle ? ColorUtil.skyRainbow(1, 0)
                  : (c.stops != null && c.stops.length > 0 ? ColorUtil.overCol(c.stops[0], c.stops[c.stops.length - 1], 0.5F) : 0xFFFFFFFF);
            String sample = "Player";
            float textW = renderer.measureText(FontRegistry.INTER_SEMIBOLD, sample, 12.0F).width;
            renderer.text(FontRegistry.INTER_SEMIBOLD, cx - textW * 0.5F, cy + 4.5F, 12.0F, sample, ColorUtil.replAlpha(color, 255));
            break;
         }
         default:
            break;
      }
   }

   public static boolean click(int mouseX, int mouseY, int button) {
      if (button != 0) {
         return false;
      }

      List<CosmeticSlot> slots = CosmeticRegistry.slots();
      for (int i = 0; i < SLOT_HITS.size() && i < slots.size(); i++) {
         float[] r = SLOT_HITS.get(i);
         if (isHovered(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
            if (currentSlot != slots.get(i)) {
               currentSlot = slots.get(i);
               scroll = 0.0F;
               playClick();
            }
            return true;
         }
      }

      for (Object[] hit : CARD_HITS) {
         float x = (Float) hit[1];
         float y = (Float) hit[2];
         float w = (Float) hit[3];
         float h = (Float) hit[4];
         if (isHovered(mouseX, mouseY, x, y, w, h)) {
            CosmeticState.toggleEquipped((Cosmetic) hit[0]);
            playClick();
            return true;
         }
      }

      return false;
   }

   public static boolean scrolled(double delta) {
      if (maxScroll <= 0.0F) {
         return false;
      }

      scroll -= (float) delta * 22.0F;
      if (scroll < 0.0F) {
         scroll = 0.0F;
      }
      if (scroll > maxScroll) {
         scroll = maxScroll;
      }
      return true;
   }

   public static boolean isHovered(float mouseX, float mouseY, float x, float y, float w, float h) {
      return mouseX >= x && mouseY >= y && mouseX < x + w && mouseY < y + h;
   }

   private static void playClick() {
      ru.zero.util.client.ClientSounds.playClickGuiOpen();
   }
}
