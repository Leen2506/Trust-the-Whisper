package shm;

/** The high-level stage the game is in. */
public enum GamePhase {
    TITLE,          // waiting for the player to start
    BETWEEN_STEPS,  // short pause; no input accepted
    AWAITING_INPUT, // an instruction was given and the timer is running
    GHOST,          // hide encounter: cover the hiding sensor, touch nothing
    WON,
    LOST
}
