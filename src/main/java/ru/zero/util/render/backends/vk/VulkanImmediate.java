package ru.zero.util.render.backends.vk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;
import ru.zero.mixin.RenderLayerAccessor;

@Environment(EnvType.CLIENT)
public final class VulkanImmediate extends VertexConsumerProvider.Immediate {
   private static final int[] NO_TEXTURE = new int[] {0, 0, 0};
   private static final Map<Identifier, int[]> textureDimCache = new java.util.HashMap<>();
   private static final java.util.IdentityHashMap<RenderLayer, Integer> drawModeCache = new java.util.IdentityHashMap<>();
   private final VulkanWorldRenderer world;
   private final List<VulkanVertexConsumer> consumers = new ArrayList<>();
   private final java.util.ArrayDeque<VulkanVertexConsumer> pool = new java.util.ArrayDeque<>();

   public VulkanImmediate(VulkanWorldRenderer world, BufferAllocator allocator) {
      super(allocator, new java.util.LinkedHashMap<>());
      this.world = world;
   }

   @Override
   public VertexConsumer getBuffer(RenderLayer layer) {
      Integer drawModeBox = drawModeCache.get(layer);
      int drawMode;
      if (drawModeBox != null) {
         drawMode = drawModeBox;
      } else {
         drawMode = VulkanVertexConsumer.QUADS;
         if (layer != null) {
            String name = layer.toString();
            boolean isLine = false;
            for (int i = 0; i < name.length(); i++) {
               char c = name.charAt(i);
               if (c == 'l' || c == 'L') {
                  if (name.regionMatches(true, i, "line", 0, 4)) {
                     isLine = true;
                     break;
                  }
               }
            }
            if (isLine) {
               drawMode = VulkanVertexConsumer.LINES;
            }
         }
         drawModeCache.put(layer, drawMode);
      }
      int[] tex = resolveLayerTexture(layer);
      VulkanVertexConsumer consumer = this.pool.poll();
      if (consumer == null) {
         consumer = new VulkanVertexConsumer(this.world, drawMode, tex[0], tex[1], tex[2]);
      } else {
         consumer.reset(drawMode, tex[0], tex[1], tex[2]);
      }
      this.consumers.add(consumer);
      return consumer;
   }

   private static int[] resolveLayerTexture(RenderLayer layer) {
      if (layer == null) {
         return NO_TEXTURE;
      }
      try {
         RenderSetup setup = ((RenderLayerAccessor) layer).zero$getRenderSetup();
         if (setup == null) {
            return NO_TEXTURE;
         }
         Map<String, RenderSetup.TextureSpec> textures = setup.textures;
         if (textures == null || textures.isEmpty()) {
            return NO_TEXTURE;
         }
         RenderSetup.TextureSpec spec = textures.values().iterator().next();
         if (spec == null) {
            return NO_TEXTURE;
         }
         Identifier id = spec.location();
         if (id == null) {
            return NO_TEXTURE;
         }
         MinecraftClient mc = MinecraftClient.getInstance();
         if (mc == null || mc.getTextureManager() == null) {
            return NO_TEXTURE;
         }
         AbstractTexture abstractTexture = mc.getTextureManager().getTexture(id);
         if (abstractTexture == null) {
            return NO_TEXTURE;
         }
         if (!(abstractTexture.getGlTexture() instanceof GlTexture glTexture)) {
            return NO_TEXTURE;
         }
         int glId = glTexture.getGlId();
         if (glId <= 0) {
            return NO_TEXTURE;
         }
         int[] cached = VulkanImmediate.textureDimCache.get(id);
         if (cached != null && cached[0] == glId && cached[1] > 0 && cached[2] > 0) {
            return cached;
         }
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, glId);
         int w = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
         int h = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
         GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
         if (w <= 0 || h <= 0) {
            return NO_TEXTURE;
         }
         int[] resolved = new int[] {glId, w, h};
         VulkanImmediate.textureDimCache.put(id, resolved);
         return resolved;
      } catch (Throwable ignored) {
         return NO_TEXTURE;
      }
   }

   public List<VulkanVertexConsumer> consumers() {
      return this.consumers;
   }

   public void clear() {
      java.util.ArrayDeque<VulkanVertexConsumer> p = this.pool;
      for (int i = 0; i < this.consumers.size(); i++) {
         p.offer(this.consumers.get(i));
      }
      this.consumers.clear();
   }
}
