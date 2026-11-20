# ShareKit

Share text, links, and files from Kotlin Multiplatform apps on Android, iOS, desktop, and the web.

[![Maven Central](https://img.shields.io/maven-central/v/dev.vladleesi/kmp-sharekit)](https://central.sonatype.com/artifact/dev.vladleesi/kmp-sharekit)
[![CI](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml/badge.svg)](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml)

## Install

Use Kotlin 2.4.21 or later. Add these dependency repositories in `settings.gradle.kts`.

```kotlin
dependencyResolutionManagement {
    repositories {
        google() // AndroidX dependencies
        mavenCentral()
    }
}
```

In your shared module’s `build.gradle.kts`, add ShareKit to `commonMain`.

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("dev.vladleesi:kmp-sharekit:0.9.0")
    }
}
```

## Platform setup

| Platform | Setup | Share types |
| --- | --- | --- |
| Android 23+ | Register the current Activity and call on the main thread | Text, links, files |
| iOS 15+ | Call on the main thread with a visible view controller | Text, links, files |
| macOS 13+ | Use an active window and package the [macOS native library](docs/macos.md) | Text, links, files |
| Windows 10 1607+ / 11 | Use an x64 JVM and a foreground window. Package the [Windows native library](docs/windows.md) | Text, links, files |
| Linux desktop | Use a graphical session with a configured mail client | Text and links in an email draft |
| Browser (JS / WasmJS) | Use a secure context and a browser with Web Share support. Call directly from a click or tap | Text, links, supported files |

On Android, register the Activity in `onResume()`.

```kotlin
override fun onResume() {
    super.onResume()
    ShareKit.setActivity(this)
}
```

On iOS, ShareKit finds the visible view controller automatically. Leave the optional `title` field unset.

## Usage

Use this helper to handle the result of any share action.

```kotlin
import dev.vladleesi.kmpsharekit.ShareResult

fun ShareResult.statusMessage(): String? = when (this) {
    ShareResult.Launched -> "Sharing started."
    ShareResult.Completed -> "Share action completed."
    ShareResult.Cancelled -> null
    ShareResult.CancelledOrUnavailable -> "Sharing was cancelled or no share target was available."
    is ShareResult.Failed -> "Sharing failed ($reason)."
}
```

`Failed.reason` identifies the failure category, and `Failed.cause` may contain platform details. `Launched` and `Completed` leave delivery to the recipient unverified.

Core and Compose callers can handle every `ShareResult` and `ShareFailure` in an exhaustive `when`. During 1.x, adding, removing, or renaming an outcome or failure category requires a major release. New platform errors map to the existing categories, with additional details available through `Failed.cause`.

Pick a call for your share button in shared Kotlin.

```kotlin
import dev.vladleesi.kmpsharekit.ShareFile
import dev.vladleesi.kmpsharekit.ShareFilesRequest
import dev.vladleesi.kmpsharekit.ShareKit
import dev.vladleesi.kmpsharekit.ShareRequest
import dev.vladleesi.kmpsharekit.ShareResult

val onResult: (ShareResult) -> Unit = { result ->
    result.statusMessage()?.let { println(it) }
}

// Text
ShareKit.share(ShareRequest(text = "Meet at 6"), onResult)

// Link with optional text
ShareKit.share(ShareRequest(url = "https://example.com", text = "Event details"), onResult)

// File
val notes = ShareFile("notes.txt", "text/plain", "Meet at 6".encodeToByteArray())
ShareKit.shareFiles(ShareFilesRequest(listOf(notes)), onResult)
```

For an image, pass encoded bytes with a matching name and MIME type, such as `ShareFile("photo.png", "image/png", pngBytes)`. Add more entries to share multiple files. Check `canShare(request)` or `canShareFiles(request)` before showing the share button if support varies.

To open an email draft explicitly from JVM code, call `ShareKit.share(request, ShareMethod.EMAIL, onResult)`.

## Compose Multiplatform

Add the Compose adapter to `commonMain.dependencies`. It includes the core library. [Maven Central](https://central.sonatype.com/artifact/dev.vladleesi/kmp-sharekit-compose)

```kotlin
implementation("dev.vladleesi:kmp-sharekit-compose:0.9.0")
```

This example uses Compose Material 3. The button is enabled when the request can be shared, and failures appear below it.

```kotlin
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import dev.vladleesi.kmpsharekit.ShareKit
import dev.vladleesi.kmpsharekit.ShareRequest
import dev.vladleesi.kmpsharekit.ShareResult
import dev.vladleesi.kmpsharekit.compose.rememberShareState

@Composable
fun ShareButton(text: String) {
    val request = ShareRequest(text = text)
    val sharing = rememberShareState()
    Column {
        Button(
            enabled = !sharing.isSharing && ShareKit.canShare(request),
            onClick = { sharing.share(request) },
        ) {
            Text("Share")
        }
        val result = sharing.result
        if (result is ShareResult.Failed) {
            Text("Sharing failed: ${result.reason}")
        }
    }
}
```

Use `sharing.shareFiles(filesRequest)` in `onClick` to share attachments.

## Sample app demos

The same Compose sample opens the platform’s share picker with text, a link, or the bundled files. [Run the samples](samples/README.md).

| Android | iOS |
| --- | --- |
| ![ShareKit sample opening the Android Sharesheet for text, a link, and two files](docs/media/android-share-demo.gif) | ![ShareKit sample opening and dismissing the iOS share sheet](docs/media/ios-share-demo.gif) |

| macOS | Web (WasmJS in Chrome) |
| --- | --- |
| ![ShareKit desktop sample opening the native macOS share picker](docs/media/macos-share-demo.gif) | ![ShareKit web sample opening the share picker through Chrome's Web Share API](docs/media/web-share-demo.gif) |

[Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Roadmap](ROADMAP.md) · [Apache 2.0](LICENSE)
