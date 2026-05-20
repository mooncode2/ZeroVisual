package ru.zero.ui.gui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Arrays;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import ru.zero.Zero;
import ru.zero.module.api.Category;
import ru.zero.module.api.Module;
import ru.zero.module.api.Theme;
import ru.zero.module.api.setting.impl.BindSettings;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.impl.HueSetting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.module.api.setting.impl.StringSetting;
import ru.zero.util.render.math.ScrollUtil;
import ru.zero.util.render.math.animation.Animation;
import ru.zero.util.render.math.animation.anim.util.Animation2;
import ru.zero.util.render.math.animation.anim2.Easing;
import ru.zero.util.render.math.animation.impl.EaseInOutQuad;

@Environment(EnvType.CLIENT)
public class GuiScreen {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   public static Animation mainAnimation = new EaseInOutQuad(200, 1.0);
   public static Animation categoryAnimation = new EaseInOutQuad(500, 1.0);
   public static Animation moduleAnimation = new EaseInOutQuad(500, 1.0);
   public static Animation animation14 = new EaseInOutQuad(500, 1.0);
   public static Animation animation15 = new EaseInOutQuad(1000, 1.0);
   public static ru.zero.util.render.math.animation.anim2.Animation alpha = new ru.zero.util.render.math.animation.anim2.Animation(
      Easing.EASE_OUT_SINE, 1500L
   );
   public static ru.zero.util.render.math.animation.anim.Animation animation = new ru.zero.util.render.math.animation.anim.Animation();
   public static Animation2 alphaPC = new Animation2();
   public static Animation2 settingPC = new Animation2();
   public static Animation2 alphaPC2 = new Animation2();
   public static Animation2 alphaPC3 = new Animation2();
   public static HueSetting activeColorPicker = null;
   public static float colorPickerX = 0.0F;
   public static float colorPickerY = 0.0F;
   public static boolean pickingSaturationBrightness = false;
   public static boolean pickingHue = false;
   public static boolean pickingAlpha = false;
   public static BindSettings activeBindSetting = null;
   public static StringSetting activeStringSetting = null;
   public static SliderSetting activeSliderSetting = null;
   public static Module activeModuleBind = null;
   public static float sliderX = 0.0F;
   public static float sliderY = 0.0F;
   public static float sliderWidth = 0.0F;
   public static String searchText = "";
   public static boolean activeSearch = false;
   public static long lastBackspaceTime = 0L;
   public static boolean backspaceHeld = false;
   public static long firstBackspacePressTime = 0L;
   public static boolean showClientSettingsPopup = false;
   public static boolean serverMapOpen = false;
   public static BooleanSetting clientBlurSetting = new BooleanSetting("Блюр (Размытие)", false);
   public static BooleanSetting clientVanillaSetting = new BooleanSetting("Ванильный", false);
   public static BooleanSetting clientVulcanSetting = new BooleanSetting("Vulcan", false);
   public static boolean friendsTabOpen = false;
   public static boolean friendInputActive = false;
   public static String friendInputText = "";
   public static boolean exit = false;
   public static float x;
   public static float y;
   public static float width;
   public static float height;
   public static int currentMouseX = 0;
   public static int currentMouseY = 0;
   public static Category[] categories;
   public static Theme selectedTheme;
   public static Theme preSelectedTheme;
   public static Theme[] themes;
   public static Category selectedCategories;
   public static List<Module> modules;
   private static ScrollUtil scrollUtil;
   public static Set<Module> openSettingsModules = new HashSet<>();
   public static Map<Module, Animation2> moduleSettingsAnimations = new HashMap<>();
   public static Map<Module, Animation2> moduleSettingsAlphaAnimations = new HashMap<>();
   public static Map<Module, Animation2> moduleBindAnimations = new HashMap<>();
   public static Map<SliderSetting, Animation2> sliderAnimations = new HashMap<>();

   public static Category[] resolveCategories() {
      boolean showPrime = Zero.get != null
         && Zero.get.visualLinkingClient != null
         && Zero.get.visualLinkingClient.isPrimeOnTargetServer();
      return showPrime ? new Category[] { Category.Visuals, Category.Utils, Category.Prime } : new Category[] { Category.Visuals, Category.Utils };
   }

   public static void refreshCategoriesAndModules() {
      Category[] resolved = resolveCategories();
      if (categories == null || !Arrays.equals(categories, resolved)) {
         categories = resolved;
      }

      boolean primeAvailable = false;
      for (Category category : categories) {
         if (category == Category.Prime) {
            primeAvailable = true;
            break;
         }
      }

      if (!primeAvailable && selectedCategories == Category.Prime) {
         selectedCategories = Category.Visuals;
      } else if (selectedCategories == null) {
         selectedCategories = Category.Visuals;
      }

      if (Zero.get != null && Zero.get.manager != null) {
         modules = Zero.get.manager.getType(selectedCategories);
      }
   }

   public static ScrollUtil getScrollUtil() {
      if (scrollUtil == null) {
         scrollUtil = new ScrollUtil();
      }

      return scrollUtil;
   }

   public static Animation2 getModuleSettingsAnimation(Module module) {
      return moduleSettingsAnimations.computeIfAbsent(module, k -> new Animation2());
   }

   public static Animation2 getModuleSettingsAlphaAnimation(Module module) {
      return moduleSettingsAlphaAnimations.computeIfAbsent(module, k -> new Animation2());
   }

   public static Animation2 getModuleBindAnimation(Module module) {
      Animation2 anim = moduleBindAnimations.computeIfAbsent(module, k -> new Animation2());
      if (module.bind != -1 && anim.getDuration() == 0.0 && anim.getValue() == 0.0) {
         anim.setValue(1.0);
      }

      return anim;
   }

   public static Animation2 getSliderAnimation(SliderSetting slider) {
      return sliderAnimations.computeIfAbsent(slider, k -> {
         Animation2 newAnim = new Animation2();
         float targetProgress = (slider.current - slider.minimum) / (slider.maximum - slider.minimum);
         newAnim.setValue(targetProgress);
         return newAnim;
      });
   }

   public static boolean isVanillaStyle() {
      return clientVanillaSetting != null && clientVanillaSetting.get();
   }

   public static boolean isVulkanMode() {
      return clientVulcanSetting != null && clientVulcanSetting.get();
   }
}
