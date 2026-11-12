package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;

/**
 * Tints potions the way the pre-1.20 renderer did: the bottle colour is the
 * blend of the colours of the effects inside it rather than the potion's own
 * colour field.
 *
 * <p>Vanilla still ships the blend as {@code PotionContentsComponent.mixColors}
 * and uses it for custom potions, so this reuses that instead of reimplementing
 * the averaging. Effectless potions fall back to the vanilla effectless colour.</p>
 */
public final class OldPotions extends Module {
    public OldPotions() {
        super("OldPotions", Category.RENDER, "Tints potions by their effects like the old renderer");
    }
}
