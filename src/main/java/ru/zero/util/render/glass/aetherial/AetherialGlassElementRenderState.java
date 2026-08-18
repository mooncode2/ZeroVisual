package ru.zero.util.render.glass.aetherial;

import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.special.SpecialGuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;

public record AetherialGlassElementRenderState(int x1, int y1, int x2, int y2, float cornerRadius, AetherialGlassStyle style, Matrix3x2f pose, @Nullable ScreenRect scissorArea, float hover, float focus) implements SpecialGuiElementRenderState {
   public float scale() {
      return 1.0F;
   }

   public @Nullable ScreenRect bounds() {
      ScreenRect ownBounds = (new ScreenRect(this.x1, this.y1, this.x2 - this.x1, this.y2 - this.y1)).transformEachVertex(this.pose);
      return this.scissorArea != null ? this.scissorArea.intersection(ownBounds) : ownBounds;
   }
}
