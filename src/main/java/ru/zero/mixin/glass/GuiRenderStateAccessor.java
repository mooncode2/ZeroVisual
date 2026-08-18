package ru.zero.mixin.glass;

import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({GuiRenderState.class})
public interface GuiRenderStateAccessor {
   @Accessor("blurLayer")
   int zero$getBlurLayer();
}
