package shm;

public final class GameConfig {
    private GameConfig() {}

    public static final int BAUD_RATE = 9600;
    
    public static final int MAZE_ROWS = 7;
    public static final int MAZE_COLS = 9;

    public static final int START_LIVES = 3;
    
    public static final long START_DELAY_MS = 3000;
    /** Response window for the very first instruction. */
    public static final long BASE_WINDOW_MS = 5000;
    /** The window shrinks by this much after every correct step. */
    public static final long WINDOW_DECAY_PER_STEP_MS = 100;
    /** At maximum fear the window is shortened by up to this much. */
    public static final long FEAR_WINDOW_PENALTY_MS = 900;
    /** The window never goes below this. */
    public static final long MIN_WINDOW_MS = 1800;
    /** Pause between a correct answer and the next instruction. */
    public static final long NEXT_INSTRUCTION_DELAY_MS = 700;
    /** Pause after a mistake (lets the LED / buzzer finish). */
    public static final long AFTER_MISTAKE_DELAY_MS = 1500;
    /** Pause after the helper becomes poisoned (dramatic effect). */
    public static final long AFTER_POISON_DELAY_MS = 3000;

    // ---- Helper poisoning ----
    /** The helper is poisoned somewhere between these fractions of the path. */
    public static final double POISON_AT_MIN = 0.35;
    public static final double POISON_AT_MAX = 0.50;

    // ---- Ghost ----
    /** Steps between the ghost and the player at the start. */
    public static final int GHOST_START_GAP = 7;
    /** After poisoning, the ghost creeps one step closer every N correct steps. */
    public static final int GHOST_CREEP_EVERY_STEPS = 5;
    /** Chance that a hide encounter starts after a correct step (poisoned phase only). */
    public static final double GHOST_ENCOUNTER_CHANCE = 0.30;
    /** Minimum correct steps between two encounters. */
    public static final int GHOST_ENCOUNTER_COOLDOWN = 3;
    /** How long the player must stay hidden. */
    public static final long GHOST_ENCOUNTER_MS = 6000;

    // ---- Presentation ----
    /** false = the screen never tells the player whether the helper is poisoned (memory challenge). */
    public static final boolean SHOW_HELPER_STATE = false;
    /** true = the exit glows faintly in the dark so the player knows the goal. */
    public static final boolean SHOW_EXIT = true;

    // ---- Horror audio (laptop speakers) ----
    /** Master volume of the synthesized horror sounds, 0.0 .. 1.0 */
    public static final double HORROR_VOLUME = 0.8;
    /** Deep heartbeat on the laptop as well as the buzzer. */
    public static final boolean LAPTOP_HEARTBEAT = true;
    /** macOS voice for the helper. "Whisper" is creepy; set to "" for the default voice. */
    public static final String MAC_HELPER_VOICE = "Whisper";

    /** true = poisoned instructions are spoken in a pitched-down voice (makes the game easier). */
    public static final boolean DISTORT_POISONED_VOICE = false;
}
