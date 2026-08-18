package ru.zero.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import ru.zero.compat.LunarCompat;
import ru.zero.event.render.RenderEvent;
import ru.zero.ui.gui.GuiClient;
import ru.zero.ui.gui.component.render.GuiRender;
import ru.zero.util.other.MouseUtil;

@Environment(EnvType.CLIENT)
public final class RenderHandler {
   private static volatile boolean registered = false;

   private RenderHandler() {
   }

   public static void register() {
      if (!registered) {
         registered = true;
         EventManager.register(new Object() {
             @EventInit
             public void onRender(RenderEvent event) {
                MinecraftClient client = event.client();
                if (client != null) {
                   if (LunarCompat.isLunarClient()) {
                      return;
                   }
                   if (client.currentScreen instanceof GuiClient) {
                      MouseUtil.MousePosition mousePos = MouseUtil.getMousePos();
                      DrawContext drawContext = null;
                      GuiRender.render(event.renderer(), drawContext, mousePos.x(), mousePos.y(), client.getRenderTickCounter().getDynamicDeltaTicks());
                   }
                }
                ru.zero.util.render.glass.zero.ZeroGlassApi.end();
             }
         });
      }
   }
}