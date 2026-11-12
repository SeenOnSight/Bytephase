package dev.bytephase.module.world;

import dev.bytephase.BytePhase;
import dev.bytephase.data.LocationStore.Location;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** A compact direction readout for a saved waypoint, without an entity ESP overlay. */
public final class Navigator extends Module {
    private final NumberSetting arrivalRange = add(new NumberSetting("ArrivalRange", 8, 1, 32, 1));
    private final BooleanSetting announceArrival = add(new BooleanSetting("ArrivalMessage", true));
    private Location lastTarget;
    private boolean arrived;

    public Navigator() {
        super("Navigator", Category.EXPLORE, "Shows distance and direction to your selected waypoint");
    }

    @Override
    protected void onDisable() {
        lastTarget = null;
        arrived = false;
    }

    @Override
    public void onTick() {
        Location target = BytePhase.locations.selected();
        if (target == null || BytePhase.mc.player == null) {
            onDisable();
            return;
        }
        if (target != lastTarget) {
            lastTarget = target;
            arrived = false;
        }
        double distance = BytePhase.mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(target.pos()));
        if (!arrived && distance <= arrivalRange.getValue()) {
            arrived = true;
            if (announceArrival.getValue()) BytePhase.mc.inGameHud.getChatHud().addMessage(
                    Text.literal("[Bytephase] Arrived at " + target.name()));
        } else if (distance > arrivalRange.getValue() * 2) {
            arrived = false;
        }
    }

    @Override
    public void onRender2D(DrawContext context, float tickDelta) {
        Location target = BytePhase.locations.selected();
        var mc = BytePhase.mc;
        if (target == null || mc.player == null || mc.options.hudHidden) return;
        Vec3d offset = Vec3d.ofCenter(target.pos()).subtract(mc.player.getEntityPos());
        double yaw = Math.toDegrees(Math.atan2(-offset.x, offset.z));
        double difference = MathHelper.wrapDegrees(yaw - mc.player.getYaw());
        String direction = Math.abs(difference) < 15 ? "Ahead" : Math.abs(difference) > 165 ? "Behind"
                : difference < 0 ? "Left" : "Right";
        String text = target.name() + " | " + Math.round(offset.length()) + "m | " + direction
                + " | Y " + target.y();
        int width = mc.textRenderer.getWidth(text);
        int x = Math.max(4, (context.getScaledWindowWidth() - width) / 2);
        context.drawTextWithShadow(mc.textRenderer, text, x, 28, 0xFF6466E9);
    }
}
