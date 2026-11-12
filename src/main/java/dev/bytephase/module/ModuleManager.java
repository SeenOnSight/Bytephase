package dev.bytephase.module;

import dev.bytephase.BytePhase;
import dev.bytephase.module.combat.AimAssist;
import dev.bytephase.module.combat.AutoArmor;
import dev.bytephase.module.combat.AutoClicker;
import dev.bytephase.module.combat.AutoTotem;
import dev.bytephase.module.combat.AutoWeapon;
import dev.bytephase.module.combat.JumpReset;
import dev.bytephase.module.combat.SprintReset;
import dev.bytephase.module.combat.WTap;
import dev.bytephase.module.misc.AntiPacketKick;
import dev.bytephase.module.misc.AntiVanish;
import dev.bytephase.module.misc.SlowSwing;
import dev.bytephase.module.misc.AutoReconnect;
import dev.bytephase.module.misc.CoordLogger;
import dev.bytephase.module.misc.CoordSnapper;
import dev.bytephase.module.misc.KillEffects;
import dev.bytephase.module.misc.AntiSpam;
import dev.bytephase.module.misc.ChatFilter;
import dev.bytephase.module.misc.Notifications;
import dev.bytephase.module.misc.UnfocusedFps;
import dev.bytephase.module.misc.NameProtect;
import dev.bytephase.module.misc.PMSound;
import dev.bytephase.module.misc.PacketLogger;
import dev.bytephase.module.misc.VisualRange;
import dev.bytephase.module.movement.AutoJump;
import dev.bytephase.module.movement.AutoWalk;
import dev.bytephase.module.movement.InventoryMove;
import dev.bytephase.module.movement.Parkour;
import dev.bytephase.module.movement.SafeWalk;
import dev.bytephase.module.movement.Sneak;
import dev.bytephase.module.movement.Sprint;
import dev.bytephase.module.movement.Yaw;
import dev.bytephase.module.player.AntiAfk;
import dev.bytephase.module.player.AutoEat;
import dev.bytephase.module.player.AutoReplenish;
import dev.bytephase.module.player.HotbarArrange;
import dev.bytephase.module.player.TradeAssist;
import dev.bytephase.module.player.AutoFish;
import dev.bytephase.module.player.AutoRespawn;
import dev.bytephase.module.player.AutoTool;
import dev.bytephase.module.player.ChestStealer;
import dev.bytephase.module.player.ChestSwap;
import dev.bytephase.module.player.Offhand;
import dev.bytephase.module.player.RotationLock;
import dev.bytephase.module.render.ArrayListView;
import dev.bytephase.module.render.ArmorHud;
import dev.bytephase.module.render.BetterChat;
import dev.bytephase.module.render.BlockEsp;
import dev.bytephase.module.render.BlockHighlight;
import dev.bytephase.module.render.BreadCrumbs;
import dev.bytephase.module.render.Coordinates;
import dev.bytephase.module.render.Crosshair;
import dev.bytephase.module.render.CustomFov;
import dev.bytephase.module.render.ExtraTab;
import dev.bytephase.module.render.Fps;
import dev.bytephase.module.render.Fullbright;
import dev.bytephase.module.render.Hitmarker;
import dev.bytephase.module.render.HideArmor;
import dev.bytephase.module.render.HoleEsp;
import dev.bytephase.module.render.InventoryInfo;
import dev.bytephase.module.render.ItemEsp;
import dev.bytephase.module.render.LogoutSpots;
import dev.bytephase.module.render.LowHealthWarning;
import dev.bytephase.module.render.Nametags;
import dev.bytephase.module.render.MiningProgress;
import dev.bytephase.module.render.NoBob;
import dev.bytephase.module.render.NoRender;
import dev.bytephase.module.render.NoWeather;
import dev.bytephase.module.render.OldPotions;
import dev.bytephase.module.render.Ping;
import dev.bytephase.module.render.PlayerEsp;
import dev.bytephase.module.render.Skeleton;
import dev.bytephase.module.render.SmallShield;
import dev.bytephase.module.render.StatusEffects;
import dev.bytephase.module.render.StorageEsp;
import dev.bytephase.module.render.SkyShaders;
import dev.bytephase.module.render.TargetFx;
import dev.bytephase.module.render.SusChunks;
import dev.bytephase.module.render.TimeChanger;
import dev.bytephase.module.render.Tracers;
import dev.bytephase.module.render.Trajectories;
import dev.bytephase.module.render.ViewClip;
import dev.bytephase.module.render.ViewModel;
import dev.bytephase.module.render.Watermark;
import dev.bytephase.module.render.UiSettings;
import dev.bytephase.module.render.Zoom;
import dev.bytephase.module.world.BaseFinder;
import dev.bytephase.module.world.Navigator;
import dev.bytephase.module.world.TunnelMiner;
import dev.bytephase.render.EntitySnapshot;
import dev.bytephase.render.EspRenderer;
import dev.bytephase.render.RenderBox;
import dev.bytephase.render.esp.ClarityEspRenderer;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.Packet;

public final class ModuleManager {
    private final List<Module> modules = new ArrayList<>();
    private final List<Module> allView = Collections.unmodifiableList(modules);
    private final Map<Class<?>, Module> byClass = new HashMap<>();
    private final Map<String, Module> byName = new HashMap<>();
    private final Map<Category, List<Module>> byCategory = new EnumMap<>(Category.class);
    private final Map<Module, ErrorState> errorStates = new ConcurrentHashMap<>();

    /** Errors in quick succession before a module is switched off instead of throwing forever. */
    private static final int MAX_RAPID_ERRORS = 40;
    /** An error older than this no longer counts toward {@link #MAX_RAPID_ERRORS}. */
    private static final long ERROR_WINDOW_NANOS = 10_000_000_000L;

    private static final class ErrorState {
        int count;
        long lastNanos;
    }

    /*
     * These are immutable snapshots, rebuilt only when a module is toggled.
     * The packet handlers run on the Netty thread while the GUI/config toggle
     * modules on the client thread, so dispatching from a mutable list would
     * introduce a data race in the network hot path.
     */
    private volatile List<Module> enabledModules = List.of();
    private volatile List<Module> activeHud = List.of();
    private volatile List<Module> activeWorld = List.of();
    private volatile List<Module> activeFrame = List.of();
    private volatile List<Module> activePacketSenders = List.of();
    private volatile List<Module> activePacketReceivers = List.of();
    private volatile List<Module> activeBeforeAttack = List.of();
    private volatile List<Module> activeAttack = List.of();
    private volatile int snapshotConsumers;
    private volatile boolean projectionNeeded;

    // Capability sets are computed once at startup by checking which methods a
    // module actually overrides. Calling empty onRender/onPacket hooks for all
    // 100 modules was measurable on the render and network threads.
    private final Set<Module> hudRenderers = new HashSet<>();
    private final Set<Module> worldRenderers = new HashSet<>();
    private final Set<Module> frameHandlers = new HashSet<>();
    private final Set<Module> packetSenders = new HashSet<>();
    private final Set<Module> packetReceivers = new HashSet<>();
    private final Set<Module> beforeAttackHandlers = new HashSet<>();
    private final Set<Module> attackHandlers = new HashSet<>();
    private final List<Module> alwaysTickModules = new ArrayList<>();

    /*
     * Direct references for mixins that sit in very hot engine methods. The
     * old path did a HashMap lookup for every bounding box, step-height query
     * and block slipperiness query even when the module was disabled.
     */

    private volatile Zoom zoom;
    private volatile CustomFov customFov;
    private volatile NoRender noRender;
    private volatile Crosshair crosshair;
    private volatile SkyShaders skyShaders;
    private volatile ArmorHud armorHud;
    private volatile Nametags nametags;
    private volatile PlayerEsp playerEsp;

    private volatile SlowSwing slowSwing;
    private volatile WTap wTap;
    private volatile SafeWalk safeWalk;

    private volatile AntiPacketKick antiPacketKick;
    private volatile PacketLogger packetLogger;

    private volatile LogoutSpots logoutSpots;

    private volatile BreadCrumbs breadCrumbs;
    private volatile Skeleton skeleton;
    private volatile NoWeather noWeather;
    private volatile OldPotions oldPotions;
    private volatile ViewClip viewClip;
    private volatile SmallShield smallShield;
    private volatile ViewModel viewModel;
    private volatile ExtraTab extraTab;
    private volatile InventoryInfo inventoryInfo;
    private volatile BetterChat betterChat;

    public void init() {
        // Combat
        add(new AutoTotem());
        add(new AutoArmor());
        add(new AimAssist());
        add(new AutoClicker());
        add(new AutoWeapon());
        add(new JumpReset());
        add(new WTap());
        add(new SprintReset());

        // Movement
        add(new Sprint());
        // Redirect after Speed has adjusted the horizontal magnitude.
        add(new AutoWalk());
        add(new InventoryMove());
        add(new SafeWalk());
        add(new AutoJump());
        add(new Parkour());
        add(new Sneak());
        add(new Yaw());

        // Player
        add(new AutoTool());
        add(new AutoEat());
        add(new ChestStealer());
        add(new AntiAfk());
        add(new AutoRespawn());
        add(new RotationLock());
        add(new AutoFish());
        add(new ChestSwap());
        add(new Offhand());
        add(new AutoReplenish());
        add(new HotbarArrange());
        add(new TradeAssist());

        // Render
        add(new Watermark());
        add(new Coordinates());
        add(new Fps());
        add(new Ping());
        add(new ArrayListView());
        add(new ArmorHud());
        add(new StatusEffects());
        add(new Tracers());
        add(new PlayerEsp());
        add(new Nametags());
        add(new StorageEsp());
        add(new HoleEsp());
        add(new BlockHighlight());
        add(new MiningProgress());
        add(new HideArmor());
        add(new Fullbright());
        add(new Crosshair());
        add(new Hitmarker());
        add(new Zoom());
        add(new NoBob());
        add(new Trajectories());
        add(new LogoutSpots());
        add(new TimeChanger());
        add(new NoRender());
        add(new CustomFov());
        add(new ItemEsp());
        add(new BlockEsp());
        add(new LowHealthWarning());
        add(new SusChunks());
        add(new TargetFx());
        add(new SkyShaders());
        add(new BreadCrumbs());
        add(new Skeleton());
        add(new NoWeather());
        add(new OldPotions());
        add(new ViewClip());
        add(new SmallShield());
        add(new ViewModel());
        add(new ExtraTab());
        add(new InventoryInfo());
        add(new BetterChat());

        // UI
        add(new UiSettings());

        // World
        add(new TunnelMiner());

        // Explore
        add(new BaseFinder());
        add(new Navigator());
        add(new CoordSnapper());

        // Misc
        add(new PacketLogger());
        add(new AutoReconnect());
        add(new VisualRange());
        add(new PMSound());
        add(new NameProtect());
        add(new ChatFilter());
        add(new AntiSpam());
        add(new UnfocusedFps());
        add(new Notifications());
        add(new CoordLogger());
        add(new KillEffects());
        add(new AntiPacketKick());
        add(new SlowSwing());
        add(new AntiVanish());

        rebuildActiveModules();
    }

    private void add(Module module) {
        modules.add(module);
        byClass.put(module.getClass(), module);
        byName.put(module.getName().toLowerCase(Locale.ROOT), module);

        byCategory.computeIfAbsent(module.getCategory(), category -> new ArrayList<>()).add(module);

        if (overrides(module, "onRender2D", DrawContext.class, float.class)) {
            hudRenderers.add(module);
        }
        if (overrides(module, "onFrame")) {
            frameHandlers.add(module);
        }
        if (overrides(module, "onRender3D", MatrixStack.class, VertexConsumerProvider.class, float.class)) {
            worldRenderers.add(module);
        }
        if (overrides(module, "onPacketSend", Packet.class)) {
            packetSenders.add(module);
        }
        if (overrides(module, "onPacketReceive", Packet.class)) {
            packetReceivers.add(module);
        }
        if (overrides(module, "onBeforeAttack", PlayerEntity.class, Entity.class)) {
            beforeAttackHandlers.add(module);
        }
        if (overrides(module, "onAttack", PlayerEntity.class, Entity.class)) {
            attackHandlers.add(module);
        }
        if (overrides(module, "onTickAlways")) {
            alwaysTickModules.add(module);
        }
    }

    private static boolean overrides(Module module, String method, Class<?>... parameters) {
        try {
            Method resolved = module.getClass().getMethod(method, parameters);
            return resolved.getDeclaringClass() != Module.class;
        } catch (NoSuchMethodException | SecurityException e) {
            BytePhase.LOGGER.error("Could not inspect {} on {}", method, module.getClass().getName(), e);
            return false;
        }
    }

    public List<Module> all() {
        return allView;
    }

    /** Called by {@link Module} after its enabled flag has changed. */
    public void moduleStateChanged() {
        rebuildActiveModules();
    }

    private void rebuildActiveModules() {
        List<Module> enabled = new ArrayList<>();
        List<Module> hud = new ArrayList<>();
        List<Module> world = new ArrayList<>();
        List<Module> frame = new ArrayList<>();
        List<Module> send = new ArrayList<>();
        List<Module> receive = new ArrayList<>();
        List<Module> beforeAttack = new ArrayList<>();
        List<Module> attack = new ArrayList<>();
        int snapshots = 0;
        boolean projection = false;
        zoom = null;
        customFov = null;
        noRender = null;
        crosshair = null;
        skyShaders = null;
        armorHud = null;
        nametags = null;
        playerEsp = null;
        slowSwing = null;
        wTap = null;
        safeWalk = null;
        antiPacketKick = null;
        packetLogger = null;
        logoutSpots = null;
        breadCrumbs = null;
        skeleton = null;
        noWeather = null;
        oldPotions = null;
        viewClip = null;
        smallShield = null;
        viewModel = null;
        extraTab = null;
        inventoryInfo = null;
        betterChat = null;
        for (Module module : modules) {
            if (!module.isEnabled()) continue;
            enabled.add(module);
            if (module instanceof Zoom value) {
                zoom = value;
            }
            else if (module instanceof CustomFov value) {
                customFov = value;
            }
            else if (module instanceof NoRender value) {
                noRender = value;
            }
            else if (module instanceof Crosshair value) {
                crosshair = value;
            }
            else if (module instanceof SkyShaders value) {
                skyShaders = value;
            }
            else if (module instanceof ArmorHud value) {
                armorHud = value;
            }
            else if (module instanceof Nametags value) {
                nametags = value;
            }
            else if (module instanceof PlayerEsp value) {
                playerEsp = value;
            }
            else if (module instanceof SlowSwing value) {
                slowSwing = value;
            }
            else if (module instanceof WTap value) {
                wTap = value;
            }
            else if (module instanceof SafeWalk value) {
                safeWalk = value;
            }
            else if (module instanceof AntiPacketKick value) {
                antiPacketKick = value;
            }
            else if (module instanceof PacketLogger value) {
                packetLogger = value;
            }
            else if (module instanceof LogoutSpots value) {
                logoutSpots = value;
            }
            else if (module instanceof BreadCrumbs value) {
                breadCrumbs = value;
            }
            else if (module instanceof Skeleton value) {
                skeleton = value;
            }
            else if (module instanceof NoWeather value) {
                noWeather = value;
            }
            else if (module instanceof OldPotions value) {
                oldPotions = value;
            }
            else if (module instanceof ViewClip value) {
                viewClip = value;
            }
            else if (module instanceof SmallShield value) {
                smallShield = value;
            }
            else if (module instanceof ViewModel value) {
                viewModel = value;
            }
            else if (module instanceof ExtraTab value) {
                extraTab = value;
            }
            else if (module instanceof InventoryInfo value) {
                inventoryInfo = value;
            }
            else if (module instanceof BetterChat value) {
                betterChat = value;
            }
            if (hudRenderers.contains(module)) hud.add(module);
            if (frameHandlers.contains(module)) frame.add(module);
            if (packetSenders.contains(module)) send.add(module);
            if (packetReceivers.contains(module)) receive.add(module);
            if (beforeAttackHandlers.contains(module)) beforeAttack.add(module);
            if (attackHandlers.contains(module)) attack.add(module);
            if (worldRenderers.contains(module)) {
                world.add(module);
                projection = true;
            }
            if (module.usesProjection()) projection = true;
            if (module.usesEntitySnapshot()) snapshots++;
        }
        enabledModules = List.copyOf(enabled);
        activeHud = List.copyOf(hud);
        activeWorld = List.copyOf(world);
        activeFrame = List.copyOf(frame);
        activePacketSenders = List.copyOf(send);
        activePacketReceivers = List.copyOf(receive);
        activeBeforeAttack = List.copyOf(beforeAttack);
        activeAttack = List.copyOf(attack);
        snapshotConsumers = snapshots;
        projectionNeeded = projection || skyShaders != null;
        if (snapshots == 0) EntitySnapshot.clear();
    }

    public List<Module> byCategory(Category category) {
        return byCategory.getOrDefault(category, List.of());
    }

    public List<Module> enabled() { return enabledModules; }

    public <T extends Module> T get(Class<T> type) { return type.cast(byClass.get(type)); }

    public Module getByName(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public Zoom zoom() { return zoom; }

    public CustomFov customFov() { return customFov; }

    public NoRender noRender() { return noRender; }

    public Crosshair crosshair() { return crosshair; }

    public SkyShaders skyShaders() { return skyShaders; }

    public ArmorHud armorHud() { return armorHud; }

    public Nametags nametags() { return nametags; }

    public PlayerEsp playerEsp() { return playerEsp; }

    public SlowSwing slowSwing() { return slowSwing; }

    public WTap wTap() { return wTap; }

    public SafeWalk safeWalk() { return safeWalk; }

    public AntiPacketKick antiPacketKick() { return antiPacketKick; }

    public PacketLogger packetLogger() { return packetLogger; }

    public LogoutSpots logoutSpots() { return logoutSpots; }

    public BreadCrumbs breadCrumbs() { return breadCrumbs; }

    public Skeleton skeleton() { return skeleton; }

    public NoWeather noWeather() { return noWeather; }

    public OldPotions oldPotions() { return oldPotions; }

    public ViewClip viewClip() { return viewClip; }

    public SmallShield smallShield() { return smallShield; }

    public ViewModel viewModel() { return viewModel; }

    public ExtraTab extraTab() { return extraTab; }

    public InventoryInfo inventoryInfo() { return inventoryInfo; }

    public BetterChat betterChat() { return betterChat; }

    public boolean needsProjection() { return projectionNeeded; }

    /** Runs the sky shader pass at {@code START_MAIN}, after vanilla sky. */
    public void renderSky() {
        SkyShaders sky = skyShaders;
        if (sky == null) {
            return;
        }
        try {
            sky.render();
        } catch (Exception e) {
            report(sky, "sky render", e);
        }
    }

    public void tick() {
        if (snapshotConsumers > 0) {
            EntitySnapshot.refresh();
        }
        for (Module module : enabledModules) {
            // The list is an immutable snapshot, so a module another module
            // just switched off this tick must be skipped explicitly.
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onTick();
            } catch (Exception e) {
                report(module, "tick", e);
            }
        }
        for (Module module : alwaysTickModules) {
            try {
                module.onTickAlways();
            } catch (Exception e) {
                report(module, "tick", e);
            }
        }
    }

    public void render2D(DrawContext context, float tickDelta) {
        List<Module> active = activeHud;
        if (active.isEmpty()) {
            return;
        }
        for (Module module : active) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onRender2D(context, tickDelta);
            } catch (Exception e) {
                report(module, "HUD render", e);
            }
        }
    }

    public void render3D(MatrixStack matrices, VertexConsumerProvider consumers, float tickDelta) {
        for (Module module : activeFrame) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onFrame();
            } catch (Exception e) {
                report(module, "frame", e);
            }
        }
        // No active world renderer means no buffer/pass setup at all. This is
        // the common case when the client is running with no ESP enabled.
        List<Module> active = activeWorld;
        if (active.isEmpty()) {
            return;
        }
        // One buffer per layer for the whole frame: each RenderLayer.draw is a
        // GPU render pass, so batching matters more than per-module simplicity.
        RenderBox.beginFrame(matrices, consumers);
        try {
            for (Module module : active) {
                if (!module.isEnabled()) {
                    continue;
                }
                try {
                    module.onRender3D(matrices, consumers, tickDelta);
                } catch (Exception e) {
                    report(module, "world render", e);
                }
            }
        } finally {
            RenderBox.endFrame();
            // Shader styles render after the batched box passes so they can
            // sample a mask generated from the same boxes without a second
            // module walk. Do not touch the shader renderer at all for plain
            // box styles; class-loading it compiles all supplied programs.
            if (EspRenderer.hasQueuedShader()) {
                try {
                    ClarityEspRenderer.flush();
                } finally {
                    EspRenderer.clearQueuedShader();
                }
            }
        }
    }

    public void beforeAttack(PlayerEntity self, Entity target) {
        // Runs before ClientPlayerInteractionManager sends the interaction
        // packet, so held-item and sprint changes made here reach the server
        // with the attack rather than one tick later.
        if (BytePhase.mc == null || BytePhase.mc.player != self) {
            return;
        }
        List<Module> active = activeBeforeAttack;
        if (active.isEmpty()) {
            return;
        }
        for (Module module : active) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onBeforeAttack(self, target);
            } catch (Exception e) {
                report(module, "pre-attack", e);
            }
        }
    }

    public void attack(PlayerEntity self, Entity target) {
        // PlayerEntity.attack also runs for the integrated server player. Only
        // feed the local client player's attacks to client-side modules.
        if (BytePhase.mc == null || BytePhase.mc.player != self) {
            return;
        }
        List<Module> active = activeAttack;
        if (active.isEmpty()) {
            return;
        }
        for (Module module : active) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onAttack(self, target);
            } catch (Exception e) {
                report(module, "attack", e);
            }
        }
    }

    public void serverCorrection() {
        dev.bytephase.util.CameraAim.release();
        // Corrections are rare; no extra dispatch list or per-packet work is needed.
        for (Module module : enabledModules) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                module.onServerCorrection();
            } catch (Exception e) {
                report(module, "server correction", e);
            }
        }
    }

    public boolean packetSend(Packet<?> packet) {
        List<Module> active = activePacketSenders;
        if (active.isEmpty()) {
            return false;
        }
        for (Module module : active) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                if (module.onPacketSend(packet)) {
                    return true;
                }
            } catch (Exception e) {
                report(module, "outgoing packet", e);
            }
        }
        return false;
    }

    public boolean packetReceive(Packet<?> packet) {
        List<Module> active = activePacketReceivers;
        if (active.isEmpty()) {
            return false;
        }
        for (Module module : active) {
            if (!module.isEnabled()) {
                continue;
            }
            try {
                if (module.onPacketReceive(packet)) {
                    return true;
                }
            } catch (Exception e) {
                report(module, "incoming packet", e);
            }
        }
        return false;
    }

    private void report(Module module, String phase, Throwable throwable) {
        ErrorState state = errorStates.computeIfAbsent(module, ignored -> new ErrorState());
        int count;
        synchronized (state) {
            long now = System.nanoTime();
            state.count = now - state.lastNanos > ERROR_WINDOW_NANOS ? 1 : state.count + 1;
            state.lastNanos = now;
            count = state.count;
        }
        if (count == 1) {
            BytePhase.LOGGER.error("Module {} failed during {}", module.getName(), phase, throwable);
        } else if (count == MAX_RAPID_ERRORS) {
            // A module that throws every tick or frame costs a stack trace per
            // call. Switch it off (on the client thread, since this can be
            // reached from the network thread) and tell the user why.
            BytePhase.LOGGER.error("Module {} kept failing during {}; disabling it", module.getName(), phase);
            var mc = BytePhase.mc;
            if (mc != null) {
                mc.execute(() -> module.setEnabled(false));
            }
        }
    }
}
