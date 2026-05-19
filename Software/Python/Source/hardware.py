"""Modul-Wrapper für CalliopeIO — Python-Pendant zur Java-Klasse ``hardware``.

Beispiel::

    import hardware
    hardware.led_on(2, 2)
    hardware.scroll_text("Hallo")
    hardware.set_rgb_all(0, 255, 0)
    print(hardware.temperature(), "°C")

Die Verbindung wird beim ersten Aufruf automatisch hergestellt.
"""

from __future__ import annotations

import threading
from typing import Callable, Sequence

from calliope_io import CalliopeIO


_calliope: CalliopeIO | None = None
_lock = threading.Lock()


def _io() -> CalliopeIO:
    global _calliope
    with _lock:
        if _calliope is None:
            _calliope = CalliopeIO()
        return _calliope


# ---- LED-Matrix ------------------------------------------------------------
def led_on(x: int, y: int) -> None:                                _io().led_on(x, y)
def led_off(x: int, y: int) -> None:                               _io().led_off(x, y)
def led_set(x: int, y: int, state: bool) -> None:                  _io().led_set(x, y, state)
def led_dim(x: int, y: int, brightness: int) -> None:              _io().led_dim(x, y, brightness)
def clear_display() -> None:                                       _io().clear_display()
def show_image(grid: Sequence[Sequence[int]]) -> None:             _io().show_image(grid)
def scroll_text(text: str, delay: int = 120) -> None:              _io().scroll_text(text, delay)
def scroll_number(n: int, delay: int = 120) -> None:               _io().scroll_number(n, delay)
def enable_display(enable: bool) -> None:                          _io().enable_display(enable)

# ---- RGB-LEDs --------------------------------------------------------------
def set_rgb(led: int, r: int, g: int, b: int) -> None:             _io().set_rgb(led, r, g, b)
def set_rgb_all(r: int, g: int, b: int) -> None:                   _io().set_rgb_all(r, g, b)
def clear_rgb() -> None:                                           _io().clear_rgb()

# ---- Motoren ---------------------------------------------------------------
def set_motor(motor: int, forward: bool, speed: int) -> None:      _io().set_motor(motor, forward, speed)
def stop_motor(motor: int) -> None:                                _io().stop_motor(motor)
def stop_motors() -> None:                                         _io().stop_motors()

# ---- Buttons ---------------------------------------------------------------
def is_button_a_pressed() -> bool:                          return _io().is_button_a_pressed()
def is_button_b_pressed() -> bool:                          return _io().is_button_b_pressed()
def was_button_a_pressed() -> bool:                         return _io().was_button_a_pressed()
def was_button_b_pressed() -> bool:                         return _io().was_button_b_pressed()
def clear_pressed() -> None:                                       _io().clear_pressed()

# ---- Sensoren --------------------------------------------------------------
def acceleration_x() -> int:                                return _io().acceleration_x()
def acceleration_y() -> int:                                return _io().acceleration_y()
def acceleration_z() -> int:                                return _io().acceleration_z()
def acceleration() -> tuple[int, int, int]:                 return _io().acceleration()
def compass_x() -> int:                                     return _io().compass_x()
def compass_y() -> int:                                     return _io().compass_y()
def compass_z() -> int:                                     return _io().compass_z()
def compass() -> tuple[int, int, int]:                      return _io().compass()
def compass_heading() -> int:                               return _io().compass_heading()
def temperature() -> int:                                   return _io().temperature()
def light_level() -> int:                                   return _io().light_level()
def was_shaken() -> bool:                                   return _io().was_shaken()

# ---- Ringpads --------------------------------------------------------------
def set_pad_analog_in(pad: int) -> None:                           _io().set_pad_analog_in(pad)
def read_pad_analog(pad: int) -> int:                       return _io().read_pad_analog(pad)
def set_pad_digital_in(pad: int, pull_up: bool = False) -> None:   _io().set_pad_digital_in(pad, pull_up)
def read_pad_digital(pad: int) -> bool:                     return _io().read_pad_digital(pad)
def set_pad_digital_out(pad: int) -> None:                         _io().set_pad_digital_out(pad)
def write_pad_digital(pad: int, state: bool) -> None:              _io().write_pad_digital(pad, state)
def set_pad_pwm(pad: int) -> None:                                 _io().set_pad_pwm(pad)
def write_pad_pwm(pad: int, value: int) -> None:                   _io().write_pad_pwm(pad, value)

# ---- Lautsprecher ----------------------------------------------------------
def play_beep(duration_ms: int) -> None:                           _io().play_beep(duration_ms)
def play_tone(frequency: int, duration_ms: int) -> None:           _io().play_tone(frequency, duration_ms)
def stop_beep() -> None:                                           _io().stop_beep()

# ---- Loop ------------------------------------------------------------------
def loop(fn: Callable[[], None], delay_ms: int = 0) -> int: return _io().start_loop(fn, delay_ms)
def stop_loop(loop_id: int) -> None:                               _io().stop_loop(loop_id)
def stop_all_loops() -> None:                                      _io().stop_all_loops()
def list_loops() -> None:                                          _io().list_loops()

# ---- Timing ----------------------------------------------------------------
def pause(ms: int) -> None:                                        CalliopeIO.pause(ms)
def start_clock() -> None:                                         _io().start_clock()
def clock() -> int:                                         return _io().clock()
def stop_clock() -> int:                                    return _io().stop_clock()

# ---- Verbindung ------------------------------------------------------------
def is_connected() -> bool:                                 return _io().is_connected()
def open() -> None:                                                _io().open()
def close() -> None:                                               _io().close()
def reconnect() -> None:
    global _calliope
    with _lock:
        _calliope = CalliopeIO()
