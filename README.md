# ShareKit

Lightweight native text sharing for Kotlin Multiplatform and Compose Multiplatform.

[![Version](https://img.shields.io/badge/version-0.4.0-blue)](CHANGELOG.md)
[![Build](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml/badge.svg)](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/dev.vladleesi/kmp-sharekit)](https://central.sonatype.com/artifact/dev.vladleesi/kmp-sharekit)
[![License](https://img.shields.io/badge/license-Apache_2.0-blue)](LICENSE)

## Targets

| Target | Sharing | Requirements |
| --- | --- | --- |
| `android` | Native share sheet | Android 21+; register the current Activity |
| `iosArm64`, `iosSimulatorArm64`, `iosX64` | Native share sheet, including iPad | iOS 15+; a visible view controller |
| `jvm` (desktop) | Email draft in the default mail client | Graphical desktop with Java Desktop MAIL support and a configured mail client |
| `js`, `wasmJs` with `browser()` | Browser's native share UI | Web Share support, a secure context, and a user gesture |

JVM desktop supports Compose Multiplatform apps on macOS, Windows, and Linux where the runtime provides Desktop MAIL support. Browser sharing works on supported **desktop and mobile browsers**, not every browser. WasmJS also requires [Kotlin/Wasm browser support](https://kotlinlang.org/docs/wasm-configuration.html). Kotlin/Native desktop and WASI targets are unsupported.

## Installation

Release commits are published to Maven Central by CI after all validation jobs pass.

Use Kotlin 2.4.21+ and Maven Central. Add to your shared module:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("dev.vladleesi:kmp-sharekit:0.4.0")
    }
}
```

Use `commonMain` when all your targets are supported. Projects with unsupported targets should use a source set shared only by supported targets.

## Shared usage

Both `isSupported()` and `shareText()` are available in **`commonMain`** for Android, iOS, JVM desktop, JS, and WasmJS. For example, in Compose Multiplatform:

```kotlin
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import dev.vladleesi.kmpsharekit.ShareKit

@Composable
fun ShareButton(text: String) {
    Button(
        enabled = ShareKit.isSupported(),
        onClick = { ShareKit.shareText(text) },
    ) {
        Text("Share")
    }
}
```

Plain Kotlin can call `ShareKit.shareText(text)` too; Compose is optional and is not a library dependency.

`isSupported()` returns `true` on Android/iOS, checks Desktop MAIL support on JVM, and checks secure-context and Web Share availability in browsers. It checks capability, not host readiness, mail client configuration, user activation, permissions, or delivery.

## Structured sharing (0.4.0)

Use the common API when your UI needs results or separate URL/title fields:

```kotlin
val request = ShareRequest(text = "Check this out", url = "https://example.com")
// Call directly from onClick, on the UI thread.
ShareKit.share(request) { result ->
    when (result) {
        is ShareResult.Failed -> showError(result.reason)
        ShareResult.Cancelled, ShareResult.CancelledOrUnavailable -> Unit
        ShareResult.Launched, ShareResult.Completed -> Unit
    }
}
```

Import `ShareRequest`, `ShareResult`, and `ShareKit` from `dev.vladleesi.kmpsharekit`; `showError` is application code. `capabilities()` reports `NATIVE`, `EMAIL`, or `UNAVAILABLE`, plus `supportsTitle`. `canShare(request)` checks content and capability without a gesture, but does not guarantee a ready host, permissions, or a configured mail client. Always handle the result of `share` even after a positive check.

Requests require nonblank text or an absolute HTTP(S) URL. URL parsing follows the platform parser; use encoded URLs with an ASCII hostname for portability. Title-only requests are invalid. Payload text is preserved. The legacy `shareText` methods retain their existing behavior, including handling empty strings.

| Platform | Text and URL | Title | Result timing |
| --- | --- | --- | --- |
| Android | Joined with a newline in `EXTRA_TEXT` | `EXTRA_SUBJECT` and `EXTRA_TITLE`; recipients may ignore metadata | `Launched` immediately after starting the chooser |
| iOS | Separate string and native URL activity items | Unsupported; returns `UNSUPPORTED_CONTENT` when supplied | Activity completion or cancellation |
| JVM desktop | Joined with a newline in the email body | Email subject | `Launched` immediately after opening the composer |
| Browser | Separate Web Share fields | Web Share title; targets may ignore it | Promise resolution or rejection |

The callback receives one terminal result. Preflight errors are synchronous. Android and JVM cannot observe later cancellation or delivery. iOS and browsers report `Completed` only as a platform completion signal; it does not confirm delivery. Browser `AbortError` maps to `CancelledOrUnavailable`. Unexpected platform failures retain their cause, including browser error names. Exceptions thrown by your callback are not converted into sharing failures.

New iOS and browser requests return `BUSY` while a prior new-API request is pending; Android and JVM requests can launch separate choosers/composers. Avoid mixing old and new calls concurrently. Callbacks use the platform callback thread (UI thread on iOS; browser event loop), or the calling thread for synchronous results. Calls from the wrong thread on Android/iOS report `WRONG_THREAD` without dispatching automatically.

For multiple windows, use the Android overload `share(activity, request, onResult)` or iOS overload `share(request, presentingViewController, onResult)`. The core has no Compose or coroutine dependency and performs no automatic clipboard or email fallback.

## Compatibility and fallback UX

Support is determined at runtime, rather than from browser or OS names. A running JS app can receive `UNAVAILABLE` when Web Share is absent, including embedded browsers and insecure contexts. WasmJS still requires a compatible Wasm runtime; use a JS build when supporting browsers without Kotlin/Wasm support. JVM desktop requires the `java.desktop` module and reports unavailable sharing in headless environments. Kotlin/Native desktop targets remain outside the supported target set.

Keep the content accessible even when sharing is unavailable. Offer an explicit Copy action if your app's clipboard API is supported, and a selectable text field when clipboard access is denied. These are app UI responsibilities; the core library has no UI dependency. For example:

```kotlin
ShareKit.share(request) { result ->
    if (result is ShareResult.Failed) {
        // Application-owned dialog with selectable content and an optional Copy button.
        showSelectableText(listOfNotNull(request.text, request.url).joinToString("\n"))
    }
}
```

Do not interpret cancellation as an error or trigger a fallback automatically after cancellation. Browser and OS APIs can impose runtime restrictions even after a successful capability check. Native UI smoke checks on Safari/iOS, Android, Windows, macOS, and the supported Linux desktop environments complement automated tests; universal native UI availability is not promised.

## Platform setup

**Android:** register the Activity hosting your UI whenever it resumes. Sharing must run on the main thread.

```kotlin
override fun onResume() {
    super.onResume()
    ShareKit.setActivity(this)
}
```

ShareKit keeps a weak reference. Alternatively, use `ShareKit.shareText(activity, text)` directly.

**iOS:** no registration is needed with a visible view controller, including `ComposeUIViewController`. Sharing runs on the main thread and anchors the sheet for iPad. For a specific window, use `ShareKit.shareText(text, presentingViewController = controller)`.

**JVM desktop:** enable `jvm()` or a named JVM target such as `jvm("desktop")` in your KMP module. No registration or additional dependencies are needed. `ShareKit.shareText(text)` opens an email draft with the text in its body through [Java Desktop MAIL](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/java/awt/Desktop.html#mail(java.net.URI)). The user chooses recipients and sends it. This is an email composer; the JVM does not provide a portable system share sheet.

Headless environments and desktops without MAIL support return `false` from `isSupported()` and throw `UnsupportedOperationException` when sharing. A supported desktop can still fail to launch a configured client; launch failures throw `IllegalStateException` with the original cause. Mail clients may limit payload length or normalize line endings. Custom runtime images must include the `java.desktop` module. ShareKit never copies text automatically.

**Web:** enable `js { browser() }` and/or `wasmJs { browser() }` in your KMP module. Call sharing directly from `onClick` or a click/tap handler, before deferred coroutine or network work.

## Browser limitations

- Requires HTTPS or a trustworthy local context such as localhost. Embedded apps may also need `allow="web-share"` and a compatible Permissions Policy.
- `shareText(text)` returns immediately. Unsupported browsers or invalid preconditions throw; later browser errors are logged and cancellation is ignored.
- To handle results in your UI, use the callback overload from **`jsMain`, `wasmJsMain`, or `webMain`**. You can pass this handler into shared Compose UI:

```kotlin
import dev.vladleesi.kmpsharekit.BrowserShareException

ShareKit.shareText("Hello") { result ->
    result.onFailure { error ->
        if (error !is BrowserShareException || error.name != "AbortError") {
            // Show an error or offer an explicit Copy action.
        }
    }
}
```

`BrowserShareException` preserves the browser error name and message. `AbortError` means cancellation or no share targets. Browsers may reject empty text; success does not guarantee delivery. ShareKit never copies text automatically. See the [Web Share specification](https://www.w3.org/TR/web-share/).

## More

Currently supports plain text. See [CHANGELOG.md](CHANGELOG.md) and [CONTRIBUTING.md](CONTRIBUTING.md) for release notes, checks, and publishing.

[Apache License 2.0](LICENSE) · [Code of Conduct](CODE_OF_CONDUCT.md)

Release milestones and approval checkpoints are tracked in [ROADMAP.md](ROADMAP.md).
