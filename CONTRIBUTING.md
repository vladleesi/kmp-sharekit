# Contributing

Open an issue for bugs or proposed API changes. Branch from `develop` and target all contributor pull requests at `develop`. Keep changes focused, describe the behavior change, and add tests when behavior changes. CI promotes verified `develop` commits to the protected `master` branch. Releases require explicit maintainer review and a confirmed manual dispatch. See [ROADMAP.md](ROADMAP.md).

## Development

Use JDK 21, Android SDK Platform 37, and SDK Build Tools 36.0.0. Set `ANDROID_HOME` or `sdk.dir` in the ignored `local.properties`. Apple builds and simulator tests require macOS with Xcode and an installed iOS simulator runtime. Use an Apple Silicon Mac to run `iosSimulatorArm64Test`.

Browser tests require Node.js 24 LTS/npm on `PATH` and Chrome (`CHROME_BIN` if needed). Gradle uses the installed Node.js; commit `kotlin-js-store` lockfiles when updating tooling. The webpack pin keeps Karma and its plugins compatible.

The toolchain uses Kotlin 2.4.21, AGP 9.3.1, and Gradle 9.7.0, within Kotlin's [fully supported compatibility range](https://kotlinlang.org/docs/gradle-configure-project.html). The wrapper verifies the Gradle distribution checksum.

```sh
# All hosts: formatting, Android tests, and AAR
./gradlew ktlintCheck :sharekit:testAndroidHostTest :sharekit:bundleAndroidMainAar

# All desktop hosts: JVM tests and library JAR
./gradlew :sharekit:jvmTest :sharekit:jvmJar

# Browser tests: run the shared webTest suite on both JS and WasmJS in headless Chrome
./gradlew :sharekit:jsBrowserTest :sharekit:wasmJsBrowserTest

# macOS: compile every Apple target, link frameworks, and run simulator tests
./gradlew :sharekit:compileKotlinIosArm64 :sharekit:compileKotlinIosSimulatorArm64 :sharekit:compileKotlinIosX64 \
  :sharekit:linkDebugFrameworkIosArm64 :sharekit:linkDebugFrameworkIosSimulatorArm64 \
  :sharekit:linkDebugFrameworkIosX64 :sharekit:iosSimulatorArm64Test

# Apply Kotlin formatting
./gradlew ktlintFormat
```

Android host tests use Robolectric on API 23 and 37; they do not replace testing on a device. Intel iOS simulator code is compiled and linked, but its tests cannot run on Apple Silicon. Before releases, also open the share sheet in an Android app and an iPhone/iPad app; standalone native tests do not host an application window.

Browser tests cover Web Share results and failures on both backends. Before releases, manually test sharing and cancellation over HTTPS with mouse/touch, unsupported browsers, and error/fallback UI. Verify the README's common Compose button on Android, iOS, JS, and WasmJS. Automated browser checks can stub `navigator.share` while preserving real user activation; native OS share sheets need manual testing.

Desktop CI runs JVM tests on macOS, Windows, and Linux. Tests run headless and inject mail handlers to cover URI encoding, capability checks, and launch failures without opening apps. Before releases, also test the common Compose button on a graphical desktop with a configured mail client, verifying Unicode, multiline text, cancellation, and a missing mail client. Desktop sharing opens an email draft via Java Desktop MAIL; support varies by desktop environment. No email is sent automatically.

## Versioning

`VERSION_NAME` in `gradle.properties` is the only release version setting. ShareKit follows semantic versioning independently of Kotlin and Android tooling. Before a release, update it and `CHANGELOG.md`, and keep README installation examples current. Regenerate both npm lockfiles with `./gradlew kotlinUpgradePackageLock kotlinWasmUpgradePackageLock --rerun-tasks`; run `python3 .github/scripts/check-release-version.py` and commit the lockfiles with the version bump. Release tags use `v<version>` (first release: `v0.1.0`).

Renovate uses the [shared preset](https://github.com/vladleesi/renovate-config) to report updates in its Dependency Dashboard. Updates require dashboard approval and target `develop`; automerge is disabled.

## Publishing

Publications use `dev.vladleesi:kmp-sharekit` and the version above. The publishing plugin generates eight publications: KMP metadata, Android, three iOS architectures, JVM (`kmp-sharekit-jvm`), JS (`kmp-sharekit-js`), and WasmJS (`kmp-sharekit-wasm-js`). It includes source jars and generated Dokka HTML documentation in the Javadoc classifier jars. Generate browsable documentation with `./gradlew :sharekit:dokkaGeneratePublicationHtml` (output: `sharekit/build/dokka/html`). The validator checks every artifact, JVM and browser variant attributes, root platform references, and signatures when requested; no WASI publication is expected.

Validate the complete Maven repository locally without credentials (macOS required):

```sh
./gradlew :sharekit:publishAllPublicationsToValidationRepository -PsignAllPublications=false
python3 .github/scripts/verify-publications.py
```

For releases, verify the `dev.vladleesi` namespace in [Central Portal](https://central.sonatype.com/) and generate a Portal user token. Configure these GitHub Actions secrets:

- `MAVEN_CENTRAL_USERNAME` and `MAVEN_CENTRAL_PASSWORD`: Portal token credentials.
- `SIGNING_IN_MEMORY_KEY`: the complete ASCII-armored private GPG key.
- `SIGNING_IN_MEMORY_KEY_PASSWORD`: its passphrase.

Publish the corresponding public key to a public keyserver and keep a secure backup of the private key. Local signing can use the publisher's [in-memory signing properties](https://vanniktech.github.io/gradle-maven-publish-plugin/central/#secrets) through environment variables or private user Gradle properties. Never put credentials in this repository or logs.

After Android, desktop, browser, and Apple CI checks pass on `develop`, CI fast-forwards `master` to that exact commit and stops there for maintainer review. It never force-pushes or overwrites divergent history. A newer `develop` commit supersedes an older run awaiting promotion.

After explicit approval, manually dispatch Release on `master` with the reviewed commit SHA and `confirm=true`. The Release workflow verifies the master commit, reruns validation, creates `v<version>` and a stable GitHub release, then signs and publishes to Maven Central. A version already tagged at an older commit is skipped until `VERSION_NAME` is bumped. To retry a failed release, rerun its failed jobs or dispatch Release on `master` with the original commit SHA and explicit confirmation. Central versions are immutable; do not republish a successful version.

## API and consumer checks

Run `./gradlew :sharekit:checkKotlinAbi` to compare the exported API with the checked-in baselines. Only run `:sharekit:updateKotlinAbi` after reviewing an intentional API change. The full check runs on macOS so every Apple target is available. Baselines cover JVM, Android, JS, WasmJS, and iOS; source compatibility and behavior still require tests.

After local publication, compile [checks/consumer](checks/consumer/README.md) to verify root metadata and target selection from an independent build. CI resolves ShareKit exclusively from the local validation repository so a missing publication cannot silently fall back to Maven Central.
