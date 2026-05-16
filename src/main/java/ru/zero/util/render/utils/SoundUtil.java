package ru.zero.util.render.utils;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.FloatControl.Type;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;

@Environment(EnvType.CLIENT)
public class SoundUtil {
   private static final int MAX_WAV_CLIPS = 8;
   private static Clip currentClip = null;
   private static final List<Clip> CLIPS_LIST = new ArrayList<>();

   public static void playSound_mp3(String sound, float value, boolean nonstop) {
      closeClip(currentClip);
      currentClip = null;

      try {
         currentClip = AudioSystem.getClip();
         InputStream is = Zero.class.getResourceAsStream("/assets/" + "zero" + "/sound/mp3/" + sound);
         if (is == null) {
            System.out.println("Sound not found!");
            closeClip(currentClip);
            currentClip = null;
            return;
         }

         BufferedInputStream bis = new BufferedInputStream(is);
         AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bis);
         if (audioInputStream == null) {
            System.out.println("Sound not found!");
            closeClip(currentClip);
            currentClip = null;
            return;
         }

         currentClip.open(audioInputStream);
         currentClip.start();
         FloatControl floatControl = (FloatControl)currentClip.getControl(Type.MASTER_GAIN);
         float min = floatControl.getMinimum();
         float max = floatControl.getMaximum();
         float volumeInDecibels = (float)(min * (1.0 - value / 100.0) + max * (value / 100.0));
         floatControl.setValue(volumeInDecibels);
         if (nonstop) {
            Clip loopClip = currentClip;
            currentClip.addLineListener(event -> {
               if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                  loopClip.setFramePosition(0);
                  loopClip.start();
               }
            });
         }
      } catch (Exception var10) {
         closeClip(currentClip);
         currentClip = null;
         var10.printStackTrace();
      }
   }

   public static void playSound_wav(String location, float volume) {
      purgeFinishedClips();

      try {
         String resourcePath = "/assets/" + "zero" + "/sound/wav/" + location + ".wav";
         InputStream inputStream = SoundUtil.class.getResourceAsStream(resourcePath);
         if (inputStream == null) {
            return;
         }

         BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
         AudioInputStream audioStream = AudioSystem.getAudioInputStream(bufferedInputStream);
         Clip clip = AudioSystem.getClip();
         clip.open(audioStream);
         float volumeVal = volume < 0.0F ? 0.0F : (volume > 1.0F ? 1.0F : volume);
         FloatControl volumeControl = (FloatControl)clip.getControl(Type.MASTER_GAIN);
         volumeControl.setValue((float)(Math.log(volumeVal) / Math.log(10.0) * 20.0));
         clip.start();
         while (CLIPS_LIST.size() >= MAX_WAV_CLIPS) {
            closeClip(CLIPS_LIST.remove(0));
         }
         CLIPS_LIST.add(clip);
      } catch (Exception var6) {
         var6.printStackTrace();
      }
   }

   public static void releaseAll() {
      closeClip(currentClip);
      currentClip = null;
      for (Clip clip : new ArrayList<>(CLIPS_LIST)) {
         closeClip(clip);
      }
      CLIPS_LIST.clear();
   }

   private static void purgeFinishedClips() {
      for (int i = CLIPS_LIST.size() - 1; i >= 0; i--) {
         Clip clip = CLIPS_LIST.get(i);
         if (clip == null || !clip.isOpen() || !clip.isRunning()) {
            closeClip(clip);
            CLIPS_LIST.remove(i);
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
