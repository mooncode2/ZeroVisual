package ru.zero.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Зарезервирован под общие хуки сущности. Логика HitBox/NoPush удалена — клиент не зависит от боевых/игрок-модулей.
 */
@Environment(EnvType.CLIENT)
@Mixin({ Entity.class })
public abstract class EntityMixin {
}
