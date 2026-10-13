"""One-off image generator for the website (needs Pillow; build.py does not).

Writes favicons from the store icon and the 1200x630 social preview image.
Run from anywhere:  python site/tools/make_assets.py
The outputs are committed, so this only needs re-running when the icon or
the og:image design changes.
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[2]
SITE = ROOT / "site"
ICON_SRC = ROOT / "fastlane/metadata/android/en-US/images/icon.png"
FONT_DIR = ROOT / "app/src/main/res/font"
ICONS = SITE / "assets/icons"

CHROME = (12, 14, 16)
INK_LIGHT = (245, 247, 248)
MUTED = (154, 160, 166)
VERIFIED = (86, 203, 152)
GLASS = (27, 30, 34)
OUTLINE = (51, 56, 61)


def favicons():
    ICONS.mkdir(parents=True, exist_ok=True)
    src = Image.open(ICON_SRC).convert("RGBA")
    for size, name in [(32, "favicon-32.png"), (180, "apple-touch-icon.png"),
                       (192, "icon-192.png"), (512, "icon-512.png")]:
        img = src.resize((size, size), Image.LANCZOS)
        if name == "apple-touch-icon.png":
            img = img.convert("RGB")  # iOS ignores alpha
        img.save(ICONS / name, optimize=True)


def font(name, size):
    return ImageFont.truetype(str(FONT_DIR / f"{name}.ttf"), size)


def brand_mark(draw, x, y, s, frame=INK_LIGHT):
    """The lens-frame-and-aperture-dot mark, drawn in a s x s box."""
    k = s / 200.0
    draw.rounded_rectangle([x + 25 * k, y + 25 * k, x + 175 * k, y + 175 * k],
                           radius=50 * k, fill=frame)
    draw.rounded_rectangle([x + 39 * k, y + 39 * k, x + 161 * k, y + 161 * k],
                           radius=36 * k, fill=CHROME)
    r = 18.75 * k
    cx, cy = x + 100 * k, y + 100 * k
    draw.ellipse([cx - r, cy - r, cx + r, cy + r], fill=VERIFIED)


def og_image():
    W, H = 1200, 630
    scale = 2  # draw at 2x, downsample for smooth edges
    img = Image.new("RGB", (W * scale, H * scale), CHROME)
    d = ImageDraw.Draw(img)
    S = lambda v: int(v * scale)

    # soft green glow, top right
    glow = Image.new("L", (S(900), S(900)), 0)
    gd = ImageDraw.Draw(glow)
    for i in range(60):
        r = S(450) - i * S(7)
        gd.ellipse([S(450) - r, S(450) - r, S(450) + r, S(450) + r], fill=int(i * 0.55))
    tint = Image.new("RGB", glow.size, (24, 74, 54))
    img.paste(tint, (S(700), S(-380)), glow)

    brand_mark(d, S(72), S(64), S(84))
    d.text((S(172), S(86)), "GeoTag Camera", font=font("poppins_semibold", S(36)), fill=INK_LIGHT)

    d.text((S(72), S(200)), "Every photo, exactly where", font=font("poppins_light", S(50)), fill=INK_LIGHT)
    d.text((S(72), S(268)), "and when it happened.", font=font("poppins_light", S(50)), fill=INK_LIGHT)
    d.text((S(72), S(360)), "GPS, address and time stamped on the photo,",
           font=font("poppins_regular", S(25)), fill=MUTED)
    d.text((S(72), S(396)), "signed on the phone so edits show up.",
           font=font("poppins_regular", S(25)), fill=MUTED)

    # pills
    x = S(72)
    pf = font("roboto_mono_medium", S(19))
    for label in ["NO ADS", "NO TRACKING", "WORKS OFFLINE", "GPL-3.0"]:
        tw = d.textlength(label, font=pf)
        d.rounded_rectangle([x, S(470), x + tw + S(36), S(514)], radius=S(22),
                            outline=OUTLINE, width=S(2), fill=GLASS)
        d.text((x + S(18), S(480)), label, font=pf, fill=INK_LIGHT)
        x += tw + S(50)

    d.text((S(72), S(560)), "konkomaji.com/geotagcamera", font=font("roboto_mono_regular", S(20)), fill=VERIFIED)

    # mock stamp card on the right
    cx0, cy0, cx1, cy1 = S(800), S(170), S(1140), S(420)
    d.rounded_rectangle([cx0, cy0, cx1, cy1], radius=S(18), fill=GLASS, outline=OUTLINE, width=S(2))
    mono = font("roboto_mono_regular", S(17))
    monob = font("roboto_mono_medium", S(17))
    d.text((cx0 + S(22), cy0 + S(20)), "Kolkata, West Bengal  IN", font=font("poppins_semibold", S(20)), fill=INK_LIGHT)
    d.text((cx0 + S(22), cy0 + S(62)), "22.572600 N  88.363900 E", font=mono, fill=INK_LIGHT)
    d.text((cx0 + S(22), cy0 + S(94)), "14 Oct 2026 10:42 GMT+05:30", font=mono, fill=INK_LIGHT)
    d.text((cx0 + S(22), cy0 + S(126)), "acc 4 m   alt 9 m   brg 312", font=mono, fill=MUTED)
    # signed chip
    d.rounded_rectangle([cx0 + S(22), cy0 + S(172), cx0 + S(230), cy0 + S(214)], radius=S(21), fill=(15, 74, 52))
    d.ellipse([cx0 + S(38), cy0 + S(186), cx0 + S(52), cy0 + S(200)], fill=VERIFIED)
    d.text((cx0 + S(64), cy0 + S(182)), "SIGNED SHA-256", font=monob, fill=(190, 242, 217))

    img = img.resize((W, H), Image.LANCZOS)
    img.save(SITE / "assets/og-image.png", optimize=True)


if __name__ == "__main__":
    favicons()
    og_image()
    print("icons and og-image written")
