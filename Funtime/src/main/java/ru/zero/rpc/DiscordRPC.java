package ru.zero.rpc;

import com.sun.jna.Library;
import com.sun.jna.Native;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface DiscordRPC extends Library {
   void Discord_UpdateHandlers(DiscordEventHandlers var1);

   void Discord_UpdatePresence(DiscordRichPresence var1);

   void Discord_Respond(String var1, int var2);

   void Discord_Register(String var1, String var2);

   void Discord_Shutdown();

   void Discord_UpdateConnection();

   void Discord_RegisterSteamGame(String var1, String var2);

   void Discord_RunCallbacks();

   void Discord_Initialize(String var1, DiscordEventHandlers var2, boolean var3, String var4);

   void Discord_ClearPresence();

   @Environment(EnvType.CLIENT)
   public static class Loader {
      private static DiscordRPC instance;
      private static boolean loaded = false;

      public static DiscordRPC getInstance() {
         if (!loaded) {
            loaded = true;

            try {
               instance = (DiscordRPC)Native.loadLibrary("discord-rpc", DiscordRPC.class);
            } catch (UnsatisfiedLinkError var1) {
               System.err.println("[ZeroDLC] Discord RPC библиотека недоступна: " + var1.getMessage());
               instance = null;
            }
         }

         return instance;
      }

      public static boolean isAvailable() {
         return getInstance() != null;
      }
   }
}
