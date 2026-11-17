package dev.bytephase.engine.mixins;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo.TextureAsset;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.bytephase.engine.categories.misc.Capes;
import dev.bytephase.engine.categories.misc.SkinProtect;
import dev.bytephase.engine.utils.EmitVertex;
import dev.bytephase.engine.utils.SubmitMatrixApi;

@Mixin({AbstractClientPlayerEntity.class})
public class MixinAbstractClientPlayerEntity implements SubmitMatrixApi {
   @Unique
   private EmitVertex bytephase$capeFrame;

   @Override
   public EmitVertex bytephase$capeFrame() {
      return this.bytephase$capeFrame;
   }

   @Override
   public void bytephase$capeFrame(EmitVertex bytephasecapeFrame) {
      this.bytephase$capeFrame = bytephasecapeFrame;
   }

   @Inject(
      method = {"getSkin()Lnet/minecraft/entity/player/SkinTextures;"},
      at = {@At("RETURN")},
      cancellable = true
   )
   private void bytephaseCosmeticsOverride(CallbackInfoReturnable<SkinTextures> callbackInfoReturnable) {
      AbstractClientPlayerEntity self = (AbstractClientPlayerEntity)((net.minecraft.client.network.AbstractClientPlayerEntity) (Object) (this));
      SkinTextures orig = (SkinTextures)callbackInfoReturnable.getReturnValue();
      if (orig != null) {
         SkinTextures skin = SkinProtect.getBytephaseclientInstance(self);
         Identifier cape = Capes.getIdentifierInstance(self);
         if (skin != null || cape != null) {
            TextureAsset body = skin != null ? skin.body() : orig.body();
            PlayerSkinType model = skin != null ? skin.model() : orig.model();
            TextureAsset capeAsset = cape != null ? Capes.getIfAbsent(cape) : orig.cape();
            callbackInfoReturnable.setReturnValue(SkinTextures.create(body, capeAsset, orig.elytra(), model));
         }
      }
   }
}
