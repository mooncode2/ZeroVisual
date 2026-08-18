package ru.zero.util.client;

import java.util.HashMap;
import java.util.Map;

public class MusicPlayer {
   private static boolean isPlaying = false;
   private static String currentTrackName = "Unknown Track";
   private static String currentArtistName = "Unknown Artist";
   private static String currentAlbumArt = "";
   private static int currentTime = 0;
   private static int totalTime = 180;
   
   // Simulated music data for different players
   private static final Map<String, MusicInfo> simulatedMusicData = new HashMap<>();
   
   static {
      // Simulated Yandex Music data
      simulatedMusicData.put("yandex", new MusicInfo(
          "Любимка",
          "Грибы",
          "https://example.com/album-art.jpg",
          60,
          180
      ));
      
      // Simulated Spotify data
      simulatedMusicData.put("spotify", new MusicInfo(
          "Blinding Lights",
          "The Weeknd",
          "https://example.com/blinding-lights.jpg",
          90,
          240
      ));
   }
   
   public static boolean isPlaying() {
      return isPlaying;
   }
   
   public static String getCurrentTrackName() {
      return currentTrackName;
   }
   
   public static String getCurrentArtistName() {
      return currentArtistName;
   }
   
   public static String getCurrentAlbumArt() {
      return currentAlbumArt;
   }
   
   public static int getCurrentTime() {
      return currentTime;
   }
   
   public static int getTotalTime() {
      return totalTime;
   }
   
   public static void updateFromPlayer(String playerType) {
      if (simulatedMusicData.containsKey(playerType)) {
         MusicInfo info = simulatedMusicData.get(playerType);
         currentTrackName = info.trackName;
         currentArtistName = info.artistName;
         currentAlbumArt = info.albumArt;
         currentTime = info.currentTime;
         totalTime = info.totalTime;
         isPlaying = true;
      } else {
         isPlaying = false;
      }
   }
   
   public static void togglePlayPause() {
      isPlaying = !isPlaying;
   }
   
   public static void nextTrack() {
      // Simulate next track
      if (currentTime < totalTime) {
         currentTime = 0;
      }
   }
   
   public static void previousTrack() {
      // Simulate previous track
      currentTime = 0;
   }
   
   public static void updateProgress(int seconds) {
      currentTime = Math.min(seconds, totalTime);
   }
   
   private static class MusicInfo {
      String trackName;
      String artistName;
      String albumArt;
      int currentTime;
      int totalTime;
      
      public MusicInfo(String trackName, String artistName, String albumArt, int currentTime, int totalTime) {
         this.trackName = trackName;
         this.artistName = artistName;
         this.albumArt = albumArt;
         this.currentTime = currentTime;
         this.totalTime = totalTime;
      }
   }
}