package ru.zero.mixin.plugin;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import ru.zero.compat.LunarCompat;

/**
 * Lunar Client (ichor) applies redirects on {@code Mouse} and {@code Camera} (freelook).
 * Zero skips conflicting mixins and uses bridge mixins in {@code ru.zero.mixin.lunar} instead.
 *
 * @see LunarCompat
 * @see <a href="https://uku3lig.net/posts/2024-08-20-lunar-compat/">Fabric mods on Lunar</a>
 */
public final class ZeroMixinPlugin implements IMixinConfigPlugin {

   private static final String CAMERA_CLASS = "net.minecraft.client.render.Camera";

   /** Mixins that conflict with Lunar's ichor / bundled Sodium—Iris stack. */
   private static final Set<String> LUNAR_SKIP = Set.of(
         "MouseMixin",
         "GameRendererMixin",
         "FogRendererMixin",
         "LightmapTextureManagerMixin",
         "WorldRendererEntityCaptureMixin",
         "RenderLayerMultiPhaseMixin",
         "CameraMixin",
         "KeyboardInputMixin",
         "InGameHudMixin",
         "InGameHudCrosshairMixin",
         "InGameOverlayRendererMixin",
         "ItemPhysicsMixin"
   );

   private static boolean lunarLogged;

   @Override
   public void onLoad(String mixinPackage) {
      LunarCompat.probeLunarClient();
   }

   @Override
   public String getRefMapperConfig() {
      return null;
   }

   @Override
   public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
      if (!LunarCompat.probeLunarClient()) {
         return true;
      }

      if (!lunarLogged) {
         lunarLogged = true;
         System.out.println("[Zero] Lunar Client detected — skipping incompatible mixins");
      }

      if (CAMERA_CLASS.equals(targetClassName)) {
         return false;
      }

      String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
      return !LUNAR_SKIP.contains(simpleName);
   }

   @Override
   public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
   }

   @Override
   public List<String> getMixins() {
      if (!LunarCompat.probeLunarClient()) {
         return null;
      }

      return List.of(
            "lunar.GameRendererLunarBridgeMixin",
            "lunar.InGameHudLunarMixin",
            "lunar.ScreenLunarMixin"
      );
   }

   @Override
   public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }

   @Override
   public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
   }
}
