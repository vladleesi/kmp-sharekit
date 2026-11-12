pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository { maven { url = uri("../../sharekit/build/maven-repository") } }
            filter { includeGroup("dev.vladleesi") }
        }
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../../gradle/libs.versions.toml")) }
    }
}
rootProject.name = "sharekit-consumer"
