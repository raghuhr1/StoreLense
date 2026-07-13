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
    namespace  = "com.storelense.c66"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.storelense.c66"
        minSdk        = 29
        targetSdk     = 35
        versionCode   = 2
        versionName   = "1.1.0"
    }

    flavorDimensions += "rfid"
    productFlavors {
        create("mock") {
            dimension = "rfid"
            buildConfigField("Boolean", "USE_MOCK_RFID", "true")
            applicationIdSuffix = ".mock"
            versionNameSuffix   = "-mock"
        }
        create("chainway") {
            dimension = "rfid"
            buildConfigField("Boolean", "USE_MOCK_RFID", "false")
        }
    }

    buildTypes {
        debug {
            val url = localProps.getProperty("storelense.debug.url", "http://10.0.2.2:8080/")
                .trimEnd('/') + "/"
            buildConfigField("String", "BASE_URL", "\"$url\"")
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

    buildFeatures { compose = true; buildConfig = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.splash)
    implementation(libs.coroutines.android)

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

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)
    ksp(libs.hilt.work.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // CameraX + ML Kit barcode
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)

    // Room (offline queue)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // WorkManager (background sync)
    implementation(libs.work.runtime)

    implementation(libs.security.crypto)
    implementation(libs.timber)
}
