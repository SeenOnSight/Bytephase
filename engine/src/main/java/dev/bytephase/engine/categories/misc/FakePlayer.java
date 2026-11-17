package dev.bytephase.engine.categories.misc;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity.RemovalReason;
import ai.auth.Module;
import dev.bytephase.engine.utils.IconsPngType;

public class FakePlayer extends Module {
   private OtherClientPlayerEntity otherClientPlayerEntity;

   public FakePlayer() {
      super("Fake Player", IconsPngType.MISC);
   }

   @Override
   public void onDisable() {
      if (this.otherClientPlayerEntity != null && mc.world != null) {
         mc.world.removeEntity(this.otherClientPlayerEntity.getId(), RemovalReason.DISCARDED);
         this.otherClientPlayerEntity = null;
      }
   }

   @Override
   public void onEnable() {
      if (mc.world != null && mc.player != null) {
         GameProfile gameProfile = new GameProfile(UUID.randomUUID(), "BytephaseClient");
         this.otherClientPlayerEntity = new OtherClientPlayerEntity(mc.world, gameProfile);
         this.otherClientPlayerEntity.copyPositionAndRotation(mc.player);
         this.otherClientPlayerEntity.getInventory().clone(mc.player.getInventory());
         mc.world.addEntity(this.otherClientPlayerEntity);
      } else {
         this.setEnabled(false);
      }
   }
}
