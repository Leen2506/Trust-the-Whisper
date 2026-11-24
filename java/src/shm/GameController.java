package shm;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class GameController implements SensorListener {

    private final ScheduledExecutorService gameThread = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "game-logic");
        t.setDaemon(true);
        return t;
    });
    private final List<GameListener> listeners = new CopyOnWriteArrayList<>();
    private final Random rng = new Random();

    // ---- model objects ----
    private Maze maze;
    private Player player;
    private Helper helper;
    private Ghost ghost;

    // ---- game state ----
    private GamePhase phase;
    private long phaseStart;
    private Direction spoken;
    private long deadline;
    private long window;
    private ScheduledFuture<?> timeoutTask;
    private int steps;
    private int pathLength;
    private int poisonAtStep;
    private boolean hidden;
    private String message = "";
    private int lastTempo = -1;
    private volatile boolean hardwareConnected;
    private volatile long lastHardwareLine;

    public GameController() {
        newGame();
        // 10 Hz tick: ghost encounters, heartbeat tempo, GUI refresh
        gameThread.scheduleAtFixedRate(this::safeTick, 100, 100, TimeUnit.MILLISECONDS);
    }

    public void addListener(GameListener l) {
        listeners.add(l);
        gameThread.execute(this::publish);
    }

    public void setHardwareConnected(boolean connected) {
        hardwareConnected = connected;
    }

    // =====================================================================
    // SensorListener — called from the serial thread or the Swing thread.
    // Each method only queues work onto the game thread.
    // =====================================================================

    @Override
    public void onDirection(Direction d) {
        gameThread.execute(() -> handleDirection(d));
    }

    @Override
    public void onHideState(boolean isHidden) {
        gameThread.execute(() -> {
            hidden = isHidden;
            publish();
        });
    }

    @Override
    public void onHardwareAlive() {
        lastHardwareLine = System.currentTimeMillis();
    }

    /** ENTER key / any touch on the title, win or lose screen. */
    public void startOrRestart() {
        gameThread.execute(this::handleStartKey);
    }

    // =====================================================================
    // Game flow (everything below runs on the game thread only)
    // =====================================================================

    private void newGame() {
        maze = new Maze(GameConfig.MAZE_ROWS, GameConfig.MAZE_COLS, rng.nextLong());
        player = new Player(maze.startRow, maze.startCol, GameConfig.START_LIVES);
        helper = new Helper();
        ghost = new Ghost();
        steps = 0;
        pathLength = maze.distanceToExit(maze.startRow, maze.startCol);
        double fraction = GameConfig.POISON_AT_MIN
                + rng.nextDouble() * (GameConfig.POISON_AT_MAX - GameConfig.POISON_AT_MIN);
        poisonAtStep = Math.max(2, (int) Math.round(pathLength * fraction));
        spoken = null;
        lastTempo = -1;
        hidden = false;
        setPhase(GamePhase.TITLE);
        message = "Touch any sensor (or press ENTER) to begin";
    }

    private void handleStartKey() {
        if (phase == GamePhase.WON || phase == GamePhase.LOST) {
            newGame();
            publish();
            return;
        }
        if (phase == GamePhase.TITLE) {
            setPhase(GamePhase.BETWEEN_STEPS);
            message = "Your helper is with you. Listen carefully...";
            fire(GameEventType.GAME_START, "");
            scheduleNextInstruction(GameConfig.START_DELAY_MS);
            publish();
        }
    }

    private void handleDirection(Direction d) {
        switch (phase) {
            case TITLE, WON, LOST -> handleStartKey();
            case AWAITING_INPUT -> checkAnswer(d);
            case GHOST -> {
                // Touching anything while hiding makes noise
                ghost.addNoise(50);
                message = "You moved! The ghost heard you...";
                fire(GameEventType.GHOST_CLOSE, "");
            }
            default -> { /* between steps: ignore */ }
        }
        publish();
    }

    private void scheduleNextInstruction(long delayMs) {
        gameThread.schedule(this::issueInstruction, delayMs, TimeUnit.MILLISECONDS);
    }

    /** Asks the helper for the next instruction and starts the response timer. */
    private void issueInstruction() {
        if (phase != GamePhase.BETWEEN_STEPS) return;
        Direction correct = maze.nextStepToExit(player.getRow(), player.getCol());
        if (correct == null) { win(); return; }

        spoken = helper.giveInstruction(correct);
        window = computeWindow();
        deadline = now() + window;
        setPhase(GamePhase.AWAITING_INPUT);
        message = "";
        fire(GameEventType.INSTRUCTION, spoken.word + (helper.isPoisoned() ? ":P" : ""));
        timeoutTask = gameThread.schedule(this::handleTimeout, window, TimeUnit.MILLISECONDS);
        publish();
    }

    private void checkAnswer(Direction pressed) {
        cancelTimeout();
        Direction expected = helper.getExpectedDirection();
        if (pressed == expected) {
            onCorrect(expected);
        } else {
            onMistake("Wrong way!", GameEventType.WRONG);
        }
    }

    private void handleTimeout() {
        if (phase != GamePhase.AWAITING_INPUT) return;
        onMistake("Too slow!", GameEventType.TIMEOUT);
        publish();
    }

    private void onCorrect(Direction move) {
        long timeLeft = Math.max(0, deadline - now());
        maze.markVisited(player.getRow(), player.getCol()); // the cell we are leaving becomes trail
        player.move(move);
        steps++;
        int points = 100 + (int) (timeLeft / 20);
        if (helper.isPoisoned()) points *= 2;      // reversing is harder, so it is worth more
        player.addScore(points);
        fire(GameEventType.CORRECT, "");

        if (maze.isExit(player.getRow(), player.getCol())) {
            win();
            return;
        }

        setPhase(GamePhase.BETWEEN_STEPS);

        if (!helper.isPoisoned() && steps >= poisonAtStep) {
            poisonHelper();
            return;
        }

        if (helper.isPoisoned()) {
            if (steps % GameConfig.GHOST_CREEP_EVERY_STEPS == 0) {
                ghost.approach();
                if (ghost.getGap() <= 2) fire(GameEventType.GHOST_CLOSE, "");
            }
            if (ghost.shouldStartEncounter(rng)) {
                startGhostEncounter();
                return;
            }
        }
        scheduleNextInstruction(GameConfig.NEXT_INSTRUCTION_DELAY_MS);
    }

    private void onMistake(String text, GameEventType type) {
        player.loseLife();
        ghost.approach();
        message = text + " The ghost gets closer...";
        fire(type, "");

        // Ghost already on top of you + another mistake = it grabs you (extra life lost)
        if (player.isAlive() && ghost.getGap() == 0) {
            player.loseLife();
            ghost.retreat(3);
            message = "The ghost grabbed you!";
            fire(GameEventType.CAUGHT, "");
        }

        if (!player.isAlive()) {
            lose();
            return;
        }
        setPhase(GamePhase.BETWEEN_STEPS);
        scheduleNextInstruction(GameConfig.AFTER_MISTAKE_DELAY_MS);
    }

    private void poisonHelper() {
        helper.poison();
        ghost.approach();
        ghost.approach();
        message = "A cold whisper... your helper sounds different now.";
        fire(GameEventType.HELPER_POISONED, "");
        scheduleNextInstruction(GameConfig.AFTER_POISON_DELAY_MS);
    }

    private void startGhostEncounter() {
        ghost.startEncounter(now());
        setPhase(GamePhase.GHOST);
        message = "IT'S COMING! HIDE: cover the hiding spot and touch nothing!";
        fire(GameEventType.GHOST_ENCOUNTER, "");
    }

    private void endGhostEncounter(boolean caught) {
        ghost.endEncounter();
        if (caught) {
            player.loseLife();
            message = "It found you!";
            fire(GameEventType.CAUGHT, "");
            if (!player.isAlive()) { lose(); return; }
        } else {
            player.addScore(150);
            message = "It passed by... keep moving.";
            fire(GameEventType.GHOST_PASSED, "");
        }
        setPhase(GamePhase.BETWEEN_STEPS);
        scheduleNextInstruction(1500);
    }

    private void win() {
        cancelTimeout();
        player.addScore(player.getLives() * 500);
        maze.revealAll();
        setPhase(GamePhase.WON);
        message = "You escaped the maze!";
        fire(GameEventType.WON, "");
        updateHeartTempo();
    }

    private void lose() {
        cancelTimeout();
        maze.revealAll();
        setPhase(GamePhase.LOST);
        message = "The maze claims another soul...";
        fire(GameEventType.LOST, "");
        updateHeartTempo();
    }

    // =====================================================================
    // Periodic tick (10 Hz)
    // =====================================================================

    private void safeTick() {
        try {
            tick();
        } catch (Exception e) {
            e.printStackTrace(); // never let an exception kill the scheduler
        }
    }

    private void tick() {
        long now = now();
        if (phase == GamePhase.GHOST) {
            ghost.tick(hidden, now);
            if (ghost.isDetected()) endGhostEncounter(true);
            else if (ghost.encounterOver(now)) endGhostEncounter(false);
        }
        updateHeartTempo();
        publish();
    }

    /** Buzzer heartbeat speeds up with progress, fear, poisoning and the ghost's distance. */
    private void updateHeartTempo() {
        int tempo;
        if (phase == GamePhase.TITLE || phase == GamePhase.WON || phase == GamePhase.LOST) {
            tempo = 0;
        } else {
            tempo = 60 + (int) (progress() * 40) + (int) (currentFear() * 40);
            if (helper.isPoisoned()) tempo += 15;
            if (ghost.getGap() <= 2) tempo += 20;
            if (phase == GamePhase.GHOST) tempo += 35;
            tempo = Math.min(tempo, 200);
        }
        if (lastTempo < 0 || Math.abs(tempo - lastTempo) >= 4 || (tempo == 0) != (lastTempo == 0)) {
            lastTempo = tempo;
            fire(GameEventType.HEARTBEAT_TEMPO, Integer.toString(tempo));
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private long computeWindow() {
        long w = GameConfig.BASE_WINDOW_MS
                - steps * GameConfig.WINDOW_DECAY_PER_STEP_MS
                - (long) (currentFear() * GameConfig.FEAR_WINDOW_PENALTY_MS);
        return Math.max(GameConfig.MIN_WINDOW_MS, w);
    }

    private double progress() {
        return pathLength == 0 ? 0 : (double) steps / pathLength;
    }

    /** Fear (0..1) grows as the player gets further through the maze. */
    private double currentFear() {
        return Math.max(0, Math.min(1, progress() * 0.6));
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }
    }

    private void setPhase(GamePhase p) {
        phase = p;
        phaseStart = now();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    private void fire(GameEventType type, String detail) {
        for (GameListener l : listeners) {
            try {
                l.onEvent(type, detail);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /** Builds an immutable snapshot and hands it to every observer. */
    private void publish() {
        long now = now();
        boolean linkAlive = hardwareConnected && now - lastHardwareLine < 5000;
        GameSnapshot s = new GameSnapshot(
                phase, maze, maze.copyRevealed(),
                player.getRow(), player.getCol(),
                ghost.position(player.getTrail()), ghost.getGap(),
                player.getLives(), player.getScore(),
                steps, pathLength,
                phase == GamePhase.AWAITING_INPUT && spoken != null ? spoken.word : null,
                deadline, window,
                currentFear(),
                helper.isPoisoned(),
                ghost.getDetection(), hidden,
                phaseStart, message, linkAlive);
        for (GameListener l : listeners) {
            try {
                l.onSnapshot(s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
