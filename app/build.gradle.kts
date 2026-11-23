import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Release signing: CI passes the keystore through SALAH_* environment variables (from repo
 * secrets); locally, keystore.properties (gitignored) points at signing/salah-release.jks.
 * Every release must use the same key, or updates can't install over each other.
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(env: String, prop: String): String? = System.getenv(env)?.takeIf { it.isNotBlank() } ?: keystoreProps.getProperty(prop)
val releaseStore = signingValue("SALAH_KEYSTORE_PATH", "storeFile")?.let { rootProject.file(it) }?.takeIf { it.exists() }

android {
    namespace = "com.zainkhalid.salah"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zainkhalid.salah"
        minSdk = 26
        targetSdk = 36
        // CI sets these per build (-PversionCode=…), so every push is a newer version.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 5
        versionName = (project.findProperty("versionName") as String?) ?: "1.4.0"
    }

    signingConfigs {
        if (releaseStore != null) {
            create("release") {
                storeFile = releaseStore
                storePassword = signingValue("SALAH_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("SALAH_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("SALAH_KEY_PASSWORD", "keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the release key (a fork, a pull request), fall back to the debug key so it still builds.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
}
