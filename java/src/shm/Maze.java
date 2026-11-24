package shm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

public class Maze {
    private final int rows;
    private final int cols;
    private final int[][] walls;
    private final boolean[][] revealed;
    private final int[][] distToExit;

    public final int startRow;
    public final int startCol;
    public final int exitRow;
    public final int exitCol;

    public Maze(int rows, int cols, long seed) {
        this.rows = rows;
        this.cols = cols;
        this.walls = new int[rows][cols];
        this.revealed = new boolean[rows][cols];
        this.distToExit = new int[rows][cols];
        this.startRow = rows - 1;
        this.startCol = 0;
        this.exitRow = 0;
        this.exitCol = cols - 1;

        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                walls[r][c] = 1 | 2 | 4 | 8; // every wall closed

        generate(new Random(seed));
        computeDistances();
        // Nothing is revealed at the start: the player only ever sees where they have been.
    }

    /** Iterative DFS ("recursive backtracker") maze generation. */
    private void generate(Random rng) {
        boolean[][] visited = new boolean[rows][cols];
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startRow, startCol});
        visited[startRow][startCol] = true;

        while (!stack.isEmpty()) {
            int[] cell = stack.peek();
            List<Direction> options = new ArrayList<>();
            for (Direction d : Direction.values()) {
                int nr = cell[0] + d.dRow, nc = cell[1] + d.dCol;
                if (inBounds(nr, nc) && !visited[nr][nc]) options.add(d);
            }
            if (options.isEmpty()) {
                stack.pop();          // dead end: backtrack
                continue;
            }
            Collections.shuffle(options, rng);
            Direction d = options.get(0);
            int nr = cell[0] + d.dRow, nc = cell[1] + d.dCol;
            walls[cell[0]][cell[1]] &= ~d.wallBit;          // knock down the wall on this side
            walls[nr][nc] &= ~d.opposite().wallBit;         // ...and on the neighbour's side
            visited[nr][nc] = true;
            stack.push(new int[]{nr, nc});
        }
    }

    /** Breadth-first search from the exit: distToExit tells the helper the right way from any cell. */
    private void computeDistances() {
        for (int[] row : distToExit) java.util.Arrays.fill(row, -1);
        Deque<int[]> queue = new ArrayDeque<>();
        distToExit[exitRow][exitCol] = 0;
        queue.add(new int[]{exitRow, exitCol});
        while (!queue.isEmpty()) {
            int[] cell = queue.poll();
            for (Direction d : Direction.values()) {
                if (!isValidMove(cell[0], cell[1], d)) continue;
                int nr = cell[0] + d.dRow, nc = cell[1] + d.dCol;
                if (distToExit[nr][nc] == -1) {
                    distToExit[nr][nc] = distToExit[cell[0]][cell[1]] + 1;
                    queue.add(new int[]{nr, nc});
                }
            }
        }
    }

    public boolean inBounds(int r, int c) {
        return r >= 0 && r < rows && c >= 0 && c < cols;
    }

    /** True if the player can step from (r,c) in direction d (no wall, inside the maze). */
    public boolean isValidMove(int r, int c, Direction d) {
        return inBounds(r + d.dRow, c + d.dCol) && (walls[r][c] & d.wallBit) == 0;
    }

    public boolean hasWall(int r, int c, Direction d) {
        return (walls[r][c] & d.wallBit) != 0;
    }

    /** The correct next step towards the exit (what a trustworthy helper says). */
    public Direction nextStepToExit(int r, int c) {
        for (Direction d : Direction.values()) {
            if (isValidMove(r, c, d) && distToExit[r + d.dRow][c + d.dCol] == distToExit[r][c] - 1) {
                return d;
            }
        }
        return null; // already at the exit
    }

    public int distanceToExit(int r, int c) {
        return distToExit[r][c];
    }

    public boolean isExit(int r, int c) {
        return r == exitRow && c == exitCol;
    }

    /**
     * Marks a cell the player has LEFT as visible. Only the trail behind the player
     * is drawn, so the walls ahead never give away the correct direction:
     * the player has to listen to (or reverse) the helper.
     */
    public void markVisited(int r, int c) {
        revealed[r][c] = true;
    }

    public void revealAll() {
        for (boolean[] row : revealed) java.util.Arrays.fill(row, true);
    }

    public boolean[][] copyRevealed() {
        boolean[][] copy = new boolean[rows][];
        for (int r = 0; r < rows; r++) copy[r] = revealed[r].clone();
        return copy;
    }

    public int getRows() { return rows; }
    public int getCols() { return cols; }
}
