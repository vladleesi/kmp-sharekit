#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../.."

./gradlew :samples:compose:linkDebugFrameworkIosSimulatorArm64
framework_dir="samples/compose/build/bin/iosSimulatorArm64/debugFramework"
app="samples/ios/build/ShareKitSample.app"
sdk="$(xcrun --sdk iphonesimulator --show-sdk-path)"

mkdir -p "$app"
cp samples/ios/Info.plist "$app/Info.plist"
xcrun --sdk iphonesimulator swiftc -parse-as-library -module-name ShareKitSampleHost -sdk "$sdk" -target arm64-apple-ios18.5-simulator \
  -F "$framework_dir" -framework ShareKitSample -framework UIKit -framework Metal -lc++ \
  samples/ios/ShareKitSampleApp.swift -o "$app/ShareKitSample"
codesign --force --sign - "$app"

if [[ "${1:-}" != "--build-only" ]]; then
  simulator="${IOS_SIMULATOR:-booted}"
  xcrun simctl install "$simulator" "$app"
  xcrun simctl launch --terminate-running-process "$simulator" dev.vladleesi.sharekit.sample.ios
fi
