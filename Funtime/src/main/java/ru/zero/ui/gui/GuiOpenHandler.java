package ru.zero.ui.gui;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.event.EventInit;
import ru.zero.event.input.KeyInputEvent;

@Environment(EnvType.CLIENT)
public class GuiOpenHandler {
   @EventInit
   public void onKeyInput(KeyInputEvent event) {
   }
}
