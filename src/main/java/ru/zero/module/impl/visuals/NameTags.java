package ru.zero.module.impl.visuals;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Vector2d;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventScreen;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.util.render.world.WorldProjection;

@IModule(name = "Name Tags", description = "Теги над сущностями", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class NameTags extends Module {
    private static final float FONT_SIZE = 11.0F;
    private static final float TAG_PADDING_X = 5.0F;
    private static final float TAG_PADDING_Y = 3.0F;
    private static final float TAG_RADIUS = 3.0F;
    private static final float MAX_DISTANCE = 96.0F;

    private static final Vector2d SCRATCH = new Vector2d();
    private final List<ItemStack> armorStacks = new ArrayList<>();
    private final List<ItemStack> shulkerBuffer = new ArrayList<>();

   public static BooleanSetting armor = new BooleanSetting("Броня", true);
   public static BooleanSetting offFriends = new BooleanSetting("Скрывать друзей", false);
   public static BooleanSetting items = new BooleanSetting("Предметы", true);
   public static BooleanSetting backItems = new BooleanSetting("Фон предметов", true)
         .hidden(() -> !items.get());

   public NameTags() {
      this.addSettings(new Setting[] { armor, offFriends, items, backItems });
   }

   public static boolean isEnabled() {
      if (Zero.get == null || Zero.get.manager == null) {
         return false;
      }

      NameTags module = Zero.get.manager.get(NameTags.class);
      return module != null && module.enable;
   }

   public static boolean shouldHideVanillaLabel(Entity entity) {
      if (!isEnabled() || entity == null) {
         return false;
      }

      if (entity instanceof PlayerEntity player) {
         if (offFriends.get()
               && Zero.get != null
               && Zero.get.friendManager != null
               && Zero.get.friendManager.isFriend(player.getNameForScoreboard())) {
            return false;
         }

         return true;
      }

      return items.get() && entity.getType() == EntityType.ITEM;
   }

   @EventInit
   public void onHudRender(EventScreen event) {
      if (!this.enable || mc.world == null || mc.player == null || event == null) {
         return;
      }

      Renderer2D renderer = event.renderer();
      DrawContext context = event.drawContext();
      if (renderer == null) {
         return;
      }

      float tickDelta = mc.getRenderTickCounter().getTickProgress(true);

      for (PlayerEntity player : mc.world.getPlayers()) {
         if (player == mc.player || !player.isAlive()) {
            continue;
         }

         if (shouldSkipFriend(player)) {
            continue;
         }

         if (player.distanceTo(mc.player) > MAX_DISTANCE) {
            continue;
         }

         Vector2d screen = projectHead(player, tickDelta, true);
         if (screen == null) {
            continue;
         }

         renderPlayerTag(renderer, player, screen);
      }

      if (items.get()) {
         for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity item) || item.distanceTo(mc.player) > MAX_DISTANCE) {
               continue;
            }

            Vector2d screen = projectHead(item, tickDelta, true);
            if (screen == null) {
               continue;
            }

            renderItemTag(renderer, item, screen);
         }
      }

      if (context != null) {
         renderer.flush();

         for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive() || shouldSkipFriend(player)) {
               continue;
            }

            if (!armor.get() || player.distanceTo(mc.player) > MAX_DISTANCE) {
               continue;
            }

            Vector2d screen = projectHead(player, tickDelta, false);
            if (screen != null) {
               renderArmorRow(context, player, screen, tickDelta);
            }
         }

         for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity item) || item.distanceTo(mc.player) > MAX_DISTANCE) {
               continue;
            }

            List<ItemStack> shulkerItems = getShulkerItems(item.getStack());
            if (shulkerItems.isEmpty()) {
               continue;
            }

            Vector2d screen = projectHead(item, tickDelta, false);
            if (screen != null) {
               renderShulkerGrid(context, item, shulkerItems, screen, tickDelta);
            }
         }
      }
   }

   private boolean shouldSkipFriend(PlayerEntity player) {
      return offFriends.get()
            && Zero.get != null
            && Zero.get.friendManager != null
            && Zero.get.friendManager.isFriend(player.getNameForScoreboard());
   }

    private Vector2d projectHead(Entity entity, float tickDelta, boolean framebuffer) {
       double x = MathHelper.lerp(tickDelta, entity.lastX, entity.getX());
       double y = MathHelper.lerp(tickDelta, entity.lastY, entity.getY());
       double z = MathHelper.lerp(tickDelta, entity.lastZ, entity.getZ());
       y += entity.getHeight() + 0.35;
       Vector2d projected = WorldProjection.project(x, y, z);
       if (projected == null) {
          return null;
       }
       if (framebuffer) {
          SCRATCH.set(
                WorldProjection.toFramebufferX((float) projected.x),
                WorldProjection.toFramebufferY((float) projected.y));
       } else {
          SCRATCH.set(projected.x, projected.y);
       }
       return SCRATCH;
    }

   private float scaleFor(Entity entity) {
      float distance = entity.distanceTo(mc.player);
      return MathHelper.clamp(1.15F - distance / 48.0F, 0.65F, 1.0F);
   }

   private void renderPlayerTag(Renderer2D renderer, PlayerEntity player, Vector2d screen) {
      float scale = scaleFor(player);
      String label = buildPlayerLabel(player);
      float textWidth = renderer.measureText(FontRegistry.INTER_MEDIUM, label, FONT_SIZE).width * scale;
      float boxW = textWidth + TAG_PADDING_X * 2.0F * scale;
      float boxH = (FONT_SIZE + TAG_PADDING_Y * 2.0F) * scale;
      float x = (float) screen.x - boxW * 0.5F;
      float y = (float) screen.y - boxH;

      int outline = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), 45);
      int bg = resolvePlayerBackground(player);
      int textColor = ColorUtil.getColor(255, 255, 255, 255);

      renderer.rectOutline(x, y, boxW, boxH, TAG_RADIUS, outline, 0.35F);
      renderer.rect(x, y, boxW, boxH, TAG_RADIUS, bg);
      renderer.text(
            FontRegistry.INTER_MEDIUM,
            x + TAG_PADDING_X * scale,
            y + TAG_PADDING_Y * scale + FONT_SIZE * 0.82F * scale,
            FONT_SIZE * scale,
            label,
            textColor
      );
   }

    private void renderItemTag(Renderer2D renderer, ItemEntity item, Vector2d screen) {
       float scale = scaleFor(item);
       ItemStack stack = item.getStack();
       String label = stack.getName().getString() + " x" + stack.getCount();
       float textWidth = renderer.measureText(FontRegistry.INTER_MEDIUM, label, FONT_SIZE).width * scale;
      float boxW = textWidth + TAG_PADDING_X * 2.0F * scale;
      float boxH = (FONT_SIZE + TAG_PADDING_Y * 2.0F) * scale;
      float x = (float) screen.x - boxW * 0.5F;
      float y = (float) screen.y - boxH;

      if (backItems.get()) {
         int outline = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), 40);
         int bg = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), 170);
         renderer.rectOutline(x, y, boxW, boxH, TAG_RADIUS, outline, 0.3F);
         renderer.rect(x, y, boxW, boxH, TAG_RADIUS, bg);
      }

      renderer.text(
            FontRegistry.INTER_MEDIUM,
            x + TAG_PADDING_X * scale,
            y + TAG_PADDING_Y * scale + FONT_SIZE * 0.82F * scale,
            FONT_SIZE * scale,
            label,
            ColorUtil.getColor(255, 255, 255, 255)
      );
   }

    private void renderArmorRow(DrawContext context, PlayerEntity player, Vector2d screen, float tickDelta) {
       this.armorStacks.clear();
       this.armorStacks.add(player.getEquippedStack(EquipmentSlot.HEAD));
       this.armorStacks.add(player.getEquippedStack(EquipmentSlot.CHEST));
       this.armorStacks.add(player.getEquippedStack(EquipmentSlot.LEGS));
       this.armorStacks.add(player.getEquippedStack(EquipmentSlot.FEET));
       this.armorStacks.add(player.getEquippedStack(EquipmentSlot.MAINHAND));
       this.armorStacks.add(player.getEquippedStack(EquipmentSlot.OFFHAND));
       this.armorStacks.removeIf(ItemStack::isEmpty);
       if (this.armorStacks.isEmpty()) {
          return;
       }

       float scale = scaleFor(player);
       float rowW = this.armorStacks.size() * 18.0F;
       float startX = (float) screen.x - rowW * 0.5F * scale;
       float startY = (float) screen.y - (18.0F + 6.0F) * scale;

       context.getMatrices().pushMatrix();
       context.getMatrices().translate(startX, startY);
       context.getMatrices().scale(scale, scale);

       for (int i = 0; i < this.armorStacks.size(); i++) {
          context.drawItem(this.armorStacks.get(i), i * 18, 0);
       }

       context.getMatrices().popMatrix();
    }

   private void renderShulkerGrid(
         DrawContext context,
         ItemEntity item,
         List<ItemStack> shulkerItems,
         Vector2d screen,
         float tickDelta
   ) {
      float scale = scaleFor(item);
      int columns = Math.min(shulkerItems.size(), 9);
      int rows = (int) Math.ceil(shulkerItems.size() / 9.0F);
      float boxW = columns * 18.0F + 4.0F;
      float boxH = rows * 18.0F + 4.0F;

      context.getMatrices().pushMatrix();
      context.getMatrices().translate((float) screen.x, (float) screen.y);
      context.getMatrices().scale(scale, scale);

      for (int i = 0; i < shulkerItems.size(); i++) {
         int ix = i % 9 * 18 - (int) (boxW / 2.0F) + 2;
         int iy = i / 9 * 18 - (int) (boxH / 2.0F) + 2;
         context.drawItem(shulkerItems.get(i), ix, iy);
      }

      context.getMatrices().popMatrix();
   }

    private String buildPlayerLabel(PlayerEntity player) {
       String name = player.getDisplayName().getString();
       int health = Math.round(player.getHealth() + player.getAbsorptionAmount());
       return name + " [" + health + "]";
    }

   private int resolvePlayerBackground(PlayerEntity player) {
      String name = player.getNameForScoreboard();
      if (Zero.get != null && Zero.get.friendManager != null && Zero.get.friendManager.isFriend(name)) {
         return ColorUtil.getColor(0, 110, 0, 140);
      }

      if (Zero.get != null && Zero.get.targetManager != null && Zero.get.targetManager.isTarget(name)) {
         return ColorUtil.getColor(130, 20, 20, 150);
      }

      return Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), 175);
   }

    private List<ItemStack> getShulkerItems(ItemStack stack) {
       this.shulkerBuffer.clear();
       if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem blockItem) || !(blockItem.getBlock() instanceof ShulkerBoxBlock)) {
          return this.shulkerBuffer;
       }

       var container = stack.get(net.minecraft.component.DataComponentTypes.CONTAINER);
       if (container == null) {
          return this.shulkerBuffer;
       }

       container.iterateNonEmpty().forEach(this.shulkerBuffer::add);
       return this.shulkerBuffer;
    }
}
