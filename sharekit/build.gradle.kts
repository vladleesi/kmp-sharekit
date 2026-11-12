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
        minSdk = 21
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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.robolectric)
        }
    }
}

mavenPublishing {
    configureBasedOnAppliedPlugins(javadocJar = JavadocJar.Dokka("dokkaGeneratePublicationHtml"))
    // Remote publication is an explicit release workflow step.
    publishToMavenCentral(automaticRelease = false)
    pom {
        name.set("ShareKit")
        description.set(
            "Text sharing for Kotlin Multiplatform and Compose Multiplatform on Android, iOS, JVM desktop, and supported browsers (JS/WasmJS).",
        )
        url.set("https://github.com/vladleesi/kmp-sharekit")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("vladleesi")
                name.set("Vladislav Kochetov")
                url.set("https://github.com/vladleesi")
            }
        }
        scm {
            url.set("https://github.com/vladleesi/kmp-sharekit")
            connection.set("scm:git:https://github.com/vladleesi/kmp-sharekit.git")
            developerConnection.set("scm:git:ssh://git@github.com/vladleesi/kmp-sharekit.git")
            tag.set("v${project.version}")
        }
    }
}

publishing {
    repositories {
        maven {
            name = "validation"
            url =
                layout.buildDirectory
                    .dir("maven-repository")
                    .get()
                    .asFile
                    .toURI()
        }
    }
}

tasks.withType<Jar>().configureEach {
    from(rootProject.file("LICENSE")) {
        into("META-INF")
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
