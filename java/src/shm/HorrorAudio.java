package shm;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class HorrorAudio implements GameListener {

    private static final float RATE = 44100f;
    private static final int BLOCK = 1024;           // samples per write (~23 ms)

    /** One sound being played: precomputed samples and a read position. */
    private static final class Playing {
        final float[] data;
        int pos;
        Playing(float[] data) { this.data = data; }
    }

    private final ConcurrentLinkedQueue<float[]> incoming = new ConcurrentLinkedQueue<>();
    private final Random rng = new Random();

    // state written by the game thread, read by the audio thread
    private volatile int heartbeatBpm;
    private volatile double intensity;   // 0 = calm, 1 = terror
    private volatile boolean ended;

    // precomputed sounds
    private final float[] heartbeat = makeHeartbeat();
    private final float[] wrongHit = makeWrongHit();
    private final float[] chime = makeChime();
    private final float[] poisonSwell = makePoisonSwell();
    private final float[] ghostStabs = makeGhostStabs();
    private final float[] scream = makeScream();
    private final float[] whisper = makeWhisper();
    private final float[] creak = makeCreak();
    private final float[] winSwell = makeWinSwell();
    private final float[] loseBoom = makeLoseBoom();

    public HorrorAudio() {
        Thread t = new Thread(this::mixLoop, "horror-audio");
        t.setDaemon(true);
        t.start();
    }

    // ================================================================ observer

    @Override
    public void onSnapshot(GameSnapshot s) {
        double i = s.fear();
        if (s.helperPoisoned()) i += 0.2;
        if (s.ghostGap() <= 2) i += 0.2;
        if (s.phase() == GamePhase.GHOST) i = 1.0;
        intensity = Math.min(1.0, i);
        ended = s.phase() == GamePhase.WON || s.phase() == GamePhase.LOST;
    }

    @Override
    public void onEvent(GameEventType type, String detail) {
        switch (type) {
            case HEARTBEAT_TEMPO -> heartbeatBpm = GameConfig.LAPTOP_HEARTBEAT ? parse(detail) : 0;
            case CORRECT -> play(chime);
            case WRONG, TIMEOUT -> play(wrongHit);
            case HELPER_POISONED -> play(poisonSwell);
            case GHOST_ENCOUNTER -> play(ghostStabs);
            case GHOST_CLOSE -> play(whisper);
            case CAUGHT -> play(scream);
            case WON -> play(winSwell);
            case LOST -> { play(loseBoom); play(scream); }
            default -> { }
        }
    }

    private void play(float[] sound) {
        incoming.add(sound);
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return 0; }
    }

    // ================================================================ mixer thread

    private void mixLoop() {
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, BLOCK * 2 * 4);
            line.start();

            List<Playing> playing = new ArrayList<>();
            byte[] out = new byte[BLOCK * 2];
            long n = 0;                                  // sample counter
            double windLp = 0, windLfo = 0;
            long samplesSinceBeat = Long.MAX_VALUE / 2;
            long nextCreak = (long) (RATE * (6 + rng.nextDouble() * 10));
            double smoothIntensity = 0;
            double master = GameConfig.HORROR_VOLUME;

            while (true) {
                float[] s;
                while ((s = incoming.poll()) != null) playing.add(new Playing(s));

                for (int i = 0; i < BLOCK; i++, n++) {
                    double t = n / RATE;
                    smoothIntensity += (intensity - smoothIntensity) * 0.00005; // slow glide
                    double inten = ended ? 0.2 : smoothIntensity;

                    // --- ambient drone: two detuned low sines + a dissonant tritone rising with fear
                    double drone = 0.10 * Math.sin(2 * Math.PI * 41.2 * t)
                            + 0.10 * Math.sin(2 * Math.PI * 41.9 * t)
                            + 0.05 * Math.sin(2 * Math.PI * 82.4 * t + Math.sin(t * 0.3))
                            + 0.06 * inten * Math.sin(2 * Math.PI * 58.3 * t);   // tritone above 41 Hz

                    // --- wind: low-passed noise with slow swells
                    windLfo = 0.5 + 0.5 * Math.sin(2 * Math.PI * 0.07 * t) * Math.sin(2 * Math.PI * 0.031 * t);
                    windLp += (rng.nextGaussian() - windLp) * (0.01 + 0.02 * windLfo);
                    double wind = windLp * (0.10 + 0.12 * windLfo) * (0.6 + 0.6 * inten);

                    double mix = drone + wind;

                    // --- heartbeat
                    int bpm = heartbeatBpm;
                    samplesSinceBeat++;
                    if (bpm > 0 && samplesSinceBeat >= RATE * 60.0 / bpm) {
                        samplesSinceBeat = 0;
                        playing.add(new Playing(heartbeat));
                    }

                    // --- random distant creaks
                    if (n >= nextCreak) {
                        playing.add(new Playing(creak));
                        nextCreak = n + (long) (RATE * (8 + rng.nextDouble() * 14 - inten * 5));
                    }

                    // --- one-shot sounds
                    for (Iterator<Playing> it = playing.iterator(); it.hasNext(); ) {
                        Playing p = it.next();
                        mix += p.data[p.pos++];
                        if (p.pos >= p.data.length) it.remove();
                    }

                    // soft clip keeps loud moments from distorting harshly
                    double v = Math.tanh(mix * master) * 0.95;
                    short sample = (short) (v * 32767);
                    out[i * 2] = (byte) sample;
                    out[i * 2 + 1] = (byte) (sample >> 8);
                }
                line.write(out, 0, out.length);   // blocks until the sound card needs more
            }
        } catch (Exception e) {
            System.err.println("Horror audio unavailable: " + e.getMessage());
        }
    }

    // ================================================================ sound design

    private static float[] buffer(double seconds) {
        return new float[(int) (RATE * seconds)];
    }

    /** Deep "lub-dub": two pitch-dropping sine thumps with a short click. */
    private float[] makeHeartbeat() {
        float[] b = buffer(0.6);
        addThump(b, 0.00, 1.0, 62, 38);
        addThump(b, 0.19, 0.75, 55, 34);
        return b;
    }

    private void addThump(float[] b, double start, double amp, double f0, double f1) {
        int s0 = (int) (start * RATE);
        double phase = 0;
        for (int i = 0; s0 + i < b.length && i < RATE * 0.35; i++) {
            double t = i / RATE;
            double f = f1 + (f0 - f1) * Math.exp(-t / 0.05);
            phase += 2 * Math.PI * f / RATE;
            double env = Math.exp(-t / 0.07) * Math.min(1, t / 0.004);
            double click = i < 200 ? rng.nextGaussian() * 0.05 * (1 - i / 200.0) : 0;
            b[s0 + i] += (float) (amp * (0.9 * Math.sin(phase) * env + click));
        }
    }

    /** Wrong answer: low boom + clashing tritone that rings out. */
    private float[] makeWrongHit() {
        float[] b = buffer(1.6);
        double lp = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            lp += (rng.nextGaussian() - lp) * 0.05;
            double boom = Math.sin(2 * Math.PI * (35 + 30 * Math.exp(-t / 0.08)) * t) * Math.exp(-t / 0.35);
            double clash = (Math.sin(2 * Math.PI * 233 * t) + Math.sin(2 * Math.PI * 329.6 * t)
                    + 0.5 * Math.sin(2 * Math.PI * 247 * t)) * 0.12 * Math.exp(-t / 0.5);
            b[i] = (float) (0.9 * boom + clash + lp * 0.5 * Math.exp(-t / 0.1));
        }
        return b;
    }

    /** Correct answer: faint music-box chime, slightly out of tune (unsettling, not cheerful). */
    private float[] makeChime() {
        float[] b = buffer(1.2);
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            double env = Math.exp(-t / 0.35) * Math.min(1, t / 0.003);
            b[i] = (float) (0.10 * env * (Math.sin(2 * Math.PI * 1318 * t) + 0.6 * Math.sin(2 * Math.PI * 1396 * t)
                    + 0.3 * Math.sin(2 * Math.PI * 2637 * t)));
        }
        return b;
    }

    /** Helper possessed: reverse-cymbal swell of noise and falling cluster, cut by a boom. */
    private float[] makePoisonSwell() {
        float[] b = buffer(4.0);
        double lp = 0;
        double[] phases = new double[4];
        double[] start = {420, 445, 300, 317};
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            double swell = t < 2.5 ? Math.pow(t / 2.5, 3) : Math.exp(-(t - 2.5) / 0.4);
            lp += (rng.nextGaussian() - lp) * 0.2;
            double cluster = 0;
            for (int k = 0; k < 4; k++) {
                double f = start[k] * Math.pow(0.35, t / 4.0);   // all voices slide down
                phases[k] += 2 * Math.PI * f / RATE;
                cluster += Math.sin(phases[k]) + 0.3 * Math.sin(2 * phases[k]);
            }
            double boom = t > 2.5 ? Math.sin(2 * Math.PI * 38 * t) * Math.exp(-(t - 2.5) / 0.6) : 0;
            b[i] = (float) (swell * (0.12 * cluster + 0.35 * lp) + 0.9 * boom);
        }
        return b;
    }

    /** Ghost arrives: screeching string-like stabs (sawtooth with vibrato), then a low rumble. */
    private float[] makeGhostStabs() {
        float[] b = buffer(3.2);
        double[] freqs = {1245, 1318, 1175, 1396, 1245, 1480};
        for (int k = 0; k < freqs.length; k++) {
            int s0 = (int) (k * 0.26 * RATE);
            double phase = 0;
            for (int i = 0; i < RATE * 0.22 && s0 + i < b.length; i++) {
                double t = i / RATE;
                double f = freqs[k] * (1 + 0.02 * Math.sin(2 * Math.PI * 7 * t));
                phase += f / RATE;
                double saw = 2 * (phase - Math.floor(phase + 0.5));
                double env = Math.min(1, t / 0.01) * Math.exp(-t / 0.12);
                b[s0 + i] += (float) (0.38 * saw * env);
            }
        }
        double lp = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            lp += (rng.nextGaussian() - lp) * 0.01;
            b[i] += (float) ((0.12 * Math.sin(2 * Math.PI * 36 * t) + lp * 0.4) * Math.min(1, t / 1.0));
        }
        return b;
    }

    /** Caught: shrieking pitch-sweep with heavy vibrato and noise. */
    private float[] makeScream() {
        float[] b = buffer(1.6);
        double phase = 0, phase2 = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            double f = 1100 * Math.exp(-t / 1.2) + 250 + 60 * Math.sin(2 * Math.PI * 11 * t);
            phase += 2 * Math.PI * f / RATE;
            phase2 += 2 * Math.PI * f * 1.51 / RATE;
            double env = Math.min(1, t / 0.02) * Math.exp(-t / 0.7);
            double body = Math.tanh(3 * Math.sin(phase)) * 0.5 + 0.25 * Math.sin(phase2);
            b[i] = (float) (env * (0.55 * body + 0.25 * rng.nextGaussian()));
        }
        return b;
    }

    /** Ghost close: breathy hiss with a fluttering envelope, like a whisper behind you. */
    private float[] makeWhisper() {
        float[] b = buffer(1.4);
        double slow = 0, smooth = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            double noise = rng.nextGaussian();
            slow += (noise - slow) * 0.05;          // low part of the noise
            double hiss = noise - slow;             // remove it = high "sss" hiss
            smooth += (hiss - smooth) * 0.5;        // soften the very top
            double env = Math.sin(Math.PI * t / 1.4) * (0.5 + 0.5 * Math.sin(2 * Math.PI * 6 * t));
            b[i] = (float) (0.3 * smooth * env);
        }
        return b;
    }

    /** Distant door creak: irregular friction clicks through a resonance. */
    private float[] makeCreak() {
        float[] b = buffer(1.5);
        double y1 = 0, y2 = 0;
        double f = 650, r = 0.995;
        double nextClick = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            double x = 0;
            if (t >= nextClick && t < 1.2) {
                x = 1.0;
                double rate = 35 + 25 * Math.sin(Math.PI * t / 1.2) + rng.nextDouble() * 10;
                nextClick = t + 1.0 / rate;
            }
            double w = 2 * Math.PI * (f + 80 * Math.sin(Math.PI * t / 1.2)) / RATE;
            double y = x + 2 * r * Math.cos(w) * y1 - r * r * y2;   // two-pole resonator
            y2 = y1;
            y1 = y;
            b[i] = (float) (0.012 * y * Math.min(1, (1.5 - t) / 0.3));
        }
        return b;
    }

    /** Escape: slow swell of an open chord, still a little uneasy. */
    private float[] makeWinSwell() {
        float[] b = buffer(4.0);
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            double env = Math.sin(Math.PI * t / 4.0);
            b[i] = (float) (0.12 * env * (Math.sin(2 * Math.PI * 220 * t) + Math.sin(2 * Math.PI * 330 * t)
                    + 0.7 * Math.sin(2 * Math.PI * 277.2 * t) + 0.4 * Math.sin(2 * Math.PI * 440.5 * t)));
        }
        return b;
    }

    /** Game over: huge low boom and a dissonant drone that dies away. */
    private float[] makeLoseBoom() {
        float[] b = buffer(5.0);
        double lp = 0;
        for (int i = 0; i < b.length; i++) {
            double t = i / RATE;
            lp += (rng.nextGaussian() - lp) * 0.03;
            double boom = Math.sin(2 * Math.PI * (28 + 40 * Math.exp(-t / 0.1)) * t) * Math.exp(-t / 1.2);
            double drone = (Math.sin(2 * Math.PI * 55 * t) + Math.sin(2 * Math.PI * 58.3 * t)
                    + Math.sin(2 * Math.PI * 77.8 * t)) * 0.12 * Math.exp(-t / 2.0);
            b[i] = (float) (boom + drone + 0.4 * lp * Math.exp(-t / 0.5));
        }
        return b;
    }
}
