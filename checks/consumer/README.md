# Publication consumer check

This independent build resolves the root Maven coordinate from the validation repository exclusively. It does not depend on project sources or substitute a project dependency. A named JVM target also checks Compose desktop's usual target naming.

From the repository root, publish unsigned validation artifacts, then run:

```sh
./gradlew :sharekit:publishAllPublicationsToValidationRepository -PsignAllPublications=false
./gradlew -p checks/consumer compileKotlinDesktop compileAndroidMain compileKotlinJs compileKotlinWasmJs compileKotlinIosArm64 compileKotlinIosSimulatorArm64 compileKotlinIosX64
```

Requires macOS and the Android SDK (`ANDROID_HOME`). This checks variant selection and common API compilation, not graphical presentation. Interactive samples are planned separately in ROADMAP.md.
