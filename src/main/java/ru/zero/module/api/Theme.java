package ru.zero.module.api;

import java.awt.Color;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.util.render.math.animation.Animation;
import ru.zero.util.render.math.animation.impl.EaseInOutQuad;

@Environment(EnvType.CLIENT)
public enum Theme {
   THEME1(new Color(9150463), new Color(856880), new Color(1251638), new Color(9150463), new Color(13489663), new Color(9216255)),
   THEME2(new Color(16747403), new Color(3149069), new Color(3543827), new Color(16747403), new Color(16764365), new Color(16747660)),
   THEME3(new Color(16771211), new Color(3156749), new Color(3551507), new Color(16771211), new Color(16774349), new Color(16771724)),
   THEME4(new Color(9175006), new Color(864296), new Color(1259050), new Color(9175006), new Color(13500399), new Color(9240537)),
   THEME5(new Color(9165567), new Color(859440), new Color(1254710), new Color(9165567), new Color(13494783), new Color(9232639)),
   THEME6(new Color(14191615), new Color(2559280), new Color(2888502), new Color(14191615), new Color(16240127), new Color(14388479)),
   THEME7(new Color(16747513), new Color(3149103), new Color(3412790), new Color(16747513), new Color(16764408), new Color(16747773)),
   THEME8(new Color(16747467), new Color(3149089), new Color(3543849), new Color(16747467), new Color(16764391), new Color(16747733)),
    THEME9(new Color(13684944), new Color(789516), new Color(1118481), new Color(12105912), new Color(15790320), new Color(10066329)),
     LIQUID_GLASS(new Color(0x66CCFF), new Color(0x1B2838), new Color(0x243447), new Color(0x88CCFF), new Color(0xE8F1F8), new Color(0xAABED0));

   private final Color main;
   private final Color bg;
   private final Color bg2;
   private final Color outline;
   private final Color text;
   private final Color text2;
   public Animation animation = new EaseInOutQuad(300, 1.0);

   private Theme(Color main, Color bg, Color bg2, Color outline, Color text, Color text2) {
      this.main = main;
      this.bg = bg;
      this.bg2 = bg2;
      this.outline = outline;
      this.text = text;
      this.text2 = text2;
   }

   public Color getMain() {
      return this.main;
   }

   public Color getBg() {
      return this.bg;
   }

   public Color getBg2() {
      return this.bg2;
   }

   public Color getOutline() {
      return this.outline;
   }

   public Color getText() {
      return this.text;
   }

   public Color getText2() {
      return this.text2;
   }

   public Animation getAnimation() {
      return this.animation;
   }
}
