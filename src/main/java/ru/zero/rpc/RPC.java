package ru.zero.rpc;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.client.Lang;
import ru.zero.util.other.IMinecraft;

/**
 * Discord Rich Presence клиента.
 * <p>
 * Использует собственный {@link DiscordIpcClient}, потому что legacy-библиотека
 * {@code club.minnced:discord-rpc} не поддерживает кнопки (поле {@code buttons}).
 * Тексты берутся из словарей через {@link Lang}, поэтому язык статуса совпадает
 * с языком клиента. Название кнопки Modrinth не переводится.
 */
@Environment(EnvType.CLIENT)
public class RPC implements IMinecraft {
   private static final String APPLICATION_ID = "1522909252971135156";
   private static final String BUILD_TEXT = "Build 3.0";
   private static final String MODRINTH_LABEL = "Modrinth";
   private static final String MODRINTH_URL = "https://modrinth.com/mod/zerovisuals";
   private static final String TELEGRAM_KEY = "Telegram";
   private static final String TELEGRAM_URL = "https://t.me/zerovisualsoff";
   private static final String LARGE_IMAGE_KEY = "h";
   private static final long RETRY_DELAY_MS = 15_000L;
   private static final long REFRESH_INTERVAL_MS = 20_000L;

   private DiscordIpcClient client;
   private Thread worker;
   private volatile boolean running;
   private volatile String lastPushedSignature;

   public static boolean isDiscordRPCAvailable() {
      return true;
   }

   public void startRpc() {
      if (this.running) {
         return;
      }

      this.running = true;
      this.lastPushedSignature = null;
      this.worker = new Thread(this::runLoop, "Zero-DiscordRPC");
      this.worker.setDaemon(true);
      this.worker.start();
   }

   private void runLoop() {
      long lastRefresh = 0L;

      while (this.running && !Thread.currentThread().isInterrupted()) {
         try {
            if (this.client == null || !this.client.isConnected()) {
               DiscordIpcClient fresh = new DiscordIpcClient();
               if (!fresh.connect(APPLICATION_ID)) {
                  fresh.close();
                  Thread.sleep(RETRY_DELAY_MS);
                  continue;
               }

               this.client = fresh;
               this.lastPushedSignature = null;
            }

            long now = System.currentTimeMillis();
            String signature = buildSignature();
            if (!signature.equals(this.lastPushedSignature) || now - lastRefresh >= REFRESH_INTERVAL_MS) {
               if (this.pushPresence()) {
                  this.lastPushedSignature = signature;
                  lastRefresh = now;
               }
            }

            Thread.sleep(2000L);
         } catch (InterruptedException e) {
            break;
         } catch (Throwable t) {
            this.closeClient();

            try {
               Thread.sleep(RETRY_DELAY_MS);
            } catch (InterruptedException ie) {
               break;
            }
         }
      }

      this.closeClient();
   }

   /**
    * Строка-отпечаток текущего состояния: язык + текст, чтобы не спамить обновлениями.
    */
   private static String buildSignature() {
      return Lang.current() + "|" + resolveState();
   }

   private boolean pushPresence() {
      DiscordIpcClient active = this.client;
      if (active == null || !active.isConnected()) {
         return false;
      }

      return active.sendActivity(
            BUILD_TEXT,
            resolveState(),
            System.currentTimeMillis() / 1000L,
            LARGE_IMAGE_KEY,
            resolveImageText(),
            MODRINTH_LABEL,
            MODRINTH_URL,
            Lang.t(TELEGRAM_KEY),
            TELEGRAM_URL);
   }

   private static String resolveState() {
      try {
         if (mc != null && mc.world != null && mc.player != null) {
            return Lang.t("В игре");
         }
      } catch (Throwable ignored) {
      }

      return Lang.t("В меню");
   }

   private static String resolveImageText() {
      return Lang.t("ZeroVisuals") + " " + BUILD_TEXT;
   }

   /**
    * Принудительно обновляет статус (например, после смены языка в GUI).
    */
   public void refresh() {
      this.lastPushedSignature = null;
   }

   public void updatePresence(String state) {
      this.refresh();
   }

   public void stopRpc() {
      this.running = false;

      Thread active = this.worker;
      if (active != null && active.isAlive()) {
         active.interrupt();
         try {
            active.join(1000L);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }

      this.worker = null;
      this.closeClient();
   }

   private void closeClient() {
      DiscordIpcClient active = this.client;
      this.client = null;
      if (active != null) {
         active.close();
      }
   }

   public boolean isInitialized() {
      DiscordIpcClient active = this.client;
      return active != null && active.isConnected();
   }

   public boolean isRunning() {
      return this.running;
   }
}
