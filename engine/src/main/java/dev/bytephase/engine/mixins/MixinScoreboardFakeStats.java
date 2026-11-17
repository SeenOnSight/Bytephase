package dev.bytephase.engine.mixins;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.StringVisitable;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import dev.bytephase.engine.categories.donutsmp.FakeStats;
import ai.auth.ModuleRegistry;

@Mixin({InGameHud.class})
public class MixinScoreboardFakeStats {
   private static final String SIDEBAR = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V";

   @Inject(
      method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"},
      at = {@At("HEAD")}
   )
   private void bytephase_beginSidebar(DrawContext drawContext, ScoreboardObjective scoreboardObjective, CallbackInfo callbackInfo) {
      FakeStats fs = fakeStats();
      if (fs != null) {
         fs.resetLineCounters();
      }
   }

   @Redirect(
      method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/font/TextRenderer;getWidth(Lnet/minecraft/text/StringVisitable;)I",
         ordinal = 1
      ),
      require = 0
   )
   private int bytephase_lineWidth(TextRenderer textRenderer, StringVisitable stringVisitable) {
      FakeStats fs = fakeStats();
      if (fs != null && stringVisitable instanceof Text t) {
         try {
            return textRenderer.getWidth(fs.rewriteLineFirstPass(t));
         } catch (Exception e) {
         }
      }

      return textRenderer.getWidth(stringVisitable);
   }

   @ModifyArg(
      method = {"renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/gui/DrawContext;drawText(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/Text;IIIZ)V",
         ordinal = 1
      ),
      index = 1,
      require = 0
   )
   private Text bytephase_lineText(Text bytephase_lineText) {
      FakeStats fs = fakeStats();
      if (fs != null) {
         try {
            return fs.rewriteLineSecondPass(bytephase_lineText);
         } catch (Exception e) {
         }
      }

      return bytephase_lineText;
   }

   private static FakeStats fakeStats() {
      FakeStats fs = (FakeStats)ModuleRegistry.getModule(FakeStats.class);
      return fs != null && fs.isEnabled() ? fs : null;
   }
}
