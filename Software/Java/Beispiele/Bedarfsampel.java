/**
 * Bedarfsampel auf dem Calliope mini V3.
 *
 * Hardware:
 *   - Straßenampel: linke RGB-LED (rot/gelb/grün)
 *   - Fußgängerampel: rechte RGB-LED (rot/grün)
 *   - Anforderungstaster: Button A
 */
public class Bedarfsampel {
    private final int S_LED = 0; // RGB-LED 0 = Straßenampel
    private final int F_LED = 2; // RGB-LED 2 = Fußgängerampel
    private int loopID;
    private CalliopeIO calliope;

    public Bedarfsampel() {
        calliope = new CalliopeIO();
        loopID = -1;
    }

    public void start() {
        if (loopID == -1) {
            calliope.open();
            calliope.clearPressed();
            sGruen();   // Straße: grün
            fRot();     // Fußgänger: rot
            loopID = calliope.startLoop(this, "loop");
        } else {
            System.out.println("Loop bereits gestartet");
        }
    }

    private void loop() {
        if (calliope.wasButtonAPressed()) {
            schaltePhase();
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

    private void schaltePhase() {
        calliope.pause(500);
        sGelb();                                // Straße: gelb
        calliope.pause(1000);
        sRot();                                 // Straße: rot
        calliope.pause(500);
        fGruen();                               // Fußgänger: grün
        calliope.pause(4000);
        fRot();                                 // Fußgänger: rot
        calliope.clearPressed();
        calliope.pause(500);
        sGelb();                                // Straße: rot+gelb (nur gelb in RGB-Darstellung)
        calliope.pause(500);
        sGruen();                               // Straße: grün
        calliope.pause(2000);
    }

    private void sRot()   { calliope.setRgb(S_LED, 255,   0,   0); }
    private void sGelb()  { calliope.setRgb(S_LED, 255, 200,   0); }
    private void sGruen() { calliope.setRgb(S_LED,   0, 255,   0); }
    private void fRot()   { calliope.setRgb(F_LED, 255,   0,   0); }
    private void fGruen() { calliope.setRgb(F_LED,   0, 255,   0); }
}
