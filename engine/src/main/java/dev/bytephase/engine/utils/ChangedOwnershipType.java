package dev.bytephase.engine.utils;

public enum ChangedOwnershipType {
   NONE,
   ALREADY_COMPLETE,
   TEAMMATE_COMPLETED,
   OWNERSHIP_REVOKED,
   ASSIGNMENT_EPOCH_CHANGED,
   WORLD_STATE_CHANGED;

   public boolean isNone() {
      return this != NONE;
   }
}
