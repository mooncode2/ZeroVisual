package ru.zero.linking;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;

@Environment(EnvType.CLIENT)
public final class VisualLinkingClient {
   private static final String TARGET_SERVER_IP = "194.164.96.153";
   private static final String API_URL_TEMPLATE = "http://194.164.96.153:8114/visuals/zero?nick=%s";
   private static final long RETRY_COOLDOWN_MS = 15000L;

   public enum PrimeState {
      UNKNOWN,
      PRIME,
      UNPRIME
   }

   private volatile PrimeState state = PrimeState.UNKNOWN;
   private volatile String displayRank = "ZeroUser";
   private volatile long lastRequestAt = 0L;
   private volatile boolean requestInFlight = false;
   private String lastCheckedNick = "";
   private String lastCheckedHost = "";

   @EventInit
   public void onClientTick(ClientTickEvent event) {
      try {
         MinecraftClient mc = event.client();
         if (mc == null || mc.player == null) {
            this.resetSession();
            return;
         }

         if (!isTargetServer(mc)) {
            this.resetSession();
            return;
         }

         String host = resolveConnectedHost(mc);
         if (host == null || host.isBlank()) {
            host = TARGET_SERVER_IP;
         }

         String nick = mc.player.getName().getString();
         long now = System.currentTimeMillis();
         boolean identityChanged = !nick.equals(this.lastCheckedNick) || !host.equals(this.lastCheckedHost);
         boolean canRetry = now - this.lastRequestAt >= RETRY_COOLDOWN_MS;
         if ((identityChanged || this.state == PrimeState.UNKNOWN && canRetry) && !this.requestInFlight) {
            this.lastCheckedNick = nick;
            this.lastCheckedHost = host;
            this.lastRequestAt = now;
            System.out.println("[VisualLinkingClient] Sending request for nick=" + nick + " to " + API_URL_TEMPLATE.replace("%s", "<nick>"));
            requestPrimeStatusAsync(nick);
         }
      } catch (Exception error) {
         System.out.println("[VisualLinkingClient] Tick handler error: " + error.getMessage());
      }
   }

   public String getDisplayRank() {
      return this.displayRank;
   }

   private void resetSession() {
      this.lastCheckedNick = "";
      this.lastCheckedHost = "";
      this.requestInFlight = false;
      this.state = PrimeState.UNKNOWN;
      this.displayRank = "ZeroUser";
   }

   private void requestPrimeStatusAsync(String nick) {
      this.requestInFlight = true;
      Thread thread = new Thread(() -> {
         boolean prime = false;
         try {
            String encodedNick = URLEncoder.encode(nick, StandardCharsets.UTF_8);
            String requestUrl = String.format(API_URL_TEMPLATE, encodedNick);
            HttpURLConnection connection = (HttpURLConnection)URI.create(requestUrl).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(3500);
            connection.setRequestProperty("Accept", "text/plain, application/json");
            int code = connection.getResponseCode();
            System.out.println("[VisualLinkingClient] Response code: " + code);
            if (code >= 200 && code < 300) {
               try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                  String body = reader.readLine();
                  if (body != null) {
                     String normalized = body.trim().toLowerCase();
                     System.out.println("[VisualLinkingClient] Response body: " + body);
                     prime = normalized.contains("true") || normalized.contains("\"prime\":true");
                  }
               }
            }
         } catch (Exception error) {
            System.out.println("[VisualLinkingClient] Request failed: " + error.getMessage());
         } finally {
            this.state = prime ? PrimeState.PRIME : PrimeState.UNPRIME;
            this.displayRank = prime ? "Prime" : "Unprime";
            this.requestInFlight = false;
         }
      }, "VisualLinkingClient");
      thread.setDaemon(true);
      thread.start();
   }

   private static String resolveConnectedHost(MinecraftClient mc) {
      if (mc.isConnectedToLocalServer()) {
         return "localhost";
      }
      if (mc.getCurrentServerEntry() != null && mc.getCurrentServerEntry().address != null) {
         return normalizeHost(mc.getCurrentServerEntry().address);
      }
      if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
         SocketAddress address = mc.getNetworkHandler().getConnection().getAddress();
         if (address instanceof InetSocketAddress inetSocketAddress) {
            String host = inetSocketAddress.getHostString();
            if (host != null && !host.isBlank()) {
               return normalizeHost(host);
            }
         } else if (address != null) {
            return normalizeHost(address.toString());
         }
      }
      return null;
   }

   private static boolean isTargetServer(MinecraftClient mc) {
      if (mc == null || mc.isConnectedToLocalServer()) {
         return false;
      }

      String entryAddress = mc.getCurrentServerEntry() != null ? mc.getCurrentServerEntry().address : null;
      if (entryAddress != null && normalizeHost(entryAddress).equals(TARGET_SERVER_IP)) {
         return true;
      }

      if (mc.getNetworkHandler() != null && mc.getNetworkHandler().getConnection() != null) {
         SocketAddress address = mc.getNetworkHandler().getConnection().getAddress();
         if (address instanceof InetSocketAddress inetSocketAddress) {
            String hostString = inetSocketAddress.getHostString();
            if (TARGET_SERVER_IP.equals(normalizeHost(hostString))) {
               return true;
            }
            InetAddress inetAddress = inetSocketAddress.getAddress();
            if (inetAddress != null && TARGET_SERVER_IP.equals(inetAddress.getHostAddress())) {
               return true;
            }
         }
      }
      return false;
   }

   private static String normalizeHost(String raw) {
      if (raw == null) return null;
      String value = raw.trim();
      if (value.startsWith("/")) {
         value = value.substring(1);
      }
      int colonIndex = value.lastIndexOf(':');
      if (colonIndex > 0) {
         value = value.substring(0, colonIndex);
      }
      return value;
   }
}

