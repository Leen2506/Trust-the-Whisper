package shm;

public class PoisonedState implements HelperState {
    @Override public Direction speak(Direction correct) { return correct.opposite(); }
    @Override public Direction expectedAnswer(Direction spoken) { return spoken.opposite(); }
    @Override public String name() { return "POISONED"; }
}
