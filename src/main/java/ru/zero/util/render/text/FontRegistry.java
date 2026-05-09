package ru.zero.util.render.text;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.render.backends.gl.GlBackend;
import ru.zero.util.render.core.Renderer2D;

@Environment(EnvType.CLIENT)
public final class FontRegistry {
   private static final Map<String, MsdfFont> REGISTERED_FONTS = new HashMap<>();
   private static final Map<String, FontObject> FONT_OBJECTS = new HashMap<>();
   private static GlBackend backend;
   private static boolean backendConfigured = false;
   private static boolean rendererFontsInitialized = false;
   public static FontObject INTER_MEDIUM;
   public static FontObject ICONS;
   public static FontObject INTER_SEMIBOLD;

   private FontRegistry() {
   }

   public static synchronized void initialize(GlBackend glBackend, Renderer2D renderer) {
      configureBackend(glBackend);
      Objects.requireNonNull(renderer, "renderer");
      if (!rendererFontsInitialized) {
         renderer.registerTextRenderer(INTER_MEDIUM, createTextRenderer(INTER_MEDIUM));
         renderer.registerTextRenderer(ICONS, createTextRenderer(ICONS));
         renderer.registerTextRenderer(INTER_SEMIBOLD, createTextRenderer(INTER_SEMIBOLD));
         rendererFontsInitialized = true;
      }
   }

   public static synchronized FontObject register(String id, String jsonResourcePath, String textureResourcePath) {
      ensureBackendConfigured();
      Objects.requireNonNull(id, "id");
      Objects.requireNonNull(jsonResourcePath, "jsonResourcePath");
      Objects.requireNonNull(textureResourcePath, "textureResourcePath");
      if (REGISTERED_FONTS.containsKey(id)) {
         throw new IllegalStateException("Font already registered: " + id);
      } else {
         MsdfFont font = MsdfFont.load(backend, jsonResourcePath, textureResourcePath);
         REGISTERED_FONTS.put(id, font);
         FontObject fontObject = new FontObject(id);
         FONT_OBJECTS.put(id, fontObject);
         return fontObject;
      }
   }

   public static synchronized TextRenderer createTextRenderer(FontObject fontObject) {
      ensureBackendConfigured();
      MsdfFont msdfFont = resolve(fontObject);
      return new TextRenderer(backend, msdfFont);
   }

   public static synchronized float centeredBaselineOffset(FontObject fontObject, int codepoint, float size) {
      ensureBackendConfigured();
      if (fontObject != null && !(size <= 0.0F)) {
         MsdfFont font = resolve(fontObject);
         MsdfFont.Glyph glyph = font.glyph(codepoint);
         if (glyph != null && glyph.renderable) {
            float emSize = Math.max(1.0E-6F, font.emSize());
            float scale = size / emSize;
            return (glyph.planeTop + glyph.planeBottom) * 0.5F * scale;
         } else {
            return 0.0F;
         }
      } else {
         return 0.0F;
      }
   }

   public static synchronized FontObject get(String id) {
      ensureBackendConfigured();
      FontObject fontObject = FONT_OBJECTS.get(id);
      if (fontObject == null) {
         throw new IllegalArgumentException("Font not registered: " + id);
      } else {
         return fontObject;
      }
   }

   static synchronized MsdfFont resolve(FontObject fontObject) {
      ensureBackendConfigured();
      MsdfFont font = REGISTERED_FONTS.get(fontObject.id);
      if (font == null) {
         throw new IllegalStateException("Font not registered: " + fontObject.id);
      } else {
         return font;
      }
   }

   private static void configureBackend(GlBackend glBackend) {
      Objects.requireNonNull(glBackend, "backend");
      if (backendConfigured) {
         if (backend != glBackend) {
            throw new IllegalStateException("FontRegistry already initialized with a different backend instance");
         }
      } else {
         backend = glBackend;
         backendConfigured = true;
         registerBuiltinFonts();
      }
   }

   private static void registerBuiltinFonts() {
      INTER_MEDIUM = register("inter_medium", "assets/zero/fonts/medium.json", "assets/zero/fonts/medium.png");
      ICONS = register("icons", "assets/zero/fonts/icons.json", "assets/zero/fonts/icons.png");
      INTER_SEMIBOLD = register("inter_semibold", "assets/zero/fonts/semibold.json", "assets/zero/fonts/semibold.png");
   }

   private static void ensureBackendConfigured() {
      if (!backendConfigured || backend == null) {
         throw new IllegalStateException("FontRegistry.initialize(backend, renderer) must be called before use");
      }
   }
}
