package ru.zero.ui.gui.widget.settings;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;

@FunctionalInterface
@Environment(EnvType.CLIENT)
public interface PopupOpenContext {
   void openForSetting(Module var1, Setting var2, double var3, double var5, Object var7);
}
