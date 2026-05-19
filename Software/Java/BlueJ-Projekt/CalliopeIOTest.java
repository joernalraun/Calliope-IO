public class CalliopeIOTest {

    public static void main(String[] args) {
        CalliopeIO c = new CalliopeIO();

        System.out.println("\n==== CalliopeIO Testprogramm ====\n");

        if (!c.getStatus()) c.open();
        if (!c.getStatus()) {
            System.out.println("Calliope mini nicht verbunden. Test abgebrochen.");
            return;
        }

        // ---- LED-Matrix-Sweep ----
        System.out.println("LED-Matrix: laufendes Licht (Zeile-für-Zeile)");
        for (int y = 0; y < 5; y++) {
            for (int x = 0; x < 5; x++) {
                c.ledOn(x, y);
                c.pause(40);
            }
        }
        c.pause(300);
        c.clearDisplay();

        // ---- Lauftext ----
        System.out.println("Lauftext: \"Hallo!\"");
        c.scrollText("Hallo!");
        c.pause(2500);

        // ---- RGB-LEDs ----
        System.out.println("RGB-LEDs: rot / grün / blau");
        c.setRgbAll(255, 0, 0); c.pause(500);
        c.setRgbAll(0, 255, 0); c.pause(500);
        c.setRgbAll(0, 0, 255); c.pause(500);
        c.clearRgb();

        // ---- Sensoren ----
        System.out.println("Sensorwerte (Momentaufnahme):");
        c.pause(200); // kurz warten, damit erste Werte da sind
        System.out.println("  Beschleunigung: x=" + c.getAccelerationX() +
                           "  y=" + c.getAccelerationY() +
                           "  z=" + c.getAccelerationZ());
        System.out.println("  Kompass:        x=" + c.getCompassX() +
                           "  y=" + c.getCompassY() +
                           "  z=" + c.getCompassZ() +
                           "  Heading=" + c.getCompassHeading() + "°");
        System.out.println("  Temperatur:     " + c.getTemperature() + " °C");

        // ---- Lautsprecher (interner Calliope-mini-Speaker) ----
        System.out.println("Lautsprecher: drei Töne (C5, E5, G5)");
        c.playTone(523, 300); c.pause(350);
        c.playTone(659, 300); c.pause(350);
        c.playTone(784, 300); c.pause(350);
        c.stopBeep();

        // ---- Buttons ----
        System.out.println("Drücken Sie 3 Sekunden lang Button A oder B...");
        c.clearPressed();
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < 3000) {
            if (c.wasButtonAPressed()) System.out.println("  → Button A wurde gedrückt");
            if (c.wasButtonBPressed()) System.out.println("  → Button B wurde gedrückt");
            c.pause(50);
        }

        // ---- Motor (sehr kurzer Spin, falls Antrieb angeschlossen) ----
        System.out.println("Motor M_A: 500 ms vorwärts mit halber Kraft (falls Antrieb angeschlossen)");
        c.setMotor(0, true, 64);
        c.pause(500);
        c.stopMotors();

        System.out.println("\n==== Test abgeschlossen. ====");
        c.close();
    }
}
