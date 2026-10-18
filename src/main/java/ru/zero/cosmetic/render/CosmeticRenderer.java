package ru.zero.cosmetic.render;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import ru.zero.cosmetic.Cosmetic;
import ru.zero.cosmetic.CosmeticRegistry;
import ru.zero.cosmetic.model.BedrockModel;
import ru.zero.cosmetic.model.MeleePose;
import ru.zero.cosmetic.model.ModelAnimation;
import ru.zero.cosmetic.model.ModelPose;
import ru.zero.cosmetic.model.ModelQuad;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.texture.TextureLoader;

/**
 * Renders Bedrock models as a software-orthographic projection of textured quads through
 * {@link Renderer2D}'s affine transform stack. No 3D pipeline is required.
 */
public final class CosmeticRenderer {
   private static final Map<String, BedrockModel> MODELS = new HashMap<>();
   private static final Map<String, ModelAnimation> ANIMATIONS = new HashMap<>();
   private static final Map<String, Set<String>> CLIP_NAMES = new HashMap<>();
   private static final Map<String, Integer> TEXTURES = new HashMap<>();
   private static final Map<String, CachedQuads> FLATTEN_CACHE = new HashMap<>();
   private static final float POSE_INTERVAL = 1.0F / 20.0F;
   private static final long START = System.nanoTime();

   private CosmeticRenderer() {
   }

   public static float time() {
      return (System.nanoTime() - START) / 1.0E9F;
   }

   public static BedrockModel model(Cosmetic cosmetic) {
      if (cosmetic == null || cosmetic.geometry == null) {
         return null;
      }

      boolean mirrored = cosmetic.kind == Cosmetic.Kind.MELEE;
      return MODELS.computeIfAbsent(cosmetic.geometry, path -> {
         try (InputStream in = CosmeticRegistry.class.getClassLoader().getResourceAsStream(path)) {
            return in == null ? null : BedrockModel.parse(in, mirrored);
         } catch (Exception e) {
            return null;
         }
      });
   }

   public static ModelAnimation animation(Cosmetic cosmetic) {
      return animation(cosmetic, null);
   }

   /** Loads a specific clip from the cosmetic's animation file, or the first one when {@code clip} is null. */
   public static ModelAnimation animation(Cosmetic cosmetic, String clip) {
      if (cosmetic == null || cosmetic.animation == null) {
         return null;
      }

      String key = cosmetic.animation + '|' + (clip == null ? "" : clip);
      return ANIMATIONS.computeIfAbsent(key, ignored -> {
         try (InputStream in = CosmeticRegistry.class.getClassLoader().getResourceAsStream(cosmetic.animation)) {
            return in == null ? null : ModelAnimation.parse(in, clip);
         } catch (Exception e) {
            return null;
         }
      });
   }

   /** True when the cosmetic's animation file defines a clip with the given name. */
   public static boolean hasClip(Cosmetic cosmetic, String clip) {
      if (cosmetic == null || cosmetic.animation == null || clip == null) {
         return false;
      }

      return clipNames(cosmetic.animation).contains(clip);
   }

   private static Set<String> clipNames(String path) {
      return CLIP_NAMES.computeIfAbsent(path, key -> {
         try (InputStream in = CosmeticRegistry.class.getClassLoader().getResourceAsStream(key)) {
            if (in == null) {
               return java.util.Collections.emptySet();
            }

            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject animations;
            if (root.has("animations") && root.get("animations").isJsonObject()) {
               animations = root.getAsJsonObject("animations");
            } else if (root.has("clips") && root.get("clips").isJsonObject()) {
               animations = root.getAsJsonObject("clips");
            } else {
               animations = root;
            }

            return new HashSet<>(animations.keySet());
         } catch (Exception e) {
            return java.util.Collections.emptySet();
         }
      });
   }

   /**
    * Flattens the model to posed quads, caching the result per (geometry, animation) and sampling
    * animation poses at a fixed interval so the per-frame cost stays constant. Callers must not
    * mutate the returned quads.
    */
   public static List<ModelQuad> flattened(Cosmetic cosmetic, float timeSeconds) {
      BedrockModel model = model(cosmetic);
      if (model == null) {
         return java.util.Collections.emptyList();
      }

      ModelAnimation anim = animation(cosmetic);
      String key = (cosmetic.geometry == null ? "" : cosmetic.geometry) + '|'
            + (cosmetic.animation == null ? "" : cosmetic.animation);
      float bucket = anim == null ? -1.0F : Math.round(timeSeconds / POSE_INTERVAL);
      CachedQuads cached = FLATTEN_CACHE.get(key);
      if (cached != null && cached.bucket == bucket) {
         return cached.quads;
      }

      ModelPose pose = ModelPose.fromAnimation(anim, anim != null ? anim.time(timeSeconds) : 0.0F);
      List<ModelQuad> quads = pose.flatten(model);
      FLATTEN_CACHE.put(key, new CachedQuads(bucket, quads));
      return quads;
   }

   /**
    * Flattens the model using a specific animation clip (or rest pose when {@code clip} is null),
    * caching per (geometry, clip, hideHands) and sampling at a fixed interval.
    */
   public static List<ModelQuad> poseQuads(Cosmetic cosmetic, String clip, float timeSeconds, boolean hideHands) {
      BedrockModel model = model(cosmetic);
      if (model == null) {
         return java.util.Collections.emptyList();
      }

      ModelAnimation anim = clip == null ? null : animation(cosmetic, clip);
      if (clip != null && anim == null) {
         anim = null;
      }

      String key = (cosmetic.geometry == null ? "" : cosmetic.geometry) + '|' + (clip == null ? "" : clip)
            + (hideHands ? "|h" : "");
      float bucket = anim == null ? -1.0F : Math.round(timeSeconds / POSE_INTERVAL);
      CachedQuads cached = FLATTEN_CACHE.get(key);
      if (cached != null && cached.bucket == bucket) {
         return cached.quads;
      }

      ModelPose pose = ModelPose.fromAnimation(anim, anim != null ? anim.time(timeSeconds) : 0.0F);
      if (hideHands) {
         pose.hide("righthand_pos");
         pose.hide("lefthand_pos");
      }

      List<ModelQuad> quads = pose.flatten(model);
      FLATTEN_CACHE.put(key, new CachedQuads(bucket, quads));
      return quads;
   }

   /** Rest-pose flattened quads (no animation) with the arm anchor bones hidden, cached per geometry. */
   public static List<ModelQuad> restQuads(Cosmetic cosmetic) {
      return poseQuads(cosmetic, null, 0.0F, true);
   }

   /**
    * Flattens the model using an already-composed {@link MeleePose} (the animator's output). The
    * arm anchor bones plus any variant-hidden bones are excluded.
    */
   public static List<ModelQuad> meleeQuads(Cosmetic cosmetic, MeleePose pose, Set<String> hiddenBones) {
      BedrockModel model = model(cosmetic);
      if (model == null || pose == null) {
         return java.util.Collections.emptyList();
      }

      ModelPose modelPose = ModelPose.fromMeleePose(pose, hiddenBones);
      modelPose.hide("righthand_pos");
      modelPose.hide("lefthand_pos");
      return modelPose.flatten(model);
   }

   /** Resource-pack identifier of the cosmetic texture, usable with vanilla render layers. */
   public static net.minecraft.util.Identifier textureId(Cosmetic cosmetic) {
      if (cosmetic == null || cosmetic.texture == null) {
         return null;
      }

      String path = cosmetic.texture;
      if (path.startsWith("assets/zero/")) {
         path = path.substring("assets/zero/".length());
      }

      return net.minecraft.util.Identifier.of("zero", path);
   }

   public static int texture(Cosmetic cosmetic) {
      if (cosmetic == null || cosmetic.texture == null) {
         return 0;
      }

      Integer cached = TEXTURES.get(cosmetic.texture);
      if (cached != null) {
         return cached;
      }

      int id = TextureLoader.load(cosmetic.texture);
      TEXTURES.put(cosmetic.texture, id);
      return id;
   }

   /**
    * Draws {@code cosmetic} centred at ({@code cx}, {@code cy}) with the largest dimension
    * roughly {@code sizePx} GUI units.
    */
   public static void renderPreview(Renderer2D renderer, Cosmetic cosmetic, float cx, float cy, float sizePx,
         float yawDeg, float pitchDeg, float time) {
      BedrockModel model = model(cosmetic);
      int texture = texture(cosmetic);
      if (model == null || texture <= 0) {
         return;
      }

      ModelPose pose = ModelPose.fromAnimation(animation(cosmetic), time);
      List<ModelQuad> quads = pose.flatten(model);
      if (quads.isEmpty()) {
         return;
      }

      float[] bounds = bounds(quads);
      float centerX = (bounds[0] + bounds[1]) * 0.5F;
      float centerY = (bounds[2] + bounds[3]) * 0.5F;
      float centerZ = (bounds[4] + bounds[5]) * 0.5F;
      float extent = Math.max(Math.max(bounds[1] - bounds[0], bounds[3] - bounds[2]), bounds[5] - bounds[4]);
      if (!Float.isFinite(extent) || extent <= 1.0E-4F) {
         extent = 1.0F;
      }

      float scale = sizePx / extent;
      float yaw = (float) Math.toRadians(yawDeg);
      float pitch = (float) Math.toRadians(pitchDeg);
      float cosY = (float) Math.cos(yaw);
      float sinY = (float) Math.sin(yaw);
      float cosP = (float) Math.cos(pitch);
      float sinP = (float) Math.sin(pitch);

      List<ProjectedQuad> projected = new ArrayList<>(quads.size());
      for (ModelQuad quad : quads) {
         ProjectedQuad p = project(quad, model, centerX, centerY, centerZ, scale, cosY, sinY, cosP, sinP, cx, cy);
         if (p != null) {
            projected.add(p);
         }
      }

      projected.sort(Comparator.comparingDouble(q -> q.depth));

      float[] original = renderer.getTransformStack().current().clone();
      for (ProjectedQuad p : projected) {
         float[] m = { p.x1, p.x2, p.x3, p.y1, p.y2, p.y3, 0.0F, 0.0F, 1.0F };
         float[] composed = compose(original, m);
         renderer.getTransformStack().replaceTop(composed);
         renderer.drawRgbaTextureWithUV(texture, 0.0F, 0.0F, 1.0F, 1.0F, p.u0, p.v0, p.u1, p.v1);
      }

      renderer.getTransformStack().replaceTop(original);
   }

   private static ProjectedQuad project(ModelQuad quad, BedrockModel model, float centerX, float centerY, float centerZ,
         float scale, float cosY, float sinY, float cosP, float sinP, float cx, float cy) {
      float[] px = new float[4];
      float[] py = new float[4];
      float[] u = new float[4];
      float[] v = new float[4];
      float depthSum = 0.0F;
      float uMin = Float.MAX_VALUE;
      float uMax = -Float.MAX_VALUE;
      float vMin = Float.MAX_VALUE;
      float vMax = -Float.MAX_VALUE;

      for (int i = 0; i < 4; i++) {
         float x = quad.pos[i][0] - centerX;
         float y = quad.pos[i][1] - centerY;
         float z = quad.pos[i][2] - centerZ;
         float viewX = x * cosY + z * sinY;
         float viewZ = -x * sinY + z * cosY;
         float viewY = y * cosP - viewZ * sinP;
         float viewZ2 = y * sinP + viewZ * cosP;
         px[i] = cx + viewX * scale;
         py[i] = cy - viewY * scale;
         u[i] = clamp01(quad.uv[i][0] / model.textureWidth);
         v[i] = clamp01(quad.uv[i][1] / model.textureHeight);
         depthSum += viewZ2;
         uMin = Math.min(uMin, u[i]);
         uMax = Math.max(uMax, u[i]);
         vMin = Math.min(vMin, v[i]);
         vMax = Math.max(vMax, v[i]);
      }

      for (int i = 0; i < 4; i++) {
         if (!Float.isFinite(px[i]) || !Float.isFinite(py[i])) {
            return null;
         }
      }

      int a = -1;
      int b = -1;
      int d = -1;
      for (int i = 0; i < 4; i++) {
         boolean uLow = Math.abs(u[i] - uMin) < 1.0E-4F;
         boolean uHigh = Math.abs(u[i] - uMax) < 1.0E-4F;
         boolean vLow = Math.abs(v[i] - vMin) < 1.0E-4F;
         boolean vHigh = Math.abs(v[i] - vMax) < 1.0E-4F;
         if (uLow && vLow) {
            a = i;
         } else if (uHigh && vLow) {
            b = i;
         } else if (uLow && vHigh) {
            d = i;
         }
      }

      if (a < 0 || b < 0 || d < 0) {
         return null;
      }

      ProjectedQuad result = new ProjectedQuad();
      result.x1 = px[b] - px[a];
      result.x2 = px[d] - px[a];
      result.x3 = px[a];
      result.y1 = py[b] - py[a];
      result.y2 = py[d] - py[a];
      result.y3 = py[a];
      result.u0 = uMin;
      result.v0 = vMin;
      result.u1 = uMax;
      result.v1 = vMax;
      result.depth = depthSum / 4.0F;
      return result;
   }

   private static float[] compose(float[] a, float[] b) {
      float[] out = new float[9];
      out[0] = a[0] * b[0] + a[1] * b[3] + a[2] * b[6];
      out[1] = a[0] * b[1] + a[1] * b[4] + a[2] * b[7];
      out[2] = a[0] * b[2] + a[1] * b[5] + a[2] * b[8];
      out[3] = a[3] * b[0] + a[4] * b[3] + a[5] * b[6];
      out[4] = a[3] * b[1] + a[4] * b[4] + a[5] * b[7];
      out[5] = a[3] * b[2] + a[4] * b[5] + a[5] * b[8];
      out[6] = a[6] * b[0] + a[7] * b[3] + a[8] * b[6];
      out[7] = a[6] * b[1] + a[7] * b[4] + a[8] * b[7];
      out[8] = a[6] * b[2] + a[7] * b[5] + a[8] * b[8];
      return out;
   }

   private static float[] bounds(List<ModelQuad> quads) {
      float[] b = { Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE };
      for (ModelQuad quad : quads) {
         for (float[] p : quad.pos) {
            b[0] = Math.min(b[0], p[0]);
            b[1] = Math.max(b[1], p[0]);
            b[2] = Math.min(b[2], p[1]);
            b[3] = Math.max(b[3], p[1]);
            b[4] = Math.min(b[4], p[2]);
            b[5] = Math.max(b[5], p[2]);
         }
      }

      return b;
   }

   private static float clamp01(float value) {
      return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
   }

   private static final class CachedQuads {
      final float bucket;
      final List<ModelQuad> quads;

      CachedQuads(float bucket, List<ModelQuad> quads) {
         this.bucket = bucket;
         this.quads = quads;
      }
   }

   private static final class ProjectedQuad {
      float x1;
      float x2;
      float x3;
      float y1;
      float y2;
      float y3;
      float u0;
      float v0;
      float u1;
      float v1;
      float depth;
   }
}
