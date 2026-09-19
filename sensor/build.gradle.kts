plugins { alias(libs.plugins.android.library) }

android {
    namespace = "dev.lingmulongtai.cadence.sensor"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies { implementation(project(":motion")) }
