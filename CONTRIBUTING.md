# Contributing to Vibrato

Thanks for considering a contribution. Vibrato is a free, open source IPTV **client**
licensed under the GPLv3.

Before anything else, read [`docs/FREEZE.md`](docs/FREEZE.md). It is the canonical
description of the project and it is **frozen for v1.0**. A pull request that conflicts
with it will be closed regardless of code quality — not because the idea is bad, but
because scope discipline is what gets v1.0 shipped. If you think the freeze is wrong,
open a discussion proposing a dated amendment rather than a PR implementing the change.

---

## The one rule that gets you banned

**Never post a playlist URL, a provider hostname, an Xtream credential, or a pointer to
where any of those may be obtained.** Not in an issue, not in a PR, not in a test
fixture, not in a comment, not in a screenshot, not in a log paste.

This applies to issues, pull requests, discussions, and commit messages. Violations are
removed and the account is blocked on the first offence. There is no warning.

This is not squeamishness. Vibrato is a general-purpose media player in the same category
as VLC. Its legal position depends on the project having no knowledge of, and no
involvement in, what any user's playlist contains. A single provider URL in the issue
tracker undermines that for everyone. See `docs/ACCEPTANCE.md`, AC-LEGAL-04.

**Test fixtures use synthetic data only.** Invent hostnames like `example.invalid`.
Never sanitise a real playlist and assume that is sufficient — write a fake one.

Requests to add a default playlist, a channel directory, a provider list, a "discover
content" feature, or any bundled source will be closed immediately. This is a permanent
non-goal (`docs/FREEZE.md` §2).

## Also out of scope for v1.0

Each of these is a deliberate decision, not an oversight. See `docs/FREEZE.md` §2 and §3:

- Android TV / Google TV / desktop / web frontends — phase 2
- DRM (Widevine, ClearKey, PlayReady)
- Recording, catch-up, or timeshift
- Any backend, account system, telemetry, or cloud sync
- XMLTV EPG (v1 takes programme data from the Xtream API only)

## Getting set up

**Requirements**

- JDK 17 or newer
- Android SDK with platform 37 and build-tools 37.0.0
- No Android Studio required — IntelliJ IDEA works, and so does the command line

```bash
git clone https://github.com/vibrato-tv/vibrato.git
cd vibrato
./gradlew build
```

That runs compilation, unit tests, detekt, and Android Lint. It must be green before you
open a PR; CI runs the same thing.

Useful targets:

```bash
./gradlew test            # unit tests
./gradlew detektAll       # static analysis across every module
./gradlew lint            # Android Lint
./gradlew :app:installDebug
```

## Architecture rules that are enforced, not suggested

These come from `docs/FREEZE.md` §4. Breaking one is a design regression even if every
test passes.

1. **No Compose or UI code in `:core:*` or `:source:*`.** This is checked by the build:
   `checkNoCompose` fails the `check` task if a Compose artifact reaches those modules'
   compile classpath. `:core:model` and `:source:*` are plain JVM modules so it cannot
   happen structurally. Do not work around the check — the phase-2 TV frontend depends on
   these modules staying clean.
2. **The source layer is abstracted.** Adding a protocol means adding a `MediaSource`
   implementation, not editing feature modules.
3. **EPG is source-agnostic.** Storage accepts programmes from any provider, even though
   only Xtream supplies them in v1.
4. **Playback sits behind `PlayerController`.** Feature code never touches ExoPlayer.
5. **The app never phones home.** No analytics, no crash reporting, no update check
   against a project server. The only outbound traffic goes to hosts the user typed in.
6. **Credentials never leave the device**, and never reach a log, an export, or a crash
   trace.

## Code standards

- **Kotlin**, official code style. detekt (with its ktlint-backed `formatting` ruleset)
  runs in CI with `maxIssues: 0`.
- **Every source file carries the GPLv3 header** (AC-LEGAL-02). Copy it from any existing
  file.
- **Every dependency goes through `gradle/libs.versions.toml`.** A hardcoded coordinate in
  a module build file will be rejected.
- **No hardcoded user-facing strings.** They belong in `strings.xml` (AC-NFR-08).
- Parser modules need **80%+ unit test coverage**, including malformed input (AC-NFR-07).

## Commits and pull requests

- [Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`,
  `refactor:`, `docs:`, `test:`, `build:`, `ci:`, `chore:`
- Reference acceptance criteria by ID where one applies:
  `fix: AC-PL-04 crash on BOM-prefixed M3U`
- Trunk-based development: short-lived branches off `main`, squash merge
- Keep PRs focused. One concern per PR.
- Describe how you tested. "Works on my device" is not a test plan.

## Licensing of contributions

Vibrato is GPLv3. By contributing you agree your work is licensed under
[GPL-3.0-or-later](LICENSE). There is no CLA and no copyright assignment; you keep your
copyright.

Only add dependencies whose licence is GPLv3-compatible. Apache-2.0, MIT, and BSD are
fine. **GPLv2-only is not** — that is precisely why this project is GPLv3 rather than
GPLv2 (`docs/FREEZE.md` §3).

## Reporting bugs

Include: device and Android version, Vibrato version, source type (M3U or Xtream — **not
the source itself**), what you expected, what happened, and a synthetic reproduction where
possible.

Security issues go through [`SECURITY.md`](SECURITY.md), never the public tracker.
