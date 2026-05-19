# Java-Bibliotheken für Calliope-IO

Calliope-IO benötigt zwei externe Java-Bibliotheken:

| Bibliothek | Zweck | Bezugsquelle |
|---|---|---|
| **jSerialComm** | Auffinden des Calliope-USB-Ports anhand Hersteller-/Produkt-ID | https://fazecast.github.io/jSerialComm/ |
| **jSSC** | Eigentliche serielle Kommunikation (Read/Write + Event-Listener) | https://github.com/scream3r/java-simple-serial-connector |


- `jSerialComm-2.11.0.jar`
- `jssc-2.9.6.jar`

Calliope-IO bringt mit `FirmataLink.java` einen schlanken Firmata-
Client mit, der direkt das Firmata-Protokoll spricht (inkl.
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
