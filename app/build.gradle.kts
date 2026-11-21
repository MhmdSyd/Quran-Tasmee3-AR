plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// APK file name: Quran-Tasmee3-debug.apk / Quran-Tasmee3-release.apk
base {
    archivesName.set("Quran-Tasmee3")
}

android {
    namespace = "com.quranrecite.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.quranrecite.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "0.4.8"
    }

    // Optional release signing: only when CI (or the developer) provides the keystore through the environment.
    // Nothing secret is ever stored in the repository.
    val releaseKeystore = System.getenv("RELEASE_KEYSTORE_PATH")
    if (!releaseKeystore.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (!releaseKeystore.isNullOrBlank()) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    // One build, several APKs: a universal APK (all ABIs, "normal") plus one per ABI. CI publishes the universal APK and
    // the arm64-v8a one (about 40 MB smaller). The sherpa-onnx native libs come from src/main/jniLibs (tools/fetch_asr_assets.sh).
    // Do NOT also set ndk.abiFilters: it conflicts with ABI splits.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }
    buildFeatures { compose = true }
    // The ASR model is a 36 MB asset and each ABI adds a ~16-27 MB uncompressed .so; keep them mmap-able.
    packaging { jniLibs { useLegacyPackaging = false } }
    // SQLite assets must stay byte-identical when copied to app storage.
    androidResources { noCompress += listOf("db", "onnx") }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.navigation:navigation-compose:2.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.xerial:sqlite-jdbc:3.46.1.0") // JVM tests read the asset DBs directly
}
