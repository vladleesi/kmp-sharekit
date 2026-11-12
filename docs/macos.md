# macOS desktop setup

The JVM request API uses `NSSharingServicePicker` on macOS 13+. It requires a graphical app with a visible, active native window and the ShareKit bridge on `java.library.path`. AWT/Swing and Compose Desktop windows work through their AppKit host. No registration is required.

## Package the native library

The published JVM jar contains `native/macos/libsharekit.dylib`, a universal binary for Apple Silicon and Intel. Extract it when building your app, then include it with your app's native libraries. ShareKit loads it using `System.loadLibrary("sharekit")`.

For a local run, extract the library into a build directory:

```sh
mkdir -p build/sharekit-native
cd build/sharekit-native
jar xf /path/to/kmp-sharekit-jvm-0.5.0.jar native/macos/libsharekit.dylib
```

Launch the JVM with `-Djava.library.path=/absolute/path/to/build/sharekit-native/native/macos`. Set the path at JVM startup; changing the property after launch does not update the JVM's native search path.

For `jpackage`, copy the dylib into the input directory alongside your application jars, then add:

```sh
jpackage --type app-image --name MyApp --input build/package-input \
  --main-jar my-app.jar --main-class example.Main \
  --java-options '-Djava.library.path=$APPDIR'
```

The single quotes preserve jpackage's `$APPDIR` placeholder. Retain any other native library paths your application needs. Custom runtime images must include `java.desktop`. With JDK 24+, also pass `--enable-native-access=ALL-UNNAMED` for classpath applications (or enable access for your named module).

Sign the dylib with your application's signing identity before signing/notarizing the finished app. It links only against Apple system frameworks and adds no JVM dependencies. For shrinkers/obfuscators, preserve the JNI entry points and callback:

```proguard
-keep class dev.vladleesi.kmpsharekit.MacSharingBridge { *; }
-keep class dev.vladleesi.kmpsharekit.MacSharingCallback { *; }
```

## Runtime notes

A missing or incompatible library reports `UNAVAILABLE` for native requests. Email remains available through the explicit method shown in the [README](../README.md).

AppKit presentation is dispatched to the main thread. Native outcomes arrive on the AWT event thread; preflight failures are synchronous. One native picker can be pending at a time (`BUSY` otherwise). The picker and its host are retained until handoff, cancellation, or failure, then released. A request without a visible active window reports `HOST_UNAVAILABLE`.

For a runnable packaging example, see the [macOS check app](../checks/macos/README.md). Build and test instructions are in [Contributing](../CONTRIBUTING.md).
