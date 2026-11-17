package dev.bytephase.engine.categories.movement;

import dev.bytephase.engine.categories.bots.AutoBuilder;
import ai.auth.Module;
import dev.bytephase.engine.categories.movement.Scaffold;
import dev.bytephase.engine.utils.ElytraAutoUtils;
import dev.bytephase.engine.utils.IconsPngType;

public class Sprint extends Module {
   @Override
   protected void onReset() {
      if (!AutoBuilder.bytephaseGapActive) {
         if (!Scaffold.isGodBridgeFlag()) {
            if (mc.player != null && mc.player.forwardSpeed > 0.0F) {
               ElytraAutoUtils.handleRawPressed(mc.options.sprintKey, true);
            }
         }
      }
   }

   public Sprint() {
      super("Sprint", IconsPngType.MOVEMENT);
   }
}
