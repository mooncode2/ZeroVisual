package ru.zero.rpc.callbacks;

import com.sun.jna.Callback;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.rpc.DiscordUser;

@Environment(EnvType.CLIENT)
public interface ReadyCallback extends Callback {
   void apply(DiscordUser var1);
}
