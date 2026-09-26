"""Gera PNG e ICO do mesmo símbolo usado no ícone vetorial Android."""
from pathlib import Path
import math
import struct
import zlib

SIZE = 512
OUT = Path(__file__).resolve().parents[1] / "desktop" / "assets"


def inside_star(x, y, cx, cy, radius):
    dx, dy = x - cx, y - cy
    distance = math.hypot(dx, dy)
    if distance > radius:
        return False
    angle = (math.atan2(dy, dx) + math.pi / 2) % (math.pi * 2)
    segment = angle / (math.pi / 5)
    fraction = segment % 1
    r1 = radius if int(segment) % 2 == 0 else radius * .45
    r2 = radius * .45 if int(segment) % 2 == 0 else radius
    return distance < r1 + (r2 - r1) * fraction


def sample(x, y):
    # Cantos arredondados e fundo em degradê, com transparência fora do ícone.
    edge, radius = .016, .21
    if x < edge or x > 1 - edge or y < edge or y > 1 - edge:
        return (0, 0, 0, 0)
    cx = min(max(x, edge + radius), 1 - edge - radius)
    cy = min(max(y, edge + radius), 1 - edge - radius)
    if math.hypot(x - cx, y - cy) > radius:
        return (0, 0, 0, 0)
    t = max(0, min(1, (x + y) / 2))
    color = tuple(round(a * (1 - t) + b * t) for a, b in zip((75, 51, 90), (24, 19, 37)))
    distance = math.hypot((x - .47) / .40, (y - .56) / .42)
    glow = max(0, 1 - distance) * .20
    color = tuple(round(c * (1 - glow) + g * glow) for c, g in zip(color, (215, 172, 247)))
    outer = ((x - .45) / .265) ** 2 + ((y - .55) / .29) ** 2 < 1
    cut = ((x - .565) / .215) ** 2 + ((y - .425) / .255) ** 2 < 1
    if outer and not cut:
        t = max(0, min(1, y))
        color = tuple(round(a * (1 - t) + b * t) for a, b in zip((255, 223, 159), (255, 167, 129)))
    for sx, sy, sr in ((.67, .39, .072), (.54, .25, .036), (.80, .27, .025)):
        if inside_star(x, y, sx, sy, sr):
            color = (255, 224, 153)
            break
    return (*color, 255)


def png(size, pixels):
    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body))
    raw = b"".join(b"\0" + b"".join(bytes(pixel) for pixel in row) for row in pixels)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def reduced(master, width):
    step = SIZE // width
    rows = []
    for y in range(0, SIZE, step):
        row = []
        for x in range(0, SIZE, step):
            block = [master[yy][xx] for yy in range(y, y + step) for xx in range(x, x + step)]
            alpha = sum(p[3] for p in block)
            if alpha == 0:
                row.append((0, 0, 0, 0))
            else:
                row.append(tuple(round(sum(p[k] * p[3] for p in block) / alpha) for k in range(3))
                           + (round(alpha / len(block)),))
        rows.append(row)
    return rows


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    master = [[sample((x + .5) / SIZE, (y + .5) / SIZE) for x in range(SIZE)] for y in range(SIZE)]
    sizes = (16, 32, 64, 128, 256)
    images = [(width, png(width, reduced(master, width))) for width in sizes]
    (OUT / "hardcore.png").write_bytes(images[-1][1])
    header = struct.pack("<HHH", 0, 1, len(images))
    offset = 6 + 16 * len(images)
    entries = []
    for width, payload in images:
        entries.append(struct.pack("<BBBBHHII", width % 256, width % 256, 0, 0, 1, 32, len(payload), offset))
        offset += len(payload)
    (OUT / "hardcore.ico").write_bytes(header + b"".join(entries) + b"".join(payload for _, payload in images))
    print(OUT / "hardcore.ico")


if __name__ == "__main__":
    main()
