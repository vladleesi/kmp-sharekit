import com.vanniktech.maven.publish.JavadocJar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

ktlint { version.set(libs.versions.ktlint.engine) }

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()

    jvm { compilerOptions.jvmTarget.set(JvmTarget.JVM_11) }
    android {
        namespace = "dev.vladleesi.kmpsharekit.compose"
        compileSdk = 37
        minSdk = 23
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    iosArm64()
    iosSimulatorArm64()
    js { browser() }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            api(project(":sharekit"))
            api(libs.compose.runtime)
        }
        jvmTest.dependencies { implementation(kotlin("test")) }
    }
}

mavenPublishing {
    coordinates(artifactId = "kmp-sharekit-compose")
    configureBasedOnAppliedPlugins(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"))
    pom {
        name.set("ShareKit Compose")
        description.set("Observable sharing state for ShareKit in Compose Multiplatform.")
    }
}
