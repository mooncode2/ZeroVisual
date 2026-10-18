package ru.zero.module.impl.visuals;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.option.Perspective;
import ru.zero.event.EventInit;
import ru.zero.event.render.HandEvent;
import ru.zero.module.api.Category;
import ru.zero.module.api.IModule;
import ru.zero.module.api.Module;
import ru.zero.module.api.setting.Setting;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.util.render.core.Renderer2D;
import ru.zero.util.render.hands.HandsShaderRenderer;

@IModule(
    name = "Hands Shader",
    description = "Накладывает шейдер на руку от первого лица",
    category = Category.Visuals,
    bind = -1
)
@Environment(EnvType.CLIENT)
public class HandsShader extends Module {
    public SliderSetting opacity = new SliderSetting("Непрозрачность", 0.6f, 0.0f, 1.0f, 0.05f, false);
    private final HandsShaderRenderer renderer = new HandsShaderRenderer();

    public HandsShader() {
        this.addSettings(new Setting[]{this.opacity});
    }

    @EventInit
    public void onHandEvent(HandEvent event) {
        if (mc.options.getPerspective() != Perspective.FIRST_PERSON) {
            return;
        }

        if (event.isPre()) {
            this.renderer.snapshotDepth();
        } else if (event.isPost()) {
            float[] color = Renderer2D.ColorUtil.getRGBAf(Renderer2D.ColorUtil.getClientColor());
            this.renderer.draw(color[0], color[1], color[2], this.opacity.get());
        }
    }

    @Override
    public void onDisable() {
        super.onDisable();
        this.renderer.close();
    }
}
