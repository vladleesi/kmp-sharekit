plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ktlint)
}

ktlint { version.set(libs.versions.ktlint.engine) }

android {
    namespace = "dev.vladleesi.sharekit.sample"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.vladleesi.sharekit.sample"
        minSdk = 23
        targetSdk = 37
        versionCode = 1
        versionName = providers.gradleProperty("VERSION_NAME").get()
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":samples:compose"))
    implementation(project(":sharekit"))
    implementation(libs.androidx.activity.compose)
}
