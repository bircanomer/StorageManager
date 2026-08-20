package com.storagemanager.ml

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bir fotoğrafın kaba içerik türü.
 *
 * Bulanıklık/duplike gibi sinyaller görüntünün *biçimine* bakar; bu sınıflandırma
 * ise *ne olduğuna* bakar ve tek bir amaca hizmet eder: anı değeri yüksek
 * fotoğrafları silme adayı listesinden korumak.
 *
 * Kapsam bilinçli olarak dar tutuldu. Cihaz üstünde ölçülen çıkarım maliyeti
 * ~85 ms/fotoğraf ve ML Kit içeride serileştiği için paralelleştirme fayda etmiyor;
 * 17 binlik bir galeride tam geçiş ~40 dakika sürüyordu. Bu yüzden sınıflandırma
 * yalnızca bulanıklık eşiğini geçen adaylara uygulanır.
 */
enum class PhotoContent {
    /** İnsan içeren fotoğraf — anı değeri yüksek, agresif temizlikten korunur. */
    PEOPLE,

    /** Evcil hayvan fotoğrafı — aynı şekilde korunur. */
    PET,

    /** Yukarıdakilerden hiçbiri veya sınıflandırma yapılmadı. */
    UNKNOWN
}

/**
 * ML Kit görüntü etiketleme (Image Labeling) tabanlı içerik sınıflandırıcı.
 *
 * Bu projedeki ilk gerçek sinir ağı: cihaz üzerinde çalışan, APK'ya gömülü
 * ~400 etiketlik taban model. Ağ bağlantısı gerektirmez, fotoğraf cihazdan çıkmaz.
 *
 * Model belleği ucuz değildir; [close] ile tarama sonunda serbest bırakılır ve
 * bir sonraki taramada tembel olarak yeniden açılır.
 */
@Singleton
class PhotoClassifier @Inject constructor() {

    @Volatile
    private var labeler: ImageLabeler? = null

    private fun obtainLabeler(): ImageLabeler =
        labeler ?: synchronized(this) {
            labeler ?: ImageLabeling.getClient(
                ImageLabelerOptions.Builder()
                    .setConfidenceThreshold(MIN_CONFIDENCE)
                    .build()
            ).also { labeler = it }
        }

    /**
     * Verilen bitmap'in içerik türünü belirler.
     *
     * ML Kit asenkron çalışır; çağıran zaten bir arka plan işçisi olduğu için
     * sonucu [Tasks.await] ile bekliyoruz. Bu, işçi başına en fazla bir eşzamanlı
     * çıkarım demek — model kuyruğunun şişmesini de kendiliğinden engeller.
     *
     * Hata veya zaman aşımında [PhotoContent.UNKNOWN] döner; sınıflandırma
     * başarısız olduğunda tarama, ML öncesi davranışına düşer.
     */
    fun classify(bitmap: Bitmap): PhotoContent {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = Tasks.await(
                obtainLabeler().process(image),
                TIMEOUT_SECONDS,
                TimeUnit.SECONDS
            )
            categorize(labels.map { it.text to it.confidence })
        } catch (e: Exception) {
            Log.w(TAG, "Sınıflandırma başarısız", e)
            PhotoContent.UNKNOWN
        }
    }

    /** Modeli bellekten düşürür. Bir sonraki [classify] çağrısı yeniden açar. */
    fun close() {
        synchronized(this) {
            try {
                labeler?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Etiketleyici kapatılamadı", e)
            }
            labeler = null
        }
    }

    companion object {
        /** Bu güvenin altındaki etiketler ML Kit tarafından zaten elenir. */
        const val MIN_CONFIDENCE = 0.6f

        /** Tek bir fotoğrafın çıkarımı için üst sınır. */
        const val TIMEOUT_SECONDS = 5L

        private const val TAG = "PhotoClassifier"

        /** İnsan içeren etiketler — bulanık olsa bile silinmeye aday gösterilmez. */
        private val PEOPLE_LABELS = setOf(
            "person", "people", "baby", "selfie", "crowd", "bride", "groom",
            "wedding", "smile", "face", "hair", "eyelash"
        )

        /** Evcil hayvan etiketleri — insan fotoğraflarıyla aynı korumaya tabidir. */
        private val PET_LABELS = setOf(
            "dog", "cat", "pet", "puppy", "kitten", "rabbit", "bird", "horse"
        )

        /**
         * Etiket listesini tek bir içerik türüne indirger.
         *
         * Saf hesaplama — ML Kit ve Android bağımlılığı yoktur, birim testlerinde
         * doğrudan çalıştırılır.
         *
         * Eşleşen etiketlerden **en yüksek güvene sahip olanı** kazanır; eşitlik
         * durumunda [PhotoContent] sıralaması (PEOPLE > PET) belirleyicidir,
         * böylece sonuç etiketlerin geliş sırasından bağımsız olur.
         *
         * @param labels (etiket metni, güven) çiftleri
         */
        @JvmStatic
        fun categorize(labels: List<Pair<String, Float>>): PhotoContent {
            var best = PhotoContent.UNKNOWN
            var bestConfidence = -1f

            for ((text, confidence) in labels) {
                val key = text.lowercase()
                val content = when (key) {
                    in PEOPLE_LABELS -> PhotoContent.PEOPLE
                    in PET_LABELS -> PhotoContent.PET
                    else -> continue
                }

                val better = confidence > bestConfidence ||
                        (confidence == bestConfidence && content.ordinal < best.ordinal)
                if (better) {
                    best = content
                    bestConfidence = confidence
                }
            }
            return best
        }

        /** İçerik türü koruma altında mı — bulanık listesine eklenmemeli mi? */
        @JvmStatic
        fun isProtected(content: PhotoContent): Boolean =
            content == PhotoContent.PEOPLE || content == PhotoContent.PET
    }
}
