import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
}

// Firebase yalnızca google-services.json eklendiğinde derlemeye dahil edilir.
// Böylece dosya olmadan da proje derlenir; dosya eklenince Analytics kendiliğinden devreye girer.
val hasFirebaseConfig = file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// ── İmzalama ────────────────────────────────────────────────────────────────
// Anahtar deposu bilgileri local.properties'ten okunur; bu dosya .gitignore'dadır.
// Böylece parolalar depoya girmez ve makinede anahtar yoksa derleme yine de sürer
// (release yalnızca imzasız üretilir).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val keystoreFile = localProps.getProperty("signing.storeFile")?.let(::File)
val hasKeystore = keystoreFile?.exists() == true

android {
    namespace = "com.storagemanager"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.storagemanager"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasKeystore) {
            create("release") {
                storeFile = keystoreFile
                storePassword = localProps.getProperty("signing.storePassword")
                keyAlias = localProps.getProperty("signing.keyAlias")
                keyPassword = localProps.getProperty("signing.keyPassword")

                // Play App Signing yükleme anahtarı için V1+V2 yeterli; V3/V4 Play tarafında üretilir.
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = if (hasKeystore) signingConfigs.getByName("release") else null
            manifestPlaceholders["admobAppId"] = "ca-app-pub-5402319591036466~2640660062"
            buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"ca-app-pub-5402319591036466/7920906907\"")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"ca-app-pub-5402319591036466/3981661892\"")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
            // Google'ın resmî test birimleri — hata ayıklamada asla gerçek reklam istenmez.
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_NATIVE_UNIT_ID", "\"ca-app-pub-3940256099942544/2247696110\"")
            buildConfigField("String", "ADMOB_REWARDED_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Activity
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.animation)
    debugImplementation(libs.androidx.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Gson (JSON serialization for DB caching)
    implementation(libs.gson)

    // AppCompat — uygulama içi dil seçici (per-app language) backport'u
    implementation(libs.androidx.appcompat)

    // Play Billing — Pro satın alma
    implementation(libs.billing.ktx)

    // AdMob — native + ödüllü reklam
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    // ML Kit — fotoğraf içerik sınıflandırması (cihaz üzerinde, gömülü model)
    implementation(libs.mlkit.image.labeling)

    // Firebase Analytics
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    // Coil (image loading)
    implementation(libs.coil.compose)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // DataStore
    implementation(libs.datastore.preferences)

    // Unit Testing (JVM)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Compose UI Testing
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
