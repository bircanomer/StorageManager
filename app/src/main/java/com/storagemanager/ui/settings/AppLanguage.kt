package com.storagemanager.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Uygulamanın desteklediği diller.
 *
 * Her giriş `res/xml/locales_config.xml` ve `res/values-XX/strings.xml` ile birebir
 * eşleşmelidir. [nativeName] kasıtlı olarak çevrilmez: kullanıcı, anlamadığı bir dile
 * düştüğünde kendi dilini listede yine de tanıyabilmelidir.
 *
 * Liste, kullanıcıya gösterilirken İngilizce başta kalacak şekilde tanımlanma sırasını korur;
 * geri kalan diller kendi adlarının Latin alfabesindeki karşılığına göre alfabetiktir.
 */
enum class AppLanguage(val tag: String, val nativeName: String) {
    ENGLISH("en", "English"),
    AFRIKAANS("af", "Afrikaans"),
    ALBANIAN("sq", "Shqip"),
    AMHARIC("am", "አማርኛ"),
    ARABIC("ar", "العربية"),
    ARMENIAN("hy", "Հայերեն"),
    AZERBAIJANI("az", "Azərbaycan"),
    BASQUE("eu", "Euskara"),
    BELARUSIAN("be", "Беларуская"),
    BENGALI("bn", "বাংলা"),
    BULGARIAN("bg", "Български"),
    BURMESE("my", "မြန်မာ"),
    CATALAN("ca", "Català"),
    CHINESE_SIMPLIFIED("zh-CN", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "繁體中文"),
    CROATIAN("hr", "Hrvatski"),
    CZECH("cs", "Čeština"),
    DANISH("da", "Dansk"),
    DUTCH("nl", "Nederlands"),
    ESTONIAN("et", "Eesti"),
    FILIPINO("fil", "Filipino"),
    FINNISH("fi", "Suomi"),
    FRENCH("fr", "Français"),
    GALICIAN("gl", "Galego"),
    GEORGIAN("ka", "ქართული"),
    GERMAN("de", "Deutsch"),
    GREEK("el", "Ελληνικά"),
    GUJARATI("gu", "ગુજરાતી"),
    HEBREW("he", "עברית"),
    HINDI("hi", "हिन्दी"),
    HUNGARIAN("hu", "Magyar"),
    ICELANDIC("is", "Íslenska"),
    INDONESIAN("id", "Bahasa Indonesia"),
    ITALIAN("it", "Italiano"),
    JAPANESE("ja", "日本語"),
    KANNADA("kn", "ಕನ್ನಡ"),
    KAZAKH("kk", "Қазақ"),
    KHMER("km", "ខ្មែរ"),
    KOREAN("ko", "한국어"),
    KYRGYZ("ky", "Кыргызча"),
    LAO("lo", "ລາວ"),
    LATVIAN("lv", "Latviešu"),
    LITHUANIAN("lt", "Lietuvių"),
    MACEDONIAN("mk", "Македонски"),
    MALAY("ms", "Bahasa Melayu"),
    MALAYALAM("ml", "മലയാളം"),
    MARATHI("mr", "मराठी"),
    MONGOLIAN("mn", "Монгол"),
    NEPALI("ne", "नेपाली"),
    NORWEGIAN("nb", "Norsk bokmål"),
    PERSIAN("fa", "فارسی"),
    POLISH("pl", "Polski"),
    PORTUGUESE_BR("pt-BR", "Português (Brasil)"),
    PORTUGUESE_PT("pt-PT", "Português (Portugal)"),
    PUNJABI("pa", "ਪੰਜਾਬੀ"),
    ROMANIAN("ro", "Română"),
    ROMANSH("rm", "Rumantsch"),
    RUSSIAN("ru", "Русский"),
    SERBIAN("sr", "Српски"),
    SINHALA("si", "සිංහල"),
    SLOVAK("sk", "Slovenčina"),
    SLOVENIAN("sl", "Slovenščina"),
    SPANISH("es", "Español"),
    SWAHILI("sw", "Kiswahili"),
    SWEDISH("sv", "Svenska"),
    TAMIL("ta", "தமிழ்"),
    TELUGU("te", "తెలుగు"),
    THAI("th", "ไทย"),
    TURKISH("tr", "Türkçe"),
    UKRAINIAN("uk", "Українська"),
    URDU("ur", "اردو"),
    VIETNAMESE("vi", "Tiếng Việt"),
    ZULU("zu", "isiZulu");

    companion object {

        /**
         * Uygulama dilini değiştirir. `null` verilirse sistem diline dönülür.
         *
         * Android 13+ sürümlerde sistem `LocaleManager`'ına, altındaki sürümlerde
         * AppCompat'in yedek uygulamasına yazar.
         */
        fun apply(language: AppLanguage?) {
            val locales = if (language == null) {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                LocaleListCompat.forLanguageTags(language.tag)
            }
            AppCompatDelegate.setApplicationLocales(locales)
        }

        /** Kullanıcının seçtiği dil; sistem dili kullanılıyorsa `null`. */
        fun current(): AppLanguage? {
            val tags = AppCompatDelegate.getApplicationLocales()
            if (tags.isEmpty) return null
            val tag = normalize(tags.toLanguageTags().substringBefore(','))
            return entries.firstOrNull { it.tag.equals(tag, ignoreCase = true) }
                ?: entries.firstOrNull {
                    it.tag.substringBefore('-').equals(tag.substringBefore('-'), ignoreCase = true)
                }
        }

        /**
         * Java'nın eski dil kodlarını modern BCP47 karşılıklarına çevirir.
         * `Locale.toLanguageTag()` bu üç dil için hâlâ eski kodu döndürebilir.
         */
        private fun normalize(tag: String): String = when (tag.substringBefore('-').lowercase()) {
            "iw" -> "he" + tag.substringAfter('-', "").let { if (it.isEmpty()) "" else "-$it" }
            "in" -> "id" + tag.substringAfter('-', "").let { if (it.isEmpty()) "" else "-$it" }
            "tl" -> "fil"
            else -> tag
        }
    }
}
