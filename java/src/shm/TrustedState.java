package shm;

public class TrustedState implements HelperState {
    @Override public Direction speak(Direction correct) { return correct; }
    @Override public Direction expectedAnswer(Direction spoken) { return spoken; }
    @Override public String name() { return "TRUSTED"; }
}
