package ru.zero.commands;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.client.Lang;

@Environment(EnvType.CLIENT)
public final class LanguageCommand implements Command {
   private static final LanguageCommand INSTANCE = new LanguageCommand();
   private static final List<String> ALIASES = List.of(".language", ".lang");

   private LanguageCommand() {
   }

   public static LanguageCommand getInstance() {
      return INSTANCE;
   }

   @Override
   public String name() {
      return "language";
   }

   @Override
   public List<String> aliases() {
      return ALIASES;
   }

   @Override
   public String usage() {
      return ".language <RU|EN>";
   }

   @Override
   public String description() {
      return "Switch client language";
   }

   @Override
   public void execute(CommandContext context, String arguments) throws CommandException {
      if (arguments == null || arguments.isBlank()) {
         context.sendInfo("Current language: " + Lang.current() + " | Usage: " + usage());
         return;
      }
      String normalized = Lang.normalize(arguments.trim());
      if (normalized == null) {
         throw new CommandException("Unknown language. Use RU or EN");
      }
      Lang.setLanguage(normalized);
      context.sendSuccess("Language: " + normalized);
   }
}
