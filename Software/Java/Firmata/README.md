# Firmata-Firmware für den Calliope mini V3

Calliope-IO benötigt eine Firmata-Firmware auf dem Calliope mini.

## Schnellster Weg: fertige Firmware flashen

In diesem Ordner liegt eine **vorkompilierte** Firmware:

- **[`CalliopeMiniFirmata.hex`](CalliopeMiniFirmata.hex)** (~533 KB)
  Gebaut gegen den Calliope-mini-CODAL-Fork und das Calliope-mini-Sample-Repo.

### Flashen

1. Calliope mini per USB anschließen — er erscheint als USB-Laufwerk
   `MINI` (oder ähnlich).
2. `CalliopeMiniFirmata.hex` per Drag-and-drop auf das Laufwerk kopieren.
3. Nach kurzem Reset blinkt die Onboard-Anzeige; der Calliope mini ist bereit.

### Verifizieren

Nach erfolgreichem Flashen sollte das Java-Testprogramm
[`../Source/CalliopeIOTest.java`](../Source/CalliopeIOTest.java) ohne
Fehlermeldungen durchlaufen.

## Selbst bauen

Falls die mitgelieferte `.hex` aktualisiert werden soll (z. B. nach
Änderungen an der mbFirmata-Quelle):

1. **Toolchain installieren.** Auf macOS:
   ```bash
   brew install --cask gcc-arm-embedded
   brew install cmake ninja python srecord
   ```
2. **Build-Wrapper bereitstellen.** Das Calliope-Sample-Repo ist
   [`[../../../../calliopemini-samples/](https://github.com/calliope-edu/calliopemini-samples/tree/calliope_nov24)`][(https://github.com/calliope-edu/microbit-v2-samples/tree/calliope_nov24];
   es lädt den Calliope-CODAL-Fork (Branch `v0.2.57-calliope-1.5`) beim
   ersten Build automatisch nach.
3. **Firmata-Source einschleusen.**
   ```bash
   SAMPLES=https://github.com/calliope-edu/calliopemini-samples/tree/calliope_nov24
   rm -rf "$SAMPLES/source"
   cp -R ../../../../calliopemini-firmata-master/firmware/source "$SAMPLES/source"
   ```
4. **`versions.h` ergänzen.** Am Ende von
   `$SAMPLES/source/versions.h` folgende Zeile einfügen
   (ersetzt das, was `buildv2.py` sonst injiziert):
   ```c
   #define CODAL_FIRMATA_VERSION_STRING "codal-microbit-v2-calliope=local-build"
   ```
5. **`codal.json` ersetzen** durch die schlanke Firmata-Variante mit
   `BOARD_CALLIOPE_MINI_V3=1`:
   ```json
   {
       "target": {
           "name": "codal-microbit-v2",
           "url": "https://github.com/calliope-edu/codal-calliopemini",
           "branch": "v0.2.57-calliope-1.5",
           "type": "git"
       },
       "config": {
           "DEVICE_DMESG": 0, "DMESG_SERIAL_DEBUG": 0, "CODAL_DEBUG": 0,
           "MICROBIT_BLE_ENABLED": 0, "MICROBIT_BLE_PAIRING_MODE": 0,
           "CONFIG_MICROBIT_ERASE_USER_DATA_ON_REFLASH": 1,
           "DEVICE_BLE": 1,
           "BOARD_CALLIOPE_MINI_V3": 1
       },
       "definitions": "-DBOARD_CALLIOPE_MINI_V3=1"
   }
   ```
6. **Bauen.**
   ```bash
   cd "$SAMPLES"
   python3 build.py
   ```
   Ergebnis: `MINI.hex` im Repo-Root. Diese in
   `Calliope-IO/Software/Java/Firmata/CalliopeMiniFirmata.hex` kopieren.

## Wichtige Hinweise

- **USB-IDs:** Calliope-IO erwartet standardmäßig VID `0x0D28` / PID
  `0x0204` (ARM mbed DAPLink — gleiche Werte wie beim micro:bit V2).
  Sollte euer Calliope mini andere IDs nutzen, kann das im Java-Code per
  `calliope.addUsbId(vid, pid)` ergänzt werden.

- **Lautsprecher:** Der interne Calliope-mini-Lautsprecher wird über
  mbFirmata via SysEx `MB_CALLIOPE_SOUND` angesteuert (Frequenz + Dauer).
  Im Java-Client stehen `playTone(freq, dur)`, `playBeep(dur)` und
  `stopBeep()` direkt zur Verfügung — keine zusätzliche Hardware nötig.

- **Touch auf P3:** Das mbFirmata-Update für den Calliope mini hat P3 in
  den Touch-Pin-Set aufgenommen. Über `MB_SET_TOUCH_MODE` mit `pin=3`
  lässt sich der Ringpad als Touch-Sensor nutzen.

- **Größe der `.hex`:** Die Datei ist ~533 KB, weil sie die volle CODAL-
  Library und den Nordic-Softdevice für BLE-Support enthält. Auch wenn
  BLE in dieser Firmata-Variante deaktiviert ist (`MICROBIT_BLE_ENABLED=0`),
  bleibt das Softdevice im Image, weil `DEVICE_BLE=1` für die Calliope-
  CODAL-Init nötig ist.
