package ru.zero.module.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.Zero;
import ru.zero.module.impl.visuals.Arrows;
import ru.zero.module.impl.visuals.AspectRation;
import ru.zero.module.impl.visuals.BlockOverlay;
import ru.zero.module.impl.visuals.CustomWorld;
import ru.zero.module.impl.prime.KillEffect;
import ru.zero.module.impl.prime.Pets;
import ru.zero.module.impl.prime.PrimeParticles;
import ru.zero.module.impl.visuals.ESP;
import ru.zero.module.impl.visuals.Hat;
import ru.zero.module.impl.visuals.HitColor;
import ru.zero.module.impl.visuals.Hud;
import ru.zero.module.impl.visuals.Keystrokes;
import ru.zero.module.impl.visuals.ItemESP;
import ru.zero.module.impl.visuals.ItemPhysics;
import ru.zero.module.impl.visuals.JumpCircle;
import ru.zero.module.impl.visuals.Crosshair;
import ru.zero.module.impl.visuals.CustomHitbox;
import ru.zero.module.impl.visuals.NoFluid;
import ru.zero.module.impl.visuals.ShulkerPreview;
import ru.zero.module.impl.visuals.NightVision;
import ru.zero.module.impl.visuals.NoRender;
import ru.zero.module.impl.visuals.Particles;
import ru.zero.module.impl.visuals.Projectile;
import ru.zero.module.impl.visuals.RTXSounds;
import ru.zero.module.impl.visuals.Svetych;
import ru.zero.module.impl.visuals.SwingAnimation;
import ru.zero.module.impl.visuals.TargetESP;
import ru.zero.module.impl.visuals.Trails;
import ru.zero.module.impl.utils.AutoInvisibility;
import ru.zero.module.impl.utils.AutoResell;
import ru.zero.module.impl.misc.ItemScroller;
import ru.zero.module.impl.utils.ClientTune;
import ru.zero.module.impl.utils.DiscordRPC;
import ru.zero.module.impl.utils.HitSound;
import ru.zero.module.impl.utils.ClickBind;
import ru.zero.module.impl.utils.ElytraHelper;
import ru.zero.module.impl.utils.InvMove;
import ru.zero.module.impl.utils.NoDamage;
import ru.zero.module.impl.utils.Optimizer;
import ru.zero.module.impl.utils.Zoom;
import ru.zero.module.impl.utils.MaceHelper;

/**
 * Список модулей клиента: только Visuals, Misc и Utils. Каталоги combat/movement/player не используются.
 */
@Environment(EnvType.CLIENT)
public class Manager {
   public ArrayList<Module> module = new ArrayList<>();
   private final Map<Class<?>, Module> classCache = new HashMap<>();

   public Manager() {
      this.module.add(new ESP());
      this.module.add(new JumpCircle());
      this.module.add(new Trails());
      this.module.add(new Hud());
      this.module.add(new Keystrokes());
      this.module.add(new Arrows());
      this.module.add(new ItemESP());
      this.module.add(new ItemPhysics());
      this.module.add(new Svetych());
      this.module.add(new Particles());
      this.module.add(new Projectile());
      this.module.add(new BlockOverlay());
      this.module.add(new NoRender());
      this.module.add(new SwingAnimation());
      this.module.add(new Hat());
      this.module.add(new TargetESP());
      this.module.add(new CustomWorld());
      this.module.add(new NightVision());
      this.module.add(new AspectRation());
      this.module.add(new RTXSounds());
      this.module.add(new Crosshair());
      this.module.add(new HitColor());
      this.module.add(new NoFluid());
      this.module.add(new CustomHitbox());
      this.module.add(new ShulkerPreview());
      this.module.add(new InvMove());
      this.module.add(new ItemScroller());
      this.module.add(new ClickBind());
      this.module.add(new ElytraHelper());
      this.module.add(new Optimizer());
      this.module.add(new NoDamage());
      this.module.add(new Zoom());
      this.module.add(new HitSound());
      this.module.add(new DiscordRPC());
      this.module.add(new ClientTune());
      this.module.add(new AutoResell());
      this.module.add(new AutoInvisibility());
        this.module.add(new MaceHelper());  // <-- Добавленная регистрация
        this.module.add(new Pets());
       this.module.add(new KillEffect());
      this.module.add(new PrimeParticles());
      this.rebuildCache();
   }

   private void rebuildCache() {
      this.classCache.clear();
      for (Module m : this.module) {
         this.classCache.put(m.getClass(), m);
      }
   }

   public ArrayList<Module> getModules() {
      return this.module;
   }

   public <T extends Module> T get(Class<T> clazz) {
      Module exact = this.classCache.get(clazz);
      if (exact != null) {
         return clazz.cast(exact);
      }
      for (Module module1 : this.module) {
         if (clazz.isAssignableFrom(module1.getClass())) {
            return clazz.cast(module1);
         }
      }
      return null;
   }

   public Module getModule(Class<?> class1) {
      Module cached = this.classCache.get(class1);
      if (cached != null) {
         return cached;
      }
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
