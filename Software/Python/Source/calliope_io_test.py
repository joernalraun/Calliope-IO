"""Selbsttest für CalliopeIO."""

from calliope_io import CalliopeIO


def main() -> None:
    print("\n==== CalliopeIO Testprogramm ====\n")

    with CalliopeIO() as c:
        # ---- LED-Matrix-Sweep ----
        print("LED-Matrix: laufendes Licht (Zeile-für-Zeile)")
        for y in range(5):
            for x in range(5):
                c.led_on(x, y)
                c.pause(40)
        c.pause(300)
        c.clear_display()

        # ---- Lauftext ----
        print('Lauftext: "Hallo!"')
        c.scroll_text("Hallo!")
        c.pause(2500)

        # ---- RGB-LEDs ----
        print("RGB-LEDs: rot / grün / blau")
        for r, g, b in [(255, 0, 0), (0, 255, 0), (0, 0, 255)]:
            c.set_rgb_all(r, g, b)
            c.pause(500)
        c.clear_rgb()

        # ---- Lautsprecher ----
        print("Lautsprecher: drei Töne (C5, E5, G5)")
        for freq in (523, 659, 784):
            c.play_tone(freq, 300)
            c.pause(350)
        c.stop_beep()

        # ---- Sensoren ----
        c.pause(200)
        ax, ay, az = c.acceleration()
        cx, cy, cz = c.compass()
        print("Sensorwerte (Momentaufnahme):")
        print(f"  Beschleunigung: x={ax}  y={ay}  z={az}")
        print(f"  Kompass:        x={cx}  y={cy}  z={cz}"
              f"  Heading={c.compass_heading()}°")
        print(f"  Temperatur:     {c.temperature()} °C")

        # ---- Buttons ----
        print("Drücken Sie 3 Sekunden lang Button A oder B...")
        c.clear_pressed()
        import time
        end = time.monotonic() + 3.0
        while time.monotonic() < end:
            if c.was_button_a_pressed():
                print("  → Button A wurde gedrückt")
            if c.was_button_b_pressed():
                print("  → Button B wurde gedrückt")
            c.pause(50)

        # ---- Motor ----
        print("Motor M_A: 500 ms vorwärts mit halber Kraft "
              "(falls Antrieb angeschlossen)")
        c.set_motor(0, forward=True, speed=64)
        c.pause(500)
        c.stop_motors()

    print("\n==== Test abgeschlossen. ====")


if __name__ == "__main__":
    main()
