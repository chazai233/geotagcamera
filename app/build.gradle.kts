plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Release signing comes from env vars (CI secrets, see .github/workflows/release.yml).
// Without them assembleRelease still works and produces an unsigned APK.
val releaseStoreFile: String? = System.getenv("RELEASE_STORE_FILE")

android {
    namespace = "com.geotagcamera.geotagginglocationonphoto"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.geotagcamera.geotagginglocationonphoto"
        minSdk = 26
        targetSdk = 36
        // Split APKs get versionCode * 10 + ABI digit (see androidComponents below).
        versionCode = 5
        versionName = "1.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // Without release secrets, sign with this machine's debug key: same
            // certificate every earlier GitHub release used, so users update in
            // place, but shrunk by R8 (a fraction of the debug APK's size).
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // MapLibre ships ~8-11 MB of native code per CPU type. One APK per ABI keeps
    // downloads small; the universal APK stays for anyone unsure which to pick.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = true
        }
    }

    // F-Droid can't verify the encrypted dependency metadata blob, so leave it out.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Each ABI APK needs its own versionCode so stores and in-place updates can
// tell them apart: universal = base*10, then 1 = armeabi-v7a, 2 = arm64-v8a, 3 = x86_64.
val abiDigit = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find { it.filterType == com.android.build.api.variant.FilterConfiguration.FilterType.ABI }?.identifier
            val base = output.versionCode.orNull ?: 0
            output.versionCode.set(base * 10 + (abi?.let { abiDigit[it] } ?: 0))
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    // Committed schemas make every future Room migration reviewable and testable.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // Core / Compose
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Vector icons only, no emoji glyphs anywhere in the UI. R8 strips the unused ones.
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Manifest theme (android:theme="@style/Theme.Material3...") needs the
    // classic Material Components XML styles, Compose material3 alone doesn't ship them
    implementation(libs.material)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    // Night / HDR, only where the phone maker exposes them (CameraX Extensions)
    implementation(libs.androidx.camera.extensions)

    // Location
    implementation(libs.play.services.location)

    // Map thumbnail + offline maps: MapLibre renders OpenFreeMap vector tiles
    // on the phone. No API key, no Google services, works offline.
    implementation(libs.maplibre.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore (stamp field / org branding preferences)
    implementation(libs.androidx.datastore.preferences)

    // EXIF
    implementation(libs.androidx.exifinterface)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Coil for gallery thumbnails
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    // Real org.json on the unit-test classpath — the android.jar stub throws
    // "not mocked", so JVM tests of UserCommentCodec need the actual impl.
    testImplementation(libs.org.json)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
