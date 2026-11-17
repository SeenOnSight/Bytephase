package dev.bytephase.engine.utils;

import java.util.List;
import dev.bytephase.engine.utils.ShardAxeType;
import dev.bytephase.engine.utils.ElseMaxData;

public record MaxDataOver(
   ShardAxeType kind,
   List<ElseMaxData> ranked,
   List<ElseMaxData> all,
   int listings,
   int fakes,
   int unread,
   int tooShort,
   int overPrice,
   int overBudget,
   int pages,
   boolean opened,
   long scannedAtMs
) {
   public MaxDataOver(
      ShardAxeType kind,
      List<ElseMaxData> ranked,
      List<ElseMaxData> all,
      int listings,
      int fakes,
      int unread,
      int tooShort,
      int overPrice,
      int overBudget,
      int pages,
      boolean opened,
      long scannedAtMs
   ) {
      ranked = ranked == null ? List.of() : List.copyOf(ranked);
      all = all == null ? ranked : List.copyOf(all);
      this.kind = kind;
      this.ranked = ranked;
      this.all = all;
      this.listings = listings;
      this.fakes = fakes;
      this.unread = unread;
      this.tooShort = tooShort;
      this.overPrice = overPrice;
      this.overBudget = overBudget;
      this.pages = pages;
      this.opened = opened;
      this.scannedAtMs = scannedAtMs;
   }

   public ElseMaxData getRanked() {
      return this.ranked.isEmpty() ? null : this.ranked.get(0);
   }
}
