package ru.zero.ui.gui.component.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import ru.zero.module.api.Module;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.ui.gui.map.GuiServerMapPanel;
import ru.zero.ui.gui.theme.ThemeScreen;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.math.ScaleHelper;

@Environment(EnvType.CLIENT)
public class GuiRender extends GuiScreen {
   public static void render(Renderer2D renderer2D, DrawContext p_281549_, int p_281550_, int p_282878_, float p_282465_) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.getWindow() != null) {
         int viewportWidth = client.getWindow().getFramebufferWidth();
         int viewportHeight = client.getWindow().getFramebufferHeight();
         if (viewportWidth > 0 && viewportHeight > 0) {
            int mouseX = (int)(p_281550_ / ScaleHelper.size);
            int mouseY = (int)(p_282878_ / ScaleHelper.size);
            GuiScreen.currentMouseX = mouseX;
            GuiScreen.currentMouseY = mouseY;
            GuiScreen.alphaPC.update();
            GuiScreen.settingPC.update();
            GuiScreen.alphaPC2.update();
            GuiScreen.alphaPC3.update();
            GuiScreen.refreshCategoriesAndModules();
            if (GuiScreen.modules != null) {
               for (Module module : GuiScreen.modules) {
                  GuiScreen.getModuleSettingsAnimation(module).update();
                  GuiScreen.getModuleSettingsAlphaAnimation(module).update();
                  GuiScreen.getModuleBindAnimation(module).update();
               }
            }

            GuiScreen.alpha.run(1.0);
            float mainAlpha = GuiScreen.alphaPC.get();
            if (!(mainAlpha <= 0.001F)) {
               float scaledWidth = client.getWindow().getScaledWidth();
               float scaledHeight = client.getWindow().getScaledHeight();
               GuiScreen.x = scaledWidth / 2.0F - GuiScreen.width / 2.0F;
               GuiScreen.y = scaledHeight / 2.0F - GuiScreen.height / 2.0F - (80.0F - 80.0F * mainAlpha);
               MatrixStack pose = new MatrixStack();
               float scale = (float)client.getWindow().getFramebufferWidth() / client.getWindow().getScaledWidth();
               renderer2D.pushScale(scale);

               try {
                  renderer2D.rect(0.0F, 0.0F, scaledWidth, scaledHeight, Renderer2D.ColorUtil.rgba(0, 0, 0, (int)(140.0F * mainAlpha)));
                  GuiRenderBackground.renderBackground(renderer2D, pose, mainAlpha);
                  GuiRenderUpPanel.renderUpPanel(renderer2D, pose, mainAlpha);
                  if (GuiScreen.serverMapOpen) {
                     GuiRenderLines.renderLines(renderer2D, pose, mainAlpha, true);
                     GuiServerMapPanel.renderPanel(renderer2D, pose, mainAlpha);
                  } else {
                     GuiRenderLeftPanel.renderLeftPanel(renderer2D, pose, mainAlpha);
                     GuiRenderLines.renderLines(renderer2D, pose, mainAlpha, false);
                     GuiRenderMain.renderMain(renderer2D, pose, mouseX, mouseY, mainAlpha * categoryAnimation.getOutput());
                  }
                  ThemeScreen.renderTheme(renderer2D, p_281549_, mouseX, mouseY);
               } finally {
                  renderer2D.popTransform();
                  renderer2D.popTransform();
               }
            }
         }
      }
   }
}
