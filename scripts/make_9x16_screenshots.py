#!/usr/bin/env python3
"""Ham cihaz ekran görüntülerini Play Store'un istediği 9:16 çerçeveye oturtur.

Modern telefonlar 9:19.5 gibi uzun ekranlara sahiptir; Play ise 9:16'dan uzun
görselleri kabul etmez. Görüntü oranı korunarak 1080x1920 tuvale sığdırılır ve
kalan boşluk uygulamanın koyu arka planıyla doldurulur.
"""

from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "play-store" / "screenshots"
TARGET = ROOT / "play-store" / "screenshots-9x16"
CANVAS = (1080, 1920)
BACKGROUND = (18, 18, 18)

converted = 0
for source in sorted(SOURCE.rglob("*.png")):
    image = Image.open(source).convert("RGB")
    image.thumbnail(CANVAS, Image.LANCZOS)
    frame = Image.new("RGB", CANVAS, BACKGROUND)
    frame.paste(image, ((CANVAS[0] - image.width) // 2, (CANVAS[1] - image.height) // 2))
    destination = TARGET / source.relative_to(SOURCE)
    destination.parent.mkdir(parents=True, exist_ok=True)
    frame.save(destination)
    converted += 1

print(f"Converted {converted} screenshots into {TARGET}")
