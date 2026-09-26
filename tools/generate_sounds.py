"""Gera os dois toques originais do aplicativo, sem dependências externas."""
import math
import random
from pathlib import Path
import struct
import wave

ROOT = Path(__file__).resolve().parents[1] / "mobile/app/src/main/res/raw"


def render(name, urgent=False, style="classic"):
    rate = 22050
    samples = bytearray()
    phase = 0.0
    for i in range(rate * 2):
        t = i / rate
        if style == "aurora":
            frequency = [523.25, 659.25, 783.99, 1046.5][int(t * 2) % 4]
            envelope = .42 * math.exp(-4 * (t % .5))
        elif style == "orbit":
            frequency = 440 if int(t * 4) % 2 == 0 else 660
            envelope = .42 * max(0, math.sin(2 * math.pi * 2 * t))
        elif urgent:
            frequency = 650 + 650 * (0.5 + 0.5 * math.sin(2 * math.pi * t))
            envelope = 0.65
        else:
            frequency = 880
            position = t % 0.5
            envelope = 0.4 * max(0, min(1, position / 0.01, (0.22 - position) / 0.01))
        phase += 2 * math.pi * frequency / rate
        # Rampas evitam cliques na transição entre repetições.
        edge = min(1, t / 0.01, (2 - t) / 0.01)
        samples.extend(struct.pack("<h", int(32767 * envelope * edge * math.sin(phase))))
    ROOT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(ROOT / name), "wb") as output:
        output.setnchannels(1); output.setsampwidth(2); output.setframerate(rate)
        output.writeframes(samples)


def render_zen():
    """Ondas do mar sintetizadas: ruído filtrado, dinâmica lenta e emenda em crossfade."""
    rate = 22050
    rng = random.Random(21)
    values = []
    low = 0.0
    for i in range(rate * 13):
        t = i / rate
        low = .94 * low + .06 * rng.uniform(-1, 1)
        swell = .4 + .6 * (0.5 + .5 * math.sin(2 * math.pi * t / 6))
        values.append(max(-.8, min(.8, low * 2.8 * swell)))
    for i in range(rate):
        blend = i / rate
        values[-rate + i] = values[-rate + i] * (1 - blend) + values[i] * blend
    ROOT.mkdir(parents=True, exist_ok=True)
    with wave.open(str(ROOT / "zen.wav"), "wb") as output:
        output.setnchannels(1); output.setsampwidth(2); output.setframerate(rate)
        output.writeframes(b"".join(struct.pack("<h", int(32767 * v)) for v in values[rate:]))


if __name__ == "__main__":
    render("alarm.wav", False)
    render("siren.wav", True)
    render("aurora.wav", style="aurora")
    render("orbit.wav", style="orbit")
    render_zen()
    print(f"Toques gerados em {ROOT}")
