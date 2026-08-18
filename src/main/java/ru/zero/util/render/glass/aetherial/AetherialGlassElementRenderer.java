package ru.zero.util.render.glass.aetherial;

import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public final class AetherialGlassElementRenderer extends SpecialGuiElementRenderer<AetherialGlassElementRenderState> {
   public AetherialGlassElementRenderer(VertexConsumerProvider.Immediate vertexConsumers) {
      super(vertexConsumers);
   }

   public void render(AetherialGlassElementRenderState element, GuiRenderState state, int scale) {
   }

   public Class<AetherialGlassElementRenderState> getElementClass() {
      return AetherialGlassElementRenderState.class;
   }

   protected void render(AetherialGlassElementRenderState element, MatrixStack matrices) {
   }

   protected String getName() {
      return "aetherial_glass_widget";
   }
}
