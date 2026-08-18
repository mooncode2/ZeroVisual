package ru.zero.util.render.glass.aetherial;

public final class AetherialGlassStyle {
   private int tintColor = -16777216;
   private float tintAlpha = 0.0F;
   private float opacity = 1.0F;
   private float smoothing = 0.003F;
   private int blurRadius = 12;
   private float shadowExpand = 30.0F;
   private float shadowFactor = 0.25F;
   private float shadowOffsetX = 0.0F;
   private float shadowOffsetY = 2.0F;
   private int shadowColor = -16777216;
   private float shadowColorAlpha = 1.0F;
   private float refThickness = 20.0F;
   private float refFactor = 1.4F;
   private float refDispersion = 7.0F;
   private float refFresnelRange = 30.0F;
   private float refFresnelHardness = 20.0F;
   private float refFresnelFactor = 20.0F;
   private float glareRange = 30.0F;
   private float glareHardness = 20.0F;
   private float glareConvergence = 50.0F;
   private float glareOppositeFactor = 80.0F;
   private float glareFactor = 90.0F;
   private float glareAngleRad = (-(float)Math.PI / 4F);

   public static AetherialGlassStyle create() {
      return new AetherialGlassStyle();
   }

   public AetherialGlassStyle tint(int color, float alpha) {
      this.tintColor = color;
      this.tintAlpha = Math.max(0.0F, Math.min(1.0F, alpha));
      return this;
   }

   public AetherialGlassStyle smoothing(float factor) {
      this.smoothing = factor;
      return this;
   }

   public AetherialGlassStyle blurRadius(int radius) {
      this.blurRadius = Math.max(0, radius);
      return this;
   }

   public AetherialGlassStyle opacity(float value) {
      this.opacity = Math.max(0.0F, Math.min(1.0F, value));
      return this;
   }

   public float getOpacity() {
      return this.opacity;
   }

   public AetherialGlassStyle refFactor(float factor) {
      this.refFactor = Math.max(1.0F, factor);
      return this;
   }

   public AetherialGlassStyle refThickness(float thickness) {
      this.refThickness = Math.max(0.0F, thickness);
      return this;
   }

   public AetherialGlassStyle refDispersion(float dispersion) {
      this.refDispersion = Math.max(0.0F, dispersion);
      return this;
   }

   public AetherialGlassStyle refFresnelFactor(float factor) {
      this.refFresnelFactor = Math.max(0.0F, factor);
      return this;
   }

   public AetherialGlassStyle noShadow() {
      this.shadowExpand = 0.0F;
      this.shadowFactor = 0.0F;
      return this;
   }

   public AetherialGlassStyle simpleFrost() {
      this.refThickness = 0.0F;
      this.refFresnelFactor = 0.0F;
      this.refFresnelHardness = 0.0F;
      this.glareHardness = -100.0F;
      this.glareOppositeFactor = 0.0F;
      this.glareFactor = 0.0F;
      return this;
   }

   public int getTintColor() {
      return this.tintColor;
   }

   public float getTintAlpha() {
      return this.tintAlpha;
   }

   public float getSmoothing() {
      return this.smoothing;
   }

   public int getBlurRadius() {
      return this.blurRadius;
   }

   public float getShadowExpand() {
      return this.shadowExpand;
   }

   public float getShadowFactor() {
      return this.shadowFactor;
   }

   public float getShadowOffsetX() {
      return this.shadowOffsetX;
   }

   public float getShadowOffsetY() {
      return this.shadowOffsetY;
   }

   public int getShadowColor() {
      return this.shadowColor;
   }

   public float getShadowColorAlpha() {
      return this.shadowColorAlpha;
   }

   public float getRefThickness() {
      return this.refThickness;
   }

   public float getRefFactor() {
      return this.refFactor;
   }

   public float getRefDispersion() {
      return this.refDispersion;
   }

   public float getRefFresnelRange() {
      return this.refFresnelRange;
   }

   public float getRefFresnelHardness() {
      return this.refFresnelHardness;
   }

   public float getRefFresnelFactor() {
      return this.refFresnelFactor;
   }

   public float getGlareRange() {
      return this.glareRange;
   }

   public float getGlareHardness() {
      return this.glareHardness;
   }

   public float getGlareConvergence() {
      return this.glareConvergence;
   }

   public float getGlareOppositeFactor() {
      return this.glareOppositeFactor;
   }

   public float getGlareFactor() {
      return this.glareFactor;
   }

   public float getGlareAngleRad() {
      return this.glareAngleRad;
   }
}
