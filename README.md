# ShareKit

Text and URL sharing for Kotlin Multiplatform on Android, iOS, JVM desktop, and browsers. Use it from shared Kotlin or Compose UI; the library has no Compose or coroutine dependency.

[![Maven Central](https://img.shields.io/maven-central/v/dev.vladleesi/kmp-sharekit)](https://central.sonatype.com/artifact/dev.vladleesi/kmp-sharekit)
[![Build](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml/badge.svg)](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml)

## Install

Use Kotlin 2.4.21+ and `mavenCentral()`. Add to your shared module:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("dev.vladleesi:kmp-sharekit:0.5.0")
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

On JVM, this legacy call opens an email draft. Use `share(request)` for the native macOS picker and typed failures.

## Compose example

Use the same request API from `commonMain`. Pass the result to your app's error or status UI:

```kotlin
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ShareButton(text: String, onResult: (ShareResult) -> Unit) {
    val request = ShareRequest(text = text)
    Button(
        enabled = ShareKit.canShare(request),
        onClick = { ShareKit.share(request, onResult) },
    ) {
        Text("Share")
    }
}
```

`canShare(request)` checks content and capability, not whether a host or share target is ready. Always handle failures. Your app can offer a Copy action when sharing is unavailable; ShareKit does not copy content or switch to email automatically.

## Platforms

| Platform | Sharing | Setup |
| --- | --- | --- |
| Android 21+ | Native share sheet | Register your Activity below; call on the main thread |
| iOS 15+ | Native share sheet, including iPad | Visible view controller; call on the main thread |
| JVM desktop | Native picker on macOS 13+; email on Windows/Linux | [macOS packaging](docs/macos.md), or a configured mail client |
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

Windows and Linux open an email draft through the default mail client. On any desktop, select email explicitly from `jvmMain`:

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
