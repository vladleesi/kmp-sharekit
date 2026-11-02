# ShareKit

Plain-text sharing for Kotlin Multiplatform apps on Android and iOS.

[![Version](https://img.shields.io/badge/version-0.1.0-blue)](CHANGELOG.md)
[![Build](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml/badge.svg)](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/dev.vladleesi/kmp-sharekit)](https://central.sonatype.com/artifact/dev.vladleesi/kmp-sharekit)
[![License](https://img.shields.io/badge/license-Apache_2.0-blue)](LICENSE)

## Platforms

- Android API 21 and later.
- iOS 15 and later: devices (`iosArm64`), Apple Silicon simulators (`iosSimulatorArm64`), and Intel simulators (`iosX64`).

## Sharing support

| Content | Status |
| --- | --- |
| Plain text | Implemented |
| HTML | Planned |
| Images | Planned |
| Video | Planned |

Planned formats have no release date. ShareKit does not report the selected destination or whether sharing completed.

## Installation

Add Maven Central to your repositories and include ShareKit in your KMP module:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("dev.vladleesi:kmp-sharekit:0.1.0")
        }
    }
}
```

Use Kotlin 2.4.21 or newer to consume the Kotlin metadata. Maven provides the KMP library and platform artifacts; there is no standalone Swift package.

## Usage

All calls must run on the main thread with a visible presentation host.

### Android

Pass the current Activity directly from a click handler:

```kotlin
import dev.vladleesi.kmpsharekit.ShareKit

ShareKit.shareText(activity, "Hello from ShareKit")
```

To call from common code, register the Activity whenever it resumes:

```kotlin
// In your Activity
override fun onResume() {
    super.onResume()
    ShareKit.setActivity(this)
}
```

ShareKit keeps a weak reference and rejects finishing or destroyed Activities. Registering the latest Activity also handles recreation. The deprecated `setActivityProvider` remains available, but now evaluates its callback once when registered.

### Common Kotlin and iOS

```kotlin
import dev.vladleesi.kmpsharekit.ShareKit

ShareKit.shareText("Hello from shared code")
```

On iOS, ShareKit finds the foreground key window and its visible controller, including navigation, tab, and presented controllers. The share sheet is anchored for iPad. When using multiple windows, pass the intended controller from `iosMain`:

```kotlin
ShareKit.shareText("Hello from iOS", presentingViewController = viewController)
```

A missing or invalid host raises `IllegalStateException`. Canceling the system share sheet is handled by the operating system.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for setup, checks, and the release process. Bug reports and focused pull requests are welcome. Follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## License

[Apache License 2.0](LICENSE).
