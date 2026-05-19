/**
 * Dämmerungsschalter auf dem Calliope mini V3.
 *
 * Wenn der Lichtsensor (integriert in der LED-Matrix) unter einen Schwellwert
 * fällt, leuchtet die mittlere RGB-LED warm-weiß als "Straßenbeleuchtung".
 */
public class Daemmerung {
    private final int LAMPE = 1;        // mittlere RGB-LED
    private final int SCHWELLE = 80;    // Helligkeit, ab der es dunkel ist (0..255)
    private int loopID = -1;
    private CalliopeIO calliope = new CalliopeIO();

    public void start() {
        if (loopID == -1) {
            calliope.open();
            loopID = calliope.startLoopWithDelay(this, "loop", 200);
        }
    }

    private void loop() {
        int licht = calliope.getLightLevel();
        if (licht < SCHWELLE) {
            // gedimmtes warmes Licht — je dunkler, desto heller die Lampe
            int helligkeit = Math.min(255, (SCHWELLE - licht) * 4);
            calliope.setRgb(LAMPE, helligkeit, helligkeit * 8 / 10, helligkeit / 3);
        } else {
            calliope.setRgb(LAMPE, 0, 0, 0);
        }
    }

    public void stopp() {
        if (loopID != -1) {
            calliope.stopLoop(loopID);
            loopID = -1;
            calliope.clearRgb();
            calliope.close();
        }
    }
}
