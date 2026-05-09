package ru.zero.commands;

import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.config.ConfigCommand;

@Environment(EnvType.CLIENT)
public final class CommandBootstrap {
   private static final AtomicBoolean INITIALIZED = new AtomicBoolean(false);

   private CommandBootstrap() {
   }

   public static void initialize() {
      if (INITIALIZED.compareAndSet(false, true)) {
         CommandManager manager = CommandManager.getInstance();
         manager.register(ConfigCommand.getInstance());
      }
   }
}
