import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.npm.NpmExtension
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin

plugins {
    base
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint.engine)
}

// Use the installed Node.js/npm so toolchain downloads do not add project repositories.
allprojects {
    plugins.withType<NodeJsPlugin> {
        the<NodeJsEnvSpec>().download = false
    }
    plugins.withType<WasmNodeJsPlugin> {
        the<WasmNodeJsEnvSpec>().download = false
    }
}

// Karma and its plugins must load one webpack version, including transitive peer dependencies.
plugins.withType<NodeJsRootPlugin> {
    val webpackVersion = libs.versions.webpack.get()
    the<NodeJsRootExtension>().versions.webpack.version = webpackVersion
    the<NpmExtension>().override("webpack", webpackVersion)
}
