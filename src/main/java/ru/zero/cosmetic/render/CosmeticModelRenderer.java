package ru.zero.cosmetic.render;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import ru.zero.cosmetic.Cosmetic;
import ru.zero.cosmetic.model.BedrockModel;
import ru.zero.cosmetic.model.ModelQuad;
import ru.zero.util.render.world.WorldRenderer;

/**
 * Draws a cosmetic Bedrock model as camera-relative textured quads through
 * {@link WorldRenderer#drawTexturedQuad}, applying a yaw/pitch/roll orientation around
 * an anchor point. Positions are already in block units.
 */
@Environment(EnvType.CLIENT)
public final class CosmeticModelRenderer {
   private static final Map<String, Identifier> TEXTURE_IDS = new HashMap<>();

   private CosmeticModelRenderer() {
   }

   public static boolean draw(WorldRenderer renderer, Cosmetic cosmetic, double anchorX, double anchorY, double anchorZ,
         float yawDeg, float pitchDeg, float rollDeg, float scale, float animTime) {
      BedrockModel model = CosmeticRenderer.model(cosmetic);
      if (model == null) {
         return false;
      }

      Identifier texture = textureId(cosmetic);
      if (texture == null) {
         return false;
      }

      List<ModelQuad> quads = CosmeticRenderer.flattened(cosmetic, animTime);
      if (quads.isEmpty()) {
         return false;
      }

      float yaw = (float) Math.toRadians(yawDeg);
      float pitch = (float) Math.toRadians(pitchDeg);
      float roll = (float) Math.toRadians(rollDeg);
      float cy = (float) Math.cos(yaw);
      float sy = (float) Math.sin(yaw);
      float cp = (float) Math.cos(pitch);
      float sp = (float) Math.sin(pitch);
      float cr = (float) Math.cos(roll);
      float sr = (float) Math.sin(roll);

      for (ModelQuad quad : quads) {
         Vec3d[] points = new Vec3d[4];
         for (int i = 0; i < 4; i++) {
            float x = quad.pos[i][0] * scale;
            float y = quad.pos[i][1] * scale;
            float z = quad.pos[i][2] * scale;
            float rx = x * cr - y * sr;
            float ry = x * sr + y * cr;
            float py = ry * cp - z * sp;
            float pz = ry * sp + z * cp;
            float yx = rx * cy + pz * sy;
            float yz = -rx * sy + pz * cy;
            points[i] = new Vec3d(anchorX + yx, anchorY + py, anchorZ + yz);
         }

         renderer.drawTexturedQuad(texture, points[0], points[1], points[2], points[3],
               u(quad, model, 0), v(quad, model, 0),
               u(quad, model, 1), v(quad, model, 1),
               u(quad, model, 2), v(quad, model, 2),
               u(quad, model, 3), v(quad, model, 3),
               0xFFFFFFFF, true);
      }

      return true;
   }

   private static float u(ModelQuad quad, BedrockModel model, int i) {
      return quad.uv[i][0] / model.textureWidth;
   }

   private static float v(ModelQuad quad, BedrockModel model, int i) {
      return quad.uv[i][1] / model.textureHeight;
   }

   private static Identifier textureId(Cosmetic cosmetic) {
      if (cosmetic.texture == null) {
         return null;
      }

      return TEXTURE_IDS.computeIfAbsent(cosmetic.texture, path -> {
         String relative = path.startsWith("assets/zero/") ? path.substring("assets/zero/".length()) : path;
         return Identifier.of("zero", relative);
      });
   }
}
