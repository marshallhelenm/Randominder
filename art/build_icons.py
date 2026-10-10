"""Builds Randominder's launcher icons, notification icon and in-app logo from art/randominder_logo.png.

Pure Python (zlib only), so it runs without any image library:

    python3 -I art/build_icons.py art/randominder_logo.png app/src/main

It overwrites the generated bitmaps under <out_dir>/res. Takes about half a minute.
"""
import math
import os
import struct
import sys
import zlib

PURPLE = (0x67, 0x50, 0xA4)


# ---------- PNG I/O ----------

def read_png(path):
    d = open(path, 'rb').read()
    assert d[:8] == b'\x89PNG\r\n\x1a\n'
    i, idat, w = 8, b'', None
    while i < len(d):
        n = struct.unpack('>I', d[i:i + 4])[0]
        t, c = d[i + 4:i + 8], d[i + 8:i + 8 + n]
        i += 12 + n
        if t == b'IHDR':
            w, h, bd, ct = struct.unpack('>IIBB', c[:10])
            assert bd == 8 and ct == 6, 'expects 8-bit RGBA'
        elif t == b'IDAT':
            idat += c
    raw = zlib.decompress(idat)
    bpp, stride = 4, w * 4
    out, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[p]
        line = bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for x in range(stride):
            a = line[x - bpp] if x >= bpp else 0
            b = prev[x]
            c = prev[x - bpp] if x >= bpp else 0
            if f == 1:
                line[x] = (line[x] + a) & 255
            elif f == 2:
                line[x] = (line[x] + b) & 255
            elif f == 3:
                line[x] = (line[x] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[x] = (line[x] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out.append(line)
        prev = line
    # Premultiplied float pixels: (r, g, b, a) with r,g,b already multiplied by a, all 0..1.
    px = []
    for line in out:
        for x in range(w):
            r, g, b, a = line[x * 4:x * 4 + 4]
            af = a / 255
            px.append((r / 255 * af, g / 255 * af, b / 255 * af, af))
    return Image(w, h, px)


def write_png(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    raw = bytearray()
    for y in range(img.h):
        raw.append(0)
        for x in range(img.w):
            r, g, b, a = img.px[y * img.w + x]
            if a <= 0:
                raw += b'\0\0\0\0'
            else:
                raw += bytes((
                    min(255, round(r / a * 255)), min(255, round(g / a * 255)),
                    min(255, round(b / a * 255)), min(255, round(a * 255))))

    def chunk(t, c):
        return struct.pack('>I', len(c)) + t + c + struct.pack('>I', zlib.crc32(t + c) & 0xffffffff)

    data = (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', img.w, img.h, 8, 6, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(bytes(raw), 9)) + chunk(b'IEND', b''))
    open(path, 'wb').write(data)


# ---------- image ops (premultiplied) ----------

class Image:
    def __init__(self, w, h, px=None):
        self.w, self.h = w, h
        self.px = px if px is not None else [(0.0, 0.0, 0.0, 0.0)] * (w * h)

    def crop(self, x0, y0, x1, y1):
        return Image(x1 - x0, y1 - y0, [self.px[y * self.w + x] for y in range(y0, y1) for x in range(x0, x1)])

    def map(self, f):
        return Image(self.w, self.h, [f(p) for p in self.px])

    def resize(self, ow, oh):
        """Area-average downscale (each output pixel averages the source pixels it covers)."""
        sx, sy = self.w / ow, self.h / oh
        out = []
        for oy in range(oh):
            fy0, fy1 = oy * sy, (oy + 1) * sy
            for ox in range(ow):
                fx0, fx1 = ox * sx, (ox + 1) * sx
                acc = [0.0, 0.0, 0.0, 0.0]
                tot = 0.0
                for y in range(int(fy0), min(self.h, math.ceil(fy1))):
                    wy = min(fy1, y + 1) - max(fy0, y)
                    row = y * self.w
                    for x in range(int(fx0), min(self.w, math.ceil(fx1))):
                        wgt = wy * (min(fx1, x + 1) - max(fx0, x))
                        p = self.px[row + x]
                        acc[0] += p[0] * wgt; acc[1] += p[1] * wgt; acc[2] += p[2] * wgt; acc[3] += p[3] * wgt
                        tot += wgt
                out.append(tuple(v / tot for v in acc))
        return Image(ow, oh, out)

    def paste_over(self, top, ox, oy):
        """Source-over composite of `top` at integer offset."""
        for y in range(top.h):
            for x in range(top.w):
                tx, ty = ox + x, oy + y
                if 0 <= tx < self.w and 0 <= ty < self.h:
                    t = top.px[y * top.w + x]
                    b = self.px[ty * self.w + tx]
                    k = 1 - t[3]
                    self.px[ty * self.w + tx] = (t[0] + b[0] * k, t[1] + b[1] * k, t[2] + b[2] * k, t[3] + b[3] * k)


def solid_shape(size, colour, inside, samples=4):
    """Anti-aliased filled shape; `inside(x, y)` takes coordinates in 0..1."""
    r, g, b = (c / 255 for c in colour)
    px = []
    for y in range(size):
        for x in range(size):
            hits = sum(inside((x + (i + .5) / samples) / size, (y + (j + .5) / samples) / size)
                       for i in range(samples) for j in range(samples))
            a = hits / samples ** 2
            px.append((r * a, g * a, b * a, a))
    return Image(size, size, px)


def circle(x, y):
    return (x - .5) ** 2 + (y - .5) ** 2 <= .46 ** 2


def rounded_square(x, y, inset=.06, radius=.16):
    lo, hi = inset + radius, 1 - inset - radius
    if not (inset <= x <= 1 - inset and inset <= y <= 1 - inset):
        return False
    dx = max(lo - x, 0, x - hi)
    dy = max(lo - y, 0, y - hi)
    return dx * dx + dy * dy <= radius * radius


# ---------- artwork helpers ----------

def bounds(img, threshold=0.06):
    xs, ys = [], []
    for y in range(img.h):
        for x in range(img.w):
            if img.px[y * img.w + x][3] > threshold:
                xs.append(x); ys.append(y)
    return min(xs), min(ys), max(xs) + 1, max(ys) + 1


def enclosing_radius(img, threshold=0.06):
    x0, y0, x1, y1 = bounds(img, threshold)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    r = 0.0
    for y in range(y0, y1, 2):
        for x in range(x0, x1, 2):
            if img.px[y * img.w + x][3] > threshold:
                r = max(r, math.hypot(x - cx, y - cy))
    return cx, cy, r


def ink_mask(p):
    """White, with alpha = how dark the pixel is. Outlines and pips stay; white faces drop out."""
    r, g, b, a = p
    if a <= 0:
        return (0.0, 0.0, 0.0, 0.0)
    lum = (r + g + b) / (3 * a)  # un-premultiplied luminance 0..1
    m = a * (1 - lum)
    return (m, m, m, m)


def invert(p):
    """For dark theme: black outlines become white, white faces become near-black."""
    r, g, b, a = p
    if a <= 0:
        return p
    ur, ug, ub = r / a, g / a, b / a
    return ((1 - ur) * a, (1 - ug) * a, (1 - ub) * a, a)


def fit_centered(art, canvas, radius_px, centre=None):
    """Scales `art` so its enclosing circle has `radius_px`, centred on a transparent canvas."""
    cx, cy, r = enclosing_radius(art)
    scale = radius_px / r
    x0, y0, x1, y1 = bounds(art)
    region = art.crop(x0, y0, x1, y1)
    ow, oh = max(1, round(region.w * scale)), max(1, round(region.h * scale))
    small = region.resize(ow, oh)
    out = Image(canvas, canvas)
    c = canvas / 2 if centre is None else centre
    out.paste_over(small, round(c - ((cx - x0) * scale)), round(c - ((cy - y0) * scale)))
    return out


def fit_box(art, canvas, box_px):
    """Scales `art`'s bounding box to fit a centred `box_px` square."""
    x0, y0, x1, y1 = bounds(art)
    region = art.crop(x0, y0, x1, y1)
    scale = box_px / max(region.w, region.h)
    small = region.resize(max(1, round(region.w * scale)), max(1, round(region.h * scale)))
    out = Image(canvas, canvas)
    out.paste_over(small, (canvas - small.w) // 2, (canvas - small.h) // 2)
    return out


DENSITIES = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}


def main(src, out):
    art = read_png(src)
    res = os.path.join(out, 'res')

    # Adaptive icon layers: 108dp canvas, everything inside the 66dp safe circle (radius 33dp).
    for name, dens in DENSITIES.items():
        fg = fit_centered(art, round(108 * dens), 33 * dens)
        write_png(fg, f'{res}/drawable-{name}/ic_launcher_foreground.png')
        write_png(fg.map(ink_mask), f'{res}/drawable-{name}/ic_launcher_monochrome.png')

    # Pre-Android 8 launchers show these bitmaps as-is.
    for name, dens in DENSITIES.items():
        size = round(48 * dens)
        for file, shape in (('ic_launcher', rounded_square), ('ic_launcher_round', circle)):
            icon = solid_shape(size, PURPLE, shape)
            icon.paste_over(fit_centered(art, size, size * .36), 0, 0)
            write_png(icon, f'{res}/mipmap-{name}/{file}.png')

    # Status bar glyph: 24dp with ~1dp padding. Android only uses its alpha, so it's drawn in white.
    for name, dens in DENSITIES.items():
        size = round(24 * dens)
        write_png(fit_box(art, size, round(22 * dens)).map(ink_mask),
                  f'{res}/drawable-{name}/ic_notification_logo.png')

    # In-app logo, 640px wide (160dp at xxxhdpi), plus an inverted copy for dark theme.
    x0, y0, x1, y1 = bounds(art)
    region = art.crop(x0, y0, x1, y1)
    logo = region.resize(640, round(region.h * 640 / region.w))
    write_png(logo, f'{res}/drawable-nodpi/logo.png')
    write_png(logo.map(invert), f'{res}/drawable-night-nodpi/logo.png')


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
