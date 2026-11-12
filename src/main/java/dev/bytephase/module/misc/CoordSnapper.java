package dev.bytephase.module.misc;

import dev.bytephase.BytePhase;
import dev.bytephase.module.Category;
import dev.bytephase.module.Module;
import dev.bytephase.setting.BooleanSetting;
import dev.bytephase.setting.ModeSetting;
import dev.bytephase.setting.NumberSetting;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

/** One-shot coordinate capture, using the normal module bind. */
public final class CoordSnapper extends Module {
    private final ModeSetting target = add(new ModeSetting("Target",
            new String[]{"OwnPosition", "LookedAt"}, "LookedAt"));
    private final NumberSetting range = add(new NumberSetting("Range", 128, 16, 512, 1));
    private final BooleanSetting includeDimension = add(new BooleanSetting("IncludeDimension", true));
    private final BooleanSetting fluids = add(new BooleanSetting("StopAtFluids", false));

    public CoordSnapper() {
        super("CoordSnapper", Category.EXPLORE, "Copies your position or a targeted block to the clipboard");
    }

    public static BlockPos lookedAt(double range, boolean fluids) {
        var mc = BytePhase.mc;
        if (mc == null || mc.player == null || mc.world == null) {
            throw new IllegalStateException("Join a world first.");
        }
        HitResult hit = mc.player.raycast(range, 1.0f, fluids);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            throw new IllegalArgumentException("No loaded block is in your line of sight.");
        }
        return block.getBlockPos();
    }

    public static String copy(BlockPos pos, boolean includeDimension) {
        String coordinates = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        if (includeDimension) coordinates += " [" + BytePhase.mc.world.getRegistryKey().getValue() + "]";
        BytePhase.mc.keyboard.setClipboard(coordinates);
        return coordinates;
    }

    @Override
    public void onTick() {
        try {
            if (BytePhase.mc.player == null || BytePhase.mc.world == null) return;
            BlockPos pos = target.getValue().equals("LookedAt") ? lookedAt(range.getValue(), fluids.getValue())
                    : BytePhase.mc.player.getBlockPos();
            BytePhase.mc.inGameHud.getChatHud().addMessage(Text.literal("[Bytephase] Copied "
                    + copy(pos, includeDimension.getValue())));
        } catch (IllegalArgumentException error) {
            BytePhase.mc.inGameHud.getChatHud().addMessage(Text.literal("[Bytephase] " + error.getMessage()));
        } finally {
            setEnabled(false);
        }
    }
}
