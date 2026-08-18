package ru.zero.compat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Lunar Client (Genesis / ichor) detection and runtime helpers.
 *
 * @see <a href="https://uku3lig.net/posts/2024-08-20-lunar-compat/">Fabric mods on Lunar (ichor)</a>
 */
@Environment(EnvType.CLIENT)
public final class LunarCompat {

   private static final int DEFER_GPU_TICKS = 120;
   private static final Set<String> DUPLICATE_GPU_MOD_IDS = Set.of("sodium", "iris");

   private static volatile boolean lunarConfirmed;
   private static volatile boolean startupLogged;
   private static volatile int ticksInWorld;

   private LunarCompat() {
   }

   /**
    * Used by {@link ru.zero.mixin.plugin.ZeroMixinPlugin}. Never caches a negative result — ichor may load later.
    */
   public static boolean probeLunarClient() {
      if (lunarConfirmed) {
         return true;
      }

      if (FabricLoader.getInstance().isModLoaded("ichor")) {
         lunarConfirmed = true;
         return true;
      }

      for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
         String id = mod.getMetadata().getId().toLowerCase(Locale.ROOT);
         if (id.contains("lunar") || id.equals("ichor")) {
            lunarConfirmed = true;
            return true;
         }
      }

      if (classExists("com.moonsworth.lunar.genesis.Genesis")
            || classExists("com.moonsworth.lunar.genesis.ClientGameBootstrap")) {
         lunarConfirmed = true;
         return true;
      }

      String classpath = System.getProperty("java.class.path", "").toLowerCase(Locale.ROOT);
      if (classpath.contains("lunarclient") || classpath.contains("genesis-") || classpath.contains("ichor")) {
         lunarConfirmed = true;
         return true;
      }

      return false;
   }

   public static boolean isLunarClient() {
      return lunarConfirmed;
   }

   public static boolean shouldDeferGpuInit() {
      if (!lunarConfirmed || ticksInWorld >= DEFER_GPU_TICKS) {
         return false;
      }

      net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
      return client != null && client.world != null && client.player != null;
   }

   public static void onClientTickInWorld() {
      if (lunarConfirmed && ticksInWorld < DEFER_GPU_TICKS) {
         ticksInWorld++;
      }
   }

   public static void applyStartupTweaks() {
      if (!probeLunarClient()) {
         return;
      }

      if (!startupLogged) {
         startupLogged = true;
         System.out.println("[Zero] Lunar Client detected — compatibility mode enabled");
         warnDuplicateBundledMods();
      }
   }

   private static void warnDuplicateBundledMods() {
      Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
      if (!Files.isDirectory(modsDir)) {
         return;
      }

      try (var stream = Files.list(modsDir)) {
         stream.filter(Files::isRegularFile)
               .map(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))
               .forEach(name -> {
                  for (String modId : DUPLICATE_GPU_MOD_IDS) {
                     if (name.contains(modId) && name.endsWith(".jar")) {
                        System.err.println(
                              "[Zero] Lunar already bundles " + modId
                                    + ". Remove " + name + " from mods/ to avoid GPU out-of-memory crashes.");
                     }
                  }
               });
      } catch (Exception ignored) {
      }
   }

   private static boolean classExists(String name) {
      for (ClassLoader loader : classLoaders()) {
         if (loader == null) {
            continue;
         }
         try {
            Class.forName(name, false, loader);
            return true;
         } catch (ClassNotFoundException ignored) {
         }
      }
      return false;
   }

   private static ClassLoader[] classLoaders() {
      return new ClassLoader[] {
            LunarCompat.class.getClassLoader(),
            Thread.currentThread().getContextClassLoader(),
            ClassLoader.getSystemClassLoader()
      };
   }
}
