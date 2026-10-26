import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        load(FileInputStream(file))
    }
}

android {
    namespace = "com.idlix.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.idlix.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    // Release signing is optional: present keystore → signed APK; absent → unsigned APK (CI default).
    // Provide via local.properties (storeFile/storePassword/keyAlias/keyPassword) or env
    // (IDLIX_KEYSTORE_FILE/IDLIX_KEYSTORE_PASSWORD/IDLIX_KEY_ALIAS/IDLIX_KEY_PASSWORD).
    val releaseKeystorePath = localProperties.getProperty("storeFile")
        ?: System.getenv("IDLIX_KEYSTORE_FILE")
    val releaseKeystorePassword = localProperties.getProperty("storePassword")
        ?: System.getenv("IDLIX_KEYSTORE_PASSWORD")
    val releaseKeyAlias = localProperties.getProperty("keyAlias")
        ?: System.getenv("IDLIX_KEY_ALIAS")
    val releaseKeyPassword = localProperties.getProperty("keyPassword")
        ?: System.getenv("IDLIX_KEY_PASSWORD")
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
        logger.warn("No release keystore configured: :app-mobile:assembleRelease will produce an UNSIGNED APK.")
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
}

dependencies {
    implementation(project(":core:data"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM & Material 3
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.hls)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.common)

    // Coil Image Loading
    implementation(libs.coil.compose)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
