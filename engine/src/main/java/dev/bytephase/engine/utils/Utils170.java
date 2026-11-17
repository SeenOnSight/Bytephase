package dev.bytephase.engine.utils;

import dev.bytephase.engine.gui.VertexGuiUtils;
import dev.bytephase.engine.render.BytephaseRoundedUtils;
import dev.bytephase.engine.render.FlameBytephaseUtils;
import dev.bytephase.engine.render.GhostBuilderUtils;
import dev.bytephase.engine.render.SamplerBuilderUtils;
import dev.bytephase.engine.render.TexBuilderUtils;
import dev.bytephase.engine.render.VertexBuilderUtils;

public class Utils170 {
   public static void update() {
      BytephaseRoundedUtils.updateRegisterBuild();
      VertexGuiUtils.updateTriangleFan();
      VertexBuilderUtils.updateRegisterBuild();
      FlameBytephaseUtils.updateRegisterBuild();
      TexBuilderUtils.update();
      SamplerBuilderUtils.update();
      GhostBuilderUtils.update();
   }
}
