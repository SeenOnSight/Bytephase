package dev.bytephase.pvp.util.pvp;

/** Paces successful interactions, retaining fractional tick intervals. */
public final class PlacementRate {
    private boolean scheduled;
    private double nextTick;
    private int lastPlacementTick;
    private double previousCps;

    public boolean ready(int tick, double cps) {
        if (!this.scheduled) {
            return true;
        }
        if (cps != this.previousCps) {
            this.nextTick = this.lastPlacementTick + 20.0 / cps;
            this.previousCps = cps;
        }
        return tick + 1.0E-7 >= this.nextTick;
    }

    public void placed(int tick, double cps) {
        double interval = 20.0 / cps;
        if (!this.scheduled || cps != this.previousCps || tick - this.nextTick >= interval) {
            // Restart after a server wait instead of accumulating extra clicks.
            this.nextTick = tick + interval;
        } else {
            this.nextTick += interval;
        }
        this.lastPlacementTick = tick;
        this.previousCps = cps;
        this.scheduled = true;
    }

    public void reset() {
        this.scheduled = false;
        this.nextTick = 0.0;
        this.lastPlacementTick = 0;
        this.previousCps = 0;
    }
}
