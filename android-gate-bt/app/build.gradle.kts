import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

kotlin {
    jvmToolchain(17)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace  = "com.storelense.gateBt"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.storelense.gateBt"
        minSdk        = 23
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"
    }

    // Two flavors: mock (emulator/dev) and bluetooth (real MUBR01 hardware)
    flavorDimensions += "rfid"
    productFlavors {
        create("mock") {
            dimension = "rfid"
            buildConfigField("Boolean", "USE_MOCK_RFID", "true")
            applicationIdSuffix = ".mock"
            versionNameSuffix   = "-mock"
        }
        create("bluetooth") {
            dimension = "rfid"
            buildConfigField("Boolean", "USE_MOCK_RFID", "false")
        }
    }

    buildTypes {
        debug {
            val url = localProps.getProperty("storelense.debug.url", "http://150.241.244.61:8080/")
                .trimEnd('/') + "/"
            val useMock = localProps.getProperty("storelense.use.mock.rfid", "true").toBoolean()
            buildConfigField("String",  "BASE_URL",      "\"$url\"")
            // debug overrides the flavor default so emulator builds always mock
            buildConfigField("Boolean", "USE_MOCK_RFID", useMock.toString())
            isDebuggable = true
        }
        release {
            val url = localProps.getProperty("storelense.release.url", "https://api.storelense.internal/")
                .trimEnd('/') + "/"
            buildConfigField("String", "BASE_URL", "\"$url\"")
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose     = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
        )
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    // Identium MUBR01 BLE RFID SDK — bluetooth flavor only
    "bluetoothImplementation"(fileTree(mapOf("dir" to "libs", "include" to listOf("ModuleAPI_Android_BT.jar"))))

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.core.ktx)
    implementation(libs.splash)
    implementation(libs.coroutines.android)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.activity)
    implementation(libs.compose.icons.ext)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.runtime)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // CameraX + ML Kit barcode (for QR bill scanning)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // Room (offline gate-check queue for unreliable network)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager (background sync of queued checks)
    implementation(libs.workmanager)

    implementation(libs.security.crypto)
    implementation(libs.timber)
}
