import com.fazecast.jSerialComm.SerialPort;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java-Anbindung für den Calliope mini V3 — analog zu PicoIO.java,
 * aber für die Calliope-mini-eigene Hardware (LED-Matrix, RGB-LEDs, Motoren,
 * Beschleunigung, Kompass, Temperatur, Mikrofon, Ringpads P0–P3, Buttons,
 * Touch-Logo).
 *
 * Der Calliope mini muss vor der ersten Verwendung mit der mbFirmata-Firmware
 * (siehe Firmata/-Ordner) geflasht sein.
 */
public class CalliopeIO {

    // ---- Pin-Konstanten (Firmata-Index 0..20) ------------------------------
    // Ringpads
    public static final int P0 = 0, P1 = 1, P2 = 2, P3 = 3;
    // Buttons (intern, NICHT extern bespielen)
    public static final int PIN_BUTTON_A = 5;
    public static final int PIN_BUTTON_B = 11;
    // Grove-Pins (Index entspricht der Firmata-Aliasierung)
    public static final int A0_SCL = 19, A0_SDA = 20; // Grove A0 (I²C)
    public static final int A1_RX  = 16, A1_TX  = 17; // Grove A1 (UART/PWM)

    // ---- Sensor-Channels (mbFirmata-Mapping) -------------------------------
    private static final int CH_ACCEL_X    = 8;
    private static final int CH_ACCEL_Y    = 9;
    private static final int CH_ACCEL_Z    = 10;
    private static final int CH_LIGHT      = 11;
    private static final int CH_TEMP       = 12;
    private static final int CH_COMPASS_X  = 13;
    private static final int CH_COMPASS_Y  = 14;
    private static final int CH_COMPASS_Z  = 15;

    // ---- USB-Identifikation des Calliope mini V3 ---------------------------
    // DAPLink (ARM mbed) Standard: VID 0x0D28, PID 0x0204.
    // Falls auf eurem Board abweichend: über addUsbId(...) ergänzbar.
    private final Set<Integer> validVids = new HashSet<>();
    private final Set<Integer> validPids = new HashSet<>();

    // ---- Zustand -----------------------------------------------------------
    private FirmataLink link;
    private boolean boardActive   = false;
    private boolean buttonALatched = false;
    private boolean buttonBLatched = false;
    private long    startZeit     = -1L;

    // Loop-System (1:1 wie PicoIO)
    private final ConcurrentHashMap<Integer, Thread> loops = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeLoopIdentifiers = new HashMap<>();
    private int loopCounter = 0;

    // Button-Monitor-Thread für Edge-Detection
    private Thread monitorThread;

    public CalliopeIO() {
        validVids.add(0x0D28);
        validPids.add(0x0204);
    }

    /** Zusätzliche USB-Identifikation aktivieren, falls euer Calliope mini abweicht. */
    public void addUsbId(int vid, int pid) {
        validVids.add(vid);
        validPids.add(pid);
    }

    private boolean openAttempted = false;

    private void ensureConnected() {
        if (boardActive && link != null) return;
        if (!openAttempted) {
            openAttempted = true;
            System.out.println("Starte automatische Verbindung zum Calliope mini...");
            open();
        }
        if (link == null) {
            throw new IllegalStateException(
                "Calliope mini nicht verbunden. " +
                "Bitte Firmware flashen (Calliope-IO/Software/Java/Firmata/CalliopeMiniFirmata.hex) " +
                "und USB-Kabel prüfen. Mit hardReset() lässt sich ein neuer Verbindungsversuch anstoßen."
            );
        }
    }

    // ---- Verbindung --------------------------------------------------------

    public void open() {
        openAttempted = true;
        if (link != null) {
            System.err.println("Calliope mini ist bereits verbunden.");
            return;
        }
        String portName = findCalliopePort();
        if (portName == null) {
            System.err.println("Kein Calliope mini V3 gefunden. Anschluss / Berechtigungen prüfen.");
            return;
        }
        link = new FirmataLink();
        if (!link.open(portName)) {
            System.err.println("Verbindung zum Calliope mini fehlgeschlagen.");
            link = null;
            return;
        }
        initPins();
        boardActive = true;
        startMonitor();
        System.out.println("Calliope mini verbunden auf: " + portName +
                " (Firmware " + link.getFirmwareMajor() + "." + link.getFirmwareMinor() + ")");
    }

    public void close() {
        if (link == null) {
            System.err.println("Kein Calliope mini verbunden.");
            return;
        }
        try {
            stopAllLoops();
        } catch (Exception ignored) {}
        try {
            clearDisplay();
            clearRgb();
            stopMotors();
            stopBeep();
        } catch (Exception ignored) {}
        boardActive = false;
        if (monitorThread != null) monitorThread.interrupt();
        link.close();
        link = null;
        System.out.println("Verbindung zum Calliope mini beendet.");
    }

    public boolean getStatus() { return boardActive; }

    public void hardReset() {
        System.out.println("Hard-Reset wird ausgeführt...");
        try { stopAllLoops(); } catch (Exception ignored) {}
        if (link != null) { try { link.close(); } catch (Exception ignored) {} link = null; }
        boardActive = false;
        openAttempted = false;
        if (monitorThread != null) { monitorThread.interrupt(); monitorThread = null; }
        System.out.println("Hard-Reset abgeschlossen.");
    }

    private String findCalliopePort() {
        SerialPort[] ports = SerialPort.getCommPorts();
        for (SerialPort p : ports) {
            int vid = p.getVendorID();
            int pid = p.getProductID();
            if (validVids.contains(vid) && validPids.contains(pid)) {
                String id = portIdentifier(p);
                System.out.printf("Calliope-mini-Kandidat: %s (VID=0x%04X, PID=0x%04X)%n",
                        id, vid, pid);
                return id;
            }
        }
        return null;
    }

    /**
     * Liefert den Port-Namen in der Form, die jSSC erwartet.
     * jSerialComm gibt unter macOS/Linux nur den Basenamen (z. B. "cu.usbmodem102"),
     * jSSC will jedoch den vollen Geräte-Pfad ("/dev/cu.usbmodem102").
     */
    private static String portIdentifier(SerialPort p) {
        String name = p.getSystemPortName();
        String os = System.getProperty("os.name").toLowerCase();
        boolean unixLike = os.contains("mac") || os.contains("darwin")
                        || os.contains("nux") || os.contains("nix") || os.contains("aix");
        if (unixLike && name != null && !name.startsWith("/")) {
            return "/dev/" + name;
        }
        return name;
    }

    private void initPins() {
        // Buttons als digitale Eingänge mit Streaming für Port 0 (Pins 0-7) und Port 1 (Pins 8-15).
        link.setPinMode(PIN_BUTTON_A, FirmataLink.MODE_DIGITAL_INPUT);
        link.setPinMode(PIN_BUTTON_B, FirmataLink.MODE_DIGITAL_INPUT);
        link.streamDigital(0, true); // Pins 0..7
        link.streamDigital(1, true); // Pins 8..15

        // Sensorchannels (Beschleunigung, Kompass, Temperatur) dauerhaft streamen.
        for (int ch : new int[]{CH_ACCEL_X, CH_ACCEL_Y, CH_ACCEL_Z,
                                CH_TEMP, CH_COMPASS_X, CH_COMPASS_Y, CH_COMPASS_Z}) {
            link.streamAnalog(ch, true);
        }
        link.setSamplingInterval(50); // 20 Hz reicht für die Beispiele
    }

    private void startMonitor() {
        monitorThread = new Thread(() -> {
            while (boardActive) {
                if (link.digitalRose(PIN_BUTTON_A)) buttonALatched = true;
                if (link.digitalRose(PIN_BUTTON_B)) buttonBLatched = true;
                try { Thread.sleep(30); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    // ---- Buttons -----------------------------------------------------------

    /** Aktueller Druckzustand Button A. */
    public boolean isButtonAPressed() {
        ensureConnected();
        return !link.digital(PIN_BUTTON_A); // active-low
    }

    /** Aktueller Druckzustand Button B. */
    public boolean isButtonBPressed() {
        ensureConnected();
        return !link.digital(PIN_BUTTON_B);
    }

    /** True einmalig, wenn Button A seit dem letzten Aufruf gedrückt wurde. */
    public boolean wasButtonAPressed() {
        ensureConnected();
        boolean v = buttonALatched;
        buttonALatched = false;
        return v;
    }

    /** True einmalig, wenn Button B seit dem letzten Aufruf gedrückt wurde. */
    public boolean wasButtonBPressed() {
        ensureConnected();
        boolean v = buttonBLatched;
        buttonBLatched = false;
        return v;
    }

    public void clearPressed() {
        ensureConnected();
        buttonALatched = false;
        buttonBLatched = false;
    }

    // ---- LED-Matrix (5×5) --------------------------------------------------

    /** Einzelne LED setzen (x, y in 0..4; brightness 0..255). */
    public void ledDim(int x, int y, int brightness) {
        ensureConnected();
        if (x < 0 || x > 4 || y < 0 || y > 4) return;
        int level = Math.max(0, Math.min(255, brightness)) / 2; // 0..127 (7-bit)
        link.sendSysEx(FirmataLink.MB_DISPLAY_PLOT,
                new byte[]{(byte) x, (byte) y, (byte) level});
    }

    public void ledOn(int x, int y)  { ledDim(x, y, 255); }
    public void ledOff(int x, int y) { ledDim(x, y, 0);   }

    public void ledSet(int x, int y, boolean state) {
        ledDim(x, y, state ? 255 : 0);
    }

    /** Komplette 5×5-Matrix in einem Rutsch (grid[y][x] = Helligkeit 0..255). */
    public void showImage(int[][] grid) {
        ensureConnected();
        if (grid == null || grid.length != 5) return;
        byte[] payload = new byte[1 + 25];
        payload[0] = 1; // grayscale flag
        for (int y = 0; y < 5; y++) {
            int[] row = grid[y];
            if (row == null || row.length != 5) return;
            for (int x = 0; x < 5; x++) {
                int v = Math.max(0, Math.min(255, row[x])) / 2;
                payload[1 + (5 * y) + x] = (byte) v;
            }
        }
        link.sendSysEx(FirmataLink.MB_DISPLAY_SHOW, payload);
    }

    public void clearDisplay() {
        ensureConnected();
        link.sendSysEx(FirmataLink.MB_DISPLAY_CLEAR, null);
    }

    /** Text auf der Matrix scrollen lassen (delay 50..400 ms pro Spalte ist sinnvoll). */
    public void scrollText(String text, int delay) {
        ensureConnected();
        if (text == null) text = "";
        int d = Math.max(1, Math.min(127, delay));
        byte[] bytes = text.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] payload = new byte[1 + bytes.length * 2];
        payload[0] = (byte) d;
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            payload[1 + i * 2]     = (byte) (b & 0x7F);
            payload[1 + i * 2 + 1] = (byte) ((b >> 7) & 0x01);
        }
        link.sendSysEx(FirmataLink.MB_SCROLL_STRING, payload);
    }

    public void scrollText(String text) { scrollText(text, 120); }

    public void scrollNumber(int n, int delay) {
        ensureConnected();
        int d = Math.max(1, Math.min(127, delay));
        link.sendSysEx(FirmataLink.MB_SCROLL_INTEGER, new byte[]{
            (byte) d,
            (byte) (n & 0x7F),
            (byte) ((n >> 7)  & 0x7F),
            (byte) ((n >> 14) & 0x7F),
            (byte) ((n >> 21) & 0x7F),
            (byte) ((n >> 28) & 0x7F)
        });
    }

    public void scrollNumber(int n) { scrollNumber(n, 120); }

    /** Display ein-/ausschalten. Ausgeschaltet werden die Display-Pins (C4,C6,C7,C10,C18)
     *  als reguläre GPIOs nutzbar. */
    public void enableDisplay(boolean enable) {
        ensureConnected();
        link.sendSysEx(FirmataLink.MB_DISPLAY_ENABLE, new byte[]{(byte) (enable ? 1 : 0)});
    }

    // ---- RGB-LEDs (3 Stück) ------------------------------------------------

    private final int[][] rgbState = new int[3][3]; // [led][r,g,b], 0..127

    /** Eine RGB-LED setzen (led 0..2; r/g/b je 0..255 — intern auf 7 Bit reduziert). */
    public void setRgb(int led, int r, int g, int b) {
        ensureConnected();
        if (led < 0 || led > 2) return;
        rgbState[led][0] = Math.max(0, Math.min(255, r)) / 2;
        rgbState[led][1] = Math.max(0, Math.min(255, g)) / 2;
        rgbState[led][2] = Math.max(0, Math.min(255, b)) / 2;
        flushRgb();
    }

    public void setRgbAll(int r, int g, int b) {
        for (int i = 0; i < 3; i++) setRgb(i, r, g, b);
    }

    public void clearRgb() {
        setRgbAll(0, 0, 0);
    }

    private void flushRgb() {
        byte[] payload = new byte[9];
        for (int led = 0; led < 3; led++) {
            payload[led * 3 + 0] = (byte) rgbState[led][0];
            payload[led * 3 + 1] = (byte) rgbState[led][1];
            payload[led * 3 + 2] = (byte) rgbState[led][2];
        }
        link.sendSysEx(FirmataLink.MB_CALLIOPE_RGB, payload);
    }

    // ---- Motoren -----------------------------------------------------------

    /**
     * Motor steuern.
     * @param motor    0 = M_A, 1 = M_B, 2 = beide
     * @param forward  true = vorwärts, false = rückwärts
     * @param speed    0..127
     */
    public void setMotor(int motor, boolean forward, int speed) {
        ensureConnected();
        if (motor < 0 || motor > 2) return;
        int s = Math.max(0, Math.min(127, speed));
        link.sendSysEx(FirmataLink.MB_CALLIOPE_MOTOR,
                new byte[]{(byte) motor, (byte) (forward ? 0 : 1), (byte) s});
    }

    public void stopMotor(int motor) { setMotor(motor, true, 0); }
    public void stopMotors() { setMotor(2, true, 0); }

    // ---- Sensoren ----------------------------------------------------------

    public int getAccelerationX() { ensureConnected(); return signed14(link.analog(CH_ACCEL_X)); }
    public int getAccelerationY() { ensureConnected(); return signed14(link.analog(CH_ACCEL_Y)); }
    public int getAccelerationZ() { ensureConnected(); return signed14(link.analog(CH_ACCEL_Z)); }

    public int getCompassX() { ensureConnected(); return signed14(link.analog(CH_COMPASS_X)); }
    public int getCompassY() { ensureConnected(); return signed14(link.analog(CH_COMPASS_Y)); }
    public int getCompassZ() { ensureConnected(); return signed14(link.analog(CH_COMPASS_Z)); }

    /** Magnetfeld-Heading in Grad (0..359). */
    public int getCompassHeading() {
        ensureConnected();
        double x = getCompassX();
        double y = getCompassY();
        double rad = Math.atan2(y, x);
        int deg = (int) Math.toDegrees(rad);
        return (deg + 360) % 360;
    }

    public int getTemperature() {
        ensureConnected();
        return link.analog(CH_TEMP); // °C
    }

    /** Licht-Sensor 0..255. Aktiviert sich automatisch (mbFirmata Pseudo-Pin 11 = ANALOG_INPUT). */
    public int getLightLevel() {
        ensureConnected();
        if (!lightSensorActive) {
            link.setPinMode(11, FirmataLink.MODE_ANALOG_INPUT);
            link.streamAnalog(CH_LIGHT, true);
            lightSensorActive = true;
        }
        return link.analog(CH_LIGHT);
    }
    private boolean lightSensorActive = false;

    /** Heuristik: liefert true einmalig, wenn die Beschleunigung |a| signifikant über 1g springt. */
    public boolean wasShaken() {
        ensureConnected();
        int ax = getAccelerationX();
        int ay = getAccelerationY();
        int az = getAccelerationZ();
        long magSq = (long) ax * ax + (long) ay * ay + (long) az * az;
        boolean shaken = magSq > 3_500_000L; // ca. >1.9g
        if (shaken && !shakeLatched) { shakeLatched = true; return true; }
        if (!shaken) shakeLatched = false;
        return false;
    }
    private boolean shakeLatched = false;

    // ---- Ringpads P0..P3 ---------------------------------------------------

    public void setPadAnalogIn(int pad) {
        ensureConnected();
        if (pad < 0 || pad > 2) return; // nur P0,P1,P2 sind analog-fähig
        link.setPinMode(pad, FirmataLink.MODE_ANALOG_INPUT);
        link.streamAnalog(pad, true);
    }

    public int readPadAnalog(int pad) {
        ensureConnected();
        if (pad < 0 || pad > 6) return 0;
        return link.analog(pad);
    }

    public void setPadDigitalIn(int pad, boolean pullUp) {
        ensureConnected();
        link.setPinMode(pad, pullUp ? FirmataLink.MODE_INPUT_PULLUP : FirmataLink.MODE_DIGITAL_INPUT);
    }

    public boolean readPadDigital(int pad) {
        ensureConnected();
        return link.digital(pad);
    }

    public void setPadDigitalOut(int pad) {
        ensureConnected();
        link.setPinMode(pad, FirmataLink.MODE_DIGITAL_OUTPUT);
    }

    public void writePadDigital(int pad, boolean state) {
        ensureConnected();
        link.writeDigital(pad, state);
    }

    public void setPadPwm(int pad) {
        ensureConnected();
        link.setPinMode(pad, FirmataLink.MODE_PWM);
    }

    public void writePadPwm(int pad, int value) {
        ensureConnected();
        link.writeAnalog(pad, value);
    }

    // ---- Interner Lautsprecher des Calliope mini V3 ------------------------
    // mbFirmata erzeugt eine Rechteckschwingung auf io.speaker (P0_00) und
    // stoppt sie selbständig nach der angegebenen Dauer. Bei dauer = 0
    // läuft der Ton bis zum nächsten Stop-Aufruf weiter.

    private static final int DEFAULT_BEEP_FREQ = 1000; // Hz

    /** Ton mit Frequenz (Hz) und Dauer (ms, 0 = bis stopBeep()). */
    public void playTone(int frequency, int durationMs) {
        ensureConnected();
        int f = Math.max(0, Math.min(16383, frequency));
        int d = Math.max(0, Math.min(16383, durationMs));
        link.sendSysEx(FirmataLink.MB_CALLIOPE_SOUND, new byte[]{
            (byte) (f & 0x7F), (byte) ((f >> 7) & 0x7F),
            (byte) (d & 0x7F), (byte) ((d >> 7) & 0x7F)
        });
    }

    /** Kurzer Piepton mit Default-Frequenz; blockiert bis der Ton ausgeklungen ist. */
    public void playBeep(int durationMs) {
        ensureConnected();
        if (durationMs < 0 || durationMs > 5000) {
            System.err.println("Beep-Dauer muss 0..5000 ms sein.");
            return;
        }
        playTone(DEFAULT_BEEP_FREQ, durationMs);
        pause(durationMs);
    }

    /** Ton sofort abschalten. */
    public void stopBeep() {
        ensureConnected();
        playTone(0, 0);
    }

    // ---- Timing ------------------------------------------------------------

    public void pause(int ms) {
        try { Thread.sleep(Math.max(0, ms)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public void startClock() {
        ensureConnected();
        startZeit = System.currentTimeMillis();
    }

    public long getClock() {
        ensureConnected();
        if (startZeit == -1L) { System.out.println("Stoppuhr nicht gestartet."); return 0L; }
        return System.currentTimeMillis() - startZeit;
    }

    public long stopClock() {
        ensureConnected();
        long e = getClock();
        startZeit = -1L;
        return e;
    }

    // ---- Loop-System (1:1 wie PicoIO) --------------------------------------

    public int startLoop(Object o, String methodName) {
        ensureConnected();
        return startLoopWithDelay(o, methodName, 0);
    }

    public int startLoopWithDelay(Object o, String methodName, int delay) {
        ensureConnected();
        String key = o.getClass().getName() + "#" + methodName;
        if (activeLoopIdentifiers.containsKey(key)) {
            System.err.println("Loop bereits aktiv: " + key);
            return -1;
        }
        int id = loopCounter++;
        Thread t = new Thread(() -> {
            try {
                Method m = o.getClass().getDeclaredMethod(methodName);
                m.setAccessible(true);
                while (!Thread.currentThread().isInterrupted()) {
                    m.invoke(o);
                    Thread.sleep(delay);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                if (e.getCause() instanceof InterruptedException) Thread.currentThread().interrupt();
                else System.err.println("Fehler in Loop " + id + ": " + e.getMessage());
            }
        });
        loops.put(id, t);
        activeLoopIdentifiers.put(key, id);
        t.setDaemon(true);
        t.start();
        return id;
    }

    public void stopLoop(int id) {
        ensureConnected();
        Thread t = loops.get(id);
        if (t != null) {
            t.interrupt();
            try { t.join(2000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            loops.remove(id);
            activeLoopIdentifiers.values().removeIf(v -> v == id);
        }
    }

    public void stopAllLoops() {
        if (loops.isEmpty()) return;
        Set<Integer> ids = new HashSet<>(loops.keySet());
        for (int id : ids) stopLoop(id);
        loops.clear();
        activeLoopIdentifiers.clear();
    }

    public void listLoops() {
        ensureConnected();
        System.out.println("Aktive Loops: " + loops.keySet());
    }

    // ---- Hilfsfunktion -----------------------------------------------------

    /** mbFirmata sendet 14-Bit-Werte; Beschleunigung/Kompass sind signed → Sign-Extension. */
    private static int signed14(int raw) {
        if ((raw & 0x2000) != 0) return raw | ~0x3FFF; // negativ
        return raw;
    }
}
