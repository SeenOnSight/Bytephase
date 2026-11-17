package dev.bytephase.engine.mixins;

import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import dev.bytephase.engine.categories.misc.FakeCoordinates;

@Mixin({Keyboard.class})
public abstract class MixinKeyboardFakeCoords {
   @ModifyArg(
      method = {"processF3(Lnet/minecraft/client/input/KeyInput;)Z", "copyBlock(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/nbt/NbtCompound;)V", "copyEntity(Lnet/minecraft/util/Identifier;Lnet/minecraft/util/math/Vec3d;Lnet/minecraft/nbt/NbtCompound;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/Keyboard;setClipboard(Ljava/lang/String;)V"
      )
   )
   private String bytephase$fakeCopiedCoords(String bytephasefakeCopiedText) {
      return FakeCoordinates.formatInstanceForString(bytephasefakeCopiedText);
   }
}
