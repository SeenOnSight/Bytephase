"""Regenerate Bytephase's binary-themed Fabric icon using only Python stdlib."""
import math
import struct
import zlib

SIZE = 256
pixels = bytearray(SIZE * SIZE * 4)


def blend(x, y, color, alpha=255):
    if not (0 <= x < SIZE and 0 <= y < SIZE):
        return
    i = (y * SIZE + x) * 4
    a = alpha / 255
    pixels[i:i + 4] = bytes((
        round(pixels[i] * (1 - a) + color[0] * a),
        round(pixels[i + 1] * (1 - a) + color[1] * a),
        round(pixels[i + 2] * (1 - a) + color[2] * a), 255))


def rounded_box(x, y, left, top, right, bottom, radius):
    cx = min(max(x, left + radius), right - radius)
    cy = min(max(y, top + radius), bottom - radius)
    return (x - cx) ** 2 + (y - cy) ** 2 <= radius ** 2


for y in range(SIZE):
    for x in range(SIZE):
        if rounded_box(x, y, 8, 8, 248, 248, 46):
            t = (x + y) / (2 * SIZE)
            color = (round(20 - 15 * t), round(34 - 24 * t), round(62 - 40 * t))
            distance = math.hypot(x - 128, y - 128)
            halo = max(0, 1 - distance / 115) ** 2 * 0.25
            color = tuple(round(c * (1 - halo) + h * halo) for c, h in zip(color, (79, 220, 255)))
            blend(x, y, color)
        outer = rounded_box(x, y, 32, 32, 224, 224, 40)
        inner = rounded_box(x, y, 38, 38, 218, 218, 35)
        if outer and not inner:
            blend(x, y, (76, 215, 255))

digits = [
    ["01110", "10001", "10011", "10101", "11001", "10001", "01110"],
    ["00100", "01100", "00100", "00100", "00100", "00100", "01110"],
]
scale = 15
gap = 12
glyph_width = 5 * scale
total_width = glyph_width * 2 + gap
start_x = (SIZE - total_width) // 2
start_y = 75
for glyph_index, glyph in enumerate(digits):
    ox = start_x + glyph_index * (glyph_width + gap)
    for row, line in enumerate(glyph):
        for col, bit in enumerate(line):
            if bit == "1":
                for py in range(start_y + row * scale, start_y + (row + 1) * scale):
                    for px in range(ox + col * scale, ox + (col + 1) * scale):
                        blend(px, py, (138, 242, 255))


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xffffffff)


raw = b"".join(b"\0" + pixels[y * SIZE * 4:(y + 1) * SIZE * 4] for y in range(SIZE))
png = (b"\x89PNG\r\n\x1a\n"
       + chunk(b"IHDR", struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0))
       + chunk(b"IDAT", zlib.compress(raw, 9))
       + chunk(b"IEND", b""))
with open("src/main/resources/assets/bytephase/icon.png", "wb") as output:
    output.write(png)
