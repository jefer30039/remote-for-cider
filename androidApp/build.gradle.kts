import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

// Firma de las versiones: se lee de keystore.properties (fuera de git). Sin ese
// archivo la versión de release sale sin firmar.
val signing = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "cider.remote.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.jefer30039.remoteforcider"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }

    buildFeatures {
        compose = true
    }

    signingConfigs {
        if (signing.isNotEmpty()) create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            // R8: menos código que cargar y compilar; la primera apertura de las pantallas va más suave.
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
        // Para medir rendimiento en el teléfono: como release (R8, sin depuración) pero
        // con la firma de depuración, así se instala encima de la de depuración sin
        // perder el emparejamiento. `profileable` permite trazas de Perfetto/simpleperf.
        create("bench") {
            initWith(getByName("release"))
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isProfileable = true
            matchingFallbacks += listOf("release")
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.core.ktx)
    implementation(libs.reorderable)
    implementation(libs.haze.blur)
    implementation(libs.kyant.backdrop)
    implementation(libs.kyant.shapes)
    implementation(libs.coroutines.android)
    // Instala al arrancar los perfiles de referencia que traen Compose y otras librerías
    // (van en el APK, pero sin esto solo los aplica Google Play).
    implementation(libs.profileinstaller)
}
