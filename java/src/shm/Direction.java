package shm;

public enum Direction {
    FORWARD('F', -1, 0, 1, "FORWARD"),
    BACKWARD('B', 1, 0, 2, "BACKWARD"),
    LEFT('L', 0, -1, 4, "LEFT"),
    RIGHT('R', 0, 1, 8, "RIGHT");

    public final char code;
    public final int dRow;
    public final int dCol;
    public final int wallBit;
    public final String word;

    Direction(char code, int dRow, int dCol, int wallBit, String word) {
        this.code = code;
        this.dRow = dRow;
        this.dCol = dCol;
        this.wallBit = wallBit;
        this.word = word;
    }

    public Direction opposite() {
        return switch (this) {
            case FORWARD -> BACKWARD;
            case BACKWARD -> FORWARD;
            case LEFT -> RIGHT;
            case RIGHT -> LEFT;
        };
    }
    public static Direction fromCode(char c) {
        for (Direction d : values()) {
            if (d.code == Character.toUpperCase(c)) return d;
        }
        return null;
    }
}
