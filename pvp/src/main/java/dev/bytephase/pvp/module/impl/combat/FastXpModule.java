package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.mixin.AccessorCombatMinecraft;
import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.EventPriority;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.module.impl.combat.melee.MeleeAttacks;
import dev.bytephase.pvp.module.setting.NumberSetting;
import dev.bytephase.pvp.util.pvp.ServerRotation;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

@Environment(EnvType.CLIENT)
public final class FastXpModule extends ClientModule {
    private final NumberSetting delay = this.addSetting("delay", new NumberSetting("Delay", 0.0F, 0.0F, 3.0F).step(1.0F));

    public FastXpModule() {
        super("fastxp", "FastXP", "Throws experience bottles faster while use is held.", ModuleCategory.COMBAT);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onTick(TickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player != null && mc.screen == null && mc.options.keyUse.isDown()) {
            if (!player.isUsingItem() && holdsXp(player)) {
                AccessorCombatMinecraft access = (AccessorCombatMinecraft)mc;
                int wait = access.pvp$rightClickDelay();
                if (!ServerRotation.pending() && !MeleeAttacks.actedThisTick(player)) {
                    if (wait > this.delay.getInt() + 1) {
                        access.pvp$setRightClickDelay(this.delay.getInt() + 1);
                    }
                } else {
                    access.pvp$setRightClickDelay(Math.max(wait, 2));
                    KeyMapping use = mc.options.keyUse;

                    while (use.consumeClick()) {
                    }
                }
            }
        }
    }

    private static boolean holdsXp(LocalPlayer player) {
        ItemStack main = player.getMainHandItem();
        return main.is(Items.EXPERIENCE_BOTTLE) || player.getOffhandItem().is(Items.EXPERIENCE_BOTTLE) && !(main.getItem() instanceof BlockItem);
    }
}
