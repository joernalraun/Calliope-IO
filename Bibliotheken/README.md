# Java-Bibliotheken für Calliope-IO

Calliope-IO benötigt zwei externe Java-Bibliotheken:

| Bibliothek | Zweck | Bezugsquelle |
|---|---|---|
| **jSerialComm** | Auffinden des Calliope-USB-Ports anhand Hersteller-/Produkt-ID | https://fazecast.github.io/jSerialComm/ |
| **jSSC** | Eigentliche serielle Kommunikation (Read/Write + Event-Listener) | https://github.com/scream3r/java-simple-serial-connector |

Beide Bibliotheken sind aus dem Pico-IO-Projekt **direkt übernehmbar** —
die identischen JARs befinden sich unter
[`../../../../Pico-IO-main/Software/Java/Bibliotheken/`](../../../../Pico-IO-main/Software/Java/Bibliotheken/):

- `jSerialComm-2.11.0.jar`
- `jssc-2.9.6.jar`

Die `firmata4j`-JAR aus dem Pico-IO-Ordner wird hier **nicht** benötigt —
Calliope-IO bringt mit `FirmataLink.java` einen eigenen, schlanken Firmata-
Client mit, der direkt das mbFirmata-Protokoll spricht (inkl.
Calliope-spezifischer SysEx wie RGB-LEDs und Motoren).

## Einbindung

### BlueJ
- Menü „Werkzeuge" → „Einstellungen" → „Bibliotheken" → „Benutzerbibliotheken"
  und die beiden `.jar`-Dateien hinzufügen.
- **Alternativ:** Im Projektordner einen Unterordner `+libs/` anlegen und
  die `.jar`-Dateien dort ablegen — BlueJ erkennt sie automatisch.

### JavaEditor
- „Fenster" → „Konfiguration" → „Classpath User", beide JAR-Dateien
  eintragen.

### Kommandozeile

```bash
javac -cp "jSerialComm-2.11.0.jar:jssc-2.9.6.jar:." *.java
java  -cp "jSerialComm-2.11.0.jar:jssc-2.9.6.jar:." CalliopeIOTest
```

Unter Windows die Trennzeichen `:` durch `;` ersetzen.
