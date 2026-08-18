package ru.zero.rpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Минимальный клиент Discord IPC (RPC v1).
 * <p>
 * Реализован вручную, потому что legacy-библиотека {@code club.minnced:discord-rpc:3.4.0}
 * не поддерживает поле {@code buttons} в activity — через неё кнопки создать невозможно.
 * Общение идёт по локальному сокету (Unix domain socket на Linux/macOS,
 * named pipe на Windows) в формате: [opcode int32 LE][length int32 LE][json utf-8].
 */
@Environment(EnvType.CLIENT)
public final class DiscordIpcClient implements AutoCloseable {
   private static final int OP_HANDSHAKE = 0;
   private static final int OP_FRAME = 1;
   private static final int OP_CLOSE = 2;
   private static final int MAX_PAYLOAD = 64 * 1024;

   private SocketChannel unixChannel;
   private RandomAccessFile windowsPipe;
   private boolean connected;

   public boolean isConnected() {
      return this.connected;
   }

   /**
    * Пытается подключиться к любому доступному слоту discord-ipc-0..9.
    */
   public boolean connect(String applicationId) {
      if (this.connected) {
         return true;
      }

      boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");

      for (int i = 0; i < 10; i++) {
         try {
            if (windows) {
               File pipe = new File("\\\\.\\pipe\\discord-ipc-" + i);
               RandomAccessFile handle = new RandomAccessFile(pipe, "rw");
               this.windowsPipe = handle;
            } else {
               Path socketPath = resolveUnixSocket(i);
               if (socketPath == null) {
                  continue;
               }

               SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
               channel.connect(UnixDomainSocketAddress.of(socketPath));
               this.unixChannel = channel;
            }

            JsonObject handshake = new JsonObject();
            handshake.addProperty("v", 1);
            handshake.addProperty("client_id", applicationId);
            this.write(OP_HANDSHAKE, handshake.toString());
            String response = this.read();
            if (response == null) {
               this.closeQuietly();
               continue;
            }

            this.connected = true;
            return true;
         } catch (Throwable ignored) {
            this.closeQuietly();
         }
      }

      return false;
   }

   private static Path resolveUnixSocket(int index) {
      String[] envVars = { "XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP" };
      for (String env : envVars) {
         String base = System.getenv(env);
         if (base == null || base.isBlank()) {
            continue;
         }

         File candidate = new File(base, "discord-ipc-" + index);
         if (candidate.exists()) {
            return candidate.toPath();
         }
      }

      File fallback = new File("/tmp", "discord-ipc-" + index);
      return fallback.exists() ? fallback.toPath() : null;
   }

   /**
    * Отправляет activity с деталями и кнопками.
    */
   public boolean sendActivity(
         String details,
         String state,
         long startTimestamp,
         String largeImageKey,
         String largeImageText,
         String button1Label,
         String button1Url,
         String button2Label,
         String button2Url
   ) {
      if (!this.connected) {
         return false;
      }

      JsonObject activity = new JsonObject();
      if (details != null && !details.isBlank()) {
         activity.addProperty("details", trim(details, 128));
      }
      if (state != null && !state.isBlank()) {
         activity.addProperty("state", trim(state, 128));
      }

      if (startTimestamp > 0L) {
         JsonObject timestamps = new JsonObject();
         timestamps.addProperty("start", startTimestamp);
         activity.add("timestamps", timestamps);
      }

      if (largeImageKey != null && !largeImageKey.isBlank()) {
         JsonObject assets = new JsonObject();
         assets.addProperty("large_image", largeImageKey);
         if (largeImageText != null && !largeImageText.isBlank()) {
            assets.addProperty("large_text", trim(largeImageText, 128));
         }
         activity.add("assets", assets);
      }

      JsonArray buttons = new JsonArray();
      addButton(buttons, button1Label, button1Url);
      addButton(buttons, button2Label, button2Url);
      if (!buttons.isEmpty()) {
         activity.add("buttons", buttons);
      }

      JsonObject args = new JsonObject();
      args.addProperty("pid", currentPid());
      args.add("activity", activity);

      JsonObject frame = new JsonObject();
      frame.addProperty("cmd", "SET_ACTIVITY");
      frame.add("args", args);
      frame.addProperty("nonce", UUID.randomUUID().toString());

      try {
         this.write(OP_FRAME, frame.toString());
         return true;
      } catch (Throwable t) {
         this.connected = false;
         return false;
      }
   }

   private static void addButton(JsonArray target, String label, String url) {
      if (label == null || label.isBlank() || url == null || url.isBlank()) {
         return;
      }

      JsonObject button = new JsonObject();
      button.addProperty("label", trim(label, 31));
      button.addProperty("url", url);
      target.add(button);
   }

   private static String trim(String value, int max) {
      return value.length() <= max ? value : value.substring(0, max);
   }

   private static long currentPid() {
      try {
         return ProcessHandle.current().pid();
      } catch (Throwable ignored) {
         return 0L;
      }
   }

   private void write(int opcode, String payload) throws IOException {
      byte[] data = payload.getBytes(StandardCharsets.UTF_8);
      ByteBuffer buffer = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
      buffer.putInt(opcode);
      buffer.putInt(data.length);
      buffer.put(data);
      buffer.flip();

      if (this.unixChannel != null) {
         while (buffer.hasRemaining()) {
            this.unixChannel.write(buffer);
         }
      } else if (this.windowsPipe != null) {
         this.windowsPipe.write(buffer.array());
      } else {
         throw new IOException("No IPC transport available");
      }
   }

   private String read() throws IOException {
      byte[] header = this.readFully(8);
      if (header == null) {
         return null;
      }

      ByteBuffer headerBuffer = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
      headerBuffer.getInt();
      int length = headerBuffer.getInt();
      if (length <= 0 || length > MAX_PAYLOAD) {
         return null;
      }

      byte[] body = this.readFully(length);
      return body == null ? null : new String(body, StandardCharsets.UTF_8);
   }

   private byte[] readFully(int length) throws IOException {
      byte[] result = new byte[length];

      if (this.unixChannel != null) {
         ByteBuffer buffer = ByteBuffer.wrap(result);
         while (buffer.hasRemaining()) {
            int read = this.unixChannel.read(buffer);
            if (read < 0) {
               return null;
            }
         }
         return result;
      }

      if (this.windowsPipe != null) {
         int offset = 0;
         while (offset < length) {
            int read = this.windowsPipe.read(result, offset, length - offset);
            if (read < 0) {
               return null;
            }
            offset += read;
         }
         return result;
      }

      return null;
   }

   @Override
   public void close() {
      if (this.connected) {
         try {
            this.write(OP_CLOSE, "{}");
         } catch (Throwable ignored) {
         }
      }

      this.closeQuietly();
   }

   private void closeQuietly() {
      this.connected = false;

      if (this.unixChannel != null) {
         try {
            this.unixChannel.close();
         } catch (Throwable ignored) {
         }
         this.unixChannel = null;
      }

      if (this.windowsPipe != null) {
         try {
            this.windowsPipe.close();
         } catch (Throwable ignored) {
         }
         this.windowsPipe = null;
      }
   }
}
