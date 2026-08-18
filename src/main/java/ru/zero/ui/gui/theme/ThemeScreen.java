package ru.zero.ui.gui.theme;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import org.joml.Vector4f;
import ru.zero.Zero;
import ru.zero.module.api.Theme;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.component.render.RenderUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.MathHelper;
import ru.zero.util.render.math.ScaleHelper;
import ru.zero.util.render.math.animation.Direction;

@Environment(EnvType.CLIENT)
public class ThemeScreen extends GuiScreen {
   public static void renderTheme(Renderer2D renderer2D, DrawContext drawContext, int mouseX1, int mouseY1) {
      float mainAnim = (float)GuiScreen.alphaPC.getValue();
      int color255 = (int)(255.0F * mainAnim);
      int color100 = (int)(100.0F * mainAnim);
      int color20 = (int)(90.0F * mainAnim);
      float centerX = mc.getWindow().getScaledWidth() / 2.0F;
      float y = mc.getWindow().getScaledHeight() - 16 + (15.0F - 15.0F * mainAnim);
      int count = GuiScreen.themes.length;
      float offset = 18.0F;
      float totalWidth = count * offset;
      float startX = centerX - totalWidth / 2.0F;
      int outlineColor = Renderer2D.ColorUtil.replAlpha(
         Renderer2D.ColorUtil.multDark(Renderer2D.ColorUtil.getOutLineColor(1, 1), 1.0F), (int)(15.299999F * mainAnim)
      );
      int backGroundOneColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), (int)(178.0F * mainAnim));
      float barX = startX - 9.0F + 1.0F;
      float barY = y - 5.0F;
      float barW = totalWidth + 9.0F - 1.0F;
      float barH = 21.25F;
      if (ru.zero.util.render.glass.LiquidGlassRenderer.isEnabled()) {
         ru.zero.util.render.glass.LiquidGlassRenderer.drawGlassPanel(renderer2D, barX, barY, barW, barH, 6.5F, outlineColor, backGroundOneColor);
      } else {
         if (!GuiScreen.clientBlurSetting.get()) {
            renderer2D.prepareBlurRegion(barX, barY, barW, barH, 23.0F);
            renderer2D.blurRegion(barX, barY, barW, barH, 6.5F, mainAnim);
         }

         RenderUtil.drawRoundedCorner(
            renderer2D, barX, barY, barW, barH, new Vector4f(6.5F, 6.5F, 0.0F, 0.0F), backGroundOneColor
         );
         renderer2D.rectOutline(barX, barY, barW, 25.0F, 6.5F, outlineColor, 0.5F);
      }
      float x = startX;

      for (Theme theme : GuiScreen.themes) {
         theme.animation.setDirection(theme == GuiScreen.selectedTheme ? Direction.FORWARDS : Direction.BACKWARDS);
         renderer2D.shadow(
            x + 4.5F,
            y + 4.76F + 0.5F,
            0.1F,
            0.1F,
            10.0F,
            6.0F,
            0.1F,
            Renderer2D.ColorUtil.setAlpha(theme.getMain(), (int)(color20 * theme.animation.getOutput())).getRGB()
         );
         renderer2D.rect(x, y + 0.76F, 9.25F, 9.25F, 10.0F, Renderer2D.ColorUtil.setAlpha(theme.getMain(), color255).getRGB());
         x += offset;
      }
   }

   public static void mouseClickedTheme(double mouseX1, double mouseY1, int button) {
      int mouseX = (int)ScaleHelper.calc((float)mouseX1, (float)mouseY1)[0];
      int mouseY = (int)ScaleHelper.calc((float)mouseX1, (float)mouseY1)[1];
      float centerX = mc.getWindow().getScaledWidth() / 2.0F;
      float y = mc.getWindow().getScaledHeight() - 16;
      int count = GuiScreen.themes.length;
      float offset = 18.0F;
      float totalWidth = count * offset;
      float startX = centerX - totalWidth / 2.0F;
      float x = startX;

      for (Theme theme : GuiScreen.themes) {
         if (MathHelper.isHovered(mouseX, mouseY, x, y, 16.0F, 16.0F)) {
            GuiScreen.animation14.reset();
            GuiScreen.preSelectedTheme = GuiScreen.selectedTheme;
            GuiScreen.selectedTheme = theme;
            Zero.get.guiManager.setGuiTheme(theme);
         }

         x += offset;
      }
   }
}
