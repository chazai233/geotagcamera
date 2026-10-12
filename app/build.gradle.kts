import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Stadia Maps tile key lives in the gitignored local.properties (same pattern
// as sdk.dir), never in version control. Absent key -> empty string; the map
// feature degrades to "no tile" rather than failing the build.
val stadiaApiKey: String = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}.getProperty("stadiaMaps.apiKey", "")

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
        versionCode = 3
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "STADIA_API_KEY", "\"$stadiaApiKey\"")
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
            signingConfigs.findByName("release")?.let { signingConfig = it }
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
