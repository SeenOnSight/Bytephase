package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import java.util.Locale;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;

public final class PMSound extends Module {
    public PMSound() {
        super("PMSound", Category.MISC, "Plays a sound when you receive a private message");
    }

    public void onGameMessage(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        // Vanilla also echoes outgoing whispers as a game message; those are
        // not private messages received by the player.
        if (lower.startsWith("you whisper") || !lower.contains("whisper")) {
            return;
        }
        var mc = BytePhase.mc;
        if (mc == null) {
            return;
        }
        mc.getSoundManager().play(PositionedSoundInstance.ui(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.6f));
    }
}
