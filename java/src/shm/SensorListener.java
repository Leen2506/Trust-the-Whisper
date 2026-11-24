package shm;


public interface SensorListener {
    /** "D:F" / "D:B" / "D:L" / "D:R" — a touch sensor was pressed. */
    void onDirection(Direction direction);

    /** "I:1" / "I:0" — hiding sensor (IR) covered / uncovered. */
    void onHideState(boolean hidden);
    /** Any line received at all — used to show that the radio link is alive. */
    default void onHardwareAlive() {}
}
