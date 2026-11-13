package dev.bytephase.pvp.module.impl.combat;

import dev.bytephase.pvp.event.EventHandler;
import dev.bytephase.pvp.event.impl.PacketAppliedEvent;
import dev.bytephase.pvp.event.impl.TickEvent;
import dev.bytephase.pvp.module.ClientModule;
import dev.bytephase.pvp.module.ModuleCategory;
import dev.bytephase.pvp.util.pvp.InputControl;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;

@Environment(EnvType.CLIENT)
public final class JumpResetModule extends ClientModule {
    private static final double MIN_KNOCKBACK = 0.1;
    private boolean pending;

    public JumpResetModule() {
        super("jump-reset", "Jump Reset", "Jumps the tick knockback lands to take less of it.", ModuleCategory.COMBAT);
    }

    @Override
    protected void onDisable() {
        this.pending = false;
    }

    @EventHandler
    public void onPacketApplied(PacketAppliedEvent event) {
        if (event.packet instanceof ClientboundSetEntityMotionPacket motion) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null && motion.getId() == player.getId()) {
                Vec3 knockback = motion.getMovement();
                if (knockback.horizontalDistance() >= 0.1 && knockback.y > 0.0) {
                    this.pending = true;
                }
            }
        }
    }

    @EventHandler
    public void onTick(TickEvent event) {
        if (this.pending) {
            this.pending = false;
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer player = mc.player;
            if (player != null && mc.screen == null) {
                if (player.onGround() && player.isSprinting() && player.input.keyPresses.forward()) {
                    if (!player.isInWater() && !player.isInLava() && !player.onClimbable() && !player.isPassenger() && !player.isFallFlying()) {
                        InputControl.jump();
                    }
                }
            }
        }
    }
}
