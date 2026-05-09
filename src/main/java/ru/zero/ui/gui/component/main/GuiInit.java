package ru.zero.ui.gui.component.main;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.ui.gui.GuiScreen;
import ru.zero.util.render.math.animation.anim.util.Easings;

@Environment(EnvType.CLIENT)
public class GuiInit extends GuiScreen {
   public static void init() {
      GuiScreen.animation = GuiScreen.animation.animate(1.0, 0.2F);
      GuiScreen.alphaPC.set(0.0);
      GuiScreen.alphaPC.run(1.0, 0.4F, Easings.CIRC_OUT);
      GuiScreen.exit = false;
      GuiScreen.mainAnimation.reset();
      GuiScreen.alpha.run(1.0);
   }
}
