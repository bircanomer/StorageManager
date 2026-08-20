# Play Console Uyum Dosyası

Bu belge yayına alma sırasında Play Console'da doldurulacak formların hazır cevaplarını içerir.
Köşeli parantezli alanları kendi bilgilerinizle değiştirin.

---

## 1. Hassas izin beyanı — Tüm dosyalara erişim (MANAGE_EXTERNAL_STORAGE)

Play Console → **Uygulama içeriği → Hassas izinler ve API'ler → Tüm dosyalara erişim izni**

**Uygulamanın temel işlevi (form alanına yapıştırılacak metin):**

> StorageManager bir dosya yöneticisi ve depolama temizleme aracıdır. Uygulamanın tek amacı,
> kullanıcının cihazındaki dosyaları listeleyip boyutlarına göre analiz etmek ve kullanıcının
> seçtiklerini silmesini sağlamaktır. Bunun için cihazın tüm paylaşılan depolama alanını
> okuyabilmesi gerekir:
>
> - Büyük dosya avcısı, kullanıcının belirlediği eşiğin (varsayılan 50 MB) üzerindeki tüm
>   dosyaları klasör ayrımı yapmadan listeler.
> - Kopya belge tespiti, farklı klasörlerdeki aynı içerikli dosyaları karşılaştırır; bu yalnızca
>   depolamanın tamamı okunabildiğinde mümkündür.
> - Boş klasör ve geçici dosya temizliği, uygulama dizinlerinin dışındaki artıkları bulur.
> - Depolama haritası, tüm klasör ağacının boyut dağılımını gösterir.
>
> MediaStore ve Storage Access Framework bu işlevler için yeterli değildir: MediaStore yalnızca
> medya türlerini indeksler; belgeler, arşivler, APK'lar ve uygulama artıkları görünmez. SAF ise
> kullanıcıdan her klasör için ayrı ayrı seçim ister ve toplu boyut analizine izin vermez.
>
> Okunan hiçbir dosya, dosya adı veya içerik cihazdan dışarı gönderilmez. Silme işlemi yalnızca
> kullanıcının açıkça seçtiği dosyalar üzerinde yapılır ve dosyalar önce uygulama içi çöp
> kutusuna taşınır.

**Gerekli video:** Formda izni kullanan akışın ekran kaydı isteniyor. Şu akışı çekin:
Ana ekran → "Akıllı taramayı başlat" → **Büyük dosyalar** ekranı → bir dosya seçip silme →
**Sistem çöpleri** ekranı. Video herkese açık bir bağlantıda olmalı (ör. YouTube "liste dışı").

---

## 2. Hassas izin beyanı — QUERY_ALL_PACKAGES

Play Console → **Hassas izinler → Tüm uygulama paketlerini sorgulama**

Seçilecek kullanım amacı: **Cihaz temizleme / depolama yöneticisi**

> Uygulama, kullanılmayan uygulamaları tespit etmek, her uygulamanın kapladığı alanı ve önbellek
> boyutunu göstermek için yüklü uygulama listesine ihtiyaç duyar. Bu, uygulamanın ana ekranındaki
> "Kullanılmayan uygulamalar" ve "Önbellek" özelliklerinin temelidir. Liste yalnızca cihazda
> işlenir, hiçbir yere gönderilmez.

---

## 3. Veri güvenliği (Data Safety) formu cevapları

| Soru | Cevap |
|---|---|
| Uygulamanız kullanıcı verisi topluyor veya paylaşıyor mu? | **Evet** (yalnızca reklam ve analitik SDK'ları nedeniyle) |
| Veriler aktarım sırasında şifreleniyor mu? | **Evet** |
| Kullanıcı verisinin silinmesini talep edebiliyor mu? | **Evet** — uygulama kaldırıldığında tüm yerel veri silinir |
| Uygulama Play Family Policy kapsamında mı? | **Hayır** |

### Toplanan / paylaşılan veri türleri

| Veri türü | Toplanıyor | Paylaşılıyor | Amaç | Zorunlu mu |
|---|---|---|---|---|
| Cihaz veya diğer kimlikler (Reklam Kimliği) | Evet | Evet (AdMob) | Reklam, analitik | Hayır — Pro'da reklam yok |
| Uygulama etkileşimleri (ekran görüntüleme, tarama olayları) | Evet | Evet (Firebase) | Analitik | Hayır |
| Kilitlenme günlükleri / tanılama | Evet | Evet (Firebase) | Uygulama işlevselliği | Hayır |
| Satın alma geçmişi | Evet | Hayır | Uygulama işlevselliği (Pro hakkı) | Evet |
| **Fotoğraflar ve videolar** | **Hayır** | **Hayır** | Yalnızca cihazda işlenir | — |
| **Dosyalar ve belgeler** | **Hayır** | **Hayır** | Yalnızca cihazda işlenir | — |

> Önemli: "Fotoğraflar / Dosyalar" için **"Toplanmıyor"** işaretlenir. Play'in tanımına göre
> "toplama", verinin cihazdan çıkarılmasıdır; yalnızca cihaz üzerinde geçici işleme toplama
> sayılmaz. Bu formda ayrıca "veriler yalnızca cihazda işleniyor" kutusunu işaretleyin.

---

## 4. Reklam beyanı

- Play Console → **Uygulama içeriği → Reklamlar → "Evet, uygulamamda reklam var"**.
- AdMob konsolunda **AEA/İngiltere onay mesajı (UMP)** yayımlanmalı; uygulama kodu
  `AdsManager.requestConsentIfNeeded` ile bunu zaten çağırıyor.
- Yayına almadan önce **test reklam kimliklerini gerçek kimliklerle değiştirin**:
  - `app/build.gradle.kts` → `ADMOB_NATIVE_UNIT_ID`, `ADMOB_REWARDED_UNIT_ID`
  - `AndroidManifest.xml` → `com.google.android.gms.ads.APPLICATION_ID`
  - Gerçek kimliklerle test etmeyin; kendi reklamlarınıza tıklamak hesap kapanmasına yol açar.

---

## 5. Ürün kurulumu (Play Billing)

Play Console → **Para kazanma → Ürünler**. Koddaki kimliklerle birebir aynı olmalı:

| Ürün kimliği | Tür | Not |
|---|---|---|
| `pro_monthly` | Abonelik | Aylık plan |
| `pro_yearly` | Abonelik | Yıllık plan — paywall'da "en avantajlı" rozeti bunda |
| `pro_lifetime` | Tek seferlik ürün | Ömür boyu erişim |

Ürünler oluşturulup **etkinleştirilmeden** paywall fiyat gösteremez; ekran bu durumda
"fiyatlar şu anda kullanılamıyor" mesajını gösterir (sabit fiyat yazmaz).

Satın alma akışını test etmek için Play Console → **Test → Lisans testi** listesine kendi
Google hesabınızı ekleyin; test satın alımlarından ücret alınmaz.

---

## 6. İmzalama

Release imzası **`local.properties`** üzerinden yapılandırıldı. Bu dosya `.gitignore`'da
olduğu için parolalar depoya girmez; `.gitignore` ayrıca `*.keystore`, `*.jks`, `*.aab`
ve `*.apk` dosyalarını da dışarıda tutar.

```properties
signing.storeFile=.../certifications/mathquiz_upload.keystore
signing.storePassword=...
signing.keyAlias=zerone
signing.keyPassword=...
```

`app/build.gradle.kts` bu değerleri okur ve **anahtar deposu bulunamazsa imzasız derler** —
başka bir makinede veya CI'da derleme bu yüzden kırılmaz.

Üretilen imza sertifikası: `CN=zerone, OU=Mobile, O=Zerone, L=Istanbul, ST=Istanbul, C=TR`
(2053'e kadar geçerli). `jarsigner -verify` ile doğrulandı.

> Bu, MathQuiz ile ortak bir **yükleme (upload) anahtarıdır**. Play App Signing kullanıldığında
> dağıtım anahtarını Google üretir, dolayısıyla aynı yükleme anahtarını birden çok uygulamada
> kullanmak sorun değildir. Ancak anahtar kaybedilirse **tüm** bu uygulamalar için yükleme
> anahtarı sıfırlama talebi gerekir — yedeğini ayrı bir yerde tutun.

Sürüm çıktısı almak için:

```bash
./gradlew :app:bundleRelease
```

Çıktı: `app/build/outputs/bundle/release/app-release.aab`

## 7. Play Developer API servis hesabı

Otomatik yükleme için kullanılacak hesap:

- Dosya: `~/Documents/Development/Credentials/upheld-flow-490222-g4-fd8e265baeb7.json`
- Hesap: `google-play-console@upheld-flow-490222-g4.iam.gserviceaccount.com`
- `local.properties` içinde `play.serviceAccountFile` olarak kayıtlı.

Kullanmadan önce Play Console → **Kullanıcılar ve izinler** bölümünde bu servis hesabına
StorageManager için en az "Sürümleri yönet" yetkisi verilmelidir.

> Not: Play Developer API **yeni bir uygulama oluşturamaz**. İlk AAB'nin Play Console'a elle
> yüklenmesi gerekir; otomatik yayınlama ancak ondan sonra devreye alınabilir.

## 8. Yayın öncesi kontrol listesi

- [ ] `app/build.gradle.kts` → `versionCode`/`versionName` artırıldı (ilk sürüm: 1 / 1.0.0)
- [x] Release imzalama yapılandırıldı ve imza doğrulandı (6. bölüm)
- [ ] AdMob gerçek kimlikleri girildi (yukarıdaki 4. madde)
- [ ] `google-services.json` eklendi (Firebase Analytics bu dosya olmadan sessizce devre dışı kalır)
- [ ] Gizlilik politikası URL'si Play Console'a girildi:
      `https://www.zeroneapps.com/storagemanager-privacy.html` (yayımlandı ✔)
- [ ] Tüm dosyalara erişim beyanı + tanıtım videosu gönderildi
- [ ] Veri güvenliği formu dolduruldu
- [ ] Mağaza listesi 20 dile çevrildi (uygulama içi diller ile aynı liste)
- [ ] Her dil için en az 2 ekran görüntüsü yüklendi

### Kapalı test zorunluluğu (yeni bireysel geliştirici hesapları)

Play, yeni **bireysel** geliştirici hesaplarından üretim yayınından önce şunu istiyor:

- En az **12 test kullanıcısı** kapalı teste katılmalı (davetleri kabul etmeleri gerekir),
- Bu 12 kişi **kesintisiz 14 gün** boyunca teste dahil kalmalı,
- Süre dolduğunda "üretime yükselt" başvurusu açılır ve incelemeye girer.

Pratik notlar:
- Sayaç, tester sayısı 12'nin altına düşerse sıfırlanır — kimseyi listeden çıkarmayın.
- Testerlar uygulamayı gerçekten açmalı; sadece davet kabul etmek yeterli sayılmayabilir.
- Bu süreyi 20 dilin mağaza metinlerini hazırlamak ve reklam/ürün kimliklerini test etmek için kullanın.
- Şirket (tüzel kişi) hesaplarında bu 12/14 kuralı uygulanmaz.
