package ru.zero.module.impl.prime;

import java.lang.reflect.Method;
import ru.zero.module.api.Module;

final class PrimeCommandUtil {
   private PrimeCommandUtil() {
   }

   static void sendCommand(String command) {
      if (Module.mc.player == null || Module.mc.player.networkHandler == null || command == null) {
         return;
      }

      String raw = command.trim();
      if (raw.isEmpty()) {
         return;
      }

      String withoutSlash = raw.startsWith("/") ? raw.substring(1) : raw;
      String chatPayload = raw.startsWith("/") ? raw : "/" + raw;
      Object networkHandler = Module.mc.player.networkHandler;
      // На некоторых маппингах/сборках sendChatCommand не уходит,
      // поэтому сначала принудительно шлем как обычное сообщение чата.
      if (tryInvokeFlexible(networkHandler, "sendChatMessage", chatPayload)) {
         return;
      }
      if (tryInvokeFlexible(networkHandler, "sendChatCommand", withoutSlash)) {
         return;
      }
      tryInvokeFlexible(networkHandler, "sendCommand", withoutSlash);
   }

   private static boolean tryInvokeFlexible(Object target, String methodName, String value) {
      Method[] methods = target.getClass().getMethods();
      for (Method method : methods) {
         if (!method.getName().equals(methodName)) {
            continue;
         }

         Class<?>[] params = method.getParameterTypes();
         try {
            if (params.length == 1 && params[0] == String.class) {
               method.invoke(target, value);
               return true;
            }
            if (params.length >= 1 && params[0] == String.class) {
               Object[] args = new Object[params.length];
               args[0] = value;
               for (int i = 1; i < params.length; i++) {
                  args[i] = defaultValue(params[i]);
               }
               method.invoke(target, args);
               return true;
            }
         } catch (Exception ignored) {
         }
      }
      return false;
   }

   private static Object defaultValue(Class<?> type) {
      if (!type.isPrimitive()) {
         return null;
      }
      if (type == boolean.class) return false;
      if (type == byte.class) return (byte)0;
      if (type == short.class) return (short)0;
      if (type == int.class) return 0;
      if (type == long.class) return 0L;
      if (type == float.class) return 0.0F;
      if (type == double.class) return 0.0;
      if (type == char.class) return '\0';
      return null;
   }
}
