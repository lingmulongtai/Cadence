plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.lingmulongtai.cadence"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "dev.lingmulongtai.cadence"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.1.0-alpha.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    // The public recorder deliberately uses the debug source set. Keep a stable dedicated
    // signing identity for phone updates, while ordinary developer builds use the local debug key.
    providers.environmentVariable("CADENCE_SIGNING_STORE_FILE").orNull?.let { keyPath ->
        val recorder = signingConfigs.create("recorder") {
            storeFile = file(keyPath)
            storePassword = providers.environmentVariable("CADENCE_SIGNING_PASSWORD").get()
            keyAlias = "cadence"
            keyPassword = storePassword
        }
        buildTypes.getByName("debug").signingConfig = recorder
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":overlay"))
    implementation(project(":data"))
    implementation(libs.androidx.core)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.coroutines.android)
}
