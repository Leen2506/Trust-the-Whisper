package shm;

import com.fazecast.jSerialComm.SerialPort;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;


public class SensorManager {
    private final SensorListener listener;
    private SerialPort port;
    private Thread readerThread;
    private volatile boolean running;

    public SensorManager(SensorListener listener) {
        this.listener = listener;
    }

    /** Names of every serial port on this computer (for the start-up chooser). */
    public static SerialPort[] availablePorts() {
        return SerialPort.getCommPorts();
    }

    public boolean connect(String portName) {
        port = SerialPort.getCommPort(portName);
        port.setComPortParameters(GameConfig.BAUD_RATE, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 200, 0);
        if (!port.openPort()) {
            System.err.println("Could not open " + portName + " (is XCTU or the Arduino Serial Monitor still using it?)");
            return false;
        }
        running = true;
        readerThread = new Thread(this::readLoop, "zigbee-reader");
        readerThread.setDaemon(true);
        readerThread.start();
        System.out.println("Connected to " + portName + " at " + GameConfig.BAUD_RATE + " baud");
        return true;
    }

    /** Reader thread: collects bytes into lines and parses each complete line. */
    private void readLoop() {
        StringBuilder line = new StringBuilder();
        byte[] buffer = new byte[64];
        try (InputStream in = port.getInputStream()) {
            while (running) {
                int n;
                try {
                    n = in.read(buffer);
                } catch (Exception timeout) {
                    continue; // read timeout with no data — normal, just loop again
                }
                if (n <= 0) continue;
                for (int i = 0; i < n; i++) {
                    char c = (char) buffer[i];
                    if (c == '\n' || c == '\r') {
                        if (line.length() > 0) {
                            parseLine(line.toString().trim());
                            line.setLength(0);
                        }
                    } else if (line.length() < 32) {
                        line.append(c);
                    } else {
                        line.setLength(0); // garbage / radio noise: drop it
                    }
                }
            }
        } catch (Exception e) {
            if (running) e.printStackTrace();
        }
    }

    /** Converts one protocol line into a SensorListener callback. Package-private for testing. */
    void parseLine(String msg) {
        if (msg.isEmpty()) return;
        listener.onHardwareAlive();
        if (msg.startsWith("D:") && msg.length() >= 3) {
            Direction d = Direction.fromCode(msg.charAt(2));
            if (d != null) listener.onDirection(d);
        } else if (msg.startsWith("I:")) {
            listener.onHideState(msg.endsWith("1"));
        } else if (msg.equals("HELLO")) {
            System.out.println("Controller says HELLO — ZigBee link is up");
        } else {
            System.out.println("Unknown message: " + msg);
        }
    }

    /** Sends one command line to the Arduino. Synchronized because several threads may send. */
    public synchronized void send(String command) {
        if (port == null || !port.isOpen()) return;
        byte[] data = (command + "\n").getBytes(StandardCharsets.US_ASCII);
        port.writeBytes(data, data.length);
    }

    public boolean isConnected() {
        return port != null && port.isOpen();
    }

    public void close() {
        running = false;
        if (port != null) port.closePort();
    }
}
