package dev.bytephase.engine.utils;

import net.minecraft.network.packet.Packet;

public interface DisconnectMultiplayerHandler {
   long getKnockbackWater(Packet<?> packet, boolean knockbackWaterFlag, long knockbackWaterValue);
}
