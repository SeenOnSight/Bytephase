package dev.bytephase.module.combat;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;

public final class Criticals extends Module {
    private final BooleanSetting onlyFullCharge = add(new BooleanSetting("OnlyFullCharge", true));

    public Criticals() {
        super("Criticals", Category.COMBAT, "Spoofs a short fall before attacking so the server grants critical hits");
    }

    public boolean shouldCrit(PlayerEntity player) {
        if (onlyFullCharge.getValue() && player.getAttackCooldownProgress(0.5f) < 0.9f) {
            return false;
        }
        return player.isOnGround()
                && !player.isTouchingWater()
                && !player.isClimbing()
                && !player.isGliding()
                && !player.hasVehicle()
                && !player.isUsingItem();
    }

    public void sendCritPackets(PlayerEntity player) {
        var mc = BytePhase.mc;
        if (mc == null || mc.getNetworkHandler() == null) {
            return;
        }
        double y = player.getY();
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), y + 0.0625, player.getZ(), false, false));
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), y, player.getZ(), false, false));
        // The packet pair is only a server-side spoof. Changing local ground
        // state here leaks into movement and jump handling for the rest of the
        // client tick, even though the player never actually left the ground.
    }
}
