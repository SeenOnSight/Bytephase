package dev.bytephase.pvp.mixin;

import dev.bytephase.pvp.marlow.MarlowCrystal;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.EndCrystalItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value={EndCrystalItem.class})
public class MarlowEndCrystalItemMixin {
    @ModifyVariable(method={"useOn"}, at=@At(value="STORE"), ordinal=0)
    private List<Entity> bytephase$marlow$skipHiddenCrystals(List<Entity> obstructing) {
        MarlowCrystal mod = MarlowCrystal.get();
        return mod.serverSession().isOptedOut() ? obstructing : mod.keptCrystals().withoutHidden(obstructing);
    }
}

