package ru.zero.rpc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public class RPC implements IMinecraft {
   public static DiscordRichPresence presence = new DiscordRichPresence();
   public static boolean started;
   private static Thread thread;

   public void stopRpc() {
      if (thread != null) {
         thread.interrupt();
         thread = null;
      }

      if (started && DiscordRPC.Loader.isAvailable()) {
         DiscordRPC.Loader.getInstance().Discord_Shutdown();
      }

      started = false;
   }

   public void startRpc() {
      if (DiscordRPC.Loader.isAvailable()) {
         DiscordRPC rpc = DiscordRPC.Loader.getInstance();
         if (!started) {
            started = true;
            DiscordEventHandlers handlers = new DiscordEventHandlers();
            rpc.Discord_Initialize("1396524858464145521", handlers, true, "");
            presence.startTimestamp = System.currentTimeMillis() / 1000L;
            presence.largeImageText = "Zero DLC - 1.21.8";
            rpc.Discord_UpdatePresence(presence);
            thread = new Thread(() -> {
               while (!Thread.currentThread().isInterrupted()) {
                  rpc.Discord_RunCallbacks();
                  presence.details = "Build 1.0.0 [Dev]";
                  presence.state = "Update to soon";
                  presence.button_label_1 = "Telegram";
                  presence.button_url_1 = "";
                  presence.button_label_2 = "";
                  presence.button_url_2 = "";
                  presence.largeImageKey = "h";
                  rpc.Discord_UpdatePresence(presence);

                  try {
                     Thread.sleep(2000L);
                  } catch (InterruptedException var2x) {
                  }
               }
            }, "TH-RPC-Handler");
            thread.start();
         }
      }
   }
}
