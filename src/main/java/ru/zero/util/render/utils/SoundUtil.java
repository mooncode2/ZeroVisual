package ru.zero.util.render.utils;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.Line;
import javax.sound.sampled.FloatControl.Type;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;

@Environment(EnvType.CLIENT)
public class SoundUtil {
   private static Clip currentClip = null;
   private static AudioInputStream stream;
   private static final List<Clip> CLIPS_LIST = new ArrayList<>();

   public static void playSound_mp3(String sound, float value, boolean nonstop) {
      if (currentClip != null && currentClip.isRunning()) {
         currentClip.stop();
      }

      try {
         currentClip = AudioSystem.getClip();
         InputStream is = Zero.class.getResourceAsStream("/assets/" + "zero" + "/sound/mp3/" + sound);
         BufferedInputStream bis = new BufferedInputStream(is);
         AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(bis);
         if (audioInputStream == null) {
            System.out.println("Sound not found!");
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
            currentClip.addLineListener(event -> {
               if (event.getType() == javax.sound.sampled.LineEvent.Type.STOP) {
                  currentClip.setFramePosition(0);
                  currentClip.start();
               }
            });
         }
      } catch (Exception var10) {
         var10.printStackTrace();
      }
   }

   public static void playSound_wav(String location, float volume) {
      CLIPS_LIST.stream().filter(Objects::nonNull).filter(clip -> clip.isOpen()).filter(clip -> !clip.isRunning()).forEach(Line::close);
      CLIPS_LIST.stream().filter(Objects::nonNull).filter(clip -> !clip.isOpen() || !clip.isRunning()).forEach(DataLine::stop);
      CLIPS_LIST.stream().filter(Objects::nonNull).collect(Collectors.toList()).forEach(clip -> {
         if (!clip.isRunning()) {
            CLIPS_LIST.remove(clip);
         }
      });

      try {
         String resourcePath = "/assets/" + "zero" + "/sound/wav/" + location + ".wav";
         InputStream inputStream = SoundUtil.class.getResourceAsStream(resourcePath);
         BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
         stream = AudioSystem.getAudioInputStream(bufferedInputStream);
      } catch (Exception var6) {
         var6.printStackTrace();
      }

      if (stream != null) {
         try {
            CLIPS_LIST.add(AudioSystem.getClip());
         } catch (Exception var5) {
            var5.printStackTrace();
         }

         CLIPS_LIST.stream().filter(Objects::nonNull).filter(clip -> !clip.isOpen()).forEach(clip -> {
            try {
               clip.open(stream);
            } catch (Exception var2x) {
               var2x.printStackTrace();
            }
         });
         CLIPS_LIST.stream().filter(Objects::nonNull).filter(Line::isOpen).forEach(clip -> {
            float volumeVal = volume < 0.0F ? 0.0F : (volume > 1.0F ? 1.0F : volume);
            FloatControl volumeControl = (FloatControl)clip.getControl(Type.MASTER_GAIN);
            volumeControl.setValue((float)(Math.log(volumeVal) / Math.log(10.0) * 20.0));
         });
         CLIPS_LIST.stream().filter(Objects::nonNull).filter(Line::isOpen).filter(clip -> !clip.isRunning()).forEach(DataLine::start);
      }
   }
}
