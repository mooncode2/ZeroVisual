package ru.zero.ui.gui.component.render;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.component.setting.GuiRenderSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.backends.gl.StencilHelper;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public class GuiRenderUpPanel extends GuiScreen {
   public static void renderUpPanel(Renderer2D renderer2D, MatrixStack pose, float mainAlpha) {
      if (mainAlpha <= 0.001F) {
         return;
      }

      boolean vanillaStyle = GuiScreen.isVanillaStyle();
      int outlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
      int backGroundTwoColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundTwoColor(1, 1), (int)(178.5F * mainAlpha));
      int backGroundThreeColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(10.2F * mainAlpha));
      int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(255.0F * mainAlpha));
      int textColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(255.0F * mainAlpha));
      int textTwoColor40 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextTwoColor(1, 1), (int)(80.0F * mainAlpha));
      Color mainColorGlow = Renderer2D.ColorUtil.getColor(Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(35.0F * mainAlpha)));
      renderer2D.flush();
      StencilHelper.initStencil();
      RenderUtil.drawRoundedCorner(renderer2D, GuiScreen.x, GuiScreen.y, 366.475F, 33.7F, new Vector4f(6.5F, 0.0F, 0.0F, 0.0F), -1);
      renderer2D.flush();
      StencilHelper.bindReadStencilBuffer(1);
      renderer2D.rectOutline(GuiScreen.x, GuiScreen.y, 366.475F, 36.7F, 6.5F, outlineColor, 0.5F);
      renderer2D.rect(GuiScreen.x, GuiScreen.y, 366.475F, 36.7F, 6.5F, backGroundTwoColor);
      renderer2D.flush();
      StencilHelper.uninitStencilBuffer();
      renderer2D.shadow(GuiScreen.x + 9.92F + 5.5F, GuiScreen.y + 11.355F + 5.5F, 0.1F, 0.1F, 6.0F, 10.5F, 0.1F, mainColorGlow.getRGB());
      // Лого клиента в ClickGui: круг вместо символьной иконки.
      float logoCx = GuiScreen.x + 16.3F;
      float logoCy = GuiScreen.y + 16.85F;
      renderer2D.circle(logoCx, logoCy, 6.1F, 0.0F, 1.0F, mainColor);
      renderer2D.circle(
         logoCx,
         logoCy,
         3.9F,
         0.0F,
         1.0F,
         Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), (int)(210.0F * mainAlpha))
      );
      renderer2D.text(FontRegistry.INTER_MEDIUM, GuiScreen.x + 28.17F, GuiScreen.y + 12.595F + 7.0F, 14.0F, "Zero", textColor);
      renderer2D.text(FontRegistry.INTER_MEDIUM, GuiScreen.x + 66.99F, GuiScreen.y + 12.595F + 7.0F, 14.0F, "1.21.11", textTwoColor40);
      renderer2D.rectOutline(GuiScreen.x + 111.885F, GuiScreen.y + 6.185F, 124.04F, 21.325F, 5.5F, outlineColor, 0.1F);
      renderer2D.rect(GuiScreen.x + 111.885F, GuiScreen.y + 6.185F, 124.04F, 21.325F, 5.5F, backGroundThreeColor);
      renderer2D.text(vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS, GuiScreen.x + 119.87F, GuiScreen.y + 12.335F - 1.0F + 9.75F, 17.5F, vanillaStyle ? "?" : "C", mainColor);
      float searchTextX = GuiScreen.x + 134.775F;
      float searchTextY = GuiScreen.y + 12.595F + 7.0F - 0.4F;
      String searchDisplayText;
      if (GuiScreen.activeSearch) {
         searchDisplayText = GuiScreen.searchText.isEmpty() ? "" : GuiScreen.searchText;
      } else {
         searchDisplayText = "Search";
      }

      renderer2D.text(FontRegistry.INTER_MEDIUM, searchTextX, searchTextY, 14.0F, searchDisplayText, textTwoColor40);
      if (GuiScreen.activeSearch) {
         long currentTime = System.currentTimeMillis();
         boolean showCursor = currentTime / 500L % 2L == 0L;
         if (showCursor) {
            float textWidth = renderer2D.measureText(FontRegistry.INTER_MEDIUM, searchDisplayText, 14.0F).width;
            float cursorX = searchTextX + textWidth;
            float cursorY = searchTextY - 0.5F;
            renderer2D.rect(cursorX, cursorY - 5.0F, 1.0F, 7.0F, 0.5F, mainColor);
         }
      }

      renderer2D.rectOutline(GuiScreen.x + 312.23F, GuiScreen.y + 6.185F, 21.325F, 21.325F, 5.5F, outlineColor, 0.1F);
      renderer2D.rect(GuiScreen.x + 312.23F, GuiScreen.y + 6.185F, 21.325F, 21.325F, 5.5F, backGroundThreeColor);
      renderer2D.text(vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS, GuiScreen.x + 318.55F, GuiScreen.y + 12.85F - 1.5F + 9.8F, 18.0F, vanillaStyle ? "S" : "W", mainColor);
      renderer2D.rectOutline(GuiScreen.x + 338.555F, GuiScreen.y + 6.185F, 21.325F, 21.325F, 5.5F, outlineColor, 0.1F);
      renderer2D.rect(GuiScreen.x + 338.555F, GuiScreen.y + 6.185F, 21.325F, 21.325F, 5.5F, backGroundThreeColor);
      renderer2D.text(vanillaStyle ? FontRegistry.INTER_MEDIUM : FontRegistry.ICONS, GuiScreen.x + 344.95F, GuiScreen.y + 12.85F - 1.5F + 9.65F, 18.0F, vanillaStyle ? "x" : "X", mainColor);
      if (GuiScreen.showClientSettingsPopup) {
         renderClientSettingsPopup(renderer2D, mainAlpha);
      }
   }

   private static void renderClientSettingsPopup(Renderer2D renderer2D, float mainAlpha) {
      float popupWidth = 100.0F;
      float popupHeight = 86.0F;
      float popupX = GuiScreen.x + 450.0F + 21.325F - popupWidth;
      float popupY = GuiScreen.y - 15.0F + 21.325F + 5.0F;
      int outlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
      int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(255.0F * mainAlpha));
      int mainColor40 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(102.0F * mainAlpha));
      int textColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(255.0F * mainAlpha));
      drawClientRect(renderer2D, popupX, popupY, popupWidth, popupHeight, 5.0F, mainAlpha, 1.0F);
      float settingY = popupY + 10.0F;
      float settingX = popupX + 10.0F;
      float settingWidth = popupWidth - 20.0F;
      GuiRenderSetting.renderSetting(
         renderer2D,
         GuiScreen.clientBlurSetting,
         settingX,
         settingY,
         settingWidth,
         GuiScreen.currentMouseX,
         GuiScreen.currentMouseY,
         outlineColor,
         mainColor,
         Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(15.3F * mainAlpha)),
         mainColor40,
         textColor,
         mainAlpha
      );
      float secondSettingY = settingY + GuiRenderSetting.getSettingHeight(renderer2D, GuiScreen.clientBlurSetting) + 3.0F;
      GuiRenderSetting.renderSetting(
         renderer2D,
         GuiScreen.clientVanillaSetting,
         settingX,
         secondSettingY,
         settingWidth,
         GuiScreen.currentMouseX,
         GuiScreen.currentMouseY,
         outlineColor,
         mainColor,
         Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(15.3F * mainAlpha)),
         mainColor40,
         textColor,
         mainAlpha
      );
      float thirdSettingY = secondSettingY + GuiRenderSetting.getSettingHeight(renderer2D, GuiScreen.clientVanillaSetting) + 3.0F;
      GuiRenderSetting.renderSetting(
         renderer2D,
         GuiScreen.clientVulcanSetting,
         settingX,
         thirdSettingY,
         settingWidth,
         GuiScreen.currentMouseX,
         GuiScreen.currentMouseY,
         outlineColor,
         mainColor,
         Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(15.3F * mainAlpha)),
         mainColor40,
         textColor,
         mainAlpha
      );
   }

   public static void drawClientRect(Renderer2D r2, float x, float y, float w, float h, float radius, float alpha, float thickness) {
      if (!GuiScreen.clientBlurSetting.get()) {
         r2.prepareBlurRegion(x, y, w, h, 23.0F);
         r2.blurRegion(x, y, w, h, radius, alpha);
      }

      r2.rectOutline(x - 1.0F, y - 1.0F, w + 2.0F, h + 2.0F, radius, ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), alpha * 0.1F), thickness);
      r2.rect(x, y, w, h, radius, ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), alpha * 0.7F));
   }
}
