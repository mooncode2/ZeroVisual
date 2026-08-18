package ru.zero.util.client;

import com.sun.jna.Native;

/**
 * Sends system media transport control key events (Play/Pause, Next, Previous).
 * Works universally for any app hooked into the Windows System Media Transport
 * Controls (Yandex Music, Spotify, Apple Music, Media Player, browsers, ...).
 */
final class MediaKeys {
   private static final byte VK_MEDIA_PLAY_PAUSE = (byte) 0xB3;
   private static final byte VK_MEDIA_NEXT_TRACK = (byte) 0xB0;
   private static final byte VK_MEDIA_PREV_TRACK = (byte) 0xB1;
   private static final int KEYEVENTF_KEYDOWN = 0x0000;
   private static final int KEYEVENTF_KEYUP = 0x0002;

   private MediaKeys() {
   }

   static void playPause() {
      sendKey(VK_MEDIA_PLAY_PAUSE);
   }

   static void next() {
      sendKey(VK_MEDIA_NEXT_TRACK);
   }

   static void previous() {
      sendKey(VK_MEDIA_PREV_TRACK);
   }

   private static void sendKey(byte vk) {
      try {
         keybd_event(vk, (byte) 0, KEYEVENTF_KEYDOWN, 0L);
         keybd_event(vk, (byte) 0, KEYEVENTF_KEYUP, 0L);
      } catch (Throwable ignored) {
      }
   }

   private static native void keybd_event(byte bVk, byte bScan, int dwFlags, long dwExtraInfo);

   static {
      try {
         Native.register(MediaKeys.class, "user32.dll");
      } catch (Throwable ignored) {
      }
   }
}