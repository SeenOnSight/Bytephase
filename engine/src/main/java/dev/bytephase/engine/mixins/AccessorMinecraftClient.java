package dev.bytephase.engine.mixins;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({MinecraftClient.class})
public interface AccessorMinecraftClient {
   @Invoker("doAttack")
   boolean bytephaseDoAttack();

   @Accessor("attackCooldown")
   void bytephaseSetAttackCooldown(int bytephaseAttackValue);

   @Accessor("attackCooldown")
   int bytephaseGetAttackCooldown();
}
