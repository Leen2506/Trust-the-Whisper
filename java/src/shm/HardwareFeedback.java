package shm;


public class HardwareFeedback implements GameListener {
    private final SensorManager sensors;

    public HardwareFeedback(SensorManager sensors) {
        this.sensors = sensors;
    }

    @Override
    public void onEvent(GameEventType type, String detail) {
        String cmd = switch (type) {
            case GAME_START -> "R";
            case CORRECT -> "C";
            case WRONG, TIMEOUT -> "W";
            case HELPER_POISONED -> "P";
            case GHOST_ENCOUNTER -> "G";
            case GHOST_PASSED -> "g";
            case GHOST_CLOSE -> "N";
            case CAUGHT -> "K";
            case WON -> "V";
            case LOST -> "X";
            case HEARTBEAT_TEMPO -> "B" + detail;
            case INSTRUCTION -> null; // spoken by the laptop, nothing to do on the hardware
        };
        if (cmd != null) sensors.send(cmd);
    }
}
