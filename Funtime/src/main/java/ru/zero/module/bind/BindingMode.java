package ru.zero.module.bind;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum BindingMode {
   TOGGLE,
   HOLD;
}
