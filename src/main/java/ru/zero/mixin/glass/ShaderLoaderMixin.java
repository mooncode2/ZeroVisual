package ru.zero.mixin.glass;

import com.mojang.blaze3d.shaders.ShaderType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderLoader;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ShaderLoader.class})
public class ShaderLoaderMixin {
   @Inject(
      method = {"getSource"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void zero$injectShaderSource(Identifier id, ShaderType type, CallbackInfoReturnable<String> cir) {
      if (cir.getReturnValue() == null) {
         if ("zero".equals(id.getNamespace())) {
            String extension = type == ShaderType.VERTEX ? ".vsh" : ".fsh";
            String var10000 = id.getNamespace();
            String var10001 = id.getPath();
            Identifier directResourceId = Identifier.of(var10000, var10001 + extension);
            var10000 = id.getNamespace();
            var10001 = id.getPath();
            Identifier shaderResourceId = Identifier.of(var10000, "shaders/" + var10001 + extension);

            try {
               MinecraftClient mc = MinecraftClient.getInstance();
               if (mc == null) {
                  return;
               }

               Optional<Resource> resource = mc.getResourceManager().getResource(directResourceId);
               if (resource.isEmpty()) {
                  resource = mc.getResourceManager().getResource(shaderResourceId);
               }

               if (resource.isEmpty()) {
                  return;
               }

               InputStream is = ((Resource)resource.get()).getInputStream();

               try {
                  cir.setReturnValue(new String(is.readAllBytes(), StandardCharsets.UTF_8));
               } catch (Throwable var13) {
                  if (is != null) {
                     try {
                        is.close();
                     } catch (Throwable var12) {
                        var13.addSuppressed(var12);
                     }
                  }

                  throw var13;
               }

               if (is != null) {
                  is.close();
               }
            } catch (Exception var14) {
            }

         }
      }
   }
}
