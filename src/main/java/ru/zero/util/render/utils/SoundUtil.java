package ru.zero.util.render.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.FloatControl.Type;
import javax.sound.sampled.LineEvent;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;

/**
 * Воспроизведение звуков клиента. Открытие линии и декодирование выполняются
 * на отдельном потоке, поэтому вызов не блокирует игровой/рендер-поток.
 * Готовые {@link Clip} переиспользуются из пула — повторный удар не создаёт новую линию.
 */
@Environment(EnvType.CLIENT)
public class SoundUtil {
   private static final int MAX_WAV_CLIPS = 12;
   private static final int MAX_POOLED_PER_SOUND = 4;

   private static Clip currentClip = null;
   private static final List<Clip> CLIPS_LIST = new CopyOnWriteArrayList<>();
   private static final Map<String, byte[]> WAV_BYTES_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, AudioFormat> WAV_FORMAT_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, Long> WAV_FRAMES_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, Integer> WAV_OFFSET_CACHE = new ConcurrentHashMap<>();
   private static final Map<String, List<Clip>> CLIP_POOL = new ConcurrentHashMap<>();
   private static final Map<String, Boolean> MISSING_SOUNDS = new ConcurrentHashMap<>();
   private static final String[] PRELOAD_SOUNDS = new String[] { "bell", "bonk", "bubble", "metallic" };

   private static final ExecutorService AUDIO_EXECUTOR = Executors.newSingleThreadExecutor(new ThreadFactory() {
      private final AtomicInteger counter = new AtomicInteger();

      @Override
      public Thread newThread(Runnable r) {
         Thread thread = new Thread(r, "Zero-Audio-" + this.counter.incrementAndGet());
         thread.setDaemon(true);
         thread.setPriority(Thread.NORM_PRIORITY + 1);
         return thread;
      }
   });

   static {
      AUDIO_EXECUTOR.execute(() -> {
         for (String name : PRELOAD_SOUNDS) {
            try {
               loadWavBytes(name);
               warmupClip(name);
            } catch (Throwable ignored) {
            }
         }
      });
   }

   private static byte[] loadWavBytes(String location) throws IOException {
      byte[] cached = WAV_BYTES_CACHE.get(location);
      if (cached != null) {
         return cached;
      }

      if (MISSING_SOUNDS.containsKey(location)) {
         return null;
      }

      String resourcePath = "/assets/zero/sounds/" + location + ".wav";
      InputStream inputStream = SoundUtil.class.getResourceAsStream(resourcePath);
      if (inputStream == null) {
         resourcePath = "/assets/zero/sound/wav/" + location + ".wav";
         inputStream = SoundUtil.class.getResourceAsStream(resourcePath);
      }

      if (inputStream == null) {
         MISSING_SOUNDS.put(location, Boolean.TRUE);
         return null;
      }

      byte[] bytes;
      try {
         bytes = inputStream.readAllBytes();
      } finally {
         inputStream.close();
      }

      WAV_BYTES_CACHE.put(location, bytes);

      try (AudioInputStream parsed = AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes))) {
         AudioFormat fmt = parsed.getFormat();
         long fr = parsed.getFrameLength();
         WAV_FORMAT_CACHE.put(location, fmt);
         if (fr > 0L) {
            WAV_FRAMES_CACHE.put(location, fr);
         }

         int frameSize = fmt.getFrameSize();
         if (frameSize > 0 && fr > 0L) {
            int headerOffset = bytes.length - (int) (fr * frameSize);
            if (headerOffset >= 0 && headerOffset < bytes.length) {
               WAV_OFFSET_CACHE.put(location, headerOffset);
            }
         }
      } catch (Exception ignored) {
      }

      return bytes;
   }

   private static AudioInputStream openStream(String location) throws Exception {
      byte[] bytes = loadWavBytes(location);
      if (bytes == null) {
         return null;
      }

      AudioFormat format = WAV_FORMAT_CACHE.get(location);
      Long frames = WAV_FRAMES_CACHE.get(location);
      Integer offset = WAV_OFFSET_CACHE.get(location);
      if (format != null && frames != null && offset != null && offset >= 0 && offset <= bytes.length) {
         return new AudioInputStream(new ByteArrayInputStream(bytes, offset, bytes.length - offset), format, frames);
      }

      return AudioSystem.getAudioInputStream(new ByteArrayInputStream(bytes));
   }

   /**
    * Заранее открывает одну линию, чтобы первый удар не тратил время на инициализацию.
    */
   private static void warmupClip(String location) {
      try {
         Clip clip = createClip(location);
         if (clip != null) {
            releaseToPool(location, clip);
         }
      } catch (Throwable ignored) {
      }
   }

   private static Clip createClip(String location) throws Exception {
      AudioInputStream stream = openStream(location);
      if (stream == null) {
         return null;
      }

      AudioFormat format = stream.getFormat();
      Clip clip = (Clip) AudioSystem.getLine(new DataLine.Info(Clip.class, format));
      clip.open(stream);
      return clip;
   }

   private static Clip acquireClip(String location) throws Exception {
      List<Clip> pool = CLIP_POOL.get(location);
      if (pool != null) {
         synchronized (pool) {
            for (int i = pool.size() - 1; i >= 0; i--) {
               Clip pooled = pool.remove(i);
               if (pooled != null && pooled.isOpen()) {
                  return pooled;
               }
               closeClip(pooled);
            }
         }
      }

      return createClip(location);
   }

   private static void releaseToPool(String location, Clip clip) {
      if (clip == null) {
         return;
      }

      if (!clip.isOpen()) {
         closeClip(clip);
         return;
      }

      List<Clip> pool = CLIP_POOL.computeIfAbsent(location, k -> new ArrayList<>());
      synchronized (pool) {
         if (pool.size() >= MAX_POOLED_PER_SOUND) {
            closeClip(clip);
            return;
         }

         pool.add(clip);
      }
   }

   public static void playSound_mp3(String sound, float value, boolean nonstop) {
      AUDIO_EXECUTOR.execute(() -> playSoundMp3Blocking(sound, value, nonstop));
   }

   private static void playSoundMp3Blocking(String sound, float value, boolean nonstop) {
      closeClip(currentClip);
      currentClip = null;

      try {
         InputStream is = Zero.class.getResourceAsStream("/assets/zero/sound/mp3/" + sound);
         if (is == null) {
            return;
         }

         BufferedInputStream bis = new BufferedInputStream(is);
         AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bis);
         if (audioInputStream == null) {
            return;
         }

         Clip clip = AudioSystem.getClip();
         currentClip = clip;
         clip.open(audioInputStream);
         applyVolume(clip, (float) (value / 100.0));
         if (nonstop) {
            clip.addLineListener(event -> {
               if (event.getType() == LineEvent.Type.STOP) {
                  clip.setFramePosition(0);
                  clip.start();
               }
            });
         }

         clip.start();
      } catch (Exception e) {
         closeClip(currentClip);
         currentClip = null;
      }
   }

   /**
    * Немедленно ставит звук в очередь на аудио-поток. Не блокирует вызывающий поток.
    */
   public static void playSound_wav(String location, float volume) {
      if (location == null || MISSING_SOUNDS.containsKey(location)) {
         return;
      }

      float clamped = volume < 0.0F ? 0.0F : (volume > 1.0F ? 1.0F : volume);
      AUDIO_EXECUTOR.execute(() -> playWavBlocking(location, clamped));
   }

   private static void playWavBlocking(String location, float volume) {
      purgeFinishedClips();

      try {
         Clip clip = acquireClip(location);
         if (clip == null) {
            return;
         }

         applyVolume(clip, volume);
         clip.setFramePosition(0);
         clip.addLineListener(event -> {
            if (event.getType() == LineEvent.Type.STOP) {
               CLIPS_LIST.remove(clip);
               releaseToPool(location, clip);
            }
         });

         while (CLIPS_LIST.size() >= MAX_WAV_CLIPS) {
            Clip oldest = CLIPS_LIST.isEmpty() ? null : CLIPS_LIST.remove(0);
            if (oldest == null) {
               break;
            }
            closeClip(oldest);
         }

         CLIPS_LIST.add(clip);
         clip.start();
      } catch (Exception ignored) {
      }
   }

   private static void applyVolume(Clip clip, float volume) {
      try {
         if (!clip.isControlSupported(Type.MASTER_GAIN)) {
            return;
         }

         FloatControl control = (FloatControl) clip.getControl(Type.MASTER_GAIN);
         float safe = volume <= 0.0F ? 0.0001F : (volume > 1.0F ? 1.0F : volume);
         float decibels = (float) (Math.log10(safe) * 20.0);
         control.setValue(Math.max(control.getMinimum(), Math.min(control.getMaximum(), decibels)));
      } catch (Exception ignored) {
      }
   }

   public static void releaseAll() {
      closeClip(currentClip);
      currentClip = null;

      for (Clip clip : new ArrayList<>(CLIPS_LIST)) {
         closeClip(clip);
      }
      CLIPS_LIST.clear();

      for (List<Clip> pool : CLIP_POOL.values()) {
         synchronized (pool) {
            for (Clip clip : pool) {
               closeClip(clip);
            }
            pool.clear();
         }
      }
      CLIP_POOL.clear();

      AUDIO_EXECUTOR.shutdownNow();
      try {
         AUDIO_EXECUTOR.awaitTermination(250L, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
   }

   private static void purgeFinishedClips() {
      for (Clip clip : new ArrayList<>(CLIPS_LIST)) {
         if (clip == null || !clip.isOpen() || !clip.isRunning()) {
            CLIPS_LIST.remove(clip);
            closeClip(clip);
         }
      }
   }

   private static void closeClip(Clip clip) {
      if (clip == null) {
         return;
      }

      try {
         if (clip.isRunning()) {
            clip.stop();
         }
      } catch (Exception ignored) {
      }

      try {
         if (clip.isOpen()) {
            clip.close();
         }
      } catch (Exception ignored) {
      }
   }
}
