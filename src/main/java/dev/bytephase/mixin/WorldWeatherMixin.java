package dev.bytephase.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.bytephase.BytePhase;
import dev.bytephase.module.render.NoWeather;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(World.class)
public abstract class WorldWeatherMixin {
    @ModifyReturnValue(method = "getRainGradient", at = @At("RETURN"))
    private float bytephase$noRain(float original) {
        if (original == 0.0f || BytePhase.mc == null || (Object) this != BytePhase.mc.world) {
            return original;
        }
        NoWeather noWeather = BytePhase.modules == null ? null : BytePhase.modules.noWeather();
        return noWeather != null && noWeather.hidesRain() ? 0.0f : original;
    }

    @ModifyReturnValue(method = "getThunderGradient", at = @At("RETURN"))
    private float bytephase$noThunder(float original) {
        if (original == 0.0f || BytePhase.mc == null || (Object) this != BytePhase.mc.world) {
            return original;
        }
        NoWeather noWeather = BytePhase.modules == null ? null : BytePhase.modules.noWeather();
        return noWeather != null && noWeather.hidesThunder() ? 0.0f : original;
    }
}
