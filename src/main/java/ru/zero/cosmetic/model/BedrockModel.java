package ru.zero.cosmetic.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser and in-memory representation of a Bedrock / Blockbench geometry file
 * ({@code *.geo.json}). Coordinates are converted to block units (1/16) on parse.
 */
public final class BedrockModel {
   public static final float UNIT = 1.0F / 16.0F;

   private static final String[] FACE_NAMES = { "north", "south", "east", "west", "up", "down" };

   /** Normalised face corner offsets (fractions of the cube size) in Bedrock vertex order. */
   private static final float[][][] FACE_CORNERS = {
      // north
      { { 1, 1, 0 }, { 0, 1, 0 }, { 0, 0, 0 }, { 1, 0, 0 } },
      // south
      { { 0, 1, 1 }, { 1, 1, 1 }, { 1, 0, 1 }, { 0, 0, 1 } },
      // east
      { { 1, 1, 1 }, { 1, 1, 0 }, { 1, 0, 0 }, { 1, 0, 1 } },
      // west
      { { 0, 1, 0 }, { 0, 1, 1 }, { 0, 0, 1 }, { 0, 0, 0 } },
      // up
      { { 0, 0, 0 }, { 1, 0, 0 }, { 1, 0, 1 }, { 0, 0, 1 } },
      // down
      { { 0, 1, 1 }, { 1, 1, 1 }, { 1, 1, 0 }, { 0, 1, 0 } }
   };

   /**
    * Face corner offsets matching EvoVis' parser/baker vertex order. Used when
    * {@link #mirrored} conversions are enabled so geometry and UVs line up exactly.
    */
   private static final float[][][] EVOVIS_CORNERS = {
      // north
      { { 0, 1, 0 }, { 1, 1, 0 }, { 1, 0, 0 }, { 0, 0, 0 } },
      // south
      { { 1, 1, 1 }, { 0, 1, 1 }, { 0, 0, 1 }, { 1, 0, 1 } },
      // east
      { { 1, 1, 0 }, { 1, 1, 1 }, { 1, 0, 1 }, { 1, 0, 0 } },
      // west
      { { 0, 1, 1 }, { 0, 1, 0 }, { 0, 0, 0 }, { 0, 0, 1 } },
      // up
      { { 0, 1, 0 }, { 1, 1, 0 }, { 1, 1, 1 }, { 0, 1, 1 } },
      // down
      { { 0, 0, 1 }, { 1, 0, 1 }, { 1, 0, 0 }, { 0, 0, 0 } }
   };

   public final float textureWidth;
   public final float textureHeight;
   /** When true, applies the EvoVis axis conventions (mirrored X, negated X/Y rotations). */
   public final boolean mirrored;
   private final Map<String, Bone> bones = new LinkedHashMap<>();
   private final List<Bone> roots = new ArrayList<>();

   private BedrockModel(float textureWidth, float textureHeight, boolean mirrored) {
      this.textureWidth = textureWidth > 0.0F ? textureWidth : 64.0F;
      this.textureHeight = textureHeight > 0.0F ? textureHeight : 64.0F;
      this.mirrored = mirrored;
   }

   public List<Bone> roots() {
      return this.roots;
   }

   public Map<String, Bone> bones() {
      return this.bones;
   }

   public Bone bone(String name) {
      return this.bones.get(name);
   }

   private void finish() {
      for (Bone bone : this.bones.values()) {
         if (bone.parent != null) {
            Bone parent = this.bones.get(bone.parent);
            if (parent != null) {
               parent.children.add(bone);
               continue;
            }
         }

         this.roots.add(bone);
      }
   }

   public static BedrockModel parse(InputStream in) {
      return parse(in, false);
   }

   public static BedrockModel parse(InputStream in, boolean mirrored) {
      JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
      JsonArray geometries = root.has("minecraft:geometry")
            ? root.getAsJsonArray("minecraft:geometry")
            : root.getAsJsonArray("geometry");
      JsonObject geometry = geometries.get(0).getAsJsonObject();
      JsonObject description = geometry.getAsJsonObject("description");

      float texWidth = description.has("texture_width") ? description.get("texture_width").getAsFloat() : 64.0F;
      float texHeight = description.has("texture_height") ? description.get("texture_height").getAsFloat() : 64.0F;
      BedrockModel model = new BedrockModel(texWidth, texHeight, mirrored);

      JsonArray bonesArray = geometry.getAsJsonArray("bones");
      if (bonesArray != null) {
         for (JsonElement element : bonesArray) {
            JsonObject boneObject = element.getAsJsonObject();
            String name = boneObject.get("name").getAsString();
            Bone bone = new Bone(name);
            if (boneObject.has("parent")) {
               bone.parent = boneObject.get("parent").getAsString();
            }

            bone.pivot = readPivot(boneObject.getAsJsonArray("pivot"), mirrored);
            bone.rotation = readRotation(boneObject.getAsJsonArray("rotation"), mirrored);
            if (boneObject.has("cubes")) {
               for (JsonElement cubeElement : boneObject.getAsJsonArray("cubes")) {
                  bone.cubes.add(parseCube(cubeElement.getAsJsonObject(), mirrored));
               }
            }

            model.bones.put(name, bone);
         }
      }

      model.finish();
      return model;
   }

   private static Cube parseCube(JsonObject cube, boolean mirrored) {
      Cube result = new Cube();
      result.origin = readVec(cube.getAsJsonArray("origin"));
      result.size = readVec(cube.getAsJsonArray("size"));
      result.pivot = readPivot(cube.getAsJsonArray("pivot"), mirrored);
      result.rotation = readRotation(cube.getAsJsonArray("rotation"), mirrored);
      if (cube.has("inflate")) {
         result.inflate = cube.get("inflate").getAsFloat();
      }

      if (cube.has("mirror")) {
         result.mirror = cube.get("mirror").getAsBoolean();
      }

      JsonElement uv = cube.get("uv");
      if (uv != null && uv.isJsonObject()) {
         JsonObject uvObject = uv.getAsJsonObject();
         for (int i = 0; i < FACE_NAMES.length; i++) {
            String faceName = FACE_NAMES[i];
            if (!uvObject.has(faceName)) {
               continue;
            }

            JsonObject faceObject = uvObject.getAsJsonObject(faceName);
            float u = faceObject.getAsJsonArray("uv").get(0).getAsFloat();
            float v = faceObject.getAsJsonArray("uv").get(1).getAsFloat();
            float uw = faceObject.has("uv_size")
                  ? faceObject.getAsJsonArray("uv_size").get(0).getAsFloat()
                  : result.size[0];
            float vh = faceObject.has("uv_size")
                  ? faceObject.getAsJsonArray("uv_size").get(1).getAsFloat()
                  : result.size[1];
            result.faces.add(buildFace(i, result, u, v, uw, vh, mirrored));
         }
      } else if (uv != null && uv.isJsonArray()) {
         float u = uv.getAsJsonArray().get(0).getAsFloat();
         float v = uv.getAsJsonArray().get(1).getAsFloat();
         addBoxFaces(result, u, v, mirrored);
      }

      return result;
   }

   private static void addBoxFaces(Cube cube, float u, float v, boolean mirrored) {
      float w = cube.size[0];
      float h = cube.size[1];
      float d = cube.size[2];
      // Vanilla block-unwrap layout.
      addFace(cube, 0, u + d, v + d, w, h, mirrored);              // north
      addFace(cube, 1, u + d + w, v + d, w, h, mirrored);          // south
      addFace(cube, 2, u, v + d, d, h, mirrored);                  // east
      addFace(cube, 3, u + d + w + d, v + d, d, h, mirrored);      // west
      addFace(cube, 4, u + d, v, w, d, mirrored);                  // up
      addFace(cube, 5, u + d + w + d, v, w, d, mirrored);          // down
   }

   private static void addFace(Cube cube, int index, float u, float v, float uw, float vh, boolean mirrored) {
      if (uw == 0.0F || vh == 0.0F) {
         return;
      }

      cube.faces.add(buildFace(index, cube, u, v, uw, vh, mirrored));
   }

   private static Face buildFace(int index, Cube cube, float u, float v, float uw, float vh, boolean mirrored) {
      float inflate = cube.inflate * UNIT;
      float sx = cube.size[0] + inflate * 2.0F;
      float sy = cube.size[1] + inflate * 2.0F;
      float sz = cube.size[2] + inflate * 2.0F;
      float ox = cube.origin[0] - inflate;
      float oy = cube.origin[1] - inflate;
      float oz = cube.origin[2] - inflate;
      if (mirrored) {
         // EvoVis mirrors the cube extent on X: [-(originX+sizeX), -originX] (+inflate).
         ox = -(ox + sx);
      }

      float[] size = { sx, sy, sz };

      Face face = new Face(FACE_NAMES[index]);
      float[][] corners = mirrored ? EVOVIS_CORNERS[index] : FACE_CORNERS[index];
      // UV corners paired with the vertex order above.
      float[][] uvCorners = mirrored
            ? new float[][] { { u, v }, { u + uw, v }, { u + uw, v + vh }, { u, v + vh } }
            : new float[][] { { u + uw, v }, { u, v }, { u, v + vh }, { u + uw, v + vh } };
      for (int corner = 0; corner < 4; corner++) {
         float px = ox + corners[corner][0] * size[0];
         float py = oy + corners[corner][1] * size[1];
         float pz = oz + corners[corner][2] * size[2];
          face.pos[corner][0] = px;
          face.pos[corner][1] = py;
          face.pos[corner][2] = pz;
          face.uv[corner][0] = uvCorners[corner][0];
          face.uv[corner][1] = uvCorners[corner][1];
      }

      return face;
   }

   private static float[] readVec(JsonArray array) {
      if (array == null || array.size() < 3) {
         return new float[] { 0.0F, 0.0F, 0.0F };
      }

      return new float[] { array.get(0).getAsFloat() * UNIT, array.get(1).getAsFloat() * UNIT,
            array.get(2).getAsFloat() * UNIT };
   }

   private static float[] readPivot(JsonArray array, boolean mirrored) {
      float[] value = readVec(array);
      if (mirrored) {
         return new float[] { -value[0], value[1], value[2] };
      }

      return value;
   }

   private static float[] readRotation(JsonArray array, boolean mirrored) {
      if (array == null || array.size() < 3) {
         return new float[] { 0.0F, 0.0F, 0.0F };
      }

      float x = array.get(0).getAsFloat();
      float y = array.get(1).getAsFloat();
      float z = array.get(2).getAsFloat();
      if (mirrored) {
         // EvoVis negates X and Y rotation, keeps Z.
         x = -x;
         y = -y;
      }

      return new float[] { x, y, z };
   }

   public static final class Bone {
      public final String name;
      public String parent;
      public float[] pivot = { 0.0F, 0.0F, 0.0F };
      public float[] rotation = { 0.0F, 0.0F, 0.0F };
      public final List<Cube> cubes = new ArrayList<>();
      public final List<Bone> children = new ArrayList<>();

      Bone(String name) {
         this.name = name;
      }

      public boolean isHiddenBy(String[] hidden) {
         return false;
      }
   }

   public static final class Cube {
      public float[] origin = { 0.0F, 0.0F, 0.0F };
      public float[] size = { 0.0F, 0.0F, 0.0F };
      public float[] pivot = { 0.0F, 0.0F, 0.0F };
      public float[] rotation = { 0.0F, 0.0F, 0.0F };
      public float inflate;
      public boolean mirror;
      public final List<Face> faces = new ArrayList<>();
   }

   public static final class Face {
      public final String name;
      public final float[][] pos = new float[4][3];
      public final float[][] uv = new float[4][2];

      Face(String name) {
         this.name = name;
      }
   }
}
