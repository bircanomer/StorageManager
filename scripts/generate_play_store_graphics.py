#!/usr/bin/env python3
"""Play Store ikonunu ve öne çıkan görselini üretir.

Her ikisi de uygulamanın gerçek uyarlanabilir simgesinden beslenir
(`generate_play_store_icon.render_icon`), böylece mağaza görselleri cihazdaki
simgeyle asla ayrışmaz.
"""

import io
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

from generate_play_store_icon import render_icon

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "play-store" / "assets"
OUT.mkdir(parents=True, exist_ok=True)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    names = [
        "/System/Library/Fonts/SFNSDisplay-Bold.otf" if bold else "/System/Library/Fonts/SFNS.ttf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
    ]
    for name in names:
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            pass
    return ImageFont.load_default()


def gradient(size: tuple[int, int], start: tuple[int, int, int], end: tuple[int, int, int]) -> Image.Image:
    width, height = size
    image = Image.new("RGB", size)
    px = image.load()
    for y in range(height):
        for x in range(width):
            t = (x / max(width - 1, 1) + y / max(height - 1, 1)) / 2
            px[x, y] = tuple(round(a + (b - a) * t) for a, b in zip(start, end))
    return image


def icon() -> None:
    OUT.joinpath("app_icon_512.png").write_bytes(render_icon(512))


def feature() -> None:
    image = gradient((1024, 500), (28, 21, 66), (7, 5, 20)).convert("RGBA")

    glow = Image.new("RGBA", image.size)
    gd = ImageDraw.Draw(glow, "RGBA")
    gd.ellipse((500, -160, 1020, 410), fill=(0, 188, 212, 80))
    gd.ellipse((620, 110, 1090, 610), fill=(108, 99, 255, 85))
    image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(70)))

    # Simgenin kendisi: köşeleri Play'in yuvarlatmasına benzer şekilde yumuşatılır.
    mark = Image.open(io.BytesIO(render_icon(370))).convert("RGBA")
    mask = Image.new("L", mark.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, mark.width - 1, mark.height - 1),
                                           radius=round(mark.width * 0.23), fill=255)
    mark.putalpha(mask)
    image.alpha_composite(mark, (1024 - mark.width - 60, (500 - mark.height) // 2))

    draw = ImageDraw.Draw(image, "RGBA")
    draw.text((72, 145), "StorageManager", font=font(64, True), fill=(255, 255, 255, 255))
    draw.text((75, 230), "Understand your storage.", font=font(31), fill=(178, 235, 242, 255))
    draw.text((75, 275), "Clean with confidence.", font=font(31), fill=(178, 235, 242, 255))

    image.convert("RGB").save(OUT / "feature_graphic_1024x500.png", quality=96)


if __name__ == "__main__":
    icon()
    feature()
    print(OUT)
