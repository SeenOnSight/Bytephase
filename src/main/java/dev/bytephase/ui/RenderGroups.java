package dev.bytephase.ui;

import dev.bytephase.module.Module;

/** Shared names for the compact render panel and enabled-module list. */
public final class RenderGroups {
    private RenderGroups() { }

    public static String of(Module module) {
        return switch (module.getName()) {
            case "ESP", "PlayerESP", "ItemESP", "StorageESP", "BlockESP", "HoleESP",
                    "Nametags", "Tracers", "Skeleton", "LogoutSpots", "SpawnerFinder" -> "ESP";
            case "SusChunks", "MotionScanner", "PrimeChunkFinder" -> "Discovery";
            case "Zoom", "NoBob", "CustomFov", "ViewClip", "ViewModel", "SmallShield",
                    "HideArmor", "SmoothCamera" -> "Camera";
            case "Fullbright", "TimeChanger", "NoRender", "NoWeather", "SkyShaders",
                    "OldPotions", "Performance" -> "Environment";
            case "BreadCrumbs", "TargetFX", "Hitmarker", "Highlight", "MiningProgress",
                    "Trajectories", "ThrowableTrajectory", "DamageMarkers", "HitSounds" -> "Effects";
            default -> "HUD";
        };
    }
}
