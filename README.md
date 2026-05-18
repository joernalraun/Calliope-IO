# Calliope-IO — Java-Programmierung für den Calliope mini V3

Dieses Projekt ist das Calliope-mini-V3-Pendant zu **Pico-IO** (https://github.com/ToniTaste/Pico-IO/): Es ermöglicht,
den Calliope mini von einem PC aus mit **Java** zu programmieren — im
gleichen didaktischen Stil wie Pico-IO (`CalliopeIO`-Klasse + `hardware`-
Static-Wrapper, Loop-System, BlueJ-kompatibel).

Anders als bei Pico-IO ist die Hardware-API **Calliope-mini-nativ**: statt
externer LEDs/Hallsensor/Buzzer auf einer eigenen Platine wird die
**eingebaute** Calliope-mini-Hardware angesprochen — LED-Matrix, RGB-LEDs,
Beschleunigungssensor, Kompass, Temperatursensor, Lichtsensor, Mikrofon-
nahe Erkennung, Buttons A/B, Ringpads P0–P3, zwei Motoren.

## Voraussetzungen

1. **Calliope mini V3**
2. **Firmata-Firmware** (siehe `Software/Java/Firmata/README.md`) auf den
   Calliope mini geflasht
3. **Java 17+**, BlueJ (oder eine andere Java-IDE)
4. **Bibliotheken**: `jSerialComm` und `jSSC`
   (siehe `Software/Java/Bibliotheken/README.md`)

## Schnellstart

```java
public class HalloCalliopeMini {
    public static void main(String[] args) {
        CalliopeIO c = new CalliopeIO();
        c.open();
        c.scrollText("Hallo Welt!");
        c.pause(2500);
        c.setRgbAll(255, 0, 100);
        c.pause(1000);
        c.close();
    }
}
```

Oder per Static-Wrapper:

```java
hardware.scrollText("Hallo Welt!");
hardware.setRgbAll(0, 255, 0);
```

## Funktionsübersicht

### LED-Matrix (5 × 5)
- `ledOn(x, y)`, `ledOff(x, y)`, `ledSet(x, y, state)`, `ledDim(x, y, 0..255)`
- `showImage(int[5][5])`, `clearDisplay()`
- `scrollText("...")`, `scrollNumber(42)`
- `enableDisplay(false)` — gibt die Display-Pins für freie GPIO-Nutzung frei

### RGB-LEDs (3 onboard)
- `setRgb(led, r, g, b)`, `setRgbAll(r, g, b)`, `clearRgb()`

### Motoren (M_A, M_B)
- `setMotor(motor, forward, speed)` — motor ∈ {0, 1, 2}, speed 0..127
- `stopMotor(motor)`, `stopMotors()`

### Buttons
- `isButtonAPressed()`, `isButtonBPressed()`
- `wasButtonAPressed()`, `wasButtonBPressed()`, `clearPressed()`

### Sensoren
- `getAccelerationX/Y/Z()`, `wasShaken()`
- `getCompassX/Y/Z()`, `getCompassHeading()`
- `getTemperature()` (°C), `getLightLevel()` (0..255)

### Ringpads P0–P3
- `setPadAnalogIn(pad)` / `readPadAnalog(pad)`
- `setPadDigitalIn(pad, pullUp)` / `readPadDigital(pad)`
- `setPadDigitalOut(pad)` / `writePadDigital(pad, state)`
- `setPadPwm(pad)` / `writePadPwm(pad, 0..1023)`

### Interner Lautsprecher (Calliope mini)
- `playTone(frequencyHz, durationMs)` — Ton mit beliebiger Frequenz
- `playBeep(durationMs)` — kurzer Standard-Piepton (1000 Hz), blockierend
- `stopBeep()` — Ton sofort abschalten

Intern wird der SysEx `MB_CALLIOPE_SOUND` genutzt; der eingebaute Speaker
des Calliope mini erzeugt eine Rechteckschwingung. `durationMs = 0`
bedeutet „Ton läuft, bis `stopBeep()` aufgerufen wird".

### Loop-System (Pico-IO-Stil)
- `startLoop(this, "tick")`, `startLoopWithDelay(this, "tick", 100)`
- `stopLoop(id)`, `stopAllLoops()`, `listLoops()`

### Timing
- `pause(ms)`, `startClock()`, `getClock()`, `stopClock()`

### Verbindung
- `open()`, `close()`, `getStatus()`, `hardReset()`

## Beispiele

Vier didaktische Anwendungen analog zu Pico-IO im Ordner `Software/Java/Beispiele/`:

| Datei | Was es macht |
|---|---|
| `Bedarfsampel.java` | Zwei RGB-LEDs als Auto- und Fußgängerampel, Button A als Anforderungstaster |
| `Daemmerung.java`   | Lichtsensor steuert die mittlere RGB-LED als Straßenlaterne |
| `Fensteralarm.java` | Magnetometer erkennt Fensterbewegung → Display + Buzzer alarmieren |
| `WuerfelSpiel.java` | Schütteln oder Button A würfelt; Schummelmodus per Magnet; Display-Sperre bei zu viel Licht |

## Lizenz

CC BY-SA 4.0 — analog zur Pico-IO-Lizenz.
