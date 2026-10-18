package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import ru.zero.cosmetic.CosmeticState;
import ru.zero.cosmetic.render.MeleeAnimator;
import ru.zero.event.EventInit;
import ru.zero.event.lifecycle.ClientTickEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BindSettings;
import ru.zero.module.api.setting.impl.ButtonSetting;

@IModule(name = "Cosmetics", description = "Косметика: оружие, шляпы, крылья", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class Cosmetics extends Module {
   public static BindSettings inspectKey = new BindSettings("Анимация осмотра", GLFW.GLFW_KEY_I);
   public static ButtonSetting playInspect = new ButtonSetting("Воспроизвести осмотр", 0)
         .onClick(() -> MeleeAnimator.mainHand().onInspect());

   private boolean inspectDown;
   private boolean syncing;

   public Cosmetics() {
      this.addSettings(new Setting[] { inspectKey, playInspect });
   }

   /** Aligns the module toggle with the persisted cosmetics flag (kept as the single source of truth). */
   public void syncFromState() {
      this.syncing = true;
      try {
         this.setState(CosmeticState.isEnabled());
      } finally {
         this.syncing = false;
      }
   }

   @Override
   public void onEnable() {
      super.onEnable();
      if (!this.syncing) {
         CosmeticState.setEnabled(true);
      }
   }

   @Override
   public void onDisable() {
      super.onDisable();
      MeleeAnimator.resetAll();
      if (!this.syncing) {
         CosmeticState.setEnabled(false);
      }
   }

   @EventInit
   public void onClientTick(ClientTickEvent event) {
      boolean down = isKeyDown(inspectKey.key);
      if (down && !this.inspectDown) {
         MeleeAnimator.mainHand().onInspect();
      }
      this.inspectDown = down;
   }

   private static boolean isKeyDown(int key) {
      if (key == 0) {
         return false;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.getWindow() == null) {
         return false;
      }

      long handle = client.getWindow().getHandle();
      if (key < 0) {
         return GLFW.glfwGetMouseButton(handle, -100 - key) == GLFW.GLFW_PRESS;
      }

      return InputUtil.isKeyPressed(client.getWindow(), key);
   }
}
