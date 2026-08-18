package ru.zero.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import ru.zero.Zero;
import ru.zero.module.impl.utils.MaceHelper;
import ru.zero.util.render.CustomHandRenderer;
import ru.zero.util.render.MaceColorRenderer;

@Environment(EnvType.CLIENT)
@Mixin(ItemRenderer.class)
public abstract class MaceHelperMixin {

   @WrapOperation(
      method = {"renderItem"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemQuads(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;Ljava/util/List;[III)V"
      )}
   )
   private static void zero$heldItemRender(
      MatrixStack matrices,
      VertexConsumer vertexConsumer,
      List<BakedQuad> quads,
      int[] tints,
      int light,
      int overlay,
      Operation<Void> original,
      @Local(ordinal = 0, argsOnly = true) ItemDisplayContext displayContext
   ) {
       boolean isFirstPerson = CustomHandRenderer.isFirstPersonItem(displayContext);
       MaceHelper maceHelper = Zero.get.manager.get(MaceHelper.class);
       boolean wantMace = maceHelper != null && maceHelper.shouldApplyHighlight() && isFirstPerson;

       if (!wantMace) {
          original.call(matrices, vertexConsumer, quads, tints, light, overlay);
          return;
       }

       // Базовый предмет (оригинальная текстура)
       original.call(matrices, vertexConsumer, quads, tints, light, overlay);

       // Цвет-оверлей MaceHelper поверх текстуры
       if (wantMace) {
          VertexConsumer maceColored = MaceColorRenderer.createColoredConsumer(vertexConsumer, maceHelper.getMaceColor());
          original.call(matrices, maceColored, quads, tints, light, overlay);
       }
   }
}
