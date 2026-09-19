plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }
