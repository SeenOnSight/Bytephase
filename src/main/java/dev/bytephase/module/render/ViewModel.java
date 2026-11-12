package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.util.Arm;

/**
 * Moves and scales the items held in your own first-person hands.
 *
 * <p>The offset is applied before vanilla applies its per-item transform, so it
 * behaves the same for every item. Both hands are transformed, so the offhand
 * follows the same scale and offset as the main hand; {@code OffsetX} is
 * mirrored on the left arm, so a positive value moves both hands toward the
 * same side instead of sliding the pair off-centre. It only touches the local
 * player's first-person pass: other players' hands and the third-person view
 * keep the vanilla transform.</p>
 */
public final class ViewModel extends Module {
    private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.2, 2.0, 0.05));
    private final NumberSetting offsetX = add(new NumberSetting("OffsetX", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting offsetY = add(new NumberSetting("OffsetY", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting offsetZ = add(new NumberSetting("OffsetZ", 0.0, -1.0, 1.0, 0.05));

    public ViewModel() {
        super("ViewModel", Category.RENDER, "Changes the position and size of your first-person hand");
    }

    public float scale() {
        return scale.asFloat();
    }

    public float offsetX() {
        return offsetX.asFloat();
    }

    /**
     * Horizontal offset for one hand. The left arm is mirrored so a positive
     * value moves both hands toward the same side and the pair stays symmetric.
     */
    public float offsetX(Arm arm) {
        return arm == Arm.LEFT ? -offsetX.asFloat() : offsetX.asFloat();
    }

    public float offsetY() {
        return offsetY.asFloat();
    }

    public float offsetZ() {
        return offsetZ.asFloat();
    }
}
