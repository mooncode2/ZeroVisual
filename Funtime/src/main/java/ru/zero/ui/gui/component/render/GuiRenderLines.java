package ru.zero.ui.gui.component.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.util.math.MatrixStack;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.util.render.core.Renderer2D;

@Environment(EnvType.CLIENT)
public class GuiRenderLines extends GuiScreen {
   public static void renderLines(Renderer2D renderer2D, MatrixStack pose, float mainAlpha, boolean mapMode) {
      int outlineColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(20.4F * mainAlpha));
      int outlineColor2 = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), (int)(16.0F * mainAlpha));
      renderer2D.rect(GuiScreen.x, GuiScreen.y + 33.7F, 366.475F, 0.7F, outlineColor);
      if (!mapMode) {
         renderer2D.rect(GuiScreen.x + 104.34F, GuiScreen.y, 0.7F, 238.805F, outlineColor2);
         renderer2D.rect(GuiScreen.x, GuiScreen.y + 203.19F, 104.34F, 0.7F, outlineColor2);
      }
   }
}
