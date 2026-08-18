package ru.zero.util.player;

import java.util.HashSet;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class LockManager {
   private static final Set<String> activeLocks = new HashSet<>();

   public static boolean tryLock(String moduleName) {
      if (isLocked()) {
         return false;
      } else {
         activeLocks.add(moduleName);
         return true;
      }
   }

   public static void unlock(String moduleName) {
      activeLocks.remove(moduleName);
   }

   public static boolean isLocked() {
      return !activeLocks.isEmpty();
   }

   public static boolean isLockedBy(String moduleName) {
      return activeLocks.contains(moduleName);
   }
}
