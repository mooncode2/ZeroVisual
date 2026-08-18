package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.Vec3d;
import ru.zero.Zero;
import ru.zero.event.EventInit;
import ru.zero.event.impl.EventUpdate;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.impl.BooleanSetting;
import ru.zero.module.api.setting.Setting;

/**
 * FakePlayer module. When enabled, creates a client‑side dummy player entity at the
 * current player position and direction. The entity does not exist on the server
 * and cannot move; it merely receives visual effects and can be targeted.
 */
@IModule(
    name = "FakePlayer",
    description = "Создаёт клиентскую фиктивную сущность игрока в текущей позиции",
    category = Category.Visuals,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class FakePlayer extends Module {
    // Setting to toggle the fake player visibility
    public static final BooleanSetting enabled = new BooleanSetting("Включено", false);

    private AbstractClientPlayerEntity fakeEntity;

    public FakePlayer() {
        this.addSettings(new Setting[]{enabled});
    }

    @EventInit
    public void onUpdate(EventUpdate e) {
        if (!enabled.get() || mc.player == null) {
            removeFake();
            return;
        }
        if (fakeEntity == null) {
            spawnFake();
        } else {
            // keep position synced with original player orientation
            double x = mc.player.getX();
            double y = mc.player.getY();
            double z = mc.player.getZ();
            fakeEntity.updatePosition(x, y, z);
            fakeEntity.setYaw(mc.player.getYaw());
            fakeEntity.setPitch(mc.player.getPitch());
        }
    }

    private void spawnFake() {
        try {
            ClientWorld world = mc.world;
            // Create a simple AbstractClientPlayerEntity using the current session's profile.
            // If the constructor changes, fallback to a generic Entity.
            fakeEntity = new AbstractClientPlayerEntity(world, mc.player.getGameProfile()) {
                @Override
                public boolean isSpectator() { return false; }
            };
            fakeEntity.updatePosition(mc.player.getX(), mc.player.getY(), mc.player.getZ());
            fakeEntity.setYaw(mc.player.getYaw());
            fakeEntity.setPitch(mc.player.getPitch());
            world.addEntity(fakeEntity);
        } catch (Exception ex) {
            // Log to standard error; Zero does not provide a LOGGER.
            System.err.println("Failed to create FakePlayer entity: " + ex.getMessage());
        }
    }

    private void removeFake() {
        if (fakeEntity != null && mc.world != null) {
            // removeEntity(Entity) is sufficient for client‑side entities
            mc.world.removeEntity(fakeEntity.getId(), net.minecraft.entity.Entity.RemovalReason.DISCARDED);
            fakeEntity = null;
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        removeFake();
    }
}
