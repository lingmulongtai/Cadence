plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.lingmulongtai.cadence.overlay"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    implementation(project(":motion"))
    implementation(project(":sensor"))
    implementation(project(":data"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    debugImplementation(libs.coroutines.android)
    debugImplementation(libs.androidx.core)
}
