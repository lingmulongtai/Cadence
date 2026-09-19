plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.junit.launcher)
}

tasks.test { useJUnitPlatform() }

tasks.register<JavaExec>("replay") {
    group = "verification"
    description = "Replay a recorded CSV in timestamp order without an Android device."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.lingmulongtai.cadence.motion.ReplayMainKt"
    val input = providers.gradleProperty("inputCsv")
    val output = providers.gradleProperty("outputCsv")
    doFirst {
        require(input.isPresent) { "Pass -PinputCsv=path/to/recording.csv" }
        args(rootProject.file(input.get()).absolutePath)
        if (output.isPresent) args(rootProject.file(output.get()).absolutePath)
    }
}
