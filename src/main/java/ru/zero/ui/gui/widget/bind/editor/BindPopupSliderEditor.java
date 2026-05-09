package ru.zero.ui.gui.widget.bind.editor;

import java.util.Objects;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import ru.zero.module.api.setting.impl.SliderSetting;
import ru.zero.ui.gui.widget.bind.BindPopupModel;
import ru.zero.ui.gui.widget.settings.SettingValueAccessor;
import ru.zero.ui.gui.widget.settings.SliderWidget;

@Environment(EnvType.CLIENT)
public final class BindPopupSliderEditor extends AbstractBindPopupSettingEditor<Double> {
   public BindPopupSliderEditor(BindPopupModel model, SliderSetting setting) {
      super(Objects.requireNonNull(model, "model"), createWidget(model, Objects.requireNonNull(setting, "setting")));
   }

   private static SliderWidget createWidget(BindPopupModel model, SliderSetting setting) {
      final double defaultValue = setting.current;
      SettingValueAccessor<Double> accessor = accessorFor(model, defaultValue, new AbstractBindPopupSettingEditor.ValueAdapter<Double>() {
         public Double read(BindPopupModel target) {
            return target.getEditableTargetValue() instanceof Number number ? number.doubleValue() : defaultValue;
         }

         public void write(BindPopupModel target, Double value) {
            double sanitized = value != null ? value : defaultValue;
            target.setEditableTargetValue(sanitized);
         }
      });
      return new SliderWidget(requireModule(model), noPopupContext(), setting, accessor, "New Value");
   }
}
