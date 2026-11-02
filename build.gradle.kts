plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.android.multiplatform) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint.engine)
}

tasks.register("clean", Delete::class) {
    delete(layout.buildDirectory)
}
