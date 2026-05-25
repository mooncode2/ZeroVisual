package ru.zero;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Идентификатор сборки визуала. В Funtime — легитная версия «Funtime»; имя мода в меню — Zero.
 */
@Environment(EnvType.CLIENT)
public final class ClientEdition {
   private static final Properties EDITION = loadEdition();

   private ClientEdition() {
   }

   private static Properties loadEdition() {
      Properties props = new Properties();
      try (InputStream in = ClientEdition.class.getResourceAsStream("/zero/edition.properties")) {
         if (in != null) {
            props.load(in);
         }
      } catch (IOException ignored) {
      }
      return props;
   }

   public static String getEditionId() {
      return EDITION.getProperty("edition", "funtime");
   }

   public static String getDisplayName() {
      return EDITION.getProperty("display_name", "Funtime");
   }

   public static boolean isLegit() {
      return Boolean.parseBoolean(EDITION.getProperty("legit", "true"));
   }

   public static boolean isFuntime() {
      return "funtime".equalsIgnoreCase(getEditionId());
   }
}
