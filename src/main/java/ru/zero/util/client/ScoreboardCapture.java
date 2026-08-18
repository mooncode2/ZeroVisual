package ru.zero.util.client;

import net.minecraft.scoreboard.ScoreboardObjective;

public final class ScoreboardCapture {
   private static ScoreboardObjective currentObjective;

   private ScoreboardCapture() {
   }

   public static void capture(ScoreboardObjective objective) {
      currentObjective = objective;
   }

   public static ScoreboardObjective current() {
      return currentObjective;
   }

   public static void clear() {
      currentObjective = null;
   }
}
