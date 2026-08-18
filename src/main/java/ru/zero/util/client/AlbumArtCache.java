package ru.zero.util.client;

import java.util.concurrent.ConcurrentHashMap;
import ru.zero.util.render.texture.TextureLoader;

/**
 * Bridges background-fetched album art bytes to a GL texture uploaded on the
 * render thread. The worker thread stores raw PNG bytes; the HUD, while
 * rendering, uploads them via {@link TextureLoader} and caches the texture id.
 */
final class AlbumArtCache {
   private static final ConcurrentHashMap<String, Entry> ENTRIES = new ConcurrentHashMap<>();

   private AlbumArtCache() {
   }

   static void store(String key, Object job) {
      if (key == null) {
         return;
      }
      try {
         java.lang.reflect.Field f = job.getClass().getDeclaredField("resultBytes");
         f.setAccessible(true);
         byte[] bytes = (byte[]) f.get(job);
         if (bytes != null && bytes.length > 0) {
            Entry e = ENTRIES.computeIfAbsent(key, k -> new Entry());
            e.pendingBytes = bytes;
         }
      } catch (Throwable ignored) {
      }
   }

   static int textureFor(String key) {
      if (key == null) {
         return 0;
      }
      Entry e = ENTRIES.get(key);
      if (e == null) {
         return 0;
      }
      if (e.textureId != 0 && !e.dirty) {
         return e.textureId;
      }
      if (e.pendingBytes != null) {
         try {
            int id = TextureLoader.loadFromPngBytes(e.pendingBytes);
            if (id > 0) {
               e.textureId = id;
               e.dirty = false;
               return id;
            }
         } catch (Throwable ignored) {
         }
         e.pendingBytes = null;
      }
      return 0;
   }

   static void clear() {
      ENTRIES.clear();
   }

   private static final class Entry {
      volatile byte[] pendingBytes;
      volatile int textureId;
      volatile boolean dirty;
   }
}