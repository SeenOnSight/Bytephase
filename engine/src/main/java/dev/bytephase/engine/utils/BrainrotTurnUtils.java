package dev.bytephase.engine.utils;

import java.util.List;
import dev.bytephase.engine.utils.WarmBrainrotBase;
import dev.bytephase.engine.utils.TungPupil;
import dev.bytephase.engine.utils.ShinThigh;
import dev.bytephase.engine.utils.LidBombardiro;
import dev.bytephase.engine.utils.SloshFore;
import dev.bytephase.engine.utils.BrrFore;
import dev.bytephase.engine.utils.ArmTrunk;
import dev.bytephase.engine.utils.EarChimpanzini;
import dev.bytephase.engine.utils.TailKatana;
import dev.bytephase.engine.utils.FingerClaw;
import dev.bytephase.engine.utils.FootBombombini;
import dev.bytephase.engine.utils.AmbalabuBoneca;
import dev.bytephase.engine.utils.LidRing;
import dev.bytephase.engine.utils.ShinFoot;
import dev.bytephase.engine.utils.TailFore;
import dev.bytephase.engine.utils.LegBurbaloni;
import dev.bytephase.engine.utils.ForeShinConst;
import dev.bytephase.engine.utils.CrownFore;
import dev.bytephase.engine.utils.ShinEar;
import dev.bytephase.engine.utils.TailEar;
import dev.bytephase.engine.utils.DinFore;
import dev.bytephase.engine.utils.DolfinitaBananita;

public final class BrainrotTurnUtils {
   public static final List<WarmBrainrotBase> BRAINROT_TURN_ITEMS = List.of(
      new TungPupil(),
      new ShinThigh(),
      new LidBombardiro(),
      new SloshFore(),
      new BrrFore(),
      new ArmTrunk(),
      new EarChimpanzini(),
      new TailKatana(),
      new FingerClaw(),
      new FootBombombini(),
      new AmbalabuBoneca(),
      new LidRing(),
      new ShinFoot(),
      new TailFore(),
      new LegBurbaloni(),
      new ForeShinConst(),
      new CrownFore(),
      new ShinEar(),
      new TailEar(),
      new DinFore(),
      new DolfinitaBananita()
   );

   public static String[] getBrainrotTurn() {
      String[] strings = new String[BRAINROT_TURN_ITEMS.size()];

      for (int i = 0; i < strings.length; i++) {
         strings[i] = BRAINROT_TURN_ITEMS.get(i).formatTungDinString();
      }

      return strings;
   }

   private BrainrotTurnUtils() {
   }

   public static int getBrainrotTurn(String brainrotTurnText) {
      for (int i = 0; i < BRAINROT_TURN_ITEMS.size(); i++) {
         if (BRAINROT_TURN_ITEMS.get(i).formatTungDinString().equals(brainrotTurnText)) {
            return i;
         }
      }

      return -1;
   }
}
