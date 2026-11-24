package shm;

import java.util.ArrayList;
import java.util.List;


public class Player {
    private int row;
    private int col;
    private int lives;
    private int score;
    /** Every cell the player has stood on; the ghost follows this trail. */
    private final List<int[]> trail = new ArrayList<>();

    public Player(int row, int col, int lives) {
        this.row = row;
        this.col = col;
        this.lives = lives;
        trail.add(new int[]{row, col});
    }

    public void move(Direction d) {
        row += d.dRow;
        col += d.dCol;
        trail.add(new int[]{row, col});
    }

    public void loseLife() { if (lives > 0) lives--; }
    public void addScore(int points) { score += points; }
    public boolean isAlive() { return lives > 0; }

    public int getRow() { return row; }
    public int getCol() { return col; }
    public int getLives() { return lives; }
    public int getScore() { return score; }
    public List<int[]> getTrail() { return trail; }
}
