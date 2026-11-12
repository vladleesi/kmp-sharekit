# Release plan

Each release is implemented and validated independently. After each CI-published release, stop for maintainer review and explicit approval before beginning the next milestone. An approved commit/push of a release version starts the CI publication pipeline. Version numbers describe planned scope, not promised dates. Changes that need a different compatibility policy must be reviewed first.

| Release | Scope | Exit criteria |
| --- | --- | --- |
| 0.4.0 | Common request/result/capability API; text, URL, title | Existing signatures remain compatible; shared callers handle typed failures; platform outcome semantics and unsupported fields are documented and tested |
| 0.5.0 | Native macOS sharing for JVM desktop | Packaged desktop app presents an anchored native picker; ownership, cancellation, and failure paths verified; email remains an explicit available method |
| 0.6.0 | Native Windows sharing for JVM desktop | Per-window native integration verified on Windows; dependency and packaging impact reviewed; capability detection tested |
| 0.7.0 | File and image sharing, then multiple files | MIME types, permissions, payload checks, and cleanup ownership documented; recipient access verified on supported platforms |
| 0.8.0 | Optional Compose integration and interactive samples | Lifecycle and multiple-window behavior verified; core remains independent of Compose |
| 1.0.0 | Stable supported API and platform matrix | Migration guide, compatibility policy, packaged-app checks, and real-device sharing review complete |

## 0.4.0 — ready for review

The review candidate adds the common API, explicit mobile hosts, platform mappings, tests, and consumer compilation. iOS title metadata is explicitly unsupported in this milestone. Approval and CI publication are required before advancing to 0.5.0.

Validation: 144 automated test executions pass across JVM, Android, iOS simulator, and JS/WasmJS in Chrome and Firefox. All eight local Maven publications validate, every platform consumer compiles, formatting passes, and ABI changes are additive. Real native presentation and Safari smoke checks remain manual review items. The candidate is not published.

## 0.4.0 design decisions

Introduce a small `ShareRequest` and callback API while preserving `shareText(text)` and the browser callback overload. Separate content capability from host readiness. Distinguish native sharing from email composition. Report launch/handoff, observed cancellation, unavailability, and failure without claiming delivery. Browser AbortError remains ambiguous between cancellation and no targets.

Start browser sharing synchronously within a user gesture. Define callback timing, exactly-once behavior, repeated-click handling, and retention of presentation hosts before implementation. Prefer platform-specific presentation options over exposing UI frameworks in common code. Native desktop bridges and file ownership require separate design reviews in their milestones.

## Release publication and review

After validation, CI promotes the exact develop commit to master and dispatches Release automatically. Release revalidates that commit, creates its version tag and GitHub release, and publishes signed artifacts to Maven Central. Manual confirmed dispatch is retained only for recovery. Stop after publication and wait for maintainer approval before starting the next release.
