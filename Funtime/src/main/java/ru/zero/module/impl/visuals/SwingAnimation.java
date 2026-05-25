package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Arm;
import net.minecraft.util.math.MathHelper;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import ru.zero.event.EventInit;
import ru.zero.event.render.HandAnimationEvent;
import ru.zero.event.render.RenderItemEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.ModeSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.player.CrosshairTargetUtil;

/**
 * Анимация замаха. Режим «Только Аура» означает: применять стиль только когда под прицелом есть живая цель (Vanilla).
 */
@IModule(name = "Swing Animation", description = "Кастомизация анимации руки", category = Category.Visuals, bind = -1)
@Environment(EnvType.CLIENT)
public class SwingAnimation extends Module {
   public static ModeSetting swingMode = new ModeSetting("Анимация", "Smooth", "Smooth", "Swipe", "Swipe back",
         "SwipeD", "Down", "Spin", "Off");
   public static SliderSetting animSpeed = new SliderSetting("Скорость анимации", 1.0F, 0.1F, 3.0F, 0.1F, false);
   public static SliderSetting animgsize = new SliderSetting("Размер анимации", 3.7F, 1.0F, 10.0F, 0.1F, false)
         .hidden(() -> swingMode.is("Off"));
   public static BooleanSetting auraOnly = new BooleanSetting("Только Аура", false);
   public static BooleanSetting customhands = new BooleanSetting("Модель Руки", false);
   public static BooleanSetting emptyHandModel = new BooleanSetting("Рука без предмета", false)
         .hidden(() -> !customhands.get());
   public static BooleanSetting zemetria = new BooleanSetting("Менять обе руки", false)
         .hidden(() -> !customhands.get());
   public static SliderSetting x = new SliderSetting("X", 0.0F, -2.0F, 2.0F, 0.01F, false)
         .hidden(() -> !customhands.get() || !zemetria.get());
   public static SliderSetting y = new SliderSetting("Y", 0.0F, -2.0F, 2.0F, 0.01F, false)
         .hidden(() -> !customhands.get() || !zemetria.get());
   public static SliderSetting z = new SliderSetting("Z", 0.0F, -2.0F, 2.0F, 0.01F, false)
         .hidden(() -> !customhands.get() || !zemetria.get());
   public static SliderSetting right_x = new SliderSetting("X правая", 0.0F, -2.0F, 2.0F, 0.01F, false)
         .hidden(() -> !customhands.get() || zemetria.get());
   public static SliderSetting right_y = new SliderSetting("Y правая", 0.0F, -2.0F, 2.0F, 0.01F, false)
         .hidden(() -> !customhands.get() || zemetria.get());
   public static SliderSetting right_z = new SliderSetting("Z правая", 0.0F, -2.0F, 2.0F, 0.01F, false)
         .hidden(() -> !customhands.get() || zemetria.get());
   public static SliderSetting lefvt_x = new SliderSetting("X левая", 0.0F, -2.0F, 2.0F, 0.01F, false)
         .hidden(() -> !customhands.get() || zemetria.get());
   public static SliderSetting lefvt_y = new SliderSetting("Y левая", 0.0F, -2.0F, 2.0F, 0.01F, false)
         .hidden(() -> !customhands.get() || zemetria.get());
   public static SliderSetting lefvt_z = new SliderSetting("Z левая", 0.0F, -2.0F, 2.0F, 0.01F, false)
         .hidden(() -> !customhands.get() || zemetria.get());

   public SwingAnimation() {
      this.addSettings(
            new Setting[] { swingMode, animSpeed, animgsize, auraOnly, customhands, emptyHandModel, zemetria, x, y, z,
                  right_x, right_y, right_z, lefvt_x, lefvt_y, lefvt_z });
   }

   private static boolean shouldApplyHandModel(ItemStack stack) {
      return stack == null || !stack.isEmpty() || emptyHandModel.get();
   }

   @EventInit
   public void onEvent(HandAnimationEvent event) {
      if (this.enable && !swingMode.is("Off")) {
         if (auraCheck() && event.getHand().equals(Hand.MAIN_HAND) && shouldApplyHandModel(event.getStack())) {
            String swingModeValue = swingMode.get();
            if (!swingModeValue.equals("Off")) {
               if (event.getHand().equals(Hand.MAIN_HAND)) {
                  MatrixStack matrix = event.getMatrices();
                  float swingProgress = event.getSwingProgress();
                  int i = mc.player.getMainArm().equals(Arm.RIGHT) ? 1 : -1;
                  float sin1 = (float) Math.sin(swingProgress * (Math.PI / 2) * 2.0);
                  float sin2 = (float) Math.sin(swingProgress * (Math.PI / 2) * 2.0);
                  float sinSmooth = (float) (Math.sin(swingProgress * Math.PI) * 0.5);
                  float sinD = (float) Math.sin(swingProgress * swingProgress * (float) Math.PI);
                  float sinS = (float) Math.sin((float) Math.sqrt(swingProgress) * (float) Math.PI);
                  String var11 = swingMode.get();
                  switch (var11) {
                     case "Swipe":
                        matrix.translate(i * 0.67F, -0.32F, -1.0F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * i));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-60 * i));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin1 * -animgsize.get() * 10.0F));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
                        break;
                     case "Swipe back":
                        matrix.translate(i * 0.67F, -0.32F, -1.0F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(90 * i));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-60 * i));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin1 * animgsize.get() * 10.0F));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
                        break;
                     case "SwipeD":
                        matrix.translate(i * 0.67F, -0.32F, -1.0F);
                        matrix.translate(sinS * -animgsize.get() / 35.0F, 0.0F, sinS * -animgsize.get() / 35.0F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(25.0F));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sinS * -animgsize.get() * 5.0F));
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(30.0F));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-90.0F));
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(50.0F));
                        break;
                     case "Down":
                        matrix.translate(i * 0.67F, -0.32F, -1.0F);
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(80 * i));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-30 * i));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin1 * -animgsize.get() * 10.0F));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-100.0F));
                        break;
                     case "Spin":
                        matrix.translate(i * 0.56F, -0.42F, -0.72F);
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(0.0F + swingProgress * 360.0F));
                        matrix.translate(0.0, -0.1, 0.0);
                        break;
                     case "Smooth":
                        matrix.translate(i * 0.56F, -0.42F, -0.72F);
                        matrix.multiply(
                              RotationAxis.POSITIVE_Y.rotationDegrees(i * (45.0F + sin1 * -animgsize.get() * 3.0F)));
                        matrix.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(i * sin2 * -animgsize.get() * 2.0F));
                        matrix.multiply(RotationAxis.POSITIVE_X.rotationDegrees(sin2 * -animgsize.get() * 10.0F));
                        matrix.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(i * -45.0F));
                        matrix.translate(0.0, -0.1, 0.0);
                  }

                  event.cancel();
               }
            }
         }
      }
   }

   public static boolean auraCheck() {
      if (!auraOnly.get()) {
         return true;
      }
      return CrosshairTargetUtil.getLivingCrosshairTarget() != null;
   }

   @EventInit
   public void onEvent(RenderItemEvent event) {
      if (!this.enable || !customhands.get() || !shouldApplyHandModel(event.getStack())) {
         return;
      }

      boolean rightHand = event.isRightHand();
      MatrixStack matrix = event.getMatrix();
      
      if (zemetria.get()) {
         // When "Менять обе руки" is enabled, use same settings for both hands (mirrored X for left)
         if (rightHand) {
            matrix.translate(x.get(), y.get(), z.get());
         } else {
            matrix.translate(-x.get(), y.get(), z.get());
         }
      } else {
         // When "Менять обе руки" is disabled, use separate settings for each hand
         if (rightHand) {
            matrix.translate(right_x.get(), right_y.get(), right_z.get());
         } else {
            matrix.translate(lefvt_x.get(), lefvt_y.get(), lefvt_z.get());
         }
      }
   }
}
