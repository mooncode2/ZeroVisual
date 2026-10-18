package ru.zero.event.render;

import lombok.Generated;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.event.Event;

@Environment(EnvType.CLIENT)
public class HandEvent extends Event {
    private final Phase phase;

    public enum Phase {
        PRE,
        POST
    }

    @Generated
    public HandEvent(Phase phase) {
        this.phase = phase;
    }

    @Generated
    public Phase getPhase() {
        return this.phase;
    }

    @Generated
    public boolean isPre() {
        return this.phase == Phase.PRE;
    }

    @Generated
    public boolean isPost() {
        return this.phase == Phase.POST;
    }
}
