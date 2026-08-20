#!/usr/bin/env python3
"""Play Console mağaza listelemesini (metin + telefon görselleri) API üzerinden günceller.

Yalnızca listeleme güncellenir; sürüm/APK yüklenmez. Varsayılan olarak kuru çalışma
yapar — gerçekten yazmak için `--commit` gerekir.

    python3 scripts/upload_play_store_listing.py --key /yol/servis-hesabi.json
    python3 scripts/upload_play_store_listing.py --key ... --commit

Servis hesabının Play Console'da bu uygulamaya "Mağaza girişini düzenle" yetkisiyle
davet edilmiş olması gerekir; aksi halde API 403 döner.
"""

import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
METADATA = ROOT / "play-store" / "metadata"
SCREENSHOTS = ROOT / "play-store" / "screenshots-9x16"
PACKAGE = "com.storagemanager"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def listing_for(locale: str) -> dict:
    read = lambda name: (METADATA / locale / name).read_text(encoding="utf-8").strip()
    return {
        "language": locale,
        "title": read("title.txt"),
        "shortDescription": read("short_description.txt"),
        "fullDescription": read("full_description.txt"),
    }


def screenshots_for(locale: str) -> list[Path]:
    folder = SCREENSHOTS / locale / "phoneScreenshots"
    return sorted(folder.glob("*.png")) if folder.is_dir() else []


ASSETS = {
    "icon": "app_icon_512.png",
    "featureGraphic": "feature_graphic_1024x500.png",
}


def upload_asset(args, image_type: str) -> None:
    """Mağaza görselini yalnızca varsayılan dile yükler; diğer diller bunu devralır."""
    asset = ROOT / "play-store" / "assets" / ASSETS[image_type]
    print(f"{image_type}: {asset.name} ({asset.stat().st_size // 1024} KB)")
    if not args.commit:
        print("Kuru çalışma — hiçbir şey gönderilmedi. Yayınlamak için --commit ekleyin.")
        return
    if not args.key:
        raise SystemExit("--commit için --key zorunlu")

    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload

    credentials = service_account.Credentials.from_service_account_file(args.key, scopes=[SCOPE])
    service = build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)
    edits = service.edits()
    edit_id = edits.insert(body={}, packageName=args.package).execute()["id"]
    try:
        language = edits.details().get(
            packageName=args.package, editId=edit_id).execute()["defaultLanguage"]
        edits.images().deleteall(packageName=args.package, editId=edit_id,
                                 language=language, imageType=image_type).execute()
        edits.images().upload(
            packageName=args.package, editId=edit_id, language=language, imageType=image_type,
            media_body=MediaFileUpload(str(asset), mimetype="image/png")).execute()
        edits.validate(packageName=args.package, editId=edit_id).execute()
        edits.commit(packageName=args.package, editId=edit_id).execute()
        print(f"{image_type} yayınlandı ({language})")
    except Exception:
        edits.delete(packageName=args.package, editId=edit_id).execute()
        print("hata oluştu — düzenleme iptal edildi, mağazada hiçbir şey değişmedi")
        raise


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--key", help="Servis hesabı JSON dosyası")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--commit", action="store_true", help="Değişiklikleri gerçekten yayınla")
    parser.add_argument("--text-only", action="store_true", help="Görselleri atla, yalnızca metin")
    parser.add_argument("--icon-only", action="store_true",
                        help="Yalnızca 512x512 mağaza ikonunu yükle (metin ve ekran görüntülerine dokunmaz)")
    parser.add_argument("--feature-only", action="store_true",
                        help="Yalnızca 1024x500 öne çıkan görseli yükle")
    args = parser.parse_args()

    if args.icon_only or args.feature_only:
        upload_asset(args, "icon" if args.icon_only else "featureGraphic")
        return

    locales = sorted(p.name for p in METADATA.iterdir() if p.is_dir())
    plan = [(loc, listing_for(loc), screenshots_for(loc)) for loc in locales]

    total_shots = sum(len(shots) for _, _, shots in plan)
    print(f"{len(plan)} yerel ayar, {0 if args.text_only else total_shots} telefon görseli")
    missing = [loc for loc, _, shots in plan if not shots]
    if missing and not args.text_only:
        print("UYARI — görseli olmayan yerel ayarlar:", " ".join(missing))

    if not args.commit:
        for loc, listing, shots in plan[:3]:
            print(f"  {loc}: {listing['title']} | {listing['shortDescription'][:48]}… | {len(shots)} görsel")
        print(f"  … ve {len(plan) - 3} yerel ayar daha")
        print("Kuru çalışma — hiçbir şey gönderilmedi. Yayınlamak için --commit ekleyin.")
        return

    if not args.key:
        raise SystemExit("--commit için --key zorunlu")

    from google.oauth2 import service_account
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaFileUpload

    credentials = service_account.Credentials.from_service_account_file(args.key, scopes=[SCOPE])
    service = build("androidpublisher", "v3", credentials=credentials, cache_discovery=False)
    edits = service.edits()
    edit_id = edits.insert(body={}, packageName=args.package).execute()["id"]
    print(f"düzenleme açıldı: {edit_id}")

    try:
        for loc, listing, shots in plan:
            # Mevcut listelemedeki tanıtım videosu korunur: listings.update tüm kaydı değiştirir.
            try:
                current = edits.listings().get(
                    packageName=args.package, editId=edit_id, language=loc).execute()
                if current.get("video"):
                    listing["video"] = current["video"]
            except Exception:
                pass  # yerel ayar henüz yoksa yeni oluşturulur

            edits.listings().update(
                packageName=args.package, editId=edit_id, language=loc, body=listing).execute()

            if not args.text_only and shots:
                edits.images().deleteall(
                    packageName=args.package, editId=edit_id,
                    language=loc, imageType="phoneScreenshots").execute()
                for shot in shots:
                    edits.images().upload(
                        packageName=args.package, editId=edit_id, language=loc,
                        imageType="phoneScreenshots",
                        media_body=MediaFileUpload(str(shot), mimetype="image/png")).execute()
            print(f"  ✓ {loc} ({len(shots) if not args.text_only else 0} görsel)")

        edits.validate(packageName=args.package, editId=edit_id).execute()
        edits.commit(packageName=args.package, editId=edit_id).execute()
        print("yayınlandı")
    except Exception:
        edits.delete(packageName=args.package, editId=edit_id).execute()
        print("hata oluştu — düzenleme iptal edildi, mağazada hiçbir şey değişmedi")
        raise


if __name__ == "__main__":
    main()
