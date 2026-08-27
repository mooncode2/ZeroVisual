package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.DefaultFramebufferSet;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({ WorldRenderer.class })
public interface WorldRendererAccessor {
   @Accessor("framebufferSet")
   DefaultFramebufferSet zero$getFramebufferSet();
}
