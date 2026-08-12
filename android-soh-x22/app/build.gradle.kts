import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

android {
    namespace  = "com.storelense.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.storelense.mobile"
        minSdk        = 23
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"
    }

    flavorDimensions += "device"
    productFlavors {
        // Janam XR2 / XT30 handheld (UBX URFIDLibrary UHF SDK).
        // This project targets the XR22 device only — the Zebra (EMDK) and Chainway
        // flavors live in the original android-soh project.
        create("xr22") {
            dimension = "device"
            // Separate applicationId so this APK can coexist with the Zebra/Chainway
            // builds on the same test device.
            applicationIdSuffix = ".x22"
            versionNameSuffix   = "-x22"
        }
    }

    buildTypes {
        debug {
            val url = localProps.getProperty("storelense.debug.url", "http://10.0.2.2:8080/")
                .trimEnd('/') + "/"
            // Set storelense.use.mock.rfid=false in local.properties when running on a real device
            val useMock = localProps.getProperty("storelense.use.mock.rfid", "true").toBoolean()
            buildConfigField("String",  "BASE_URL",       "\"$url\"")
            buildConfigField("Boolean", "USE_MOCK_RFID",  "$useMock")
            isDebuggable = true
        }
        release {
            val url = localProps.getProperty("storelense.release.url", "https://api.storelense.internal/")
                .trimEnd('/') + "/"
            buildConfigField("String",  "BASE_URL",       "\"$url\"")
            buildConfigField("Boolean", "USE_MOCK_RFID",  "false")
            isMinifyEnabled   = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose      = true
        buildConfig  = true
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
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
        )
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

    // Janam XR2 / XT30 SDK:
    //   URFIDLibrary-*.aar          — UHF RFID (com.ubx.usdk.*): inventory, callbacks, power
    //   com.janam.device.XT30-*.aar — Janam device services (barcode scan head, keys)
    //   platform_sdk_*.jar          — android.device.* platform classes; provided by the
    //                                 device firmware at runtime, so compileOnly.
    "xr22Implementation"(fileTree(mapOf("dir" to "xr22-libs", "include" to listOf("*.aar"))))
    "xr22CompileOnly"(fileTree(mapOf("dir" to "xr22-libs", "include" to listOf("*.jar"))))

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
    implementation(libs.lifecycle.livedata)
    implementation(libs.navigation.compose)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.work)
    implementation(libs.hilt.navigation)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // WorkManager
    implementation(libs.workmanager)

    // Security
    implementation(libs.security.crypto)

    // Utilities
    implementation(libs.timber)
}
