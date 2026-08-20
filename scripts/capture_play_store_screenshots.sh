#!/usr/bin/env bash
set -euo pipefail

# Play Store'un desteklediği her yerel ayar için mağaza görselleri üretir.
#
# Uygulama 73 dile çevrilidir, Play listelemesi ise 87 yerel ayar ister; bölgesel
# varyantlar (en-GB, es-419, fr-CA, fa-AF…) aynı uygulama diline düştüğü için bir kez
# yakalanıp kopyalanır. Böylece cihazda 73 × 4 kare çekilir, 87 klasör doldurulur.

ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
SERIAL="${1:-$("${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb" devices | awk 'NR==2{print $1}')}"
PKG="com.storagemanager.debug"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
routes=(dashboard photos files treemap)
COLD_WAIT="${COLD_WAIT:-22}"   # dil başına ilk ekran (uygulama soğuk açılıyor)
WARM_WAIT="${WARM_WAIT:-8}"    # aynı süreçte gezinme

# "<mağaza yerel ayarı> <uygulama dili>" — sıra Play Console listesiyle aynıdır.
read -r -d '' PAIRS <<'MAP' || true
af af
am am
ar ar
az-AZ az
be be
bg bg
bn-BD bn
ca ca
cs-CZ cs
da-DK da
de-DE de
el-GR el
en-AU en
en-CA en
en-GB en
en-IN en
en-SG en
en-US en
en-ZA en
es-419 es
es-ES es
es-US es
et et
eu-ES eu
fa fa
fa-AE fa
fa-AF fa
fa-IR fa
fi-FI fi
fil fil
fr-CA fr
fr-FR fr
gl-ES gl
gu gu
hi-IN hi
hr hr
hu-HU hu
hy-AM hy
id id
is-IS is
it-IT it
iw-IL he
ja-JP ja
ka-GE ka
kk kk
km-KH km
kn-IN kn
ko-KR ko
ky-KG ky
lo-LA lo
lt lt
lv lv
mk-MK mk
ml-IN ml
mn-MN mn
mr-IN mr
ms ms
ms-MY ms
my-MM my
ne-NP ne
nl-NL nl
no-NO nb
pa pa
pl-PL pl
pt-BR pt-BR
pt-PT pt-PT
rm rm
ro ro
ru-RU ru
si-LK si
sk sk
sl sl
sq sq
sr sr
sv-SE sv
sw sw
ta-IN ta
te-IN te
th th
tr-TR tr
uk uk
ur ur
vi vi
zh-CN zh-CN
zh-HK zh-TW
zh-TW zh-TW
zu zu
MAP

# LOCALES ortam değişkeni verilirse yalnızca o mağaza yerel ayarları çekilir.
if [[ -n "${LOCALES:-}" ]]; then
  filter=" ${LOCALES} "
  PAIRS="$(while read -r store app; do
    if [[ -n "$store" && "$filter" == *" $store "* ]]; then echo "$store $app"; fi
  done <<< "$PAIRS"; true)"
fi

capture_locale() {
  local app_tag="$1" output="$2"
  mkdir -p "$output"
  "$ADB" -s "$SERIAL" shell cmd locale set-app-locales "$PKG" --user 0 --locales "$app_tag" < /dev/null
  # Dil değişimi yalnızca yeniden başlatınca uygulanır; bu yüzden dil başına bir kez durdurulur.
  "$ADB" -s "$SERIAL" shell am force-stop "$PKG" < /dev/null
  for index in "${!routes[@]}"; do
    local route="${routes[$index]}"
    "$ADB" -s "$SERIAL" shell am start -a android.intent.action.VIEW \
      -d "storagemanager://screen/$route" -p "$PKG" \
      --ez screenshotMode true < /dev/null >/dev/null
    # İlk rota soğuk açılış: hem Compose'un ilk çizimi hem de depolama istatistikleri
    # gecikmeli geliyor. Sonraki rotalar sıcak gezinme olduğu için kısa bekleme yeter.
    if [[ "$index" == "0" ]]; then sleep "$COLD_WAIT"; else sleep "$WARM_WAIT"; fi
    "$ADB" -s "$SERIAL" exec-out screencap -p > "$output/$((index + 1))_${route}.png"
  done
}

declare -a captured_tags=()
declare -a captured_dirs=()
store_count=0

# adb komutları stdin'i tükettiği için liste 3 numaralı tanımlayıcıdan okunur;
# aksi halde döngü ilk dilden sonra sona erer.
while read -r store app <&3; do
  [[ -z "$store" ]] && continue
  output="$ROOT/play-store/screenshots/$store/phoneScreenshots"
  store_count=$((store_count + 1))

  source_dir=""
  for i in "${!captured_tags[@]}"; do
    if [[ "${captured_tags[$i]}" == "$app" ]]; then source_dir="${captured_dirs[$i]}"; break; fi
  done

  if [[ -n "$source_dir" ]]; then
    echo "· $store  ← $app (kopyalanıyor)"
    mkdir -p "$output"
    cp "$source_dir"/*.png "$output/"
  else
    echo "▸ $store  ($app) yakalanıyor"
    capture_locale "$app" "$output"
    captured_tags+=("$app")
    captured_dirs+=("$output")
  fi
done 3<<< "$PAIRS"

echo "Captured ${#captured_tags[@]} app languages × ${#routes[@]} screenshots → $store_count store locales."
echo "Şimdi 9:16 sürümleri için: python3 scripts/make_9x16_screenshots.py"
