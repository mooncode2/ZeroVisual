package ru.zero.module.impl.visuals.HUD;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import ru.zero.Zero;
import ru.zero.module.api.Module;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.ui.draggable.DraggableManager;
import ru.zero.util.color.ColorUtil;
import ru.zero.util.keyboard.Keyboard;
import ru.zero.util.keyboard.ScaledResolution;
import ru.zero.util.render.animation.util.Animation;
import ru.zero.util.render.animation.util.Easings;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.text.FontRegistry;
import ru.zero.ui.gui.GuiScreen;

@Environment(EnvType.CLIENT)
public class KeyBindHUD {
   public static MinecraftClient mc = MinecraftClient.getInstance();
   private static final List<Module> bindings = new ArrayList<>();
   private static final Map<Module, KeyBindCache> bindCache = new HashMap<>();
   private static final Animation widthAnimation = new Animation();
   private static final Animation heightAnimation = new Animation();
   public static Animation anim2 = new Animation();

   private static final class KeyBindCache {
      int bind = -1;
      String keyName;
      float moduleNameWidth;
      float keyNameWidth;
      float bracketedKeyNameWidth;
   }

   private static KeyBindCache getBindCache(Module module, Renderer2D r2) {
      KeyBindCache cache = bindCache.get(module);
      if (cache == null) {
         cache = new KeyBindCache();
         bindCache.put(module, cache);
      }
      if (cache.bind != module.bind) {
         cache.bind = module.bind;
         cache.keyName = Keyboard.keyName(module.bind);
         cache.moduleNameWidth = r2.measureText(FontRegistry.INTER_MEDIUM, module.name, 20.0F).width;
         cache.keyNameWidth = r2.measureText(FontRegistry.INTER_MEDIUM, cache.keyName, 18.0F).width;
         cache.bracketedKeyNameWidth = r2.measureText(FontRegistry.INTER_MEDIUM, "[" + cache.keyName + "]", 18.0F).width;
      }
      return cache;
   }

   private static boolean shouldDisplay(Module module) {
      boolean isActiveOrAnimating = module.enable || module.mAnim.get() != 0.0;
      boolean hasBind = module.bind > 0;
      return isActiveOrAnimating && hasBind;
   }

   private static void sortModules() {
      bindings.clear();
      for (Module module : Zero.get.manager.getModules()) {
         if (shouldDisplay(module)) {
            bindings.add(module);
         }
      }
      bindings.sort(Comparator.comparing(module -> module.name));
   }

   public static void keybind(Renderer2D r2) {
      ScaledResolution sr = new ScaledResolution(mc);
      sortModules();
      for (Module module : bindings) {
         module.mAnim.update();
      }
      anim2.update();
      boolean expand = !bindings.isEmpty();
      String headerName = "Binds";
      float headerTextWidth = r2.measureText(FontRegistry.INTER_MEDIUM, headerName, 20.0F).width;
      float minWidth = 34.0F + headerTextWidth;
      float calculatedWidth = minWidth;
      if (expand) {
         for (Module module : bindings) {
            float animPC = module.mAnim.get();
            if (animPC > 0.0F) {
               KeyBindCache cache = getBindCache(module, r2);
               float totalWidth = 38.0F + cache.moduleNameWidth + cache.bracketedKeyNameWidth + 10.0F;
               calculatedWidth = Math.max(calculatedWidth, totalWidth * animPC);
            }
         }
      }

      calculatedWidth = Math.max(calculatedWidth, 110.0F);
      float headerHeight = 12.0F;
      float calculatedHeight = headerHeight;
      if (expand) {
         float offset = 0.0F;

         for (Module modulex : bindings) {
            float animPC = modulex.mAnim.get();
            offset += 16.0F * animPC;
         }

         if (offset > 0.0F) {
            calculatedHeight = 36.0F + headerHeight + offset + 2.0F;
         }
      }

      calculatedHeight = Math.max(calculatedHeight, 36.0F);
      boolean isEmpty = bindings.isEmpty();
      boolean closeCondition = isEmpty && !(mc.currentScreen instanceof ChatScreen);
      anim2.run(closeCondition ? 0.0 : 1.0, 0.15F, Easings.QUAD_OUT);
      widthAnimation.update();
      heightAnimation.update();
      widthAnimation.run(calculatedWidth, 0.15, Easings.QUART_OUT);
      heightAnimation.run(Math.max(headerHeight, calculatedHeight - 4.0F), 0.15, Easings.QUART_OUT);
      float animatedWidth = widthAnimation.get();
      float animatedHeight = heightAnimation.get();
      if (!closeCondition || anim2.get() != 0.0F) {
         float preferredX = (sr.getWidth() - animatedWidth) / 2.0F;
         float preferredY = (sr.getHeight() - animatedHeight) / 2.0F;
         DraggableManager.DragSession session = DraggableManager.getInstance().beginDrag("keybinds", preferredX, preferredY, animatedWidth, animatedHeight);
         float x = session.positionX();
         float y = session.positionY() - 40.0F + 40.0F * anim2.get();
          HudEditor.registerRect(x, y, animatedWidth, animatedHeight);
          Hud.drawClientRect(r2, x, y, animatedWidth, animatedHeight, 13.0F, 1.0F * anim2.get(), 1.0F);
          r2.text(FontRegistry.ICONS, x + 12.0F, y + 22.5F, 26.0F, "x", ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), anim2.get()));
         r2.shadow(
            x + 17.0F,
            y + 16.0F,
            0.3F,
            0.3F,
            9.0F,
            6.5F,
            0.5F,
            Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(75.0F * anim2.get()))
         );
         r2.rect(x + 32.0F, y + 11.5F, 2.0F, 10.0F, Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(51.0F * anim2.get())));
         r2.text(FontRegistry.INTER_MEDIUM, x + 42.0F, y + 20.5F, 20.0F, "Binds", ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), anim2.get()));
         r2.rect(x, y + 30.0F, animatedWidth, 1.0F, Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(25.0F * anim2.get())));
         if (expand) {
            float offset = 0.0F;

            for (Module modulex : bindings) {
               float animPC = modulex.mAnim.get();
               if (animPC > 0.0F) {
                  KeyBindCache cache = getBindCache(modulex, r2);
                  String keyName = cache.keyName;
                  String moduleName = modulex.name;
                  float keyNameWidth = cache.keyNameWidth;
                  int textColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getTextColor(1, 1), (int)(animPC * 255.0F));
                  int mainColor = Renderer2D.ColorUtil.replAlpha(Renderer2D.ColorUtil.getMainColor(1, 1), (int)(animPC * 255.0F));
                  float addx = 28.0F - 28.0F * animPC;
                  r2.text(FontRegistry.INTER_MEDIUM, x + 12.0F + addx, y + 40.0F + headerHeight + offset, 20.0F, moduleName, textColor);
                  r2.rectOutline(
                     x + animatedWidth - 16.0F - keyNameWidth + addx,
                     y + 28.0F + headerHeight + offset,
                     12.0F + keyNameWidth - 5.0F,
                     13.0F,
                     4.0F,
                     Renderer2D.ColorUtil.replAlpha(mainColor, (int)(25.0F * animPC)),
                     1.0F
                  );
                  r2.rect(
                     x + animatedWidth - 16.0F - keyNameWidth - addx,
                     y + 28.0F + headerHeight + offset,
                     12.0F + keyNameWidth - 5.0F,
                     13.0F,
                     4.0F,
                     Renderer2D.ColorUtil.replAlpha(mainColor, (int)(15.0F * animPC))
                  );
                  r2.text(
                     FontRegistry.INTER_MEDIUM,
                     x + animatedWidth - 14.2F - keyNameWidth - addx,
                     y + 37.8F + headerHeight + offset,
                     18.0F,
                     keyName,
                     mainColor
                  );
                  offset += 16.0F * animPC;
               }
            }
         }

         DraggableManager.getInstance().endDrag(session);
      }
   }
}
