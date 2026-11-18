import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

version = providers.gradleProperty("VERSION_NAME").get()

ktlint { version.set(libs.versions.ktlint.engine) }

kotlin {
    android {
        namespace = "dev.vladleesi.sharekit.sample.ui"
        compileSdk = 37
        minSdk = 23
        compilerOptions.jvmTarget.set(JvmTarget.JVM_11)
    }
    jvm("desktop") { compilerOptions.jvmTarget.set(JvmTarget.JVM_11) }
    js {
        browser { commonWebpackConfig { outputFileName = "sharekit-sample.js" } }
        binaries.executable()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser { commonWebpackConfig { outputFileName = "sharekit-sample.js" } }
        binaries.executable()
    }
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "ShareKitSample"
            isStatic = true
            binaryOption("bundleId", "dev.vladleesi.sharekit.sample.framework")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":sharekit-compose"))
            implementation(libs.compose.material)
        }
        getByName("desktopMain").dependencies { implementation(compose.desktop.currentOs) }
    }
}

compose.desktop {
    application {
        mainClass = "dev.vladleesi.sharekit.sample.MainKt"
        val coreBuild = project(":sharekit").layout.buildDirectory
        val os = System.getProperty("os.name")
        val nativeDir =
            when {
                os.startsWith("Mac") -> coreBuild.dir("generated/macos/native/macos") to ":sharekit:compileMacSharing"
                os.startsWith("Windows") -> coreBuild.dir("native-windows/Release") to ":sharekit:compileWindowsSharing"
                else -> null
            }
        if (nativeDir != null) {
            val (directory, buildTask) = nativeDir
            jvmArgs += "-Djava.library.path=${directory.get().asFile.absolutePath}"
            tasks.matching { it.name == "run" }.configureEach { dependsOn(buildTask) }
        }
    }
}
