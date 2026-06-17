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
        minSdk        = 29
        targetSdk     = 35
        versionCode   = 1
        versionName   = "1.0.0"
    }

    flavorDimensions += "device"
    productFlavors {
        create("zebra") {
            dimension = "device"
        }
        create("chainway") {
            dimension    = "device"
            // Separate applicationId so both APKs can coexist on a test device
            applicationIdSuffix = ".c72"
            versionNameSuffix   = "-c72"
        }
    }

    buildTypes {
        debug {
            val url = localProps.getProperty("storelense.debug.url", "http://10.0.2.2:8080/")
                .trimEnd('/') + "/"
            buildConfigField("String",  "BASE_URL",       "\"$url\"")
            buildConfigField("Boolean", "USE_MOCK_RFID",  "true")
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    // EMDK — Zebra system library; place com.symbol.emdk.jar in app/libs/
    "zebraCompileOnly"(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Chainway UHF RFID SDK — place RFIDWithUHFUART.jar in app/chainway-libs/
    "chainwayCompileOnly"(fileTree(mapOf("dir" to "chainway-libs", "include" to listOf("*.jar"))))

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
