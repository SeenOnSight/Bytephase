package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.mixin.AccessorAbstractContainerScreen;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.setting.BooleanSetting;
import dev.bytephase.pvp.module.setting.NumberSetting;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class HoverTotemModule extends ClientModule {
    private final BooleanSetting hotbarRefill = this.addSetting("hotbar-refill", new BooleanSetting("Hotbar Refill", false));
    private final NumberSetting hotbarSlot = this.addSetting("hotbar-slot", new NumberSetting("Hotbar Slot", 1.0F, 1.0F, 9.0F).step(1.0F));

    public HoverTotemModule() {
        super("hovertotem", "Hover Totem", "Swaps a hovered totem from any open container into the offhand or selected hotbar slot.", ModuleCategory.COMBAT);
        this.hotbarSlot.visibleWhen(this.hotbarRefill::get);
    }

    @EventHandler
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && mc.gameMode != null && !player.hasInfiniteMaterials()) {
            if (mc.screen instanceof AbstractContainerScreen<?> screen && AutoInvTotemModule.clickSafe(player) && !AutoInvTotemModule.busy()) {
                AbstractContainerMenu menu = screen.getMenu();
                Slot hovered = ((AccessorAbstractContainerScreen)screen).pvp$getHoveredSlot();
                if (hovered != null && menu.getCarried().isEmpty() && hovered.getItem().is(Items.TOTEM_OF_UNDYING)) {
                    if (hovered.container != player.getInventory() || hovered.getContainerSlot() != 40) {
                        int swapButton;
                        if (!player.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                            swapButton = 40;
                        } else {
                            if (!this.hotbarRefill.get()) {
                                return;
                            }

                            swapButton = this.hotbarSlot.getInt() - 1;
                            if (player.getInventory().getItem(swapButton).is(Items.TOTEM_OF_UNDYING)) {
                                return;
                            }
                        }

                        mc.gameMode.handleInventoryMouseClick(menu.containerId, hovered.index, swapButton, ClickType.SWAP, player);
                    }
                }
            }
        }
    }
}
