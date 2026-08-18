package ru.zero.util.render.backends.vk;

import java.nio.ByteBuffer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;

@Environment(EnvType.CLIENT)
public final class VulkanShaderSystem {
   private long compiler;
   private long options;
   private boolean created;

   public VulkanShaderSystem() {
   }

   public void create() {
      this.compiler = Shaderc.shaderc_compiler_initialize();
      if (this.compiler == 0L) {
         throw new IllegalStateException("shaderc_compiler_initialize failed (returned 0)");
      }
      this.options = Shaderc.shaderc_compile_options_initialize();
      if (this.options == 0L) {
         Shaderc.shaderc_compiler_release(this.compiler);
         this.compiler = 0L;
         throw new IllegalStateException("shaderc_compile_options_initialize failed (returned 0)");
      }
      Shaderc.shaderc_compile_options_set_target_env(this.options, Shaderc.shaderc_target_env_vulkan,
            Shaderc.shaderc_env_version_vulkan_1_1);
      Shaderc.shaderc_compile_options_set_optimization_level(this.options,
            Shaderc.shaderc_optimization_level_performance);
      this.created = true;
      System.out.println("[Zero/Vulkan] ShaderSystem ready (shaderc, target=Vulkan 1.1, opt=performance)");
   }

   public ByteBuffer compileVertex(String source, String fileName) {
      return this.compile(source, fileName, Shaderc.shaderc_glsl_vertex_shader);
   }

   public ByteBuffer compileFragment(String source, String fileName) {
      return this.compile(source, fileName, Shaderc.shaderc_glsl_fragment_shader);
   }

   private ByteBuffer compile(String source, String fileName, int kind) {
      if (!this.created) {
         throw new IllegalStateException("ShaderSystem not created");
      }
      long result = Shaderc.shaderc_compile_into_spv(this.compiler, source, kind, fileName, "main", this.options);
      if (result == 0L) {
         throw new IllegalStateException("shaderc_compile_into_spv returned null result for " + fileName);
      }
      try {
         int status = Shaderc.shaderc_result_get_compilation_status(result);
         if (status != Shaderc.shaderc_compilation_status_success) {
            String error = Shaderc.shaderc_result_get_error_message(result);
            throw new IllegalStateException("Shader compilation failed for " + fileName + " (status=" + status + "): "
                  + error);
         }
         ByteBuffer spirv = Shaderc.shaderc_result_get_bytes(result);
         ByteBuffer copy = MemoryUtil.memAlloc(spirv.remaining());
         copy.put(spirv).flip();
         return copy;
      } finally {
         Shaderc.shaderc_result_release(result);
      }
   }

   public void destroy() {
      if (!this.created) {
         return;
      }
      if (this.options != 0L) {
         Shaderc.shaderc_compile_options_release(this.options);
         this.options = 0L;
      }
      if (this.compiler != 0L) {
         Shaderc.shaderc_compiler_release(this.compiler);
         this.compiler = 0L;
      }
      this.created = false;
      System.out.println("[Zero/Vulkan] ShaderSystem destroyed");
   }
}
