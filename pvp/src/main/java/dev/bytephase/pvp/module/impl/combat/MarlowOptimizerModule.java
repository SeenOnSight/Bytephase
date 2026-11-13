package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.chat.ModuleChat;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.marlow.MarlowCrystal;
import dev.bytephase.pvp.marlow.MarlowNetworking;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;

public final class MarlowOptimizerModule extends ClientModule {
    private final BooleanSetting keepRender = addSetting("keep-render", new BooleanSetting("KeepRender", false));
    private ClientLevel trackedLevel;

    public MarlowOptimizerModule() {
        super("marlow-optimizer", "Marlow Optimizer",
                "Tracks recently broken crystals so follow-up attacks and placement can proceed before server cleanup",
                ModuleCategory.COMBAT);
    }

    public boolean keepRender() { return keepRender.get(); }

    @Override
    protected void onEnable() {
        if (FabricLoader.getInstance().isModLoaded("marlowcrystal")) {
            ModuleChat.send(this, ModuleChat.body().warn("Remove the standalone optimizer JAR to use this module"));
            requestDisable();
            return;
        }
        var other = ModuleRegistry.get(CrystalOptimizerModule.class);
        if (other != null && other.isEnabled()) other.setEnabled(false);
        MarlowCrystal.get().clearTransient();
        trackedLevel = Minecraft.getInstance().level;
        MarlowNetworking.announce();
        if (MarlowCrystal.get().serverSession().requestedOptOut()) {
            ModuleChat.actionBar(this, ModuleChat.body().warn("Optimizer disabled by this server"));
        }
    }

    @Override
    protected void onDisable() {
        MarlowCrystal.get().clearTransient();
        trackedLevel = null;
    }

    @EventHandler
    public void onTick(TickEvent event) {
        var level = Minecraft.getInstance().level;
        if (level != trackedLevel) {
            MarlowCrystal.get().clearTransient();
            trackedLevel = level;
        }
    }
}
