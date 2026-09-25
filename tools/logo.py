"""Draws the PCMania logo mark and writes every icon the site uses.

The mark is an amber chip with pins and a navy lightning bolt, on a 64-unit grid. One set of
coordinates drives the SVGs and the raster icons, so they cannot drift apart. Change the
constants below and run it again:

    pip install pillow
    python tools/logo.py src/main/resources/static

Writes favicon.svg, favicon.ico, apple-touch-icon.png, images/logo-mark.svg and
images/icon-192.png / icon-512.png (the manifest icons).
"""
import sys, os
from PIL import Image, ImageDraw

NAVY, AMBER = "#0f172a", "#f59e0b"
CHIP = (16, 16, 48, 48, 6)                      # x0, y0, x1, y1, corner radius
PIN_AT, PIN_LEN, PIN_W = (23, 32, 41), 6.5, 3.6  # three pins per side
BOLT = [(35, 19.5), (23.5, 34.5), (30.5, 34.5), (28, 44.5), (40.5, 28.5), (33.5, 28.5)]

def pins():
    out = []
    for c in PIN_AT:
        h = PIN_W / 2
        out += [(CHIP[0] - PIN_LEN, c - h, CHIP[0], c + h), (CHIP[2], c - h, CHIP[2] + PIN_LEN, c + h),
                (c - h, CHIP[1] - PIN_LEN, c + h, CHIP[1]), (c - h, CHIP[3], c + h, CHIP[3] + PIN_LEN)]
    return out

def svg(background):
    f = lambda v: f"{v:g}"
    parts = ['<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 64 64">']
    if background == "rounded":
        parts.append(f'<rect width="64" height="64" rx="14" fill="{NAVY}"/>')
    parts.append(f'<g fill="{AMBER}">')
    for x0, y0, x1, y1 in pins():
        parts.append(f'<rect x="{f(x0)}" y="{f(y0)}" width="{f(x1-x0)}" height="{f(y1-y0)}" rx="1.2"/>')
    x0, y0, x1, y1, r = CHIP
    parts.append(f'<rect x="{x0}" y="{y0}" width="{x1-x0}" height="{y1-y0}" rx="{r}"/></g>')
    parts.append(f'<path fill="{NAVY}" d="M' + " ".join(f"{f(x)} {f(y)}" for x, y in BOLT) + 'Z"/>')
    parts.append('</svg>')
    return "".join(parts) + "\n"

def raster(size, background, scale_mark=1.0):
    """background: 'rounded' (favicon), 'square' (full-bleed: iOS and Android crop it themselves)."""
    ss = 8
    S = size * ss
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    u = S / 64
    if background == "rounded":
        d.rounded_rectangle([0, 0, S - 1, S - 1], radius=14 * u, fill=NAVY)
    elif background == "square":
        d.rectangle([0, 0, S, S], fill=NAVY)
    k = scale_mark
    t = lambda x, y: ((32 + (x - 32) * k) * u, (32 + (y - 32) * k) * u)
    for x0, y0, x1, y1 in pins():
        d.rounded_rectangle([*t(x0, y0), *t(x1, y1)], radius=1.2 * u * k, fill=AMBER)
    x0, y0, x1, y1, r = CHIP
    d.rounded_rectangle([*t(x0, y0), *t(x1, y1)], radius=r * u * k, fill=AMBER)
    d.polygon([t(x, y) for x, y in BOLT], fill=NAVY)
    return im.resize((size, size), Image.LANCZOS)

if __name__ == "__main__":
    static = sys.argv[1]
    open(os.path.join(static, "favicon.svg"), "w", newline="\n").write(svg("rounded"))
    open(os.path.join(static, "images", "logo-mark.svg"), "w", newline="\n").write(svg(None))
    raster(180, "square").save(os.path.join(static, "apple-touch-icon.png"), optimize=True)
    raster(192, "square").save(os.path.join(static, "images", "icon-192.png"), optimize=True)
    raster(512, "square").save(os.path.join(static, "images", "icon-512.png"), optimize=True)
    raster(256, "rounded").save(os.path.join(static, "favicon.ico"), sizes=[(16, 16), (32, 32), (48, 48)])
    print("written")
