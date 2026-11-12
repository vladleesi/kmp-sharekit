"""Fail before Gradle when a release bump leaves stale npm workspace versions."""

import json
from pathlib import Path

root = Path(__file__).resolve().parents[2]
version = next(line.split("=", 1)[1] for line in
               (root / "gradle.properties").read_text().splitlines()
               if line.startswith("VERSION_NAME="))
for relative in ("kotlin-js-store/package-lock.json", "kotlin-js-store/wasm/package-lock.json"):
    lock = json.loads((root / relative).read_text())
    for workspace in ("packages/kmp-sharekit-sharekit", "packages/kmp-sharekit-sharekit-test"):
        actual = lock["packages"][workspace]["version"]
        if actual != version:
            raise SystemExit(f"{relative}: {workspace} is {actual}, expected {version}. "
                             "Run kotlinUpgradePackageLock kotlinWasmUpgradePackageLock --rerun-tasks "
                             "and commit both lockfiles with the version bump.")
print(f"JS and Wasm workspace lockfiles match {version}.")
