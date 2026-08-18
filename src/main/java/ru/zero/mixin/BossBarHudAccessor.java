package ru.zero.mixin;

import java.util.Map;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.hud.BossBarHud;
import net.minecraft.entity.boss.BossBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Environment(EnvType.CLIENT)
@Mixin({BossBarHud.class})
public interface BossBarHudAccessor {
   @Accessor("bossBars")
   Map<UUID, BossBar> zero$getBossBars();
}
