package ru.zero.rpc.callbacks;

import com.sun.jna.Callback;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface ErroredCallback extends Callback {
   void apply(int var1, String var2);
}
