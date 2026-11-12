import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform)
}

val releaseProperties = Properties().apply {
    file("../../gradle.properties").inputStream().use { load(it) }
}

kotlin {
    jvm("desktop")
    android {
        namespace = "dev.vladleesi.sharekit.consumer"
        compileSdk = 37
        minSdk = 21
    }
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    iosX64()
    sourceSets.commonMain.dependencies {
        implementation("dev.vladleesi:kmp-sharekit:${releaseProperties.getProperty("VERSION_NAME")}")
    }
}
