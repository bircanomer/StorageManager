#!/usr/bin/env python3
"""Derlenmiş .aab dosyasını bir Play test/yayın kanalına yükler.

Varsayılan olarak dahili test kanalına gider ve kuru çalışma yapar; gerçekten
yayınlamak için `--commit` gerekir.

    python3 scripts/upload_play_store_release.py --bundle app/build/outputs/bundle/release/app-release.aab
    python3 scripts/upload_play_store_release.py --bundle ... --key ... --commit
"""

import argparse
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NOTES = ROOT / "play-store" / "release-notes"
PACKAGE = "com.storagemanager"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def retry(call, attempts: int = 5):
    """Play API ara sıra 503 döndürüyor; geçici hatalarda artan aralıkla yeniden dener."""
    from googleapiclient.errors import HttpError

    for attempt in range(1, attempts + 1):
        try:
            return call.execute()
        except HttpError as error:
            if error.status_code not in (429, 500, 502, 503, 504) or attempt == attempts:
                raise
            wait = 2 ** attempt
            print(f"  geçici hata {error.status_code}, {wait} sn sonra yeniden denenecek "
                  f"({attempt}/{attempts - 1})")
            time.sleep(wait)


def release_notes() -> list[dict]:
    if not NOTES.is_dir():
        return []
    return [
        {"language": note.stem, "text": note.read_text(encoding="utf-8").strip()}
        for note in sorted(NOTES.glob("*.txt"))
    ]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--bundle", type=Path, required=True)
    parser.add_argument("--key")
    parser.add_argument("--package", default=PACKAGE)
    parser.add_argument("--track", default="internal",
                        help="internal | alpha | beta | production (varsayılan: internal)")
    parser.add_argument("--commit", action="store_true")
    args = parser.parse_args()

    if not args.bundle.exists():
        raise SystemExit(f"paket bulunamadı: {args.bundle}")
    notes = release_notes()
    print(f"paket: {args.bundle.name} ({args.bundle.stat().st_size // 1024 // 1024} MB)")
    print(f"kanal: {args.track} | sürüm notu dili: {', '.join(n['language'] for n in notes) or 'yok'}")

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
    edit_id = retry(edits.insert(body={}, packageName=args.package))["id"]
    try:
        bundle = retry(edits.bundles().upload(
            packageName=args.package, editId=edit_id,
            media_body=MediaFileUpload(str(args.bundle), mimetype="application/octet-stream",
                                       resumable=True)))
        version = bundle["versionCode"]
        print(f"yüklendi: versionCode {version}")

        retry(edits.tracks().update(
            packageName=args.package, editId=edit_id, track=args.track,
            body={"releases": [{
                "versionCodes": [str(version)],
                "status": "completed",
                **({"releaseNotes": notes} if notes else {}),
            }]}))

        retry(edits.validate(packageName=args.package, editId=edit_id))
        retry(edits.commit(packageName=args.package, editId=edit_id))
        print(f"{args.track} kanalında yayınlandı (versionCode {version})")
    except Exception:
        try:
            retry(edits.delete(packageName=args.package, editId=edit_id), attempts=2)
        except Exception:
            pass  # düzenleme commit edilmediği sürece kendiliğinden geçersiz olur
        print("hata oluştu — düzenleme iptal edildi, mağazada hiçbir şey değişmedi")
        raise


if __name__ == "__main__":
    main()
