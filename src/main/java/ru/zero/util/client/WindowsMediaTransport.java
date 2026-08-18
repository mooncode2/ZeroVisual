package ru.zero.util.client;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.WString;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;

/**
 * Reads the Windows 10+ GlobalSystemMediaTransportControlsSessionManager (GSMTC)
 * "now playing" state. Covers Yandex Music, Spotify, Apple Music, Media Player,
 * browsers and any app integrated with the Windows System Media Transport Controls.
 *
 * Defensive + retryable: if WinRT is unavailable or activation fails, the transport
 * backs off and retries on the next {@link #snapshot()} call instead of disabling
 * itself permanently — so media that starts later is still picked up.
 */
final class WindowsMediaTransport {
   private static final int S_OK = 0;
   private static final int POINTER_SIZE = Native.POINTER_SIZE;
   private static final long RETRY_BACKOFF_MS = 4000L;

   private static final String GSMTC_CLASS =
         "Windows.Media.Playback.GlobalSystemMediaTransportControlsSessionManager";

   private static final int MGR_GET_CURRENT_SESSION = 6;
   private static final int SESSION_GET_PLAYBACK_INFO = 6;
   private static final int SESSION_GET_MEDIA_PROPERTIES = 7;
   private static final int SESSION_GET_TIMELINE = 8;
   private static final int SESSION_GET_SOURCE_APP_DISPLAY = 9;
   private static final int MP_GET_TITLE = 6;
   private static final int MP_GET_ARTIST = 7;
   private static final int MP_GET_ALBUM_ARTIST = 8;
   private static final int MP_GET_ALBUM_TITLE = 9;
   private static final int TL_GET_START_TIME = 6;
   private static final int TL_GET_END_TIME = 7;
   private static final int TL_GET_POSITION = 8;
   private static final int PI_GET_PLAYBACK_STATUS = 6;
   private static final int PLAYBACK_STATUS_PLAYING = 3;

   private static volatile boolean nativeRegistered;
   private static volatile boolean comInitialized;
   private static volatile long lastFailureTime;
   private static volatile Boolean available;

   static {
      try {
         Native.register(WindowsMediaTransport.class, "api-ms-win-core-winrt-string-l1-1-0.dll");
         Native.register(WindowsMediaTransport.class, "combase.dll");
         nativeRegistered = true;
      } catch (Throwable ignored) {
         nativeRegistered = false;
      }
   }

   private WindowsMediaTransport() {
   }

   public static native int WindowsCreateString(WString src, int len, PointerByReference out);

   public static native int WindowsDeleteString(Pointer hstr);

   public static native Pointer WindowsGetStringRawBuffer(Pointer hstr, PointerByReference lenOut);

   public static native int RoActivateInstance(Pointer classId, PointerByReference instance);

   private static native int CoInitializeEx(Pointer reserved, int coInit);

   static {
      try {
         Native.register(WindowsMediaTransport.class, "ole32.dll");
      } catch (Throwable ignored) {
      }
   }

   static boolean isAvailable() {
      if (!nativeRegistered) {
         return false;
      }
      if (available != null) {
         return available.booleanValue();
      }
      if (lastFailureTime != 0 && System.currentTimeMillis() - lastFailureTime < RETRY_BACKOFF_MS) {
         return false;
      }
      boolean ok = probe();
      available = Boolean.valueOf(ok);
      if (!ok) {
         lastFailureTime = System.currentTimeMillis();
      }
      return ok;
   }

   private static boolean ensureCom() {
      if (comInitialized) {
         return true;
      }
      try {
         int hr = CoInitializeEx(null, 0x2); // COINIT_APARTMENTTHREADED
         comInitialized = (hr == S_OK || hr == 1); // S_FALSE = already initialized
         return comInitialized;
      } catch (Throwable ignored) {
      }
      return false;
   }

   private static boolean probe() {
      try {
         if (!ensureCom()) {
            return false;
         }
         PointerByReference hstr = new PointerByReference();
         if (WindowsCreateString(new WString(GSMTC_CLASS), GSMTC_CLASS.length(), hstr) != S_OK) {
            return false;
         }
         Pointer classId = hstr.getValue();
         if (classId == null) {
            return false;
         }
         PointerByReference instance = new PointerByReference();
         int hr = RoActivateInstance(classId, instance);
         WindowsDeleteString(classId);
         if (hr == S_OK && instance.getValue() != null) {
            release(instance.getValue());
            return true;
         }
      } catch (Throwable ignored) {
      }
      return false;
   }

   static Snapshot snapshot() {
      if (!isAvailable()) {
         return null;
      }
      try {
         PointerByReference hstr = new PointerByReference();
         if (WindowsCreateString(new WString(GSMTC_CLASS), GSMTC_CLASS.length(), hstr) != S_OK) {
            lastFailureTime = System.currentTimeMillis();
            available = null;
            return null;
         }
         Pointer classId = hstr.getValue();
         if (classId == null) {
            return null;
         }
         PointerByReference mgrOut = new PointerByReference();
         int hr = RoActivateInstance(classId, mgrOut);
         WindowsDeleteString(classId);
         if (hr != S_OK || mgrOut.getValue() == null) {
            lastFailureTime = System.currentTimeMillis();
            available = null;
            return null;
         }
         Pointer mgr = mgrOut.getValue();
         Pointer session = callObject(mgr, MGR_GET_CURRENT_SESSION);
         release(mgr);
         if (session == null) {
            available = Boolean.TRUE;
            return null;
         }
         Snapshot snap = new Snapshot();
         Pointer mp = callObject(session, SESSION_GET_MEDIA_PROPERTIES);
         if (mp != null) {
            snap.title = callHString(mp, MP_GET_TITLE);
            snap.artist = callHString(mp, MP_GET_ARTIST);
            if (snap.artist == null || snap.artist.isEmpty()) {
               snap.artist = callHString(mp, MP_GET_ALBUM_ARTIST);
            }
            snap.album = callHString(mp, MP_GET_ALBUM_TITLE);
            release(mp);
         }
         Pointer tl = callObject(session, SESSION_GET_TIMELINE);
         if (tl != null) {
            long start = callTimeSpan(tl, TL_GET_START_TIME);
            long end = callTimeSpan(tl, TL_GET_END_TIME);
            long pos = callTimeSpan(tl, TL_GET_POSITION);
            snap.positionMs = pos / 10000L;
            long dur = end - start;
            if (dur > 0) {
               snap.durationMs = dur / 10000L;
            }
            release(tl);
         }
         Pointer pi = callObject(session, SESSION_GET_PLAYBACK_INFO);
         if (pi != null) {
            int status = callIntOut(pi, PI_GET_PLAYBACK_STATUS);
            snap.playing = (status == PLAYBACK_STATUS_PLAYING);
            release(pi);
         }
         snap.sourceApp = callHString(session, SESSION_GET_SOURCE_APP_DISPLAY);
         release(session);
         available = Boolean.TRUE;
         return snap;
      } catch (Throwable ignored) {
         lastFailureTime = System.currentTimeMillis();
         available = null;
      }
      return null;
   }

   private static Pointer callObject(Pointer obj, int vtableIndex) {
      if (obj == null) {
         return null;
      }
      try {
         Pointer vtable = obj.getPointer(0);
         Pointer fn = vtable.getPointer(vtableIndex * POINTER_SIZE);
         Function f = Function.getFunction(fn);
         PointerByReference out = new PointerByReference();
         int hr = f.invokeInt(new Object[]{obj, out});
         if (hr == S_OK && out.getValue() != null) {
            return out.getValue();
         }
      } catch (Throwable ignored) {
      }
      return null;
   }

   private static String callHString(Pointer obj, int vtableIndex) {
      if (obj == null) {
         return null;
      }
      try {
         Pointer vtable = obj.getPointer(0);
         Pointer fn = vtable.getPointer(vtableIndex * POINTER_SIZE);
         Function f = Function.getFunction(fn);
         PointerByReference out = new PointerByReference();
         int hr = f.invokeInt(new Object[]{obj, out});
         if (hr != S_OK || out.getValue() == null) {
            return null;
         }
         String s = readHString(out.getValue());
         WindowsDeleteString(out.getValue());
         return s;
      } catch (Throwable ignored) {
      }
      return null;
   }

   private static long callTimeSpan(Pointer obj, int vtableIndex) {
      if (obj == null) {
         return 0L;
      }
      try {
         Pointer vtable = obj.getPointer(0);
         Pointer fn = vtable.getPointer(vtableIndex * POINTER_SIZE);
         Function f = Function.getFunction(fn);
         long[] out = new long[1];
         f.invoke(new Object[]{obj, out});
         return out[0];
      } catch (Throwable ignored) {
      }
      return 0L;
   }

   private static int callIntOut(Pointer obj, int vtableIndex) {
      if (obj == null) {
         return 0;
      }
      try {
         Pointer vtable = obj.getPointer(0);
         Pointer fn = vtable.getPointer(vtableIndex * POINTER_SIZE);
         Function f = Function.getFunction(fn);
         int[] out = new int[1];
         f.invoke(new Object[]{obj, out});
         return out[0];
      } catch (Throwable ignored) {
      }
      return 0;
   }

   private static String readHString(Pointer hstr) {
      if (hstr == null) {
         return null;
      }
      try {
         PointerByReference lenOut = new PointerByReference();
         Pointer buf = WindowsGetStringRawBuffer(hstr, lenOut);
         if (buf == null) {
            return null;
         }
         long len = 0;
         if (lenOut.getValue() != null) {
            len = lenOut.getValue().getLong(0);
         }
         if (len <= 0) {
            return "";
         }
         byte[] data = buf.getByteArray(0, (int)(len * 2));
         return new String(data, StandardCharsets.UTF_16LE);
      } catch (Throwable ignored) {
      }
      return null;
   }

   private static void release(Pointer p) {
      if (p == null) {
         return;
      }
      try {
         Pointer vtable = p.getPointer(0);
         Pointer releaseFn = vtable.getPointer(2 * POINTER_SIZE);
         Function f = Function.getFunction(releaseFn);
         f.invoke(new Object[]{p});
      } catch (Throwable ignored) {
      }
   }

   static final class Snapshot {
      String title;
      String artist;
      String album;
      String sourceApp;
      long positionMs;
      long durationMs;
      boolean playing;
   }
}