package ru.zero.config.friend;

import java.util.List;
import java.util.Locale;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import ru.zero.Zero;
import ru.zero.commands.Command;
import ru.zero.commands.CommandContext;
import ru.zero.commands.CommandException;
import ru.zero.ui.Colors;
import ru.zero.util.client.Lang;

@Environment(EnvType.CLIENT)
public final class FriendCommand implements Command {
   private static final FriendCommand INSTANCE = new FriendCommand();
   private static final List<String> COMMAND_ALIASES = List.of(".friend", ".fr", ".fried");
   private static final String USAGE = ".friend add " + Lang.t("<ник>") + " | .friend remove " + Lang.t("<ник>") + " | .friend list";

   private FriendCommand() {
   }

   public static FriendCommand getInstance() {
      return INSTANCE;
   }

   public List<String> getCommandAliases() {
      return COMMAND_ALIASES;
   }

   @Override
   public String name() {
      return "friend";
   }

   @Override
   public List<String> aliases() {
      return COMMAND_ALIASES;
   }

   @Override
   public String usage() {
      return USAGE;
   }

   @Override
   public String description() {
      return Lang.t("Управление списком друзей");
   }

   @Override
   public void execute(CommandContext context, String arguments) throws CommandException {
      if (Zero.get == null || Zero.get.friendManager == null) {
         throw new CommandException(Lang.t("Система друзей ещё не загружена"));
      }

      if (arguments == null || arguments.isBlank()) {
         context.sendInfo(USAGE);
         return;
      }

      String[] parts = arguments.split("\\s+", 2);
      String subCommand = parts[0].toLowerCase(Locale.ROOT);
      String remainder = parts.length > 1 ? parts[1].trim() : "";

      switch (subCommand) {
         case "add":
         case "+":
            handleAdd(context, remainder);
            break;
         case "remove":
         case "rem":
         case "del":
         case "delete":
         case "-":
            handleRemove(context, remainder);
            break;
         case "list":
         case "ls":
            handleList(context);
            break;
         default:
            throw new CommandException(Lang.t("Неизвестная подкоманда. Используй: add, remove, list"));
      }
   }

   private void handleAdd(CommandContext context, String name) throws CommandException {
      if (name == null || name.isBlank()) {
         throw new CommandException("Укажи ник: .friend add " + Lang.t("<ник>"));
      }

      FriendManager manager = Zero.get.friendManager;
      if (manager.isFriend(name)) {
         context.sendInfo("'" + name + "'" + Lang.t(" уже в списке друзей"));
         return;
      }

      manager.add(name);
      context.sendSuccess(Lang.t("Добавлен друг: ") + name);
   }

   private void handleRemove(CommandContext context, String name) throws CommandException {
      if (name == null || name.isBlank()) {
         throw new CommandException("Укажи ник: .friend remove " + Lang.t("<ник>"));
      }

      FriendManager manager = Zero.get.friendManager;
      if (!manager.isFriend(name)) {
         throw new CommandException("'" + name + "'" + Lang.t(" нет в списке друзей"));
      }

      manager.remove(name);
      context.sendSuccess(Lang.t("Удалён из друзей: ") + name);
   }

   private void handleList(CommandContext context) {
      List<Friend> friends = FriendManager.getFriends();
      if (friends.isEmpty()) {
         context.sendInfo(Lang.t("Список друзей пуст"));
         return;
      }

      MutableText builder = Text.literal(Lang.t("Друзья: "));
      for (int i = 0; i < friends.size(); i++) {
         String friendName = friends.get(i).getName();
         MutableText nameText = Text.literal(friendName);
         nameText.setStyle(Style.EMPTY.withColor(TextColor.fromRgb(Colors.getClientPrimary())));
         builder.append(nameText);
         if (i < friends.size() - 1) {
            builder.append(Text.literal(" | "));
         }
      }

      context.sendInfo(builder);
   }
}
