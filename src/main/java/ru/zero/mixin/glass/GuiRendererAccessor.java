package ru.zero.mixin.glass;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({GuiRenderer.class})
public interface GuiRendererAccessor {
   @Accessor("vertexConsumers")
   VertexConsumerProvider.Immediate zero$getVertexConsumers();
}
