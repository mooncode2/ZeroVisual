package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.item.ItemRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import ru.zero.cosmetic.Cosmetic;
import ru.zero.cosmetic.render.MeleeHandRenderer;

/**
 * Replaces the local player's third-person held item with the equipped melee skin, mirroring
 * EvoVis' {@code PlayerItemInHandLayer} hook: the skin is drawn with the skin's own
 * {@code thirdperson_righthand}/{@code thirdperson_lefthand} transform.
 */
@Environment(EnvType.CLIENT)
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
   @Inject(
      method = "renderItem(Lnet/minecraft/client/render/entity/state/ArmedEntityRenderState;Lnet/minecraft/client/render/item/ItemRenderState;Lnet/minecraft/item/ItemStack;Lnet/minecraft/util/Arm;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/ItemRenderState;render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;III)V"
      ),
      cancellable = true
   )
   private void zero$replaceHeldItemWithMelee(ArmedEntityRenderState state, ItemRenderState itemState, ItemStack item,
         Arm arm, MatrixStack matrices, OrderedRenderCommandQueue queue, int light, CallbackInfo ci) {
      if (!MeleeHandRenderer.shouldReplace(state, item, arm)) {
         return;
      }

      Cosmetic melee = MeleeHandRenderer.equipped();
      if (MeleeHandRenderer.renderThirdPerson(melee, arm, matrices, queue, light)) {
         matrices.pop();
         ci.cancel();
      }
   }
}
