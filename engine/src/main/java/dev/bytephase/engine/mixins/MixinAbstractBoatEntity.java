package dev.bytephase.engine.mixins;

import net.minecraft.entity.MovementType;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import dev.bytephase.engine.categories.misc.BoatFly;
import ai.auth.ModuleRegistry;

@Mixin({AbstractBoatEntity.class})
public class MixinAbstractBoatEntity {
   @Redirect(
      method = {"tick()V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/entity/vehicle/AbstractBoatEntity;move(Lnet/minecraft/entity/MovementType;Lnet/minecraft/util/math/Vec3d;)V"
      )
   )
   private void bytephaseBoatFlyMove(AbstractBoatEntity abstractBoatEntity, MovementType movementType, Vec3d vec) {
      BoatFly boatFly = (BoatFly)ModuleRegistry.getModule(BoatFly.class);
      if (boatFly == null || !boatFly.isEnabled() || !boatFly.canPressedOptions(abstractBoatEntity, movementType)) {
         abstractBoatEntity.move(movementType, vec);
      }
   }
}
