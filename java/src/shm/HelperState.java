package shm;


public interface HelperState {
    /** What the helper says, given the truly correct direction. */
    Direction speak(Direction correct);

    /** Which button the player must press after hearing {@code spoken}. */
    Direction expectedAnswer(Direction spoken);

    String name();
}
