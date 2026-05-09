package ru.zero.module.api;

import java.util.ArrayList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.Arrows;
import ru.zero.module.impl.visuals.AspectRation;
import ru.zero.module.impl.visuals.CustomWorld;
import ru.zero.module.impl.visuals.ClickGUI;
import ru.zero.module.impl.visuals.ESP;
import ru.zero.module.impl.visuals.Hat;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.module.impl.visuals.ItemESP;
import ru.zero.module.impl.visuals.JumpCircle;
import ru.zero.module.impl.visuals.NameTags;
import ru.zero.module.impl.visuals.NightVision;
import ru.zero.module.impl.visuals.NoRender;
import ru.zero.module.impl.visuals.Particles;
import ru.zero.module.impl.visuals.RTXSounds;
import ru.zero.module.impl.visuals.SkinManager;
import ru.zero.module.impl.visuals.Svetych;
import ru.zero.module.impl.visuals.SwingAnimation;
import ru.zero.module.impl.visuals.TargetESP;
import ru.zero.module.impl.visuals.Test;
import ru.zero.module.impl.visuals.Trails;
import ru.zero.module.impl.misc.ItemScroller;
import ru.zero.module.impl.utils.ClickBind;
import ru.zero.module.impl.utils.ElytraHelper;
import ru.zero.module.impl.utils.InvMove;
import ru.zero.module.impl.utils.Optimizer;

/**
 * Список модулей клиента: только Visuals, Misc и Utils. Каталоги combat/movement/player не используются.
 */
@Environment(EnvType.CLIENT)
public class Manager {
   public ArrayList<Module> module = new ArrayList<>();

   public Manager() {
      this.module.add(new Test());
      this.module.add(new ESP());
      this.module.add(new JumpCircle());
      this.module.add(new Trails());
      this.module.add(new Hud());
      this.module.add(new Arrows());
      this.module.add(new ItemESP());
      this.module.add(new Svetych());
      this.module.add(new Particles());
      this.module.add(new NoRender());
      this.module.add(new SwingAnimation());
      this.module.add(new Hat());
      this.module.add(new TargetESP());
      this.module.add(new SkinManager());
      this.module.add(new CustomWorld());
      this.module.add(new ClickGUI());
      this.module.add(new NightVision());
      this.module.add(new AspectRation());
      this.module.add(new RTXSounds());
      this.module.add(new NameTags());
      this.module.add(new InvMove());
      this.module.add(new ItemScroller());
      this.module.add(new ClickBind());
      this.module.add(new ElytraHelper());
      this.module.add(new Optimizer());
   }

   public ArrayList<Module> getModules() {
      return this.module;
   }

   public <T extends Module> T get(Class<T> clazz) {
      return this.module.stream().filter(module -> clazz.isAssignableFrom(module.getClass())).map(clazz::cast).findFirst().orElse(null);
   }

   public Module getModule(Class<?> class1) {
      for (Module module1 : this.module) {
         if (module1.getClass() == class1) {
            return module1;
         }
      }

      return null;
   }

   public ArrayList<Module> getType(Category category) {
      ArrayList<Module> modules = new ArrayList<>();

      for (Module module1 : this.module) {
         if (module1.category == category) {
            modules.add(module1);
         }
      }

      return modules;
   }

   public Module[] getBind(int bind) {
      return Zero.get.manager.module.stream().filter(module -> module.bind == bind).toArray(Module[]::new);
   }
}
