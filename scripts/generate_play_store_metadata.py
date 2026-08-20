#!/usr/bin/env python3
"""Build localized Play Store listing copy from the app's reviewed translations."""

from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
OUT = ROOT / "play-store/metadata"

# Play Console'un desteklediği her yerel ayar → uygulamanın kaynak klasörü.
# Bölgesel varyantlar (en-GB, es-419, fr-CA, fa-AF…) ana dilin metnini kullanır:
# Play listeleme dilini ister ama uygulama için ayrı bir çeviri gerekmez.
LOCALES = {
    "af": "values-af",
    "am": "values-am",
    "ar": "values-ar",
    "az-AZ": "values-az",
    "be": "values-be",
    "bg": "values-bg",
    "bn-BD": "values-bn",
    "ca": "values-ca",
    "cs-CZ": "values-cs",
    "da-DK": "values-da",
    "de-DE": "values-de",
    "el-GR": "values-el",
    "en-AU": "values",
    "en-CA": "values",
    "en-GB": "values",
    "en-IN": "values",
    "en-SG": "values",
    "en-US": "values",
    "en-ZA": "values",
    "es-419": "values-es",
    "es-ES": "values-es",
    "es-US": "values-es",
    "et": "values-et",
    "eu-ES": "values-eu",
    "fa": "values-fa",
    "fa-AE": "values-fa",
    "fa-AF": "values-fa",
    "fa-IR": "values-fa",
    "fi-FI": "values-fi",
    "fil": "values-b+fil",
    "fr-CA": "values-fr",
    "fr-FR": "values-fr",
    "gl-ES": "values-gl",
    "gu": "values-gu",
    "hi-IN": "values-hi",
    "hr": "values-hr",
    "hu-HU": "values-hu",
    "hy-AM": "values-hy",
    "id": "values-in",
    "is-IS": "values-is",
    "it-IT": "values-it",
    "iw-IL": "values-iw",
    "ja-JP": "values-ja",
    "ka-GE": "values-ka",
    "kk": "values-kk",
    "km-KH": "values-km",
    "kn-IN": "values-kn",
    "ko-KR": "values-ko",
    "ky-KG": "values-ky",
    "lo-LA": "values-lo",
    "lt": "values-lt",
    "lv": "values-lv",
    "mk-MK": "values-mk",
    "ml-IN": "values-ml",
    "mn-MN": "values-mn",
    "mr-IN": "values-mr",
    "ms": "values-ms",
    "ms-MY": "values-ms",
    "my-MM": "values-my",
    "ne-NP": "values-ne",
    "nl-NL": "values-nl",
    "no-NO": "values-nb",
    "pa": "values-pa",
    "pl-PL": "values-pl",
    "pt-BR": "values-pt-rBR",
    "pt-PT": "values-pt-rPT",
    "rm": "values-rm",
    "ro": "values-ro",
    "ru-RU": "values-ru",
    "si-LK": "values-si",
    "sk": "values-sk",
    "sl": "values-sl",
    "sq": "values-sq",
    "sr": "values-sr",
    "sv-SE": "values-sv",
    "sw": "values-sw",
    "ta-IN": "values-ta",
    "te-IN": "values-te",
    "th": "values-th",
    "tr-TR": "values-tr",
    "uk": "values-uk",
    "ur": "values-ur",
    "vi": "values-vi",
    "zh-CN": "values-zh-rCN",
    "zh-HK": "values-zh-rTW",
    "zh-TW": "values-zh-rTW",
    "zu": "values-zu",
}


def strings(folder: str) -> dict[str, str]:
    base = {
        node.attrib["name"]: "".join(node.itertext()).strip()
        for node in ET.parse(RES / "values/strings.xml").getroot().findall("string")
    }
    if folder == "values":
        return base
    translated = {
        node.attrib["name"]: "".join(node.itertext()).strip()
        for node in ET.parse(RES / folder / "strings.xml").getroot().findall("string")
    }
    return base | translated


def clipped(text: str, limit: int) -> str:
    return text if len(text) <= limit else text[: limit - 1].rstrip(" ,.;:•-") + "…"


for locale, folder in LOCALES.items():
    s = strings(folder)
    destination = OUT / locale
    destination.mkdir(parents=True, exist_ok=True)

    title = "StorageManager"
    short = clipped(f"{s['dashboard_title']} — {s['onboarding_subtitle']}", 80)
    bullets = [
        s["onboarding_feature_photos"],
        f"{s['category_large_files']} — {s['card_large_files_subtitle']}",
        f"{s['category_apps']} — {s['card_apps_subtitle']}",
        f"{s['category_cache']} — {s['card_cache_subtitle']}",
        s["category_downloads_desc"],
        s["category_system_junk_desc"],
        s["treemap_title"],
        s["trash_title"],
    ]
    full = "\n\n".join(
        [s["onboarding_subtitle"], "\n".join(f"✓ {item}" for item in bullets), s["onboarding_privacy_note"]]
    )
    captions = "\n".join(
        [
            s["dashboard_title"],
            s["onboarding_feature_photos"],
            s["category_large_files_desc"],
            s["treemap_title"],
        ]
    )

    (destination / "title.txt").write_text(title + "\n", encoding="utf-8")
    (destination / "short_description.txt").write_text(short + "\n", encoding="utf-8")
    (destination / "full_description.txt").write_text(full + "\n", encoding="utf-8")
    (destination / "screenshot_captions.txt").write_text(captions + "\n", encoding="utf-8")

print(f"Generated {len(LOCALES)} localized listings in {OUT}")
