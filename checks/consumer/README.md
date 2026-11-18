# Publication consumer check

This independent build resolves both ShareKit Maven coordinates exclusively from the local validation repository. It compiles the public API on every published target, including a named JVM target that matches Compose desktop's usual target naming.

From the repository root, publish unsigned validation artifacts, then run:

```sh
./gradlew publishAllPublicationsToValidationRepository -PsignAllPublications=false
./gradlew -p checks/consumer compileKotlinDesktop compileAndroidMain compileKotlinJs compileKotlinWasmJs compileKotlinIosArm64 compileKotlinIosSimulatorArm64 compileKotlinIosX64
```

Requires macOS and the Android SDK (`ANDROID_HOME`). The [interactive samples](../../samples/README.md) cover graphical presentation.
