package ru.zero.util.player;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.Identifier;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.TextDisplayEntity;
import net.minecraft.entity.decoration.DisplayEntity.ItemDisplayEntity.Data;
import ru.zero.mixin.IItemDisplayEntity;
import ru.zero.util.other.IMinecraft;

@Environment(EnvType.CLIENT)
public class SkinEntityUtil implements IMinecraft {
   public static final String SMOKE_GRENADE_SKIN = "minecraft:229253ea86489a63";
   public static final List<String> COUNTERMINE_SKINS = List.of(
         "minecraft:05aba16cdc76d532",
         "minecraft:0690f8af68a9db2b",
         "minecraft:0b8b9c34d1adc0c3",
         "minecraft:105c0ea4662d38a3",
         "minecraft:136878647cbce0d3",
         "minecraft:142b6529689896b8",
         "minecraft:15afd281c6ac1f8a",
         "minecraft:2ecf3b470478d4d5",
         "minecraft:3676ab168bf664ab",
         "minecraft:4178409ffeb382dc",
         "minecraft:43c90169107dfe15",
         "minecraft:54837b32f9de8638",
         "minecraft:5f76e7c7a1f3743c",
         "minecraft:62e0957de0946c44",
         "minecraft:63c848962debde58",
         "minecraft:66c5a9a31593e097",
         "minecraft:6b341411a05002d0",
         "minecraft:6d9c41761b0ff136",
         "minecraft:6f4797e7acadd8ba",
         "minecraft:72fc9512570bebca",
         "minecraft:8685c69ef293f8e2",
         "minecraft:8ad41dcee5554013",
         "minecraft:8d9f5ce9a4421880",
         "minecraft:99e584e9dfcccf5d",
         "minecraft:9d61a17ffacb0247",
         "minecraft:b0df49f36e2d41f6",
         "minecraft:b2e8de1fea48aac3",
         "minecraft:bc504a5e55227c04",
         "minecraft:bd7a2247c537017f",
         "minecraft:bfcdbc047ce7ff08",
         "minecraft:ca00a03d84155b63",
         "minecraft:cc51a96870e7cfc6",
         "minecraft:d4499fdfb133be7b",
         "minecraft:d613184e0e9a42c8",
         "minecraft:d8b76592831bc53f",
         "minecraft:e5afd4ccc1eb0a95",
         "minecraft:ee0aea119ef7a97c");

   public static String getEntitySkinModel(Entity entity) {
      if (entity instanceof ItemDisplayEntity displayEntity) {
         Data data = ((IItemDisplayEntity) displayEntity).client$data();
         if (data == null) {
            return "";
         } else {
            Identifier model = (Identifier) data.itemStack().get(DataComponentTypes.ITEM_MODEL);
            return model == null ? "" : model.toString();
         }
      } else {
         return "";
      }
   }

   public static List<Entity> getSkinEntities() {
      ArrayList<Entity> result = new ArrayList<>();
      if (mc.world != null && mc.player != null) {
         for (Entity entity : mc.world.getEntities()) {
            if (entity != mc.player && entity instanceof ItemDisplayEntity
                  && COUNTERMINE_SKINS.contains(getEntitySkinModel(entity))) {
               result.add(entity);
            }
         }

         return result;
      } else {
         return result;
      }
   }

   public static boolean isTeammate(Entity entity) {
      if (MinecraftClient.getInstance().world == null) {
         return false;
      } else {
         List<TextDisplayEntity> textDisplays = MinecraftClient.getInstance().world.getEntitiesByClass(TextDisplayEntity.class,
               entity.getBoundingBox().expand(2.0), e -> true);
         boolean team = false;

         for (TextDisplayEntity ent : textDisplays) {
            if (ent.getText() != null && !ent.getText().getString().isEmpty()) {
               team = true;
               break;
            }
         }

         return team;
      }
   }

   public static boolean checkIsAlive(Entity entity) {
      if (MinecraftClient.getInstance().world == null) {
         return false;
      } else {
         List<TextDisplayEntity> textDisplays = MinecraftClient.getInstance().world.getEntitiesByClass(TextDisplayEntity.class,
               entity.getBoundingBox().expand(2.0), e -> true);
         return !textDisplays.isEmpty();
      }
   }

   public static boolean oldCheckIsAlive(Entity entity) {
      if (mc.world == null) {
         return false;
      } else {
         BlockPos pos = BlockPos.ofFloored(new Vec3d(entity.getX(), entity.getY(), entity.getZ()));
         Block block = mc.world.getBlockState(pos).getBlock();
         return block != Blocks.BEDROCK;
      }
   }

   public static boolean isSmokeGrenade(Entity entity) {
      String skinModel = getEntitySkinModel(entity);
      return skinModel.equals("minecraft:229253ea86489a63");
   }
}
