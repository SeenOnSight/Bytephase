package dev.bytephase.pvp.module.impl.combat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.Stage;
import dev.bytephase.pvp.event.impl.PacketAppliedEvent;
import dev.bytephase.pvp.event.impl.PacketReceiveNettyEvent;
import dev.bytephase.pvp.event.impl.Render2DEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.ModuleRegistry;
import dev.bytephase.pvp.module.impl.combat.totem.OffhandRefill;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerInventoryPacket;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

@Environment(EnvType.CLIENT)
public final class AutoTotemModule extends ClientModule {
    private static final int OFFHAND_BUTTON = 40;
    private static final int OFFHAND_MENU_SLOT = 45;
    private static final int TOTEM_POP_EVENT = 35;
    private static final int NEAREST_CHOICES = 3;
    private static AutoTotemModule instance;
    private final NumberSetting refillDelay = this.addSetting("refill-delay", new NumberSetting("Refill Delay", 3.0F, 1.0F, 10.0F).step(1.0F));
    private final BooleanSetting useHotbar = this.addSetting("use-hotbar-totems", new BooleanSetting("Use Hotbar Totems", false));
    private final OffhandRefill refill = new OffhandRefill(
        new OffhandRefill.Driver() {
            @Override
            public boolean needsTotem() {
                return AutoTotemModule.player().getOffhandItem().isEmpty();
            }

            @Override
            public boolean canInteract() {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;
                return mc.gameMode.getPlayerMode() == GameType.SURVIVAL
                    && player.containerMenu == player.inventoryMenu
                    && player.inventoryMenu.getCarried().isEmpty();
            }

            @Override
            public int findSource() {
                return AutoTotemModule.this.findSource(AutoTotemModule.player().getInventory());
            }

            @Override
            public boolean validSource(int index) {
                LocalPlayer player = AutoTotemModule.player();
                if (!AutoTotemModule.isSource(player.getInventory(), index, AutoTotemModule.this.useHotbar.get())) {
                    return false;
                }

                Slot slot = player.inventoryMenu.getSlot(AutoTotemModule.menuSlot(index));
                return slot.container == player.getInventory() && slot.getContainerSlot() == index && slot.mayPickup(player);
            }

            @Override
            public void swap(int index) {
                Minecraft mc = Minecraft.getInstance();
                LocalPlayer player = mc.player;
                mc.gameMode.handleInventoryMouseClick(player.inventoryMenu.containerId, AutoTotemModule.menuSlot(index), 40, ClickType.SWAP, player);
                AutoTotemModule.this.previousSource = index;
            }

            @Override
            public int containerId() {
                return AutoTotemModule.player().inventoryMenu.containerId;
            }

            @Override
            public int stateId() {
                return AutoTotemModule.player().inventoryMenu.getStateId();
            }

            @Override
            public int latencyMillis() {
                ClientPacketListener connection = Minecraft.getInstance().getConnection();
                PlayerInfo info = connection == null ? null : connection.getPlayerInfo(AutoTotemModule.player().getUUID());
                return info == null ? 0 : info.getLatency();
            }
        },
        ThreadLocalRandom.current()
    );
    private int previousSource = -1;

    public AutoTotemModule() {
        super(
            "autototem",
            "Auto Totem",
            "Refills an empty offhand with a totem from your inventory, at a human pace you choose with Refill Delay.",
            ModuleCategory.COMBAT
        );
        instance = this;
    }

    public static boolean wantsHotbar() {
        return false;
    }

    public static boolean busy() {
        AutoTotemModule module = instance;
        LocalPlayer player = Minecraft.getInstance().player;
        return module != null && module.isEnabled() && player != null && !player.isSpectator() && !player.hasInfiniteMaterials()
            ? module.refill.busy() || player.getOffhandItem().isEmpty() && module.findSource(player.getInventory()) >= 0
            : false;
    }

    @Override
    protected void onEnable() {
        AutoInvTotemModule other = ModuleRegistry.get(AutoInvTotemModule.class);
        if (other != null) {
            other.setEnabled(false);
        }

        this.reset();
    }

    @Override
    protected void onDisable() {
        this.reset();
    }

    @EventHandler(stage = Stage.POST)
    public void onTick(TickEvent event) {
        this.update();
    }

    @EventHandler
    public void onRender2D(Render2DEvent event) {
        this.update();
    }

    @EventHandler
    public void onPacketApplied(PacketAppliedEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && mc.player != null) {
            if (event.packet instanceof ClientboundEntityEventPacket status && status.getEventId() == 35 && status.getEntity(mc.level) == mc.player) {
                this.reset();
            }
        }
    }

    @EventHandler
    public void onPacketReceiveNetty(PacketReceiveNettyEvent event) {
        long now = System.nanoTime();
        if (event.packet instanceof ClientboundSetPlayerInventoryPacket update) {
            if (update.slot() == 40) {
                this.observe(-1, -1, offhand(update.contents()), now);
            }
        } else if (event.packet instanceof ClientboundContainerSetSlotPacket update) {
            if (update.getSlot() == 45) {
                this.observe(update.getContainerId(), update.getStateId(), offhand(update.getItem()), now);
            }
        } else if (event.packet instanceof ClientboundContainerSetContentPacket update && update.items().size() > 45) {
            this.observe(update.containerId(), update.stateId(), offhand(update.items().get(45)), now);
        }
    }

    private void observe(int containerId, int stateId, OffhandRefill.Offhand offhand, long receivedAt) {
        Minecraft.getInstance().execute(() -> {
            if (this.isEnabled()) {
                this.refill.observe(containerId, stateId, offhand, receivedAt);
            }
        });
    }

    private void update() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (mc.level != null && player != null && mc.gameMode != null && player.isAlive() && !player.isRemoved() && !(player.getHealth() <= 0.0F)) {
            this.refill.step(System.nanoTime(), this.refillDelay.getInt());
        } else {
            this.reset();
        }
    }

    private void reset() {
        this.refill.reset();
        this.previousSource = -1;
    }

    private int findSource(Inventory inventory) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (this.useHotbar.get()) {
            List<Integer> hotbar = new ArrayList<>();

            for (int i = 0; i < 9; i++) {
                if (isSource(inventory, i, true)) {
                    hotbar.add(i);
                }
            }

            if (!hotbar.isEmpty()) {
                return hotbar.get(random.nextInt(hotbar.size()));
            }
        }

        List<Integer> stored = new ArrayList<>();

        for (int i = 9; i < 36; i++) {
            if (isSource(inventory, i, false)) {
                stored.add(i);
            }
        }

        if (stored.isEmpty()) {
            return -1;
        }

        int anchor = this.previousSource >= 9 && this.previousSource < 36 ? this.previousSource - 9 : random.nextInt(27);
        stored.sort(Comparator.comparingInt(index -> {
            int offset = index - 9;
            return Math.abs(offset % 9 - anchor % 9) + Math.abs(offset / 9 - anchor / 9);
        }));
        return stored.get(random.nextInt(Math.min(3, stored.size())));
    }

    private static boolean isSource(Inventory inventory, int index, boolean hotbar) {
        return index >= (hotbar ? 0 : 9) && index < 36 && inventory.getItem(index).is(Items.TOTEM_OF_UNDYING);
    }

    private static int menuSlot(int index) {
        return index < 9 ? index + 36 : index;
    }

    private static OffhandRefill.Offhand offhand(ItemStack stack) {
        if (stack.isEmpty()) {
            return OffhandRefill.Offhand.EMPTY;
        } else {
            return stack.is(Items.TOTEM_OF_UNDYING) ? OffhandRefill.Offhand.TOTEM : OffhandRefill.Offhand.OTHER;
        }
    }

    private static LocalPlayer player() {
        return Minecraft.getInstance().player;
    }
}
