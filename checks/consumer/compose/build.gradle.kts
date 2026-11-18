import java.util.Properties
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform)
    alias(libs.plugins.compose.compiler)
}

val releaseProperties = Properties().apply {
    rootProject.file("../../gradle.properties").inputStream().use { load(it) }
}

kotlin {
    jvm("desktop") { compilerOptions.jvmTarget.set(JvmTarget.JVM_11) }
    android {
        namespace = "dev.vladleesi.sharekit.consumer.compose"
        compileSdk = 37
        minSdk = 23
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }
    iosArm64()
    iosSimulatorArm64()
    sourceSets.commonMain.dependencies {
        implementation("dev.vladleesi:kmp-sharekit-compose:${releaseProperties.getProperty("VERSION_NAME")}")
    }
}
