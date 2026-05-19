"""Dämmerungsschalter auf dem Calliope mini V3.

Fällt der Lichtsensor unter einen Schwellwert, leuchtet die mittlere
RGB-LED als warm-weiße Straßenlaterne — je dunkler, desto heller.
"""

import sys
sys.path.insert(0, "../Source")

from calliope_io import CalliopeIO

LAMPE = 1
SCHWELLE = 80  # 0..255


class Daemmerung:
    def __init__(self) -> None:
        self.c = CalliopeIO()
        self._loop_id = -1

    def start(self) -> None:
        if self._loop_id != -1:
            return
        self.c.open()
        self._loop_id = self.c.start_loop(self._tick, delay_ms=200)

    def stopp(self) -> None:
        if self._loop_id == -1:
            return
        self.c.stop_loop(self._loop_id)
        self._loop_id = -1
        self.c.close()

    def _tick(self) -> None:
        licht = self.c.light_level()
        if licht < SCHWELLE:
            helligkeit = min(255, (SCHWELLE - licht) * 4)
            self.c.set_rgb(LAMPE, helligkeit, helligkeit * 8 // 10, helligkeit // 3)
        else:
            self.c.set_rgb(LAMPE, 0, 0, 0)


if __name__ == "__main__":
    d = Daemmerung()
    d.start()
    try:
        input("Enter drücken zum Beenden...\n")
    finally:
        d.stopp()
