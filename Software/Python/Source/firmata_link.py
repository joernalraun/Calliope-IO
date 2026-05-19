"""Schlanker Firmata-Client für den Calliope mini V3.

Spricht das mbFirmata-Protokoll (Standard-Firmata 2.6 + micro:bit-/
Calliope-mini-SysEx). Wird intern von ``CalliopeIO`` verwendet und ist
normalerweise nicht direkt anzufassen.
"""

from __future__ import annotations

import threading
import time
from typing import Optional

import serial


# ---- Firmata-Konstanten ----------------------------------------------------

STREAM_ANALOG          = 0xC0
STREAM_DIGITAL         = 0xD0
ANALOG_UPDATE          = 0xE0
DIGITAL_UPDATE         = 0x90
SET_PIN_MODE           = 0xF4
SET_DIGITAL_PIN        = 0xF5
FIRMATA_VERSION        = 0xF9
SYSTEM_RESET           = 0xFF
SYSEX_START            = 0xF0
SYSEX_END              = 0xF7
REPORT_FIRMWARE        = 0x79
SAMPLING_INTERVAL      = 0x7A
EXTENDED_ANALOG_WRITE  = 0x6F

# mbFirmata-Erweiterungen
MB_DISPLAY_CLEAR       = 0x01
MB_DISPLAY_SHOW        = 0x02
MB_DISPLAY_PLOT        = 0x03
MB_SCROLL_STRING       = 0x04
MB_SCROLL_INTEGER      = 0x05
MB_SET_TOUCH_MODE      = 0x06
MB_DISPLAY_ENABLE      = 0x07
MB_COMPASS_CALIBRATE   = 0x08
MB_CALLIOPE_MOTOR      = 0x09
MB_CALLIOPE_RGB        = 0x0A
MB_CALLIOPE_SOUND      = 0x0B
MB_REPORT_EVENT        = 0x0D
MB_DEBUG_STRING        = 0x0E

# Pin-Modes
MODE_DIGITAL_INPUT     = 0x00
MODE_DIGITAL_OUTPUT    = 0x01
MODE_ANALOG_INPUT      = 0x02
MODE_PWM               = 0x03
MODE_INPUT_PULLUP      = 0x0B

PIN_COUNT              = 21
CHANNEL_COUNT          = 16


class FirmataLink:
    def __init__(self) -> None:
        self._port: Optional[serial.Serial] = None
        self._analog = [0] * CHANNEL_COUNT
        self._digital = [False] * PIN_COUNT
        self._digital_prev = [False] * PIN_COUNT
        self._ready = False
        self._firmware_major = 0
        self._firmware_minor = 0
        self._lock = threading.Lock()
        self._reader_thread: Optional[threading.Thread] = None
        self._stop_event = threading.Event()

        # Parser-Zustand
        self._parse_state = 0           # 0 = idle, 1 = sysex, sonst arg-count
        self._cmd_byte = 0
        self._arg_buf = [0, 0]
        self._arg_idx = 0
        self._sysex_buf = bytearray()

    # ---- Verbindung --------------------------------------------------------

    def open(self, port_name: str) -> bool:
        if self._port is not None:
            return False
        try:
            self._port = serial.Serial(port_name, baudrate=57600, timeout=0.05)
        except (serial.SerialException, OSError) as e:
            print(f"FirmataLink: Port {port_name} kann nicht geöffnet werden: {e}")
            self._port = None
            return False

        self._stop_event.clear()
        self._reader_thread = threading.Thread(target=self._reader_loop, daemon=True)
        self._reader_thread.start()

        # Standard-Firmata: REPORT_VERSION anfordern und warten.
        self._send_bytes(bytes([FIRMATA_VERSION]))
        deadline = time.monotonic() + 3.0
        while not self._ready and time.monotonic() < deadline:
            time.sleep(0.05)
        if not self._ready:
            print(f"FirmataLink: Kein FIRMATA_VERSION-Reply von {port_name}")
            self.close()
            return False
        return True

    def close(self):
        if self._port is None:
            return
        self._stop_event.set()
        try:
            self._port.close()
        except Exception:
            pass
        if self._reader_thread is not None:
            self._reader_thread.join(timeout=1.0)
            self._reader_thread = None
        self._port = None
        self._ready = False
        for i in range(CHANNEL_COUNT):
            self._analog[i] = 0
        for i in range(PIN_COUNT):
            self._digital[i] = False
            self._digital_prev[i] = False

    def is_ready(self) -> bool:
        return self._ready

    def firmware_version(self) -> tuple:
        return (self._firmware_major, self._firmware_minor)

    # ---- Schreibseite ------------------------------------------------------

    def set_pin_mode(self, pin: int, mode: int):
        self._send_bytes(bytes([SET_PIN_MODE, pin & 0x7F, mode & 0x7F]))

    def write_digital(self, pin: int, state: bool):
        self._send_bytes(bytes([SET_DIGITAL_PIN, pin & 0x7F, 1 if state else 0]))

    def write_analog(self, pin: int, value: int):
        v = max(0, min(16383, value))
        if pin <= 0x0F and v <= 0x3FFF:
            self._send_bytes(bytes([ANALOG_UPDATE | pin, v & 0x7F, (v >> 7) & 0x7F]))
        else:
            self._send_bytes(bytes([
                SYSEX_START, EXTENDED_ANALOG_WRITE,
                pin & 0x7F, v & 0x7F, (v >> 7) & 0x7F,
                SYSEX_END,
            ]))

    def stream_analog(self, channel: int, on: bool):
        self._send_bytes(bytes([STREAM_ANALOG | (channel & 0x0F), 1 if on else 0]))

    def stream_digital(self, port_num: int, on: bool):
        self._send_bytes(bytes([STREAM_DIGITAL | (port_num & 0x0F), 1 if on else 0]))

    def set_sampling_interval(self, ms: int):
        self._send_bytes(bytes([
            SYSEX_START, SAMPLING_INTERVAL,
            ms & 0x7F, (ms >> 7) & 0x7F,
            SYSEX_END,
        ]))

    def send_sysex(self, command: int, payload: bytes = b"") -> None:
        msg = bytearray([SYSEX_START, command & 0x7F])
        if payload:
            msg.extend(payload)
        msg.append(SYSEX_END)
        self._send_bytes(bytes(msg))

    def _send_bytes(self, data: bytes):
        if self._port is None:
            return
        try:
            with self._lock:
                self._port.write(data)
        except (serial.SerialException, OSError) as e:
            print(f"FirmataLink: Schreibfehler: {e}")

    # ---- Leseseite ---------------------------------------------------------

    def analog(self, channel: int) -> int:
        if 0 <= channel < CHANNEL_COUNT:
            return self._analog[channel]
        return 0

    def digital(self, pin: int) -> bool:
        if 0 <= pin < PIN_COUNT:
            return self._digital[pin]
        return False

    def digital_rose(self, pin: int) -> bool:
        """True einmalig bei steigender Flanke seit dem letzten Aufruf."""
        if not (0 <= pin < PIN_COUNT):
            return False
        cur = self._digital[pin]
        prev = self._digital_prev[pin]
        self._digital_prev[pin] = cur
        return cur and not prev

    # ---- Reader-Thread -----------------------------------------------------

    def _reader_loop(self):
        port = self._port
        while not self._stop_event.is_set() and port is not None:
            try:
                data = port.read(64)
            except (serial.SerialException, OSError):
                break
            if not data:
                continue
            for b in data:
                self._parse_byte(b)

    def _parse_byte(self, b: int):
        if self._parse_state == 1:  # SysEx
            if b == SYSEX_END:
                self._dispatch_sysex()
                self._parse_state = 0
                self._sysex_buf.clear()
            else:
                if len(self._sysex_buf) < 256:
                    self._sysex_buf.append(b)
            return

        if b & 0x80:  # Status-Byte
            if b == SYSEX_START:
                self._parse_state = 1
                self._sysex_buf.clear()
                return
            self._cmd_byte = b
            self._arg_idx = 0
            high = b & 0xF0
            if high in (DIGITAL_UPDATE, ANALOG_UPDATE) or b == FIRMATA_VERSION:
                self._parse_state = 2
            else:
                self._parse_state = 0
            return

        # Datenbyte
        if self._parse_state > 0:
            self._arg_buf[self._arg_idx] = b
            self._arg_idx += 1
            if self._arg_idx >= self._parse_state:
                self._dispatch_channel()
                self._parse_state = 0
                self._arg_idx = 0

    def _dispatch_channel(self):
        cmd = self._cmd_byte
        high = cmd & 0xF0
        chan = cmd & 0x0F
        if high == ANALOG_UPDATE:
            val = self._arg_buf[0] | (self._arg_buf[1] << 7)
            if chan < CHANNEL_COUNT:
                self._analog[chan] = val
        elif high == DIGITAL_UPDATE:
            mask = self._arg_buf[0] | (self._arg_buf[1] << 7)
            base = 8 * chan
            for i in range(8):
                pin = base + i
                if pin < PIN_COUNT:
                    self._digital[pin] = bool((mask >> i) & 1)
        elif cmd == FIRMATA_VERSION:
            self._firmware_major = self._arg_buf[0]
            self._firmware_minor = self._arg_buf[1]
            self._ready = True

    def _dispatch_sysex(self):
        if not self._sysex_buf:
            return
        if self._sysex_buf[0] == REPORT_FIRMWARE:
            self._ready = True
        # MB_REPORT_EVENT / MB_DEBUG_STRING etc. werden hier ignoriert.
