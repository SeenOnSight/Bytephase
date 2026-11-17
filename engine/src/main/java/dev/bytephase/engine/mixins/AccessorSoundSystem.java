package dev.bytephase.engine.mixins;

import java.util.Map;
import net.minecraft.client.sound.Channel.SourceManager;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({SoundSystem.class})
public interface AccessorSoundSystem {
   @Accessor("sources")
   Map<SoundInstance, SourceManager> bytephaseSources();

   @Invoker("getAdjustedVolume")
   float bytephaseAdjustedVolume(SoundInstance soundInstance);
}
