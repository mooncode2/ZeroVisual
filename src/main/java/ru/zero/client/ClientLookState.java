package ru.zero.client;

/**
 * Состояние «свободного взгляда» для редиректов yaw/pitch в движении.
 * Не привязано к чит-модулям: по умолчанию {@link #active} = false, используются угол игрока и камеры Vanilla.
 */
public final class ClientLookState {
   public static boolean active;
   public static float freeYaw;
   public static float freePitch;

   private ClientLookState() {
   }
}
