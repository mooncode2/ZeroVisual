package ru.zero.util.render.texture;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.opengl.GL11;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import ru.zero.util.render.backends.gl.GlBackend;
import ru.zero.util.render.backends.gl.ResourceUtils;

@Environment(EnvType.CLIENT)
public final class TextureLoader {
   private static GlBackend backend;
   private static final Map<String, Integer> textureCache = new HashMap<>();

   private TextureLoader() {
   }

   public static void initialize(GlBackend glBackend) {
      backend = glBackend;
   }

   public static GlBackend getBackend() {
      return backend;
   }

   public static int load(String resourcePath) {
      if (backend == null) {
         throw new IllegalStateException("TextureLoader.initialize() must be called first");
      } else {
         Integer cached = textureCache.get(resourcePath);
         if (cached != null) {
            return cached;
         } else {
            int textureId = loadTexture(resourcePath);
            if (textureId > 0) {
               textureCache.put(resourcePath, textureId);
            }

            return textureId;
         }
      }
   }

   private static int loadTexture(String resourcePath) {
      ByteBuffer imageBuffer;
      try {
         imageBuffer = ResourceUtils.readBinary(resourcePath);
      } catch (Exception var12) {
         System.err.println("Failed to read texture resource: " + resourcePath);
         var12.printStackTrace();
         return 0;
      }

      MemoryStack stack = MemoryStack.stackPush();

      int width;
      label49: {
         int var10;
         try {
            IntBuffer w = stack.mallocInt(1);
            IntBuffer h = stack.mallocInt(1);
            IntBuffer comp = stack.mallocInt(1);
            ByteBuffer image = STBImage.stbi_load_from_memory(imageBuffer, w, h, comp, 4);
            if (image == null) {
               System.err.println("Failed to decode texture: " + resourcePath + " - " + STBImage.stbi_failure_reason());
               width = 0;
               break label49;
            }

            width = w.get(0);
            int height = h.get(0);
            int textureId = backend.createMsdfTexture(width, height, image);
            STBImage.stbi_image_free(image);
            System.out.println("[TextureLoader] Loaded: " + resourcePath + " (" + width + "x" + height + ") -> ID " + textureId);
            var10 = textureId;
         } catch (Throwable var13) {
            if (stack != null) {
               try {
                  stack.close();
               } catch (Throwable var11) {
                  var13.addSuppressed(var11);
               }
            }

            throw var13;
         }

         if (stack != null) {
            stack.close();
         }

         return var10;
      }

      if (stack != null) {
         stack.close();
      }

      return width;
   }

   public static void clearCache() {
      textureCache.clear();
   }

   public static int loadFromPngBytes(byte[] pngData) {
      if (backend == null || pngData == null || pngData.length == 0) {
         return 0;
      }

      MemoryStack stack = MemoryStack.stackPush();

      try {
         IntBuffer w = stack.mallocInt(1);
         IntBuffer h = stack.mallocInt(1);
         IntBuffer comp = stack.mallocInt(1);
         ByteBuffer buffer = ByteBuffer.allocateDirect(pngData.length);
         buffer.put(pngData);
         buffer.flip();
         ByteBuffer image = STBImage.stbi_load_from_memory(buffer, w, h, comp, 4);
         if (image == null) {
            return 0;
         }

         int textureId = backend.createMsdfTexture(w.get(0), h.get(0), image);
         STBImage.stbi_image_free(image);
         return textureId;
      } catch (Throwable ignored) {
         return 0;
      } finally {
         stack.close();
      }
   }

   public static void releaseTexture(int textureId) {
      if (textureId > 0) {
         GL11.glDeleteTextures(textureId);
      }
   }
}
