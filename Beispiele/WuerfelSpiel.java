/**
 * Würfelspiel auf dem Calliope mini V3.
 *
 * Schütteln (oder Button A) wirft den Würfel — das Ergebnis (1..6) wird
 * als Augenmuster auf der LED-Matrix gezeigt.
 *
 * Schummelmodus: liegt ein Magnet am Calliope mini, fällt der Würfel immer auf 6.
 * Zu hell? Glücksspiele sind dann verboten (Display bleibt aus).
 */
public class WuerfelSpiel {
    private final int LICHT_MAX = 200;       // ab diesem Wert ist es "zu hell"
    private final int MAGNET_DIFF = 200;     // Magnet erkannt, wenn |B| > Ref+Diff
    private int magnetReferenz = 0;
    private int loopID = -1;
    private java.util.Random rng = new java.util.Random();
    private CalliopeIO calliope = new CalliopeIO();

    public void start() {
        calliope.open();
        magnetReferenz = magnitude();
        loopID = calliope.startLoopWithDelay(this, "loop", 100);
    }

    private void loop() {
        if (calliope.wasButtonAPressed() || calliope.wasShaken()) {
            if (calliope.getLightLevel() > LICHT_MAX) {
                calliope.scrollText("Zu hell!");
                return;
            }
            int ergebnis = istMagnetAn() ? 6 : (rng.nextInt(6) + 1);
            for (int i = 0; i < 6; i++) { // kurze "Animation"
                zeigeAugen(rng.nextInt(6) + 1);
                calliope.pause(80);
            }
            zeigeAugen(ergebnis);
            calliope.playBeep(120);
        }
    }

    private boolean istMagnetAn() {
        return Math.abs(magnitude() - magnetReferenz) > MAGNET_DIFF;
    }

    private int magnitude() {
        int x = calliope.getCompassX();
        int y = calliope.getCompassY();
        int z = calliope.getCompassZ();
        return (int) Math.sqrt((long) x * x + (long) y * y + (long) z * z);
    }

    private void zeigeAugen(int n) {
        int[][] g = new int[5][5];
        if (n >= 1) g[2][2] = 255;
        if (n >= 2) { g[0][0] = 255; g[4][4] = 255; }
        if (n >= 3) { g[4][0] = 255; }
        if (n >= 4) { g[0][4] = 255; }
        if (n >= 5) { /* schon gesetzt */ }
        if (n == 6) { g[2][0] = 255; g[2][4] = 255; g[2][2] = 0; }
        calliope.showImage(g);
    }

    public void stopp() {
        if (loopID != -1) {
            calliope.stopLoop(loopID);
            loopID = -1;
            calliope.clearDisplay();
            calliope.close();
        }
    }
}
