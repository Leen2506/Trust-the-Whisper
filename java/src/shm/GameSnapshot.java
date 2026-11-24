package shm;


public record GameSnapshot(
        GamePhase phase,
        Maze maze,                 // walls never change after generation, safe to share
        boolean[][] revealed,      // copied
        int playerRow, int playerCol,
        int[] ghostCell,           // null if the ghost has not entered yet
        int ghostGap,
        int lives, int score,
        int stepsTaken, int pathLength,
        String instruction,        // word currently spoken, or null
        long deadlineMillis, long windowMillis,
        double fear,
        boolean helperPoisoned,
        int detection, boolean hidden,
        long phaseStartMillis,
        String message,
        boolean hardwareConnected) {
}
