plugins { alias(libs.plugins.android.library) }

android {
    namespace = "dev.lingmulongtai.cadence.data"
    compileSdk { version = release(37) }
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { jvmToolchain(17) }

dependencies {
    debugImplementation(project(":motion"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    // Android provides JSONObject on devices; this implementation is only for local JVM tests.
    testImplementation(libs.json)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
