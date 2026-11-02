# Contributing

Open an issue for bugs or proposed API changes. Branch from `develop` and target all contributor pull requests at `develop`. Keep changes focused, describe the behavior change, and add tests when behavior changes. CI promotes verified `develop` commits to the protected `master` branch.

## Development

Use JDK 21, Android SDK Platform 37, and SDK Build Tools 36.0.0. Set `ANDROID_HOME` or `sdk.dir` in the ignored `local.properties`. Apple builds and simulator tests require macOS with Xcode and an installed iOS simulator runtime. Use an Apple Silicon Mac to run `iosSimulatorArm64Test`.

The toolchain uses Kotlin 2.4.21, AGP 9.3.1, and Gradle 9.7.0, within Kotlin's [fully supported compatibility range](https://kotlinlang.org/docs/gradle-configure-project.html). The wrapper verifies the Gradle distribution checksum.

```sh
# All hosts: formatting, Android tests, and AAR
./gradlew ktlintCheck :sharekit:testAndroidHostTest :sharekit:bundleAndroidMainAar

# macOS: compile every Apple target, link frameworks, and run simulator tests
./gradlew :sharekit:compileKotlinIosArm64 :sharekit:compileKotlinIosSimulatorArm64 :sharekit:compileKotlinIosX64 \
  :sharekit:linkDebugFrameworkIosArm64 :sharekit:linkDebugFrameworkIosSimulatorArm64 \
  :sharekit:linkDebugFrameworkIosX64 :sharekit:iosSimulatorArm64Test

# Apply Kotlin formatting
./gradlew ktlintFormat
```

Android host tests use Robolectric on API 23 and 37; they do not replace testing on a device. Intel iOS simulator code is compiled and linked, but its tests cannot run on Apple Silicon. Before releases, also open the share sheet in an Android app and an iPhone/iPad app; standalone native tests do not host an application window.

## Versioning

`VERSION_NAME` in `gradle.properties` is the only release version setting. ShareKit follows semantic versioning independently of Kotlin and Android tooling. Before a release, update it and `CHANGELOG.md`, and keep README installation examples current. Release tags use `v<version>` (first release: `v0.1.0`).

Renovate uses the [shared preset](https://github.com/vladleesi/renovate-config) to report updates in its Dependency Dashboard. Updates require dashboard approval and target `develop`; automerge is disabled.

## Publishing

Publications use `dev.vladleesi:kmp-sharekit` and the version above. The publishing plugin generates Android, iOS, and KMP metadata artifacts, source jars, and required Javadoc classifier jars. API documentation is provided as KDoc in the sources.

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

After both CI checks pass on `develop`, CI fast-forwards `master` to that exact commit and starts the Release workflow there. It never force-pushes or overwrites divergent history. A newer `develop` commit supersedes an older run awaiting promotion.

The Release workflow verifies the master commit, reruns validation, creates `v<version>` and a stable GitHub release, then signs and publishes to Maven Central. A version already tagged at an older commit is skipped until `VERSION_NAME` is bumped. To retry a failed release, rerun its failed jobs or dispatch Release on `master` with the original commit SHA and explicit confirmation. Central versions are immutable; do not republish a successful version.
