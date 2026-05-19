"""Fensteralarm auf dem Calliope mini V3.

Der Calliope mini wird ans Fenster gehängt; ein kleiner Magnet sitzt am
Rahmen. Bewegt sich das Fenster, ändert sich das Magnetfeld am
Kompass-Sensor. Übersteigt die Änderung einen Schwellwert, gibt der
Lautsprecher Alarm und die LED-Matrix zeigt ein Ausrufezeichen.
"""

import math
import sys
sys.path.insert(0, "../Source")

from calliope_io import CalliopeIO

SCHWELLE = 200  # µT-Differenz, ab der ausgelöst wird

AUSRUFEZEICHEN = [
    [0, 0, 255, 0, 0],
    [0, 0, 255, 0, 0],
    [0, 0, 255, 0, 0],
    [0, 0,   0, 0, 0],
    [0, 0, 255, 0, 0],
]


def _magnitude(c: CalliopeIO) -> int:
    x, y, z = c.compass()
    return int(math.sqrt(x * x + y * y + z * z))


class Fensteralarm:
    def __init__(self) -> None:
        self.c = CalliopeIO()
        self._referenz = 0
        self._loop_id = -1

    def start(self) -> None:
        self.c.open()
        print("Kalibriere Magnetfeld (Fenster geschlossen halten)...")
        self.c.pause(500)
        # 8 Messungen mitteln, damit der Stream stabilisiert ist.
        self._referenz = sum(_magnitude(self.c) for _ in range(8)) // 8
        print(f"Referenz: {self._referenz}")
        self._loop_id = self.c.start_loop(self._tick, delay_ms=100)

    def stopp(self) -> None:
        if self._loop_id == -1:
            return
        self.c.stop_loop(self._loop_id)
        self._loop_id = -1
        self.c.close()

    def _tick(self) -> None:
        if abs(_magnitude(self.c) - self._referenz) > SCHWELLE:
            self._alarm()

    def _alarm(self) -> None:
        self.c.show_image(AUSRUFEZEICHEN)
        self.c.set_rgb_all(255, 0, 0)
        self.c.play_beep(300)
        self.c.pause(150)
        self.c.play_beep(300)
        self.c.clear_display()
        self.c.clear_rgb()


if __name__ == "__main__":
    f = Fensteralarm()
    f.start()
    try:
        input("Enter drücken zum Beenden...\n")
    finally:
        f.stopp()
