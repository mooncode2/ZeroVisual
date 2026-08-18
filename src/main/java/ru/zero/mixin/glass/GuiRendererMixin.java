package ru.zero.mixin.glass;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import ru.zero.util.render.glass.zero.ZeroGlassElementRenderer;

@Mixin({GuiRenderer.class})
public class GuiRendererMixin {
   @Redirect(
      method = {"<init>"},
      at = @At(
   value = "INVOKE",
   target = "Lcom/google/common/collect/ImmutableMap$Builder;buildOrThrow()Lcom/google/common/collect/ImmutableMap;"
)
   )
   private ImmutableMap<Class<? extends SpecialGuiElementRenderState>, SpecialGuiElementRenderer<?>> zero$addGlassRenderer(ImmutableMap.Builder<Class<? extends SpecialGuiElementRenderState>, SpecialGuiElementRenderer<?>> builder) {
      VertexConsumerProvider.Immediate vertexConsumers = ((GuiRendererAccessor)(Object)this).zero$getVertexConsumers();
      ZeroGlassElementRenderer renderer = new ZeroGlassElementRenderer(vertexConsumers);
      builder.put(renderer.getElementClass(), renderer);
      return builder.buildOrThrow();
   }
}
