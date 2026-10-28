"""Camp Nou at night: a stylised stadium wallpaper in blaugrana, drawn in code (no photo, no crest)."""
import sys
import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H = 1920, 1080
OUT = sys.argv[1] if len(sys.argv) > 1 else "wallpaper.jpg"
rng = np.random.default_rng(7)

BLUE = np.array([0, 77, 152], float)
GARNET = np.array([165, 0, 68], float)
GOLD = np.array([237, 187, 0], float)

yy, xx = np.mgrid[0:H, 0:W].astype(float)

# Sky: deep navy, a little lighter toward the horizon where the floodlights spill.
sky_t = np.clip(yy / 520.0, 0, 1)[..., None]
img = (np.array([3, 6, 18]) * (1 - sky_t) + np.array([14, 22, 52]) * sky_t)
# Stars in the upper sky.
for _ in range(220):
    sx, sy = rng.integers(0, W), rng.integers(0, 330)
    b = rng.uniform(60, 190)
    img[sy, sx] = np.maximum(img[sy, sx], [b, b, b * 1.05])

# Stadium geometry: a bowl seen from high behind one goal.
cx, cy = W * 0.5, 690.0
def ell(rx, ry, cyy=cy):
    return ((xx - cx) / rx) ** 2 + ((yy - cyy) / ry) ** 2

outer = ell(1150, 470, 700)     # outer facade edge
rim = ell(1080, 420, 700)       # roof rim
stand_out = ell(1020, 380, 705)
stand_in = ell(700, 200, 735)   # inner edge of the stands = pitch surround
pitch_mask = ell(640, 170, 740) <= 1

# Facade and roof rim: dark concrete.
img = np.where((outer <= 1)[..., None], np.array([10, 12, 24], float), img)
img = np.where((rim <= 1)[..., None], np.array([18, 20, 34], float), img)

# Stands: alternating blaugrana sectors, with seat-row texture.
stands = (stand_out <= 1) & (stand_in > 1)
ang = np.arctan2((yy - cy) / 380.0, (xx - cx) / 1020.0)
sector = (np.floor((ang + np.pi) / (np.pi / 14)) % 2)[..., None]
seat = sector * GARNET + (1 - sector) * BLUE
# Rows: radial bands make the tiers read as seating.
radial = np.sqrt(stand_out)
rows = 0.78 + 0.22 * (np.sin(radial * 120.0) > 0)
tier_gap = (np.abs(radial - 0.82) < 0.012)
noise = rng.normal(1.0, 0.10, (H, W))
seat_col = seat * (rows * noise)[..., None] * 0.62
seat_col = np.where(tier_gap[..., None], np.array([8, 10, 20], float), seat_col)
img = np.where(stands[..., None], seat_col, img)

# Pitch: a perspective trapezoid, far touchline narrower, with mown stripes lit from above.
FY, NY, FW, NW = 585.0, 880.0, 470.0, 640.0
def P(u, v):  # u in [-1, 1] across, v in [0, 1] far to near
    y = FY + (NY - FY) * v
    return (cx + u * (FW + (NW - FW) * v), y)
vv = np.clip((yy - FY) / (NY - FY), 0, 1)
half = FW + (NW - FW) * vv
uu = (xx - cx) / half
surround = (stand_in <= 1)
img = np.where(surround[..., None], np.array([14, 40, 24], float), img)
pitch_mask = (yy >= FY - 14) & (yy <= NY + 14) & (np.abs(uu) <= 1.05)
stripe = (np.floor((uu + 1.06) * 9) % 2)
grass = np.array([24, 104, 50], float) * (0.84 + 0.16 * stripe)[..., None]
light = np.clip(1.2 - (((xx - cx) / 700.0) ** 2 + ((yy - 730) / 230.0) ** 2) * 0.5, 0.6, 1.2)[..., None]
img = np.where(pitch_mask[..., None], grass * light, img)

im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
d = ImageDraw.Draw(im, "RGBA")
lw = (240, 248, 240, 185)
def poly(pts, w=3):
    d.line(pts + [pts[0]], fill=lw, width=w, joint="curve")
poly([P(-1, 0), P(1, 0), P(1, 1), P(-1, 1)])
d.line([P(0, 0), P(0, 1)], fill=lw, width=3)
circ = [P(0.17 * np.cos(t) * (1), 0.5 + 0.21 * np.sin(t)) for t in np.linspace(0, 2 * np.pi, 60)]
d.line(circ, fill=lw, width=3)
for sgn in (-1, 1):
    poly([P(sgn * 1, 0.2), P(sgn * 0.72, 0.2), P(sgn * 0.72, 0.8), P(sgn * 1, 0.8)])
    poly([P(sgn * 1, 0.37), P(sgn * 0.9, 0.37), P(sgn * 0.9, 0.63), P(sgn * 1, 0.63)])

# "CAMP NOU" across the far stand, in gold seats.
try:
    font = ImageFont.truetype("/usr/share/fonts/opentype/inter/InterDisplay-Black.otf", 92)
except OSError:
    font = ImageFont.truetype("/usr/share/fonts/opentype/inter/InterDisplay-Bold.otf", 92)
txt = "CAMP NOU"
layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
ld = ImageDraw.Draw(layer)
tw = ld.textlength(txt, font=font)
ld.text((cx - tw / 2, 392), txt, font=font, fill=(237, 187, 0, 235))
im = Image.alpha_composite(im.convert("RGBA"), layer)

# Floodlights: bright points along the roof rim, with glow.
glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
gd = ImageDraw.Draw(glow)
lamps = []
for k in range(26):
    a = np.pi + (k + 0.5) * np.pi / 26  # upper half of the rim
    lx = cx + 1065 * np.cos(a)
    ly = 700 + 412 * np.sin(a)
    if 0 <= lx < W and ly < 640:
        lamps.append((lx, ly))
for lx, ly in lamps:
    gd.ellipse([lx - 70, ly - 34, lx + 70, ly + 34], fill=(255, 244, 214, 120))
glow = glow.filter(ImageFilter.GaussianBlur(22))
beams = Image.new("RGBA", (W, H), (0, 0, 0, 0))
bd = ImageDraw.Draw(beams)
for lx, ly in lamps:
    tx = cx + (lx - cx) * 0.25
    bd.polygon([(lx - 10, ly), (lx + 10, ly), (tx + 170, 800), (tx - 170, 800)], fill=(220, 230, 255, 16))
beams = beams.filter(ImageFilter.GaussianBlur(30))
im = Image.alpha_composite(im, beams)
im = Image.alpha_composite(im, glow)
# Light haze over the pitch from the floodlights.
haze = Image.new("RGBA", (W, H), (0, 0, 0, 0))
hd = ImageDraw.Draw(haze)
hd.ellipse([cx - 900, 330, cx + 900, 900], fill=(200, 220, 255, 26))
haze = haze.filter(ImageFilter.GaussianBlur(90))
im = Image.alpha_composite(im, haze)
d = ImageDraw.Draw(im)
for lx, ly in lamps:
    d.rounded_rectangle([lx - 14, ly - 4, lx + 14, ly + 4], radius=3, fill=(255, 252, 238))

# Keep the bottom (dock) and top-left (clock) calm: soft dark falloff.
arr = np.asarray(im.convert("RGB")).astype(float)
bottom = np.clip((yy - 860) / 220.0, 0, 1)[..., None]
arr = arr * (1 - 0.65 * bottom)
corner = np.clip(1 - np.sqrt((xx / 900.0) ** 2 + (yy / 380.0) ** 2), 0, 1)[..., None]
arr = arr * (1 - 0.35 * corner)
arr += rng.normal(0, 2.0, arr.shape)
Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8)).save(OUT, quality=86, optimize=True, progressive=True)
print(OUT)
