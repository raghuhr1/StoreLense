plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace   = "com.storelense.zebra"
    compileSdk  = 35

    defaultConfig {
        applicationId   = "com.storelense.zebra"
        minSdk          = 29          // Zebra TC-series minimum
        targetSdk       = 34
        versionCode     = 1
        versionName     = "1.0.0"

        buildConfigField("String", "BASE_URL", "\"http://10.0.2.2:8081/\"") // Emulator → localhost
    }

    buildTypes {
        debug {
            buildConfigField("Boolean", "USE_MOCK_RFID", "true")
            buildConfigField("String",  "BASE_URL",      "\"http://10.0.2.2:8081/\"")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("Boolean", "USE_MOCK_RFID", "false")
            buildConfigField("String",  "BASE_URL",      "\"https://api.storelense.internal/\"")
        }
    }

    // EMDK is a system library on Zebra devices — add stubs JAR for compilation
    sourceSets["main"].jniLibs.srcDirs("libs")

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose     = true
        buildConfig = true
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    }
}

dependencies {
    // EMDK — present as system library on Zebra devices, stubs JAR for build
    // Place com.symbol.emdk.jar in app/libs/ (download from Zebra DevPortal)
    compileOnly(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))

    // Core
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.splash.screen)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi.kotlin)
    implementation(libs.moshi.adapters)
    ksp(libs.moshi.codegen)

    // Coroutines
    implementation(libs.coroutines.android)

    // WorkManager
    implementation(libs.workmanager.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    // DataStore + Security
    implementation(libs.datastore.prefs)
    implementation(libs.security.crypto)

    // Logging
    implementation(libs.timber)
}
