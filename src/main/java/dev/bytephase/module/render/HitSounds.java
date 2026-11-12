package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundEvents;

public final class HitSounds extends Module {
    private final ModeSetting sound = add(new ModeSetting("Sound", new String[] {"Chime", "Pling", "Click"}, "Chime"));
    private final NumberSetting pitch = add(new NumberSetting("Pitch", 1.2, 0.5, 2, 0.1));
    private final NumberSetting volume = add(new NumberSetting("Volume", 0.5, 0.05, 1, 0.05));

    public HitSounds() {
        super("HitSounds", Category.RENDER, "Plays local audio feedback when you attack an entity");
    }

    @Override
    public void onAttack(PlayerEntity self, Entity target) {
        var event = switch (sound.getValue()) {
            case "Pling" -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value();
            case "Click" -> SoundEvents.UI_BUTTON_CLICK.value();
            default -> SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value();
        };
        BytePhase.mc.getSoundManager().play(PositionedSoundInstance.ui(event, pitch.asFloat(), volume.asFloat()));
    }
}
