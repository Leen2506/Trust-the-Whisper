package shm;


public interface GameListener {
    /** Called whenever the visible state changes (and ~10 times per second). */
    default void onSnapshot(GameSnapshot snapshot) {}

    /** Called once for each game event. */
    default void onEvent(GameEventType type, String detail) {}
}
