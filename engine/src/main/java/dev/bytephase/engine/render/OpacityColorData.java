package dev.bytephase.engine.render;

import dev.bytephase.engine.inventory.ConstLitematicaUtils;
import dev.bytephase.engine.world.BlockStateType;

public record OpacityColorData(
   OpacityColorData.Mode mode,
   OpacityColorData.InteriorBytephase interiorBytephase,
   OpacityColorData.LayerMode layerMode,
   float ghostOpacity,
   float missingOpacity,
   float wrongBlockOpacity,
   float wrongStateOpacity,
   float occludedOpacity,
   float interiorFadeRadius,
   boolean renderCorrect,
   boolean renderMissing,
   boolean renderIncorrect,
   boolean missingThroughWalls,
   boolean outlines,
   float outlineWidth,
   float outlineOpacity,
   int renderDistance,
   int rebuildBudget,
   int playerY,
   boolean showBox,
   boolean showOrigin,
   boolean textured,
   int correctColor,
   int missingColor,
   int wrongBlockColor,
   int wrongStateColor,
   int extraColor,
   OpacityColorData.Style style,
   boolean litematicaTranslucent
) {
   static float getAmount(float hideInterior, float interiorFade, float x) {
      if (interiorFade <= hideInterior) {
         return x < hideInterior ? 0.0F : 1.0F;
      } else {
         float opacityColorFactor = Math.max(0.0F, Math.min(1.0F, (x - hideInterior) / (interiorFade - hideInterior)));
         return opacityColorFactor * opacityColorFactor * (3.0F - 2.0F * opacityColorFactor);
      }
   }

   public boolean isLitematicaStyle() {
      return this.style == OpacityColorData.Style.LITEMATICA;
   }

   public float getHideCamera() {
      OpacityColorData.InteriorBytephase bytephaseInterior = this.getBytephaseInterior();
      if (bytephaseInterior == OpacityColorData.InteriorBytephase.OFF) {
         return 1.0F;
      } else {
         return bytephaseInterior == OpacityColorData.InteriorBytephase.HIDE_CAMERA_CELL ? 0.0F : 0.01F;
      }
   }

   public long getOpacityBytephase() {
      long ordinal = this.mode.ordinal();
      long opacityBytephaseValue = ordinal * 31L + this.style.ordinal();
      long opacityBytephaseValue2 = opacityBytephaseValue * 31L + this.layerMode.ordinal();
      long opacityBytephaseValue3 = opacityBytephaseValue2 * 31L + (this.renderCorrect ? 1 : 0);
      long opacityBytephaseValue4 = opacityBytephaseValue3 * 31L + (this.renderMissing ? 1 : 0);
      long opacityBytephaseValue5 = opacityBytephaseValue4 * 31L + (this.renderIncorrect ? 1 : 0);
      long opacityBytephaseValue6 = opacityBytephaseValue5 * 31L + (this.outlines ? 1 : 0);
      long opacityBytephaseValue7 = opacityBytephaseValue6 * 31L + (this.textured ? 1 : 0);
      long opacityBytephaseValue8 = opacityBytephaseValue7 * 31L + Math.round(this.missingOpacity * 50.0F);
      long opacityBytephaseValue9 = opacityBytephaseValue8 * 31L + Math.round(this.wrongBlockOpacity * 50.0F);
      long opacityBytephaseValue10 = opacityBytephaseValue9 * 31L + Math.round(this.wrongStateOpacity * 50.0F);
      long opacityBytephaseValue11 = opacityBytephaseValue10 * 31L + Math.round(this.outlineWidth * 4.0F);
      long opacityBytephaseValue12 = opacityBytephaseValue11 * 31L + Math.round(this.outlineOpacity * 50.0F);
      long opacityBytephase = opacityBytephaseValue12 * 31L + (this.interiorBytephase == OpacityColorData.InteriorBytephase.CROSS_SECTION ? 1 : 0);
      if (this.layerMode != OpacityColorData.LayerMode.ALL || this.interiorBytephase == OpacityColorData.InteriorBytephase.CROSS_SECTION) {
         opacityBytephase = opacityBytephase * 31L + this.playerY;
      }

      return opacityBytephase;
   }

   public float getAmount(float densityScale, float dist) {
      if (this.getBytephaseInterior() == OpacityColorData.InteriorBytephase.OFF) {
         return 1.0F;
      } else {
         float hideInterior = this.getHideInterior(dist);
         float interiorFade = this.getInteriorFade(dist);
         float opacityColorFactor = getAmount(hideInterior, interiorFade, densityScale);
         return this.getHideCamera() + (1.0F - this.getHideCamera()) * opacityColorFactor;
      }
   }

   public float getInteriorFade(float densityScale) {
      return this.getBytephaseInterior() == OpacityColorData.InteriorBytephase.OFF ? 0.0F : Math.max(0.5F, this.interiorFadeRadius) * densityScale;
   }

   public float getOpacityOutline() {
      return this.isLitematicaStyle() ? 1.0F : this.outlineOpacity;
   }

   private OpacityColorData.InteriorBytephase getBytephaseInterior() {
      return this.isLitematicaStyle() ? OpacityColorData.InteriorBytephase.OFF : this.interiorBytephase;
   }

   public float getWallsThrough() {
      if (!this.isLitematicaStyle()) {
         return this.outlineWidth;
      } else {
         return this.missingThroughWalls ? 3.0F : 1.0F;
      }
   }

   public float getCorrectMissing(BlockStateType cls, boolean ghostDrawn) {
      if (this.isLitematicaStyle()) {
         return ConstLitematicaUtils.getAmount(cls);
      } else if (cls == BlockStateType.MISSING && ghostDrawn) {
         return 0.0F;
      } else {
         return cls == BlockStateType.CORRECT ? 0.0F : this.getOpacityWrong(cls);
      }
   }

   private int getBytephaseCross() {
      int bytephaseCross = this.layerMode.getValue();
      if (this.interiorBytephase == OpacityColorData.InteriorBytephase.CROSS_SECTION && bytephaseCross < 0) {
         bytephaseCross = 1;
      }

      return bytephaseCross;
   }

   public boolean canModeMissing(BlockStateType cls) {
      if (!this.textured || this.mode == OpacityColorData.Mode.OUTLINES_ONLY || this.mode == OpacityColorData.Mode.BOX_ONLY) {
         return false;
      } else if (this.isLitematicaStyle()) {
         return cls == BlockStateType.MISSING && this.renderMissing && this.mode != OpacityColorData.Mode.WRONG_ONLY;
      } else {
         return switch (cls) {
            case MISSING -> this.renderMissing && this.mode != OpacityColorData.Mode.WRONG_ONLY;
            case WRONG_BLOCK, WRONG_STATE -> this.renderIncorrect && this.mode != OpacityColorData.Mode.MISSING_ONLY;
            case CORRECT -> this.renderCorrect && this.mode == OpacityColorData.Mode.FULL;
            case EXTRA, UNKNOWN -> false;
         };
      }
   }

   public boolean canPlayer(int sectionMinY) {
      int bytephaseCross = this.getBytephaseCross();
      return bytephaseCross < 0 ? true : sectionMinY <= this.playerY + bytephaseCross && sectionMinY + 15 >= this.playerY - bytephaseCross;
   }

   public boolean canModeRender(BlockStateType cls) {
      if (this.mode == OpacityColorData.Mode.BOX_ONLY || this.mode == OpacityColorData.Mode.GHOST_ONLY) {
         return false;
      } else if (this.isLitematicaStyle() && cls == BlockStateType.CORRECT) {
         return false;
      } else {
         return switch (cls) {
            case MISSING -> this.renderMissing && this.mode != OpacityColorData.Mode.WRONG_ONLY;
            case WRONG_BLOCK, WRONG_STATE, EXTRA -> this.renderIncorrect && this.mode != OpacityColorData.Mode.MISSING_ONLY;
            case CORRECT -> this.renderCorrect && this.mode == OpacityColorData.Mode.FULL;
            case UNKNOWN -> false;
         };
      }
   }

   public float getHideInterior(float densityScale) {
      OpacityColorData.InteriorBytephase bytephaseInterior = this.getBytephaseInterior();
      if (bytephaseInterior == OpacityColorData.InteriorBytephase.OFF) {
         return 0.0F;
      } else {
         float interiorFactor = Math.max(0.5F, this.interiorFadeRadius) * densityScale;
         return interiorFactor * (bytephaseInterior == OpacityColorData.InteriorBytephase.HIDE_CAMERA_CELL ? 0.55F : 0.3F);
      }
   }

   public float getAmount(BlockStateType cls) {
      return this.getCorrectMissing(cls, this.canModeMissing(cls));
   }

   public float getOpacityWrong(BlockStateType cls) {
      return switch (cls) {
         case MISSING -> this.missingOpacity;
         case WRONG_BLOCK -> this.wrongBlockOpacity;
         case WRONG_STATE -> this.wrongStateOpacity;
         case CORRECT -> 0.1F;
         case EXTRA -> this.wrongBlockOpacity;
         case UNKNOWN -> 0.0F;
      };
   }

   public boolean canPlayerForInt(int y) {
      int bytephaseCross = this.getBytephaseCross();
      return bytephaseCross < 0 ? true : Math.abs(y - this.playerY) <= bytephaseCross;
   }

   public static enum InteriorBytephase {
      OFF,
      FADE_NEAR_CAMERA,
      HIDE_CAMERA_CELL,
      CROSS_SECTION;
   }

   public static enum LayerMode {
      ALL,
      CURRENT,
      PLUS_ONE,
      PLUS_TWO;

      public int getValue() {
         return switch (this) {
            case ALL -> -1;
            case CURRENT -> 0;
            case PLUS_ONE -> 1;
            case PLUS_TWO -> 2;
         };
      }
   }

   public static enum Mode {
      FULL,
      MISSING_ONLY,
      MISSING_WRONG,
      WRONG_ONLY,
      GHOST_ONLY,
      OUTLINES_ONLY,
      BOX_ONLY;
   }

   public static enum Style {
      BYTEPHASE,
      LITEMATICA;
   }
}
