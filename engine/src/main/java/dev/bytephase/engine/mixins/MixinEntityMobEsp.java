package dev.bytephase.engine.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import dev.bytephase.engine.categories.visuals.MobESP;

@Mixin({Entity.class})
public abstract class MixinEntityMobEsp {
   @ModifyReturnValue(
      method = {"isGlowing()Z"},
      at = {@At("RETURN")}
   )
   private boolean bytephase$mobEspGlow(boolean originalGlowing) {
      return originalGlowing || MobESP.getCornersOutline((Entity)((net.minecraft.entity.Entity) (Object) (this))) != 0;
   }

   @ModifyReturnValue(
      method = {"getTeamColorValue()I"},
      at = {@At("RETURN")}
   )
   private int bytephase$mobEspColor(int originalColor) {
      int color = MobESP.getCornersOutline((Entity)((net.minecraft.entity.Entity) (Object) (this)));
      return color != 0 ? color : originalColor;
   }
}
