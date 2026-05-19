# Calliope-IO — Python-Variante

Inhaltliches **Pendant** zur Java-Variante unter [`../Java/`](../Java/),
aber **idiomatisch in Python** geschrieben: Context-Manager, Callable-basiertes
Loop-System, snake_case-API ohne `get_`-Prefix, Type-Hints.

Identische Hardware, identische Firmata-Firmware, identische Beispiele —
nur die Programmiersprache (und Sprachkonventionen) sind eine andere.

## Voraussetzungen

1. **Calliope mini V3** mit der Firmata-Firmware
   ([`../Java/Firmata/CalliopeMiniFirmata.hex`](../Java/Firmata/CalliopeMiniFirmata.hex)
   funktioniert für Java **und** Python — ein und dieselbe Datei).
2. **Python 3.9+** (für die `list[...]` / `tuple[...]`-Generics in den
   Type-Hints; ältere Versionen funktionieren mit `from __future__ import annotations`,
   das in den Modulen bereits gesetzt ist).
3. **pyserial** installiert:
   ```bash
   python3 -m pip install pyserial
   ```

## Schnellstart

```python
from calliope_io import CalliopeIO

with CalliopeIO() as c:
    c.scroll_text("Hallo Welt!")
    c.set_rgb_all(0, 255, 100)
    c.play_tone(880, 500)
    print("Temperatur:", c.temperature(), "°C")
# Verbindung wird automatisch geschlossen (auch bei Exception)
```

Oder per Modul-Wrapper:

```python
import hardware

hardware.scroll_text("Hallo Welt!")
hardware.set_rgb_all(0, 255, 0)
print(hardware.acceleration())   # (x, y, z)
hardware.close()
```

## Idiomatic-Python-Highlights

| Feature | Beispiel |
|---|---|
| **Context-Manager** | `with CalliopeIO() as c:` — schließt auch bei Exception sauber |
| **Callable-Loop** | `c.start_loop(self._tick, delay_ms=100)` — direkt Methode übergeben, keine Strings |
| **Tuple-Returns** | `ax, ay, az = c.acceleration()`; `cx, cy, cz = c.compass()` |
| **Keine `get_`-Prefixe** | `c.temperature()`, `c.light_level()`, `c.compass_heading()` |
| **`is_*` für Bool** | `c.is_connected()`, `c.is_button_a_pressed()` |
| **`was_*` für Edge** | `c.was_button_a_pressed()`, `c.was_shaken()` (consume-once) |
| **Type-Hints** | `def play_tone(self, frequency: int, duration_ms: int) -> None:` |
| **Default-Argumente** | `c.scroll_text("Hi", delay=120)`, `c.start_loop(fn, delay_ms=0)` |
| **Datenstrukturen** | `WUERFEL_MUSTER: dict[int, set[tuple[int, int]]] = {...}` (siehe `wuerfelspiel.py`) |

## Funktionsübersicht

### LED-Matrix (5 × 5)
- `led_on(x, y)`, `led_off(x, y)`, `led_set(x, y, state)`, `led_dim(x, y, 0..255)`
- `show_image(grid)` — 5×5-Sequence; `grid[y][x]` Helligkeit 0..255
- `clear_display()`
- `scroll_text("...", delay=120)`, `scroll_number(42, delay=120)`
- `enable_display(False)` — Display-Pins für freie GPIO-Nutzung freigeben

### RGB-LEDs
- `set_rgb(led, r, g, b)`, `set_rgb_all(r, g, b)`, `clear_rgb()`

### Motoren
- `set_motor(motor, forward, speed)` — `motor ∈ {0, 1, 2}`, `speed ∈ 0..127`
- `stop_motor(motor)`, `stop_motors()`

### Buttons
- `is_button_a_pressed()`, `is_button_b_pressed()`
- `was_button_a_pressed()`, `was_button_b_pressed()`, `clear_pressed()`

### Sensoren
- `acceleration_x()`, `acceleration_y()`, `acceleration_z()`, **`acceleration()` → Tupel**
- `compass_x()`, `compass_y()`, `compass_z()`, **`compass()` → Tupel**, `compass_heading()`
- `temperature()` (°C), `light_level()` (0..255), `was_shaken()`

### Touch-Pins P0–P3
- `set_pad_analog_in(pad)` / `read_pad_analog(pad)`
- `set_pad_digital_in(pad, pull_up=False)` / `read_pad_digital(pad)`
- `set_pad_digital_out(pad)` / `write_pad_digital(pad, state)`
- `set_pad_pwm(pad)` / `write_pad_pwm(pad, 0..1023)`

### Interner Lautsprecher
- `play_tone(frequency_hz, duration_ms)`
- `play_beep(duration_ms)` — Standard-Piepton (1 kHz), blockierend
- `stop_beep()`

### Loop-System
- `start_loop(fn, delay_ms=0)` — Callable übergeben (z. B. `self._tick`)
- `stop_loop(id)`, `stop_all_loops()`, `list_loops()`

### Timing
- `pause(ms)` (statisch nutzbar: `CalliopeIO.pause(100)`), `start_clock()`,
  `clock()`, `stop_clock()`

### Verbindung
- `open()`, `close()`, `is_connected()`, `hard_reset()`

## Beispiele (mit Klassen-Pattern wie Pico-IO)

| Datei | Was passiert |
|---|---|
| `bedarfsampel.py` | Zwei RGB-LEDs als Auto- und Fußgängerampel, Button A als Anforderungstaster |
| `daemmerung.py`   | Lichtsensor steuert die mittlere RGB-LED als Straßenlaterne |
| `fensteralarm.py` | Magnetometer erkennt Fensterbewegung → Display + Lautsprecher alarmieren |
| `wuerfelspiel.py` | Schütteln oder Button A würfelt; Schummelmodus per Magnet; Display-Sperre bei zu viel Licht |

Jedes Beispiel kann sowohl als Klasse genutzt (`Bedarfsampel().start()`)
als auch direkt gestartet werden:

```bash
cd Beispiele
python3 bedarfsampel.py
```

## Verzeichnisstruktur

```
Python/
├── README.md
├── Source/
│   ├── firmata_link.py        ← Firmata-Protokoll-Client
│   ├── calliope_io.py         ← Hauptklasse (mit Context-Manager)
│   ├── hardware.py            ← Modul-Wrapper
│   └── calliope_io_test.py    ← Selbsttest
├── Beispiele/
│   ├── bedarfsampel.py
│   ├── daemmerung.py
│   ├── fensteralarm.py
│   └── wuerfelspiel.py
└── Bibliotheken/
    └── README.md
```

## FAQ

- **„Kein Calliope mini V3 gefunden":** Firmware noch nicht geflasht, falscher
  USB-Port, oder eine andere Anwendung hält den seriellen Port offen
  (Arduino-IDE / Thonny schließen).
- **macOS-Berechtigungen:** Beim ersten Start fragt macOS nach USB-Zugriff → erlauben.
- **Linux:** Benutzer muss in der Gruppe `dialout` (Debian/Ubuntu) sein.
- **`close()` nicht vergessen** — der Context-Manager (`with ...:`) erledigt das automatisch.
