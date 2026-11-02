# ShareKit

Lightweight native text sharing for Kotlin Multiplatform and Compose Multiplatform.

[![Version](https://img.shields.io/badge/version-0.2.0-blue)](CHANGELOG.md)
[![Build](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml/badge.svg)](https://github.com/vladleesi/kmp-sharekit/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/dev.vladleesi/kmp-sharekit)](https://central.sonatype.com/artifact/dev.vladleesi/kmp-sharekit)
[![License](https://img.shields.io/badge/license-Apache_2.0-blue)](LICENSE)

## Targets

| Target | Sharing | Requirements |
| --- | --- | --- |
| `android` | Native share sheet | Android 21+; register the current Activity |
| `iosArm64`, `iosSimulatorArm64`, `iosX64` | Native share sheet, including iPad | iOS 15+; a visible view controller |
| `js`, `wasmJs` with `browser()` | Browser's native share UI | Web Share support, a secure context, and a user gesture |

Browser sharing works on supported **desktop and mobile browsers**, not every browser. WasmJS also requires [Kotlin/Wasm browser support](https://kotlinlang.org/docs/wasm-configuration.html). Native JVM/Desktop and WASI targets are unsupported.

## Installation

Use Kotlin 2.4.21+ and Maven Central. Add to your shared module:

```kotlin
kotlin {
    sourceSets.commonMain.dependencies {
        implementation("dev.vladleesi:kmp-sharekit:0.2.0")
    }
}
```

Use `commonMain` when all your targets are supported. Projects with unsupported targets should use a source set shared only by supported targets.

## Shared usage

Both `isSupported()` and `shareText()` are available in **`commonMain`** for Android, iOS, JS, and WasmJS. For example, in Compose Multiplatform:

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

`isSupported()` returns `true` on Android/iOS and checks secure-context and Web Share availability in browsers. It checks capability, not host readiness, user activation, permissions, or delivery.

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
