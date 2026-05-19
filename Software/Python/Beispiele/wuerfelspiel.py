"""Würfelspiel auf dem Calliope mini V3.

Schütteln oder Button A wirft den Würfel — das Ergebnis (1..6) wird als
Augenmuster auf der LED-Matrix gezeigt.

Schummelmodus:  liegt ein Magnet am Calliope mini, fällt der Würfel immer auf 6.
Zu hell?       Glücksspiele sind dann verboten (Display bleibt aus).
"""

import math
import random
import sys
sys.path.insert(0, "../Source")

from calliope_io import CalliopeIO

LICHT_MAX = 200
MAGNET_DIFF = 200

# Augenmuster als Set von (y, x)-Tupeln pro Augenzahl.
WUERFEL_MUSTER: dict[int, set[tuple[int, int]]] = {
    1: {(2, 2)},
    2: {(0, 0), (4, 4)},
    3: {(0, 0), (2, 2), (4, 4)},
    4: {(0, 0), (0, 4), (4, 0), (4, 4)},
    5: {(0, 0), (0, 4), (2, 2), (4, 0), (4, 4)},
    6: {(0, 0), (0, 4), (2, 0), (2, 4), (4, 0), (4, 4)},
}


def _magnitude(c: CalliopeIO) -> int:
    x, y, z = c.compass()
    return int(math.sqrt(x * x + y * y + z * z))


def _grid_for(n: int) -> list[list[int]]:
    grid = [[0] * 5 for _ in range(5)]
    for y, x in WUERFEL_MUSTER.get(n, set()):
        grid[y][x] = 255
    return grid


class WuerfelSpiel:
    def __init__(self) -> None:
        self.c = CalliopeIO()
        self._magnet_referenz = 0
        self._loop_id = -1

    def start(self) -> None:
        self.c.open()
        # Sensor-Streams brauchen kurz, bis die ersten Werte eintreffen.
        self.c.pause(300)
        self._magnet_referenz = sum(_magnitude(self.c) for _ in range(8)) // 8
        print(f"Magnet-Referenz: {self._magnet_referenz}")
        self._loop_id = self.c.start_loop(self._tick, delay_ms=100)

    def stopp(self) -> None:
        if self._loop_id == -1:
            return
        self.c.stop_loop(self._loop_id)
        self._loop_id = -1
        self.c.close()

    def _tick(self) -> None:
        if not (self.c.was_button_a_pressed() or self.c.was_shaken()):
            return
        if self.c.light_level() > LICHT_MAX:
            self.c.scroll_text("Zu hell!")
            return
        ergebnis = 6 if self._magnet_an() else random.randint(1, 6)
        for _ in range(6):  # kurze "Animation"
            self.c.show_image(_grid_for(random.randint(1, 6)))
            self.c.pause(80)
        self.c.show_image(_grid_for(ergebnis))
        self.c.play_beep(120)

    def _magnet_an(self) -> bool:
        return abs(_magnitude(self.c) - self._magnet_referenz) > MAGNET_DIFF


if __name__ == "__main__":
    w = WuerfelSpiel()
    w.start()
    try:
        input("Enter drücken zum Beenden...\n")
    finally:
        w.stopp()
