package shm;

import com.fazecast.jSerialComm.SerialPort;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;


public class Main {
    public static void main(String[] args) {
        GameController controller = new GameController();
        SensorManager sensors = new SensorManager(controller);

        String portName = args.length > 0 ? args[0] : choosePort();
        if (portName != null && !portName.equalsIgnoreCase("sim")) {
            boolean ok = sensors.connect(portName);
            controller.setHardwareConnected(ok);
            if (!ok) {
                JOptionPane.showMessageDialog(null,
                        "Could not open " + portName + ".\nClose XCTU / Serial Monitor and try again.\n"
                                + "Starting in keyboard mode.");
            }
        }

        // Observers of the controller
        controller.addListener(new HardwareFeedback(sensors));
        controller.addListener(new SoundManager());   // helper voice
        controller.addListener(new HorrorAudio());    // ambient, heartbeat, stingers
        GameGUI gui = new GameGUI(controller);
        controller.addListener(gui);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sensors.send("O");    // controller: LED and buzzer off when the game closes
            sensors.close();
        }));

        SwingUtilities.invokeLater(gui::show);
    }

    /** Shows a list of serial ports; the XBee Explorer is usually "USB Serial Port" / FTDI. */
    private static String choosePort() {
        SerialPort[] ports = SensorManager.availablePorts();
        String[] options = new String[ports.length + 1];
        for (int i = 0; i < ports.length; i++) {
            options[i] = ports[i].getSystemPortName() + "  -  " + ports[i].getDescriptivePortName();
        }
        options[ports.length] = "sim  -  Keyboard simulation (no hardware)";
        Object choice = JOptionPane.showInputDialog(null,
                "Select the port of the XBee Explorer (receiver XBee):",
                "Scary Heartbeat Maze", JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        if (choice == null) System.exit(0);
        return choice.toString().split("\\s+")[0];
    }
}
