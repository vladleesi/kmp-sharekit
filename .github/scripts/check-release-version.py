"""Fail before Gradle when a release bump leaves stale npm workspace versions."""

import json
from pathlib import Path

root = Path(__file__).resolve().parents[2]
version = next(line.split("=", 1)[1] for line in
               (root / "gradle.properties").read_text().splitlines()
               if line.startswith("VERSION_NAME="))
workspaces = ("sharekit", "sharekit-compose", "samples-compose")
for relative in ("kotlin-js-store/package-lock.json", "kotlin-js-store/wasm/package-lock.json"):
    lock = json.loads((root / relative).read_text())
    for name in workspaces:
        for suffix in ("", "-test"):
            workspace = f"packages/kmp-sharekit-{name}{suffix}"
            actual = lock["packages"].get(workspace, {}).get("version")
            if actual != version:
                raise SystemExit(f"{relative}: {workspace} is {actual}, expected {version}. "
                                 "Run kotlinUpgradePackageLock kotlinWasmUpgradePackageLock --rerun-tasks "
                                 "and commit both lockfiles with the version bump.")
print(f"JS and Wasm workspace lockfiles match {version}.")
