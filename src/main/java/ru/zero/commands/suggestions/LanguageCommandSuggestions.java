package ru.zero.commands.suggestions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.commands.LanguageCommand;
import ru.zero.util.client.Lang;

@Environment(EnvType.CLIENT)
public final class LanguageCommandSuggestions implements CommandSuggestionProvider {
   private static final LanguageCommandSuggestions INSTANCE = new LanguageCommandSuggestions();
   private static final List<String> LANGUAGES = List.of(Lang.RU, Lang.EN);

   private LanguageCommandSuggestions() {
   }

   public static LanguageCommandSuggestions getInstance() {
      return INSTANCE;
   }

   @Override
   public List<String> aliases() {
      return LanguageCommand.getInstance().aliases();
   }

   @Override
   public boolean supportsInput(String input) {
      return findAlias(input) != null || this.matchesAliasPrefix(input);
   }

   @Override
   public CommandSuggestions.SuggestionSet collect(String input) {
      String alias = findAlias(input);
      if (alias == null) {
         return null;
      }

      String arguments = input.substring(alias.length());
      int leadingSpaces = 0;
      while (leadingSpaces < arguments.length() && Character.isWhitespace(arguments.charAt(leadingSpaces))) {
         leadingSpaces++;
      }

      String partial = arguments.substring(leadingSpaces).trim();
      if (partial.indexOf(' ') >= 0) {
         return null;
      }

      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
      for (String language : LANGUAGES) {
         if (partial.isEmpty() || language.toLowerCase(Locale.ROOT).startsWith(partial.toLowerCase(Locale.ROOT))) {
            String suffix;
            if (partial.isEmpty()) {
               suffix = (arguments.isEmpty() ? " " : "") + language;
            } else {
               suffix = language.substring(partial.length());
            }
            entries.add(new CommandSuggestions.SuggestionEntry(
                  alias + " " + language,
                  Lang.t("Язык клиента"),
                  suffix,
                  false));
         }
      }
      return CommandSuggestions.of(entries);
   }

   @Override
   public List<CommandSuggestions.SuggestionEntry> collectAliasSuggestions(String input) {
      if (input == null || !input.startsWith(".")) {
         return List.of();
      }

      List<CommandSuggestions.SuggestionEntry> entries = new ArrayList<>();
      for (String alias : this.aliases()) {
         if (alias.startsWith(input)) {
            entries.add(new CommandSuggestions.SuggestionEntry(
                  alias,
                  Lang.t("Сменить язык клиента"),
                  alias.substring(input.length()),
                  false));
         }
      }
      return entries;
   }

   private static String findAlias(String input) {
      if (input == null) {
         return null;
      }
      for (String alias : LanguageCommand.getInstance().aliases()) {
         if (input.regionMatches(true, 0, alias, 0, alias.length())
               && (input.length() == alias.length() || Character.isWhitespace(input.charAt(alias.length())))) {
            return alias;
         }
      }
      return null;
   }
}
