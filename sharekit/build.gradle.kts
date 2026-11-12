import com.vanniktech.maven.publish.JavadocJar
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
