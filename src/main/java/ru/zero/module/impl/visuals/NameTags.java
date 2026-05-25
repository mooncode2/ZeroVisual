package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector2d;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventChangeWorld;
import ru.zero.event.impl.EventScreen;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.MultiBooleanSetting;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@IModule(
   name = "NameTags",
   description = "Отображает теги над сущностями",
   category = Category.Visuals,
   bind = -1
)
@Environment(EnvType.CLIENT)
public class NameTags extends Module {
   private final Map<PlayerEntity, TagState> playerTags = new HashMap<>();

   public MultiBooleanSetting entityType = new MultiBooleanSetting(
      "Отображать", new BooleanSetting("Player", true), new BooleanSetting("Mobs", false), new BooleanSetting("Item", true)
   );

   public BooleanSetting showHealth = new BooleanSetting("Здоровье", true).hidden(() -> !this.entityType.get("Player"));
   public BooleanSetting showDistance = new BooleanSetting("Дистанция", false).hidden(() -> !this.entityType.get("Player"));
   public BooleanSetting showItems = new BooleanSetting("Предметы", true).hidden(() -> !this.entityType.get("Player"));
   public BooleanSetting showArmor = new BooleanSetting("Броня", true).hidden(() -> !this.entityType.get("Player") || !showItems.get());
   public BooleanSetting showMainHand = new BooleanSetting("Правая рука", true).hidden(() -> !this.entityType.get("Player") || !showItems.get());
   public BooleanSetting showOffHand = new BooleanSetting("Левая рука", true).hidden(() -> !this.entityType.get("Player") || !showItems.get());

   public NameTags() {
      this.addSettings(new Setting[]{this.entityType, this.showHealth, this.showDistance, this.showItems, this.showArmor, this.showMainHand, this.showOffHand});
   }

   @EventInit
   public void onWorldLoad(EventChangeWorld e) {
      playerTags.clear();
   }

   @EventInit
   public void onRender2D(EventScreen event) {
      if (mc.world == null || mc.player == null || event == null) {
         return;
      }

      if (!entityType.get("Player")) {
         return;
      }

      float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
      updateTagStates(tickDelta);

      Renderer2D r2 = event.renderer();
      DrawContext dc = event.drawContext();
      if (r2 == null || dc == null) {
         return;
      }

      for (Map.Entry<PlayerEntity, TagState> entry : playerTags.entrySet()) {
         PlayerEntity p = entry.getKey();
         TagState st = entry.getValue();
         if (p == null || st == null) {
            continue;
         }

         boolean friend = Zero.get != null && Zero.get.friendManager != null
               && Zero.get.friendManager.isFriend(p.getName().getString());
         renderPlayerTag(r2, dc, p, st, friend);
      }
   }

   private void updateTagStates(float tickDelta) {
      playerTags.clear();
      if (mc.world == null || mc.player == null || mc.getWindow() == null) {
         return;
      }

      float scaledW = (float) mc.getWindow().getScaledWidth();
      float scaledH = (float) mc.getWindow().getScaledHeight();
      float fbW = (float) mc.getWindow().getFramebufferWidth();
      float fbH = (float) mc.getWindow().getFramebufferHeight();
      float xRatio = scaledW > 0.0F ? fbW / scaledW : 1.0F;
      float yRatio = scaledH > 0.0F ? fbH / scaledH : 1.0F;

      for (PlayerEntity p : mc.world.getPlayers()) {
         if (p == null || p == mc.player) {
            continue;
         }

         if (!mc.player.canSee(p)) {
            continue;
         }

         double x = lerp(tickDelta, p.lastRenderX, p.getX());
         double y = lerp(tickDelta, p.lastRenderY, p.getY()) + p.getHeight() + 0.35;
         double z = lerp(tickDelta, p.lastRenderZ, p.getZ());

         Vector2d projected = Renderer2D.project2D(x, y, z);
         if (projected == null) {
            continue;
         }

         if (!Double.isFinite(projected.x) || !Double.isFinite(projected.y)) {
            continue;
         }

         float sx = (float) projected.x;
         float sy = (float) projected.y;
         float fbX = sx * xRatio;
         float fbY = sy * yRatio;

         double dist = mc.gameRenderer.getCamera().getCameraPos().distanceTo(new Vec3d(p.getX(), p.getY(), p.getZ()));
         float scale = (float) (6.0 / Math.max(dist, 6.0));
         scale = clamp(scale, 0.55F, 1.6F);

         playerTags.put(p, new TagState(fbX, fbY, sx, sy, scale, (float) dist));
      }
   }

   private void renderPlayerTag(Renderer2D r2, DrawContext dc, PlayerEntity p, TagState st, boolean friend) {
      String name = p.getName().getString();
      float health = p.getHealth() + p.getAbsorptionAmount();

      float font = 28.0F;
      float padX = 8.0F;
      float gap = 6.0F;

      String hpText = showHealth.get() ? String.format("%.1f", health) : "";
      String distText = showDistance.get() ? String.format("%.0fm", st.distance) : "";

      float nameW = r2.measureText(FontRegistry.INTER_MEDIUM, name, font).width;
      float hpW = hpText.isEmpty() ? 0.0F : r2.measureText(FontRegistry.INTER_MEDIUM, hpText, font).width;
      float distW = distText.isEmpty() ? 0.0F : r2.measureText(FontRegistry.INTER_MEDIUM, distText, font).width;

      float friendBadgeW = friend ? 14.0F : 0.0F;
      float contentW = friendBadgeW + nameW + (hpW > 0 ? gap + hpW : 0) + (distW > 0 ? gap + distW : 0);
      float boxW = contentW + padX * 2.0F;
      float boxH = 20.0F;

      int outline = ColorUtil.replAlpha(Renderer2D.ColorUtil.getOutLineColor(1, 1), 70);
      int bg = ColorUtil.replAlpha(Renderer2D.ColorUtil.getBackGroundColor(1, 1), 180);
      int text = Renderer2D.ColorUtil.getTextColor(1, 1);
      int main = Renderer2D.ColorUtil.getMainColor(1, 1);

      r2.pushTranslation(st.fbX, st.fbY);
      r2.pushScale(st.scale, st.scale);

      Hud.drawClientRect(r2, -boxW / 2.0F, -boxH, boxW, boxH, 7.0F, 1.0F, 1.0F);
      r2.rectOutline(-boxW / 2.0F, -boxH, boxW, boxH, 7.0F, outline, 1.0F);
      r2.rect(-boxW / 2.0F, -boxH, boxW, boxH, 7.0F, bg);

      float tx = -boxW / 2.0F + padX;
      float ty = -boxH + 8.0F;

      if (friend) {
         int friendBg = ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 35);
         int friendOutline = ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), 90);
         r2.rect(tx, -boxH + 4.0F, 12.0F, 12.0F, 3.0F, friendBg);
         r2.rectOutline(tx, -boxH + 4.0F, 12.0F, 12.0F, 3.0F, friendOutline, 1.0F);
         r2.text(FontRegistry.INTER_MEDIUM, tx + 3.4F, ty + 0.2F, 20.0F, "F", Renderer2D.ColorUtil.getColor(40, 255, 40, 255));
         tx += 14.0F;
      }

      r2.text(FontRegistry.INTER_MEDIUM, tx, ty, font, name, text);
      tx += nameW;

      if (!hpText.isEmpty()) {
         tx += gap;
         r2.text(FontRegistry.INTER_MEDIUM, tx, ty, font, hpText, main);
         tx += hpW;
      }
      if (!distText.isEmpty()) {
         tx += gap;
         r2.text(FontRegistry.INTER_MEDIUM, tx, ty, font, distText, ColorUtil.replAlpha(text, 210));
      }

      r2.popScale();
      r2.popTransform();

      if (showItems.get()) {
         renderPlayerItems(dc, p, st);
      }
   }

   private void renderPlayerItems(DrawContext dc, PlayerEntity p, TagState st) {
      List<ItemStack> stacks = new ArrayList<>();
      if (showArmor.get()) {
         EquipmentSlot[] slots = new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
         for (EquipmentSlot s : slots) {
            ItemStack it = p.getEquippedStack(s);
            if (it != null && !it.isEmpty()) stacks.add(it);
         }
      }
      if (showMainHand.get()) {
         ItemStack it = p.getMainHandStack();
         if (it != null && !it.isEmpty()) stacks.add(it);
      }
      if (showOffHand.get()) {
         ItemStack it = p.getOffHandStack();
         if (it != null && !it.isEmpty()) stacks.add(it);
      }
      if (stacks.isEmpty()) return;

      float itemScale = 0.8F * st.scale;
      float slot = 18.0F * itemScale;
      float spacing = 2.0F * itemScale;
      float totalW = stacks.size() * slot + (stacks.size() - 1) * spacing;
      float left = st.scaledX - totalW / 2.0F;
      float y = st.scaledY - 28.0F * st.scale;

      for (int i = 0; i < stacks.size(); i++) {
         float ix = left + i * (slot + spacing);
         dc.getMatrices().pushMatrix();
         dc.getMatrices().translate(ix, y);
         dc.getMatrices().scale(itemScale, itemScale);
         dc.drawItem(stacks.get(i), 0, 0, i);
         dc.drawStackOverlay(mc.textRenderer, stacks.get(i), 0, 0);
         dc.getMatrices().popMatrix();
      }
   }

   private static double lerp(float t, double a, double b) {
      return a + (b - a) * t;
   }

   private static float clamp(float v, float min, float max) {
      return v < min ? min : (v > max ? max : v);
   }

   private record TagState(float fbX, float fbY, float scaledX, float scaledY, float scale, float distance) {}
}
