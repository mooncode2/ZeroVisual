package ru.zero.rpc.callbacks;

import com.sun.jna.Callback;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface JoinGameCallback extends Callback {
   void apply(String var1);
}
