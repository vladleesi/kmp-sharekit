# Windows desktop setup

The JVM request API uses the Windows share panel through `IDataTransferManagerInterop` on Windows 10 version 1607+ and Windows 11 with an x64 JVM. ARM64 and 32-bit JVMs are not supported by the bundled DLL. A visible, non-minimized foreground window must belong to the current process. AWT/Swing and Compose Desktop windows work without registration.

## Package the native library

The published JVM jar contains `native/windows-x64/sharekit_windows.dll`. Extract it at build time and include it alongside your application's native libraries. ShareKit uses `System.loadLibrary("sharekit_windows")`; it does not extract or execute binaries from temporary directories.

For a local run in PowerShell:

```powershell
New-Item -ItemType Directory -Force build/sharekit-native
Push-Location build/sharekit-native
jar xf C:/path/to/kmp-sharekit-jvm-0.7.0.jar native/windows-x64/sharekit_windows.dll
Pop-Location
java '-Djava.library.path=C:/absolute/path/to/build/sharekit-native/native/windows-x64' -jar my-app.jar
```

Set `java.library.path` when starting the JVM; changing it afterward does not update the native search path. Preserve any other native paths your application requires. The bridge uses the static MSVC runtime and Windows system libraries, with no additional JVM or Visual C++ redistributable dependency.

For `jpackage`, copy the DLL beside your application jars in the input directory and add `--java-options '-Djava.library.path=$APPDIR'`. PowerShell single quotes preserve the `$APPDIR` placeholder. Runtime images need `java.desktop`. On JDK 24+, also add `--java-options '--enable-native-access=ALL-UNNAMED'` for classpath applications, or enable access for your named module. Sign the DLL and application as part of your normal Windows distribution process.

Preserve these JNI entry points when shrinking or obfuscating:

```proguard
-keep class dev.vladleesi.kmpsharekit.WindowsSharingBridge { *; }
```

## Runtime behavior

`capabilities()` checks native availability, not whether a foreground application window or share target exists. Missing/incompatible DLLs and unsupported Windows environments report `UNAVAILABLE`; a missing, minimized, or foreign foreground window reports `HOST_UNAVAILABLE`. Windows Server, noninteractive sessions, and some restricted environments may not provide Windows sharing. Handle failures and offer explicit email or Copy in your application as appropriate.

Calls may originate on any Java thread. Native operations run on the foreground window's Windows UI thread; results arrive on AWT. `Launched` means the share panel opened. ShareKit does not observe subsequent dismissal, target selection, or delivery and does not report cancellation for Windows. Overlapping launch operations report `BUSY`; the guard is released after launch or failure.

Text and URLs populate separate Windows data formats. A missing or empty title uses "Share", as Windows requires a title. The data handler remains registered for that window until the next share request or window destruction, retaining the last request so the system can request its data. Native failures never fall back to email; `ShareMethod.EMAIL` and legacy `shareText` remain available explicitly.

Build/test requirements and the publication process are in [Contributing](../CONTRIBUTING.md).
