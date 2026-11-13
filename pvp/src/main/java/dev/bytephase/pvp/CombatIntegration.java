package dev.bytephase.pvp;

import com.google.gson.JsonParser;
import dev.bytephase.pvp.api.*;
import dev.bytephase.pvp.event.EventBus;
import dev.bytephase.pvp.event.impl.Render2DEvent;
import dev.bytephase.pvp.event.impl.Render3DEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.gui.OverlayCanvas;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.HudOverlayProvider;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.setting.*;
import dev.bytephase.pvp.util.combat.CombatTarget;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Exposes the imported combat catalog through the existing Bytephase GUI. */
public final class CombatIntegration implements CombatProvider {
    private static CombatServices services;
    private static boolean initialized;
    private List<CombatModule> handles;

    public static CombatServices services() { return services; }

    @Override
    public List<CombatModule> modules() {
        if (handles == null) {
            List<ClientModule> catalog = List.of(
                    new dev.bytephase.pvp.module.impl.combat.AimAssistModule(),
                    new dev.bytephase.pvp.module.impl.combat.AnchorAuraModule(),
                    new dev.bytephase.pvp.module.impl.combat.AutoCityModule(),
                    new dev.bytephase.pvp.module.impl.combat.AutoInvTotemModule(),
                    new dev.bytephase.pvp.module.impl.combat.AutoTotemModule(),
                    new dev.bytephase.pvp.module.impl.combat.AutoTrapModule(),
                    new dev.bytephase.pvp.module.impl.combat.bot.CrystalBotModule(),
                    new dev.bytephase.pvp.module.impl.combat.bot.SwordBotModule(),
                    new dev.bytephase.pvp.module.impl.combat.CrystalAuraModule(),
                    new dev.bytephase.pvp.module.impl.combat.CrystalMacroModule(),
                    new dev.bytephase.pvp.module.impl.combat.CrystalOptimizerModule(),
                    new dev.bytephase.pvp.module.impl.combat.MarlowOptimizerModule(),
                    new dev.bytephase.pvp.module.impl.combat.FakeLagModule(),
                    new dev.bytephase.pvp.module.impl.combat.FastXpModule(),
                    new dev.bytephase.pvp.module.impl.combat.FriendGuardModule(),
                    new dev.bytephase.pvp.module.impl.combat.HoverTotemModule(),
                    new dev.bytephase.pvp.module.impl.combat.JumpResetModule(),
                    new dev.bytephase.pvp.module.impl.combat.KeyThrowModule(),
                    new dev.bytephase.pvp.module.impl.combat.KillAuraModule(),
                    new dev.bytephase.pvp.module.impl.combat.KnockbackDelayModule(),
                    new dev.bytephase.pvp.module.impl.combat.MaceModule(),
                    new dev.bytephase.pvp.module.impl.combat.ShieldBreakerModule(),
                    new dev.bytephase.pvp.module.impl.combat.SingleAnchorMacroModule(),
                    new dev.bytephase.pvp.module.impl.combat.SpearLungeModule(),
                    new dev.bytephase.pvp.module.impl.combat.SwordObsidianModule(),
                    new dev.bytephase.pvp.module.impl.combat.TriggerbotModule(),
                    new dev.bytephase.pvp.module.impl.combat.WTapRecodeModule()
            );
            List<CombatModule> result = new ArrayList<>();
            for (ClientModule module : catalog) {
                ModuleRegistry.add(module);
                result.add(new Handle(module));
            }
            handles = List.copyOf(result);
        }
        return handles;
    }

    @Override
    public void initialize(CombatServices bridge) {
        services = bridge;
        if (!initialized) {
            CombatTarget.install();
            dev.bytephase.pvp.command.FriendCommand.register();
            dev.bytephase.pvp.marlow.MarlowNetworking.initialize();
            initialized = true;
        }
    }

    @Override
    public void beginTick() {
        EventBus.INSTANCE.drainAsync();
        dev.bytephase.pvp.util.pvp.InputControl.beginTick();
        ServerRotation.startTick();
        TickEvent.dispatch();
    }

    @Override
    public void endTick() { TickEvent.dispatchPost(); }

    @Override
    public boolean sprintSuppressed() {
        return dev.bytephase.pvp.util.pvp.InputControl.sprintSuppressed();
    }

    @Override
    public List<String> friends() { return dev.bytephase.pvp.config.FriendStore.getInstance().all(); }
    @Override
    public boolean addFriend(String name) { return dev.bytephase.pvp.config.FriendStore.getInstance().add(name); }
    @Override
    public boolean removeFriend(String name) { return dev.bytephase.pvp.config.FriendStore.getInstance().remove(name); }

    @Override
    public void renderHud(Object graphics, float tickDelta) {
        if (services != null && services.isCleanCapture()) return;
        GuiGraphics gui = (GuiGraphics) graphics;
        Render2DEvent.dispatch(gui, tickDelta);
        List<HudOverlayProvider> overlays = ModuleRegistry.hudOverlays();
        if (overlays.isEmpty()) return;
        OverlayCanvas canvas = null;
        Minecraft mc = Minecraft.getInstance();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();
        for (HudOverlayProvider overlay : overlays) {
            if (overlay.isHudOverlayEnabled()) {
                if (canvas == null) canvas = new OverlayCanvas(gui);
                overlay.renderHudOverlay(canvas, width, height);
            }
        }
    }

    @Override
    public void renderWorld(float tickDelta) {
        if (services == null || !services.isCleanCapture()) Render3DEvent.dispatch(tickDelta);
    }

    @Override
    public void renderWorldOverlays(float tickDelta) {
        if (services == null || services.isCleanCapture() || !services.supportsWorldOverlays()) return;
        var lag = ModuleRegistry.get(dev.bytephase.pvp.module.impl.combat.FakeLagModule.class);
        if (lag != null && lag.isEnabled()) lag.renderWorldOverlay();
    }

    private static final class Handle implements CombatModule {
        private final ClientModule module;
        private final List<CombatSetting> settings;
        private final boolean usesProjection;

        Handle(ClientModule module) {
            this.module = module;
            usesProjection = module instanceof HudOverlayProvider
                || java.util.Arrays.stream(module.getClass().getMethods()).anyMatch(method ->
                    method.getParameterCount() == 1
                        && (method.getParameterTypes()[0] == Render2DEvent.class
                            || method.getParameterTypes()[0] == Render3DEvent.class));
            settings = module.settings().stream()
                    .filter(setting -> !setting.id().equals("toggle-message"))
                    .map(SettingHandle::new).map(CombatSetting.class::cast).toList();
        }

        public String id() { return module.id(); }
        public String name() { return module.name().replace(" ", ""); }
        public String description() { return module.description(); }
        public List<CombatSetting> settings() { return settings; }
        public boolean isEnabled() { return module.isEnabled(); }
        public void setEnabled(boolean enabled) { module.setEnabled(enabled); }
        public boolean showInHud() { return module.showInHud() == null || module.showInHud().get(); }
        public boolean usesProjection() { return usesProjection; }
    }

    private static final class SettingHandle implements CombatSetting {
        private final Setting setting;

        SettingHandle(Setting setting) { this.setting = setting; }
        public String id() { return setting.id(); }
        public String name() { return setting.name(); }
        public Kind kind() {
            if (setting instanceof BooleanSetting) return Kind.BOOLEAN;
            if (setting instanceof NumberSetting) return Kind.NUMBER;
            if (setting instanceof ModeSetting) return Kind.MODE;
            if (setting instanceof ColorSetting) return Kind.COLOR;
            if (setting instanceof KeySetting) return Kind.KEY;
            throw new IllegalArgumentException("Unsupported combat setting: " + setting.getClass().getName());
        }
        public String json() {
            return setting instanceof ColorSetting color ? Integer.toString(color.argb()) : setting.serialize().toString();
        }
        public void json(String value) { setting.deserialize(JsonParser.parseString(value)); }
        public long revision() { return setting.revision(); }
        public boolean visible() { return setting.isVisible(); }
        public double min() { return setting instanceof NumberSetting number ? number.min() : 0; }
        public double max() { return setting instanceof NumberSetting number ? number.max() : 1; }
        public double step() {
            return setting instanceof NumberSetting number
                    ? number.step() > 0 ? number.step() : Math.pow(10, -number.decimals()) : 1;
        }
        public String[] modes() {
            return setting instanceof ModeSetting mode ? mode.modes().toArray(String[]::new) : new String[0];
        }
    }
}
