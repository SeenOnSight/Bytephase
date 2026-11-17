package dev.bytephase.module.engine;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.RangeSetting;
import dev.bytephase.engine.gui.ButtonBase;
import com.google.gson.JsonElement;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.ActionKeySetting;
import dev.bytephase.setting.ActionSetting;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.Setting;
import dev.bytephase.setting.StringSetting;
import dev.bytephase.setting.StructuredSetting;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Native settings and lifecycle for a feature that Bytephase did not already have. */
public final class BytephaseModule extends Module {
    private final ai.auth.Module delegate;
    private final boolean customToggle;
    private final List<Binding> bindings = new ArrayList<>();
    private final List<RangeBinding> ranges = new ArrayList<>();
    private final Set<String> names = new HashSet<>();
    private boolean syncing;
    private long lastRevision = Long.MIN_VALUE;
    private int syncTicks;

    private record Binding(Setting<?> local, java.util.function.Supplier<JsonElement> read,
                           java.util.function.Consumer<JsonElement> write) { }
    private record RangeBinding(RangeSetting source, NumberSetting min, NumberSetting max) { }

    BytephaseModule(ai.auth.Module delegate) {
        super(BytephaseIntegration.name(delegate), category(delegate), description(delegate));
        this.delegate = delegate;
        try {
            customToggle = delegate.getClass().getMethod("toggle").getDeclaringClass() != ai.auth.Module.class;
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(error);
        }
        for (ConfigSetting setting : delegate.getSettings()) {
            if (setting.isHiddenFromList()) continue;
            String name = unique(setting.getSettingName());
            if (setting instanceof dev.bytephase.engine.config.BooleanSetting value) {
                bind(new BooleanSetting(name, value.isEnabled()), value::getJsonValue, value::applyJsonValue);
            } else if (setting instanceof dev.bytephase.engine.config.IntegerSetting value) {
                bind(new NumberSetting(name, value.getValue(), value.getMinValue(), value.getMaxValue(),
                    Math.max(1, value.getStepSize())), value::getJsonValue, value::applyJsonValue);
            } else if (setting instanceof dev.bytephase.engine.config.DoubleSetting value) {
                bind(new NumberSetting(name, value.getValue(), value.getMinValue(), value.getMaxValue(),
                    Math.max(0.000001, value.getStepSize())), value::getJsonValue, value::applyJsonValue);
            } else if (setting instanceof dev.bytephase.engine.config.EnumSetting value) {
                bind(new ModeSetting(name, value.getOptions().toArray(String[]::new), value.getCurrentOption()),
                    value::getJsonValue, value::applyJsonValue);
            } else if (setting instanceof dev.bytephase.engine.config.ColorSetting value) {
                bind(new ColorSetting(name, value.getColor()), value::getJsonValue, value::applyJsonValue);
            } else if (setting instanceof dev.bytephase.engine.config.StringBase value) {
                bind(new StringSetting(name, value.getCurrentText()), value::getJsonValue, value::applyJsonValue);
            } else if (setting instanceof dev.bytephase.engine.config.KeybindSetting value) {
                bind(new ActionKeySetting(name, fromSourceKey(value.getKeyCode())), () ->
                    new com.google.gson.JsonPrimitive(fromSourceKey(value.getKeyCode())),
                    json -> value.setValue2(toSourceKey(json.getAsInt())));
            } else if (setting instanceof RangeSetting value) {
                NumberSetting min = new NumberSetting(unique(name + " Min"), value.getMin(), value.getRangeMin(),
                    value.getRangeMax(), Math.max(0.000001, value.getStep()));
                NumberSetting max = new NumberSetting(unique(name + " Max"), value.getMax(), value.getRangeMin(),
                    value.getRangeMax(), Math.max(0.000001, value.getStep()));
                bind(min,
                    () -> new com.google.gson.JsonPrimitive(value.getMin()),
                    json -> value.handleMaxAmount2(json.getAsDouble()));
                bind(max,
                    () -> new com.google.gson.JsonPrimitive(value.getMax()),
                    json -> value.handleMaxAmount(json.getAsDouble()));
                ranges.add(new RangeBinding(value, min, max));
            } else if (setting instanceof ButtonBase value) {
                add(new ActionSetting(name, value.getText(), value::updateAction));
            } else if (setting instanceof dev.bytephase.engine.inventory.ItemList
                    || setting instanceof dev.bytephase.engine.config.RadioSetting
                    || setting instanceof dev.bytephase.engine.config.ConfigListSetting
                    || setting instanceof dev.bytephase.engine.utils.FriendListBase
                    || setting instanceof dev.bytephase.engine.utils.PaletteBase) {
                if (setting.getJsonValue() != null)
                    add(new StructuredSetting(unique(name + " Data"), setting::getJsonValue, setting::applyJsonValue));
                ActionSetting action = (ActionSetting) dev.bytephase.ui.ImportedSettings.controls(setting).getFirst();
                add(new ActionSetting(name, action.label(), action::run));
            } else {
                if (setting.getJsonValue() != null) {
                    add(new StructuredSetting(unique(name + " Data"), setting::getJsonValue, setting::applyJsonValue));
                }
                add(new ActionSetting(name, "Edit", () -> dev.bytephase.BytePhase.mc.setScreen(
                    dev.bytephase.ui.StructuredEditor.open(name, dev.bytephase.BytePhase.mc.currentScreen,
                        setting::getJsonValue, json -> dev.bytephase.ui.ImportedSettings.apply(setting, json)))));
            }
        }
        if (hasExtraData(delegate)) {
            add(new StructuredSetting(unique("Feature Data"), delegate::saveExtraData, delegate::loadExtraData));
            add(new ActionSetting(unique("Advanced settings"), "Edit", () -> dev.bytephase.BytePhase.mc.setScreen(
                dev.bytephase.ui.StructuredEditor.open(getName(), dev.bytephase.BytePhase.mc.currentScreen,
                    delegate::saveExtraData, delegate::loadExtraData))));
        }
    }

    public ai.auth.Module delegate() { return delegate; }
    @Override public String getHudText() { return delegate.isVisibleInModuleList() ? getName() : ""; }

    private String unique(String name) {
        String result = name;
        for (int index = 2; !names.add(result.toLowerCase(java.util.Locale.ROOT)); index++) result = name + " " + index;
        return result;
    }

    private void bind(Setting<?> setting, java.util.function.Supplier<JsonElement> read,
                      java.util.function.Consumer<JsonElement> write) {
        add(setting);
        bindings.add(new Binding(setting, read, write));
        setting.onChange(json -> {
            if (!syncing) {
                write.accept(json);
                BytephaseIntegration.requestSave();
            }
        });
    }

    private static Category category(ai.auth.Module module) {
        return switch (module.getPageType()) {
            case COMBAT -> Category.COMBAT;
            case MOVEMENT -> Category.MOVEMENT;
            case VISUAL -> Category.RENDER;
            case DONUTSMP -> Category.EXPLORE;
            case BOTS -> Category.WORLD;
            default -> Category.MISC;
        };
    }

    private static String description(ai.auth.Module module) {
        String description = dev.bytephase.engine.utils.AutoYourUtils.getOrDefault(module);
        if (!description.isBlank()) return description;
        return module.getSettings().stream().map(ConfigSetting::getDescription)
            .filter(value -> value != null && !value.isBlank()).findFirst()
            .orElse("Bytephase " + module.getCategory() + " utility.");
    }

    private static boolean hasExtraData(ai.auth.Module module) {
        try {
            return module.hasExtraData() || module.getClass().getMethod("saveExtraData").getDeclaringClass() != ai.auth.Module.class;
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }

    static int fromSourceKey(int key) { return key; }
    static int toSourceKey(int key) { return key; }
    public boolean holdBind() { return delegate.getBindMode() == ai.auth.Module.BindMode.HOLD; }
    public boolean containerBind() { return delegate.requiresContainerBindPolling(); }
    public void bindKey(int key) { delegate.setBindKey(key); }

    @Override protected void onEnable() {
        delegate.onConfigLoaded();
        if (customToggle) delegate.toggle();
        else delegate.setEnabled(true);
    }

    @Override protected void onDisable() { delegate.setEnabled(false); }

    @Override public void onSettingsLoaded() {
        // Applying a new minimum first can clamp it against the old maximum.
        // Restore both persisted bounds together after the scalar controls.
        for (RangeBinding range : ranges) {
            var json = new com.google.gson.JsonObject();
            json.addProperty("min", range.min().getValue());
            json.addProperty("max", range.max().getValue());
            if (range.source().applyJsonValue(json) != ConfigSetting.Applied.APPLIED)
                throw new IllegalArgumentException("Invalid range " + range.source().getSettingName());
        }
        syncSettings(true);
    }

    @Override public void onTickAlways() {
        if (isEnabled() != delegate.isEnabled()) setEnabled(delegate.isEnabled());
        int key = dev.bytephase.input.Keybinds.keyCode(this);
        if (delegate.getBindKey() != key) delegate.setBindKey(key);
        syncSettings(false);
    }

    public void syncSettings(boolean force) {
        long revision = dev.bytephase.engine.config.ModuleConfigAutosave.revision();
        if (!force && revision == lastRevision && ++syncTicks < 20) return;
        syncTicks = 0;
        lastRevision = revision;
        syncing = true;
        boolean changed = false;
        try {
            for (Binding binding : bindings) {
                JsonElement value = binding.read().get();
                if (value != null && !value.equals(binding.local().toJson())) {
                    binding.local().fromJson(value);
                    changed = true;
                }
            }
        } finally { syncing = false; }
        if (changed) BytephaseIntegration.requestSave();
    }
}
