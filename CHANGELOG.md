# Changelog

## 0.8.0

- Publish an optional `kmp-sharekit-compose` artifact with `rememberShareState()` for observable sharing progress and results.
- Add runnable Compose samples for Android, iOS, JVM desktop, JS, and WasmJS that share text, links, and a batch of attachments.

## 0.7.0 — 2026-11-17

- Share one or more files, including encoded images, from common Kotlin using `ShareFile`, `ShareFilesRequest`, and `ShareKit.shareFiles`.
- Send files through the Android and iOS share sheets, native macOS and Windows pickers, and the Web Share API on supporting JS and WasmJS browsers.
- Check file support with `ShareKit.canShareFiles`. Browser checks use the actual payload, and the Linux email method reports file attachments as unsupported.
- Raise Android's minimum SDK to API 23 and update AndroidX Core to 1.19.1, AGP to 9.3.3, and Dokka to 2.3.0.

## 0.6.0 — 2026-11-16

- Add native Windows sharing to the JVM request API through the Windows share panel, with text, HTTP(S) URLs, and optional titles.
- Default to native sharing on Windows 10 version 1607+ with an x64 JVM. Explicit email sharing and legacy `shareText` retain their existing behavior; native failures never switch to email automatically.
- Bundle a Windows x64 JNI bridge alongside the universal macOS bridge. Applications package the DLL on their native library path; see the Windows setup guide.
- Report `Launched` when Windows opens the panel. Target selection, dismissal, and delivery are not observed. Report unavailable native support, missing application hosts, permission failures, and platform errors.
- Add Windows native payload, UI-thread dispatch, and host cleanup tests. Require the tested Windows DLL in validated and signed Maven publications.

## 0.5.0 — 2026-11-12

- Add native macOS 13+ sharing to the JVM request API, anchored in the active window, with picker cancellation, handoff, busy, and host failure results.
- Keep legacy JVM `shareText` and `isSupported` email behavior and add explicit desktop method selection for `share` and `capabilities`.
- Bundle an Apple Silicon/Intel JNI bridge in the JVM artifact. Load it by name with `System.loadLibrary`; applications package the library on their native library path.
- Add native session lifecycle tests, JVM routing tests, packaging checks, and desktop consumer coverage.
- Shorten the README while retaining usage examples; add a separate macOS packaging guide.

## 0.4.0 — 2026-11-12

- Restore automatic GitHub and Maven Central releases after CI validation.
- Add common ShareRequest, ShareResult, ShareFailure, ShareCapabilities, and ShareMethod APIs.
- Add share(request, onResult), capabilities(), and canShare(request), preserving all existing sharing signatures.
- Support text and URL payloads on every platform, and optional title metadata on Android, JVM, and browsers; explicitly report unsupported iOS titles.
- Report platform launch/completion, cancellation, ambiguous browser aborts, and typed failures. Guard overlapping iOS/browser requests and support explicit mobile presentation hosts.
- Extend platform tests, publication consumer coverage, API baselines, and migration documentation. Run JS and WasmJS tests in both Chrome and Firefox and cover missing/denied browser APIs.

## 0.3.1

- Fix browser CI failing on stale npm workspace versions; add an early version consistency check.
- Add checked-in API baselines and CI compatibility validation.
- Generate and package HTML API documentation with every Maven publication.
- Compile an independent consumer against all platform publications.
- Switch to manually triggered releases.

## 0.3.0 — 2026-11-12

- Add a JVM desktop target for macOS, Windows, and Linux using Java Desktop MAIL to open an email draft with the shared text. Requires a graphical desktop with MAIL support and a configured mail client; does not send email or copy text automatically.
- Add desktop capability checks, URI encoding and failure tests, CI on all three desktop operating systems, and validation of the JVM Maven publication.

## 0.2.0 — 2026-11-02

- Add Kotlin/JS and Kotlin/WasmJS browser targets with native text sharing through the Web Share API. WASI is not supported.
- Preserve `ShareKit.shareText(text): Unit` and existing Android and iOS behavior. Browser calls start sharing immediately, ignore cancellation, and log asynchronous errors.
- Add common `isSupported()` for Android, iOS, JS, and WasmJS, plus browser-only `shareText(text, onResult)` with original browser error names exposed through `BrowserShareException`.
- Check secure-context and Web Share availability and user activation when exposed. Never silently copy text to the clipboard.
- Add shared browser tests running on both JS and WasmJS, browser CI checks, and validation of all seven Maven Central publications.
- Document asynchronous results, HTTPS, user gestures, permissions policy, browser limitations, and explicit fallback UX.
- Document common Kotlin and Compose Multiplatform usage, including mobile/desktop browser support and native host setup.

## 0.1.0 — 2026-11-02

- Prepare the initial Maven Central release of ShareKit, supporting plain-text sharing on Android and iOS.
- Migrate to Kotlin 2.4.21, AGP 9's Android KMP library plugin, and Gradle 9.7.0.
- Rename the library module from `shared` to `sharekit` and the exported framework from `shared` to `ShareKit`.
- Rename the package to `dev.vladleesi.kmpsharekit`. Update existing imports from `dev.vladleesi.kmmsharekit`.
- Raise the implicit iOS 9 deployment target to iOS 15: the current Kotlin/Native toolchain ships runtime objects built for iOS 15. Android's minimum remains API 21.
- Add explicit presentation-host overloads, main-thread checks, and Android lifecycle validation.
- Replace Android's retained Activity callback with a weak reference. `setActivityProvider` is deprecated and now resolves its callback once at registration; register with `setActivity` in `onResume` instead.
- Handle iOS scenes, visible controllers, and iPad share-sheet anchoring. Calls without a usable host now raise `IllegalStateException` instead of silently doing nothing on iOS.
- Add platform tests, formatting checks, and CI.
- Configure signed KMP publications and a GitHub Actions release workflow for Maven Central.
