package dev.bytephase.engine.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.nio.ByteBuffer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.fog.FogRenderer;
import net.minecraft.client.world.ClientWorld;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.visuals.CustomWorld;

@Mixin({FogRenderer.class})
public class MixinFogRendererCustomWorld {
   @Unique
   private static Vector4f bytephase$color(int bytephasecolorValue) {
      return new Vector4f((bytephasecolorValue >> 16 & 0xFF) / 255.0F, (bytephasecolorValue >> 8 & 0xFF) / 255.0F, (bytephasecolorValue & 0xFF) / 255.0F, (bytephasecolorValue >>> 24 & 0xFF) / 255.0F);
   }

   @Inject(
      method = {"getFogColor(Lnet/minecraft/client/render/Camera;FLnet/minecraft/client/world/ClientWorld;IF)Lorg/joml/Vector4f;"},
      at = {@At("TAIL")},
      cancellable = true
   )
   private void bytephase$fogColor(Camera camera, float bytephasefogColorValue, ClientWorld clientWorld, int bytephasefogColorValue2, float bytephasefogColorValue3, CallbackInfoReturnable<Vector4f> callbackInfoReturnable) {
      CustomWorld cw = CustomWorld.getCustomWorld();
      if (cw != null && cw.isBase()) {
         callbackInfoReturnable.setReturnValue(bytephase$color(cw.getFogColor()));
      }
   }

   @WrapOperation(
      method = {"applyFog(Lnet/minecraft/client/render/Camera;ILnet/minecraft/client/render/RenderTickCounter;FLnet/minecraft/client/world/ClientWorld;)Lorg/joml/Vector4f;"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/render/fog/FogRenderer;applyFog(Ljava/nio/ByteBuffer;ILorg/joml/Vector4f;FFFFFF)V"
      )},
      require = 0
   )
   private void bytephase$worldFog(
      FogRenderer fogRenderer,
      ByteBuffer byteBuffer,
      int bytephaseworldFogValue,
      Vector4f vector4f,
      float bytephaseworldFogValue2,
      float bytephaseworldFogValue3,
      float bytephaseworldFogValue4,
      float bytephaseworldFogValue5,
      float bytephaseworldFogValue6,
      float bytephaseworldFogValue7,
      Operation<Void> operation
   ) {
      CustomWorld cw = CustomWorld.getCustomWorld();
      if (cw != null && cw.isBase()) {
         float end = cw.getFogEnd();
         float start = Math.min(cw.getAmount(), end);
         operation.call(new Object[]{fogRenderer, byteBuffer, bytephaseworldFogValue, bytephase$color(cw.getFogColor()), start, end, start, end, end, end});
      } else {
         operation.call(
            new Object[]{fogRenderer, byteBuffer, bytephaseworldFogValue, vector4f, bytephaseworldFogValue2, bytephaseworldFogValue3, bytephaseworldFogValue4, bytephaseworldFogValue5, bytephaseworldFogValue6, bytephaseworldFogValue7}
         );
      }
   }
}
