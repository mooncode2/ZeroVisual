package ru.zero.util.player;

import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.List;
import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import ru.zero.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public final class PlayerUtil implements IMinecraft {
   public static boolean nullCheck() {
      return mc.player == null || mc.world == null;
   }

   public static boolean isBlockSolid(double x, double y, double z) {
      BlockPos pos = BlockPos.ofFloored(x, y, z);
      return mc.world.getBlockState(pos).isFullCube(mc.world, pos);
   }

   public static Block block(BlockPos pos) {
      return mc.world.getBlockState(pos).getBlock();
   }

   public static float calculateCorrectYawOffset(float yaw) {
      double xDiff = mc.player.getX() - mc.player.lastRenderX;
      double zDiff = mc.player.getZ() - mc.player.lastRenderZ;
      float distSquared = (float)(xDiff * xDiff + zDiff * zDiff);
      float renderYawOffset = mc.player.lastBodyYaw;
      float offset = renderYawOffset;
      if (distSquared > 0.0025000002F) {
         offset = (float)MathHelper.atan2(zDiff, xDiff) * 180.0F / (float) Math.PI - 90.0F;
      }

      if (mc.player != null && mc.player.handSwingProgress > 0.0F) {
         offset = yaw;
      }

      float yawOffsetDiff = MathHelper.wrapDegrees(yaw - (renderYawOffset + MathHelper.wrapDegrees(offset - renderYawOffset) * 0.3F));
      yawOffsetDiff = MathHelper.clamp(yawOffsetDiff, -32.0F, 32.0F);
      renderYawOffset = yaw - yawOffsetDiff;
      if (yawOffsetDiff * yawOffsetDiff > 2500.0F) {
         renderYawOffset += yawOffsetDiff * 0.2F;
      }

      return renderYawOffset;
   }

   public static boolean isPlayerInWeb() {
      Box playerBox = mc.player.getBoundingBox();
      BlockPos playerPosition = mc.player.getBlockPos();
      return getNearbyBlockPositions(playerPosition).stream().anyMatch(pos -> isBlockCobweb(playerBox, pos));
   }

   private static boolean isBlockCobweb(Box playerBox, BlockPos pos) {
      if (!mc.world.getBlockState(pos).isOf(Blocks.COBWEB)) {
         return false;
      } else {
         Box blockBox = new Box(pos);
         return playerBox.intersects(blockBox);
      }
   }

   private static List<BlockPos> getNearbyBlockPositions(BlockPos center) {
      List<BlockPos> positions = new ArrayList<>();

      for (int x = center.getX() - 2; x <= center.getX() + 2; x++) {
         for (int y = center.getY() - 1; y <= center.getY() + 4; y++) {
            for (int z = center.getZ() - 2; z <= center.getZ() + 2; z++) {
               positions.add(new BlockPos(x, y, z));
            }
         }
      }

      return positions;
   }

   public static boolean isServerContains(String searchString) {
      if (searchString == null || searchString.isEmpty()) {
         return false;
      } else if (!nullCheck() && mc.getNetworkHandler() != null) {
         String serverAddress = null;
         if (mc.getCurrentServerEntry() != null) {
            serverAddress = mc.getCurrentServerEntry().address;
         }

         if ((serverAddress == null || serverAddress.isEmpty()) && mc.getNetworkHandler().getConnection() != null) {
            SocketAddress address = mc.getNetworkHandler().getConnection().getAddress();
            if (address != null) {
               serverAddress = address.toString();
               if (serverAddress.startsWith("/")) {
                  serverAddress = serverAddress.substring(1);
               }
            }
         }

         if (mc.isConnectedToLocalServer()) {
            serverAddress = "localhost";
         }

         return serverAddress != null && !serverAddress.isEmpty() ? serverAddress.toLowerCase().contains(searchString.toLowerCase()) : false;
      } else {
         return false;
      }
   }

   @Generated
   private PlayerUtil() {
      throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
   }
}
