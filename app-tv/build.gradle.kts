import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}

android {
    namespace = "com.idlix.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.idlix.tv"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "1.0.6"
        // No backend URL: direct-source via IdlixDomainResolver (https://idlix.net/ → live mirror).
        // SubDL key lives in core:data BuildConfig (local.properties subdlApiKey / SUBDL_API_KEY env).

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            debugSymbolLevel = "FULL"
        }
    }

    // Release signing is optional: present keystore → signed APK; absent → unsigned APK (CI default).
    // Provide via local.properties (storeFile/storePassword/keyAlias/keyPassword) or env
    // (RELEASE_KEYSTORE_PATH/RELEASE_KEYSTORE_PASSWORD/RELEASE_KEY_ALIAS/RELEASE_KEY_PASSWORD).
    val releaseKeystorePath = localProperties.getProperty("storeFile")
        ?: System.getenv("RELEASE_KEYSTORE_PATH")
    val releaseKeystorePassword = localProperties.getProperty("storePassword")
        ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
    val releaseKeyAlias = localProperties.getProperty("keyAlias")
        ?: System.getenv("RELEASE_KEY_ALIAS")
    val releaseKeyPassword = localProperties.getProperty("keyPassword")
        ?: System.getenv("RELEASE_KEY_PASSWORD")
    val hasReleaseKey = !releaseKeystorePath.isNullOrBlank()
        && file(releaseKeystorePath).exists()
        && !releaseKeystorePassword.isNullOrBlank()
        && !releaseKeyAlias.isNullOrBlank()
        && !releaseKeyPassword.isNullOrBlank()
    if (hasReleaseKey) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    } else {
        logger.warn("No release keystore configured: :app-tv:assembleRelease will produce an UNSIGNED APK.")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
        freeCompilerArgs = listOf(
            "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
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

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM & Core
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Android TV Compose (Netflix-style UI)
    implementation(libs.androidx.tv.foundation)
    implementation(libs.androidx.tv.material)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Network & Serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Coil for TV image loading
    implementation(libs.coil.compose)

    // Local DB
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    // Background update checks
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
}
