package dev.bytephase.module.render;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.ColorSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

public final class TargetHud extends Module {
    private final NumberSetting x = add(new NumberSetting("XPercent", 50, 0, 100, 1));
    private final NumberSetting y = add(new NumberSetting("YPercent", 70, 0, 100, 1));
    private final NumberSetting timeout = add(new NumberSetting("TimeoutTicks", 80, 10, 200, 1));
    private final ColorSetting color = add(new ColorSetting("Color", 0xFF6466E9));
    private LivingEntity target;
    private int ticks;

    public TargetHud() {
        super("TargetHUD", Category.RENDER, "Shows health, distance and held items for your last attacked target");
    }

    @Override
    protected void onDisable() { target = null; ticks = 0; }

    @Override
    public void onAttack(PlayerEntity self, Entity entity) {
        if (entity instanceof LivingEntity living) {
            target = living;
            ticks = timeout.asInt();
        }
    }

    @Override
    public void onTick() {
        if (ticks > 0) ticks--;
        if (ticks == 0 || target != null && (target.isRemoved()
                || target.getEntityWorld() != BytePhase.mc.world)) target = null;
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        var mc = BytePhase.mc;
        if (target == null || mc.player == null || target.getEntityWorld() != mc.world) return;
        int screenW = mc.getWindow().getScaledWidth(), screenH = mc.getWindow().getScaledHeight();
        int width = Math.min(150, screenW), height = 47;
        int px = (int) Math.round((screenW - width) * x.getValue() / 100);
        int py = (int) Math.round(Math.max(0, screenH - height) * y.getValue() / 100);
        context.fill(px, py, px + width, py + height, 0xB0000000);
        boolean heldItems = !target.getMainHandStack().isEmpty() || !target.getOffHandStack().isEmpty();
        String name = mc.textRenderer.trimToWidth(target.getName().getString(), Math.max(8, width - (heldItems ? 42 : 8)));
        context.drawTextWithShadow(mc.textRenderer, name, px + 4, py + 4, 0xFFFFFFFF);
        String status = Math.round(target.getHealth()) + " HP  "
                + Math.round(target.distanceTo(mc.player)) + "m";
        context.drawTextWithShadow(mc.textRenderer, status, px + 4, py + 16, color.getValue());
        float fraction = Math.max(0, Math.min(1, target.getHealth() / Math.max(1, target.getMaxHealth())));
        context.fill(px + 4, py + 29, px + width - 4, py + 33, 0x50FFFFFF);
        context.fill(px + 4, py + 29, px + 4 + Math.round((width - 8) * fraction), py + 33, color.getValue());
        if (!target.getMainHandStack().isEmpty()) context.drawItem(target.getMainHandStack(), px + width - 37, py + 4);
        if (!target.getOffHandStack().isEmpty()) context.drawItem(target.getOffHandStack(), px + width - 19, py + 4);
    }
}
