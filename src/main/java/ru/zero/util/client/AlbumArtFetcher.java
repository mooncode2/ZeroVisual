package ru.zero.util.client;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Fetches album art via the public iTunes Search API by track title + artist.
 * Universal: works for any player (Yandex Music, Spotify, Apple Music, ...).
 * Network calls run on a background worker thread; the render thread only
 * reads the cached bytes and uploads them to a GL texture.
 */
final class AlbumArtFetcher {
   private static final long FETCH_COOLDOWN_MS = 5000L;
   private static final ConcurrentHashMap<String, Long> failedKeys = new ConcurrentHashMap<>();
   private static final AtomicReference<Job> pending = new AtomicReference<>();
   private static volatile Thread worker;
   private static volatile boolean stopped;

   private AlbumArtFetcher() {
   }

   static void request(String key, String title, String artist) {
      if (stopped || key == null || key.isEmpty()) {
         return;
      }
      Long lastFail = failedKeys.get(key);
      if (lastFail != null && System.currentTimeMillis() - lastFail < FETCH_COOLDOWN_MS * 12L) {
         return;
      }
      Job j = new Job(key, title, artist);
      pending.set(j);
      ensureWorker();
   }

   private static void ensureWorker() {
      if (worker != null && worker.isAlive()) {
         return;
      }
      synchronized (AlbumArtFetcher.class) {
         if (worker == null || !worker.isAlive()) {
            worker = new Thread(AlbumArtFetcher::runWorker, "Zero-AlbumArt");
            worker.setDaemon(true);
            worker.start();
         }
      }
   }

   private static void runWorker() {
      while (!stopped) {
         Job j = pending.getAndSet(null);
         if (j == null) {
            try {
               Thread.sleep(200L);
            } catch (InterruptedException e) {
               return;
            }
            continue;
         }
         byte[] bytes = fetch(j);
         if (bytes != null) {
            j.resultBytes = bytes;
            AlbumArtCache.store(j.key, j);
         } else {
            failedKeys.put(j.key, System.currentTimeMillis());
         }
      }
   }

   private static byte[] fetch(Job j) {
      try {
         String term = buildTerm(j.title, j.artist);
         if (term.isEmpty()) {
            return null;
         }
         String u = "https://itunes.apple.com/search?term=" + URLEncoder.encode(term, StandardCharsets.UTF_8)
               + "&entity=song&limit=1";
         byte[] json = httpGet(u, 4096);
         if (json == null) {
            return null;
         }
         String artUrl = extractArtworkUrl(new String(json, StandardCharsets.UTF_8));
         if (artUrl == null || artUrl.isEmpty()) {
            return null;
         }
         artUrl = artUrl.replace("100x100bb", "256x256bb");
         return httpGet(artUrl, 262144);
      } catch (Throwable ignored) {
      }
      return null;
   }

   private static String buildTerm(String title, String artist) {
      StringBuilder sb = new StringBuilder();
      if (title != null && !title.isEmpty()) {
         sb.append(title);
      }
      if (artist != null && !artist.isEmpty()) {
         if (sb.length() > 0) {
            sb.append(' ');
         }
         sb.append(artist);
      }
      return sb.toString().replaceAll("[^a-zA-Z0-9 ]", " ").trim().replaceAll("\\s+", "+");
   }

   private static byte[] httpGet(String url, int maxBytes) {
      HttpURLConnection conn = null;
      try {
         conn = (HttpURLConnection) new URL(url).openConnection();
         conn.setConnectTimeout(4000);
         conn.setReadTimeout(4000);
         conn.setRequestProperty("User-Agent", "Zero/1.0");
         try (InputStream in = conn.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
               out.write(buf, 0, n);
               if (out.size() > maxBytes * 2) {
                  return null;
               }
            }
            return out.toByteArray();
         }
      } catch (Throwable ignored) {
      } finally {
         if (conn != null) {
            try {
               conn.disconnect();
            } catch (Throwable ignored) {
            }
         }
      }
      return null;
   }

   private static String extractArtworkUrl(String json) {
      int idx = json.indexOf("\"artworkUrl100\"");
      if (idx < 0) {
         idx = json.indexOf("\"artworkUrl\"");
      }
      if (idx < 0) {
         return null;
      }
      int q1 = json.indexOf('"', idx + 14);
      if (q1 < 0) {
         return null;
      }
      int q2 = json.indexOf('"', q1 + 1);
      if (q2 < 0) {
         return null;
      }
      return json.substring(q1 + 1, q2);
   }

   static void shutdown() {
      stopped = true;
      if (worker != null) {
         worker.interrupt();
      }
   }

   private static final class Job {
      final String key;
      final String title;
      final String artist;
      volatile byte[] resultBytes;

      Job(String key, String title, String artist) {
         this.key = key;
         this.title = title;
         this.artist = artist;
      }
   }
}