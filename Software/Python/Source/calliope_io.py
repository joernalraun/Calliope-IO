"""Python-Anbindung für den Calliope mini V3.

Funktional 1:1 zur Java-Variante (`CalliopeIO.java`), aber in
idiomatischem Python: Context-Manager-Support (`with CalliopeIO() as c:`),
Methoden ohne `get_`-Prefix, Callable-basiertes Loop-System, `is_*` für
Bool-Predicates.
"""

from __future__ import annotations

import math
import sys
import threading
import time
from typing import Callable, Optional, Sequence

import serial.tools.list_ports

from firmata_link import (
    FirmataLink,
    MODE_DIGITAL_INPUT, MODE_DIGITAL_OUTPUT,
    MODE_ANALOG_INPUT, MODE_PWM, MODE_INPUT_PULLUP,
    MB_DISPLAY_CLEAR, MB_DISPLAY_SHOW, MB_DISPLAY_PLOT,
    MB_SCROLL_STRING, MB_SCROLL_INTEGER, MB_DISPLAY_ENABLE,
    MB_CALLIOPE_MOTOR, MB_CALLIOPE_RGB, MB_CALLIOPE_SOUND,
)


# ---- Konstanten ------------------------------------------------------------

# Pin-Indices (Firmata 0..20)
P0, P1, P2, P3 = 0, 1, 2, 3
PIN_BUTTON_A = 5
PIN_BUTTON_B = 11
A0_SCL, A0_SDA = 19, 20
A1_RX, A1_TX = 16, 17

# Sensor-Channels (mbFirmata-Mapping)
_CH_ACCEL_X, _CH_ACCEL_Y, _CH_ACCEL_Z = 8, 9, 10
_CH_LIGHT = 11
_CH_TEMP = 12
_CH_COMPASS_X, _CH_COMPASS_Y, _CH_COMPASS_Z = 13, 14, 15

# Default-USB-Identifikation Calliope mini V3 (gleich wie micro:bit V2)
_DEFAULT_VID = 0x0D28
_DEFAULT_PID = 0x0204

_DEFAULT_BEEP_FREQ = 1000  # Hz
_MAX_BEEP_MS = 5000

_PWM_MAX = 1023
_BYTE7_MAX = 127
_BYTE14_MAX = 16383

_NUM_RGB_LEDS = 3


def _signed14(raw: int) -> int:
    """14-Bit-Rohwert (mbFirmata) als signed-int interpretieren."""
    return raw | ~0x3FFF if raw & 0x2000 else raw


def _clamp(value: int, lo: int, hi: int) -> int:
    return max(lo, min(hi, value))


class CalliopeIO:
    """Anbindung an einen per USB verbundenen Calliope mini V3.

    Unterstützt den Context-Manager-Stil::

        with CalliopeIO() as c:
            c.scroll_text("Hallo")
            print(c.temperature())

    oder den klassischen open/close-Stil::

        c = CalliopeIO()
        c.open()
        ...
        c.close()
    """

    def __init__(self) -> None:
        self._link: Optional[FirmataLink] = None
        self._board_active: bool = False
        self._open_attempted: bool = False
        self._button_a_latched: bool = False
        self._button_b_latched: bool = False
        self._shake_latched: bool = False
        self._light_sensor_active: bool = False
        self._start_zeit: Optional[float] = None
        self._rgb_state: list[list[int]] = [[0, 0, 0] for _ in range(_NUM_RGB_LEDS)]

        self._valid_vids: set[int] = {_DEFAULT_VID}
        self._valid_pids: set[int] = {_DEFAULT_PID}

        self._loops: dict[int, tuple[threading.Thread, threading.Event]] = {}
        self._loop_keys: dict[int, str] = {}
        self._loop_counter: int = 0

        self._monitor_thread: Optional[threading.Thread] = None
        self._monitor_stop: threading.Event = threading.Event()

    # ---- Context Manager ---------------------------------------------------

    def __enter__(self) -> "CalliopeIO":
        self.open()
        if not self._board_active:
            raise RuntimeError("Calliope mini nicht verbunden.")
        return self

    def __exit__(self, exc_type, exc_val, exc_tb) -> None:
        self.close()

    # ---- Verbindung --------------------------------------------------------

    def add_usb_id(self, vid: int, pid: int) -> None:
        """Zusätzliche USB-Identifikation registrieren, falls dein Board abweicht."""
        self._valid_vids.add(vid)
        self._valid_pids.add(pid)

    def open(self) -> None:
        """Verbindung zum Calliope mini herstellen."""
        self._open_attempted = True
        if self._link is not None:
            print("Calliope mini ist bereits verbunden.", file=sys.stderr)
            return
        port_name = self._find_port()
        if port_name is None:
            print("Kein Calliope mini V3 gefunden. Anschluss / Berechtigungen prüfen.",
                  file=sys.stderr)
            return
        link = FirmataLink()
        if not link.open(port_name):
            print("Verbindung zum Calliope mini fehlgeschlagen.", file=sys.stderr)
            return
        self._link = link
        self._init_pins()
        self._board_active = True
        major, minor = link.firmware_version()
        print(f"Calliope mini verbunden auf: {port_name} (Firmware {major}.{minor})")
        self._start_monitor()

    def close(self) -> None:
        """Verbindung sauber schließen (Display + LEDs + Motoren + Ton aus)."""
        if self._link is None:
            return
        try:
            self.stop_all_loops()
        except Exception:
            pass
        for action in (self.clear_display, self.clear_rgb, self.stop_motors, self.stop_beep):
            try:
                action()
            except Exception:
                pass
        self._board_active = False
        self._monitor_stop.set()
        if self._monitor_thread is not None:
            self._monitor_thread.join(timeout=1.0)
            self._monitor_thread = None
        self._link.close()
        self._link = None
        print("Verbindung zum Calliope mini beendet.")

    def is_connected(self) -> bool:
        """True, wenn die Verbindung steht."""
        return self._board_active

    def hard_reset(self) -> None:
        """Verbindung hart zurücksetzen; danach ist ein neuer ``open()`` möglich."""
        print("Hard-Reset wird ausgeführt...")
        try:
            self.stop_all_loops()
        except Exception:
            pass
        if self._link is not None:
            try:
                self._link.close()
            except Exception:
                pass
            self._link = None
        self._board_active = False
        self._open_attempted = False
        self._monitor_stop.set()
        if self._monitor_thread is not None:
            self._monitor_thread.join(timeout=1.0)
            self._monitor_thread = None
        print("Hard-Reset abgeschlossen.")

    def _ensure_connected(self) -> None:
        if self._board_active and self._link is not None:
            return
        if not self._open_attempted:
            print("Starte automatische Verbindung zum Calliope mini...")
            self.open()
        if self._link is None:
            raise RuntimeError(
                "Calliope mini nicht verbunden. Bitte Firmware flashen "
                "(CalliopeMiniFirmata.hex) und USB-Kabel prüfen. "
                "hard_reset() ermöglicht einen neuen Verbindungsversuch."
            )

    def _find_port(self) -> Optional[str]:
        for p in serial.tools.list_ports.comports():
            if p.vid in self._valid_vids and p.pid in self._valid_pids:
                print(f"Calliope-mini-Kandidat: {p.device} "
                      f"(VID=0x{p.vid:04X}, PID=0x{p.pid:04X})")
                return p.device
        return None

    def _init_pins(self) -> None:
        assert self._link is not None
        self._link.set_pin_mode(PIN_BUTTON_A, MODE_DIGITAL_INPUT)
        self._link.set_pin_mode(PIN_BUTTON_B, MODE_DIGITAL_INPUT)
        self._link.stream_digital(0, True)
        self._link.stream_digital(1, True)
        for ch in (_CH_ACCEL_X, _CH_ACCEL_Y, _CH_ACCEL_Z,
                   _CH_TEMP, _CH_COMPASS_X, _CH_COMPASS_Y, _CH_COMPASS_Z):
            self._link.stream_analog(ch, True)
        self._link.set_sampling_interval(50)

    def _start_monitor(self) -> None:
        self._monitor_stop.clear()

        def run() -> None:
            while not self._monitor_stop.is_set() and self._board_active:
                if self._link is None:
                    return
                if self._link.digital_rose(PIN_BUTTON_A):
                    self._button_a_latched = True
                if self._link.digital_rose(PIN_BUTTON_B):
                    self._button_b_latched = True
                self._monitor_stop.wait(0.03)

        self._monitor_thread = threading.Thread(target=run, daemon=True)
        self._monitor_thread.start()

    # ---- Buttons -----------------------------------------------------------

    def is_button_a_pressed(self) -> bool:
        """True, solange Button A gedrückt ist."""
        self._ensure_connected()
        return not self._link.digital(PIN_BUTTON_A)  # active-low

    def is_button_b_pressed(self) -> bool:
        """True, solange Button B gedrückt ist."""
        self._ensure_connected()
        return not self._link.digital(PIN_BUTTON_B)

    def was_button_a_pressed(self) -> bool:
        """True einmalig, wenn Button A seit dem letzten Aufruf gedrückt wurde."""
        self._ensure_connected()
        v, self._button_a_latched = self._button_a_latched, False
        return v

    def was_button_b_pressed(self) -> bool:
        """True einmalig, wenn Button B seit dem letzten Aufruf gedrückt wurde."""
        self._ensure_connected()
        v, self._button_b_latched = self._button_b_latched, False
        return v

    def clear_pressed(self) -> None:
        """Beide Button-Edge-Flags zurücksetzen."""
        self._ensure_connected()
        self._button_a_latched = False
        self._button_b_latched = False

    # ---- LED-Matrix (5×5) --------------------------------------------------

    def led_dim(self, x: int, y: int, brightness: int) -> None:
        """Eine LED dimmen (x,y ∈ 0..4; brightness 0..255)."""
        self._ensure_connected()
        if not (0 <= x <= 4 and 0 <= y <= 4):
            return
        level = _clamp(brightness, 0, 255) // 2  # 7-bit
        self._link.send_sysex(MB_DISPLAY_PLOT, bytes([x, y, level]))

    def led_on(self, x: int, y: int) -> None:
        self.led_dim(x, y, 255)

    def led_off(self, x: int, y: int) -> None:
        self.led_dim(x, y, 0)

    def led_set(self, x: int, y: int, state: bool) -> None:
        self.led_dim(x, y, 255 if state else 0)

    def show_image(self, grid: Sequence[Sequence[int]]) -> None:
        """Komplettes 5×5-Bild zeigen (grid[y][x] mit Helligkeiten 0..255)."""
        self._ensure_connected()
        if len(grid) != 5 or any(len(row) != 5 for row in grid):
            raise ValueError("show_image erwartet ein 5×5-Raster")
        payload = bytearray([1])  # grayscale flag
        for row in grid:
            payload.extend(_clamp(v, 0, 255) // 2 for v in row)
        self._link.send_sysex(MB_DISPLAY_SHOW, bytes(payload))

    def clear_display(self) -> None:
        """Alle Matrix-LEDs aus."""
        self._ensure_connected()
        self._link.send_sysex(MB_DISPLAY_CLEAR)

    def scroll_text(self, text: str, delay: int = 120) -> None:
        """Text über die Matrix scrollen."""
        self._ensure_connected()
        d = _clamp(delay, 1, _BYTE7_MAX)
        payload = bytearray([d])
        for byte in (text or "").encode("utf-8"):
            payload.append(byte & 0x7F)
            payload.append((byte >> 7) & 0x01)
        self._link.send_sysex(MB_SCROLL_STRING, bytes(payload))

    def scroll_number(self, n: int, delay: int = 120) -> None:
        """Ganzzahl scrollen."""
        self._ensure_connected()
        d = _clamp(delay, 1, _BYTE7_MAX)
        self._link.send_sysex(MB_SCROLL_INTEGER, bytes([
            d,
            n & 0x7F, (n >> 7)  & 0x7F, (n >> 14) & 0x7F,
            (n >> 21) & 0x7F, (n >> 28) & 0x7F,
        ]))

    def enable_display(self, enable: bool) -> None:
        """Display ein-/ausschalten (deaktiviert gibt Display-Pins als GPIO frei)."""
        self._ensure_connected()
        self._link.send_sysex(MB_DISPLAY_ENABLE, bytes([1 if enable else 0]))

    # ---- RGB-LEDs (3 onboard) ----------------------------------------------

    def set_rgb(self, led: int, r: int, g: int, b: int) -> None:
        """Eine RGB-LED (Index 0..2) setzen."""
        self._ensure_connected()
        if not (0 <= led < _NUM_RGB_LEDS):
            return
        self._rgb_state[led] = [
            _clamp(r, 0, 255) // 2,
            _clamp(g, 0, 255) // 2,
            _clamp(b, 0, 255) // 2,
        ]
        self._flush_rgb()

    def set_rgb_all(self, r: int, g: int, b: int) -> None:
        """Alle RGB-LEDs gleichzeitig setzen."""
        for i in range(_NUM_RGB_LEDS):
            self.set_rgb(i, r, g, b)

    def clear_rgb(self) -> None:
        """Alle RGB-LEDs aus."""
        self.set_rgb_all(0, 0, 0)

    def _flush_rgb(self) -> None:
        payload = bytearray()
        for triple in self._rgb_state:
            payload.extend(triple)
        self._link.send_sysex(MB_CALLIOPE_RGB, bytes(payload))

    # ---- Motoren -----------------------------------------------------------

    def set_motor(self, motor: int, forward: bool, speed: int) -> None:
        """Motor steuern (motor: 0 = M_A, 1 = M_B, 2 = beide; speed 0..127)."""
        self._ensure_connected()
        if not (0 <= motor <= 2):
            return
        s = _clamp(speed, 0, _BYTE7_MAX)
        self._link.send_sysex(MB_CALLIOPE_MOTOR,
                              bytes([motor, 0 if forward else 1, s]))

    def stop_motor(self, motor: int) -> None:
        self.set_motor(motor, True, 0)

    def stop_motors(self) -> None:
        self.set_motor(2, True, 0)

    # ---- Sensoren ----------------------------------------------------------

    def acceleration_x(self) -> int:
        self._ensure_connected()
        return _signed14(self._link.analog(_CH_ACCEL_X))

    def acceleration_y(self) -> int:
        self._ensure_connected()
        return _signed14(self._link.analog(_CH_ACCEL_Y))

    def acceleration_z(self) -> int:
        self._ensure_connected()
        return _signed14(self._link.analog(_CH_ACCEL_Z))

    def acceleration(self) -> tuple[int, int, int]:
        """Beschleunigung (x, y, z) als Tupel."""
        return self.acceleration_x(), self.acceleration_y(), self.acceleration_z()

    def compass_x(self) -> int:
        self._ensure_connected()
        return _signed14(self._link.analog(_CH_COMPASS_X))

    def compass_y(self) -> int:
        self._ensure_connected()
        return _signed14(self._link.analog(_CH_COMPASS_Y))

    def compass_z(self) -> int:
        self._ensure_connected()
        return _signed14(self._link.analog(_CH_COMPASS_Z))

    def compass(self) -> tuple[int, int, int]:
        return self.compass_x(), self.compass_y(), self.compass_z()

    def compass_heading(self) -> int:
        """Magnetfeld-Heading in Grad (0..359)."""
        x, y, _ = self.compass()
        return (int(math.degrees(math.atan2(y, x))) + 360) % 360

    def temperature(self) -> int:
        """Chiptemperatur in °C."""
        self._ensure_connected()
        return self._link.analog(_CH_TEMP)

    def light_level(self) -> int:
        """Helligkeit (0..255). Aktiviert den Sensor beim ersten Aufruf."""
        self._ensure_connected()
        if not self._light_sensor_active:
            self._link.set_pin_mode(11, MODE_ANALOG_INPUT)
            self._link.stream_analog(_CH_LIGHT, True)
            self._light_sensor_active = True
        return self._link.analog(_CH_LIGHT)

    def was_shaken(self) -> bool:
        """True einmalig, wenn |a| signifikant über 1 g geschnellt ist."""
        ax, ay, az = self.acceleration()
        shaken = ax * ax + ay * ay + az * az > 3_500_000  # ~1.9 g
        if shaken and not self._shake_latched:
            self._shake_latched = True
            return True
        if not shaken:
            self._shake_latched = False
        return False

    # ---- Ringpads P0..P3 ---------------------------------------------------

    def set_pad_analog_in(self, pad: int) -> None:
        self._ensure_connected()
        if pad not in (P0, P1, P2):  # nur diese sind analog-fähig
            return
        self._link.set_pin_mode(pad, MODE_ANALOG_INPUT)
        self._link.stream_analog(pad, True)

    def read_pad_analog(self, pad: int) -> int:
        self._ensure_connected()
        return self._link.analog(pad) if 0 <= pad <= 6 else 0

    def set_pad_digital_in(self, pad: int, pull_up: bool = False) -> None:
        self._ensure_connected()
        self._link.set_pin_mode(pad, MODE_INPUT_PULLUP if pull_up else MODE_DIGITAL_INPUT)

    def read_pad_digital(self, pad: int) -> bool:
        self._ensure_connected()
        return self._link.digital(pad)

    def set_pad_digital_out(self, pad: int) -> None:
        self._ensure_connected()
        self._link.set_pin_mode(pad, MODE_DIGITAL_OUTPUT)

    def write_pad_digital(self, pad: int, state: bool) -> None:
        self._ensure_connected()
        self._link.write_digital(pad, state)

    def set_pad_pwm(self, pad: int) -> None:
        self._ensure_connected()
        self._link.set_pin_mode(pad, MODE_PWM)

    def write_pad_pwm(self, pad: int, value: int) -> None:
        self._ensure_connected()
        self._link.write_analog(pad, _clamp(value, 0, _PWM_MAX))

    # ---- Lautsprecher (intern) --------------------------------------------

    def play_tone(self, frequency: int, duration_ms: int) -> None:
        """Ton spielen — kehrt sofort zurück. duration_ms=0 → bis stop_beep()."""
        self._ensure_connected()
        f = _clamp(frequency, 0, _BYTE14_MAX)
        d = _clamp(duration_ms, 0, _BYTE14_MAX)
        self._link.send_sysex(MB_CALLIOPE_SOUND, bytes([
            f & 0x7F, (f >> 7) & 0x7F,
            d & 0x7F, (d >> 7) & 0x7F,
        ]))

    def play_beep(self, duration_ms: int) -> None:
        """Standard-Piepton (1 kHz); blockiert bis zum Ende des Tons."""
        if not (0 <= duration_ms <= _MAX_BEEP_MS):
            print(f"Beep-Dauer muss 0..{_MAX_BEEP_MS} ms sein.", file=sys.stderr)
            return
        self.play_tone(_DEFAULT_BEEP_FREQ, duration_ms)
        self.pause(duration_ms)

    def stop_beep(self) -> None:
        self.play_tone(0, 0)

    # ---- Timing ------------------------------------------------------------

    @staticmethod
    def pause(ms: int) -> None:
        time.sleep(max(0, ms) / 1000.0)

    def start_clock(self) -> None:
        self._start_zeit = time.monotonic()

    def clock(self) -> int:
        """Laufzeit der Stoppuhr in ms; 0 wenn nicht gestartet."""
        if self._start_zeit is None:
            print("Stoppuhr nicht gestartet.")
            return 0
        return int((time.monotonic() - self._start_zeit) * 1000)

    def stop_clock(self) -> int:
        v = self.clock()
        self._start_zeit = None
        return v

    # ---- Loop-System -------------------------------------------------------

    def start_loop(self, fn: Callable[[], None], delay_ms: int = 0) -> int:
        """Callable zyklisch im Hintergrund-Thread aufrufen.

        Beispiel::

            id = c.start_loop(self._tick, delay_ms=100)
            ...
            c.stop_loop(id)
        """
        self._ensure_connected()
        key = f"{getattr(fn, '__qualname__', repr(fn))}"
        if any(k == key for k in self._loop_keys.values()):
            print(f"Loop bereits aktiv: {key}", file=sys.stderr)
            return -1
        loop_id = self._loop_counter
        self._loop_counter += 1
        stop_event = threading.Event()

        def run() -> None:
            try:
                while not stop_event.is_set():
                    fn()
                    if delay_ms > 0:
                        stop_event.wait(delay_ms / 1000.0)
            except Exception as e:
                print(f"Fehler in Loop {loop_id}: {e}", file=sys.stderr)

        t = threading.Thread(target=run, daemon=True)
        self._loops[loop_id] = (t, stop_event)
        self._loop_keys[loop_id] = key
        t.start()
        return loop_id

    def stop_loop(self, loop_id: int) -> None:
        entry = self._loops.pop(loop_id, None)
        self._loop_keys.pop(loop_id, None)
        if entry is None:
            return
        thread, stop_event = entry
        stop_event.set()
        thread.join(timeout=2.0)

    def stop_all_loops(self) -> None:
        for loop_id in list(self._loops):
            self.stop_loop(loop_id)

    def list_loops(self) -> None:
        print(f"Aktive Loops: {list(self._loops)}")
