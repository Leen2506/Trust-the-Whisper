package shm;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


public class SoundManager implements GameListener {

    /** clip name -> sentence spoken by the text-to-speech fallback */
    private static final Map<String, String> LINES = new LinkedHashMap<>();
    static {
        LINES.put("forward", "Forward");
        LINES.put("backward", "Backward");
        LINES.put("left", "Left");
        LINES.put("right", "Right");
        LINES.put("start", "Stay calm. I will guide you out.");
        LINES.put("poisoned", "I... don't feel like myself anymore.");
        LINES.put("hide", "Hide! It is coming!");
        LINES.put("passed", "It is gone. Keep moving.");
        LINES.put("caught", "Found you.");
        LINES.put("won", "You escaped.");
        LINES.put("lost", "You belong to the maze now.");
    }

    private record ClipData(AudioFormat format, byte[] bytes) {}

    private final File soundDir = new File("sounds");
    /** Generated clips live in their own folder; bump the name to force regeneration. */
    private final File generatedDir = new File(soundDir, "generated_v2");
    private final Map<String, ClipData> normal = new ConcurrentHashMap<>();
    private final Map<String, ClipData> creepy = new ConcurrentHashMap<>();
    private final ExecutorService audio = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "audio");
        t.setDaemon(true);
        return t;
    });

    public SoundManager() {
        audio.execute(this::prepareAll);
    }

    // ------------------------------------------------------------------ events

    @Override
    public void onEvent(GameEventType type, String detail) {
        switch (type) {
            case INSTRUCTION -> {
                // detail is e.g. "LEFT" or "LEFT:P" (P = helper is poisoned)
                boolean poisoned = detail.endsWith(":P");
                String word = detail.split(":")[0].toLowerCase();
                play(word, poisoned && GameConfig.DISTORT_POISONED_VOICE);
            }
            case GAME_START -> play("start", false);
            case HELPER_POISONED -> play("poisoned", true);
            case GHOST_ENCOUNTER -> play("hide", false);
            case GHOST_PASSED -> play("passed", false);
            case CAUGHT -> play("caught", true);
            case WON -> play("won", false);
            case LOST -> play("lost", true);
            default -> { }
        }
    }

    private void play(String name, boolean distorted) {
        audio.execute(() -> {
            ClipData data = (distorted ? creepy : normal).get(name);
            if (data == null) return;
            try {
                Clip clip = AudioSystem.getClip();
                clip.open(data.format(), data.bytes(), 0, data.bytes().length);
                clip.addLineListener(e -> {
                    if (e.getType() == LineEvent.Type.STOP) clip.close();
                });
                clip.start();
            } catch (Exception e) {
                System.err.println("Audio error for " + name + ": " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------- loading

    private void prepareAll() {
        if (!generatedDir.exists() && !generatedDir.mkdirs()) return;
        for (Map.Entry<String, String> entry : LINES.entrySet()) {
            String name = entry.getKey();
            File file = findFile(name);
            if (file == null) file = generateWithTts(name, entry.getValue());
            if (file != null) load(name, file);
        }
        System.out.println("Voice clips ready: " + normal.keySet()
                + "  (folder: " + soundDir.getAbsolutePath() + ")");
    }

    /** Your own recording (sounds/name.wav) wins; otherwise a clip generated earlier. */
    private File findFile(String name) {
        File own = new File(soundDir, name + ".wav");
        if (own.exists() && own.length() > 0) return own;
        File generated = new File(generatedDir, name + ".wav");
        if (generated.exists() && generated.length() > 0) return generated;
        return null;
    }

    /** Creates the clip once with the OS text-to-speech engine. Returns null if not possible. */
    private File generateWithTts(String name, String text) {
        String os = System.getProperty("os.name").toLowerCase();
        try {
            ProcessBuilder pb;
            File out;
            if (os.contains("win")) {
                out = new File(generatedDir, name + ".wav");
                String script = "Add-Type -AssemblyName System.Speech; "
                        + "$s = New-Object System.Speech.Synthesis.SpeechSynthesizer; "
                        + "$s.Rate = 1; "
                        + "$s.SetOutputToWaveFile('" + out.getAbsolutePath().replace("'", "''") + "'); "
                        + "$s.Speak('" + text.replace("'", "''") + "'); $s.Dispose()";
                pb = new ProcessBuilder("powershell", "-NoProfile", "-Command", script);
            } else if (os.contains("mac")) {
                // WAV with plain 16-bit samples: always playable by Java Sound
                out = new File(generatedDir, name + ".wav");
                File macOut = out;
                if (!GameConfig.MAC_HELPER_VOICE.isEmpty()) {
                    // Try the creepy voice first (e.g. "Whisper"); fall back to the default voice below
                    Process p = new ProcessBuilder("/usr/bin/say", "-v", GameConfig.MAC_HELPER_VOICE,
                            "-o", macOut.getAbsolutePath(), "--file-format=WAVE", "--data-format=LEI16@22050", text)
                            .redirectErrorStream(true).start();
                    p.getInputStream().readAllBytes();
                    if (p.waitFor(20, TimeUnit.SECONDS) && p.exitValue() == 0 && macOut.length() > 0) return macOut;
                }
                pb = new ProcessBuilder("/usr/bin/say", "-o", out.getAbsolutePath(),
                        "--file-format=WAVE", "--data-format=LEI16@22050", text);
            } else {
                out = new File(generatedDir, name + ".wav");
                pb = new ProcessBuilder("espeak", "-w", out.getAbsolutePath(), text);
            }
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().readAllBytes();
            if (p.waitFor(20, TimeUnit.SECONDS) && out.exists() && out.length() > 0) return out;
        } catch (Exception e) {
            System.err.println("Text-to-speech failed for '" + name + "': " + e.getMessage());
        }
        return null;
    }

    /**
     * Haunted-corridor echo: each sample is repeated 140 ms later at 45% volume,
     * several times, and the clip is extended so the tail can ring out.
     */
    private static byte[] addEcho(byte[] pcm, AudioFormat f) {
        int channels = f.getChannels();
        int frames = pcm.length / (2 * channels);
        int delay = (int) (f.getSampleRate() * 0.14);
        int tail = delay * 5;
        float[] x = new float[(frames + tail) * channels];
        for (int i = 0; i < frames * channels; i++) {
            x[i] = (short) ((pcm[2 * i] & 0xff) | (pcm[2 * i + 1] << 8));
        }
        for (int i = delay * channels; i < x.length; i++) {
            x[i] += 0.45f * x[i - delay * channels];
        }
        byte[] out = new byte[x.length * 2];
        for (int i = 0; i < x.length; i++) {
            int v = Math.max(-32768, Math.min(32767, Math.round(x[i] * 0.8f)));
            out[2 * i] = (byte) v;
            out[2 * i + 1] = (byte) (v >> 8);
        }
        return out;
    }

    /** Decodes a file to 16-bit PCM and also builds a pitched-down "possessed" copy. */
    private void load(String name, File file) {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(file)) {
            AudioFormat src = in.getFormat();
            AudioFormat pcm = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
                    src.getSampleRate(), 16, src.getChannels(), src.getChannels() * 2,
                    src.getSampleRate(), false);
            byte[] bytes;
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm, in)) {
                bytes = converted.readAllBytes();
            }
            bytes = addEcho(bytes, pcm);
            normal.put(name, new ClipData(pcm, bytes));
            // Same samples played at 70% speed = deeper, slower, creepier voice
            AudioFormat slow = new AudioFormat(pcm.getSampleRate() * 0.7f, 16, pcm.getChannels(), true, false);
            creepy.put(name, new ClipData(slow, bytes));
        } catch (Exception e) {
            System.err.println("Could not load " + file + ": " + e.getMessage());
        }
    }
}
