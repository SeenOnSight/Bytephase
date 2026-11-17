package dev.bytephase.ui;

import dev.bytephase.engine.config.ConfigSetting;
import dev.bytephase.engine.config.ModulePresetList;
import dev.bytephase.engine.gui.GuiClickScreen;
import dev.bytephase.engine.gui.PreviewText;
import dev.bytephase.BytePhase;
import dev.bytephase.module.engine.BytephaseIntegration;
import dev.bytephase.module.engine.BytephaseModule;
import dev.bytephase.setting.ActionSetting;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.LabelSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.Setting;
import dev.bytephase.setting.StringSetting;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.screen.Screen;

/** Replaces imported screens at the client boundary with Bytephase's own UI. */
public final class NativeImportedUi {
    private NativeImportedUi() { }

    public static Screen replace(Screen requested) {
        if (requested == null) return null;
        boolean imported = requested.getClass().getName().startsWith("dev.bytephase.engine.");
        boolean info = requested instanceof ClientInfoScreen;
        if (!imported && !info) return requested;
        var data = (dev.bytephase.mixin.ScreenDataAccessor) requested;
        data.bytephase$setClient(BytePhase.mc);
        data.bytephase$setTextRenderer(BytePhase.mc.textRenderer);
        requested.width = BytePhase.mc.getWindow().getScaledWidth();
        requested.height = BytePhase.mc.getWindow().getScaledHeight();
        Screen current = BytePhase.mc.currentScreen;
        Screen parent = requested instanceof GuiClickScreen preview ? preview.getCurrentScreen() : current;
        if (parent == requested || parent != null && parent.getClass().getName().startsWith("dev.bytephase.engine."))
            parent = current instanceof ClickGuiScreen ? current : new ClickGuiScreen();
        if (info || requested instanceof dev.bytephase.engine.gui.BytephaseLicense)
            return ClickGuiScreen.editor("Client information", parent, () -> List.of(
                new LabelSetting("Bytephase", BytePhase.version()), new LabelSetting("Minecraft", "1.21.11 / Fabric"),
                new LabelSetting("Utility source", "MIT"), new LabelSetting("PvP source", "GPL-3.0-only"),
                new LabelSetting("Imported source", "Original rights retained")));
        try {
            List<Object> values = members(requested);
            ai.auth.Module owner = values.stream().filter(ai.auth.Module.class::isInstance)
                .map(ai.auth.Module.class::cast).findFirst().orElse(null);
            if (owner == null && current instanceof ClickGuiScreen gui && gui.editedModule() instanceof BytephaseModule module)
                owner = module.delegate();
            BytephaseModule adapter = owner == null ? null : BytephaseIntegration.adapter(owner);
            if (requested instanceof dev.bytephase.engine.gui.MatrixMatricesScreen)
                return adapter == null ? new ClickGuiScreen() : ClickGuiScreen.forModule(adapter);
            for (Object value : values) if (value instanceof ModulePresetList presets)
                return crafter(presets, parent);
            String title = requested.getTitle().getString();
            ai.auth.Module selectedOwner = owner;
            return ClickGuiScreen.editor(title, parent, () -> controls(requested, selectedOwner)).owner(adapter);
        } catch (RuntimeException error) {
            BytePhase.LOGGER.error("Could not adapt imported editor {}", requested.getClass().getSimpleName(), error);
            return new ClickGuiScreen();
        }
    }

    private static List<Object> members(Object object) {
        List<Object> result = new ArrayList<>();
        for (Class<?> type = object.getClass(); type != null && type != Screen.class; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(object);
                    if (value != null) result.add(value);
                } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
            }
        }
        return result;
    }

    private static List<Setting<?>> controls(Screen source, ai.auth.Module owner) {
        List<Setting<?>> result = new ArrayList<>();
        if (source instanceof dev.bytephase.render.ui.NativePreviewScene scene)
            result.add(new dev.bytephase.setting.PreviewSetting("Preview", scene));
        if (source instanceof dev.bytephase.engine.utils.SchematicMaterials materials)
            for (var row : materials.nativeMaterials()) result.add(new ActionSetting(row.name(), "Details", () ->
                BytePhase.mc.setScreen(ClickGuiScreen.editor(row.name(), BytePhase.mc.currentScreen, () -> {
                    List<Setting<?>> details = new ArrayList<>();
                    for (String column : row.summary().split(" · ")) {
                        int split = column.indexOf(':');
                        details.add(new LabelSetting(split < 0 ? "Amount" : column.substring(0, split),
                            split < 0 ? column : column.substring(split + 1).trim()));
                    }
                    return details;
                }))));
        Set<ConfigSetting> seen = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Object value : members(source)) {
            if (value instanceof dev.bytephase.engine.utils.TripStats trip) {
                result.add(new LabelSetting("Result", trip.headline()));
                result.add(new LabelSetting("Reason", trip.reason()));
                result.add(new LabelSetting("Destination", trip.destination()));
                result.add(new LabelSetting("Dimension", trip.dimension()));
                result.add(new LabelSetting("Landing position", trip.landX() + ", " + trip.landY() + ", " + trip.landZ()));
                result.add(new LabelSetting("Distance", trip.formatFlownBlocks()));
                result.add(new LabelSetting("Travel time", trip.formatSecondsFlown()));
                result.add(new LabelSetting("Rockets used", Integer.toString(trip.rocketsUsed())));
                result.add(new LabelSetting("Spent", trip.formatSpent()));
            }
            if (value instanceof ConfigSetting setting && seen.add(setting)) result.addAll(ImportedSettings.controls(setting));
            if (value instanceof Map<?, ?> map) {
                int count = 0;
                for (var entry : map.entrySet()) {
                    if (++count > 512) break;
                    if (entry.getKey() instanceof net.minecraft.item.Item item && entry.getValue() instanceof Number amount)
                        result.add(new LabelSetting(item.getName().getString(), amount.toString()));
                    else if (entry.getKey() instanceof String name && entry.getValue() instanceof Number amount)
                        result.add(new LabelSetting(name, amount.toString()));
                }
            }
        }
        if (source instanceof GuiClickScreen preview) {
            int count = Math.min(256, preview.getShowSchematics());
            if (count > 0) {
                List<String> options = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    String name = preview.formatTextsInspects(i);
                    options.add((i + 1) + " · " + (name == null || name.isBlank() ? "Selection" : name));
                }
                ModeSetting selection = new ModeSetting("Selection", options.toArray(String[]::new),
                    options.get(Math.clamp(preview.nativeSelectedIndex(), 0, count - 1)));
                selection.onChange(json -> {
                    preview.handleInspectsSchematics(options.indexOf(json.getAsString()));
                    ImportedSettings.save();
                });
                result.add(selection);
            }
            for (PreviewText.NativeControl control : preview.nativeControls()) addControl(result, control);
        }
        if (owner != null) {
            for (ConfigSetting setting : owner.getSettings())
                if (!setting.isHiddenFromList() && seen.add(setting)) result.addAll(ImportedSettings.controls(setting));
            if (owner.saveExtraData() != null) result.add(new ActionSetting("Additional data", "Edit", () ->
                BytePhase.mc.setScreen(StructuredEditor.open(source.getTitle().getString(), BytePhase.mc.currentScreen,
                    owner::saveExtraData, owner::loadExtraData))));
        }
        if (result.isEmpty()) result.add(new LabelSetting(source.getTitle().getString(), "Ready"));
        return result;
    }

    private static void addControl(List<Setting<?>> result, PreviewText.NativeControl control) {
        String name = control.label() == null || control.label().isBlank() ? StructuredEditor.readable(control.key()) : control.label();
        if (List.of("close", "done", "back").contains(control.key().toLowerCase(java.util.Locale.ROOT))
                && control.action() != null) {
            result.add(new ActionSetting(name, name, () -> BytePhase.mc.currentScreen.close()));
            return;
        }
        if (control.mode() == 2 && control.action() != null && control.enabled()) {
            BooleanSetting toggle = new BooleanSetting(name, control.on());
            toggle.onChange(json -> { control.action().run(); ImportedSettings.save(); });
            result.add(toggle);
        } else if (control.mode() == 3 && control.adjust() != null && control.enabled()) {
            result.add(new LabelSetting(name, control.value()));
            result.add(new ActionSetting(name + " Previous", "Previous", () -> { control.adjust().accept(-1); ImportedSettings.save(); }));
            result.add(new ActionSetting(name + " Next", "Next", () -> { control.adjust().accept(1); ImportedSettings.save(); }));
        } else if (control.mode() == 4 && control.options() != null && control.options().length > 0 && control.enabled()) {
            String[] choices = java.util.stream.IntStream.range(0, control.options().length)
                .mapToObj(i -> (i + 1) + " · " + String.format("#%06X", control.options()[i] & 0xFFFFFF)).toArray(String[]::new);
            ModeSetting mode = new ModeSetting(name, choices, choices[Math.clamp(control.selected(), 0, choices.length - 1)]);
            mode.onChange(json -> {
                if (control.adjust() != null) control.adjust().accept(java.util.Arrays.asList(choices).indexOf(json.getAsString()));
                ImportedSettings.save();
            });
            result.add(mode);
        } else if (control.action() != null && control.enabled())
            result.add(new ActionSetting(name, name, () -> { control.action().run(); ImportedSettings.save(); }));
        else result.add(new LabelSetting(name, control.value() == null ? "" : control.value()));
    }

    private static Screen crafter(ModulePresetList presets, Screen parent) {
        class Draft {
            String name = presets.getSelectedPresetName() == null ? "" : presets.getSelectedPresetName();
            int mask = presets.getSelectedPreset() == null ? 0 : presets.getSelectedPreset().mask();
            List<Setting<?>> fields() {
                List<Setting<?>> result = new ArrayList<>();
                BooleanSetting auto = new BooleanSetting("Auto apply", presets.isAutoApply());
                auto.onChange(json -> { presets.setAutoApply(json.getAsBoolean()); ImportedSettings.save(); });
                result.add(auto);
                StringSetting title = new StringSetting("Preset name", name, 64);
                title.onChange(json -> name = json.getAsString()); result.add(title);
                for (int i = 0; i < 9; i++) {
                    int bit = 1 << i;
                    BooleanSetting slot = new BooleanSetting("Disable slot " + (i + 1), (mask & bit) != 0);
                    slot.onChange(json -> mask = json.getAsBoolean() ? mask | bit : mask & ~bit);
                    result.add(slot);
                }
                result.add(new ActionSetting("Save preset", "Save", () -> {
                    if (!presets.savePreset(name, mask)) throw new IllegalArgumentException("Choose a valid preset name");
                    presets.selectPreset(name); ImportedSettings.save();
                }));
                for (var preset : presets.getPresetList()) {
                    result.add(new ActionSetting(preset.name(), preset.name().equals(presets.getSelectedPresetName()) ? "Selected" : "Select", () -> {
                        name = preset.name(); mask = preset.mask(); presets.selectPreset(name); ImportedSettings.save();
                    }));
                    result.add(new ActionSetting("Delete " + preset.name(), "Delete", () -> {
                        presets.deletePreset(preset.name()); ImportedSettings.save();
                    }));
                }
                return result;
            }
        }
        Draft draft = new Draft();
        return ClickGuiScreen.editor("Crafter presets", parent, draft::fields);
    }
}
