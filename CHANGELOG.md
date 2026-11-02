# Changelog

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
