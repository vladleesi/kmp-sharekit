# Packaged macOS check

This small Swing app exercises the published JVM artifact through a real AppKit window. It is a release check, not a library dependency. Use a full JDK with `javac` and `jpackage` (17+), then run these commands from the repository root after local publication:

```sh
export SHAREKIT_JDK=/path/to/jdk/Contents/Home
export SHAREKIT_STDLIB=/path/to/kotlin-stdlib-2.4.21.jar
mkdir -p checks/macos/build/input checks/macos/build/classes
cp sharekit/build/maven-repository/dev/vladleesi/kmp-sharekit-jvm/0.5.0/kmp-sharekit-jvm-0.5.0.jar checks/macos/build/input/
cp "$SHAREKIT_STDLIB" checks/macos/build/input/
unzip -p checks/macos/build/input/kmp-sharekit-jvm-0.5.0.jar native/macos/libsharekit.dylib > checks/macos/build/input/libsharekit.dylib
"$SHAREKIT_JDK/bin/javac" -cp 'checks/macos/build/input/*' -d checks/macos/build/classes checks/macos/ShareKitSmoke.java
"$SHAREKIT_JDK/bin/jar" --create --file checks/macos/build/input/smoke.jar -C checks/macos/build/classes .
"$SHAREKIT_JDK/bin/jpackage" --type app-image --name ShareKitSmoke \
  --input checks/macos/build/input --main-jar smoke.jar --main-class ShareKitSmoke \
  --dest checks/macos/build/app --java-options '-Djava.library.path=$APPDIR' \
  --java-options '-Djava.awt.headless=false'
checks/macos/build/app/ShareKitSmoke.app/Contents/MacOS/ShareKitSmoke --smoke
```

Use a fresh app destination on each packaging run. JDK 24+ also needs `--java-options '--enable-native-access=ALL-UNNAMED'`.

The smoke mode opens a native picker, closes its host, verifies one cancellation callback on AWT, then verifies `HOST_UNAVAILABLE`. It exits nonzero on a failure or timeout and never selects a recipient. Run without `--smoke` for manual picker selection, cancellation, and explicit email review. The generated app is ad hoc signed; notarized release-app checks remain separate.
