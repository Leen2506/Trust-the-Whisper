package shm;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;


public class GameGUI implements GameListener {

    private static final Color BG = new Color(8, 6, 10);
    private static final Color FLOOR = new Color(38, 32, 44);
    private static final Color WALL = new Color(170, 160, 185);
    private static final Color BLOOD = new Color(200, 20, 30);
    private static final Color TEXT = new Color(225, 220, 230);
    private static final Color DIM = new Color(140, 130, 150);

    private final GameController controller;
    private final MazePanel panel = new MazePanel();
    private volatile GameSnapshot snapshot;
    private boolean spaceDown;

    public GameGUI(GameController controller) {
        this.controller = controller;
    }

    /** Must be called on the Swing thread. */
    public void show() {
        JFrame frame = new JFrame("Scary Heartbeat Maze");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        panel.setPreferredSize(new Dimension(1000, 760));
        frame.setContentPane(panel);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        installKeyboard();
        new Timer(33, e -> panel.repaint()).start(); // ~30 FPS for smooth timer bar and effects
    }

    @Override
    public void onSnapshot(GameSnapshot s) {
        snapshot = s; // volatile write; the paint code picks it up on the Swing thread
    }

    // ------------------------------------------------------------ keyboard

    private void installKeyboard() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(new KeyEventDispatcher() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent e) {
                if (e.getID() == KeyEvent.KEY_PRESSED) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_UP, KeyEvent.VK_W -> controller.onDirection(Direction.FORWARD);
                        case KeyEvent.VK_DOWN, KeyEvent.VK_S -> controller.onDirection(Direction.BACKWARD);
                        case KeyEvent.VK_LEFT, KeyEvent.VK_A -> controller.onDirection(Direction.LEFT);
                        case KeyEvent.VK_RIGHT, KeyEvent.VK_D -> controller.onDirection(Direction.RIGHT);
                        case KeyEvent.VK_ENTER -> controller.startOrRestart();
                        case KeyEvent.VK_SPACE -> {
                            if (!spaceDown) { spaceDown = true; controller.onHideState(true); }
                        }
                        default -> { }
                    }
                } else if (e.getID() == KeyEvent.KEY_RELEASED && e.getKeyCode() == KeyEvent.VK_SPACE) {
                    spaceDown = false;
                    controller.onHideState(false);
                }
                return false;
            }
        });
    }

    // ------------------------------------------------------------ drawing

    private class MazePanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0;
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int w = getWidth(), h = getHeight();
            g.setColor(BG);
            g.fillRect(0, 0, w, h);

            GameSnapshot s = snapshot;
            if (s == null) return;
            long now = System.currentTimeMillis();

            int hudH = 70, bottomH = 150;
            drawMaze(g, s, now, 20, hudH, w - 40, h - hudH - bottomH);
            drawHud(g, s, w);
            drawBottom(g, s, now, w, h - bottomH, bottomH);

            switch (s.phase()) {
                case TITLE -> drawTitle(g, w, h);
                case GHOST -> drawGhostEncounter(g, s, now, w, h);
                case WON -> drawEnd(g, s, w, h, "YOU ESCAPED", new Color(80, 220, 120));
                case LOST -> drawEnd(g, s, w, h, "GAME OVER", BLOOD);
                default -> { }
            }
        }

        private void drawMaze(Graphics2D g, GameSnapshot s, long now, int x0, int y0, int areaW, int areaH) {
            Maze m = s.maze();
            int cell = Math.max(10, Math.min(areaW / m.getCols(), areaH / m.getRows()));
            int ox = x0 + (areaW - cell * m.getCols()) / 2;
            int oy = y0 + (areaH - cell * m.getRows()) / 2;
            boolean[][] rev = s.revealed();

            // floor under the player: lit, but its walls are NOT drawn (that would show the way)
            g.setColor(FLOOR);
            g.fillRect(ox + s.playerCol() * cell, oy + s.playerRow() * cell, cell, cell);

            // floor of the trail already walked
            for (int r = 0; r < m.getRows(); r++) {
                for (int c = 0; c < m.getCols(); c++) {
                    if (!rev[r][c]) continue;
                    g.setColor(FLOOR);
                    g.fillRect(ox + c * cell, oy + r * cell, cell, cell);
                }
            }

            // walls of the trail only (cells the player has left)
            g.setColor(WALL);
            g.setStroke(new BasicStroke(Math.max(2, cell / 14f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            for (int r = 0; r < m.getRows(); r++) {
                for (int c = 0; c < m.getCols(); c++) {
                    if (!rev[r][c]) continue;
                    int x = ox + c * cell, y = oy + r * cell;
                    if (m.hasWall(r, c, Direction.FORWARD)) g.drawLine(x, y, x + cell, y);
                    if (m.hasWall(r, c, Direction.BACKWARD)) g.drawLine(x, y + cell, x + cell, y + cell);
                    if (m.hasWall(r, c, Direction.LEFT)) g.drawLine(x, y, x, y + cell);
                    if (m.hasWall(r, c, Direction.RIGHT)) g.drawLine(x + cell, y, x + cell, y + cell);
                }
            }

            // optional purple tint when the helper is poisoned (off by default: memory challenge)
            if (GameConfig.SHOW_HELPER_STATE && s.helperPoisoned()) {
                g.setColor(new Color(90, 0, 120, 50));
                g.fillRect(ox, oy, cell * m.getCols(), cell * m.getRows());
            }

            // darkness: a radial "flashlight" around the player (smaller when afraid)
            float px = ox + s.playerCol() * cell + cell / 2f;
            float py = oy + s.playerRow() * cell + cell / 2f;
            boolean ended = s.phase() == GamePhase.WON || s.phase() == GamePhase.LOST;
            if (!ended) {
                float radius = (float) (cell * (3.2 - 1.2 * s.fear()));
                RadialGradientPaint light = new RadialGradientPaint(new Point2D.Float(px, py), radius,
                        new float[]{0f, 0.55f, 1f},
                        new Color[]{new Color(0, 0, 0, 0), new Color(0, 0, 0, 110), new Color(0, 0, 0, 200)});
                g.setPaint(light);
                g.fillRect(ox - 4, oy - 4, cell * m.getCols() + 8, cell * m.getRows() + 8);
            }

            // exit: glows faintly so the player knows the goal (walls around it stay hidden)
            if (GameConfig.SHOW_EXIT || s.phase() == GamePhase.WON || s.phase() == GamePhase.LOST) {
            int ex = ox + m.exitCol * cell, ey = oy + m.exitRow * cell;
            int glow = 60 + (int) (40 * Math.sin(now / 300.0));
            g.setColor(new Color(60, 200, 110, Math.max(0, Math.min(255, glow))));
            g.fillRect(ex + 4, ey + 4, cell - 8, cell - 8);
            g.setColor(new Color(160, 255, 190));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, cell / 5)));
            centerText(g, "EXIT", ex + cell / 2, ey + cell / 2 + cell / 14);
            }

            // ghost: red eyes in the dark, body when it is close
            int[] gc = s.ghostCell();
            if (gc != null) {
                int gx = ox + gc[1] * cell + cell / 2, gy = oy + gc[0] * cell + cell / 2;
                if (s.ghostGap() <= 3) {
                    int alpha = 60 + 30 * (3 - s.ghostGap());
                    g.setColor(new Color(230, 230, 255, alpha));
                    g.fillOval(gx - cell / 3, gy - cell / 3, cell * 2 / 3, cell * 3 / 4);
                }
                float flicker = (float) (0.6 + 0.4 * Math.sin(now / 120.0));
                g.setColor(new Color(1f, 0.05f, 0.05f, flicker));
                int eye = Math.max(3, cell / 10);
                g.fillOval(gx - cell / 7 - eye / 2, gy - eye / 2, eye, eye);
                g.fillOval(gx + cell / 7 - eye / 2, gy - eye / 2, eye, eye);
            }

            // player
            g.setColor(new Color(255, 245, 220));
            int pr = cell / 4;
            g.fillOval((int) px - pr, (int) py - pr, pr * 2, pr * 2);
            if (s.hidden()) { // crouched / hidden indicator
                g.setColor(new Color(120, 180, 255));
                g.setStroke(new BasicStroke(2f));
                g.drawOval((int) px - pr - 4, (int) py - pr - 4, pr * 2 + 8, pr * 2 + 8);
            }
        }

        private void drawHud(Graphics2D g, GameSnapshot s, int w) {
            g.setStroke(new BasicStroke(1f));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            g.setColor(BLOOD);
            StringBuilder hearts = new StringBuilder();
            for (int i = 0; i < GameConfig.START_LIVES; i++) hearts.append(i < s.lives() ? "\u2665 " : "\u2661 ");
            g.drawString(hearts.toString(), 24, 44);

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
            g.setColor(TEXT);
            g.drawString("Score " + s.score(), 170, 42);
            g.drawString("Step " + s.stepsTaken() + "/" + s.pathLength(), 320, 42);

            // fear bar
            g.setColor(DIM);
            g.drawString("Fear", 480, 42);
            g.drawRect(525, 28, 120, 16);
            g.setColor(new Color(200, 30, 50));
            g.fillRect(526, 29, (int) (119 * s.fear()), 15);

            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            g.setColor(s.hardwareConnected() ? new Color(110, 220, 140) : new Color(230, 180, 80));
            String link = s.hardwareConnected() ? "ZigBee: linked" : "ZigBee: no data (keyboard works)";
            g.drawString(link, w - g.getFontMetrics().stringWidth(link) - 24, 22);

            if (GameConfig.SHOW_HELPER_STATE) {
                g.setColor(s.helperPoisoned() ? new Color(190, 90, 255) : new Color(120, 220, 160));
                g.drawString("Helper: " + (s.helperPoisoned() ? "POISONED" : "trusted"), w - 200, 62);
            }
        }

        private void drawBottom(Graphics2D g, GameSnapshot s, long now, int w, int y, int h) {
            if (s.instruction() != null) {
                g.setFont(new Font(Font.SERIF, Font.ITALIC, 18));
                g.setColor(DIM);
                centerText(g, "your helper whispers...", w / 2, y + 30);
                g.setFont(new Font(Font.SERIF, Font.BOLD, 52));
                g.setColor(TEXT);
                centerText(g, s.instruction(), w / 2, y + 85);

                // response timer bar: green -> red as time runs out
                double left = Math.max(0, s.deadlineMillis() - now) / (double) Math.max(1, s.windowMillis());
                int barW = w / 2;
                int bx = (w - barW) / 2, by = y + 105;
                g.setColor(new Color(40, 35, 45));
                g.fillRect(bx, by, barW, 12);
                g.setColor(new Color((int) (220 * (1 - left)) + 30, (int) (200 * left) + 30, 50));
                g.fillRect(bx, by, (int) (barW * left), 12);
            }
            if (s.message() != null && !s.message().isEmpty()) {
                g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 18));
                g.setColor(new Color(230, 160, 160));
                centerText(g, s.message(), w / 2, y + 140);
            }
        }

        private void drawTitle(Graphics2D g, int w, int h) {
            shade(g, w, h, 215);
            g.setFont(new Font(Font.SERIF, Font.BOLD, 56));
            g.setColor(BLOOD);
            centerText(g, "SCARY HEARTBEAT MAZE", w / 2, h / 2 - 140);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 19));
            g.setColor(TEXT);
            String[] lines = {
                    "Your helper whispers a direction. Touch the matching sensor before time runs out.",
                    "But the ghost is hunting you... and it may possess your helper.",
                    "Once the helper is poisoned, every instruction is a lie: do the OPPOSITE.",
                    "When the ghost comes, HIDE: cover the hiding spot and touch nothing.",
                    "",
                    "Touch any sensor or press ENTER to begin",
                    "(Keyboard: arrows/WASD = directions, hold SPACE = hide)"
            };
            for (int i = 0; i < lines.length; i++) {
                g.setColor(i == 5 ? new Color(255, 200, 120) : (i == 6 ? DIM : TEXT));
                centerText(g, lines[i], w / 2, h / 2 - 60 + i * 30);
            }
        }

        private void drawGhostEncounter(Graphics2D g, GameSnapshot s, long now, int w, int h) {
            // flashing red frame
            int a = 90 + (int) (80 * Math.sin(now / 90.0));
            g.setColor(new Color(200, 0, 0, Math.max(0, Math.min(255, a))));
            g.setStroke(new BasicStroke(14f));
            g.drawRect(7, 7, w - 14, h - 14);

            g.setColor(new Color(0, 0, 0, 190));
            g.fillRect(0, h / 2 - 90, w, 170);
            g.setFont(new Font(Font.SERIF, Font.BOLD, 60));
            g.setColor(s.hidden() ? new Color(120, 180, 255) : BLOOD);
            centerText(g, s.hidden() ? "STAY HIDDEN..." : "HIDE!", w / 2, h / 2 - 20);

            long left = Math.max(0, GameConfig.GHOST_ENCOUNTER_MS - (now - s.phaseStartMillis()));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
            g.setColor(TEXT);
            centerText(g, "Cover the hiding spot. Don't touch anything. " + (left / 1000 + 1) + "s",
                    w / 2, h / 2 + 20);

            // detection meter
            int barW = 360, bx = (w - barW) / 2, by = h / 2 + 40;
            g.setColor(new Color(40, 30, 35));
            g.fillRect(bx, by, barW, 18);
            g.setColor(BLOOD);
            g.fillRect(bx, by, barW * s.detection() / 100, 18);
            g.setColor(TEXT);
            g.setStroke(new BasicStroke(1f));
            g.drawRect(bx, by, barW, 18);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
            centerText(g, "detection", w / 2, by + 36);
        }

        private void drawEnd(Graphics2D g, GameSnapshot s, int w, int h, String title, Color c) {
            shade(g, w, h, 150);
            g.setFont(new Font(Font.SERIF, Font.BOLD, 64));
            g.setColor(c);
            centerText(g, title, w / 2, h / 2 - 30);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));
            g.setColor(TEXT);
            centerText(g, "Score: " + s.score() + "    Steps: " + s.stepsTaken() + "/" + s.pathLength(), w / 2, h / 2 + 20);
            g.setColor(DIM);
            centerText(g, "Touch any sensor or press ENTER to play again", w / 2, h / 2 + 60);
        }

        private void shade(Graphics2D g, int w, int h, int alpha) {
            g.setColor(new Color(0, 0, 0, alpha));
            g.fillRect(0, 0, w, h);
        }

        private void centerText(Graphics2D g, String text, int cx, int baselineY) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(text, cx - fm.stringWidth(text) / 2, baselineY);
        }
    }
}
