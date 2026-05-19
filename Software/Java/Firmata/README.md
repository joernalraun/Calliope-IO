# mbFirmata-Firmware für den Calliope mini V3

Calliope-IO benötigt eine Firmata-Firmware auf dem Calliope mini.

## Schnellster Weg: fertige Firmware flashen

In diesem Ordner liegt eine **vorkompilierte** Firmware:

### Flashen

1. Calliope mini per USB anschließen — er erscheint als USB-Laufwerk
   `MINI` (oder ähnlich).
2. `CalliopeMiniFirmata.hex` per Drag-and-drop auf das Laufwerk kopieren.
3. Nach kurzem Reset blinkt die Onboard-Anzeige; der Calliope mini ist bereit.

### Verifizieren

Nach erfolgreichem Flashen sollte das Java-Testprogramm
[`../Source/CalliopeIOTest.java`](../Source/CalliopeIOTest.java) ohne
Fehlermeldungen durchlaufen.

## Wichtige Hinweise

- **USB-IDs:** Calliope-IO erwartet standardmäßig VID `0x0D28` / PID
  `0x0204` (ARM mbed DAPLink).
  Sollte euer Calliope mini andere IDs nutzen, kann das im Java-Code per
  `calliope.addUsbId(vid, pid)` ergänzt werden.

- **Lautsprecher:** Der interne Calliope-mini-Lautsprecher wird über
  Firmata via SysEx `MB_CALLIOPE_SOUND` angesteuert (Frequenz + Dauer).
  Im Java-Client stehen `playTone(freq, dur)`, `playBeep(dur)` und
  `stopBeep()` direkt zur Verfügung — keine zusätzliche Hardware nötig.

- **Touch auf P3:** Das Firmata-Update für den Calliope mini hat P3 in
  den Touch-Pin-Set aufgenommen. Über `MB_SET_TOUCH_MODE` mit `pin=3`
  lässt sich der Ringpad als Touch-Sensor nutzen.

- **Größe der `.hex`:** Die Datei ist ~533 KB, weil sie die volle CODAL-
  Library und den Nordic-Softdevice für BLE-Support enthält. Auch wenn
  BLE in dieser Firmata-Variante deaktiviert ist,
  bleibt das Softdevice im Image, weil `DEVICE_BLE=1` für die Calliope-
  CODAL-Init nötig ist.
