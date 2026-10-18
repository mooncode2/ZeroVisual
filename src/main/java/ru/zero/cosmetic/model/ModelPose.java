package ru.zero.cosmetic.model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * A resolved pose (rest + animation) for a {@link BedrockModel}. Flattening converts the
 * bone hierarchy into a flat list of textured quads in model space (block units).
 */
public final class ModelPose {
   private static final float[] ZERO = { 0.0F, 0.0F, 0.0F };
   private static final float[] ONE = { 1.0F, 1.0F, 1.0F };

   private final Map<String, float[]> rotations = new HashMap<>();
   private final Map<String, float[]> positions = new HashMap<>();
   private final Map<String, float[]> scales = new HashMap<>();
   private final Set<String> hidden = new HashSet<>();

   public void hide(String bone) {
      this.hidden.add(bone);
   }

   public boolean isHidden(String bone) {
      return this.hidden.contains(bone);
   }

   /** Builds a pose directly from a composed {@link MeleePose}, hiding the given bones. */
   public static ModelPose fromMeleePose(MeleePose meleePose, java.util.Collection<String> hiddenBones) {
      ModelPose pose = new ModelPose();
      if (meleePose != null) {
         pose.rotations.putAll(meleePose.rotation);
         pose.positions.putAll(meleePose.position);
         pose.scales.putAll(meleePose.scale);
      }

      if (hiddenBones != null) {
         pose.hidden.addAll(hiddenBones);
      }

      return pose;
   }

   public static ModelPose fromAnimation(ModelAnimation animation, float time) {
      ModelPose pose = new ModelPose();
      if (animation == null) {
         return pose;
      }

      float t = animation.time(time);
      for (String boneName : animation.bones()) {
         float[] rotation = { 0.0F, 0.0F, 0.0F };
         float[] position = { 0.0F, 0.0F, 0.0F };
         float[] scale = { 1.0F, 1.0F, 1.0F };
         boolean changed = false;
         if (!isZero(rotation)) {
            changed = true;
         }

         animation.applyRotation(boneName, t, rotation);
         animation.applyPosition(boneName, t, position);
         animation.applyScale(boneName, t, scale);
         pose.rotations.put(boneName, rotation);
         pose.positions.put(boneName, position);
         pose.scales.put(boneName, scale);
      }

      return pose;
   }

   private static boolean isZero(float[] value) {
      return value[0] == 0.0F && value[1] == 0.0F && value[2] == 0.0F;
   }

   public List<ModelQuad> flatten(BedrockModel model) {
      FloatCollector collector = new FloatCollector(model);
      for (BedrockModel.Bone root : model.roots()) {
         this.flattenBone(root, new Matrix4f(), collector);
      }

      return collector.quads;
   }

   private void flattenBone(BedrockModel.Bone bone, Matrix4f parentMatrix, FloatCollector collector) {
      if (this.hidden.contains(bone.name)) {
         return;
      }

      float[] extraRotation = this.rotations.getOrDefault(bone.name, ZERO);
      float[] extraPosition = this.positions.getOrDefault(bone.name, ZERO);
      float[] scale = this.scales.getOrDefault(bone.name, ONE);

      Matrix4f matrix = new Matrix4f(parentMatrix);
      if (collector.model.mirrored) {
         // EvoVis buildWorld: translate(-px, py, pz) then pivot rotation with (rest - anim).
         float rx = bone.rotation[0] - extraRotation[0];
         float ry = bone.rotation[1] - extraRotation[1];
         float rz = bone.rotation[2] + extraRotation[2];
         matrix.translate(-extraPosition[0] * BedrockModel.UNIT, extraPosition[1] * BedrockModel.UNIT,
               extraPosition[2] * BedrockModel.UNIT);
         matrix.translate(bone.pivot[0], bone.pivot[1], bone.pivot[2]);
         matrix.rotateZYX((float) Math.toRadians(rz), (float) Math.toRadians(ry), (float) Math.toRadians(rx));
         matrix.scale(scale[0], scale[1], scale[2]);
         matrix.translate(-bone.pivot[0], -bone.pivot[1], -bone.pivot[2]);
      } else {
         float rx = bone.rotation[0] + extraRotation[0];
         float ry = bone.rotation[1] + extraRotation[1];
         float rz = bone.rotation[2] + extraRotation[2];
         matrix.translate(bone.pivot[0], bone.pivot[1], bone.pivot[2]);
         matrix.rotateZYX((float) Math.toRadians(rz), (float) Math.toRadians(ry), (float) Math.toRadians(rx));
         matrix.translate(extraPosition[0] * BedrockModel.UNIT, extraPosition[1] * BedrockModel.UNIT,
               extraPosition[2] * BedrockModel.UNIT);
         matrix.scale(scale[0], scale[1], scale[2]);
         matrix.translate(-bone.pivot[0], -bone.pivot[1], -bone.pivot[2]);
      }

      for (BedrockModel.Cube cube : bone.cubes) {
         this.flattenCube(cube, matrix, collector);
      }

      for (BedrockModel.Bone child : bone.children) {
         this.flattenBone(child, matrix, collector);
      }
   }

   private void flattenCube(BedrockModel.Cube cube, Matrix4f boneMatrix, FloatCollector collector) {
      Matrix4f cubeMatrix = new Matrix4f(boneMatrix);
      if (cube.pivot[0] != 0.0F || cube.pivot[1] != 0.0F || cube.pivot[2] != 0.0F
            || cube.rotation[0] != 0.0F || cube.rotation[1] != 0.0F || cube.rotation[2] != 0.0F) {
         cubeMatrix.translate(cube.pivot[0], cube.pivot[1], cube.pivot[2]);
         cubeMatrix.rotateZYX((float) Math.toRadians(cube.rotation[2]), (float) Math.toRadians(cube.rotation[1]),
               (float) Math.toRadians(cube.rotation[0]));
         cubeMatrix.translate(-cube.pivot[0], -cube.pivot[1], -cube.pivot[2]);
      }

      for (BedrockModel.Face face : cube.faces) {
         ModelQuad quad = new ModelQuad(4);
         for (int i = 0; i < 4; i++) {
            Vector3f point = new Vector3f(face.pos[i][0], face.pos[i][1], face.pos[i][2]);
            cubeMatrix.transformPosition(point);
            quad.pos[i][0] = point.x;
            quad.pos[i][1] = point.y;
            quad.pos[i][2] = point.z;
            quad.uv[i][0] = face.uv[i][0];
            quad.uv[i][1] = face.uv[i][1];
         }

         collector.quads.add(quad);
      }
   }

   private static final class FloatCollector {
      final BedrockModel model;
      final java.util.List<ModelQuad> quads = new java.util.ArrayList<>();

      FloatCollector(BedrockModel model) {
         this.model = model;
      }
   }
}
