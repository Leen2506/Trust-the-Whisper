package shm;

import java.util.List;
import java.util.Random;


public class Ghost {
    private int gap = GameConfig.GHOST_START_GAP;
    private boolean encounterActive;
    private long encounterStart;
    private int detection;
    private int stepsSinceEncounter;

    // ---------- chasing ----------
    public void approach() { gap = Math.max(0, gap - 1); }
    public void retreat(int steps) { gap += steps; }
    public int getGap() { return gap; }

    /** Cell the ghost is drawn in, or null if it has not entered the maze yet. */
    public int[] position(List<int[]> trail) {
        int index = trail.size() - 1 - gap;
        return index < 0 ? null : trail.get(index);
    }

    // ---------- hide encounters ----------
    public boolean shouldStartEncounter(Random rng) {
        stepsSinceEncounter++;
        return stepsSinceEncounter > GameConfig.GHOST_ENCOUNTER_COOLDOWN
                && rng.nextDouble() < GameConfig.GHOST_ENCOUNTER_CHANCE;
    }

    public void startEncounter(long now) {
        encounterActive = true;
        encounterStart = now;
        detection = 0;
        stepsSinceEncounter = 0;
    }

    /**
     * Called every 100 ms during an encounter.
     * @param hidden true while the IR hiding sensor is covered
     */
    public void tick(boolean hidden, long now) {
        long elapsed = now - encounterStart;
        if (!hidden) {
            // 1.5 s of grace to reach the hiding spot, then the meter rises fast
            if (elapsed > 1500) detection += 6;
        } else {
            detection -= 2;
        }
        detection = Math.max(0, Math.min(100, detection));
    }

    public void addNoise(int amount) { detection = Math.min(100, detection + amount); }
    public boolean isDetected() { return detection >= 100; }
    public boolean encounterOver(long now) { return now - encounterStart >= GameConfig.GHOST_ENCOUNTER_MS; }
    public void endEncounter() { encounterActive = false; detection = 0; }
    public boolean isEncounterActive() { return encounterActive; }
    public int getDetection() { return detection; }
    public long getEncounterStart() { return encounterStart; }
}
