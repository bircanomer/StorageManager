#!/usr/bin/env python3
"""Play Store ikonunu (512x512) uygulamanın gerçek uyarlanabilir simgesinden üretir.

`res/drawable/ic_launcher_background.xml` + `ic_launcher_foreground.xml` vektörleri
SVG'ye çevrilir ve macOS'un Quick Look motoruyla rasterleştirilir. Böylece mağaza
ikonu her zaman cihazdaki simgenin aynısı olur; elle çizilen ikinci bir kopya yoktur.
"""

import argparse
import subprocess
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DRAWABLE = ROOT / "app/src/main/res/drawable"
OUTPUT = ROOT / "play-store/assets/app_icon_512.png"
ANDROID = "{http://schemas.android.com/apk/res/android}"
AAPT = "{http://schemas.android.com/aapt}"
# Başlatıcıların gösterdiği alan 108dp tuvalin ortasındaki 72dp'lik karedir.
MASK = 72.0
CANVAS = 108.0


def color(value: str) -> tuple[str, float]:
    """#AARRGGBB veya #RRGGBB → (SVG rengi, opaklık)."""
    value = value.lstrip("#")
    if len(value) == 8:
        alpha, rgb = int(value[:2], 16) / 255, value[2:]
    else:
        alpha, rgb = 1.0, value
    return f"#{rgb}", alpha


def gradient(node: ET.Element, index: int) -> tuple[str, str]:
    """VectorDrawable gradient'ini SVG <linearGradient>/<radialGradient> olarak döndürür."""
    kind = node.get(f"{ANDROID}type", "linear")
    stops = []
    for item in node.findall("item"):
        rgb, alpha = color(item.get(f"{ANDROID}color"))
        stops.append(
            f'<stop offset="{item.get(f"{ANDROID}offset")}" stop-color="{rgb}" stop-opacity="{alpha}"/>'
        )
    body = "".join(stops)
    name = f"grad{index}"
    if kind == "radial":
        cx, cy = node.get(f"{ANDROID}centerX", "0"), node.get(f"{ANDROID}centerY", "0")
        radius = node.get(f"{ANDROID}gradientRadius", "1")
        return name, (
            f'<radialGradient id="{name}" gradientUnits="userSpaceOnUse" '
            f'cx="{cx}" cy="{cy}" r="{radius}">{body}</radialGradient>'
        )
    return name, (
        f'<linearGradient id="{name}" gradientUnits="userSpaceOnUse" '
        f'x1="{node.get(f"{ANDROID}startX", "0")}" y1="{node.get(f"{ANDROID}startY", "0")}" '
        f'x2="{node.get(f"{ANDROID}endX", "0")}" y2="{node.get(f"{ANDROID}endY", "0")}">{body}</linearGradient>'
    )


def convert(path: Path, defs: list[str], counter: list[int]) -> str:
    shapes = []
    for element in ET.parse(path).getroot().findall("path"):
        attrs = [f'd="{element.get(f"{ANDROID}pathData")}"']
        for kind, svg_paint, svg_alpha in (("fill", "fill", "fill-opacity"),
                                           ("stroke", "stroke", "stroke-opacity")):
            literal = element.get(f"{ANDROID}{kind}Color")
            nested = element.find(f'{AAPT}attr[@name="android:{kind}Color"]/gradient')
            if nested is not None:
                counter[0] += 1
                name, markup = gradient(nested, counter[0])
                defs.append(markup)
                attrs.append(f'{svg_paint}="url(#{name})"')
            elif literal:
                rgb, alpha = color(literal)
                attrs.append(f'{svg_paint}="{rgb}"')
                if alpha < 1:
                    attrs.append(f'{svg_alpha}="{alpha}"')
            else:
                attrs.append(f'{svg_paint}="none"')
            explicit = element.get(f"{ANDROID}{kind}Alpha")
            if explicit:
                attrs.append(f'{svg_alpha}="{explicit}"')
        if element.get(f"{ANDROID}strokeWidth"):
            attrs.append(f'stroke-width="{element.get(f"{ANDROID}strokeWidth")}"')
        if element.get(f"{ANDROID}strokeLineCap"):
            attrs.append(f'stroke-linecap="{element.get(f"{ANDROID}strokeLineCap")}"')
        shapes.append("<path " + " ".join(attrs) + "/>")
    return "".join(shapes)


def build_svg(full_canvas: bool) -> str:
    defs: list[str] = []
    counter = [0]
    layers = "".join(
        convert(DRAWABLE / f"ic_launcher_{layer}.xml", defs, counter)
        for layer in ("background", "foreground")
    )
    if full_canvas:
        box = f"0 0 {CANVAS} {CANVAS}"
    else:
        offset = (CANVAS - MASK) / 2
        box = f"{offset} {offset} {MASK} {MASK}"
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="{box}">'
        f"<defs>{''.join(defs)}</defs>{layers}</svg>"
    )


def render_icon(size: int = 512, full_canvas: bool = False) -> bytes:
    """Uyarlanabilir simgeyi verilen boyutta PNG baytları olarak döndürür."""
    with tempfile.TemporaryDirectory() as tmp:
        source = Path(tmp) / "icon.svg"
        source.write_text(build_svg(full_canvas), encoding="utf-8")
        subprocess.run(["qlmanage", "-t", "-s", str(size), "-o", tmp, str(source)],
                       check=True, capture_output=True)
        rendered = Path(tmp) / "icon.svg.png"
        if not rendered.exists():
            raise SystemExit("Quick Look SVG'yi dönüştüremedi")
        return rendered.read_bytes()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=OUTPUT)
    parser.add_argument("--full-canvas", action="store_true",
                        help="108dp tuvalin tamamını al (varsayılan: başlatıcı maskesi olan 72dp)")
    args = parser.parse_args()

    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(render_icon(512, args.full_canvas))
    print(f"{args.out} yazıldı")


if __name__ == "__main__":
    main()
