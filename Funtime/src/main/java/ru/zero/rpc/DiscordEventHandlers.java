package ru.zero.rpc;

import com.sun.jna.Structure;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.rpc.callbacks.DisconnectedCallback;
import ru.zero.rpc.callbacks.ErroredCallback;
import ru.zero.rpc.callbacks.JoinGameCallback;
import ru.zero.rpc.callbacks.JoinRequestCallback;
import ru.zero.rpc.callbacks.ReadyCallback;
import ru.zero.rpc.callbacks.SpectateGameCallback;

@Environment(EnvType.CLIENT)
public class DiscordEventHandlers extends Structure {
   public DisconnectedCallback disconnected;
   public JoinRequestCallback joinRequest;
   public SpectateGameCallback spectateGame;
   public ReadyCallback ready;
   public ErroredCallback errored;
   public JoinGameCallback joinGame;

   protected List<String> getFieldOrder() {
      return Arrays.asList("ready", "disconnected", "errored", "joinGame", "spectateGame", "joinRequest");
   }
}
