package dev.bytephase.engine.utils;
import dev.bytephase.engine.utils.CarefulBalancedType;

public record PearlsPlaystyleData(
   CarefulBalancedType playstyle,
   double fightRange,
   float turnScale,
   boolean combos,
   boolean aggroPearls,
   boolean anchors,
   boolean buildObsidian,
   boolean eatGapples,
   boolean escapePearls,
   boolean learnOpponents,
   boolean protectLoot
) {
}
