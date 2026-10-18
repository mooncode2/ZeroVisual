package ru.zero.cosmetic.render;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.item.Item;
import net.minecraft.util.Hand;
import ru.zero.cosmetic.Cosmetic;
import ru.zero.cosmetic.CosmeticSlot;
import ru.zero.cosmetic.CosmeticState;
import ru.zero.cosmetic.model.MeleePose;
import ru.zero.cosmetic.model.ModelAnimation;

/**
 * Port of EvoVis' {@code ahl} pose state machine and {@code aic} clip player for melee skins.
 * Composes a {@code static_idle} loop, one-shot actions (draw / melee / inspect) and the run
 * locomotion layer into a single {@link MeleePose}, firing clip sounds along the way.
 */
@Environment(EnvType.CLIENT)
public final class MeleeAnimator {
   private static final MeleeAnimator MAIN_HAND = new MeleeAnimator();
   private static final MeleeAnimator OFF_HAND = new MeleeAnimator();

   private static final String CLIP_STATIC_IDLE = "static_idle";
   private static final String CLIP_DRAW = "draw";
   private static final String CLIP_DRAW_ALTERNATE = "draw_1";
   private static final String CLIP_INSPECT = "inspect";
   private static final String CLIP_INSPECT_ALTERNATE = "inspect_1";
   private static final String CLIP_MELEE_PRIMARY = "melee_";
   private static final String CLIP_MELEE_SECONDARY = "melee_3";
   private static final String CLIP_RUN = "run";
   private static final String CLIP_RUN_START = "run_start";
   private static final String CLIP_RUN_END = "run_end";

   private static final float TRANSITION_ACTION = 0.2F;
   private static final float TRANSITION_RUN_START = 0.2F;
   private static final float TRANSITION_RUN_LOOP = 0.2F;
   private static final float TRANSITION_RUN_END = 0.3F;
   private static final float MAX_DELTA_SECONDS = 0.25F;
   private static final float RUN_CYCLE_DISTANCE = 2.0F;
   private static final int PRIMARY_ATTACK_VARIANTS = 2;
   private static final int RUN_MODE_IDLE = -1;
   private static final int RUN_MODE_CYCLE = 0;

   private final Track baseTrack = new Track();
   private final Track mainTrack = new Track();
   private final Track movementTrack = new Track();

   private final MeleePose staticPose = new MeleePose();
   private final MeleePose additivePose = new MeleePose();
   private final MeleePose staticFade = new MeleePose();
   private final MeleePose additiveFade = new MeleePose();
   private final MeleePose composedPose = new MeleePose();
   private final Set<String> hiddenBones = new HashSet<>();

   private Cosmetic cosmetic;
   private String cosmeticKey;
   private Cosmetic.Variant variant;
   private float staticFadeRemaining;
   private float staticFadeDuration;
   private float additiveFadeRemaining;
   private float additiveFadeDuration;
   private boolean startPending = true;
   private boolean running;
   private int attackIndex;
   private int runMode = RUN_MODE_IDLE;
   private float runAnchor;
   private long lastNanos;
   private Item heldItem;
   private int heldSlot = -1;

   private MeleeAnimator() {
   }

   public static MeleeAnimator mainHand() {
      return MAIN_HAND;
   }

   public static MeleeAnimator offHand() {
      return OFF_HAND;
   }

   public static void resetAll() {
      MAIN_HAND.reset();
      OFF_HAND.reset();
   }

   public void reset() {
      this.cosmetic = null;
      this.cosmeticKey = null;
      this.variant = null;
      this.baseTrack.clear();
      this.mainTrack.clear();
      this.movementTrack.clear();
      this.staticPose.clear();
      this.additivePose.clear();
      this.staticFade.clear();
      this.additiveFade.clear();
      this.composedPose.clear();
      this.hiddenBones.clear();
      this.startPending = true;
      this.running = false;
      this.runMode = RUN_MODE_IDLE;
      this.attackIndex = 0;
      this.heldItem = null;
      this.heldSlot = -1;
      this.staticFadeRemaining = 0.0F;
      this.additiveFadeRemaining = 0.0F;
      this.lastNanos = 0L;
   }

   public MeleePose pose() {
      return this.composedPose;
   }

   public Set<String> hiddenBones() {
      return this.hiddenBones;
   }

   public boolean active() {
      return this.cosmetic != null && this.cosmeticKey != null;
   }

   /**
    * Advances the state machine for the given player/hand and recomposes the pose.
    *
    * @return true when a melee cosmetic is active for this hand and the pose may be rendered
    */
   public boolean prepare(AbstractClientPlayerEntity player, Hand hand) {
      if (player == null) {
         return false;
      }

      Cosmetic equipped = CosmeticState.isEnabled() ? CosmeticState.equipped(CosmeticSlot.MELEE) : null;
      if (equipped == null) {
         return false;
      }

      if (equipped != this.cosmetic || !equipped.key.equals(this.cosmeticKey)) {
         this.bind(equipped);
         if (this.cosmetic == null) {
            return false;
         }
      } else if (this.heldChanged(player, hand)) {
         this.restart();
      }

      this.advance(player, this.delta());
      this.compose();
      return true;
   }

   public void onAttack(boolean primary, boolean hitEntity) {
      if (this.cosmetic == null || this.startPending) {
         return;
      }

      if (primary) {
         String clip = this.variantName(CLIP_MELEE_PRIMARY + (this.attackIndex + 1));
         this.attackIndex = (this.attackIndex + 1) % PRIMARY_ATTACK_VARIANTS;
         this.playMain(clip, TRANSITION_ACTION, hitEntity);
      } else {
         this.playMain(this.variantName(CLIP_MELEE_SECONDARY), TRANSITION_ACTION, hitEntity);
      }
   }

   public void onInspect() {
      if (this.cosmetic == null || this.startPending) {
         return;
      }

      String alternate = this.variantName(CLIP_INSPECT_ALTERNATE);
      boolean useAlternate = CosmeticRenderer.hasClip(this.cosmetic, alternate) && ThreadLocalRandom.current().nextBoolean();
      this.playMain(useAlternate ? alternate : this.variantName(CLIP_INSPECT), TRANSITION_ACTION);
   }

   private void bind(Cosmetic equipped) {
      this.heldItem = null;
      this.heldSlot = -1;
      this.cosmetic = CosmeticRenderer.model(equipped) != null ? equipped : null;
      if (this.cosmetic == null) {
         return;
      }

      this.cosmeticKey = equipped.key;
      List<Cosmetic.Variant> variants = equipped.variants;
      this.variant = variants.isEmpty()
            ? new Cosmetic.Variant("", new String[0])
            : variants.get(ThreadLocalRandom.current().nextInt(variants.size()));

      this.hiddenBones.clear();
      for (String bone : this.variant.hidden) {
         this.hiddenBones.add(bone);
      }

      this.restart();
      this.baseTrack.play(this.clip(this.variantName(CLIP_STATIC_IDLE)), Track.MODE_LOOP);
      this.lastNanos = 0L;
   }

   private void restart() {
      this.startPending = true;
      this.running = false;
      this.runMode = RUN_MODE_IDLE;
      this.mainTrack.clear();
      this.movementTrack.clear();
      this.staticFadeRemaining = 0.0F;
      this.additiveFadeRemaining = 0.0F;
   }

   private String variantName(String name) {
      if (this.variant == null || this.variant.suffix.isEmpty()) {
         return name;
      }

      String candidate = name + this.variant.suffix;
      return CosmeticRenderer.hasClip(this.cosmetic, candidate) ? candidate : name;
   }

   private ModelAnimation clip(String name) {
      return CosmeticRenderer.animation(this.cosmetic, name);
   }

   private boolean heldChanged(AbstractClientPlayerEntity player, Hand hand) {
      Item item = player.getStackInHand(hand).getItem();
      int slot = hand == Hand.MAIN_HAND ? player.getInventory().getSelectedSlot() : 40;
      if (item == this.heldItem && slot == this.heldSlot) {
         return false;
      }

      this.heldItem = item;
      this.heldSlot = slot;
      return true;
   }

   private float delta() {
      long now = System.nanoTime();
      if (this.lastNanos == 0L) {
         this.lastNanos = now;
         return 0.0F;
      }

      float seconds = (now - this.lastNanos) / 1.0E9F;
      this.lastNanos = now;
      MinecraftClient client = MinecraftClient.getInstance();
      if (client != null && client.isPaused()) {
         return 0.0F;
      }

      return Math.max(0.0F, Math.min(seconds, MAX_DELTA_SECONDS));
   }

   private void advance(AbstractClientPlayerEntity player, float dt) {
      this.baseTrack.advance(dt);
      this.mainTrack.advance(dt);
      this.movementTrack.advance(dt);
      this.staticFadeRemaining = Math.max(0.0F, this.staticFadeRemaining - dt);
      this.additiveFadeRemaining = Math.max(0.0F, this.additiveFadeRemaining - dt);
      this.updateMain();
      this.updateMovement(player);
   }

   private void updateMain() {
      if (!this.startPending) {
         return;
      }

      String alternate = this.variantName(CLIP_DRAW_ALTERNATE);
      boolean useAlternate = CosmeticRenderer.hasClip(this.cosmetic, alternate) && ThreadLocalRandom.current().nextBoolean();
      this.playMain(useAlternate ? alternate : this.variantName(CLIP_DRAW), 0.0F);
      this.startPending = false;
   }

   private void updateMovement(AbstractClientPlayerEntity player) {
      this.running = false;
      this.runMode = RUN_MODE_IDLE;
      this.movementTrack.clear();
   }

   private void playMain(String name, float fade) {
      this.playMain(name, fade, false);
   }

   private void playMain(String name, float fade, boolean muteSound) {
      ModelAnimation clip = this.clip(name);
      if (clip == null) {
         return;
      }

      this.beginStaticFade(fade);
      this.mainTrack.play(clip, Track.MODE_ONCE_STOP, muteSound);
   }

   private void playMovement(String name, int mode, float fade) {
      ModelAnimation clip = this.clip(name);
      if (clip == null) {
         this.movementTrack.clear();
         return;
      }

      this.beginAdditiveFade(fade);
      this.movementTrack.play(clip, mode);
   }

   private void beginStaticFade(float fade) {
      if (fade <= 0.0F) {
         this.staticFadeRemaining = 0.0F;
         return;
      }

      this.staticFade.rotation.clear();
      this.staticFade.position.clear();
      this.staticFade.scale.clear();
      copyInto(this.staticPose, this.staticFade);
      this.staticFadeDuration = fade;
      this.staticFadeRemaining = fade;
   }

   private void beginAdditiveFade(float fade) {
      if (fade <= 0.0F) {
         this.additiveFadeRemaining = 0.0F;
         return;
      }

      this.additiveFade.rotation.clear();
      this.additiveFade.position.clear();
      this.additiveFade.scale.clear();
      copyInto(this.additivePose, this.additiveFade);
      this.additiveFadeDuration = fade;
      this.additiveFadeRemaining = fade;
   }

   private static void copyInto(MeleePose source, MeleePose target) {
      for (java.util.Map.Entry<String, float[]> entry : source.rotation.entrySet()) {
         target.rotation.put(entry.getKey(), entry.getValue().clone());
      }
      for (java.util.Map.Entry<String, float[]> entry : source.position.entrySet()) {
         target.position.put(entry.getKey(), entry.getValue().clone());
      }
      for (java.util.Map.Entry<String, float[]> entry : source.scale.entrySet()) {
         target.scale.put(entry.getKey(), entry.getValue().clone());
      }
   }

   private void compose() {
      this.staticPose.clear();
      if (this.mainTrack.isActive()) {
         this.mainTrack.evaluate(this.staticPose);
      } else {
         this.baseTrack.evaluate(this.staticPose);
      }
      blend(this.staticPose, this.staticFade, this.staticFadeRemaining, this.staticFadeDuration);

      this.additivePose.clear();
      if (this.movementTrack.isActive()) {
         this.movementTrack.evaluate(this.additivePose);
      }
      blend(this.additivePose, this.additiveFade, this.additiveFadeRemaining, this.additiveFadeDuration);

      MeleePose combined = MeleePose.combine(this.staticPose, this.additivePose);
      this.composedPose.rotation.clear();
      this.composedPose.position.clear();
      this.composedPose.scale.clear();
      copyInto(combined, this.composedPose);
   }

   private static void blend(MeleePose pose, MeleePose fade, float remaining, float duration) {
      if (remaining <= 0.0F || duration <= 0.0F) {
         return;
      }

      pose.blend(fade, 1.0F - remaining / duration);
   }

   /** A single clip player (EvoVis {@code aic}) with mode, progress and clip-sound firing. */
   private static final class Track {
      static final int MODE_LOOP = 0;
      static final int MODE_ONCE_STOP = 1;
      static final int MODE_ONCE_HOLD = 2;

      private static final int SOUND_CAPACITY = 4;

      private final List<SoundInstance> playing = new ArrayList<>(SOUND_CAPACITY);
      private ModelAnimation clip;
      private int mode = MODE_ONCE_STOP;
      private float time;
      private int soundCursor;
      private boolean stopped = true;
      private boolean muted;

      void play(ModelAnimation clip, int mode) {
         this.play(clip, mode, false);
      }

      void play(ModelAnimation clip, int mode, boolean muteSound) {
         this.stopSounds();
         this.clip = clip;
         this.mode = mode;
         this.time = 0.0F;
         this.soundCursor = 0;
         this.muted = muteSound;
         this.stopped = clip == null;
      }

      void clear() {
         this.stopSounds();
         this.clip = null;
         this.mode = MODE_ONCE_STOP;
         this.time = 0.0F;
         this.soundCursor = 0;
         this.muted = false;
         this.stopped = true;
      }

      boolean isActive() {
         return this.clip != null && !this.stopped;
      }

      boolean isStopped() {
         return this.stopped || this.clip == null;
      }

      boolean isHolding() {
         return this.clip != null && this.mode == MODE_ONCE_HOLD && this.time >= this.clip.length;
      }

      void setProgress(float progress) {
         if (this.clip == null) {
            return;
         }

         float length = this.clip.length;
         if (length <= 0.0F) {
            return;
         }

         this.time = Math.max(0.0F, Math.min(progress, 1.0F)) * length;
      }

      void advance(float dt) {
         if (this.clip == null || this.stopped) {
            return;
         }

         float length = this.clip.length;
         if (length <= 0.0F) {
            this.fireSounds(0.0F);
            return;
         }

         float next = this.time + dt;
         if (next < length) {
            this.time = next;
            this.fireSounds(next);
            return;
         }

         this.fireSounds(length);
         switch (this.mode) {
            case MODE_LOOP:
               this.time = next % length;
               this.soundCursor = 0;
               this.fireSounds(this.time);
               break;
            case MODE_ONCE_HOLD:
               this.time = length;
               break;
            default:
               this.time = length;
               this.stopped = true;
         }
      }

      void evaluate(MeleePose pose) {
         if (this.clip != null) {
            pose.applyAnimation(this.clip, this.time);
         }
      }

      private void stopSounds() {
         for (SoundInstance instance : this.playing) {
            MeleeSounds.stop(instance);
         }
         this.playing.clear();
      }

      private void fireSounds(float upTo) {
         int count = this.clip.soundCount();
         while (this.soundCursor < count && this.clip.soundTime(this.soundCursor) <= upTo) {
            if (!this.muted) {
               SoundInstance instance = MeleeSounds.play(this.clip.soundName(this.soundCursor));
               if (instance != null) {
                  this.pruneFinishedSounds();
                  this.playing.add(instance);
               }
            }
            this.soundCursor++;
         }
      }

      private void pruneFinishedSounds() {
         for (int i = this.playing.size() - 1; i >= 0; i--) {
            if (!MeleeSounds.isActive(this.playing.get(i))) {
               this.playing.remove(i);
            }
         }
      }
   }
}
