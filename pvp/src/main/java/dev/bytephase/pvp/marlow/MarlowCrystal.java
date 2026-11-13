package dev.bytephase.pvp.marlow;

import dev.bytephase.pvp.marlow.crystal.CrystalBreaker;
import dev.bytephase.pvp.marlow.crystal.KeptCrystals;
import dev.bytephase.pvp.marlow.crystal.PastKeptCrystal;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.MarlowOptimizerModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.HitResult;

/** Module-owned state for the optimizer imported from the supplied 2.0.0 JAR. */
public final class MarlowCrystal {
    private static final MarlowCrystal INSTANCE = new MarlowCrystal();
    private final KeptCrystals kept = new KeptCrystals();
    private final CrystalBreaker breaker = new CrystalBreaker(kept);
    private final PastKeptCrystal past = new PastKeptCrystal(kept);
    private final RenderSettings settings = new RenderSettings();
    private final ServerSession session = new ServerSession();

    private MarlowCrystal() { }
    public static MarlowCrystal get() { return INSTANCE; }
    public static boolean enabled() {
        var module = ModuleRegistry.get(MarlowOptimizerModule.class);
        return module != null && module.isEnabled();
    }
    public static boolean active() { return enabled() && !INSTANCE.session.requestedOptOut(); }
    public static boolean hidden(Entity entity) { return active() && INSTANCE.kept.isHidden(entity); }
    public static HitResult raycast(LocalPlayer player) {
        return active() ? INSTANCE.kept.seePast(() -> player.raycastHitResult(1.0F, player))
                : player.raycastHitResult(1.0F, player);
    }
    public KeptCrystals keptCrystals() { return kept; }
    public CrystalBreaker crystalBreaker() { return breaker; }
    public PastKeptCrystal pastKeptCrystal() { return past; }
    public RenderSettings settings() { return settings; }
    public ServerSession serverSession() { return session; }

    public void clearTransient() {
        past.clear(Minecraft.getInstance());
        kept.clear();
    }

    public static final class RenderSettings {
        public boolean isKeepRender() {
            var module = ModuleRegistry.get(MarlowOptimizerModule.class);
            return !active() || module == null || module.keepRender();
        }
    }
}
