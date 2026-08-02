<!--
  Vibrato — a free, open source IPTV player.
  Copyright (C) 2026 The Vibrato Authors
  Licensed under the GNU General Public License v3.0 or later. See LICENSE.
-->

## What this changes

<!-- One or two sentences. Link the issue if there is one. -->

## Acceptance criteria

<!-- IDs from docs/ACCEPTANCE.md this PR affects, e.g. AC-PL-04. Write "none" if it
     genuinely touches none — build or docs changes often do not. -->

## How it was tested

<!-- "Works on my device" is not a test plan. Name the tests, the device, the Android
     version, and the source type used. Never name the source itself. -->

## Checklist

- [ ] `./gradlew build` is green locally (compile, tests, detekt, lint)
- [ ] Every new source file carries the GPLv3 header (AC-LEGAL-02)
- [ ] Every new dependency goes through `gradle/libs.versions.toml`
- [ ] No Compose import added to `:core:*` or `:source:*` (AC-NFR-06)
- [ ] No hardcoded user-facing strings; they are in `strings.xml` (AC-NFR-08)
- [ ] **No playlist URL, provider hostname, or credential anywhere in the diff, including test fixtures** (AC-LEGAL-04)
- [ ] Fixtures use synthetic data only
- [ ] This change does not conflict with `docs/FREEZE.md`
