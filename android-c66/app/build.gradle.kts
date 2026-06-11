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
        versionCode   = 1
        versionName   = "1.0.0"
    }

    buildTypes {
        debug {
            val url = localProps.getProperty("storelense.debug.url", "http://10.0.2.2:8080/")
                .trimEnd('/') + "/"
            buildConfigField("String",  "BASE_URL",      "\"$url\"")
            buildConfigField("Boolean", "USE_MOCK_RFID", "true")
            isDebuggable = true
        }
        release {
            val url = localProps.getProperty("storelense.release.url", "https://api.storelense.internal/")
                .trimEnd('/') + "/"
            buildConfigField("String",  "BASE_URL",      "\"$url\"")
            buildConfigField("Boolean", "USE_MOCK_RFID", "false")
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
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    implementation(libs.security.crypto)
    implementation(libs.timber)
}
