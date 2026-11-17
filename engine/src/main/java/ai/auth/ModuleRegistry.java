package ai.auth;

import dev.bytephase.engine.entity.ClientThreadUtils;
import dev.bytephase.engine.gui.Button;
import dev.bytephase.engine.gui.TickProgress;
import dev.bytephase.engine.gui.ZoomHud;
import dev.bytephase.engine.render.QueueCommand;
import dev.bytephase.engine.render.VertexBuilderUtils;
import dev.bytephase.engine.world.MathClampUtils;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.util.math.MatrixStack;
import dev.bytephase.engine.categories.bots.AutoElytraPilot;
import dev.bytephase.engine.categories.bots.AutoTpaHere;
import dev.bytephase.engine.categories.bots.CrystalPvpBot;
import dev.bytephase.engine.utils.PlayerInputOverride;
import dev.bytephase.engine.categories.bots.SwordPvpBot;
import dev.bytephase.engine.categories.combat.AimAssist;
import dev.bytephase.engine.categories.combat.AnchorMacro;
import dev.bytephase.engine.categories.combat.AutoAggroPearl;
import dev.bytephase.engine.categories.combat.AutoCart;
import dev.bytephase.engine.categories.combat.AutoClicker;
import dev.bytephase.engine.categories.combat.AutoHitCrystal;
import dev.bytephase.engine.categories.combat.AutoHotbar;
import dev.bytephase.engine.categories.combat.AutoInventoryTotem;
import dev.bytephase.engine.categories.combat.AutoJumpReset;
import dev.bytephase.engine.categories.combat.AutoShieldBreaker;
import dev.bytephase.engine.categories.combat.AutoTotem;
import dev.bytephase.engine.categories.combat.AutoTrap;
import dev.bytephase.engine.categories.combat.AutoXP;
import dev.bytephase.engine.categories.combat.Criticals;
import dev.bytephase.engine.categories.combat.CrystalAura;
import dev.bytephase.engine.categories.combat.CrystalOptimizer;
import dev.bytephase.engine.categories.combat.HitBoxes;
import dev.bytephase.engine.categories.combat.HitSelect;
import dev.bytephase.engine.categories.combat.HitSwap;
import dev.bytephase.engine.categories.combat.KeyPearl;
import dev.bytephase.engine.categories.combat.MaceBomber;
import dev.bytephase.engine.categories.combat.ObsidianOptimizer;
import dev.bytephase.engine.categories.combat.PerfectWindCharge;
import dev.bytephase.engine.categories.combat.Reach;
import dev.bytephase.engine.categories.combat.SafeAnchor;
import dev.bytephase.engine.categories.combat.SilentAim;
import dev.bytephase.engine.categories.combat.TridentAssist;
import dev.bytephase.engine.categories.combat.TriggerBot;
import dev.bytephase.engine.categories.combat.WTap;
import dev.bytephase.engine.categories.bots.AnchorHunter;
import dev.bytephase.engine.categories.donutsmp.AntiVanish;
import dev.bytephase.engine.categories.bots.AutoAreaMiner;
import dev.bytephase.engine.categories.bots.AutoAuctionFlipper;
import dev.bytephase.engine.categories.bots.AutoBuilder;
import dev.bytephase.engine.categories.bots.AutoElytraFinder;
import dev.bytephase.engine.categories.donutsmp.AutoMine;
import dev.bytephase.engine.categories.donutsmp.AutoSell;
import dev.bytephase.engine.categories.donutsmp.AutoSellChest;
import dev.bytephase.engine.categories.donutsmp.Bloodhound;
import dev.bytephase.engine.categories.donutsmp.BoneDropper;
import dev.bytephase.engine.categories.donutsmp.ChunkFinder;
import dev.bytephase.engine.categories.donutsmp.ChunkFinderV2;
import dev.bytephase.engine.categories.donutsmp.DeepPlayerFinder;
import dev.bytephase.engine.categories.donutsmp.FakePay;
import dev.bytephase.engine.categories.donutsmp.FakeStats;
import dev.bytephase.engine.categories.donutsmp.GamblingRigger;
import dev.bytephase.engine.categories.donutsmp.NetheriteFinder;
import dev.bytephase.engine.categories.donutsmp.RegionMap;
import dev.bytephase.engine.categories.donutsmp.Relog;
import dev.bytephase.engine.categories.bots.RtpBaseAlert;
import dev.bytephase.engine.categories.donutsmp.SeedAnalyzer;
import dev.bytephase.engine.categories.donutsmp.SilentHome;
import dev.bytephase.engine.categories.bots.SpawnerProtect;
import dev.bytephase.engine.categories.donutsmp.SpecialItemBuyer;
import dev.bytephase.engine.categories.donutsmp.StaffList;
import dev.bytephase.engine.categories.donutsmp.SusChunkFinder;
import dev.bytephase.engine.categories.donutsmp.SusChunkFinderV2;
import dev.bytephase.engine.categories.bots.VillagerTradeBot;
import dev.bytephase.engine.categories.donutsmp.WanderingTraderFinder;
import dev.bytephase.engine.categories.misc.ACAlert;
import dev.bytephase.engine.categories.misc.AutoPot;
import dev.bytephase.engine.categories.misc.AutoPromo;
import dev.bytephase.engine.categories.misc.AutoTool;
import dev.bytephase.engine.categories.misc.BoatFly;
import dev.bytephase.engine.categories.misc.Capes;
import dev.bytephase.engine.utils.Cooldown;
import dev.bytephase.engine.categories.misc.BytephaseRadio;
import dev.bytephase.engine.categories.misc.CreamyKeys;
import dev.bytephase.engine.categories.misc.CustomCrosshair;
import dev.bytephase.engine.categories.misc.CustomSkin;
import dev.bytephase.engine.categories.misc.FOVSlider;
import dev.bytephase.engine.categories.misc.FakeCoordinates;
import dev.bytephase.engine.categories.misc.FakePlayer;
import dev.bytephase.engine.categories.misc.FastPlace;
import dev.bytephase.engine.categories.misc.Fastbreak;
import dev.bytephase.engine.categories.misc.InventoryCleaner;
import dev.bytephase.engine.categories.misc.NameProtect;
import dev.bytephase.engine.categories.misc.PotionRefill;
import dev.bytephase.engine.categories.misc.SkinProtect;
import dev.bytephase.engine.categories.misc.TimeChanger;
import dev.bytephase.engine.categories.misc.WeatherChanger;
import dev.bytephase.engine.categories.misc.WeatherNotifier;
import dev.bytephase.engine.categories.misc.Zoom;
import dev.bytephase.engine.categories.movement.AutoSpear;
import dev.bytephase.engine.categories.movement.AutoWalk;
import dev.bytephase.engine.categories.movement.Flight;
import dev.bytephase.engine.categories.movement.Freecam;
import dev.bytephase.engine.categories.movement.KeepSprint;
import dev.bytephase.engine.categories.movement.PearlCatchAssist;
import dev.bytephase.engine.categories.movement.Scaffold;
import dev.bytephase.engine.categories.movement.SpeedHack;
import dev.bytephase.engine.categories.movement.Sprint;
import dev.bytephase.engine.utils.AutoVanish;
import dev.bytephase.engine.categories.network.BackTrack;
import dev.bytephase.engine.categories.network.Blink;
import dev.bytephase.engine.utils.ConnectionTimeUtils;
import dev.bytephase.engine.utils.EntityDamageUtils;
import dev.bytephase.engine.categories.network.FakeLag;
import dev.bytephase.engine.utils.InventoryPlayer;
import dev.bytephase.engine.categories.network.KnockbackDelay;
import dev.bytephase.engine.categories.settings.ClientSounds;
import dev.bytephase.engine.categories.configs.Configs;
import dev.bytephase.engine.categories.socials.Friends;
import dev.bytephase.engine.categories.settings.GUISettings;
import dev.bytephase.engine.categories.settings.License;
import dev.bytephase.engine.categories.settings.MiddleClickFriend;
import dev.bytephase.engine.categories.settings.Notifications;
import dev.bytephase.engine.categories.settings.Panic;
import dev.bytephase.engine.categories.settings.SelfDestruct;
import dev.bytephase.engine.categories.theme.ThemeCustomizer;
import dev.bytephase.engine.categories.visuals.AtmosphericParticles;
import dev.bytephase.engine.categories.visuals.BlockESP;
import dev.bytephase.engine.categories.visuals.BlockHighlight;
import dev.bytephase.engine.categories.visuals.BlockOverlay;
import dev.bytephase.engine.categories.visuals.CustomFlame;
import dev.bytephase.engine.categories.visuals.CustomSky;
import dev.bytephase.engine.categories.visuals.CustomWorld;
import dev.bytephase.engine.categories.visuals.FireFly;
import dev.bytephase.engine.categories.visuals.Freelook;
import dev.bytephase.engine.categories.visuals.Fullbright;
import dev.bytephase.engine.categories.visuals.HandCustomer;
import dev.bytephase.engine.categories.visuals.HandShader;
import dev.bytephase.engine.categories.visuals.HeatColor;
import dev.bytephase.engine.categories.visuals.HoleESP;
import dev.bytephase.engine.categories.visuals.JumpCircles;
import dev.bytephase.engine.categories.visuals.LootESP;
import dev.bytephase.engine.categories.visuals.MobESP;
import dev.bytephase.engine.categories.visuals.MotionBlur;
import dev.bytephase.engine.categories.visuals.NameTags;
import dev.bytephase.engine.categories.visuals.NoExplosions;
import dev.bytephase.engine.categories.visuals.NoFarmLag;
import dev.bytephase.engine.categories.visuals.NoFlame;
import dev.bytephase.engine.categories.visuals.Particles;
import dev.bytephase.engine.categories.visuals.PearlCatch;
import dev.bytephase.engine.categories.visuals.PearlPrediction;
import dev.bytephase.engine.categories.visuals.PlayerESP;
import dev.bytephase.engine.categories.visuals.RealisticWater;
import dev.bytephase.engine.categories.visuals.ShowHUD;
import dev.bytephase.engine.categories.visuals.SpawnerFinder;
import dev.bytephase.engine.categories.visuals.SpotifyHUD;
import dev.bytephase.engine.categories.visuals.StorageESP;
import dev.bytephase.engine.categories.visuals.SwordInspect;
import dev.bytephase.engine.categories.visuals.TargetESP;
import dev.bytephase.engine.categories.visuals.TotemCounter;
import dev.bytephase.engine.categories.visuals.Trails;
import dev.bytephase.engine.categories.visuals.Waypoints;
import ai.auth.Module;
import dev.bytephase.engine.utils.DeltaAngle;
import dev.bytephase.engine.utils.DirExtractionUtils;
import dev.bytephase.engine.utils.ExtUtils;
import dev.bytephase.engine.utils.IconsPngType;
import dev.bytephase.engine.utils.InstanceTimeUtils;
import dev.bytephase.engine.utils.PlayerScaffold;
import dev.bytephase.engine.utils.ScaffoldTrade;
import dev.bytephase.engine.utils.SwordBot;

public class ModuleRegistry {
    private static final Map<Class<? extends Module>, Module> modulesByClass;
    private static final List<Module> modules;
    private static final Map<IconsPngType, List<Module>> modulesByCategory;
    private static final Map<Class<?>, List<Module>> callbacks = new java.util.HashMap<>();
    private static final Map<Class<?>, String> HOOKS = Map.ofEntries(
        Map.entry(Void.class, "onReset"),
        Map.entry(InventoryPlayer.class, "onPacketSend"),
        Map.entry(AutoVanish.class, "onPacketReceive"),
        Map.entry(QueueCommand.class, "onRender3D"),
        Map.entry(TickProgress.class, "onRender2D"),
        Map.entry(ZoomHud.class, "onZoomHud"),
        Map.entry(Cooldown.class, "onPostItemUse"),
        Map.entry(ScaffoldTrade.class, "onPreItemUse"),
        Map.entry(Button.class, "onButtonClick"),
        Map.entry(SwordBot.class, "onSwordBot"),
        Map.entry(DeltaAngle.class, "onCameraAngle"),
        Map.entry(PlayerScaffold.class, "onPlayerScaffold"));

    static {
        modules = new ArrayList<>();
        modulesByCategory = new EnumMap<>(IconsPngType.class);
        modulesByClass = new IdentityHashMap<>();
    }

    private static void registerModule(Module module) {
        if (modulesByClass.putIfAbsent(module.getClass(), module) != null) return;
        if (!dev.bytephase.module.engine.BytephaseIntegration.existing(module.getClass())) {
            modules.add(module);
            modulesByCategory.get(module.getPageType()).add(module);
            for (var hook : HOOKS.entrySet()) {
                if (overrides(module.getClass(), hook.getValue(), hook.getKey()))
                    callbacks.computeIfAbsent(hook.getKey(), ignored -> new ArrayList<>()).add(module);
            }
        }
    }

    private static boolean overrides(Class<?> type, String method, Class<?> parameter) {
        for (; type != null && type != Module.class; type = type.getSuperclass()) {
            try {
                if (parameter == Void.class) type.getDeclaredMethod(method);
                else type.getDeclaredMethod(method, parameter);
                return true;
            } catch (NoSuchMethodException ignored) { }
        }
        return false;
    }

    private static List<Module> callbacks(Class<?> event) {
        return callbacks.getOrDefault(event, List.of());
    }

    public static Module getModule(Class<? extends Module> clazz) {
        return modulesByClass.get(clazz);
    }

    public static List<Module> getModules() {
        return modules;
    }

    public static List<Module> getModulesByCategory(IconsPngType category) {
        return modulesByCategory.getOrDefault(category, new ArrayList<>());
    }

    public static void registerAllModules() {
        if (!modulesByClass.isEmpty()) return;
        IconsPngType[] categories = IconsPngType.values();
        for (int i = 0; i < categories.length; ++i) {
            modulesByCategory.put(categories[i], new ArrayList<>());
        }
        registerModule(new ACAlert());
        registerModule(new AimAssist());
        registerModule(new AnchorHunter());
        registerModule(new AnchorMacro());
        registerModule(new AntiVanish());
        registerModule(new AtmosphericParticles());
        registerModule(new AutoAggroPearl());
        registerModule(new AutoAreaMiner());
        registerModule(new AutoAuctionFlipper());
        registerModule(new AutoBuilder());
        registerModule(new AutoCart());
        registerModule(new AutoClicker());
        registerModule(new AutoElytraFinder());
        registerModule(new AutoElytraPilot());
        registerModule(new AutoHitCrystal());
        registerModule(new AutoHotbar());
        registerModule(new AutoInventoryTotem());
        registerModule(new AutoJumpReset());
        registerModule(new AutoMine());
        registerModule(new AutoPot());
        registerModule(new AutoPromo());
        registerModule(new AutoSell());
        registerModule(new AutoSellChest());
        registerModule(new AutoShieldBreaker());
        registerModule(new AutoSpear());
        registerModule(new AutoTool());
        registerModule(new AutoTotem());
        registerModule(new AutoTpaHere());
        registerModule(new AutoTrap());
        registerModule(new AutoWalk());
        registerModule(new AutoXP());
        registerModule(new BackTrack());
        registerModule(new Blink());
        registerModule(new BlockESP());
        registerModule(new BlockHighlight());
        registerModule(new BlockOverlay());
        registerModule(new Bloodhound());
        registerModule(new BoatFly());
        registerModule(new BoneDropper());
        registerModule(new Capes());
        registerModule(new ChunkFinder());
        registerModule(new ChunkFinderV2());
        registerModule(new ClientSounds());
        registerModule(new Configs());
        registerModule(new BytephaseRadio());
        registerModule(new CreamyKeys());
        registerModule(new Criticals());
        registerModule(new CrystalAura());
        registerModule(new CrystalOptimizer());
        registerModule(new CrystalPvpBot());
        registerModule(new CustomCrosshair());
        registerModule(new CustomFlame());
        registerModule(new CustomSkin());
        registerModule(new CustomSky());
        registerModule(new CustomWorld());
        registerModule(new DeepPlayerFinder());
        registerModule(new FakeCoordinates());
        registerModule(new FakeLag());
        registerModule(new FakePay());
        registerModule(new FakePlayer());
        registerModule(new FakeStats());
        registerModule(new Fastbreak());
        registerModule(new FastPlace());
        registerModule(new FireFly());
        registerModule(new Flight());
        registerModule(new FOVSlider());
        registerModule(new Freecam());
        registerModule(new Freelook());
        registerModule(new Friends());
        registerModule(new Fullbright());
        registerModule(new GamblingRigger());
        registerModule(new GUISettings());
        registerModule(new HandCustomer());
        registerModule(new HandShader());
        registerModule(new HeatColor());
        registerModule(new HitBoxes());
        registerModule(new HitSelect());
        registerModule(new HitSwap());
        registerModule(new HoleESP());
        registerModule(new InventoryCleaner());
        registerModule(new JumpCircles());
        registerModule(new KeepSprint());
        registerModule(new KeyPearl());
        registerModule(new KnockbackDelay());
        registerModule(new License());
        registerModule(new LootESP());
        registerModule(new MaceBomber());
        registerModule(new MiddleClickFriend());
        registerModule(new MobESP());
        registerModule(new MotionBlur());
        registerModule(new NameProtect());
        registerModule(new NameTags());
        registerModule(new NetheriteFinder());
        registerModule(new NoExplosions());
        registerModule(new NoFarmLag());
        registerModule(new NoFlame());
        registerModule(new Notifications());
        registerModule(new ObsidianOptimizer());
        registerModule(new Panic());
        registerModule(new Particles());
        registerModule(new PearlCatch());
        registerModule(new PearlCatchAssist());
        registerModule(new PearlPrediction());
        registerModule(new PerfectWindCharge());
        registerModule(new PlayerESP());
        registerModule(new PotionRefill());
        registerModule(new Reach());
        registerModule(new RealisticWater());
        registerModule(new RegionMap());
        registerModule(new Relog());
        registerModule(new RtpBaseAlert());
        registerModule(new SafeAnchor());
        registerModule(new Scaffold());
        registerModule(new SeedAnalyzer());
        registerModule(new SelfDestruct());
        registerModule(new ShowHUD());
        registerModule(new SilentAim());
        registerModule(new SilentHome());
        registerModule(new SkinProtect());
        registerModule(new SpawnerFinder());
        registerModule(new SpawnerProtect());
        registerModule(new SpecialItemBuyer());
        registerModule(new SpeedHack());
        registerModule(new SpotifyHUD());
        registerModule(new Sprint());
        registerModule(new StaffList());
        registerModule(new StorageESP());
        registerModule(new SusChunkFinder());
        registerModule(new SusChunkFinderV2());
        registerModule(new SwordInspect());
        registerModule(new SwordPvpBot());
        registerModule(new TargetESP());
        registerModule(new ThemeCustomizer());
        registerModule(new TimeChanger());
        registerModule(new TotemCounter());
        registerModule(new Trails());
        registerModule(new TridentAssist());
        registerModule(new TriggerBot());
        registerModule(new VillagerTradeBot());
        registerModule(new WanderingTraderFinder());
        registerModule(new Waypoints());
        registerModule(new WeatherChanger());
        registerModule(new WeatherNotifier());
        registerModule(new WTap());
        registerModule(new Zoom());
    }

    public static void onGameTick() {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        ExtUtils.updateFlagAction();
        ClientThreadUtils.updateOptionsTime();
        PlayerInputOverride.updateFlagTick();
        EntityDamageUtils.updateTimeInstance();
        ConnectionTimeUtils.update();
        Module autoBuilderModule = getModule(AutoBuilder.class);
        AutoBuilder autoBuilder = autoBuilderModule instanceof AutoBuilder ab ? ab : null;
        if (autoBuilder != null) {
            autoBuilder.updateTeamLeaving();
        }
        for (Module module : callbacks(Void.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onReset());
        }
        if (autoBuilder != null) {
            autoBuilder.updateRestockDestroyed();
            autoBuilder.updateCancelledWorld();
        }
        Module villagerModule = getModule(VillagerTradeBot.class);
        if (villagerModule instanceof VillagerTradeBot villagerTradeBot) {
            villagerTradeBot.updateVillagerTrader();
        }
        InstanceTimeUtils.updateDequeArray();
        MathClampUtils.updateFlagClamp();
    }

    public static void onEndClientTick() {
    }

    public static void dispatchPacketSend(InventoryPlayer packetSendEvent) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(InventoryPlayer.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onPacketSend(packetSendEvent));
        }
    }

    public static void dispatchPacketReceive(AutoVanish packetReceiveEvent) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(AutoVanish.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onPacketReceive(packetReceiveEvent));
        }
    }

    public static void dispatchRender3D(QueueCommand render3DEvent) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        VertexBuilderUtils.handleCameraMatrix((MatrixStack) render3DEvent.matrices);
        for (Module module : callbacks(QueueCommand.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onRender3D(render3DEvent));
        }
        Module autoBuilderModule = getModule(AutoBuilder.class);
        if (autoBuilderModule instanceof AutoBuilder autoBuilder) {
            autoBuilder.handleStateEnum(render3DEvent);
            autoBuilder.handleWriteCamera(render3DEvent);
        }
        Module areaMinerModule = getModule(AutoAreaMiner.class);
        if (areaMinerModule instanceof AutoAreaMiner autoAreaMiner && !autoAreaMiner.isEnabled()) {
            autoAreaMiner.handleProgressTick(render3DEvent);
        }
        Module villagerModule = getModule(VillagerTradeBot.class);
        if (villagerModule instanceof VillagerTradeBot villagerTradeBot) {
            villagerTradeBot.handlePickCamera(render3DEvent);
        }
        VertexBuilderUtils.updateDrawImmediate();
    }

    public static void dispatchRender2D(TickProgress render2DEvent) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(TickProgress.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.render.HudLayout.drawImported("module:" + dev.bytephase.config.EngineDataMigration.moduleName(
                dev.bytephase.module.engine.BytephaseIntegration.adapter(module).getName()), render2DEvent.drawContext,
                () -> dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onRender2D(render2DEvent)));
        }
        Module autoBuilderModule = getModule(AutoBuilder.class);
        if (autoBuilderModule instanceof AutoBuilder autoBuilder) {
            autoBuilder.handleChestPlayer(render2DEvent);
            autoBuilder.handleBaseLeaving(render2DEvent);
        }
        Module villagerModule = getModule(VillagerTradeBot.class);
        if (villagerModule instanceof VillagerTradeBot villagerTradeBot) {
            villagerTradeBot.handlePickRight(render2DEvent);
        }
    }

    public static void dispatchZoomHud(ZoomHud event) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(ZoomHud.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onZoomHud(event));
        }
    }

    public static void dispatchPostItemUse(Cooldown postItemUseEvent) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(Cooldown.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onPostItemUse(postItemUseEvent));
        }
    }

    public static void dispatchPreItemUse(ScaffoldTrade preItemUseEvent) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(ScaffoldTrade.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onPreItemUse(preItemUseEvent));
        }
        Module villagerModule = getModule(VillagerTradeBot.class);
        if (villagerModule instanceof VillagerTradeBot villagerTradeBot) {
            villagerTradeBot.handleVillagerTrader(preItemUseEvent);
        }
    }

    public static void dispatchButtonClick(Button event) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(Button.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onButtonClick(event));
        }
        Module autoBuilderModule = getModule(AutoBuilder.class);
        if (autoBuilderModule instanceof AutoBuilder autoBuilder) {
            autoBuilder.handleKeybindScreen(event);
        }
    }

    public static void dispatchSwordBot(SwordBot event) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(SwordBot.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onSwordBot(event));
        }
        Module autoBuilderModule = getModule(AutoBuilder.class);
        if (autoBuilderModule instanceof AutoBuilder autoBuilder) {
            autoBuilder.handleKeybindScreen(event);
        }
        Module villagerModule = getModule(VillagerTradeBot.class);
        if (villagerModule instanceof VillagerTradeBot villagerTradeBot) {
            villagerTradeBot.handleKeybindScreen(event);
        }
    }

    public static void dispatchCameraAngle(DeltaAngle cameraEvent) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(DeltaAngle.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onCameraAngle(cameraEvent));
        }
    }

    public static void dispatchPlayerScaffold(PlayerScaffold event) {
        if (DirExtractionUtils.isFlag()) {
            return;
        }
        for (Module module : callbacks(PlayerScaffold.class)) {
            if (!module.isEnabled()) continue;
            dev.bytephase.module.engine.BytephaseIntegration.run(module, () -> module.onPlayerScaffold(event));
        }
    }
}
