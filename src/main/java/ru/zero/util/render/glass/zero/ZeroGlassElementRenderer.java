package ru.zero.util.render.glass.zero;

import net.minecraft.client.gui.render.SpecialGuiElementRenderer;
import net.minecraft.client.gui.render.state.GuiRenderState;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public final class ZeroGlassElementRenderer extends SpecialGuiElementRenderer<ZeroGlassElementRenderState> {
   public ZeroGlassElementRenderer(VertexConsumerProvider.Immediate vertexConsumers) {
      super(vertexConsumers);
   }

   public void render(ZeroGlassElementRenderState element, GuiRenderState state, int scale) {
   }

   public Class<ZeroGlassElementRenderState> getElementClass() {
      return ZeroGlassElementRenderState.class;
   }

   protected void render(ZeroGlassElementRenderState element, MatrixStack matrices) {
   }

   protected String getName() {
      return "zero_glass_widget";
   }
}
