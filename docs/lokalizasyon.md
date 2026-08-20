# Lokalizasyon

Uygulama **73 dilde** yayınlanıyor, Play Store listelemesi ise Play Console'un desteklediği
**87 yerel ayarın tamamını** kapsıyor. Varsayılan dil **İngilizce**dir (`res/values/strings.xml`);
eksik bir anahtar olursa Android otomatik olarak buraya düşer.

Uygulama dili ile mağaza yerel ayarı birebir aynı değildir: Play, bölgesel varyantları da
(en-GB, es-419, fr-CA, fa-AF, ms-MY, zh-HK…) ayrı listeleme dili sayar. Bu varyantlar için ayrı
çeviri tutulmaz; ana dilin metni kullanılır. Eşleme tek yerde tanımlıdır:
`scripts/generate_play_store_metadata.py` içindeki `LOCALES` sözlüğü ve
`scripts/capture_play_store_screenshots.sh` içindeki `MAP` bloğu.

## Desteklenen diller

| Klasör | Etiket | Dil |
|---|---|---|
| `values` | `en` | English |
| `values-af` | `af` | Afrikaans |
| `values-sq` | `sq` | Shqip |
| `values-am` | `am` | አማርኛ |
| `values-ar` | `ar` | العربية |
| `values-hy` | `hy` | Հայերեն |
| `values-az` | `az` | Azərbaycan |
| `values-eu` | `eu` | Euskara |
| `values-be` | `be` | Беларуская |
| `values-bn` | `bn` | বাংলা |
| `values-bg` | `bg` | Български |
| `values-my` | `my` | မြန်မာ |
| `values-ca` | `ca` | Català |
| `values-zh-rCN` | `zh-CN` | 简体中文 |
| `values-zh-rTW` | `zh-TW` | 繁體中文 |
| `values-hr` | `hr` | Hrvatski |
| `values-cs` | `cs` | Čeština |
| `values-da` | `da` | Dansk |
| `values-nl` | `nl` | Nederlands |
| `values-et` | `et` | Eesti |
| `values-b+fil` | `fil` | Filipino |
| `values-fi` | `fi` | Suomi |
| `values-fr` | `fr` | Français |
| `values-gl` | `gl` | Galego |
| `values-ka` | `ka` | ქართული |
| `values-de` | `de` | Deutsch |
| `values-el` | `el` | Ελληνικά |
| `values-gu` | `gu` | ગુજરાતી |
| `values-iw` | `he` | עברית |
| `values-hi` | `hi` | हिन्दी |
| `values-hu` | `hu` | Magyar |
| `values-is` | `is` | Íslenska |
| `values-in` | `id` | Bahasa Indonesia |
| `values-it` | `it` | Italiano |
| `values-ja` | `ja` | 日本語 |
| `values-kn` | `kn` | ಕನ್ನಡ |
| `values-kk` | `kk` | Қазақ |
| `values-km` | `km` | ខ្មែរ |
| `values-ko` | `ko` | 한국어 |
| `values-ky` | `ky` | Кыргызча |
| `values-lo` | `lo` | ລາວ |
| `values-lv` | `lv` | Latviešu |
| `values-lt` | `lt` | Lietuvių |
| `values-mk` | `mk` | Македонски |
| `values-ms` | `ms` | Bahasa Melayu |
| `values-ml` | `ml` | മലയാളം |
| `values-mr` | `mr` | मराठी |
| `values-mn` | `mn` | Монгол |
| `values-ne` | `ne` | नेपाली |
| `values-nb` | `nb` | Norsk bokmål |
| `values-fa` | `fa` | فارسی |
| `values-pl` | `pl` | Polski |
| `values-pt-rBR` | `pt-BR` | Português (Brasil) |
| `values-pt-rPT` | `pt-PT` | Português (Portugal) |
| `values-pa` | `pa` | ਪੰਜਾਬੀ |
| `values-ro` | `ro` | Română |
| `values-rm` | `rm` | Rumantsch |
| `values-ru` | `ru` | Русский |
| `values-sr` | `sr` | Српски |
| `values-si` | `si` | සිංහල |
| `values-sk` | `sk` | Slovenčina |
| `values-sl` | `sl` | Slovenščina |
| `values-es` | `es` | Español |
| `values-sw` | `sw` | Kiswahili |
| `values-sv` | `sv` | Svenska |
| `values-ta` | `ta` | தமிழ் |
| `values-te` | `te` | తెలుగు |
| `values-th` | `th` | ไทย |
| `values-tr` | `tr` | Türkçe |
| `values-uk` | `uk` | Українська |
| `values-ur` | `ur` | اردو |
| `values-vi` | `vi` | Tiếng Việt |
| `values-zu` | `zu` | isiZulu |

> Üç klasör adı Android'in eski dil kodlarını kullanır: İbranice `values-iw`, Endonezce
> `values-in`, Filipince `values-b+fil` (üç harfli kod `b+` ön ekiyle yazılmalıdır).
> `locales_config.xml` ve `AppLanguage` modern etiketleri (`he`, `id`, `fil`) kullanır;
> Android bunları çalışma zamanında yukarıdaki klasörlere çözer. `AppLanguage.current()`
> içindeki `normalize()` de ters yönü (`iw` → `he`) ele alır.

## Yeni dil eklerken

1. `res/values-XX/strings.xml` oluştur; `res/values/strings.xml` içindeki **tüm** anahtarları çevir.
   Üç harfli kodlar `values-b+xxx` biçiminde yazılır (ör. Filipince `values-b+fil`).
2. `res/xml/locales_config.xml` dosyasına `<locale android:name="XX" />` satırını ekle.
3. `ui/settings/AppLanguage.kt` enum'una dili **kendi dilindeki adıyla** ekle
   (kullanıcı yanlış dile düşerse kendi dilini listede tanıyabilmeli).
4. Mağaza tarafında iki eşleme güncellenir: `scripts/generate_play_store_metadata.py` içindeki
   `LOCALES` ve `scripts/capture_play_store_screenshots.sh` içindeki `MAP`. Dilin Play'de birden
   çok bölgesel varyantı varsa (ör. `es-419`, `es-US`) hepsi aynı klasöre bağlanır.
5. Aşağıdaki doğrulamayı çalıştır; ardından "Play Store varlıklarını üretme" adımlarını işlet.

## Doğrulama

Anahtar eşleşmesi ve format belirteçlerini kontrol eden komut:

```bash
python3 - <<'EOF'
import re, glob, os, xml.etree.ElementTree as ET
load = lambda p: {e.get('name'): ''.join(e.itertext())
                  for e in ET.parse(p).getroot().findall('string')
                  if e.get('translatable') != 'false'}
base = load('app/src/main/res/values/strings.xml')
spec = re.compile(r'%(\d+)\$[a-z]')
for path in sorted(glob.glob('app/src/main/res/values-*/strings.xml')):
    loc, tr = os.path.basename(os.path.dirname(path)), load(path)
    for k in sorted(set(base) - set(tr)): print(loc, 'EKSİK', k)
    for k in sorted(set(tr) - set(base)): print(loc, 'FAZLA', k)
    for k in set(tr) & set(base):
        if sorted(spec.findall(tr[k])) != sorted(spec.findall(base[k])):
            print(loc, 'FORMAT', k)
EOF
```

Android Lint de `MissingTranslation` / `ExtraTranslation` / `StringFormat` kurallarını denetler:

```bash
./gradlew :app:lintDebug
```

## Her ekranı her dilde görsel olarak test etme

Her ekranın `storagemanager://screen/<rota>` deep link'i vardır. Aşağıdaki betik
tüm diller × 12 ekran için ekran görüntüsü alır:

```bash
ADB=~/Library/Android/sdk/platform-tools/adb
PKG=com.storagemanager.debug
LOCALES=$(sed -n 's/.*android:name="\([^"]*\)".*/\1/p' app/src/main/res/xml/locales_config.xml)
for loc in $LOCALES; do
  $ADB shell cmd locale set-app-locales $PKG --locales "$loc"
  for scr in onboarding dashboard photos apps cache files downloads system_junk trash treemap settings paywall; do
    $ADB shell am force-stop $PKG
    $ADB shell am start -a android.intent.action.VIEW -d "storagemanager://screen/$scr" -p $PKG
    sleep 4
    $ADB exec-out screencap -p > "shots/${loc}__${scr}.png"
  done
done
```

## Play Store varlıklarını üretme

Mağaza metinleri uygulamanın kendi çevirilerinden türetilir; ayrı bir çeviri havuzu yoktur.
Bir metin değişince sırayla:

```bash
python3 scripts/generate_play_store_metadata.py      # 87 yerel ayar → play-store/metadata/
./scripts/capture_play_store_screenshots.sh          # 73 dil çekilir, 87 klasöre kopyalanır
python3 scripts/make_9x16_screenshots.py             # Play'in kabul ettiği 9:16 çerçeve
```

Notlar:

- Çekim betiği ilk argüman olarak cihaz/emülatör serisi alır; verilmezse ilk cihazı kullanır.
  Yalnızca birkaç dili yenilemek için: `LOCALES="tr-TR de-DE" ./scripts/capture_play_store_screenshots.sh`.
- Ekran uykudayken `screencap` 0 baytlık dosya döndürür; betik bu yüzden cihazı uyandırıp
  `svc power stayon usb` ile uyanık tutar.
- `am start -W` bazı cihazlarda (ör. Meta Quest) hiç dönmediği için kullanılmaz; yerine sabit
  bekleme vardır. Telefon oranında görsel için telefon AVD'si kullanın.
- Play, 9:16'dan uzun görselleri kabul etmez. Cihaz 1080×2340 çekiyorsa `make_9x16_screenshots.py`
  görüntüyü oranını bozmadan 1080×1920 tuvale yerleştirir; yüklenecek klasör
  `play-store/screenshots-9x16/`.

## Yerelleştirmede kolay kaçırılan tuzaklar

Bu projede karşılaşılıp düzeltilen gerçek hatalar:

- **`lowercase()` yerel bağımlıdır.** Türkçe yerelde `"TIFF".lowercase()` → `"tıff"` olur ve
  dosya uzantısı/MIME eşleştirmesi bozulur. Teknik karşılaştırmalarda daima
  `lowercase(Locale.ROOT)` kullan.
- **Ada göre sıralama `lowercase()` ile yapılamaz.** Her dilin kendi alfabe sırası vardır;
  `ui/components/TextSorting.kt` içindeki `localeCollator()` kullanılmalıdır.
- **RTL dillerde sayı + birim ters döner.** "15.1 GB" Arapça'da "GB 15.1" olarak çizilir.
  `formatFileSize` sonucu `bidiIsolate()` ile yalıtır; yeni sayısal biçimlendirmelerde de
  aynısını yap.
- **Tarih biçimi sabitlenmemeli.** `SimpleDateFormat(..., Locale.getDefault())` kullan.
- **Yüzde işaretinin yeri dile göre değişir.** Türkçe "%50", İngilizce "50%" yazar; bu yüzden
  `percent_format` anahtarı çevrilebilir bırakıldı.
- **Türkçe metin sızıntısı.** Türkçe'ye özgü karakter içermeyen sabit metinler (ör. "uygulama")
  arama sırasında kolayca gözden kaçar; ekran görüntüsü taraması bunları yakalar.
