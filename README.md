# ShareKit

Text, URL, and file sharing for Kotlin Multiplatform on Android, iOS, JVM desktop, and browsers. Use it from shared Kotlin or Compose UI. The core library runs independently of Compose and coroutines.

[![Maven Central](https://img.shields.io/maven-central/v/dev.vladleesi/kmp-sharekit)](https://central.sonatype.com/artifact/dev.vladleesi/kmp-sharekit)
[![Build](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml/badge.svg)](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml)

## Install

Use Kotlin 2.4.21+ and `mavenCentral()`. Include `google()` for Android dependencies. Add to your shared module:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("dev.vladleesi:kmp-sharekit:0.8.0")
    }
}
```

## Share

Call from your UI action (directly from a click/tap handler in browsers):

```kotlin
import dev.vladleesi.kmpsharekit.ShareKit
import dev.vladleesi.kmpsharekit.ShareRequest
import dev.vladleesi.kmpsharekit.ShareResult

ShareKit.share(ShareRequest(text = "Take a look", url = "https://example.com")) { result ->
    when (result) {
        is ShareResult.Failed -> println("Sharing failed: ${result.reason}")
        ShareResult.Cancelled, ShareResult.CancelledOrUnavailable -> Unit
        ShareResult.Launched, ShareResult.Completed -> Unit
    }
}
```

Requests accept text, an HTTP(S) URL, or both. An optional `title` is supported on Android, desktop, and browsers; iOS rejects requests containing a title. Cancellation is not an error, and launch/completion does not confirm delivery to a recipient.

For simple text sharing without a result callback:

```kotlin
ShareKit.shareText("Hello from shared Kotlin!")
```

On JVM, this legacy call opens an email draft. Use `share(request)` for native macOS/Windows sharing and typed failures.

## Share files and images

Supply each file's bytes, filename with extension, and MIME type. Use a single-item list to share one file.

```kotlin
import dev.vladleesi.kmpsharekit.ShareFile
import dev.vladleesi.kmpsharekit.ShareFilesRequest

val request = ShareFilesRequest(
    files = listOf(
        ShareFile("photo.png", "image/png", pngBytes),
        ShareFile("notes.pdf", "application/pdf", pdfBytes),
    ),
    text = "Photos and notes from the trip",
)
// In your click or tap handler
ShareKit.shareFiles(request, onResult)
```

`ShareFile` copies the supplied bytes. Load and encode them before the UI action. `canShareFiles(request)` checks the platform and payload without launching UI or requiring a browser gesture. Call `shareFiles` directly from a browser gesture. Browser support varies by file type and size. Linux email sharing has no attachment support, and iOS file requests accept optional text only. Apple and Windows use filename extensions to identify file types. Android and JVM desktop stage temporary files for the receiving app.

## Compose integration

Add `dev.vladleesi:kmp-sharekit-compose:0.8.0` to your Compose module. It includes ShareKit and exposes observable sharing state for your existing UI.

```kotlin
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
```

Use `sharing.shareFiles(request)` for attachments. Start shares directly from the click handler to preserve browser user activation. The Compose artifact supports Android, JVM desktop (Java 11+), iOS devices and Apple Silicon simulators, JS, and WasmJS. The core artifact also supports Intel iOS simulators.

Try the [interactive samples](samples/README.md) on Android, iOS, desktop, and the browser.

## Platforms

| Platform | Sharing | Setup |
| --- | --- | --- |
| Android 23+ | Native share sheet | Register your Activity below; call on the main thread |
| iOS 15+ | Native share sheet, including iPad | Visible view controller; call on the main thread |
| JVM desktop | Native sharing on macOS 13+ and Windows 10 1607+ (x64 JVM); email on Linux | [macOS](docs/macos.md) / [Windows packaging](docs/windows.md), or a configured mail client |
| JS / WasmJS | Web Share API | Supported browser, HTTPS, and a user gesture |

Android: register the current Activity when it resumes:

```kotlin
override fun onResume() {
    super.onResume()
    ShareKit.setActivity(this)
}
```

Alternatively, use `ShareKit.shareText(activity, text)` from Android code. iOS needs no registration with a visible view controller, including `ComposeUIViewController`.

Browsers require Web Share support; not every desktop or mobile browser provides it. Call directly from the click handler, before suspending or starting network work. WasmJS also needs a compatible Kotlin/Wasm browser runtime.

## Desktop sharing

On macOS, `share(request)` presents a picker anchored in the active window. Package the native library using the [macOS setup guide](docs/macos.md). Dismissing the picker reports `Cancelled`; selecting a service reports `Launched`. The service's later outcome is not observed.

On Windows, `share(request)` opens the Windows share panel for the foreground window belonging to your application. Package the x64 DLL using the [Windows setup guide](docs/windows.md). A successful panel launch reports `Launched`; subsequent selection, cancellation, and delivery are not observed.

Linux opens an email draft through the default mail client. On any desktop, select email explicitly from `jvmMain`:

```kotlin
import dev.vladleesi.kmpsharekit.ShareMethod

ShareKit.share(
    request = ShareRequest(text = "Hello", title = "A quick note"),
    method = ShareMethod.EMAIL,
) { result ->
    // Handle launch or failure; the user still chooses recipients and sends.
}
```

Use `ShareKit.capabilities(ShareMethod.EMAIL)` to check email support. Desktop sharing needs a graphical environment; email also requires a configured mail client.

For projects with unsupported targets, add ShareKit to a source set shared only by supported targets.

[Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Roadmap](ROADMAP.md) · [Apache 2.0](LICENSE)
