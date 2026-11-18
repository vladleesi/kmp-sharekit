# ShareKit samples

One Compose screen runs on Android, iOS, desktop, and the web. Enter text and an HTTP(S) link, or share the bundled PNG and text file together. The screen shows the outcome returned by ShareKit.

Run these commands from the repository root with JDK 21. Use Node.js 24 for the browser samples.

| Host | Command |
| --- | --- |
| Android emulator or device | `./gradlew :samples:android:installDebug`, then open **ShareKit sample** |
| iOS 18.5+ simulator on Apple Silicon | Boot a simulator, then run `bash samples/ios/run.sh` |
| macOS (Apple Silicon), Windows (x64), or Linux desktop | `./gradlew :samples:compose:run` |
| Kotlin/JS browser | `./gradlew :samples:compose:jsBrowserDevelopmentRun` |
| Kotlin/WasmJS browser | `./gradlew :samples:compose:wasmJsBrowserDevelopmentRun` |

Set `IOS_SIMULATOR` to a simulator UDID to select it explicitly. On Windows, the desktop run also builds the x64 [native bridge](../docs/windows.md). The desktop sample on Linux uses the configured email client for text and links.

The browser run tasks print a localhost URL. Open it in a browser with Web Share support and click a share button. If the browser cannot share the payload, the sample displays its failure result.
