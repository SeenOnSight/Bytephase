package dev.bytephase.engine.mixins;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import dev.bytephase.engine.categories.misc.FakeCoordinates;

@Mixin({ClientPlayerEntity.class})
public abstract class MixinClientPlayerEntityFakeCoords {
   @ModifyVariable(
      method = {"sendMessage(Lnet/minecraft/text/Text;Z)V"},
      at = @At("HEAD"),
      argsOnly = true
   )
   private Text bytephase$fakeCoordsInClientChat(Text text) {
      return FakeCoordinates.getLiteral(text);
   }
}
