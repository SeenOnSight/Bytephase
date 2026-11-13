package dev.bytephase.module.combat;

import com.google.gson.JsonParser;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.pvp.api.CombatModule;
import dev.bytephase.pvp.api.CombatSetting;
import dev.bytephase.setting.*;
import java.util.ArrayList;
import java.util.List;

/** Adapts imported combat settings and lifecycle to the existing catalog. */
public final class ImportedCombat extends Module {
    private final CombatModule delegate;
    private final List<Binding> bindings = new ArrayList<>();

    private static final class Binding {
        final CombatSetting source;
        final Setting<?> local;
        long revision;
        boolean pulling;

        Binding(CombatSetting source, Setting<?> local) {
            this.source = source;
            this.local = local;
            revision = source.revision();
            local.visibleWhen(source::visible);
            local.onChange(value -> {
                if (!pulling) {
                    source.json(value.toString());
                    revision = source.revision();
                    pull();
                }
            });
        }

        void pull() {
            pulling = true;
            try {
                local.fromJson(JsonParser.parseString(source.json()));
                revision = source.revision();
            } finally { pulling = false; }
        }
    }

    public ImportedCombat(CombatModule source) {
        super(source.name(), Category.COMBAT, source.description());
        delegate = source;
        for (CombatSetting setting : source.settings()) {
            var value = JsonParser.parseString(setting.json());
            Setting<?> local = switch (setting.kind()) {
                case BOOLEAN -> new BooleanSetting(setting.name(), value.getAsBoolean());
                case NUMBER -> new NumberSetting(setting.name(), value.getAsDouble(), setting.min(), setting.max(), setting.step());
                case MODE -> new ModeSetting(setting.name(), setting.modes(), value.getAsString());
                case COLOR -> new ColorSetting(setting.name(), value.getAsInt());
                case KEY -> new ActionKeySetting(setting.name(), value.getAsInt());
            };
            add(local);
            bindings.add(new Binding(setting, local));
        }
    }

    @Override
    protected void onEnable() { delegate.setEnabled(true); }

    @Override
    protected void onDisable() { delegate.setEnabled(false); }

    @Override
    public boolean usesProjection() { return delegate.usesProjection(); }

    @Override
    public void onTickAlways() {
        if (isEnabled() != delegate.isEnabled()) setEnabled(delegate.isEnabled());
        for (Binding binding : bindings) if (binding.revision != binding.source.revision()) binding.pull();
    }

    @Override
    public String getHudText() { return delegate.showInHud() ? getName() : ""; }
}
