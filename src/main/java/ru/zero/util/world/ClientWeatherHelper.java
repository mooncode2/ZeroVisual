package ru.zero.util.world;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import ru.zero.module.impl.utils.Optimizer;
import ru.zero.module.impl.visuals.CustomWorld;

@Environment(EnvType.CLIENT)
public final class ClientWeatherHelper {
   private ClientWeatherHelper() {
   }

   public static boolean shouldHideWeatherVisuals() {
      return (CustomWorld.isActive() && CustomWorld.alwaysClear.get()) || Optimizer.shouldDisableWeather();
   }

   public static boolean hasGameplayRain(World world, BlockPos pos) {
      if (world == null || pos == null || !world.getLevelProperties().isRaining()) {
         return false;
      }

      if (!world.isSkyVisible(pos)) {
         return false;
      }

      if (world.getTopPosition(Heightmap.Type.MOTION_BLOCKING, pos).getY() > pos.getY()) {
         return false;
      }

      Biome biome = world.getBiome(pos).value();
      return biome.getPrecipitation(pos, world.getSeaLevel()) == Biome.Precipitation.RAIN;
   }
}
