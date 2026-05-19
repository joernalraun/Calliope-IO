/**
 * Fensteralarm auf dem Calliope mini V3.
 *
 * Der Calliope mini wird ans Fenster gehängt; ein kleiner Magnet sitzt am Rahmen.
 * Bewegt sich das Fenster, verändert sich das Magnetfeld am Kompass-Sensor.
 * Übersteigt die Änderung einen Schwellwert, gibt der Buzzer (externer Buzzer
 * an Ringpad P0) Alarm und die LED-Matrix zeigt ein Ausrufezeichen.
 */
public class Fensteralarm {
    private final int SCHWELLE = 200; // µT-Differenz, ab der ausgelöst wird
    private int referenz = 0;
    private int loopID = -1;
    private CalliopeIO calliope = new CalliopeIO();

    public void start() {
        calliope.open();
        System.out.println("Kalibriere Magnetfeld (Fenster geschlossen halten)...");
        calliope.pause(500);
        referenz = magnitude();
        System.out.println("Referenz: " + referenz);
        loopID = calliope.startLoopWithDelay(this, "loop", 100);
    }

    private void loop() {
        int aktuell = magnitude();
        if (Math.abs(aktuell - referenz) > SCHWELLE) {
            alarm();
        }
    }

    private int magnitude() {
        int x = calliope.getCompassX();
        int y = calliope.getCompassY();
        int z = calliope.getCompassZ();
        return (int) Math.sqrt((long) x * x + (long) y * y + (long) z * z);
    }

    private void alarm() {
        zeigeAusrufezeichen();
        calliope.setRgbAll(255, 0, 0);
        calliope.playBeep(300);
        calliope.pause(150);
        calliope.playBeep(300);
        calliope.clearDisplay();
        calliope.clearRgb();
    }

    private void zeigeAusrufezeichen() {
        int[][] grid = {
            {0, 0, 255, 0, 0},
            {0, 0, 255, 0, 0},
            {0, 0, 255, 0, 0},
            {0, 0,   0, 0, 0},
            {0, 0, 255, 0, 0}
        };
        calliope.showImage(grid);
    }

    public void stopp() {
        if (loopID != -1) {
            calliope.stopLoop(loopID);
            loopID = -1;
            calliope.clearDisplay();
            calliope.clearRgb();
            calliope.close();
        }
    }
}
