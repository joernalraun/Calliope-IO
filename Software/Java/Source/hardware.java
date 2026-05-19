/**
 * Static-Singleton-Hardwareklasse für CalliopeIO,
 * analog zur Pico-IO-Variante.
 *
 * Beispiel-Aufruf in BlueJ:
 *      hardware.ledOn(2, 2);
 *      hardware.scrollText("Hallo");
 *      hardware.setRgbAll(255, 0, 0);
 *
 * Die Verbindung zum Calliope mini wird automatisch beim ersten Zugriff aufgebaut.
 */
public final class hardware {

    private static CalliopeIO calliope = null;

    private static synchronized CalliopeIO get() {
        if (calliope == null) calliope = new CalliopeIO();
        return calliope;
    }

    // ---- LED-Matrix --------------------------------------------------------
    public static void ledOn(int x, int y)                     { get().ledOn(x, y); }
    public static void ledOff(int x, int y)                    { get().ledOff(x, y); }
    public static void ledSet(int x, int y, boolean state)     { get().ledSet(x, y, state); }
    public static void ledDim(int x, int y, int brightness)    { get().ledDim(x, y, brightness); }
    public static void clearDisplay()                          { get().clearDisplay(); }
    public static void showImage(int[][] grid)                 { get().showImage(grid); }
    public static void scrollText(String s)                    { get().scrollText(s); }
    public static void scrollText(String s, int delay)         { get().scrollText(s, delay); }
    public static void scrollNumber(int n)                     { get().scrollNumber(n); }
    public static void scrollNumber(int n, int delay)          { get().scrollNumber(n, delay); }
    public static void enableDisplay(boolean enable)           { get().enableDisplay(enable); }

    // ---- RGB-LEDs ----------------------------------------------------------
    public static void setRgb(int led, int r, int g, int b)    { get().setRgb(led, r, g, b); }
    public static void setRgbAll(int r, int g, int b)          { get().setRgbAll(r, g, b); }
    public static void clearRgb()                              { get().clearRgb(); }

    // ---- Motoren -----------------------------------------------------------
    public static void setMotor(int m, boolean fwd, int speed) { get().setMotor(m, fwd, speed); }
    public static void stopMotor(int m)                        { get().stopMotor(m); }
    public static void stopMotors()                            { get().stopMotors(); }

    // ---- Buttons -----------------------------------------------------------
    public static boolean isButtonAPressed()                   { return get().isButtonAPressed(); }
    public static boolean isButtonBPressed()                   { return get().isButtonBPressed(); }
    public static boolean wasButtonAPressed()                  { return get().wasButtonAPressed(); }
    public static boolean wasButtonBPressed()                  { return get().wasButtonBPressed(); }
    public static void clearPressed()                          { get().clearPressed(); }

    // ---- Sensoren ----------------------------------------------------------
    public static int getAccelerationX()                       { return get().getAccelerationX(); }
    public static int getAccelerationY()                       { return get().getAccelerationY(); }
    public static int getAccelerationZ()                       { return get().getAccelerationZ(); }
    public static int getCompassX()                            { return get().getCompassX(); }
    public static int getCompassY()                            { return get().getCompassY(); }
    public static int getCompassZ()                            { return get().getCompassZ(); }
    public static int getCompassHeading()                      { return get().getCompassHeading(); }
    public static int getTemperature()                         { return get().getTemperature(); }
    public static int getLightLevel()                          { return get().getLightLevel(); }
    public static boolean wasShaken()                          { return get().wasShaken(); }

    // ---- Ringpads ----------------------------------------------------------
    public static void setPadAnalogIn(int pad)                 { get().setPadAnalogIn(pad); }
    public static int  readPadAnalog(int pad)                  { return get().readPadAnalog(pad); }
    public static void setPadDigitalIn(int pad, boolean pull)  { get().setPadDigitalIn(pad, pull); }
    public static boolean readPadDigital(int pad)              { return get().readPadDigital(pad); }
    public static void setPadDigitalOut(int pad)               { get().setPadDigitalOut(pad); }
    public static void writePadDigital(int pad, boolean s)     { get().writePadDigital(pad, s); }
    public static void setPadPwm(int pad)                      { get().setPadPwm(pad); }
    public static void writePadPwm(int pad, int v)             { get().writePadPwm(pad, v); }

    // ---- Lautsprecher ------------------------------------------------------
    public static void playBeep(int duration)                  { get().playBeep(duration); }
    public static void playTone(int frequency, int duration)   { get().playTone(frequency, duration); }
    public static void stopBeep()                              { get().stopBeep(); }

    // ---- Loop --------------------------------------------------------------
    public static int loop(Object o, String m)                 { return get().startLoop(o, m); }
    public static int loopWithDelay(Object o, String m, int d) { return get().startLoopWithDelay(o, m, d); }
    public static void stopLoop(int id)                        { get().stopLoop(id); }
    public static void stopAllLoops()                          { get().stopAllLoops(); }
    public static void listLoops()                             { get().listLoops(); }

    // ---- Timing ------------------------------------------------------------
    public static void pause(int ms)                           { get().pause(ms); }
    public static void startClock()                            { get().startClock(); }
    public static long getClock()                              { return get().getClock(); }
    public static long stopClock()                             { return get().stopClock(); }

    // ---- Connection --------------------------------------------------------
    public static boolean getStatus()                          { return get().getStatus(); }
    public static void reconnect()                             { calliope = new CalliopeIO(); }
    public static void close()                                 { get().close(); }
}
