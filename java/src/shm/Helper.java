package shm;

public class Helper {
    private HelperState state = new TrustedState();
    private Direction lastSpoken;

    /** Produces the instruction the player will hear for this step. */
    public Direction giveInstruction(Direction correct) {
        lastSpoken = state.speak(correct);
        return lastSpoken;
    }

    /** The direction the player must press for the last instruction. */
    public Direction getExpectedDirection() {
        return lastSpoken == null ? null : state.expectedAnswer(lastSpoken);
    }

    public void poison() { state = new PoisonedState(); }
    public boolean isPoisoned() { return state instanceof PoisonedState; }
    public String getStateName() { return state.name(); }
}
