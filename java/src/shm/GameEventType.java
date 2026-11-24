package shm;

/** One-off events the controller announces to its observers. */
public enum GameEventType {
    GAME_START,
    INSTRUCTION,      // detail = the spoken word, e.g. "LEFT"
    CORRECT,
    WRONG,
    TIMEOUT,
    HELPER_POISONED,
    GHOST_ENCOUNTER,
    GHOST_PASSED,
    GHOST_CLOSE,      // the ghost is (nearly) on top of the player
    CAUGHT,
    WON,
    LOST,
    HEARTBEAT_TEMPO   // detail = beats per minute for the buzzer heartbeat (0 = off)
}
