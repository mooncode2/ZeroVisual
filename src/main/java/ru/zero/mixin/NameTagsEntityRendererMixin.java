package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import ru.zero.module.impl.visuals.NameTags;

@Environment(EnvType.CLIENT)
@Mixin(EntityRenderer.class)
public class NameTagsEntityRendererMixin {
   @Inject(method = "getDisplayName", at = @At("HEAD"), cancellable = true)
   private void zero$hideVanillaNameTag(Entity entity, CallbackInfoReturnable<Text> cir) {
      if (NameTags.shouldHideVanillaLabel(entity)) {
         cir.setReturnValue(null);
      }
   }
}
