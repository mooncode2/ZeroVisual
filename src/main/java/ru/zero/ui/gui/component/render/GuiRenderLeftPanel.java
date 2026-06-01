package ru.zero.ui.gui.component.render;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector4f;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.animation.util.Easings;
import ru.zero.util.render.backends.gl.StencilHelper;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;

@Environment(EnvType.CLIENT)
public class GuiRenderLeftPanel extends GuiScreen {
   private static final float CATEGORY_ICON_X = 9.145F;
   private static final float CATEGORY_ICON_Y_OFFSET = 50.185F;
   private static final float CATEGORY_ICON_BASELINE_OFFSET = 8.0F;
   private static final float CATEGORY_ICON_SIZE = 16.0F;
   private static final float FUNTIME_TEXT_SIZE = 12.0F;

   public static void renderLeftPanel(Renderer2D renderer2D, MatrixStack pose, float mainAlpha) {
      if (mainAlpha <= 0.001F) {
         return;
      }

      int outlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
      int backGroundTwoColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundTwoColor(1, 1), (int)(178.5F * mainAlpha));
      int backGroundThreeColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(10.2F * mainAlpha));
      int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(255.0F * mainAlpha));
      int mainColor40 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(102.0F * mainAlpha));
      int textColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(255.0F * mainAlpha));
      int textTwoColor40 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextTwoColor(1, 1), (int)(102.0F * mainAlpha));
      Color mainColorGlow = Renderer2D.ColorUtil.getColor(Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(35.0F * mainAlpha)));
      Color mainColorGlow35 = Renderer2D.ColorUtil.getColor(Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(56.0F * mainAlpha)));
      Color koronaColors = Renderer2D.ColorUtil.getColor(Renderer2D.ColorUtil.replAlpha(Color.ORANGE.getRGB(), (int)(50.0F * mainAlpha)));
      renderer2D.flush();
      StencilHelper.initStencil();
      RenderUtil.drawRoundedCorner(renderer2D, GuiScreen.x, GuiScreen.y + 33.7F, 104.34F, 209.105F, new Vector4f(0.0F, 0.0F, 0.0F, 0.0F), -1);
      renderer2D.flush();
      StencilHelper.bindReadStencilBuffer(1);
      renderer2D.rectOutline(GuiScreen.x, GuiScreen.y + 33.7F - 4.0F, 107.34F, 209.105F, 6.5F, outlineColor, 0.5F);
      renderer2D.rect(GuiScreen.x, GuiScreen.y + 33.7F - 4.0F, 107.34F, 209.105F, 6.5F, backGroundTwoColor);
      renderer2D.flush();
      StencilHelper.uninitStencilBuffer();
      float downY = 0.0F;

      for (Category category : GuiScreen.categories) {
         float yicons = 0.0F;
         category.anim33.update();
         category.anim33.run(category == selectedCategories ? 1.0 : 0.0, 1.0, Easings.QUART_OUT);
         renderer2D.rect(GuiScreen.x, GuiScreen.y + 43.365F + 0.5F + downY, 104.34F, 0.5F, ColorUtil.overCol(0, outlineColor, category.anim33.get()));
         renderer2D.rect(GuiScreen.x, GuiScreen.y + 43.365F + downY, 104.34F, 21.325F, ColorUtil.overCol(0, backGroundThreeColor, category.anim33.get()));
         renderer2D.rect(GuiScreen.x, GuiScreen.y + 43.365F + 21.325F + downY, 104.34F, 0.5F, ColorUtil.overCol(0, outlineColor, category.anim33.get()));
         renderer2D.rect(GuiScreen.x, GuiScreen.y + 43.365F + downY, 1.0F, 21.825F, ColorUtil.overCol(0, mainColor, category.anim33.get()));
         renderer2D.shadow(
            GuiScreen.x, GuiScreen.y + 43.365F + downY, 1.0F, 21.825F, 0.0F, 1.0F, 0.1F, ColorUtil.overCol(0, mainColorGlow.getRGB(), category.anim33.get())
         );
         if (category == Category.FunTime) {
            drawFunTimeLetters(renderer2D, downY, mainColor);
            float ftGlowX = funTimeIconCenterX(downY);
            float ftGlowY = funTimeIconCenterY(downY);
            renderer2D.shadow(
               ftGlowX,
               ftGlowY,
               0.1F,
               0.1F,
               8.0F,
               5.7F,
               0.1F,
               ColorUtil.overCol(0, mainColorGlow35.getRGB(), category.anim33.get())
            );
         } else {
            renderer2D.text(
               FontRegistry.ICONS,
               GuiScreen.x + CATEGORY_ICON_X,
               GuiScreen.y + CATEGORY_ICON_Y_OFFSET + yicons + downY + CATEGORY_ICON_BASELINE_OFFSET,
               CATEGORY_ICON_SIZE,
               category.getIcon(),
               mainColor
            );
            renderer2D.shadow(
               GuiScreen.x + CATEGORY_ICON_X + 3.5F,
               GuiScreen.y + CATEGORY_ICON_Y_OFFSET + downY + yicons + 3.5F,
               0.1F,
               0.1F,
               8.0F,
               5.7F,
               0.1F,
               ColorUtil.overCol(0, mainColorGlow35.getRGB(), category.anim33.get())
            );
         }
         renderer2D.text(
            FontRegistry.INTER_MEDIUM,
            GuiScreen.x + 21.995F,
            GuiScreen.y + 49.77F + downY + 7.0F + 0.2F,
            14.0F,
            category.getName(),
            ColorUtil.overCol(0, textColor, category.anim33.get())
         );
         if (category == Category.FunTime) {
            drawFunTimeLetters(renderer2D, downY, ColorUtil.overCol(mainColor40, 0, category.anim33.get()));
         } else {
            renderer2D.text(
               FontRegistry.ICONS,
               GuiScreen.x + CATEGORY_ICON_X,
               GuiScreen.y + CATEGORY_ICON_Y_OFFSET + yicons + downY + CATEGORY_ICON_BASELINE_OFFSET,
               CATEGORY_ICON_SIZE,
               category.getIcon(),
               ColorUtil.overCol(mainColor40, 0, category.anim33.get())
            );
         }
         renderer2D.text(
            FontRegistry.INTER_MEDIUM,
            GuiScreen.x + 21.995F,
            GuiScreen.y + 49.77F + downY + 7.0F + 0.2F,
            14.0F,
            category.getName(),
            ColorUtil.overCol(mainColor40, 0, category.anim33.get())
         );
         downY += 24.0F;
      }

      renderer2D.rect(
         GuiScreen.x + 9.145F,
         GuiScreen.y + 210.72F + 1.0F,
         19.27F,
         19.27F,
         15.0F,
         Renderer2D.ColorUtil.rgba(255, 255, 255, (int)(255.0F * GuiScreen.alphaPC.get()))
      );
      String userRank = "ZeroUser";
      if (Zero.get != null && Zero.get.visualLinkingClient != null) {
         userRank = Zero.get.visualLinkingClient.getDisplayRank();
      }
      renderer2D.text(FontRegistry.INTER_MEDIUM, GuiScreen.x + 34.925F, GuiScreen.y + 213.355F + 7.0F, 14.0F, userRank, textColor);
      renderer2D.text(FontRegistry.INTER_MEDIUM, GuiScreen.x + 34.925F, GuiScreen.y + 220.89F + 7.0F, 13.0F, "Free", textTwoColor40);
      float checkNameX = renderer2D.measureText(FontRegistry.INTER_MEDIUM, userRank, 14.0F).width;
      renderer2D.shadow(GuiScreen.x + checkNameX + 39.0F + 3.0F, GuiScreen.y + 214.99F + 1.5F + 1.0F, 0.1F, 0.1F, 6.0F, 4.1666665F, 0.1F, koronaColors.getRGB());
      renderer2D.text(
         FontRegistry.ICONS,
         GuiScreen.x + checkNameX + 39.0F,
         GuiScreen.y + 214.99F - 0.5F + 5.0F + 1.0F,
         12.0F,
         "i",
         Renderer2D.ColorUtil.rgba(255, 205, 0, (int)(255.0F * GuiScreen.alphaPC.get()))
      );
   }

   private static float funTimeLabelBaselineY(float downY) {
      return GuiScreen.y + 49.77F + downY + 7.0F + 0.2F;
   }

   private static float funTimeIconCenterX(float downY) {
      return GuiScreen.x + CATEGORY_ICON_X + CATEGORY_ICON_SIZE * 0.5F;
   }

   private static float funTimeIconCenterY(float downY) {
      float labelBaselineY = funTimeLabelBaselineY(downY);
      float labelRenderSize = 14.0F * 0.5F;
      return labelBaselineY + FontRegistry.centeredBaselineOffset(FontRegistry.INTER_MEDIUM, 'F', labelRenderSize);
   }

   private static void drawFunTimeLetters(Renderer2D renderer2D, float downY, int color) {
      float labelBaselineY = funTimeLabelBaselineY(downY);
      float labelRenderSize = 14.0F * 0.5F;
      float ftRenderSize = FUNTIME_TEXT_SIZE * 0.5F;
      float labelCenterY = labelBaselineY + FontRegistry.centeredBaselineOffset(FontRegistry.INTER_MEDIUM, 'F', labelRenderSize);
      float ftBaselineY = labelCenterY - FontRegistry.centeredBaselineOffset(FontRegistry.INTER_MEDIUM, 'F', ftRenderSize);
      renderer2D.text(
         FontRegistry.INTER_MEDIUM,
         funTimeIconCenterX(downY),
         ftBaselineY,
         FUNTIME_TEXT_SIZE,
         "FT",
         color,
         "c"
      );
   }
}
