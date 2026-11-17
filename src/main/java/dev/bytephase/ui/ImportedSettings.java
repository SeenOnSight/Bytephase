package dev.bytephase.ui;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.RadioSetting;
import dev.bytephase.engine.gui.ButtonBase;
import dev.bytephase.engine.inventory.ItemList;
import com.google.gson.JsonElement;
import dev.bytephase.BytePhase;
import dev.bytephase.setting.ActionSetting;
import dev.bytephase.setting.ActionKeySetting;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.Setting;
import dev.bytephase.setting.StringSetting;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;

/** Native controls for imported data and actions; no imported Screen is rendered. */
public final class ImportedSettings {
    private ImportedSettings() { }

    public static List<Setting<?>> controls(ConfigSetting source) {
        String name = source.getSettingName();
        if (source instanceof dev.bytephase.engine.utils.DelegatingAutoEspBase wrapper) return controls(wrapper.getDelegate());
        if (source instanceof ItemList items) return List.of(new ActionSetting(name, "Choose items", () -> openItems(items)));
        if (source instanceof dev.bytephase.engine.config.ConfigListSetting)
            return List.of(new ActionSetting(name, "Configs", () -> BytePhase.mc.setScreen(ClickGuiScreen.page("CONFIGS"))));
        if (source instanceof dev.bytephase.engine.utils.FriendListBase)
            return List.of(new ActionSetting(name, "Friends", () -> BytePhase.mc.setScreen(ClickGuiScreen.page("FRIENDS"))));
        if (source instanceof dev.bytephase.engine.utils.PaletteBase)
            return List.of(new ActionSetting(name, "Themes", () -> BytePhase.mc.setScreen(ClickGuiScreen.page("THEMES"))));
        if (source instanceof ButtonBase button) return List.of(new ActionSetting(name, button.getText(), button::updateAction));
        Setting<?> local;
        if (source instanceof dev.bytephase.engine.config.BooleanSetting value) local = new BooleanSetting(name, value.isEnabled());
        else if (source instanceof dev.bytephase.engine.config.IntegerSetting value)
            local = new NumberSetting(name, value.getValue(), value.getMinValue(), value.getMaxValue(), Math.max(1, value.getStepSize()));
        else if (source instanceof dev.bytephase.engine.config.DoubleSetting value)
            local = new NumberSetting(name, value.getValue(), value.getMinValue(), value.getMaxValue(), Math.max(0.000001, value.getStepSize()));
        else if (source instanceof dev.bytephase.engine.config.EnumSetting value)
            local = new ModeSetting(name, value.getOptions().toArray(String[]::new), value.getCurrentOption());
        else if (source instanceof dev.bytephase.engine.config.ColorSetting value) local = new ColorSetting(name, value.getColor());
        else if (source instanceof dev.bytephase.engine.config.StringBase value) local = new StringSetting(name, value.getCurrentText());
        else if (source instanceof dev.bytephase.engine.config.KeybindSetting value) local = new ActionKeySetting(name, value.getKeyCode());
        else if (source instanceof dev.bytephase.engine.config.RangeSetting value) {
            NumberSetting min = new NumberSetting(name + " Min", value.getMin(), value.getRangeMin(), value.getRangeMax(), value.getStep());
            NumberSetting max = new NumberSetting(name + " Max", value.getMax(), value.getRangeMin(), value.getRangeMax(), value.getStep());
            min.onChange(json -> { value.handleMaxAmount2(json.getAsDouble()); save(); });
            max.onChange(json -> { value.handleMaxAmount(json.getAsDouble()); save(); });
            return List.of(min, max);
        } else if (source instanceof RadioSetting radio) {
            return List.of(new ActionSetting(name, "Choose station", () -> openRadio(radio)));
        } else {
            return List.of(new ActionSetting(name, "Edit", () -> BytePhase.mc.setScreen(StructuredEditor.open(
                name, BytePhase.mc.currentScreen, source::getJsonValue, json -> apply(source, json)))));
        }
        local.onChange(json -> apply(source, json));
        return List.of(local);
    }

    public static void openItems(ItemList source) {
        BytePhase.mc.setScreen(ClickGuiScreen.editor(source.getSettingName(), BytePhase.mc.currentScreen, () -> {
            List<Setting<?>> controls = new ArrayList<>();
            Registries.ITEM.stream().sorted(java.util.Comparator.comparing(item -> item.getName().getString())).forEach(item ->
                controls.add(new ActionSetting(item.getName().getString(), source.isContains(item) ? "Remove" : "Add",
                    () -> { source.handle3(item); save(); })));
            return controls;
        }));
    }

    public static void openRadio(RadioSetting source) {
        BytePhase.mc.setScreen(ClickGuiScreen.editor(source.getSettingName(), BytePhase.mc.currentScreen, () -> {
            List<Setting<?>> controls = new ArrayList<>();
            for (var station : source.getStations()) if (station.hasUrl())
                controls.add(new ActionSetting(station.title().isBlank() ? station.key() : station.title(),
                    source.isSelected(station) ? "Selected" : "Select",
                    () -> { source.setSelectedStationKey(station.key()); save(); }));
            StringSetting custom = new StringSetting("Station key", source.getSelectedStationKey(), 640);
            custom.onChange(json -> { source.setSelectedStationKey(json.getAsString()); save(); });
            controls.add(custom);
            return controls;
        }));
    }

    public static void apply(ConfigSetting source, JsonElement json) {
        if (source.applyJsonValue(json) != ConfigSetting.Applied.APPLIED)
            throw new IllegalArgumentException("The setting rejected that value");
        save();
    }

    public static void save() { if (BytePhase.config != null) BytePhase.config.requestSave(); }
}
