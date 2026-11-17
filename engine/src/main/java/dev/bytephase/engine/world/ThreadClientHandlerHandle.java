package dev.bytephase.engine.world;

import net.minecraft.util.math.Vec3d;

public interface ThreadClientHandlerHandle {
   void handlePlayerReduction(Vec3d vec, boolean playerReductionFlag);
}
