package ru.zero.cosmetic.render;

import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.state.ArmedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import ru.zero.cosmetic.Cosmetic;
import ru.zero.cosmetic.CosmeticSlot;
import ru.zero.cosmetic.CosmeticState;
import ru.zero.cosmetic.model.BedrockModel;
import ru.zero.cosmetic.model.ModelQuad;
import ru.zero.util.render.world.WorldRenderLayers;

/**
 * Renders the equipped melee skin attached to the item pose, ported from EvoVis'
 * {@code ahz.renderThirdPerson} / {@code ahz.renderFirstPerson}. The geometry is submitted
 * through the vanilla render queue so it inherits the exact hand transform (and depth).
 */
@Environment(EnvType.CLIENT)
public final class MeleeHandRenderer {
   private MeleeHandRenderer() {
   }

   /** True when the local player's held item in {@code arm} should be replaced by the melee skin. */
   public static boolean shouldReplace(ArmedEntityRenderState state, ItemStack stack, Arm arm) {
      if (!CosmeticState.isEnabled() || !isSkinned(stack)) {
         return false;
      }
      if (!(state instanceof PlayerEntityRenderState playerState)) {
         return false;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      return client.player != null && client.player.getId() == playerState.id && equipped() != null;
   }

   /** True when the local player's first-person held item should be replaced by the melee skin. */
   public static boolean shouldReplaceFirstPerson(AbstractClientPlayerEntity player, ItemStack stack) {
      if (!CosmeticState.isEnabled() || !isSkinned(stack)) {
         return false;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      return client.player != null && player == client.player && equipped() != null;
   }

   /**
    * True when the vanilla first-person view-bob (hand shake while walking/running) must be skipped.
    * Melee skins carry their own run animation, so the vanilla bob would make them jitter.
    */
   public static boolean shouldDisableHandBob() {
      if (!CosmeticState.isEnabled() || equipped() == null) {
         return false;
      }

      MinecraftClient client = MinecraftClient.getInstance();
      if (client == null || client.player == null) {
         return false;
      }

      return isSkinned(client.player.getMainHandStack()) || isSkinned(client.player.getOffHandStack());
   }

   private static boolean isSkinned(ItemStack stack) {
      return stack != null && !stack.isEmpty() && stack.isIn(ItemTags.SWORDS);
   }

   public static Cosmetic equipped() {
      return CosmeticState.equipped(CosmeticSlot.MELEE);
   }

   /**
    * Draws the melee skin for the third person arm. {@code matrices} must already contain the
    * vanilla held-item transform (arm angle, X -90, Y 180 and the hand translate).
    *
    * @return true when geometry was submitted
    */
   public static boolean renderThirdPerson(Cosmetic melee, Arm arm, MatrixStack matrices,
         OrderedRenderCommandQueue queue, int light) {
      BedrockModel model = CosmeticRenderer.model(melee);
      Identifier texture = CosmeticRenderer.textureId(melee);
      List<ModelQuad> quads = CosmeticRenderer.restQuads(melee);
      if (model == null || texture == null || quads.isEmpty()) {
         return false;
      }

      float[] rotation = arm == Arm.RIGHT ? melee.thirdPersonRightRotation : melee.thirdPersonLeftRotation;
      float[] translation = arm == Arm.RIGHT ? melee.thirdPersonRightTranslation : melee.thirdPersonLeftTranslation;
      float[] scale = arm == Arm.RIGHT ? melee.thirdPersonRightScale : melee.thirdPersonLeftScale;

      matrices.translate(translation[0] / 16.0F, translation[1] / 16.0F, translation[2] / 16.0F);
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rotation[0]));
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(rotation[1]));
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(rotation[2]));
      matrices.scale(scale[0], scale[1], scale[2]);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(180.0F));

      BedrockModel.Bone anchor = model.bone("thirdperson_hand");
      if (anchor != null) {
         matrices.translate(-anchor.pivot[0], -anchor.pivot[1], -anchor.pivot[2]);
      }

      submit(model, texture, quads, matrices, queue);
      return true;
   }

   /**
    * Draws the melee skin as a first-person viewmodel, anchored at the skin's {@code idle_view} bone.
    * The pose comes from the EvoVis-style {@link MeleeAnimator} state machine (static_idle loop plus
    * draw / melee / inspect / run layers).
    *
    * @return true when geometry was submitted
    */
   public static boolean renderFirstPerson(AbstractClientPlayerEntity player, Hand hand, Cosmetic melee,
         MatrixStack matrices, OrderedRenderCommandQueue queue, float equipProgress, boolean mirrored) {
      BedrockModel model = CosmeticRenderer.model(melee);
      Identifier texture = CosmeticRenderer.textureId(melee);
      if (model == null || texture == null) {
         return false;
      }

      MeleeAnimator animator = hand == Hand.OFF_HAND ? MeleeAnimator.offHand() : MeleeAnimator.mainHand();
      List<ModelQuad> quads;
      if (animator.prepare(player, hand)) {
         quads = CosmeticRenderer.meleeQuads(melee, animator.pose(), animator.hiddenBones());
      } else {
         quads = CosmeticRenderer.restQuads(melee);
      }

      if (quads.isEmpty()) {
         return false;
      }

      matrices.translate(0.0F, equipProgress * -0.6F, 0.0F);
      if (mirrored) {
         matrices.scale(-1.0F, 1.0F, 1.0F);
      }

      applyCamera(melee, animator, matrices);

      BedrockModel.Bone anchor = model.bone("idle_view");
      if (anchor == null) {
         anchor = model.bone("camera");
      }
      if (anchor != null) {
         matrices.translate(-anchor.pivot[0], -anchor.pivot[1], -anchor.pivot[2]);
      }

      submit(model, texture, quads, matrices, queue);
      return true;
   }

   /** Mirrors EvoVis' {@code applyCamera}: offsets by the animated {@code camera} bone. */
   private static void applyCamera(Cosmetic melee, MeleeAnimator animator, MatrixStack matrices) {
      BedrockModel model = CosmeticRenderer.model(melee);
      if (model == null || model.bone("camera") == null || !animator.active()) {
         return;
      }

      float[] rotation = animator.pose().rotationOf("camera");
      float[] position = animator.pose().positionOf("camera");
      if (rotation == null && position == null) {
         return;
      }

      float rx = rotation != null ? rotation[0] : 0.0F;
      float ry = rotation != null ? rotation[1] : 0.0F;
      float rz = rotation != null ? rotation[2] : 0.0F;
      float px = position != null ? position[0] : 0.0F;
      float py = position != null ? position[1] : 0.0F;
      float pz = position != null ? position[2] : 0.0F;

      matrices.translate(px / 16.0F, -py / 16.0F, -pz / 16.0F);
      matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-rz));
      matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(ry));
      matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(rx));
   }

   private static void submit(BedrockModel model, Identifier texture, List<ModelQuad> quads, MatrixStack matrices,
         OrderedRenderCommandQueue queue) {
      RenderLayer layer = WorldRenderLayers.TEXTURED_QUADS(texture);
      queue.submitCustom(matrices, layer, (entry, vertices) -> {
         Matrix4f position = entry.getPositionMatrix();
         for (ModelQuad quad : quads) {
            for (int i = 0; i < 4; i++) {
               float u = quad.uv[i][0] / model.textureWidth;
               float v = quad.uv[i][1] / model.textureHeight;
               vertices.vertex(position, quad.pos[i][0], quad.pos[i][1], quad.pos[i][2])
                     .color(255, 255, 255, 255)
                     .texture(u, v);
            }
         }
      });
   }
}
