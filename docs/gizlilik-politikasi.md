# Gizlilik Politikası

Yayımlanan sürüm Zerone Apps sitesinde tutuluyor; bu depoda kopyası **bilerek yok** —
iki metin zamanla birbirinden ayrılırsa Play'e yanlış beyan verilmiş olur.

**Play Console'a girilecek URL:**
`https://www.zeroneapps.com/storagemanager-privacy.html`

**Kaynak dosya:** `zeroneapps/storagemanager-privacy.html`
(depo: [bircanomer/zeroneapps](https://github.com/bircanomer/zeroneapps))

## Politika değiştiğinde

Uygulamanın veri davranışı değişirse (yeni izin, yeni SDK, yeni veri gönderimi) sırasıyla:

1. `storagemanager-privacy.html` içindeki ilgili kartı güncelle ve alttaki
   "Effective date" tarihini değiştir.
2. [play-console-uyum.md](play-console-uyum.md) içindeki **Veri güvenliği** tablosunu
   aynı değişikliğe göre güncelle ve Play Console'daki formu yeniden gönder.

Politikanın kapsadığı üç dış servis: AdMob (AEA/İngiltere onay formuyla),
Firebase Analytics ve Play Billing. Fotoğraf ve dosya analizinin tamamı cihazda yapılır —
bu iddia kod tarafından korunmalıdır; sunucuya veri gönderen bir özellik eklenirse
politika önce güncellenmelidir.
