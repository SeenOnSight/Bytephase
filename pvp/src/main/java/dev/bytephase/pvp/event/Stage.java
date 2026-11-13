package dev.bytephase.pvp.event;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public enum Stage {
    PRE,
    POST;
}
