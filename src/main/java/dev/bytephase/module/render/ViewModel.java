package dev.bytephase.module.render;

import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.NumberSetting;
import dev.bytephase.setting.BooleanSetting;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;

/** Independent first-person hand transforms, with an optional mirrored offhand. */
public final class ViewModel extends Module {
    private final BooleanSetting mirror = add(new BooleanSetting("MirrorOffhand", true));
    private final NumberSetting scale = add(new NumberSetting("Scale", 1.0, 0.2, 2.0, 0.05));
    private final NumberSetting offsetX = add(new NumberSetting("OffsetX", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting offsetY = add(new NumberSetting("OffsetY", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting offsetZ = add(new NumberSetting("OffsetZ", 0.0, -1.0, 1.0, 0.05));
    private final NumberSetting rotateX = add(new NumberSetting("RotationX", 0, -180, 180, 1));
    private final NumberSetting rotateY = add(new NumberSetting("RotationY", 0, -180, 180, 1));
    private final NumberSetting rotateZ = add(new NumberSetting("RotationZ", 0, -180, 180, 1));
    private final NumberSetting offScale = add(new NumberSetting("OffhandScale", 1, 0.2, 2, 0.05));
    private final NumberSetting offX = add(new NumberSetting("OffhandOffsetX", 0, -1, 1, 0.05));
    private final NumberSetting offY = add(new NumberSetting("OffhandOffsetY", 0, -1, 1, 0.05));
    private final NumberSetting offZ = add(new NumberSetting("OffhandOffsetZ", 0, -1, 1, 0.05));
    private final NumberSetting offRotateX = add(new NumberSetting("OffhandRotationX", 0, -180, 180, 1));
    private final NumberSetting offRotateY = add(new NumberSetting("OffhandRotationY", 0, -180, 180, 1));
    private final NumberSetting offRotateZ = add(new NumberSetting("OffhandRotationZ", 0, -180, 180, 1));

    public ViewModel() {
        super("ViewModel", Category.RENDER, "Changes the position and size of your first-person hand");
        for (var setting : java.util.List.of(offScale, offX, offY, offZ, offRotateX, offRotateY, offRotateZ))
            setting.visibleWhen(() -> !mirror.getValue());
    }

    private boolean separateOffhand(Hand hand) { return hand == Hand.OFF_HAND && !mirror.getValue(); }
    public float scale(Hand hand) { return separateOffhand(hand) ? offScale.asFloat() : scale.asFloat(); }
    public float offsetX(Hand hand, Arm arm) {
        return mirror.getValue() ? offsetX(arm) : separateOffhand(hand) ? offX.asFloat() : offsetX.asFloat();
    }
    public float offsetY(Hand hand) { return separateOffhand(hand) ? offY.asFloat() : offsetY.asFloat(); }
    public float offsetZ(Hand hand) { return separateOffhand(hand) ? offZ.asFloat() : offsetZ.asFloat(); }
    public float rotationX(Hand hand) { return separateOffhand(hand) ? offRotateX.asFloat() : rotateX.asFloat(); }
    public float rotationY(Hand hand) {
        float value = separateOffhand(hand) ? offRotateY.asFloat() : rotateY.asFloat();
        return mirror.getValue() && hand == Hand.OFF_HAND ? -value : value;
    }
    public float rotationZ(Hand hand) {
        float value = separateOffhand(hand) ? offRotateZ.asFloat() : rotateZ.asFloat();
        return mirror.getValue() && hand == Hand.OFF_HAND ? -value : value;
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
