"""Bedarfsampel auf dem Calliope mini V3.

Hardware:
  - Straßenampel:    linke RGB-LED (rot/gelb/grün)
  - Fußgängerampel:  rechte RGB-LED (rot/grün)
  - Anforderungstaster: Button A
"""

import sys
sys.path.insert(0, "../Source")

from calliope_io import CalliopeIO

STRASSE = 0   # linke RGB-LED
FUSS = 2      # rechte RGB-LED

ROT = (255, 0, 0)
GELB = (255, 200, 0)
GRUEN = (0, 255, 0)


class Bedarfsampel:
    def __init__(self) -> None:
        self.c = CalliopeIO()
        self._loop_id = -1

    def start(self) -> None:
        if self._loop_id != -1:
            print("Loop bereits gestartet")
            return
        self.c.open()
        self.c.clear_pressed()
        self.c.set_rgb(STRASSE, *GRUEN)
        self.c.set_rgb(FUSS, *ROT)
        self._loop_id = self.c.start_loop(self._tick, delay_ms=50)

    def stopp(self) -> None:
        if self._loop_id == -1:
            return
        self.c.stop_loop(self._loop_id)
        self._loop_id = -1
        self.c.close()

    def _tick(self) -> None:
        if self.c.was_button_a_pressed():
            self._phase_durchlaufen()

    def _phase_durchlaufen(self) -> None:
        self.c.pause(500)
        self.c.set_rgb(STRASSE, *GELB)
        self.c.pause(1000)
        self.c.set_rgb(STRASSE, *ROT)
        self.c.pause(500)
        self.c.set_rgb(FUSS, *GRUEN)
        self.c.pause(4000)
        self.c.set_rgb(FUSS, *ROT)
        self.c.clear_pressed()
        self.c.pause(500)
        self.c.set_rgb(STRASSE, *GELB)
        self.c.pause(500)
        self.c.set_rgb(STRASSE, *GRUEN)
        self.c.pause(2000)


if __name__ == "__main__":
    ampel = Bedarfsampel()
    ampel.start()
    try:
        input("Enter drücken zum Beenden...\n")
    finally:
        ampel.stopp()
