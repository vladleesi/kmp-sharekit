# Release plan

Each release is implemented and validated independently. Stop for the maintainer's review and explicit approval before publishing or beginning the next milestone. Version numbers describe planned scope, not promised dates. Changes that need a different compatibility policy must be reviewed first.

| Release | Scope | Exit criteria |
| --- | --- | --- |
| 0.3.1 | Release quality: ABI baselines, generated API documentation, independent publication consumer, manual release dispatch | All eight publications validate with real documentation; all target consumer compilations pass; API check passes; maintainer approves |
| 0.4.0 | Common request/result/capability API; text, URL, title | Existing signatures remain compatible; shared callers handle typed failures; platform outcome semantics and unsupported fields are documented and tested |
| 0.5.0 | Native macOS sharing for JVM desktop | Packaged desktop app presents an anchored native picker; ownership, cancellation, and failure paths verified; email remains an explicit available method |
| 0.6.0 | Native Windows sharing for JVM desktop | Per-window native integration verified on Windows; dependency and packaging impact reviewed; capability detection tested |
| 0.7.0 | File and image sharing, then multiple files | MIME types, permissions, payload checks, and cleanup ownership documented; recipient access verified on supported platforms |
| 0.8.0 | Optional Compose integration and interactive samples | Lifecycle and multiple-window behavior verified; core remains independent of Compose |
| 1.0.0 | Stable supported API and platform matrix | Migration guide, compatibility policy, packaged-app checks, and real-device sharing review complete |

## 0.3.1 review checklist

- Review the API baseline against 0.3.0; this release must not change the public API.
- Run formatting, platform tests, ABI validation, publication validation, and the independent consumer build.
- Inspect generated documentation and verify real share UI using the manual checks in CONTRIBUTING.md. Consumer compilation does not replace graphical app testing.
- Record maintainer approval before dispatching Release on master with the reviewed commit SHA and `confirm=true`.
- After publication, verify the Central artifacts and GitHub release before starting 0.4.0.

## 0.4.0 design decisions

Introduce a small `ShareRequest` and callback API while preserving `shareText(text)` and the browser callback overload. Separate content capability from host readiness. Distinguish native sharing from email composition. Report launch/handoff, observed cancellation, unavailability, and failure without claiming delivery. Browser AbortError remains ambiguous between cancellation and no targets.

Start browser sharing synchronously within a user gesture. Define callback timing, exactly-once behavior, repeated-click handling, and retention of presentation hosts before implementation. Prefer platform-specific presentation options over exposing UI frameworks in common code. Native desktop bridges and file ownership require separate design reviews in their milestones.

## Release approval

CI can promote validated commits to master, but cannot dispatch Release automatically. Publishing requires a manual workflow dispatch with explicit confirmation. Repository workflow permissions determine who can dispatch; this is not a substitute for configuring protected environments if account-level reviewer enforcement is required.
