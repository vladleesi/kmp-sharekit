import com.vanniktech.maven.publish.JavadocJar
import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.dokka)
}

group = providers.gradleProperty("GROUP").get()
version = providers.gradleProperty("VERSION_NAME").get()

ktlint {
    version.set(libs.versions.ktlint.engine)
}

kotlin {
    explicitApi()
    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    jvm {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
        testRuns["test"].executionTask.configure {
            systemProperty("java.awt.headless", "true")
        }
    }

    android {
        namespace = "dev.vladleesi.kmpsharekit"
        compileSdk = 37
        minSdk = 23
        androidResources.enable = true
        compilerOptions.jvmTarget.set(JvmTarget.JVM_1_8)
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    js {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    useFirefoxHeadless()
                }
            }
        }
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            testTask {
                useKarma {
                    useChromeHeadless()
                    useFirefoxHeadless()
                }
            }
        }
    }

    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { target ->
        target.binaries.framework {
            baseName = "ShareKit"
            isStatic = true
            binaryOption("bundleId", "dev.vladleesi.kmpsharekit")
            binaryOption("bundleShortVersionString", project.version.toString())
            binaryOption("bundleVersion", project.version.toString())
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.core)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
        }
    }
}

mavenPublishing {
    coordinates(artifactId = "kmp-sharekit")
    configureBasedOnAppliedPlugins(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"))
    pom {
        name.set("ShareKit")
        description.set(
            "Text, URL, and file sharing for Kotlin Multiplatform on Android, iOS, JVM desktop, and supported browsers (JS/WasmJS).",
        )
    }
}

// Release publications are built on macOS. Other hosts still build/test the JVM implementation.
if (System.getProperty("os.name").startsWith("Mac")) {
    val nativeOutput = layout.buildDirectory.dir("generated/macos/native/macos")
    val jniHeaders = providers.gradleProperty("sharekit.jniHeaders").orElse("${System.getProperty("java.home")}/include")
    val nativeSource = layout.projectDirectory.file("src/jvmMain/native/ShareKit.m")
    val compileMacSharing =
        tasks.register<Exec>("compileMacSharing") {
            group = "build"
            description = "Builds the universal macOS JNI sharing library for Apple Silicon and Intel."
            inputs.file(nativeSource)
            inputs.dir(jniHeaders)
            outputs.dir(nativeOutput)
            doFirst { nativeOutput.get().asFile.mkdirs() }
            commandLine(
                "xcrun",
                "clang",
                "-dynamiclib",
                "-fobjc-arc",
                "-fblocks",
                "-Wall",
                "-Werror",
                "-arch",
                "arm64",
                "-arch",
                "x86_64",
                "-mmacosx-version-min=13.0",
                "-I${jniHeaders.get()}",
                "-I${jniHeaders.get()}/darwin",
                "-framework",
                "AppKit",
                "-Wl,-install_name,@rpath/libsharekit.dylib",
                nativeSource.asFile.absolutePath,
                "-o",
                nativeOutput
                    .get()
                    .file("libsharekit.dylib")
                    .asFile.absolutePath,
            )
        }
    tasks.named<Copy>("jvmProcessResources") {
        dependsOn(compileMacSharing)
        from(layout.buildDirectory.dir("generated/macos"))
    }
    tasks.named<Test>("jvmTest") {
        dependsOn(compileMacSharing)
        systemProperty("java.library.path", nativeOutput.get().asFile.absolutePath)
    }
    val nativeTestBinary = layout.buildDirectory.file("native-tests/ShareKitNativeTest")
    val compileMacSharingTest =
        tasks.register<Exec>("compileMacSharingTest") {
            group = "build"
            description = "Compiles the macOS native sharing lifecycle tests for this host."
            inputs.file(nativeSource)
            inputs.file("src/jvmTest/native/ShareKitNativeTest.m")
            inputs.dir(jniHeaders)
            outputs.file(nativeTestBinary)
            doFirst {
                nativeTestBinary
                    .get()
                    .asFile.parentFile
                    .mkdirs()
            }
            commandLine(
                "xcrun",
                "clang",
                "-fobjc-arc",
                "-fblocks",
                "-Wall",
                "-Werror",
                "-mmacosx-version-min=13.0",
                "-I${jniHeaders.get()}",
                "-I${jniHeaders.get()}/darwin",
                "-framework",
                "AppKit",
                file("src/jvmTest/native/ShareKitNativeTest.m").absolutePath,
                "-o",
                nativeTestBinary.get().asFile.absolutePath,
            )
        }
    val macSharingTest =
        tasks.register<Exec>("macSharingTest") {
            group = "verification"
            description = "Runs macOS native sharing cancellation, host ownership, and failure tests."
            dependsOn(compileMacSharingTest)
            commandLine(nativeTestBinary.get().asFile.absolutePath)
        }
    tasks.named("jvmTest") { dependsOn(macSharingTest) }
}

tasks.named<Jar>("jvmSourcesJar") {
    from("src/jvmMain/native") { into("native") }
}

if (System.getProperty("os.name").startsWith("Windows")) {
    val nativeBuild = layout.buildDirectory.dir("native-windows")
    val jniHeaders = providers.gradleProperty("sharekit.jniHeaders").orElse("${System.getProperty("java.home")}/include")
    val configureWindowsSharing =
        tasks.register<Exec>("configureWindowsSharing") {
            inputs.file("src/jvmMain/native/windows/CMakeLists.txt")
            inputs.dir(jniHeaders)
            outputs.file(nativeBuild.map { it.file("CMakeCache.txt") })
            commandLine(
                "cmake",
                "-S",
                file("src/jvmMain/native/windows").absolutePath,
                "-B",
                nativeBuild.get().asFile.absolutePath,
                "-A",
                "x64",
                "-DSHAREKIT_JNI_HEADERS=${jniHeaders.get()}",
            )
        }
    val compileWindowsSharing =
        tasks.register<Exec>("compileWindowsSharing") {
            group = "build"
            description = "Builds the Windows x64 JNI sharing bridge and native tests."
            dependsOn(configureWindowsSharing)
            inputs.dir("src/jvmMain/native/windows")
            inputs.dir("src/jvmTest/native/windows")
            outputs.file(nativeBuild.map { it.file("Release/sharekit_windows.dll") })
            outputs.file(nativeBuild.map { it.file("Release/ShareKitWindowsTest.exe") })
            commandLine("cmake", "--build", nativeBuild.get().asFile.absolutePath, "--config", "Release")
        }
    val windowsSharingTest =
        tasks.register<Exec>("windowsSharingTest") {
            group = "verification"
            description = "Tests Windows native payloads, dispatch, and host lifetime."
            dependsOn(compileWindowsSharing)
            commandLine("ctest", "--test-dir", nativeBuild.get().asFile.absolutePath, "-C", "Release", "--output-on-failure", "--verbose")
        }
    tasks.named<Copy>("jvmProcessResources") {
        dependsOn(compileWindowsSharing)
        from(nativeBuild.map { it.dir("Release") }) {
            include("sharekit_windows.dll")
            into("native/windows-x64")
        }
    }
    tasks.named<Test>("jvmTest") {
        dependsOn(windowsSharingTest)
        systemProperty(
            "java.library.path",
            nativeBuild
                .get()
                .dir("Release")
                .asFile.absolutePath,
        )
    }
}

// macOS release jobs consume the DLL compiled and tested on Windows in the same CI run.
providers.gradleProperty("sharekit.windowsNativeDir").orNull?.let { windowsNativeDir ->
    tasks.named<Copy>("jvmProcessResources") {
        from(windowsNativeDir) {
            include("sharekit_windows.dll")
            into("native/windows-x64")
        }
    }
}

tasks.withType<Test>().configureEach {
    // Required by Robolectric on JDK 17+.
    jvmArgs(
        "--add-opens=java.base/java.lang=ALL-UNNAMED",
        "--add-opens=java.base/java.util=ALL-UNNAMED",
        "--add-opens=java.base/java.io=ALL-UNNAMED",
        "--add-opens=java.base/java.net=ALL-UNNAMED",
        "--add-opens=java.base/java.security=ALL-UNNAMED",
        "--add-opens=java.base/java.text=ALL-UNNAMED",
        "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED",
        "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
        "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    )
}
