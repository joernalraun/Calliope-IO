# Python-Bibliotheken für Calliope-IO

Calliope-IO benötigt unter Python eine einzige externe Bibliothek:

| Bibliothek | Zweck |
|---|---|
| **pyserial** | Auffinden des Calliope-mini-USB-Ports (VID/PID) + serielle Kommunikation |

## Installation

```bash
python3 -m pip install pyserial
```

Damit ist sowohl `serial` (für `serial.Serial(...)`) als auch
`serial.tools.list_ports` (für die VID/PID-Suche) verfügbar.

In einer virtuellen Umgebung (empfohlen für Schul-Setups):

```bash
python3 -m venv .venv
source .venv/bin/activate   # macOS/Linux
# .venv\Scripts\activate    # Windows
python -m pip install pyserial
```

## Versionscheck

```bash
python3 -c "import serial, serial.tools.list_ports; print(serial.__version__)"
```

Getestet mit pyserial 3.5; jede Version ≥ 3.4 sollte funktionieren.

## Hinweise zu Plattformen

- **macOS**: USB-Geräte erscheinen als `/dev/cu.usbmodemXXX`. Beim ersten
  Zugriff fragt das System nach Berechtigung — erlauben.
- **Linux**: Benutzer muss in der Gruppe `dialout` (Debian/Ubuntu) bzw.
  `uucp` (Arch) sein, um auf `/dev/ttyACM*` zugreifen zu können.
- **Windows**: Geräte erscheinen als `COMx`. Falls kein Treiber installiert
  ist, hilft mbed Serial Port Driver oder Zadig.
