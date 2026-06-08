import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val versionProperties = Properties().apply {
    file("version.properties").inputStream().use { load(it) }
}

fun versionProperty(name: String): String = versionProperties.getProperty(name).orEmpty().trim()

val appVersionCode = versionProperty("VERSION_CODE").toInt()
val appVersionName = buildString {
    append(versionProperty("VERSION_MAJOR"))
    append(".")
    append(versionProperty("VERSION_MINOR"))
    append(".")
    append(versionProperty("VERSION_PATCH"))
    versionProperty("VERSION_SUFFIX").takeIf { it.isNotBlank() }?.let { suffix ->
        append("-")
        append(suffix)
    }
}

val keystorePropertiesFile = rootProject.file("release/keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

fun signingProperty(name: String, envName: String): String? {
    return keystoreProperties.getProperty(name)?.trim()?.takeIf { it.isNotBlank() }
        ?: System.getenv(envName)?.trim()?.takeIf { it.isNotBlank() }
}

val releaseStoreFile = signingProperty("storeFile", "ANDROID_KEYSTORE_FILE")
val releaseStorePassword = signingProperty("storePassword", "ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingProperty("keyAlias", "ANDROID_KEY_ALIAS")
val releaseKeyPassword = signingProperty("keyPassword", "ANDROID_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

fun resolveStoreFile(path: String): File {
    val candidate = File(path)
    return if (candidate.isAbsolute) candidate else rootProject.file(path)
}

android {
    namespace = "com.vunbo.watchtogether"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.vunbo.watchtogether"
        minSdk = 24
        targetSdk = 28
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = resolveStoreFile(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Some TVBox spider jars include Guard/native checks and terminate debuggable apps.
            // Keep the local install runnable with those subscriptions; attach a debugger only
            // from a separate build type if needed.
            isDebuggable = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Networking
    implementation(libs.okhttp)
    implementation(libs.okhttp.sse)
    implementation(libs.gson)

    // Image loading
    implementation(libs.coil.compose)

    // ZXing
    implementation(libs.zxing.core)

    // DataStore & Security
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Media3 / ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.hls)
    implementation(libs.androidx.media3.ui)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
