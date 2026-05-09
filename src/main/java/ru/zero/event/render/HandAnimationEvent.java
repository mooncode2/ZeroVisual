package ru.zero.event.render;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Hand;
import net.minecraft.client.util.math.MatrixStack;
import ru.zero.event.Event;

@Environment(EnvType.CLIENT)
public class HandAnimationEvent extends Event {
   private MatrixStack matrices;
   private Hand hand;
   private float swingProgress;

   @Generated
   public HandAnimationEvent(MatrixStack matrices, Hand hand, float swingProgress) {
      this.matrices = matrices;
      this.hand = hand;
      this.swingProgress = swingProgress;
   }

   @Generated
   public MatrixStack getMatrices() {
      return this.matrices;
   }

   @Generated
   public Hand getHand() {
      return this.hand;
   }

   @Generated
   public float getSwingProgress() {
      return this.swingProgress;
   }

   @Generated
   public void setMatrices(MatrixStack matrices) {
      this.matrices = matrices;
   }

   @Generated
   public void setHand(Hand hand) {
      this.hand = hand;
   }

   @Generated
   public void setSwingProgress(float swingProgress) {
      this.swingProgress = swingProgress;
   }
}
