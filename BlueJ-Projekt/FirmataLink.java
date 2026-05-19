import jssc.SerialPort;
import jssc.SerialPortEvent;
import jssc.SerialPortEventListener;
import jssc.SerialPortException;

import java.util.Arrays;

/**
 * Schlanker Firmata-Client für den Calliope mini V3.
 *
 * Spricht das mbFirmata-Protokoll (microbit-firmata-master, an Calliope V3
 * angepasste Variante): Standard-Firmata 2.6 + micro:bit/Calliope-SysEx
 * (Display, Touch, RGB-LED, Motoren).
 *
 * Diese Klasse wird intern von CalliopeIO verwendet und ist normalerweise
 * nicht direkt vom Anwender anzufassen.
 */
public class FirmataLink implements SerialPortEventListener {

    // ---- Firmata-Konstanten ------------------------------------------------

    public static final int STREAM_ANALOG          = 0xC0;
    public static final int STREAM_DIGITAL         = 0xD0;
    public static final int ANALOG_UPDATE          = 0xE0;
    public static final int DIGITAL_UPDATE         = 0x90;
    public static final int SET_PIN_MODE           = 0xF4;
    public static final int SET_DIGITAL_PIN        = 0xF5;
    public static final int FIRMATA_VERSION        = 0xF9;
    public static final int SYSTEM_RESET           = 0xFF;
    public static final int SYSEX_START            = 0xF0;
    public static final int SYSEX_END              = 0xF7;
    public static final int REPORT_FIRMWARE        = 0x79;
    public static final int SAMPLING_INTERVAL      = 0x7A;
    public static final int EXTENDED_ANALOG_WRITE  = 0x6F;

    // mbFirmata-Erweiterungen
    public static final int MB_DISPLAY_CLEAR       = 0x01;
    public static final int MB_DISPLAY_SHOW        = 0x02;
    public static final int MB_DISPLAY_PLOT        = 0x03;
    public static final int MB_SCROLL_STRING       = 0x04;
    public static final int MB_SCROLL_INTEGER      = 0x05;
    public static final int MB_SET_TOUCH_MODE      = 0x06;
    public static final int MB_DISPLAY_ENABLE      = 0x07;
    public static final int MB_COMPASS_CALIBRATE   = 0x08;
    public static final int MB_CALLIOPE_MOTOR      = 0x09;
    public static final int MB_CALLIOPE_RGB        = 0x0A;
    public static final int MB_CALLIOPE_SOUND      = 0x0B;
    public static final int MB_REPORT_EVENT        = 0x0D;
    public static final int MB_DEBUG_STRING        = 0x0E;

    // Pin-Modes
    public static final int MODE_DIGITAL_INPUT     = 0x00;
    public static final int MODE_DIGITAL_OUTPUT    = 0x01;
    public static final int MODE_ANALOG_INPUT      = 0x02;
    public static final int MODE_PWM               = 0x03;
    public static final int MODE_INPUT_PULLUP      = 0x0B;

    public static final int PIN_COUNT              = 21;
    public static final int CHANNEL_COUNT          = 16;

    // ---- Zustand -----------------------------------------------------------

    private SerialPort port;
    private final int[] analogValues  = new int[CHANNEL_COUNT];
    private final boolean[] digitalIn = new boolean[PIN_COUNT];
    private final boolean[] digitalPrev = new boolean[PIN_COUNT];
    private boolean ready = false;
    private int firmwareMajor = 0;
    private int firmwareMinor = 0;

    // ---- Parser-Zustand ----------------------------------------------------

    private int  parseState = 0;       // 0 = idle, 1 = sysex, sonst = arg-count remaining
    private int  cmdByte    = 0;
    private final int[] argBuf = new int[8];
    private int  argIdx     = 0;
    private final byte[] sysexBuf = new byte[256];
    private int  sysexIdx   = 0;

    // ---- Verbindung --------------------------------------------------------

    public synchronized boolean open(String portName) {
        if (port != null) return false;
        port = new SerialPort(portName);
        try {
            port.openPort();
            port.setParams(57600, 8, 1, 0);
            port.addEventListener(this, SerialPort.MASK_RXCHAR);
        } catch (SerialPortException e) {
            System.err.println("FirmataLink: Port " + portName + " kann nicht geöffnet werden: " + e.getMessage());
            port = null;
            return false;
        }
        // Standard-Firmata fordert: zuerst REPORT_VERSION abwarten.
        long deadline = System.currentTimeMillis() + 3000;
        sendBytes(new byte[]{(byte) FIRMATA_VERSION});
        while (!ready && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (!ready) {
            System.err.println("FirmataLink: Kein FIRMATA_VERSION-Reply von " + portName);
            close();
            return false;
        }
        return true;
    }

    public synchronized void close() {
        if (port == null) return;
        try {
            port.removeEventListener();
            port.closePort();
        } catch (SerialPortException ignored) {}
        port = null;
        ready = false;
        Arrays.fill(analogValues, 0);
        Arrays.fill(digitalIn, false);
        Arrays.fill(digitalPrev, false);
    }

    public boolean isReady() { return ready; }

    public int getFirmwareMajor() { return firmwareMajor; }
    public int getFirmwareMinor() { return firmwareMinor; }

    // ---- Standard-Firmata-Schreibseite -------------------------------------

    public void setPinMode(int pin, int mode) {
        sendBytes(new byte[]{(byte) SET_PIN_MODE, (byte) pin, (byte) mode});
    }

    public void writeDigital(int pin, boolean state) {
        sendBytes(new byte[]{(byte) SET_DIGITAL_PIN, (byte) pin, (byte) (state ? 1 : 0)});
    }

    public void writeAnalog(int pin, int value) {
        // Bei Pins >15 oder Werten > 16383: EXTENDED_ANALOG nehmen.
        int v = Math.max(0, Math.min(16383, value));
        if (pin <= 0x0F && v <= 0x3FFF) {
            sendBytes(new byte[]{(byte) (ANALOG_UPDATE | pin), (byte) (v & 0x7F), (byte) ((v >> 7) & 0x7F)});
        } else {
            sendBytes(new byte[]{
                (byte) SYSEX_START, (byte) EXTENDED_ANALOG_WRITE,
                (byte) pin,
                (byte) (v & 0x7F), (byte) ((v >> 7) & 0x7F),
                (byte) SYSEX_END
            });
        }
    }

    public void streamAnalog(int channel, boolean on) {
        sendBytes(new byte[]{(byte) (STREAM_ANALOG | (channel & 0x0F)), (byte) (on ? 1 : 0)});
    }

    public void streamDigital(int portNum, boolean on) {
        sendBytes(new byte[]{(byte) (STREAM_DIGITAL | (portNum & 0x0F)), (byte) (on ? 1 : 0)});
    }

    public void setSamplingInterval(int ms) {
        sendBytes(new byte[]{
            (byte) SYSEX_START, (byte) SAMPLING_INTERVAL,
            (byte) (ms & 0x7F), (byte) ((ms >> 7) & 0x7F),
            (byte) SYSEX_END
        });
    }

    public void sendSysEx(int command, byte[] payload) {
        byte[] msg = new byte[3 + (payload == null ? 0 : payload.length)];
        msg[0] = (byte) SYSEX_START;
        msg[1] = (byte) command;
        if (payload != null) System.arraycopy(payload, 0, msg, 2, payload.length);
        msg[msg.length - 1] = (byte) SYSEX_END;
        sendBytes(msg);
    }

    private void sendBytes(byte[] data) {
        if (port == null) return;
        try {
            port.writeBytes(data);
        } catch (SerialPortException e) {
            System.err.println("FirmataLink: Schreibfehler: " + e.getMessage());
        }
    }

    // ---- Standard-Firmata-Leseseite ----------------------------------------

    public int analog(int channel) {
        if (channel < 0 || channel >= CHANNEL_COUNT) return 0;
        return analogValues[channel];
    }

    public boolean digital(int pin) {
        if (pin < 0 || pin >= PIN_COUNT) return false;
        return digitalIn[pin];
    }

    /** Liefert true einmalig, wenn pin seit dem letzten Aufruf high→low oder low→high gewechselt hat. */
    public boolean digitalRose(int pin) {
        if (pin < 0 || pin >= PIN_COUNT) return false;
        boolean cur = digitalIn[pin];
        boolean prev = digitalPrev[pin];
        digitalPrev[pin] = cur;
        return cur && !prev;
    }

    // ---- jSSC-Listener: eingehende Bytes parsen ----------------------------

    @Override
    public void serialEvent(SerialPortEvent ev) {
        if (!ev.isRXCHAR() || ev.getEventValue() <= 0) return;
        try {
            byte[] buf = port.readBytes(ev.getEventValue());
            if (buf == null) return;
            for (byte b : buf) parseByte(b & 0xFF);
        } catch (SerialPortException e) {
            System.err.println("FirmataLink: Lesefehler: " + e.getMessage());
        }
    }

    private void parseByte(int b) {
        if (parseState == 1) { // innerhalb SysEx
            if (b == SYSEX_END) {
                dispatchSysEx();
                parseState = 0;
                sysexIdx = 0;
            } else if (sysexIdx < sysexBuf.length) {
                sysexBuf[sysexIdx++] = (byte) b;
            }
            return;
        }
        if ((b & 0x80) != 0) { // Status-Byte
            if (b == SYSEX_START) {
                parseState = 1;
                sysexIdx = 0;
                return;
            }
            cmdByte = b;
            argIdx  = 0;
            int high = b & 0xF0;
            if (high == DIGITAL_UPDATE || high == ANALOG_UPDATE) {
                parseState = 2;
            } else if (b == FIRMATA_VERSION) {
                parseState = 2;
            } else {
                parseState = 0;
            }
            return;
        }
        // Datenbyte
        if (parseState > 0) {
            argBuf[argIdx++] = b;
            if (argIdx >= parseState) {
                dispatchChannelMessage();
                parseState = 0;
                argIdx = 0;
            }
        }
    }

    private void dispatchChannelMessage() {
        int high = cmdByte & 0xF0;
        int chan = cmdByte & 0x0F;
        if (high == ANALOG_UPDATE) {
            int val = argBuf[0] | (argBuf[1] << 7);
            if (chan < CHANNEL_COUNT) analogValues[chan] = val;
        } else if (high == DIGITAL_UPDATE) {
            int mask = argBuf[0] | (argBuf[1] << 7);
            int base = 8 * chan;
            for (int i = 0; i < 8; i++) {
                int pin = base + i;
                if (pin < PIN_COUNT) digitalIn[pin] = ((mask >> i) & 1) != 0;
            }
        } else if (cmdByte == FIRMATA_VERSION) {
            firmwareMajor = argBuf[0];
            firmwareMinor = argBuf[1];
            ready = true;
        }
    }

    private void dispatchSysEx() {
        if (sysexIdx == 0) return;
        int cmd = sysexBuf[0] & 0xFF;
        // Wir interessieren uns hier nur für REPORT_FIRMWARE als Bereitschaftssignal.
        if (cmd == REPORT_FIRMWARE) ready = true;
        // MB_REPORT_EVENT, MB_DEBUG_STRING etc. werden hier ignoriert (für jetzt).
    }
}
